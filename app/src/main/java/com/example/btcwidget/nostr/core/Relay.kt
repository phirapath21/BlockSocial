package com.example.btcwidget.nostr.core

import org.json.JSONArray
import org.json.JSONObject

/** A NIP-01 subscription filter. Null fields are left out. */
data class Filter(
    val ids: List<String>? = null,
    val authors: List<String>? = null,
    val kinds: List<Int>? = null,
    /** Tag filters, e.g. "e" to ids, "p" to pubkeys (written as "#e", "#p"). */
    val tags: Map<String, List<String>> = emptyMap(),
    val since: Long? = null,
    val until: Long? = null,
    val limit: Int? = null,
    /** NIP-50 full-text search; only search relays understand it. */
    val search: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        ids?.let { put("ids", Json.stringArray(it)) }
        authors?.let { put("authors", Json.stringArray(it)) }
        kinds?.let { put("kinds", JSONArray(it)) }
        tags.forEach { (name, values) -> put("#$name", Json.stringArray(values)) }
        since?.let { put("since", it) }
        until?.let { put("until", it) }
        limit?.let { put("limit", it) }
        search?.let { put("search", it) }
    }

    /**
     * Whether [event] passes this filter (used to route events to local subscriptions).
     * A search term is left to the relay: relevance can't be checked locally.
     */
    fun matches(event: Event): Boolean =
        (ids == null || event.id in ids) &&
            (authors == null || event.pubkey in authors) &&
            (kinds == null || event.kind in kinds) &&
            (since == null || event.createdAt >= since) &&
            (until == null || event.createdAt <= until) &&
            tags.all { (name, values) -> event.tagValues(name).any { it in values } }
}

/** Messages a client sends to a relay. */
object ClientMessage {
    fun req(subId: String, filters: List<Filter>): String =
        JSONArray().apply { put("REQ"); put(subId); filters.forEach { put(it.toJson()) } }.toString()

    fun close(subId: String): String = JSONArray().apply { put("CLOSE"); put(subId) }.toString()

    /** Written by hand so the event JSON is exactly what was signed. */
    fun event(event: Event): String = "[\"EVENT\"," + event.toJson() + "]"
}

/** Messages a relay sends to a client. */
sealed interface RelayMessage {
    data class EventMsg(val subId: String, val event: Event) : RelayMessage
    data class Eose(val subId: String) : RelayMessage
    data class Ok(val eventId: String, val accepted: Boolean, val message: String) : RelayMessage
    data class Closed(val subId: String, val message: String) : RelayMessage
    data class Notice(val message: String) : RelayMessage
    data class Auth(val challenge: String) : RelayMessage

    companion object {
        /** Parses one relay frame; null for anything unknown or malformed. */
        fun parse(text: String): RelayMessage? = runCatching {
            val a = JSONArray(text)
            when (a.getString(0)) {
                "EVENT" -> EventMsg(a.getString(1), Event.fromJson(a.getJSONObject(2)))
                "EOSE" -> Eose(a.getString(1))
                "OK" -> Ok(a.getString(1), a.getBoolean(2), a.optString(3, ""))
                "CLOSED" -> Closed(a.getString(1), a.optString(2, ""))
                "NOTICE" -> Notice(a.optString(1, ""))
                "AUTH" -> Auth(a.optString(1, ""))
                else -> null
            }
        }.getOrNull()
    }
}

/** Normalizes a relay URL for comparison and storage; null if it isn't a ws(s) URL. */
fun normalizeRelayUrl(url: String): String? {
    val u = url.trim().trimEnd('/')
    if (!(u.startsWith("wss://") || u.startsWith("ws://"))) return null
    val host = u.substringAfter("://").substringBefore('/')
    if (host.isEmpty() || host.any { it.isWhitespace() }) return null
    return u.lowercase()
}
