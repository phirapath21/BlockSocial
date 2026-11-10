package com.example.btcwidget.nostr.core

import org.json.JSONObject
import java.net.URLEncoder

/** NIP-05 internet identifiers ("name@domain" → public key). */
object Nip05 {
    private val ID = Regex("^([a-z0-9._-]+)@([a-z0-9.-]+\\.[a-z]{2,})$", RegexOption.IGNORE_CASE)

    /** True for something that looks like name@domain (or just a domain, meaning "_@domain"). */
    fun looks(identifier: String): Boolean = parts(identifier) != null

    /** (name, domain); a bare domain means name "_". */
    fun parts(identifier: String): Pair<String, String>? {
        val s = identifier.trim().removePrefix("@")
        ID.find(s)?.let { return it.groupValues[1].lowercase() to it.groupValues[2].lowercase() }
        if (Regex("^[a-z0-9.-]+\\.[a-z]{2,}$", RegexOption.IGNORE_CASE).matches(s) && '.' in s) return "_" to s.lowercase()
        return null
    }

    /** The well-known URL to look up [identifier]; null if it isn't one. */
    fun url(identifier: String): String? = parts(identifier)?.let { (name, domain) ->
        "https://$domain/.well-known/nostr.json?name=" + URLEncoder.encode(name, "UTF-8")
    }

    /** The hex pubkey for [name] in a nostr.json document; null if absent or malformed. */
    fun pubkeyFrom(json: String, name: String): String? = runCatching {
        JSONObject(json).getJSONObject("names").optString(name.lowercase(), "").lowercase().takeIf { Hex.isKey(it) }
    }.getOrNull()
}
