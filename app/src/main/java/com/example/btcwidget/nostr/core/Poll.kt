package com.example.btcwidget.nostr.core

/** A poll (NIP-88, kind 1068): the question is the content, choices are "option" tags. */
data class Poll(
    val event: Event,
    val options: List<Option>,
    val multipleChoice: Boolean,
    val endsAt: Long?,
    val relays: List<String>
) {
    data class Option(val id: String, val label: String)

    val question get() = event.content
    fun isClosed(now: Long = System.currentTimeMillis() / 1000) = endsAt != null && now >= endsAt

    /** Votes per option id, and who voted for what (each person's latest response counts). */
    data class Tally(val counts: Map<String, Int>, val voters: Int, val mine: Set<String>) {
        fun count(optionId: String) = counts[optionId] ?: 0
    }

    /**
     * Counts [responses] (kind 1018): one per person, their latest before the poll ended,
     * with unknown options dropped and only the first option kept for a single-choice poll.
     */
    fun tally(responses: List<Event>, me: String? = null): Tally {
        val known = options.map { it.id }.toSet()
        val latest = responses
            .filter { it.kind == Kind.POLL_RESPONSE && it.tagValues("e").contains(event.id) }
            .filter { endsAt == null || it.createdAt <= endsAt }
            .groupBy { it.pubkey }
            .mapValues { (_, rs) -> rs.maxBy { it.createdAt } }
        val choices = latest.mapValues { (_, r) ->
            val picked = r.tagValues("response").filter { it in known }.distinct()
            if (multipleChoice) picked else picked.take(1)
        }.filterValues { it.isNotEmpty() }
        val counts = choices.values.flatten().groupingBy { it }.eachCount()
        return Tally(counts, choices.size, me?.let { choices[it]?.toSet() }.orEmpty())
    }

    companion object {
        fun of(event: Event): Poll? {
            if (event.kind != Kind.POLL) return null
            val options = event.tags.filter { it.size >= 3 && it[0] == "option" && it[1].isNotBlank() }
                .map { Option(it[1], it[2]) }.distinctBy { it.id }
            if (options.size < 2) return null
            return Poll(
                event = event,
                options = options,
                multipleChoice = event.firstTag("polltype")?.get(1) == "multiplechoice",
                endsAt = event.firstTag("endsAt")?.get(1)?.toLongOrNull(),
                relays = event.tagValues("relay").filter { it.startsWith("wss://") || it.startsWith("ws://") }
            )
        }

        /** A new poll by [pubkey]; responses are read from [relays]. */
        fun create(
            pubkey: String, question: String, options: List<String>, multipleChoice: Boolean,
            endsAt: Long?, relays: List<String>, now: Long = Events.now()
        ): UnsignedEvent {
            val labels = options.map { it.trim() }.filter { it.isNotEmpty() }
            require(labels.size >= 2) { "A poll needs at least two options" }
            val tags = ArrayList<List<String>>()
            labels.forEachIndexed { i, label -> tags += listOf("option", "opt$i", label) }
            relays.forEach { tags += listOf("relay", it) }
            tags += listOf("polltype", if (multipleChoice) "multiplechoice" else "singlechoice")
            endsAt?.let { tags += listOf("endsAt", it.toString()) }
            return UnsignedEvent(pubkey, now, Kind.POLL, tags, question.trim())
        }

        /** A vote (kind 1018) for [optionIds] in [poll]. */
        fun response(pubkey: String, poll: Poll, optionIds: List<String>, now: Long = Events.now()): UnsignedEvent {
            val ids = if (poll.multipleChoice) optionIds.distinct() else optionIds.take(1)
            require(ids.isNotEmpty()) { "Pick an option" }
            val tags = listOf(listOf("e", poll.event.id)) + ids.map { listOf("response", it) }
            return UnsignedEvent(pubkey, now, Kind.POLL_RESPONSE, tags, "")
        }
    }
}
