package com.example.btcwidget.nostr

import android.content.Context
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Events
import com.example.btcwidget.nostr.core.Filter
import com.example.btcwidget.nostr.core.Kind
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.nostr.core.ThreadRef
import com.example.btcwidget.nostr.core.UnsignedEvent
import com.example.btcwidget.nostr.core.Zaps
import com.example.btcwidget.nostr.core.normalizeRelayUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/** Likes, reposts, replies and zaps on a note, and whether the user did each. */
data class NoteStats(
    val replies: Int = 0,
    val reposts: Int = 0,
    val likes: Int = 0,
    val zapMsat: Long = 0,
    val likedByMe: Boolean = false,
    val repostedByMe: Boolean = false
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

    private val _muted = MutableStateFlow<Set<String>>(emptySet())
    /** Pubkeys the user muted (NIP-51 kind 10000); hidden everywhere. */
    val muted: StateFlow<Set<String>> = _muted.asStateFlow()

    private val notes = ConcurrentHashMap<String, Event>()
    private val profileEvents = ConcurrentHashMap<String, Event>()
    private var contactsEvent: Event? = null
    private var muteEvent: Event? = null
    private var signer: NostrSigner? = NostrAccountStore.signer(context)

    val pubkey: String? get() = _account.value?.pubkey

    init {
        pool.setRelays(NostrAccountStore.relays(context) ?: RelayPool.DEFAULT_RELAYS)
    }

    // ---- Account ----

    suspend fun loginWithAmber() {
        val (pubkey, pkg) = AmberSigner.connect()
        NostrAccountStore.saveAmber(context, pubkey, pkg)
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
        _account.value = NostrAccountStore.account(context)
        signer = NostrAccountStore.signer(context)
    }

    fun logout() {
        NostrAccountStore.clear(context)
        _account.value = null
        signer = null
        contactsEvent = null
        muteEvent = null
        _following.value = emptySet()
        _muted.value = emptySet()
        _stats.value = emptyMap()
    }

    /** Loads the user's relay list, contacts, mutes and profile. */
    suspend fun loadAccount() {
        val me = pubkey ?: return
        val events = pool.query(listOf(Filter(authors = listOf(me), kinds = listOf(Kind.METADATA, Kind.CONTACTS, Kind.MUTE_LIST, Kind.RELAY_LIST))))
        fun latest(kind: Int) = events.filter { it.kind == kind }.maxByOrNull { it.createdAt }
        latest(Kind.METADATA)?.let { remember(it) }
        latest(Kind.CONTACTS)?.let { contactsEvent = it; _following.value = it.tagValues("p").toSet() }
        latest(Kind.MUTE_LIST)?.let { muteEvent = it; _muted.value = it.tagValues("p").toSet() }
        if (NostrAccountStore.relays(context) == null) {
            latest(Kind.RELAY_LIST)?.let { list ->
                // NIP-65: use the user's relays (both read and write) when they have some.
                val urls = list.tags.filter { it.size >= 2 && it[0] == "r" }.mapNotNull { normalizeRelayUrl(it[1]) }.take(8)
                if (urls.isNotEmpty()) pool.setRelays(urls)
            }
        }
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
                Filter(authors = listOf(me), kinds = listOf(Kind.TEXT_NOTE, Kind.REPOST), until = until, limit = limit),
                Filter(kinds = listOf(Kind.TEXT_NOTE), tags = mapOf("t" to listOf("bitcoin")), until = until, limit = limit)
            )
        }
        val authors = (_following.value + me).toList()
        return timeline(Filter(authors = authors, kinds = listOf(Kind.TEXT_NOTE, Kind.REPOST), until = until, limit = limit))
    }

    /** A profile's notes and reposts. */
    suspend fun profileFeed(pubkey: String, until: Long? = null, limit: Int = 30): List<FeedItem> =
        timeline(Filter(authors = listOf(pubkey), kinds = listOf(Kind.TEXT_NOTE, Kind.REPOST), until = until, limit = limit))

    /** Notes tagged with [hashtag]. */
    suspend fun hashtagFeed(hashtag: String, until: Long? = null): List<FeedItem> =
        timeline(Filter(kinds = listOf(Kind.TEXT_NOTE), tags = mapOf("t" to listOf(hashtag.lowercase())), until = until, limit = 40))

    private suspend fun timeline(vararg filters: Filter): List<FeedItem> {
        val events = pool.query(filters.toList())
        val items = events.mapNotNull { e ->
            when (e.kind) {
                Kind.TEXT_NOTE -> FeedItem(e)
                Kind.REPOST -> repostedNote(e)?.let { FeedItem(it, repostedBy = e.pubkey, sortTime = e.createdAt) }
                else -> null
            }
        }.filter { it.note.pubkey !in _muted.value && it.repostedBy !in _muted.value }
            .distinctBy { it.key }
            .sortedByDescending { it.sortTime }
        items.forEach { remember(it.note) }
        loadProfiles(items.flatMap { listOfNotNull(it.note.pubkey, it.repostedBy) })
        loadStats(items.map { it.note.id })
        return items
    }

    /** The note inside a repost: embedded JSON if valid, else fetched by id. */
    private suspend fun repostedNote(repost: Event): Event? {
        runCatching { Event.fromJson(repost.content) }.getOrNull()?.takeIf { it.kind == Kind.TEXT_NOTE && it.verify() }?.let { return it }
        val id = repost.tagValues("e").firstOrNull() ?: return null
        return note(id)
    }

    /** A note by id, from cache or relays. */
    suspend fun note(id: String): Event? {
        notes[id]?.let { return it }
        return pool.query(listOf(Filter(ids = listOf(id))), 4_000).firstOrNull()?.also {
            remember(it)
            loadProfiles(listOf(it.pubkey))
        }
    }

    fun cachedNote(id: String): Event? = notes[id]

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
        val replies = pool.query(listOf(Filter(kinds = listOf(Kind.TEXT_NOTE), tags = mapOf("e" to listOf(rootId, focus.id).distinct()), limit = 200)))
            .filter { it.pubkey !in _muted.value }
        replies.forEach { remember(it) }
        val below = replies.filter { ThreadRef.of(it).parent == focus.id }.sortedBy { it.createdAt }
        val all = ancestors + focus + below
        loadProfiles(all.map { it.pubkey })
        loadStats(all.map { it.id })
        return Thread(ancestors, focus, below)
    }

    data class Thread(val ancestors: List<Event>, val focus: Event, val replies: List<Event>)

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
        }.filter { it.actor !in _muted.value }
        loadProfiles(out.map { it.actor })
        return out.sortedByDescending { it.event.createdAt }
    }

    /** Fetches kind-0 profiles not yet cached. */
    suspend fun loadProfiles(pubkeys: Collection<String>) {
        val missing = pubkeys.filter { !profileEvents.containsKey(it) }.distinct()
        if (missing.isEmpty()) return
        missing.chunked(150).forEach { chunk ->
            pool.query(listOf(Filter(authors = chunk, kinds = listOf(Kind.METADATA))), 4_000).forEach { remember(it) }
        }
    }

    /** Fetches a profile even if cached (for the profile screen). */
    suspend fun refreshProfile(pubkey: String): Profile? {
        pool.query(listOf(Filter(authors = listOf(pubkey), kinds = listOf(Kind.METADATA), limit = 1)), 4_000).forEach { remember(it) }
        return _profiles.value[pubkey]
    }

    /** Whom [pubkey] follows (their latest contact list). */
    suspend fun followingOf(pubkey: String): Set<String> =
        pool.query(listOf(Filter(authors = listOf(pubkey), kinds = listOf(Kind.CONTACTS), limit = 1)), 4_000)
            .maxByOrNull { it.createdAt }?.tagValues("p")?.toSet().orEmpty()

    /** Counts likes, reposts, replies and zaps for [noteIds]; marks the user's own. */
    suspend fun loadStats(noteIds: List<String>) {
        if (noteIds.isEmpty()) return
        val me = pubkey
        val events = noteIds.chunked(50).flatMap { chunk ->
            pool.query(listOf(Filter(kinds = listOf(Kind.TEXT_NOTE, Kind.REPOST, Kind.REACTION, Kind.ZAP_RECEIPT), tags = mapOf("e" to chunk), limit = 1000)), 5_000)
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
                Kind.REACTION -> if (e.content == "-") s else s.copy(likes = s.likes + 1, likedByMe = s.likedByMe || e.pubkey == me)
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
        repostedByMe = new.repostedByMe || old.repostedByMe
    )

    private fun remember(e: Event) {
        when (e.kind) {
            Kind.METADATA -> {
                val old = profileEvents[e.pubkey]
                if (old == null || e.createdAt > old.createdAt) {
                    profileEvents[e.pubkey] = e
                    Profile.fromEvent(e)?.let { p -> _profiles.update { it + (e.pubkey to p) } }
                }
            }
            else -> notes[e.id] = e
        }
    }

    // ---- Writing ----

    private fun requireSigner(): NostrSigner = signer ?: throw IllegalStateException("Log in to Nostr first")

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

    suspend fun post(content: String): Event = publish(Events.note(requireMe(), content))
    suspend fun reply(content: String, parent: Event): Event = publish(Events.reply(requireMe(), content, parent, relayHint()))
    suspend fun quote(content: String, quoted: Event): Event = publish(Events.quote(requireMe(), content, quoted, relayHint()))

    suspend fun repost(note: Event) {
        publish(Events.repost(requireMe(), note, relayHint()))
        bump(note.id) { it.copy(reposts = it.reposts + 1, repostedByMe = true) }
    }

    suspend fun like(note: Event) {
        if (_stats.value[note.id]?.likedByMe == true) return
        bump(note.id) { it.copy(likes = it.likes + 1, likedByMe = true) }
        runCatching { publish(Events.reaction(requireMe(), note)) }
            .onFailure { bump(note.id) { s -> s.copy(likes = (s.likes - 1).coerceAtLeast(0), likedByMe = false) }; throw it }
    }

    suspend fun setFollowing(target: String, follow: Boolean) {
        val me = requireMe()
        // Never overwrite a contact list we haven't loaded: that would wipe the user's follows.
        if (contactsEvent == null) loadAccount()
        val event = publish(Events.contacts(me, contactsEvent, target, follow))
        contactsEvent = event
        _following.value = event.tagValues("p").toSet()
    }

    /** Mutes (or unmutes) [target] with a public NIP-51 mute list. */
    suspend fun setMuted(target: String, mute: Boolean) {
        val me = requireMe()
        if (muteEvent == null) loadAccount()
        val others = muteEvent?.tags.orEmpty().filterNot { it.size >= 2 && it[0] == "p" && it[1] == target }
        val tags = if (mute) others + listOf(listOf("p", target)) else others
        val event = publish(UnsignedEvent(me, Events.now(), Kind.MUTE_LIST, tags, muteEvent?.content.orEmpty()))
        muteEvent = event
        _muted.value = event.tagValues("p").toSet()
    }

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
        @Volatile private var instance: NostrRepository? = null

        fun get(context: Context): NostrRepository =
            instance ?: synchronized(this) {
                instance ?: NostrRepository(context.applicationContext).also { instance = it }
            }
    }
}
