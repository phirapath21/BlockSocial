package com.example.btcwidget.nostr.core

/**
 * The outbox model (NIP-65): people publish to their own "write" relays, so to read
 * someone's notes, ask the relays they write to, rather than only your own.
 */
object Outbox {
    /** Relays [list] (kind 10002) says its author writes to: "r" tags marked write or unmarked. */
    fun writeRelays(list: Event?): List<String> =
        list?.tags.orEmpty()
            .filter { it.size >= 2 && it[0] == "r" && (it.getOrNull(2).isNullOrEmpty() || it[2] == "write") }
            .mapNotNull { normalizeRelayUrl(it[1]) }
            .distinct()

    /**
     * Chooses relays to read [authors] from: up to [perAuthor] of each author's write
     * relays, favouring relays already picked for others (fewer connections), at most
     * [maxRelays] in all. Returns relay → authors. Authors without a known list, or whose
     * relays didn't make the cut, are left for the caller's default relays.
     */
    fun plan(
        authors: Collection<String>,
        writeRelays: Map<String, List<String>>,
        perAuthor: Int = 2,
        maxRelays: Int = 12,
        avoid: Set<String> = emptySet()
    ): Map<String, List<String>> {
        // How many of these authors write to each relay: popular relays first.
        val popularity = HashMap<String, Int>()
        authors.forEach { a -> writeRelays[a].orEmpty().forEach { r -> if (r !in avoid) popularity[r] = (popularity[r] ?: 0) + 1 } }
        val chosen = LinkedHashMap<String, MutableList<String>>()
        // Authors with fewer relays first: they have the fewest options.
        for (a in authors.distinct().sortedBy { writeRelays[it]?.size ?: Int.MAX_VALUE }) {
            val options = writeRelays[a].orEmpty().filter { it !in avoid }
                .sortedWith(compareByDescending<String> { it in chosen }.thenByDescending { popularity[it] ?: 0 })
            var taken = 0
            for (r in options) {
                if (taken >= perAuthor) break
                if (r !in chosen && chosen.size >= maxRelays) continue
                chosen.getOrPut(r) { mutableListOf() }.add(a)
                taken++
            }
        }
        return chosen
    }
}
