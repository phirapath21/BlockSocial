package com.example.btcwidget.nostr

import android.content.Context
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Events
import com.example.btcwidget.nostr.core.Filter
import com.example.btcwidget.nostr.core.ReplyTree
import com.example.btcwidget.nostr.core.Kind
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.nostr.core.ThreadRef
import com.example.btcwidget.nostr.core.UnsignedEvent
import com.example.btcwidget.nostr.core.Zaps
import com.example.btcwidget.nostr.core.normalizeRelayUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap

/** Likes, reposts, replies and zaps on a note, and whether the user did each. */
data class NoteStats(
    val replies: Int = 0,
    val reposts: Int = 0,
    val likes: Int = 0,
    val zapMsat: Long = 0,
    val likedByMe: Boolean = false,
    val repostedByMe: Boolean = false,
    /** The user's own reaction ("+" for a like, or an emoji), when [likedByMe]. */
    val myReaction: String? = null
)

/** One row of a timeline: a note, or a repost of one. */
data class FeedItem(val note: Event, val repostedBy: String? = null, val sortTime: Long = note.createdAt) {
    val key get() = (repostedBy ?: "") + note.id
}

/** A notification about the user's notes or account. */
data class NostrNotification(
    val event: Event,
    val type: Type,
    /** Who acted (the zapper for zaps). */
    val actor: String,
    /** The user's note it concerns, if any. */
    val noteId: String?,
    val zapMsat: Long = 0
) {
    enum class Type { REPLY, MENTION, REPOST, LIKE, ZAP }
}

/**
 * Nostr data for the app: the account, relays, an in-memory cache of notes, profiles
 * and stats, and every action the user can take. One instance for the process.
 */
class NostrRepository private constructor(private val context: Context) {
    val pool = RelayPool()

    /**
     * Indexer relays that collect profiles, follow lists and relay lists from everyone
     * (as Amethyst uses): profiles load even when people post to relays we don't use.
     */
    internal val indexers = RelayPool().also { it.setRelays(INDEXER_RELAYS) }

    /** Background work (profiles, counts, cache writes) that screens don't wait for. */
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    internal val cache = NostrCache(java.io.File(context.cacheDir, "nostr"))
    /** The local event database (SQLite): what was seen, for instant and offline screens. */
    internal val events = EventStore(context)
    private val toStore = java.util.concurrent.ConcurrentLinkedQueue<Event>()
    @Volatile private var storeJob: Job? = null

    /** Private messages (NIP-17). */
    val dms: DirectMessages by lazy { DirectMessages(this) }

    /** True once the account's lists were read from relays this session (not just the cache). */
    @Volatile private var accountFresh = false

    private val _account = MutableStateFlow(NostrAccountStore.account(context))
    val account: StateFlow<NostrAccount?> = _account.asStateFlow()

    private val _profiles = MutableStateFlow<Map<String, Profile>>(emptyMap())
    /** Every profile seen so far, by pubkey. */
    val profiles: StateFlow<Map<String, Profile>> = _profiles.asStateFlow()

    private val _stats = MutableStateFlow<Map<String, NoteStats>>(emptyMap())
    val stats: StateFlow<Map<String, NoteStats>> = _stats.asStateFlow()

    private val _following = MutableStateFlow<Set<String>>(emptySet())
    /** Pubkeys the user follows. */
    val following: StateFlow<Set<String>> = _following.asStateFlow()

    /** Words, hashtags and threads from the mute list (accounts are in [muted]). */
    private val _mutes = MutableStateFlow(com.example.btcwidget.nostr.core.MuteRules())
    val mutes: StateFlow<com.example.btcwidget.nostr.core.MuteRules> = _mutes.asStateFlow()
    /** Follows and their follows, when the web-of-trust filter is on and loaded. */
    @Volatile private var trust: com.example.btcwidget.nostr.core.TrustNetwork? = null
    private val _muted = MutableStateFlow<Set<String>>(emptySet())
    /** Pubkeys the user muted (NIP-51 kind 10000); hidden everywhere. */
    val muted: StateFlow<Set<String>> = _muted.asStateFlow()

    private val notes = ConcurrentHashMap<String, Event>()
    private val profileEvents = ConcurrentHashMap<String, Event>()
    private var contactsEvent: Event? = null
    private var muteEvent: Event? = null
    private var bookmarkEvent: Event? = null
    private var pinEvent: Event? = null
    private var emojiListEvent: Event? = null
    /** The user's custom emoji (NIP-30 kind 10030 list), shortcode to image. */
    private val _emojis = MutableStateFlow<Map<String, String>>(emptyMap())
    val emojis: StateFlow<Map<String, String>> = _emojis.asStateFlow()

    private val _pinned = MutableStateFlow<List<String>>(emptyList())
    /** The user's pinned note ids (NIP-51 kind 10001), first shown first. */
    val pinned: StateFlow<List<String>> = _pinned.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<String>>(emptyList())
    /** Note ids the user bookmarked (NIP-51 kind 10003), newest first. */
    val bookmarks: StateFlow<List<String>> = _bookmarks.asStateFlow()
    /** The user's Blossom servers (BUD-03 kind 10063), empty for the default. */
    private var mediaServers: List<String> = emptyList()
    // Declared before init: the cache loader started there may set them.
    private var relayList: Event? = null
    private var mediaServerList: Event? = null
    private var signer: NostrSigner? = NostrAccountStore.signer(context)

    val pubkey: String? get() = _account.value?.pubkey

    internal val appContext: Context get() = context

    // Outbox state: declared before init, whose cache loader fills it.
    /** Connections to other people's write relays (separate from the user's own relays). */
    private val outbox = RelayPool()

    /** pubkey → write relays from their kind-10002 list; an empty list means "has none". */
    private val writeRelays = ConcurrentHashMap<String, List<String>>()
    private val relayListEvents = ConcurrentHashMap<String, Event>()

    /** Completes once the disk cache (profiles, the account's lists) is loaded. */
    val cacheReady: Job

    init {
        pool.setRelays(NostrAccountStore.relays(context) ?: RelayPool.DEFAULT_RELAYS)
        // Show what we knew last time straight away; relays refresh it.
        cacheReady = scope.launch(Dispatchers.IO) {
            // The database, plus the JSON files earlier versions wrote (moved into it once).
            val legacyProfiles = cache.loadEvents(PROFILES_CACHE)
            val legacyRelayLists = cache.loadEvents(RELAY_LIST_CACHE)
            if (legacyProfiles.isNotEmpty() || legacyRelayLists.isNotEmpty()) {
                events.insert(legacyProfiles + legacyRelayLists)
                cache.delete(PROFILES_CACHE); cache.delete(RELAY_LIST_CACHE)
            }
            events.query(listOf(Filter(kinds = listOf(Kind.METADATA), limit = 3_000))).forEach { remember(it, save = false) }
            events.query(listOf(Filter(kinds = listOf(Kind.RELAY_LIST), limit = 3_000))).forEach { e ->
                relayListEvents[e.pubkey] = e
                writeRelays[e.pubkey] = com.example.btcwidget.nostr.core.Outbox.writeRelays(e)
            }
            pubkey?.let { me -> applyAccountEvents(cache.loadEvents(ACCOUNT_CACHE).filter { it.pubkey == me }, fromRelays = false) }
            events.prune(keep = 50_000, me = pubkey)
        }
    }

    /** Both the user's relays and the indexers, merged (for profiles and lists). */
    internal suspend fun queryEverywhere(filters: List<Filter>, timeoutMs: Long = 4_000): List<Event> = coroutineScope {
        val a = async { pool.query(filters, timeoutMs, graceMs = 700) }
        val b = async { indexers.query(filters, timeoutMs, graceMs = 700) }
        (a.await() + b.await()).distinctBy { it.id }
    }

    // ---- Account ----

    suspend fun loginWithAmber() {
        val (pubkey, pkg) = AmberSigner.connect()
        NostrAccountStore.saveAmber(context, pubkey, pkg)
        onLoggedIn()
    }

    /** Logs in through a NIP-46 bunker:// link; waits for the signer to approve. */
    suspend fun loginWithBunker(uri: String) {
        val bunker = com.example.btcwidget.nostr.core.Nip46.parseBunker(uri)
            ?: throw IllegalArgumentException("That isn't a bunker:// link")
        val client = com.example.btcwidget.nostr.core.Schnorr.newSecretKey()
        val pairing = BunkerSigner("", bunker.signerPubkey, bunker.relays, client)
        val user = try { pairing.connect(bunker.secret) } finally { pairing.close() }
        NostrAccountStore.saveBunker(context, user, bunker.signerPubkey, bunker.relays, client)
        onLoggedIn()
    }

    /** Accepts an nsec (or 64-char hex). Throws IllegalArgumentException if invalid. */
    fun loginWithSecret(input: String) {
        val trimmed = input.trim()
        val hex = when (val e = com.example.btcwidget.nostr.core.Nip19.decode(trimmed)) {
            is com.example.btcwidget.nostr.core.Nip19.SecretKey -> e.hex
            null -> trimmed.lowercase().takeIf { com.example.btcwidget.nostr.core.Hex.isKey(it) }
            else -> null
        } ?: throw IllegalArgumentException("That isn't an nsec private key")
        NostrAccountStore.saveLocal(context, hex)
        onLoggedIn()
    }

    /** Creates a fresh key pair and logs in with it. Returns the new nsec to show once for backup. */
    fun createAccount(): String {
        val seckey = com.example.btcwidget.nostr.core.Schnorr.newSecretKey()
        NostrAccountStore.saveLocal(context, seckey)
        onLoggedIn()
        return com.example.btcwidget.nostr.core.Nip19.nsec(seckey)
    }

    private fun onLoggedIn() {
        val account = NostrAccountStore.account(context)
        // Another account was active: drop everything loaded for it first.
        if (_account.value != null && _account.value?.pubkey != account?.pubkey) resetSession()
        _account.value = account
        (signer as? BunkerSigner)?.close()
        signer = NostrAccountStore.signer(context)
        _nwc.value = NostrAccountStore.nwc(context)?.let { com.example.btcwidget.nostr.core.Nwc.parse(it) }
        _savedAccounts.value = NostrAccountStore.savedAccounts(context)
    }

    private val _savedAccounts = MutableStateFlow(NostrAccountStore.savedAccounts(context))
    /** Other accounts on this phone that the user can switch to. */
    val savedAccounts: StateFlow<List<NostrAccount>> = _savedAccounts.asStateFlow()

    /** Switches to a saved account; the current one is saved aside. */
    fun switchAccount(pubkey: String) {
        NostrAccountStore.switchTo(context, pubkey)
        resetSession()
        onLoggedIn()
    }

    /** Saves the current account aside so another can log in (Add account). */
    fun stashForNewLogin() {
        if (_account.value == null) return
        NostrAccountStore.stashActive(context)
        resetSession()
        _savedAccounts.value = NostrAccountStore.savedAccounts(context)
    }

    fun removeSavedAccount(pubkey: String) {
        NostrAccountStore.removeSaved(context, pubkey)
        Drafts.clear(context, pubkey)
        _savedAccounts.value = NostrAccountStore.savedAccounts(context)
    }

    fun logout() {
        pubkey?.let { Drafts.clear(context, it) }
        NostrAccountStore.clear(context)
        resetSession()
        _savedAccounts.value = NostrAccountStore.savedAccounts(context)
    }

    /** Forgets everything loaded for the active account (not what's stored for it). */
    private fun resetSession() {
        (signer as? BunkerSigner)?.close()
        dms.reset()
        _nwc.value = null
        pool.setRelays(NostrAccountStore.relays(context) ?: RelayPool.DEFAULT_RELAYS)
        relayList = null
        mediaServerList = null
        mediaServers = emptyList()
        cache.clear()
        toStore.clear()
        scope.launch(Dispatchers.IO) { this@NostrRepository.events.clear() }
        accountFresh = false
        _account.value = null
        signer = null
        contactsEvent = null
        muteEvent = null
        bookmarkEvent = null
        pinEvent = null
        _pinned.value = emptyList()
        emojiListEvent = null
        _emojis.value = emptyMap()
        _bookmarks.value = emptyList()
        _following.value = emptySet()
        _muted.value = emptySet()
        _mutes.value = com.example.btcwidget.nostr.core.MuteRules()
        trust = null
        followsLists = null
        _stats.value = emptyMap()
    }

    /** True when follows are known (from relays or the cache), so the home feed can start. */
    val hasCachedAccount: Boolean get() = contactsEvent != null

    /** Loads the user's relay list, contacts, mutes and profile from relays and indexers. */
    suspend fun loadAccount() {
        val me = pubkey ?: return
        val events = queryEverywhere(
            listOf(Filter(authors = listOf(me), kinds = listOf(Kind.METADATA, Kind.CONTACTS, Kind.MUTE_LIST, Kind.BOOKMARKS, Kind.PINNED, Kind.RELAY_LIST, MEDIA_SERVER_LIST, Kind.EMOJI_LIST))),
            timeoutMs = 6_000
        )
        applyAccountEvents(events, fromRelays = true)
        saveAccount()
        scope.launch { runCatching { loadTrust() } }
    }

    private fun applyAccountEvents(events: List<Event>, fromRelays: Boolean) {
        fun latest(kind: Int) = events.filter { it.kind == kind }.maxByOrNull { it.createdAt }
        latest(Kind.METADATA)?.let { remember(it) }
        latest(Kind.CONTACTS)?.let { if (it.createdAt >= (contactsEvent?.createdAt ?: 0)) { contactsEvent = it; _following.value = it.tagValues("p").toSet() } }
        latest(Kind.PINNED)?.let { if (it.createdAt >= (pinEvent?.createdAt ?: 0)) { pinEvent = it; _pinned.value = it.tagValues("e") } }
        latest(Kind.EMOJI_LIST)?.let { if (it.createdAt >= (emojiListEvent?.createdAt ?: 0)) { emojiListEvent = it; _emojis.value = com.example.btcwidget.nostr.core.CustomEmoji.of(it) } }
        latest(Kind.BOOKMARKS)?.let { if (it.createdAt >= (bookmarkEvent?.createdAt ?: 0)) { bookmarkEvent = it; _bookmarks.value = it.tagValues("e").reversed() } }
        latest(Kind.MUTE_LIST)?.let { if (it.createdAt >= (muteEvent?.createdAt ?: 0)) { muteEvent = it; _muted.value = it.tagValues("p").toSet(); _mutes.value = com.example.btcwidget.nostr.core.MuteRules.of(it) } }
        latest(MEDIA_SERVER_LIST)?.let { mediaServerList = it; mediaServers = com.example.btcwidget.nostr.core.Blossom.serversFrom(it) }
        latest(Kind.RELAY_LIST)?.let { list ->
            relayList = list
            if (NostrAccountStore.relays(context) == null) {
                // NIP-65: add the user's relays to the defaults (a slow personal relay alone would slow everything).
                val urls = list.tags.filter { it.size >= 2 && it[0] == "r" }.mapNotNull { normalizeRelayUrl(it[1]) }
                if (urls.isNotEmpty()) pool.setRelays((urls.take(5) + RelayPool.DEFAULT_RELAYS).distinct().take(8))
            }
        }
        if (fromRelays) accountFresh = true
    }

    private fun saveAccount() {
        val me = pubkey ?: return
        val events = listOfNotNull(profileEvents[me], contactsEvent, muteEvent, bookmarkEvent, pinEvent, relayList, mediaServerList, emojiListEvent)
        scope.launch(Dispatchers.IO) { cache.saveEvents(ACCOUNT_CACHE, events) }
        events.forEach { store(it) }
    }

    /** The home timeline saved last time, for an instant first screen. */
    suspend fun cachedHome(): List<FeedItem> = withContext(Dispatchers.IO) {
        cache.loadFeed(HOME_CACHE).filter { !hidden(it.note) }.also { items ->
            items.forEach { remember(it.note) }
        }
    }

    fun saveHome(items: List<FeedItem>) {
        scope.launch(Dispatchers.IO) { cache.saveFeed(HOME_CACHE, items.take(60)) }
    }

    fun setRelays(urls: List<String>?) {
        val clean = urls?.mapNotNull { normalizeRelayUrl(it) }?.distinct()?.takeIf { it.isNotEmpty() }
        NostrAccountStore.setRelays(context, clean)
        pool.setRelays(clean ?: RelayPool.DEFAULT_RELAYS)
    }

    // ---- Reading ----

    /** Home timeline: notes and reposts from people the user follows (or themselves), newest first. */
    suspend fun homeFeed(until: Long? = null, limit: Int = 40): List<FeedItem> {
        val me = pubkey ?: return emptyList()
        // Following nobody yet: show your own notes plus #bitcoin, so the timeline isn't empty.
        if (_following.value.isEmpty()) {
            return timeline(
                Filter(authors = listOf(me), kinds = listOf(Kind.TEXT_NOTE, Kind.REPOST, Kind.POLL), until = until, limit = limit),
                Filter(kinds = listOf(Kind.TEXT_NOTE), tags = mapOf("t" to listOf("bitcoin")), until = until, limit = limit)
            )
        }
        val authors = (_following.value + me).toList()
        return authoredTimeline(authors) { Filter(authors = it, kinds = listOf(Kind.TEXT_NOTE, Kind.REPOST, Kind.POLL), until = until, limit = limit) }
    }

    /** A profile's notes and reposts. */
    suspend fun profileFeed(pubkey: String, until: Long? = null, limit: Int = 30): List<FeedItem> =
        authoredTimeline(listOf(pubkey)) { Filter(authors = it, kinds = listOf(Kind.TEXT_NOTE, Kind.REPOST, Kind.POLL), until = until, limit = limit) }

    // ---- Outbox model (NIP-65): read people from the relays they write to ----


    /** Fetches relay lists (from the indexers and our relays) for authors we don't know yet. */
    private suspend fun loadWriteRelays(authors: Collection<String>) {
        val missing = authors.distinct().filter { !writeRelays.containsKey(it) }
        if (missing.isEmpty()) return
        coroutineScope {
            missing.chunked(150).map { chunk ->
                async { queryEverywhere(listOf(Filter(authors = chunk, kinds = listOf(Kind.RELAY_LIST))), timeoutMs = 4_000) }
            }.awaitAll()
        }.flatten().groupBy { it.pubkey }.forEach { (pk, lists) ->
            val latest = lists.maxBy { it.createdAt }
            relayListEvents[pk] = latest
            writeRelays[pk] = com.example.btcwidget.nostr.core.Outbox.writeRelays(latest)
            store(latest)
        }
        missing.forEach { writeRelays.putIfAbsent(it, emptyList()) }
    }

    /**
     * Notes by [authors], from our relays and, at the same time, from the relays each of
     * them writes to (at most 12 extra relays, two per person). Merged into one timeline.
     */
    private suspend fun authoredTimeline(authors: List<String>, filterFor: (List<String>) -> Filter): List<FeedItem> =
        buildTimeline(authoredEvents(authors, filterFor = filterFor))

    /** Events by [authors] from our relays, their write relays (outbox) and any [hints]. */
    private suspend fun authoredEvents(authors: List<String>, hints: List<String> = emptyList(), filterFor: (List<String>) -> Filter): List<Event> =
        coroutineScope {
            val ours = async { pool.query(listOf(filterFor(authors))) }
            val theirs = async {
                runCatching {
                    loadWriteRelays(authors)
                    val plan = com.example.btcwidget.nostr.core.Outbox.plan(authors, writeRelays, avoid = pool.urls)
                    if (plan.isEmpty()) return@runCatching emptyList()
                    outbox.addRelays(plan.keys)
                    plan.map { (url, who) ->
                        async { outbox.query(listOf(filterFor(who)), timeoutMs = 5_000, only = setOf(url)) }
                    }.awaitAll().flatten()
                }.getOrDefault(emptyList())
            }
            val hinted = async {
                val urls = hints.filter { it.startsWith("wss://") && it !in pool.urls }.take(3).toSet()
                if (urls.isEmpty()) emptyList() else runCatching {
                    outbox.addRelays(urls)
                    outbox.query(listOf(filterFor(authors)), timeoutMs = 5_000, only = urls)
                }.getOrDefault(emptyList())
            }
            val stored = async(Dispatchers.IO) { events.query(listOf(filterFor(authors))) }
            (ours.await() + theirs.await() + hinted.await() + stored.await()).distinctBy { it.id }
        }

    // ---- Long-form articles (NIP-23) ----

    /** The latest version of the article at [address]. */
    suspend fun article(address: com.example.btcwidget.nostr.core.Nip19.Address): com.example.btcwidget.nostr.core.Article? {
        val events = authoredEvents(listOf(address.pubkey), address.relays) {
            Filter(authors = it, kinds = listOf(address.kind), tags = mapOf("d" to listOf(address.identifier)), limit = 3)
        }
        val latest = events.filter { it.pubkey == address.pubkey && it.firstTag("d")?.get(1).orEmpty() == address.identifier }
            .maxByOrNull { it.createdAt } ?: return null
        enrich(listOf(latest.pubkey), listOf(latest.id))
        return com.example.btcwidget.nostr.core.Article.of(latest)
    }

    /** Articles [pubkey] has written, newest first (one per identifier). */
    suspend fun articlesBy(pubkey: String): List<com.example.btcwidget.nostr.core.Article> =
        authoredEvents(listOf(pubkey)) { Filter(authors = it, kinds = listOf(Kind.ARTICLE), limit = 30) }
            .filter { it.pubkey == pubkey }
            .groupBy { it.firstTag("d")?.get(1).orEmpty() }
            .mapNotNull { (_, versions) -> com.example.btcwidget.nostr.core.Article.of(versions.maxBy { it.createdAt }) }
            .sortedByDescending { it.publishedAt }

    /**
     * Recent notes from everyone, for discovering people: from all our relays, or only from
     * [relay] (which needn't be one of ours).
     */
    suspend fun globalFeed(relay: String? = null, until: Long? = null): List<FeedItem> {
        val filter = Filter(kinds = listOf(Kind.TEXT_NOTE, Kind.POLL), until = until, limit = 40)
        if (relay == null) return timeline(filter)
        outbox.addRelays(listOf(relay))
        return buildTimeline(outbox.query(listOf(filter), timeoutMs = 6_000, only = setOf(relay)))
    }

    /** Notes tagged with [hashtag]. */
    suspend fun hashtagFeed(hashtag: String, until: Long? = null): List<FeedItem> =
        timeline(Filter(kinds = listOf(Kind.TEXT_NOTE), tags = mapOf("t" to listOf(hashtag.lowercase())), until = until, limit = 40))

    /**
     * Notes for a timeline. Returns as soon as the notes are in; names, avatars and counts
     * fill in afterwards (their flows update the screen), instead of holding it up.
     */
    private suspend fun timeline(vararg filters: Filter, source: RelayPool = pool): List<FeedItem> =
        buildTimeline(source.query(filters.toList()))

    private suspend fun buildTimeline(events: List<Event>): List<FeedItem> {
        // Reposts carry the note as JSON; fetch the few that don't in one request.
        val embedded = events.filter { it.kind == Kind.REPOST }.associateWith { r ->
            runCatching { Event.fromJson(r.content) }.getOrNull()?.takeIf { it.kind == Kind.TEXT_NOTE && it.verify() }
        }
        val missing = embedded.filterValues { it == null }.keys.mapNotNull { it.tagValues("e").firstOrNull() }
            .filter { !notes.containsKey(it) }.distinct()
        if (missing.isNotEmpty()) {
            pool.query(listOf(Filter(ids = missing)), 3_000, graceMs = 500).forEach { remember(it) }
        }
        events.filter { it.kind == Kind.REPOST }.forEach { store(it) }
        val items = events.mapNotNull { e ->
            when (e.kind) {
                Kind.TEXT_NOTE, Kind.POLL -> FeedItem(e)
                Kind.REPOST -> (embedded[e] ?: e.tagValues("e").firstOrNull()?.let { notes[it] })
                    ?.let { FeedItem(it, repostedBy = e.pubkey, sortTime = e.createdAt) }
                else -> null
            }
        }.filter { !hidden(it.note) && it.repostedBy !in _muted.value && (it.repostedBy != null || trusted(it.note.pubkey)) }
            .distinctBy { it.key }
            .sortedByDescending { it.sortTime }
        items.forEach { remember(it.note) }
        enrich(items.flatMap { listOfNotNull(it.note.pubkey, it.repostedBy) }, items.map { it.note.id })
        return items
    }

    /** Loads [pubkeys]' profiles and [noteIds]' counts in the background. */
    private fun enrich(pubkeys: List<String>, noteIds: List<String>) {
        scope.launch { runCatching { loadProfiles(pubkeys) } }
        scope.launch { runCatching { loadStats(noteIds) } }
    }

    /** A note by id, from cache or relays. */
    suspend fun note(id: String): Event? {
        notes[id]?.let { return it }
        withContext(Dispatchers.IO) { events.query(listOf(Filter(ids = listOf(id)))) }.firstOrNull()?.let {
            remember(it, save = false)
            return it
        }
        return pool.query(listOf(Filter(ids = listOf(id))), 4_000, graceMs = 400).firstOrNull()?.also {
            remember(it)
            enrich(listOf(it.pubkey), emptyList())
        }
    }

    fun cachedNote(id: String): Event? = notes[id]

    /** Hashtags starting with [prefix] in notes seen so far, most used first (for autocomplete). */
    fun hashtagSuggestions(prefix: String, limit: Int = 5): List<String> {
        val p = prefix.lowercase()
        return notes.values.asSequence()
            .flatMap { it.tagValues("t").asSequence() }
            .map { it.lowercase() }
            .filter { it.startsWith(p) && it != p && it.length <= 40 }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .take(limit).map { it.key }
            .ifEmpty { DEFAULT_TAGS.filter { it.startsWith(p) && it != p }.take(limit) }
    }

    /** A thread around [noteId]: ancestors (oldest first), the note, and direct and nested replies. */
    suspend fun thread(noteId: String): Thread? {
        val focus = note(noteId) ?: return null
        val ancestors = ArrayList<Event>()
        var cursor = ThreadRef.of(focus).parent
        while (cursor != null && ancestors.size < 20) {
            val parent = note(cursor) ?: break
            ancestors.add(0, parent)
            cursor = ThreadRef.of(parent).parent
        }
        val rootId = ThreadRef.of(focus).root ?: focus.id
        val replyFilter = Filter(kinds = listOf(Kind.TEXT_NOTE), tags = mapOf("e" to listOf(rootId, focus.id).distinct()), limit = 200)
        val stored = withContext(Dispatchers.IO) { events.query(listOf(replyFilter)) }
        val replies = (pool.query(listOf(replyFilter)) + stored).distinctBy { it.id }
            .filter { !hidden(it, threadMuteApplies = false) && trusted(it.pubkey) }
        replies.forEach { remember(it) }
        val below = ReplyTree.flatten(focus.id, replies)
        val all = ancestors + focus + below.map { it.event }
        enrich(all.map { it.pubkey }, all.map { it.id })
        return Thread(ancestors, focus, below)
    }

    /** [replies] holds every reply below [focus], nested, in reading order. */
    data class Thread(val ancestors: List<Event>, val focus: Event, val replies: List<ReplyTree.Row>)

    /** Replies, mentions, reposts, likes and zaps involving the user. */
    suspend fun notifications(limit: Int = 100): List<NostrNotification> {
        val me = pubkey ?: return emptyList()
        val events = pool.query(listOf(Filter(
            kinds = listOf(Kind.TEXT_NOTE, Kind.REPOST, Kind.REACTION, Kind.ZAP_RECEIPT),
            tags = mapOf("p" to listOf(me)), limit = limit
        )))
        val out = events.mapNotNull { e ->
            when (e.kind) {
                Kind.TEXT_NOTE -> if (e.pubkey == me) null else {
                    val thread = ThreadRef.of(e)
                    val parent = thread.parent
                    val type = if (parent != null) NostrNotification.Type.REPLY else NostrNotification.Type.MENTION
                    remember(e)
                    NostrNotification(e, type, e.pubkey, parent)
                }
                Kind.REPOST -> if (e.pubkey == me) null else NostrNotification(e, NostrNotification.Type.REPOST, e.pubkey, e.tagValues("e").firstOrNull())
                Kind.REACTION -> if (e.pubkey == me || e.content == "-") null
                    else NostrNotification(e, NostrNotification.Type.LIKE, e.pubkey, e.tagValues("e").lastOrNull())
                Kind.ZAP_RECEIPT -> Zaps.parseReceipt(e)?.let { r ->
                    NostrNotification(e, NostrNotification.Type.ZAP, r.sender ?: e.pubkey, r.noteId, r.amountMsat)
                }
                else -> null
            }
        }.filter { it.actor !in _muted.value && !hidden(it.event) && trusted(it.actor) }
        enrich(out.map { it.actor }, emptyList())
        return out.sortedByDescending { it.event.createdAt }
    }

    // ---- Search ----

    /** Relays that support NIP-50 search; opened on first search. */
    private val searchPool by lazy { RelayPool().also { it.setRelays(SEARCH_RELAYS) } }

    /** What a search box entry points straight at, if anything. */
    sealed interface Direct {
        data class ProfileTarget(val pubkey: String) : Direct
        data class NoteTarget(val id: String) : Direct
        data class HashtagTarget(val tag: String) : Direct
        data class ArticleTarget(val address: com.example.btcwidget.nostr.core.Nip19.Address) : Direct
    }

    /** npub/nprofile/note/nevent/hex, "#tag" or a NIP-05 address; null for free text. */
    suspend fun resolve(query: String): Direct? {
        val q = query.trim()
        when (val e = com.example.btcwidget.nostr.core.Nip19.decode(q)) {
            is com.example.btcwidget.nostr.core.Nip19.Pubkey -> return Direct.ProfileTarget(e.hex)
            is com.example.btcwidget.nostr.core.Nip19.Note -> return Direct.NoteTarget(e.id)
            is com.example.btcwidget.nostr.core.Nip19.Address -> if (e.kind == Kind.ARTICLE) return Direct.ArticleTarget(e)
            else -> Unit
        }
        if (q.startsWith("#") && q.length > 1 && q.drop(1).all { it.isLetterOrDigit() || it == '_' }) return Direct.HashtagTarget(q.drop(1))
        if (com.example.btcwidget.nostr.core.Nip05.looks(q) && q.contains('@')) {
            val url = com.example.btcwidget.nostr.core.Nip05.url(q) ?: return null
            val name = com.example.btcwidget.nostr.core.Nip05.parts(q)!!.first
            val pubkey = runCatching { withContext(Dispatchers.IO) { com.example.btcwidget.nostr.core.Nip05.pubkeyFrom(httpGet(url), name) } }.getOrNull()
            if (pubkey != null) return Direct.ProfileTarget(pubkey)
        }
        return null
    }

    /** People matching [query] (NIP-50 on profile metadata), most followed first per the relay. */
    suspend fun searchPeople(query: String): List<Profile> {
        val events = searchPool.query(listOf(Filter(kinds = listOf(Kind.METADATA), search = query.trim(), limit = 30)), 6_000)
        events.forEach { remember(it) }
        val wanted = events.map { it.pubkey }.distinct()
        return wanted.mapNotNull { _profiles.value[it] }.filter { it.pubkey !in _muted.value && trusted(it.pubkey) }
    }

    /** Notes matching [query] (NIP-50). */
    suspend fun searchNotes(query: String): List<FeedItem> =
        timeline(Filter(kinds = listOf(Kind.TEXT_NOTE), search = query.trim(), limit = 40), source = searchPool)

    // ---- Nostr Wallet Connect (NIP-47) ----

    private val _nwc = MutableStateFlow(NostrAccountStore.nwc(context)?.let { com.example.btcwidget.nostr.core.Nwc.parse(it) })
    /** The connected wallet, or null: zaps then open a wallet app instead. */
    val nwc: StateFlow<com.example.btcwidget.nostr.core.Nwc.Connection?> = _nwc.asStateFlow()

    /** Saves a `nostr+walletconnect://` string (encrypted). Throws if it isn't one. */
    fun connectNwc(uri: String) {
        val c = com.example.btcwidget.nostr.core.Nwc.parse(uri) ?: throw IllegalArgumentException("That isn't a nostr+walletconnect:// link")
        NostrAccountStore.setNwc(context, uri.trim())
        _nwc.value = c
    }

    fun disconnectNwc() {
        NostrAccountStore.setNwc(context, null)
        _nwc.value = null
    }

    /**
     * Pays [invoice] through the connected wallet: sends the request to the wallet's relay
     * and waits up to 45 s for its answer. Returns the preimage; throws the wallet's error.
     */
    suspend fun payWithNwc(invoice: String): String {
        val c = _nwc.value ?: error("No wallet connected")
        val nwc = com.example.btcwidget.nostr.core.Nwc
        val walletPool = RelayPool().also { it.setRelays(c.relays) }
        try {
            // NIP-44 if the wallet says it supports it, else NIP-04.
            val info = walletPool.query(listOf(Filter(authors = listOf(c.walletPubkey), kinds = listOf(nwc.INFO), limit = 1)), 4_000).firstOrNull()
            val request = nwc.payInvoiceRequest(c, invoice, nwc.supportsNip44(info))
            val answer = kotlinx.coroutines.CompletableDeferred<Event>()
            val stop = walletPool.subscribe(
                listOf(Filter(authors = listOf(c.walletPubkey), kinds = listOf(nwc.RESPONSE), tags = mapOf("e" to listOf(request.id)))),
                { e, _ -> answer.complete(e) }
            )
            try {
                val sent = walletPool.publish(request)
                if (sent.none { it.accepted }) error(sent.firstOrNull { it.message.isNotBlank() }?.message ?: "The wallet's relay didn't accept the request")
                val response = kotlinx.coroutines.withTimeoutOrNull(45_000) { answer.await() } ?: error("The wallet didn't answer in time")
                return nwc.parseResponse(c, response).getOrThrow()
            } finally {
                stop()
            }
        } finally {
            walletPool.closeAll()
        }
    }

    // ---- Link previews ----

    /** url → preview (null = page has none); newest 300 kept. */
    private val previews = object : LinkedHashMap<String, com.example.btcwidget.nostr.core.LinkPreview?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, com.example.btcwidget.nostr.core.LinkPreview?>?) = size > 300
    }
    private val previewLock = kotlinx.coroutines.sync.Mutex()

    /**
     * The card for [url], fetched once: only HTML pages, only the first 256 KB, and only
     * https. Null if the page has no title or can't be read.
     */
    suspend fun linkPreview(url: String): com.example.btcwidget.nostr.core.LinkPreview? {
        if (!url.startsWith("https://")) return null
        previewLock.withLock { if (previews.containsKey(url)) return previews[url] }
        val preview = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url).header("Accept", "text/html").header("User-Agent", "Mozilla/5.0 (Linux; Android) Blockface").build()
                RelayPool.defaultClient.newCall(request).execute().use { r ->
                    val type = r.header("Content-Type").orEmpty()
                    if (!r.isSuccessful || !type.contains("html", ignoreCase = true)) return@use null
                    val source = r.body.source()
                    source.request(256 * 1024L)
                    val html = source.buffer.readUtf8(minOf(source.buffer.size, 256 * 1024L))
                    com.example.btcwidget.nostr.core.LinkPreview.parse(html, r.request.url.toString())
                }
            }.getOrNull()
        }
        previewLock.withLock { previews[url] = preview }
        return preview
    }

    // ---- Who engaged with a note ----

    /** Who reposted, liked (with their reaction) and zapped a note. */
    data class Engagement(
        val reposts: List<String>,
        val likes: List<Pair<String, String>>,
        val zaps: List<Zaps.Receipt>
    )

    suspend fun engagement(noteId: String): Engagement {
        val events = pool.query(listOf(Filter(kinds = listOf(Kind.REPOST, Kind.REACTION, Kind.ZAP_RECEIPT), tags = mapOf("e" to listOf(noteId)), limit = 500)), 6_000)
            .filter { it.pubkey !in _muted.value }
        val reposts = events.filter { it.kind == Kind.REPOST && it.tagValues("e").firstOrNull() == noteId }
            .sortedByDescending { it.createdAt }.map { it.pubkey }.distinct()
        // NIP-25: the reaction targets the last "e" tag; "-" is a dislike, not a like.
        val likes = events.filter { it.kind == Kind.REACTION && it.tagValues("e").lastOrNull() == noteId && it.content != "-" }
            .sortedByDescending { it.createdAt }.distinctBy { it.pubkey }
            .map { it.pubkey to it.content.ifEmpty { "+" } }
        val zaps = events.filter { it.kind == Kind.ZAP_RECEIPT }.mapNotNull { Zaps.parseReceipt(it) }
            .filter { it.noteId == noteId }.sortedByDescending { it.amountMsat }
        loadProfiles(reposts + likes.map { it.first } + zaps.mapNotNull { it.sender })
        return Engagement(reposts, likes, zaps)
    }

    // ---- NIP-05 verification ----

    private val _nip05 = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    /** "pubkey|name@domain" → whether the domain's nostr.json really maps the name to that key. */
    val nip05Verified: StateFlow<Map<String, Boolean>> = _nip05.asStateFlow()
    private val nip05InFlight = ConcurrentHashMap.newKeySet<String>()

    /**
     * Checks [identifier] against https://domain/.well-known/nostr.json once per session.
     * Until it's confirmed, the app doesn't show the address as verified.
     */
    fun verifyNip05(pubkey: String, identifier: String) {
        val key = "$pubkey|$identifier"
        if (_nip05.value.containsKey(key) || !nip05InFlight.add(key)) return
        scope.launch(Dispatchers.IO) {
            val ok = runCatching {
                val url = com.example.btcwidget.nostr.core.Nip05.url(identifier) ?: return@runCatching false
                val name = com.example.btcwidget.nostr.core.Nip05.parts(identifier)?.first ?: return@runCatching false
                com.example.btcwidget.nostr.core.Nip05.pubkeyFrom(httpGet(url), name) == pubkey
            }.getOrDefault(false)
            _nip05.update { it + (key to ok) }
            nip05InFlight.remove(key)
        }
    }

    /** Pubkeys whose profiles are being fetched, so concurrent screens don't ask twice. */
    private val profilesInFlight = ConcurrentHashMap.newKeySet<String>()

    /** Fetches kind-0 profiles not yet known, from the user's relays and the indexers at once. */
    suspend fun loadProfiles(pubkeys: Collection<String>) {
        val missing = pubkeys.distinct().filter { !profileEvents.containsKey(it) && profilesInFlight.add(it) }
        if (missing.isEmpty()) return
        try {
            coroutineScope {
                missing.chunked(100).map { chunk ->
                    async { queryEverywhere(listOf(Filter(authors = chunk, kinds = listOf(Kind.METADATA)))).forEach { remember(it) } }
                }.awaitAll()
            }
        } finally {
            profilesInFlight.removeAll(missing.toSet())
        }
    }

    /** Fetches a profile even if cached (for the profile screen). */
    suspend fun refreshProfile(pubkey: String): Profile? {
        queryEverywhere(listOf(Filter(authors = listOf(pubkey), kinds = listOf(Kind.METADATA), limit = 1))).forEach { remember(it) }
        return _profiles.value[pubkey]
    }

    /** Whom [pubkey] follows (their latest contact list). */
    suspend fun followingOf(pubkey: String): Set<String> =
        queryEverywhere(listOf(Filter(authors = listOf(pubkey), kinds = listOf(Kind.CONTACTS), limit = 1)))
            .maxByOrNull { it.createdAt }?.tagValues("p")?.toSet().orEmpty()

    /**
     * Who follows [pubkey]: authors whose latest contact list includes them, from the
     * user's relays and the indexers. Capped by what relays return ([limit] lists), so
     * for popular accounts this is a lower bound.
     */
    suspend fun followersOf(pubkey: String, limit: Int = 500): List<String> =
        queryEverywhere(listOf(Filter(kinds = listOf(Kind.CONTACTS), tags = mapOf("p" to listOf(pubkey)), limit = limit)), timeoutMs = 6_000)
            .groupBy { it.pubkey }
            .mapNotNull { (author, lists) -> author.takeIf { lists.maxBy { it.createdAt }.tagValues("p").contains(pubkey) } }
            .filter { it !in _muted.value }

    /** Counts likes, reposts, replies and zaps for [noteIds]; marks the user's own. */
    suspend fun loadStats(noteIds: List<String>) {
        if (noteIds.isEmpty()) return
        val me = pubkey
        val events = coroutineScope {
            noteIds.distinct().chunked(40).map { chunk ->
                async { pool.query(listOf(Filter(kinds = listOf(Kind.TEXT_NOTE, Kind.REPOST, Kind.REACTION, Kind.ZAP_RECEIPT), tags = mapOf("e" to chunk), limit = 500)), 5_000) }
            }.awaitAll().flatten().distinctBy { it.id }
        }
        val update = noteIds.associateWith { NoteStats() }.toMutableMap()
        for (e in events) {
            val targets = e.tagValues("e").filter { it in update }
            val target = when (e.kind) {
                Kind.REACTION -> targets.lastOrNull()
                Kind.TEXT_NOTE -> ThreadRef.of(e).parent?.takeIf { it in update }
                else -> targets.firstOrNull()
            } ?: continue
            val s = update.getValue(target)
            update[target] = when (e.kind) {
                Kind.TEXT_NOTE -> s.copy(replies = s.replies + 1)
                Kind.REPOST -> s.copy(reposts = s.reposts + 1, repostedByMe = s.repostedByMe || e.pubkey == me)
                Kind.REACTION -> if (e.content == "-") s else s.copy(
                    likes = s.likes + 1, likedByMe = s.likedByMe || e.pubkey == me,
                    myReaction = if (e.pubkey == me) e.content else s.myReaction
                )
                Kind.ZAP_RECEIPT -> s.copy(zapMsat = s.zapMsat + (Zaps.parseReceipt(e)?.amountMsat ?: 0))
                else -> s
            }
        }
        _stats.update { old -> old + update.mapValues { (id, new) -> mergeStats(old[id], new) } }
    }

    /** Keeps optimistic local actions (a like just sent) when relays haven't caught up. */
    private fun mergeStats(old: NoteStats?, new: NoteStats): NoteStats = if (old == null) new else new.copy(
        likes = maxOf(new.likes, if (old.likedByMe && !new.likedByMe) new.likes + 1 else new.likes),
        likedByMe = new.likedByMe || old.likedByMe,
        reposts = if (old.repostedByMe && !new.repostedByMe) new.reposts + 1 else new.reposts,
        repostedByMe = new.repostedByMe || old.repostedByMe,
        myReaction = new.myReaction ?: old.myReaction
    )

    private fun remember(e: Event, save: Boolean = true) {
        when (e.kind) {
            Kind.METADATA -> {
                val old = profileEvents[e.pubkey]
                if (old == null || e.createdAt > old.createdAt) {
                    profileEvents[e.pubkey] = e
                    Profile.fromEvent(e)?.let { p -> _profiles.update { it + (e.pubkey to p) } }
                    if (save) store(e)
                }
            }
            else -> {
                val isNew = notes.put(e.id, e) == null
                if (save && isNew) store(e)
            }
        }
    }

    /** Queues [e] for the local database; written in batches a moment later. */
    internal fun store(e: Event) {
        toStore.add(e)
        if (storeJob?.isActive == true) return
        storeJob = scope.launch(Dispatchers.IO) {
            delay(1_500)
            while (toStore.isNotEmpty()) {
                events.insert(generateSequence { toStore.poll() }.take(2_000).toList())
            }
        }
    }

    // ---- Writing ----

    internal fun requireSigner(): NostrSigner = signer ?: throw IllegalStateException("Log in to Nostr first")

    /** Signs and publishes; succeeds if at least one relay accepted it. */
    suspend fun publish(unsigned: UnsignedEvent): Event {
        val signed = requireSigner().sign(unsigned)
        val results = pool.publish(signed)
        if (results.none { it.accepted }) {
            throw IllegalStateException(results.firstOrNull { it.message.isNotBlank() }?.message ?: "No relay accepted the post")
        }
        remember(signed)
        return signed
    }

    suspend fun post(content: String, extraTags: List<List<String>> = emptyList()): Event =
        publish(Events.note(requireMe(), content, extraTags))
    suspend fun reply(content: String, parent: Event, extraTags: List<List<String>> = emptyList()): Event =
        publish(Events.reply(requireMe(), content, parent, relayHint(), extraTags))
    suspend fun quote(content: String, quoted: Event, extraTags: List<List<String>> = emptyList()): Event =
        publish(Events.quote(requireMe(), content, quoted, relayHint(), extraTags))

    // ---- Media servers (Blossom, kind 10063) ----

    /** The user's media servers, or empty when they use the default. */
    fun mediaServerUrls(): List<String> = mediaServers

    /** Publishes the media server list; empty goes back to the default server. */
    suspend fun setMediaServers(urls: List<String>) {
        val me = requireMe()
        val clean = urls.map { it.trim().trimEnd('/') }.filter { it.startsWith("https://") }.distinct()
        val event = publish(UnsignedEvent(me, Events.now(), MEDIA_SERVER_LIST, clean.map { listOf("server", it) }, ""))
        mediaServerList = event
        mediaServers = com.example.btcwidget.nostr.core.Blossom.serversFrom(event)
        saveAccount()
    }

    // ---- Polls (NIP-88) ----

    /** Votes on [poll], from the relays it names and ours. */
    suspend fun pollTally(poll: com.example.btcwidget.nostr.core.Poll): com.example.btcwidget.nostr.core.Poll.Tally {
        val filter = Filter(kinds = listOf(Kind.POLL_RESPONSE), tags = mapOf("e" to listOf(poll.event.id)), limit = 500)
        val responses = coroutineScope {
            val ours = async { pool.query(listOf(filter), 5_000, graceMs = 700) }
            val theirs = async {
                val urls = poll.relays.filter { it !in pool.urls }.take(4).toSet()
                if (urls.isEmpty()) emptyList() else runCatching {
                    outbox.addRelays(urls)
                    outbox.query(listOf(filter), timeoutMs = 5_000, only = urls)
                }.getOrDefault(emptyList())
            }
            (ours.await() + theirs.await()).distinctBy { it.id }.filter { it.verify() }
        }
        return poll.tally(responses, pubkey)
    }

    /** Votes for [optionIds]; sent to our relays and the ones the poll reads from. */
    suspend fun vote(poll: com.example.btcwidget.nostr.core.Poll, optionIds: List<String>) {
        val signed = requireSigner().sign(com.example.btcwidget.nostr.core.Poll.response(requireMe(), poll, optionIds))
        val extra = poll.relays.filter { it !in pool.urls }.take(4).toSet()
        val results = coroutineScope {
            val ours = async { pool.publish(signed) }
            val theirs = async {
                if (extra.isEmpty()) emptyList() else runCatching { outbox.addRelays(extra); outbox.publish(signed, only = extra) }.getOrDefault(emptyList())
            }
            ours.await() + theirs.await()
        }
        if (results.none { it.accepted }) throw IllegalStateException("No relay accepted the vote")
    }

    /** Publishes a poll; votes are collected on our relays. */
    suspend fun createPoll(question: String, options: List<String>, multipleChoice: Boolean, endsAt: Long?): Event =
        publish(com.example.btcwidget.nostr.core.Poll.create(requireMe(), question, options, multipleChoice, endsAt, pool.urls.take(4).toList()))

    /**
     * Uploads [image] to the user's Blossom servers (or the default), trying up to three.
     * Signing the upload permission goes through the user's signer.
     */
    suspend fun uploadImage(image: PreparedImage): com.example.btcwidget.nostr.core.Blossom.Blob {
        val sha = com.example.btcwidget.nostr.core.Blossom.sha256Hex(image.bytes)
        return uploadBlob(sha, image.bytes.size.toLong()) { image.bytes.toRequestBody(image.mime.toMediaType()) }
    }

    /** Uploads already-encrypted bytes (a photo for a private message). */
    suspend fun uploadEncrypted(bytes: ByteArray): com.example.btcwidget.nostr.core.Blossom.Blob {
        val sha = com.example.btcwidget.nostr.core.Blossom.sha256Hex(bytes)
        return uploadBlob(sha, bytes.size.toLong()) { bytes.toRequestBody("application/octet-stream".toMediaType()) }
    }

    /** Uploads a video as-is; the cached copy is deleted afterwards. */
    suspend fun uploadVideo(video: PreparedVideo): com.example.btcwidget.nostr.core.Blossom.Blob = try {
        uploadBlob(video.sha256, video.size) { video.file.asRequestBody(video.mime.toMediaType()) }
    } finally {
        video.file.delete()
    }

    /**
     * Uploads a file to the user's Blossom servers (or the default), trying up to three.
     * Signing the upload permission goes through the user's signer.
     */
    private suspend fun uploadBlob(sha: String, size: Long, body: () -> okhttp3.RequestBody): com.example.btcwidget.nostr.core.Blossom.Blob {
        val me = requireMe()
        val blossom = com.example.btcwidget.nostr.core.Blossom
        val auth = requireSigner().sign(blossom.uploadAuth(me, sha, size))
        val header = blossom.authHeader(auth)
        val servers = mediaServers.ifEmpty { listOf(blossom.DEFAULT_SERVER) }.take(3)
        var failure: Exception? = null
        for (server in servers) {
            try {
                return withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(blossom.uploadUrl(server))
                        .header("Authorization", header)
                        .put(body())
                        .build()
                    RelayPool.uploadClient.newCall(request).execute().use { r ->
                        if (!r.isSuccessful) {
                            error(r.header("X-Reason") ?: "${server.removePrefix("https://")} refused the upload (HTTP ${r.code})")
                        }
                        blossom.parseUpload(r.body.string(), sha).getOrThrow()
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
            }
        }
        throw failure ?: IllegalStateException("Upload failed")
    }

    suspend fun repost(note: Event) {
        publish(Events.repost(requireMe(), note, relayHint()))
        bump(note.id) { it.copy(reposts = it.reposts + 1, repostedByMe = true) }
    }

    suspend fun like(note: Event) = react(note, "+")

    /** Reacts with "+" (a like), an emoji, or ":code:" with [emojiUrl] (NIP-30). One reaction per note. */
    suspend fun react(note: Event, content: String, emojiUrl: String? = null) {
        if (_stats.value[note.id]?.likedByMe == true) return
        bump(note.id) { it.copy(likes = it.likes + 1, likedByMe = true, myReaction = content) }
        runCatching { publish(Events.reaction(requireMe(), note, content, emojiUrl)) }
            .onFailure { bump(note.id) { s -> s.copy(likes = (s.likes - 1).coerceAtLeast(0), likedByMe = false, myReaction = null) }; throw it }
    }


    suspend fun setFollowing(target: String, follow: Boolean) {
        val me = requireMe()
        // Never overwrite a contact list we haven't read from relays this session: a missing or
        // stale list (e.g. from the cache) would wipe follows made in other apps.
        if (!accountFresh) loadAccount()
        if (contactsEvent == null && _following.value.isNotEmpty()) error("Couldn't load your follow list; try again")
        val event = publish(Events.contacts(me, contactsEvent, target, follow))
        contactsEvent = event
        _following.value = event.tagValues("p").toSet()
        saveAccount()
    }

    /** Mutes (or unmutes) [target] with a public NIP-51 mute list. */
    suspend fun setMuted(target: String, mute: Boolean) = setMuteEntry("p", target, mute)

    /**
     * Adds or removes one entry ("p" account, "word", "t" hashtag or "e" thread) on the mute
     * list, keeping everything else on it (including other apps' private entries).
     */
    suspend fun setMuteEntry(type: String, value: String, mute: Boolean) {
        val me = requireMe()
        if (!accountFresh) loadAccount()
        val v = if (type == "word" || type == "t") value.trim().lowercase().removePrefix("#") else value
        if (v.isEmpty()) return
        val others = muteEvent?.tags.orEmpty().filterNot { it.size >= 2 && it[0] == type && it[1].equals(v, ignoreCase = true) }
        val tags = if (mute) others + listOf(listOf(type, v)) else others
        val event = publish(UnsignedEvent(me, Events.now(), Kind.MUTE_LIST, tags, muteEvent?.content.orEmpty()))
        muteEvent = event
        _muted.value = event.tagValues("p").toSet()
        _mutes.value = com.example.btcwidget.nostr.core.MuteRules.of(event)
        saveAccount()
    }

    /** Removes every muted conversation ("e" entries) in one update. */
    suspend fun unmuteAllThreads() {
        val me = requireMe()
        if (!accountFresh) loadAccount()
        val tags = muteEvent?.tags.orEmpty().filterNot { it.size >= 2 && it[0] == "e" }
        val event = publish(UnsignedEvent(me, Events.now(), Kind.MUTE_LIST, tags, muteEvent?.content.orEmpty()))
        muteEvent = event
        _mutes.value = com.example.btcwidget.nostr.core.MuteRules.of(event)
        saveAccount()
    }

    /** Hidden by the mute list. [threadMuteApplies] is false inside a thread the user opened on purpose. */
    private fun hidden(e: Event, threadMuteApplies: Boolean = true): Boolean {
        if (e.pubkey in _muted.value) return true
        val rules = _mutes.value
        return if (threadMuteApplies) rules.hides(e) else rules.copy(threads = emptySet()).hides(e)
    }

    /** False for strangers when the web-of-trust filter is on (always true until it has loaded). */
    fun trusted(pubkey: String): Boolean {
        if (pubkey == this.pubkey) return true
        val net = trust ?: return true
        return pubkey in _following.value || net.contains(pubkey)
    }

    /**
     * Loads the web of trust: the user's follows and whom they follow (up to 400 follows'
     * lists), when the filter is on. Cheap to call again.
     */
    suspend fun loadTrust() {
        val me = pubkey ?: return
        if (!NostrAccountStore.wotFilter(context)) { trust = null; return }
        if (trust != null) return
        val lists = followsContactLists()
        trust = com.example.btcwidget.nostr.core.TrustNetwork(me, _following.value, lists.values.flatten())
    }

    /** Whom each followed account follows (up to 400 of them), loaded once per session. */
    @Volatile private var followsLists: Map<String, List<String>>? = null

    private suspend fun followsContactLists(): Map<String, List<String>> {
        followsLists?.let { return it }
        val follows = _following.value.toList()
        val lists = coroutineScope {
            follows.take(400).chunked(100).map { chunk ->
                async { queryEverywhere(listOf(Filter(authors = chunk, kinds = listOf(Kind.CONTACTS))), timeoutMs = 6_000) }
            }.awaitAll().flatten()
        }
        val out = lists.groupBy { it.pubkey }.mapValues { (_, versions) -> versions.maxBy { it.createdAt }.tagValues("p") }
        if (out.isNotEmpty()) followsLists = out
        return out
    }

    /** People followed by several of the people you follow, most shared first, with that count. */
    suspend fun followSuggestions(): List<Pair<Profile, Int>> {
        val me = pubkey ?: return emptyList()
        if (_following.value.isEmpty()) return emptyList()
        val ranked = com.example.btcwidget.nostr.core.FollowSuggestions.rank(me, _following.value, followsContactLists(), exclude = _muted.value)
        loadProfiles(ranked.map { it.first })
        return ranked.mapNotNull { (pk, n) -> _profiles.value[pk]?.let { it to n } }
    }

    // ---- Badges (NIP-58) ----

    /** Badges [pubkey] shows on their profile, verified against the issuers' awards. */
    suspend fun badgesOf(pubkey: String): List<com.example.btcwidget.nostr.core.Badges.Definition> {
        val profile = queryEverywhere(listOf(Filter(authors = listOf(pubkey), kinds = listOf(com.example.btcwidget.nostr.core.Badges.PROFILE), tags = mapOf("d" to listOf(com.example.btcwidget.nostr.core.Badges.PROFILE_D)), limit = 2)))
            .maxByOrNull { it.createdAt }
        val shown = com.example.btcwidget.nostr.core.Badges.shown(profile).take(30)
        if (shown.isEmpty()) return emptyList()
        val (awards, definitions) = coroutineScope {
            val a = async { queryEverywhere(listOf(Filter(ids = shown.map { it.second }))) }
            val d = async {
                queryEverywhere(listOf(Filter(
                    authors = shown.map { it.first.issuer }.distinct(), kinds = listOf(com.example.btcwidget.nostr.core.Badges.DEFINITION),
                    tags = mapOf("d" to shown.map { it.first.id }.distinct())
                )))
            }
            a.await() to d.await()
        }
        val awardById = awards.associateBy { it.id }
        val defs = definitions.mapNotNull { com.example.btcwidget.nostr.core.Badges.Definition.of(it) }.groupBy { it.ref }.mapValues { it.value.first() }
        return shown.mapNotNull { (ref, awardId) ->
            val award = awardById[awardId] ?: return@mapNotNull null
            if (!com.example.btcwidget.nostr.core.Badges.isValidAward(award, ref, pubkey)) null else defs[ref]
        }
    }

    fun setWotFilter(on: Boolean) {
        NostrAccountStore.setWotFilter(context, on)
        if (!on) trust = null
    }

    /**
     * Adds or removes [note] from the user's public bookmark list (NIP-51 kind 10003),
     * keeping everything else on it (other apps' entries, hashtags, links).
     */
    suspend fun setBookmarked(note: Event, on: Boolean) {
        val me = requireMe()
        if (!accountFresh) loadAccount()
        val others = bookmarkEvent?.tags.orEmpty().filterNot { it.size >= 2 && it[0] == "e" && it[1] == note.id }
        val tags = if (on) others + listOf(listOf("e", note.id, relayHint())) else others
        val event = publish(UnsignedEvent(me, Events.now(), Kind.BOOKMARKS, tags, bookmarkEvent?.content.orEmpty()))
        bookmarkEvent = event
        _bookmarks.value = event.tagValues("e").reversed()
        saveAccount()
    }

    /** Pins [note] to the top of the user's profile (first in their kind-10001 list), or unpins it. */
    suspend fun setPinned(note: Event, on: Boolean) {
        val me = requireMe()
        require(note.pubkey == me) { "Only your own notes can be pinned" }
        if (!accountFresh) loadAccount()
        val others = pinEvent?.tags.orEmpty().filterNot { it.size >= 2 && it[0] == "e" && it[1] == note.id }
        val tags = if (on) listOf(listOf("e", note.id, relayHint())) + others else others
        val event = publish(UnsignedEvent(me, Events.now(), Kind.PINNED, tags, pinEvent?.content.orEmpty()))
        pinEvent = event
        _pinned.value = event.tagValues("e")
        saveAccount()
    }

    /** The note [pubkey] pinned first, if any. */
    suspend fun pinnedNoteOf(pubkey: String): Event? {
        val ids = if (pubkey == this.pubkey) _pinned.value
            else queryEverywhere(listOf(Filter(authors = listOf(pubkey), kinds = listOf(Kind.PINNED), limit = 1)))
                .maxByOrNull { it.createdAt }?.tagValues("e").orEmpty()
        val id = ids.firstOrNull() ?: return null
        return note(id)?.takeIf { it.pubkey == pubkey }
    }

    /** The bookmarked notes, in bookmark order (newest first). */
    suspend fun bookmarkedNotes(): List<FeedItem> {
        val ids = _bookmarks.value
        if (ids.isEmpty()) return emptyList()
        val missing = ids.filter { !notes.containsKey(it) }
        if (missing.isNotEmpty()) pool.query(listOf(Filter(ids = missing)), 5_000).forEach { remember(it) }
        val items = ids.mapNotNull { notes[it] }.map { FeedItem(it) }
        enrich(items.map { it.note.pubkey }, items.map { it.note.id })
        return items
    }

    /**
     * New notes and reposts from [authors] after [since], delivered as they arrive (for the
     * "new posts" pill). Call the returned function to stop.
     */
    fun liveFeed(authors: List<String>, since: Long, onItem: (FeedItem) -> Unit): () -> Unit =
        pool.subscribe(listOf(Filter(authors = authors, kinds = listOf(Kind.TEXT_NOTE, Kind.REPOST, Kind.POLL), since = since + 1)), { e, _ ->
            if (hidden(e)) return@subscribe
            val item = when (e.kind) {
                Kind.TEXT_NOTE -> FeedItem(e)
                else -> runCatching { Event.fromJson(e.content) }.getOrNull()
                    ?.takeIf { it.kind == Kind.TEXT_NOTE && it.verify() }
                    ?.let { FeedItem(it, repostedBy = e.pubkey, sortTime = e.createdAt) }
            } ?: return@subscribe
            remember(item.note)
            onItem(item)
            enrich(listOfNotNull(item.note.pubkey, item.repostedBy), listOf(item.note.id))
        })

    /** Whom the home feed is built from (follows plus the user). */
    fun homeAuthors(): List<String> = pubkey?.let { (_following.value + it).toList() }.orEmpty()

    /** Requests deletion of the user's own note (NIP-09). */
    suspend fun delete(note: Event) {
        publish(Events.deletion(requireMe(), listOf(note.id)))
        notes.remove(note.id)
    }

    suspend fun updateProfile(profile: Profile) {
        val me = requireMe()
        publish(Events.metadata(me, profile, profileEvents[me]))
    }

    /** NIP-56 report of [note] (e.g. "spam", "illegal"), as Google Play requires for user content. */
    suspend fun report(note: Event, reason: String) {
        publish(UnsignedEvent(requireMe(), Events.now(), 1984, listOf(listOf("e", note.id, reason), listOf("p", note.pubkey)), ""))
    }

    // ---- Zaps ----

    /**
     * Gets a Lightning invoice zapping [amountSat] to the author of [note] (or to [recipient]).
     * The caller hands it to a wallet app.
     */
    suspend fun zapInvoice(recipient: String, amountSat: Long, note: Event? = null, comment: String = ""): String = withContext(Dispatchers.IO) {
        val me = requireMe()
        val profile = _profiles.value[recipient] ?: refreshProfile(recipient)
            ?: error("Couldn't load their profile")
        val endpoint = Zaps.payEndpoint(profile) ?: error("They haven't set up a Lightning address")
        val info = Zaps.parsePayInfo(httpGet(endpoint)) ?: error("Their Lightning address didn't answer")
        val msat = amountSat * 1000
        if (msat < info.minSendableMsat || msat > info.maxSendableMsat) {
            error("Amount must be between ${info.minSendableMsat / 1000} and ${info.maxSendableMsat / 1000} sats")
        }
        val lnurl = Zaps.encodeLnurl(endpoint)
        if (!info.allowsNostr) {
            // Plain LNURL payment: still pays, but no zap receipt will appear.
            return@withContext Zaps.parseInvoice(httpGet(info.callback + (if ('?' in info.callback) "&" else "?") + "amount=$msat")).getOrThrow()
        }
        val request = requireSigner().sign(
            Zaps.request(me, recipient, msat, lnurl, pool.urls.toList(), note?.id, note?.kind, comment)
        )
        Zaps.parseInvoice(httpGet(Zaps.callbackUrl(info, msat, request, lnurl))).getOrThrow()
    }

    private fun httpGet(url: String): String {
        RelayPool.defaultClient.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            return r.body.string()
        }
    }

    private fun requireMe(): String = pubkey ?: throw IllegalStateException("Log in to Nostr first")
    private fun relayHint(): String = pool.urls.firstOrNull().orEmpty()

    private fun bump(id: String, change: (NoteStats) -> NoteStats) =
        _stats.update { it + (id to change(it[id] ?: NoteStats())) }

    companion object {
        /** Relays that index profiles, follow lists and relay lists (also used by Amethyst). */
        val INDEXER_RELAYS = listOf("wss://purplepag.es", "wss://user.kindpag.es", "wss://indexer.coracle.social")

        private const val PROFILES_CACHE = "profiles"
        private const val ACCOUNT_CACHE = "account"
        private const val HOME_CACHE = "home"
        private const val RELAY_LIST_CACHE = "relay_lists"

        /** BUD-03 list of the user's Blossom media servers. */
        const val MEDIA_SERVER_LIST = 10063

        /** Public relays that implement NIP-50 search. */
        val SEARCH_RELAYS = listOf("wss://relay.nostr.band", "wss://search.nos.today")

        @Volatile private var instance: NostrRepository? = null

        fun get(context: Context): NostrRepository =
            instance ?: synchronized(this) {
                instance ?: NostrRepository(context.applicationContext).also { instance = it }
            }
    }
}

/** Suggested when no seen note has a matching hashtag yet. */
private val DEFAULT_TAGS = listOf("bitcoin", "nostr", "lightning", "zap", "photography", "art", "music", "news", "plebchain", "grownostr", "thailand", "food", "travel", "memes", "introductions")
