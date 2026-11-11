package com.example.btcwidget.nostr.core

/**
 * What the user's mute list (NIP-51 kind 10000) hides: accounts ("p"), words ("word"),
 * hashtags ("t") and conversations ("e", by their root note).
 */
data class MuteRules(
    val pubkeys: Set<String> = emptySet(),
    val words: Set<String> = emptySet(),
    val hashtags: Set<String> = emptySet(),
    val threads: Set<String> = emptySet()
) {
    /** True when [event] should not be shown. */
    fun hides(event: Event): Boolean {
        if (event.pubkey in pubkeys) return true
        if (threads.isNotEmpty()) {
            val ref = ThreadRef.of(event)
            if (event.id in threads || ref.root in threads || ref.reply in threads) return true
        }
        if (hashtags.isNotEmpty() && event.tagValues("t").any { it.lowercase() in hashtags }) return true
        if (words.isNotEmpty()) {
            val text = event.content.lowercase()
            if (words.any { w -> containsWord(text, w) }) return true
        }
        return false
    }

    companion object {
        /** The rules from [list]'s public tags plus its decrypted [private] ones (NIP-51). */
        fun of(list: Event?, private: List<List<String>> = emptyList()): MuteRules {
            val tags = list?.tags.orEmpty() + private
            fun values(name: String) = tags.filter { it.size >= 2 && it[0] == name }.map { it[1].trim() }.filter { it.isNotEmpty() }
            return MuteRules(
                pubkeys = values("p").toSet(),
                words = values("word").map { it.lowercase() }.toSet(),
                hashtags = values("t").map { it.lowercase().removePrefix("#") }.toSet(),
                threads = values("e").toSet()
            )
        }

        /**
         * A muted word matches on word boundaries for Latin text ("art" doesn't hide "start"),
         * and anywhere for scripts written without spaces, such as Thai.
         */
        internal fun containsWord(text: String, word: String): Boolean {
            if (word.isEmpty()) return false
            if (word.any { it.code > 0x2FF }) return text.contains(word)
            var from = 0
            while (true) {
                val i = text.indexOf(word, from)
                if (i < 0) return false
                val before = text.getOrNull(i - 1)
                val after = text.getOrNull(i + word.length)
                if ((before == null || !before.isLetterOrDigit()) && (after == null || !after.isLetterOrDigit())) return true
                from = i + 1
            }
        }
    }
}

/**
 * Web of trust: the accounts the user follows and the accounts they follow. Kept as 64-bit
 * prefixes of the public keys so a large network fits in a few megabytes.
 */
class TrustNetwork(me: String, follows: Collection<String>, followsOfFollows: Collection<String>) {
    private val keys: Set<Long> = HashSet<Long>(follows.size + followsOfFollows.size + 1).apply {
        add(prefix(me)); follows.forEach { add(prefix(it)) }; followsOfFollows.forEach { add(prefix(it)) }
    }

    val size get() = keys.size

    fun contains(pubkey: String): Boolean = keys.contains(prefix(pubkey))

    private fun prefix(pubkey: String): Long = pubkey.take(16).toULongOrNull(16)?.toLong() ?: pubkey.hashCode().toLong()
}

/**
 * The private part of a NIP-51 list: a JSON array of tags, encrypted to yourself (NIP-44; old
 * lists used NIP-04, recognisable by "?iv="), kept in the list event's content.
 */
object PrivateTags {
    fun isNip04(content: String) = "?iv=" in content

    /** The tags in decrypted [json]; empty for anything that isn't an array of string arrays. */
    fun parse(json: String): List<List<String>> = runCatching {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val t = arr.optJSONArray(i) ?: return@mapNotNull null
            (0 until t.length()).map { t.optString(it) }.takeIf { it.isNotEmpty() }
        }
    }.getOrDefault(emptyList())

    fun serialize(tags: List<List<String>>): String =
        org.json.JSONArray().apply { tags.forEach { t -> put(org.json.JSONArray().apply { t.forEach { put(it) } }) } }.toString()
}
