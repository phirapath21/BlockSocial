package com.example.btcwidget.nostr.core

import org.json.JSONObject

/**
 * The user's relay lists:
 * - NIP-65 (kind 10002): where they write (others read their notes there) and read (others send
 *   replies and mentions there): "r" tags, marked "read", "write" or neither (both).
 * - Search relays (kind 10007) and blocked relays (kind 10006), NIP-51: "relay" tags.
 */
object RelayLists {
    const val SEARCH = 10007
    const val BLOCKED = 10006

    data class Entry(val url: String, val read: Boolean, val write: Boolean)

    /** The entries of a kind-10002 list, each address once (normalized). */
    fun entries(list: Event?): List<Entry> =
        list?.tags.orEmpty().filter { it.size >= 2 && it[0] == "r" }.mapNotNull { t ->
            val url = normalizeRelayUrl(t[1]) ?: return@mapNotNull null
            when (t.getOrNull(2)?.lowercase()) {
                "read" -> Entry(url, read = true, write = false)
                "write" -> Entry(url, read = false, write = true)
                else -> Entry(url, read = true, write = true)
            }
        }.distinctBy { it.url }

    /** A kind-10002 list of [entries] (an entry with neither read nor write is left out). */
    fun relayList(me: String, entries: List<Entry>, now: Long = Events.now()) = UnsignedEvent(
        me, now, Kind.RELAY_LIST,
        entries.filter { it.read || it.write }.distinctBy { it.url }.map { e ->
            when {
                e.read && e.write -> listOf("r", e.url)
                e.read -> listOf("r", e.url, "read")
                else -> listOf("r", e.url, "write")
            }
        },
        ""
    )

    /** The relays in a NIP-51 relay set (search or blocked). */
    fun relays(list: Event?): List<String> =
        list?.tags.orEmpty().filter { it.size >= 2 && it[0] == "relay" }.mapNotNull { normalizeRelayUrl(it[1]) }.distinct()

    /** A NIP-51 relay set of [kind] with [urls], keeping other tags and private content of [current]. */
    fun relaySet(me: String, kind: Int, urls: List<String>, current: Event?, now: Long = Events.now()) = UnsignedEvent(
        me, now, kind,
        current?.tags.orEmpty().filterNot { it.firstOrNull() == "relay" } + urls.mapNotNull { normalizeRelayUrl(it) }.distinct().map { listOf("relay", it) },
        current?.content.orEmpty()
    )
}

/** What a relay says about itself (NIP-11): fetched over HTTPS with Accept: application/nostr+json. */
data class RelayInfo(
    val name: String?,
    val description: String?,
    val operator: String?,
    val contact: String?,
    val software: String?,
    val version: String?,
    val icon: String?,
    val nips: List<Int>,
    val paymentRequired: Boolean,
    val authRequired: Boolean,
    val restrictedWrites: Boolean,
    val paymentsUrl: String?
) {
    companion object {
        fun parse(json: String): RelayInfo? = runCatching {
            val o = JSONObject(json)
            fun str(k: String) = o.optString(k).trim().takeIf { it.isNotEmpty() && it != "null" }
            val lim = o.optJSONObject("limitation")
            val nips = o.optJSONArray("supported_nips")?.let { a -> (0 until a.length()).mapNotNull { a.opt(it)?.toString()?.toIntOrNull() } }.orEmpty()
            RelayInfo(
                name = str("name"), description = str("description"),
                operator = str("pubkey")?.takeIf { Hex.isKey(it) }, contact = str("contact"),
                software = str("software"), version = str("version"),
                icon = str("icon")?.takeIf { it.startsWith("https://") },
                nips = nips.distinct().sorted(),
                paymentRequired = lim?.optBoolean("payment_required") == true,
                authRequired = lim?.optBoolean("auth_required") == true,
                restrictedWrites = lim?.optBoolean("restricted_writes") == true,
                paymentsUrl = str("payments_url")
            )
        }.getOrNull()

        /** The HTTPS address to ask: wss:// becomes https://, ws:// becomes http://. */
        fun httpUrl(relay: String): String = when {
            relay.startsWith("wss://") -> "https://" + relay.removePrefix("wss://")
            relay.startsWith("ws://") -> "http://" + relay.removePrefix("ws://")
            else -> relay
        }
    }
}
