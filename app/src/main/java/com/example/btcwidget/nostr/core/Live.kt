package com.example.btcwidget.nostr.core

/**
 * Live streams (NIP-53): a kind-30311 event describes the stream (title, picture, the HLS
 * "streaming" URL, status), updated by the host while it runs; viewers chat with kind-1311
 * messages pointing at it with an "a" tag.
 */
object LiveStreams {
    const val KIND = 30311
    const val CHAT = 1311

    /** Treated as ended when the host hasn't updated a "live" stream for this long (as NIP-53 says). */
    private const val STALE_SECONDS = 3_600L

    data class Live(
        val event: Event,
        /** "30311:<host pubkey>:<d>", what chat messages point at. */
        val address: String,
        /** Who's streaming: the participant marked "Host", else the event's author. */
        val host: String,
        val title: String,
        val summary: String?,
        val image: String?,
        val streaming: String?,
        /** The video of a stream that has ended, to watch again (NIP-53 "recording"). */
        val recording: String?,
        val status: Status,
        val starts: Long?,
        val viewers: Int?,
        val relays: List<String>
    ) {
        val isLive get() = status == Status.LIVE
    }

    enum class Status { LIVE, PLANNED, ENDED }

    fun parse(e: Event, now: Long = Events.now()): Live? {
        if (e.kind != KIND) return null
        val d = e.firstTag("d")?.get(1) ?: return null
        fun tag(name: String) = e.firstTag(name)?.getOrNull(1)?.trim()?.ifBlank { null }
        val declared = when (tag("status")?.lowercase()) {
            "live" -> Status.LIVE
            "planned" -> Status.PLANNED
            else -> Status.ENDED
        }
        val status = if (declared == Status.LIVE && now - e.createdAt > STALE_SECONDS) Status.ENDED else declared
        val host = e.tags.firstOrNull { it.size >= 4 && it[0] == "p" && it[3].equals("host", ignoreCase = true) && Hex.isKey(it[1]) }?.get(1) ?: e.pubkey
        val relays = e.tags.filter { it.firstOrNull() == "relays" }.flatMap { it.drop(1) }.mapNotNull { normalizeRelayUrl(it) }.distinct()
        return Live(
            event = e, address = "$KIND:${e.pubkey}:$d", host = host,
            title = tag("title") ?: "Live stream", summary = tag("summary"), image = tag("image"),
            streaming = tag("streaming")?.takeIf { it.startsWith("https://") || it.startsWith("http://") },
            recording = tag("recording")?.takeIf { it.startsWith("https://") || it.startsWith("http://") },
            status = status, starts = tag("starts")?.toLongOrNull(),
            viewers = tag("current_participants")?.toIntOrNull(), relays = relays.take(5)
        )
    }

    /** Newest version of each stream, live ones first (most watched first), then upcoming ones. */
    fun latest(events: List<Event>, now: Long = Events.now()): List<Live> =
        events.filter { it.kind == KIND }.groupBy { it.pubkey to it.firstTag("d")?.get(1) }.values
            .mapNotNull { parse(it.maxBy { e -> e.createdAt }, now) }
            .sortedWith(compareBy<Live>({ it.status.ordinal }, { -(it.viewers ?: 0) }, { it.starts ?: Long.MAX_VALUE }))

    /** A chat message for [live]. */
    fun chat(me: String, live: Live, text: String, now: Long = Events.now()) = UnsignedEvent(
        me, now, CHAT, listOf(listOf("a", live.address, live.relays.firstOrNull().orEmpty(), "root")), text
    )

    /** The stream a chat message belongs to. */
    fun chatOf(e: Event): String? = e.tags.firstOrNull { it.size >= 2 && it[0] == "a" && it[1].startsWith("$KIND:") }?.get(1)
}
