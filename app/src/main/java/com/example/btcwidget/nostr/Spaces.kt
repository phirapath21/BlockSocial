package com.example.btcwidget.nostr

import com.example.btcwidget.nostr.core.Communities
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Filter
import com.example.btcwidget.nostr.core.LiveStreams
import com.example.btcwidget.nostr.core.PublicChat
import com.example.btcwidget.nostr.core.RelayGroups
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Places to talk beyond the timeline: communities (NIP-72), public chat channels (NIP-28)
 * and relay-based groups (NIP-29).
 */
class Spaces internal constructor(private val repo: NostrRepository) {

    // ---- Communities ----

    private var joinedList: Event? = null
    private val _joined = MutableStateFlow<Set<String>>(emptySet())
    /** Coordinates of the communities the user joined (kind 10004). */
    val joined: StateFlow<Set<String>> = _joined.asStateFlow()

    private val definitions = ConcurrentHashMap<String, Communities.Community>()

    /** Joined communities first, then recently active ones. */
    suspend fun communities(): List<Communities.Community> = coroutineScope {
        val me = repo.pubkey
        val list = async {
            if (me == null) null else repo.queryEverywhere(listOf(Filter(authors = listOf(me), kinds = listOf(Communities.LIST), limit = 2))).maxByOrNull { it.createdAt }
        }
        val recent = async { repo.queryEverywhere(listOf(Filter(kinds = listOf(Communities.DEFINITION), limit = 80)), timeoutMs = 6_000) }
        list.await()?.let { if (it.createdAt >= (joinedList?.createdAt ?: 0)) { joinedList = it; _joined.value = it.tagValues("a").toSet() } }
        val joinedCoords = _joined.value
        val missing = joinedCoords.filter { c -> recent.await().none { coordinate(it) == c } }.mapNotNull { parseCoordinate(it) }
        val extra = if (missing.isEmpty()) emptyList() else repo.queryEverywhere(listOf(Filter(
            authors = missing.map { it.first }.distinct(), kinds = listOf(Communities.DEFINITION), tags = mapOf("d" to missing.map { it.second }.distinct())
        )))
        val all = (recent.await() + extra).groupBy { coordinate(it) }.values.mapNotNull { v -> Communities.of(v.maxBy { it.createdAt }) }
            .filter { it.owner !in repo.muted.value }
        all.forEach { definitions[it.coordinate] = it }
        repo.loadProfiles(all.map { it.owner })
        all.sortedWith(compareBy({ it.coordinate !in joinedCoords }, { -it.event.createdAt }))
    }

    suspend fun community(coordinate: String): Communities.Community? {
        definitions[coordinate]?.let { return it }
        val (owner, id) = parseCoordinate(coordinate) ?: return null
        val e = repo.queryEverywhere(listOf(Filter(authors = listOf(owner), kinds = listOf(Communities.DEFINITION), tags = mapOf("d" to listOf(id)))))
            .maxByOrNull { it.createdAt } ?: return null
        return Communities.of(e)?.also { definitions[coordinate] = it }
    }

    /** Posts in [community], newest first: approved ones, or all with [includePending]. */
    suspend fun posts(community: Communities.Community, includePending: Boolean, until: Long? = null): List<FeedItem> {
        val coord = community.coordinate
        val filters = listOf(
            Filter(kinds = listOf(Communities.POST, 1), tags = mapOf("A" to listOf(coord)), until = until, limit = 60),
            Filter(kinds = listOf(Communities.POST, 1), tags = mapOf("a" to listOf(coord)), until = until, limit = 60),
            Filter(kinds = listOf(Communities.APPROVAL), tags = mapOf("a" to listOf(coord)), until = until, limit = 200)
        )
        val events = (repo.queryEverywhere(filters, timeoutMs = 6_000) + fromRelays(community.relays, filters)).distinctBy { it.id }
        val posts = events.filter { it.kind != Communities.APPROVAL && Communities.communityOf(it) == coord }
            .filter { it.pubkey !in repo.muted.value && repo.trusted(it.pubkey) }
        val approved = Communities.approved(community, posts, events.filter { it.kind == Communities.APPROVAL })
        val shown = posts.filter { includePending || it.id in approved }.sortedByDescending { it.createdAt }
        repo.loadProfiles(shown.map { it.pubkey })
        shown.forEach { repo.store(it) }
        return shown.map { FeedItem(it) }
    }

    suspend fun post(community: Communities.Community, text: String): Event {
        val me = repo.pubkey ?: error("Log in to Nostr first")
        val signed = repo.publish(Communities.post(me, community, text.trim()))
        sendTo(community.relays, signed)
        return signed
    }

    suspend fun setJoined(community: Communities.Community, join: Boolean) {
        val me = repo.pubkey ?: error("Log in to Nostr first")
        val event = repo.publish(Communities.listWith(me, joinedList, community.coordinate, join))
        joinedList = event
        _joined.value = event.tagValues("a").toSet()
    }

    // ---- Public chat channels (NIP-28) ----

    suspend fun channels(): List<PublicChat.Channel> {
        val creates = repo.queryEverywhere(listOf(Filter(kinds = listOf(PublicChat.CREATE), limit = 60)), timeoutMs = 6_000)
            .filter { it.pubkey !in repo.muted.value }
        if (creates.isEmpty()) return emptyList()
        val meta = repo.queryEverywhere(listOf(Filter(kinds = listOf(PublicChat.METADATA), tags = mapOf("e" to creates.map { it.id }), limit = 300)))
        return creates.sortedByDescending { it.createdAt }.mapNotNull { PublicChat.channel(it, meta) }
    }

    suspend fun channel(id: String): PublicChat.Channel? {
        val create = repo.note(id) ?: return null
        val meta = repo.queryEverywhere(listOf(Filter(kinds = listOf(PublicChat.METADATA), authors = listOf(create.pubkey), tags = mapOf("e" to listOf(id)))))
        return PublicChat.channel(create, meta)
    }

    /** Recent messages in a channel, then new ones as they arrive. Returns a stop function. */
    fun watchChannel(channel: PublicChat.Channel, onMessages: (List<Event>) -> Unit): () -> Unit {
        val filter = Filter(kinds = listOf(PublicChat.MESSAGE), tags = mapOf("e" to listOf(channel.id)), limit = 200)
        val seen = ConcurrentHashMap<String, Event>()
        fun emit() = onMessages(seen.values.filter { it.pubkey !in repo.muted.value && PublicChat.channelOf(it) == channel.id }.sortedBy { it.createdAt })
        val stop = repo.pool.subscribe(listOf(filter), { e, _ -> if (seen.putIfAbsent(e.id, e) == null) { repo.loadProfilesLater(listOf(e.pubkey)); emit() } }, { _, _ -> emit() })
        return stop
    }

    suspend fun sendToChannel(channel: PublicChat.Channel, text: String) {
        val me = repo.pubkey ?: error("Log in to Nostr first")
        val signed = repo.publish(PublicChat.message(me, channel.id, text.trim(), channel.relay ?: ""))
        sendTo(listOfNotNull(channel.relay), signed)
    }

    // ---- Live streams (NIP-53) ----

    /** Streams live now and coming up, from our relays and the indexers. */
    suspend fun liveStreams(): List<LiveStreams.Live> {
        val events = repo.queryEverywhere(listOf(Filter(kinds = listOf(LiveStreams.KIND), limit = 150)), timeoutMs = 6_000)
        return LiveStreams.latest(events)
            .filter { it.status != LiveStreams.Status.ENDED && it.host !in repo.muted.value && it.event.pubkey !in repo.muted.value }
            .also { list -> repo.loadProfilesLater(list.map { it.host }) }
    }

    /** One stream by its address ("30311:<pubkey>:<d>"), live or not. */
    suspend fun liveStream(address: String): LiveStreams.Live? {
        val parts = address.split(':', limit = 3).takeIf { it.size == 3 && it[0] == LiveStreams.KIND.toString() } ?: return null
        val events = repo.queryEverywhere(listOf(Filter(kinds = listOf(LiveStreams.KIND), authors = listOf(parts[1]), tags = mapOf("d" to listOf(parts[2])))), timeoutMs = 6_000)
        return LiveStreams.latest(events).firstOrNull()?.also { repo.loadProfilesLater(listOf(it.host)) }
    }

    /** Recent chat in [live], then new messages as they arrive (also on its own relays). Returns a stop function. */
    fun watchLiveChat(live: LiveStreams.Live, onMessages: (List<Event>) -> Unit): () -> Unit {
        val filter = Filter(kinds = listOf(LiveStreams.CHAT), tags = mapOf("a" to listOf(live.address)), limit = 200)
        val seen = ConcurrentHashMap<String, Event>()
        fun emit() = onMessages(seen.values.filter { it.pubkey !in repo.muted.value && LiveStreams.chatOf(it) == live.address }.sortedBy { it.createdAt })
        val onEvent = { e: Event, _: String -> if (seen.putIfAbsent(e.id, e) == null) { repo.loadProfilesLater(listOf(e.pubkey)); emit() } }
        if (live.relays.isNotEmpty()) repo.outbox.addRelays(live.relays)
        val stops = listOf(
            repo.pool.subscribe(listOf(filter), onEvent, { _, _ -> emit() }),
            if (live.relays.isNotEmpty()) repo.outbox.subscribe(listOf(filter), onEvent, { _, _ -> emit() }, only = live.relays.toSet()) else ({})
        )
        return { stops.forEach { it() } }
    }

    suspend fun sendLiveChat(live: LiveStreams.Live, text: String) {
        val me = repo.pubkey ?: error("Log in to Nostr first")
        val signed = repo.publish(LiveStreams.chat(me, live, text.trim()))
        sendTo(live.relays, signed)
    }

    // ---- Relay-based groups (NIP-29) ----

    private val groupPools = ConcurrentHashMap<String, RelayPool>()

    /** One connection per group relay; it answers AUTH with the user's key (only for groups opened). */
    private fun poolFor(relay: String): RelayPool = groupPools.getOrPut(relay) {
        RelayPool().also { pool ->
            pool.setRelays(listOf(relay))
            pool.authenticator = { url, challenge ->
                repo.pubkey?.let { me -> runCatching { repo.requireSigner().sign(RelayGroups.auth(me, url, challenge)) }.getOrNull() }
            }
        }
    }

    suspend fun group(address: RelayGroups.Address): RelayGroups.Group {
        val meta = poolFor(address.relay).query(listOf(Filter(kinds = listOf(RelayGroups.METADATA), tags = mapOf("d" to listOf(address.id)))), 6_000)
        return RelayGroups.metadata(address, meta)
    }

    fun watchGroup(address: RelayGroups.Address, onMessages: (List<Event>) -> Unit): () -> Unit {
        val seen = ConcurrentHashMap<String, Event>()
        fun emit() = onMessages(seen.values.filter { it.pubkey !in repo.muted.value }.sortedBy { it.createdAt })
        return poolFor(address.relay).subscribe(
            listOf(Filter(kinds = listOf(RelayGroups.CHAT), tags = mapOf("h" to listOf(address.id)), limit = 200)),
            { e, _ -> if (seen.putIfAbsent(e.id, e) == null) { repo.loadProfilesLater(listOf(e.pubkey)); emit() } },
            { _, _ -> emit() }
        )
    }

    /** Sends to the group's relay; true if it accepted (it may require joining first). */
    suspend fun sendToGroup(address: RelayGroups.Address, text: String): String? {
        val me = repo.pubkey ?: error("Log in to Nostr first")
        val signed = repo.requireSigner().sign(RelayGroups.chat(me, address, text.trim()))
        val result = poolFor(address.relay).publish(signed).firstOrNull()
        return if (result?.accepted == true) null else (result?.message?.takeIf { it.isNotBlank() } ?: "The group's relay didn't accept the message")
    }

    suspend fun joinGroup(address: RelayGroups.Address): String? {
        val me = repo.pubkey ?: error("Log in to Nostr first")
        val signed = repo.requireSigner().sign(RelayGroups.joinRequest(me, address))
        val result = poolFor(address.relay).publish(signed).firstOrNull()
        if (result?.accepted == true) {
            NostrAccountStore.setGroups(repo.appContext, (NostrAccountStore.groups(repo.appContext) + address.toString()).toSet())
            return null
        }
        return result?.message?.takeIf { it.isNotBlank() } ?: "The group's relay didn't accept the request"
    }

    /** Groups the user joined or opened, as "host'id". */
    fun savedGroups(): List<RelayGroups.Address> = NostrAccountStore.groups(repo.appContext).mapNotNull { RelayGroups.parse(it) }

    fun rememberGroup(address: RelayGroups.Address) =
        NostrAccountStore.setGroups(repo.appContext, (NostrAccountStore.groups(repo.appContext) + address.toString()).toSet())

    fun forgetGroup(address: RelayGroups.Address) =
        NostrAccountStore.setGroups(repo.appContext, NostrAccountStore.groups(repo.appContext) - address.toString())

    fun reset() {
        groupPools.values.forEach { it.closeAll() }
        groupPools.clear()
        definitions.clear()
        joinedList = null
        _joined.value = emptySet()
    }

    // ---- Helpers ----

    private fun coordinate(e: Event) = "${e.kind}:${e.pubkey}:${e.firstTag("d")?.get(1).orEmpty()}"

    private fun parseCoordinate(c: String): Pair<String, String>? {
        val parts = c.split(':', limit = 3)
        return if (parts.size == 3 && parts[0] == Communities.DEFINITION.toString() && com.example.btcwidget.nostr.core.Hex.isKey(parts[1])) parts[1] to parts[2] else null
    }

    /** Asks a community's own relays too (they may not be ours). */
    private suspend fun fromRelays(relays: List<String>, filters: List<Filter>): List<Event> {
        val extra = relays.filter { it !in repo.pool.urls }.take(3).toSet()
        if (extra.isEmpty()) return emptyList()
        return runCatching {
            repo.outbox.addRelays(extra)
            repo.outbox.query(filters, timeoutMs = 5_000, only = extra)
        }.getOrDefault(emptyList())
    }

    private suspend fun sendTo(relays: List<String>, event: Event) {
        val extra = relays.filter { it !in repo.pool.urls }.take(3).toSet()
        if (extra.isEmpty()) return
        runCatching { repo.outbox.addRelays(extra); repo.outbox.publish(event, only = extra) }
    }
}
