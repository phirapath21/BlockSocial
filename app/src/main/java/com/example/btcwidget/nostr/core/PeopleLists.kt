package com.example.btcwidget.nostr.core

/**
 * A list of people (NIP-51 "follow set", kind 30000), like X's Lists: a name and the people
 * in it, readable as a feed. Only the public members are read and changed; any private
 * (encrypted) members and other tags in an existing list are kept as they were.
 */
data class PeopleList(
    val owner: String,
    /** The list's "d" tag: which of the owner's lists this is. */
    val d: String,
    val title: String,
    val description: String?,
    val members: List<String>,
    val createdAt: Long,
    /** The event it came from, to keep what we don't edit; null for a new list. */
    val source: Event? = null
) {
    /** The NIP-01 address "30000:owner:d". */
    val address: String get() = "$KIND:$owner:$d"

    fun with(pubkey: String) = if (pubkey in members) this else copy(members = members + pubkey)
    fun without(pubkey: String) = copy(members = members - pubkey)

    /** The list as an event to sign, newer than the one it replaces. */
    fun toEvent(now: Long): UnsignedEvent {
        val kept = source?.tags.orEmpty().filter { it.isNotEmpty() && it[0] !in setOf("d", "title", "name", "description", "p") }
        val tags = buildList {
            add(listOf("d", d))
            add(listOf("title", title))
            description?.takeIf { it.isNotBlank() }?.let { add(listOf("description", it)) }
            addAll(kept)
            members.distinct().forEach { add(listOf("p", it)) }
        }
        return UnsignedEvent(owner, maxOf(now, (source?.createdAt ?: 0) + 1), KIND, tags, source?.content.orEmpty())
    }

    companion object {
        const val KIND = 30000

        /** Lists made by other apps for their own use, not by people. */
        private val HIDDEN = setOf("mute", "muted", "bookmark", "bookmarks", "pin", "pinned")

        fun parse(e: Event): PeopleList? {
            if (e.kind != KIND) return null
            val d = e.firstTag("d")?.get(1) ?: return null
            if (d.lowercase() in HIDDEN) return null
            val members = e.tags.filter { it.size >= 2 && it[0] == "p" && Hex.isKey(it[1]) }.map { it[1] }.distinct()
            // A deleted list is often republished empty with no title.
            val title = (e.firstTag("title")?.get(1) ?: e.firstTag("name")?.get(1))?.trim()?.ifBlank { null } ?: d.takeIf { members.isNotEmpty() } ?: return null
            return PeopleList(e.pubkey, d, title.take(80), e.firstTag("description")?.get(1), members, e.createdAt, e)
        }

        /** A new, empty list; [d] is random so names can repeat or change. */
        fun create(owner: String, title: String, random: java.util.Random = java.security.SecureRandom()): PeopleList {
            val bytes = ByteArray(8).also { random.nextBytes(it) }
            return PeopleList(owner, Hex.encode(bytes), title.trim().take(80), null, emptyList(), 0)
        }

        /** Newest version of each list, sorted by name. */
        fun latest(events: List<Event>): List<PeopleList> =
            events.filter { it.kind == KIND }.groupBy { it.pubkey to it.firstTag("d")?.get(1) }.values
                .mapNotNull { versions -> parse(versions.maxBy { it.createdAt }) }
                .sortedBy { it.title.lowercase() }

        /** Deletion request (NIP-09) for [list], by its address. */
        fun deletion(list: PeopleList, now: Long) = UnsignedEvent(
            list.owner, now, Kind.DELETION, listOf(listOf("a", list.address), listOf("k", KIND.toString())), ""
        )
    }
}
