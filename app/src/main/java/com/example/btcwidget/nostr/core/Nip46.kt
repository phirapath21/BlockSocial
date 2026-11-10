package com.example.btcwidget.nostr.core

import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

/**
 * NIP-46 remote signing ("bunker"): requests and responses are kind 24133 events between
 * a throwaway client key and the remote signer, NIP-44 encrypted (NIP-04 accepted on reply).
 */
object Nip46 {
    const val KIND = 24133

    /** A parsed bunker://<signer pubkey>?relay=…&secret=… link. */
    data class Bunker(val signerPubkey: String, val relays: List<String>, val secret: String?)

    fun parseBunker(uri: String): Bunker? = runCatching {
        val trimmed = uri.trim()
        require(trimmed.startsWith("bunker://"))
        val rest = trimmed.removePrefix("bunker://")
        val pubkey = rest.substringBefore('?').lowercase()
        require(Hex.isKey(pubkey))
        val params = rest.substringAfter('?', "").split('&').filter { '=' in it }.map {
            it.substringBefore('=') to java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        val relays = params.filter { it.first == "relay" }.map { it.second.trim().trimEnd('/') }
            .filter { it.startsWith("wss://") || it.startsWith("ws://") }.distinct()
        require(relays.isNotEmpty())
        Bunker(pubkey, relays, params.firstOrNull { it.first == "secret" }?.second?.takeIf { it.isNotBlank() })
    }.getOrNull()

    data class Request(val id: String, val method: String, val params: List<String>) {
        fun toJson(): String = JSONObject().put("id", id).put("method", method).put("params", JSONArray(params)).toString()
    }

    data class Response(val id: String, val result: String?, val error: String?) {
        /** The signer wants the user to approve on a web page first; [error] is its URL. */
        val isAuthUrl get() = result == "auth_url" && error != null
    }

    private val random = SecureRandom()

    fun newRequestId(): String = Hex.encode(ByteArray(8).also { random.nextBytes(it) })

    fun parseResponse(json: String): Response? = runCatching {
        val o = JSONObject(json)
        Response(o.getString("id"), o.optString("result").takeIf { o.has("result") && !o.isNull("result") }, o.optString("error").takeIf { o.has("error") && !o.isNull("error") && o.optString("error").isNotEmpty() })
    }.getOrNull()

    /** The unsigned event as the signer expects it in sign_event. */
    fun eventParam(e: UnsignedEvent): String = JSONObject()
        .put("kind", e.kind).put("content", e.content).put("created_at", e.createdAt).put("pubkey", e.pubkey)
        .put("tags", JSONArray(e.tags.map { JSONArray(it) })).toString()

    /** A request event from [clientSeckey] to [signerPubkey], encrypted with NIP-44. */
    fun requestEvent(clientSeckey: String, signerPubkey: String, request: Request, now: Long = Events.now()): Event {
        val clientPubkey = Schnorr.publicKey(clientSeckey)
        val unsigned = UnsignedEvent(
            clientPubkey, now, KIND, listOf(listOf("p", signerPubkey)),
            Nip44.encrypt(request.toJson(), clientSeckey, signerPubkey)
        )
        return unsigned.signed(Schnorr.sign(unsigned.id, clientSeckey))
    }

    /** Opens a response event; null if it isn't one for us or can't be read. */
    fun openResponse(clientSeckey: String, event: Event): Response? {
        if (event.kind != KIND || !event.verify()) return null
        val text = runCatching {
            if ("?iv=" in event.content) Nip04.decrypt(event.content, clientSeckey, event.pubkey)
            else Nip44.decrypt(event.content, clientSeckey, event.pubkey)
        }.getOrNull() ?: return null
        return parseResponse(text)
    }
}
