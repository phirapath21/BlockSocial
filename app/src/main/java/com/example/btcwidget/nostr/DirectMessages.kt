package com.example.btcwidget.nostr

import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Filter
import com.example.btcwidget.nostr.core.Nip17
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Private messages (NIP-17). Gift wraps addressed to the user are fetched from their DM
 * inbox relays (kind 10050), opened with their key (or signer app), and grouped into
 * conversations. Sent messages are wrapped once per recipient and once for the user's own
 * copy, and delivered to each person's inbox relays.
 *
 * Only the still-encrypted wraps are cached on disk; decrypted text lives in memory.
 */
class DirectMessages internal constructor(private val repo: NostrRepository) {

    /** A conversation: everyone except the user, and its newest message. */
    data class Conversation(val peers: List<String>, val last: Nip17.Message) {
        val key get() = peers.joinToString(",")
    }

    private val _messages = MutableStateFlow<List<Nip17.Message>>(emptyList())
    /** Every decrypted message, oldest first. */
    val messages: StateFlow<List<Nip17.Message>> = _messages.asStateFlow()

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Wraps the signer app wouldn't open without asking (the user can allow it once). */
    private val _locked = MutableStateFlow(0)
    val locked: StateFlow<Int> = _locked.asStateFlow()

    /** The user's DM inbox relays (kind 10050); empty when they haven't set one up. */
    private val _inbox = MutableStateFlow<List<String>?>(null)
    val inbox: StateFlow<List<String>?> = _inbox.asStateFlow()

    private val byId = ConcurrentHashMap<String, Nip17.Message>()
    private val wraps = ConcurrentHashMap<String, Event>()
    private val lockedWraps = ConcurrentHashMap<String, Event>()
    private val mutex = Mutex()
    private var inboxPool: RelayPool? = null
    private var stopLive: (() -> Unit)? = null
    private var started = false

    /** Loads cached and new messages and keeps listening while the app runs. */
    suspend fun start() = mutex.withLock {
        val me = repo.pubkey ?: return@withLock
        if (started) return@withLock
        started = true
        _loading.value = true
        try {
            open(repo.cache.loadEvents(WRAP_CACHE).filter { it.tagValues("p").contains(me) })
            val myList = repo.queryEverywhere(listOf(Filter(authors = listOf(me), kinds = listOf(Nip17.DM_RELAYS), limit = 1)))
                .maxByOrNull { it.createdAt }
            val inboxRelays = Nip17.relaysFrom(myList)
            _inbox.value = inboxRelays
            NostrAccountStore.setDmInbox(repo.appContext, inboxRelays)
            // Read from the inbox relays and the usual ones (some clients still deliver there).
            val pool = RelayPool().also { it.setRelays((inboxRelays + repo.pool.urls).distinct().take(10)) }
            inboxPool = pool
            val filter = Filter(kinds = listOf(Nip17.GIFT_WRAP), tags = mapOf("p" to listOf(me)), limit = 1000)
            open(pool.query(listOf(filter), timeoutMs = 8_000))
            // New messages as they arrive. Wraps are back-dated up to two days, so look back that far.
            stopLive = pool.subscribe(
                listOf(Filter(kinds = listOf(Nip17.GIFT_WRAP), tags = mapOf("p" to listOf(me)), since = com.example.btcwidget.nostr.core.Events.now() - 2 * 24 * 3600 - 60)),
                { wrap, _ -> repo.scope.launch { open(listOf(wrap)) } }
            )
        } finally {
            _loading.value = false
        }
    }

    /** Decrypts new wraps and publishes the result. */
    private suspend fun open(newWraps: List<Event>) {
        val signer = runCatching { repo.requireSigner() }.getOrNull() ?: return
        val fresh = newWraps.filter { wraps.putIfAbsent(it.id, it) == null }
        if (fresh.isEmpty()) return
        val opened = coroutineScope {
            fresh.map { wrap ->
                async {
                    val msg = runCatching {
                        Nip17.unwrap(wrap) { ciphertext, peer ->
                            signer.nip44Decrypt(ciphertext, peer, interactive = false) ?: throw Locked()
                        }
                    }
                    if (msg.exceptionOrNull() is Locked) lockedWraps[wrap.id] = wrap
                    msg.getOrNull()
                }
            }.awaitAll().filterNotNull()
        }
        _locked.value = lockedWraps.size
        opened.forEach { byId[it.id] = it }
        publishState()
        repo.loadProfiles(opened.flatMap { it.recipients + it.sender })
        repo.scope.launch { repo.cache.saveEvents(WRAP_CACHE, wraps.values.sortedByDescending { it.createdAt }.take(2_000)) }
    }

    private class Locked : Exception()

    /** Asks the signer app once (the user can tick "always allow"), then opens the rest. */
    suspend fun unlock() {
        val signer = repo.requireSigner()
        val first = lockedWraps.values.firstOrNull() ?: return
        signer.nip44Decrypt(first.content, first.pubkey, interactive = true)
        val retry = lockedWraps.values.toList()
        lockedWraps.clear()
        retry.forEach { wraps.remove(it.id) }
        open(retry)
    }

    private fun publishState() {
        val me = repo.pubkey ?: return
        val all = byId.values.sortedBy { it.createdAt }
        _messages.value = all
        _conversations.value = all.groupBy { it.peers(me) }
            .map { (peers, msgs) -> Conversation(peers, msgs.last()) }
            .sortedByDescending { it.last.createdAt }
    }

    /** Messages with exactly these [peers], oldest first. */
    fun thread(peers: List<String>): List<Nip17.Message> {
        val me = repo.pubkey ?: return emptyList()
        val key = peers.sorted()
        return _messages.value.filter { it.peers(me) == key }
    }

    /**
     * Sends [text] to [peers]: one gift wrap per recipient (to their inbox relays) plus one
     * for the user's own copy. Fails if no recipient's relay accepted it.
     */
    suspend fun send(peers: List<String>, text: String, replyTo: String? = null) {
        val me = repo.pubkey ?: error("Log in to Nostr first")
        val signer = repo.requireSigner()
        val rumor = Nip17.rumor(me, peers, text.trim(), replyTo)
        val targets = (peers + me).distinct()
        // Inbox relays of every recipient, looked up together.
        val lists = repo.queryEverywhere(listOf(Filter(authors = targets, kinds = listOf(Nip17.DM_RELAYS))))
            .groupBy { it.pubkey }.mapValues { (_, l) -> Nip17.relaysFrom(l.maxByOrNull { it.createdAt }) }
        var delivered = false
        coroutineScope {
            targets.map { receiver ->
                async {
                    val seal = Nip17.seal(rumor, receiver, signer::nip44Encrypt, signer::sign)
                    val wrap = Nip17.wrap(seal, receiver)
                    val relays = lists[receiver].orEmpty().ifEmpty { repo.pool.urls.toList() }
                    val ok = publishTo(relays, wrap)
                    if (receiver != me && ok) delivered = true
                    if (receiver == me) open(listOf(wrap))
                }
            }.awaitAll()
        }
        if (!delivered) error("No relay accepted the message")
    }

    /** Publishes [event] to [relays] (reusing open connections when they're ours). */
    private suspend fun publishTo(relays: List<String>, event: Event): Boolean {
        val known = repo.pool.urls
        if (relays.all { it in known }) return repo.pool.publish(event).any { it.accepted }
        val temp = RelayPool().also { it.setRelays(relays.take(5)) }
        return try { temp.publish(event).any { it.accepted } } finally { temp.closeAll() }
    }

    /** Publishes a kind-10050 list so others' apps know where to deliver the user's DMs. */
    suspend fun setUpInbox(relays: List<String> = DEFAULT_INBOX) {
        repo.publish(Nip17.dmRelayList(repo.pubkey ?: error("Log in first"), relays))
        _inbox.value = relays
        NostrAccountStore.setDmInbox(repo.appContext, relays)
    }

    fun reset() {
        stopLive?.invoke()
        stopLive = null
        inboxPool?.closeAll()
        inboxPool = null
        started = false
        byId.clear(); wraps.clear(); lockedWraps.clear()
        _messages.value = emptyList(); _conversations.value = emptyList(); _locked.value = 0; _inbox.value = null
    }

    companion object {
        private const val WRAP_CACHE = "dm_wraps"

        /** Inbox relays suggested when setting up DMs. */
        val DEFAULT_INBOX = listOf("wss://relay.damus.io", "wss://nos.lol", "wss://relay.primal.net")
    }
}
