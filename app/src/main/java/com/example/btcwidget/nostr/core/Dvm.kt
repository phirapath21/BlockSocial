package com.example.btcwidget.nostr.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Feeds from Data Vending Machines (NIP-90, "content discovery", kind 5300): a DVM announces
 * itself (NIP-89, kind 31990 with k = 5300); the app sends a job request and the DVM answers
 * (kind 6300) with a list of notes.
 */
object Dvm {
    const val ANNOUNCEMENT = 31990
    const val FEED_REQUEST = 5300
    const val FEED_RESULT = 6300
    const val FEEDBACK = 7000

    data class Service(val pubkey: String, val id: String, val name: String, val about: String?, val picture: String?, val personalized: Boolean) {
        val key get() = "$pubkey:$id"
    }

    fun service(e: Event): Service? {
        if (e.kind != ANNOUNCEMENT || FEED_REQUEST.toString() !in e.tagValues("k")) return null
        val d = e.firstTag("d")?.get(1) ?: return null
        val o = runCatching { JSONObject(e.content) }.getOrNull()
        val name = (o?.optString("name") ?: "").trim().ifEmpty { o?.optString("display_name").orEmpty().trim() }
        if (name.isEmpty()) return null
        return Service(
            pubkey = e.pubkey, id = d, name = name.take(60),
            about = o?.optString("about")?.trim()?.takeIf { it.isNotEmpty() },
            picture = (o?.optString("picture") ?: o?.optString("image"))?.takeIf { it.startsWith("https://") },
            personalized = o?.optBoolean("personalized", false) == true
        )
    }

    /** A job request to [dvm]; results go to [relays]. */
    fun request(pubkey: String, dvm: String, relays: List<String>, now: Long = Events.now()) = UnsignedEvent(
        pubkey, now, FEED_REQUEST,
        listOf(listOf("p", dvm), listOf("relays") + relays.take(5), listOf("param", "max_results", "100")),
        ""
    )

    /** Note ids from a result: its content is a JSON list of ["e", id] tags (some DVMs also tag them). */
    fun noteIds(result: Event): List<String> {
        val fromContent = runCatching {
            val a = JSONArray(result.content)
            (0 until a.length()).mapNotNull { i -> a.optJSONArray(i)?.takeIf { it.length() >= 2 && it.getString(0) == "e" }?.getString(1) }
        }.getOrDefault(emptyList())
        return fromContent.ifEmpty { result.tagValues("e").drop(1) }.filter { Hex.isKey(it) }.distinct()
    }

    /** True when [result] answers [requestId] from [dvm]. */
    fun answers(result: Event, requestId: String, dvm: String): Boolean =
        result.kind == FEED_RESULT && result.pubkey == dvm && result.tagValues("e").contains(requestId)
}
