package com.example.btcwidget.nostr

import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Filter
import com.example.btcwidget.nostr.core.Nip46
import com.example.btcwidget.nostr.core.Schnorr
import com.example.btcwidget.nostr.core.UnsignedEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * NIP-46 remote signer ("bunker", such as nsec.app): the user's key stays with the signer;
 * this app holds only a throwaway client key and asks over the bunker's relays.
 */
class BunkerSigner(
    override val pubkey: String,
    private val signerPubkey: String,
    private val relays: List<String>,
    private val clientSeckey: String,
    /** Opens a page where the user approves a request (auth_url). */
    private val openUrl: (String) -> Unit = {}
) : NostrSigner {
    private val clientPubkey = Schnorr.publicKey(clientSeckey)
    private val pool = RelayPool().also { it.setRelays(relays) }
    private val waiting = ConcurrentHashMap<String, CompletableDeferred<Nip46.Response>>()
    @Volatile private var stopListening: (() -> Unit)? = null

    private fun listen() {
        if (stopListening != null) return
        stopListening = pool.subscribe(
            listOf(Filter(kinds = listOf(Nip46.KIND), tags = mapOf("p" to listOf(clientPubkey)), since = System.currentTimeMillis() / 1000 - 10)),
            { e, _ ->
                if (e.pubkey != signerPubkey) return@subscribe
                val response = Nip46.openResponse(clientSeckey, e) ?: return@subscribe
                if (response.isAuthUrl) {
                    // Keep waiting for the real answer once the user approves on that page.
                    response.error?.takeIf { it.startsWith("https://") }?.let(openUrl)
                    return@subscribe
                }
                waiting.remove(response.id)?.complete(response)
            }
        )
    }

    /** Sends [method] and waits for the answer; null on timeout. */
    private suspend fun call(method: String, params: List<String>, timeoutMs: Long): String? {
        listen()
        val request = Nip46.Request(Nip46.newRequestId(), method, params)
        val deferred = CompletableDeferred<Nip46.Response>()
        waiting[request.id] = deferred
        try {
            val results = pool.publish(Nip46.requestEvent(clientSeckey, signerPubkey, request))
            if (results.none { it.accepted }) throw SignerUnavailable("The signer's relays didn't take the request")
            val response = withTimeoutOrNull(timeoutMs) { deferred.await() } ?: return null
            response.error?.let { if (it.contains("reject", true) || it.contains("denied", true)) throw SignerRejected() else throw SignerUnavailable(it) }
            return response.result
        } finally {
            waiting.remove(request.id)
        }
    }

    /** Pairs with the bunker (the secret from the link, if any). Returns the user's pubkey. */
    suspend fun connect(secret: String?): String {
        call("connect", listOfNotNull(signerPubkey, secret), CONNECT_TIMEOUT)
            ?: throw SignerUnavailable("The signer didn't answer. Check the bunker link and that the signer is online.")
        val user = call("get_public_key", emptyList(), CONNECT_TIMEOUT)?.lowercase()
            ?: throw SignerUnavailable("The signer didn't share your public key")
        if (!com.example.btcwidget.nostr.core.Hex.isKey(user)) throw SignerUnavailable("The signer returned an invalid public key")
        return user
    }

    override suspend fun sign(event: UnsignedEvent): Event {
        require(event.pubkey == pubkey) { "event is not from this account" }
        val json = call("sign_event", listOf(Nip46.eventParam(event)), INTERACTIVE_TIMEOUT)
            ?: throw SignerUnavailable("The signer didn't answer in time")
        val signed = runCatching { Event.fromJson(json) }.getOrNull() ?: throw SignerUnavailable("The signer returned no event")
        if (signed.id != event.id || !signed.verify()) throw SignerUnavailable("The signer returned an invalid signature")
        return signed
    }

    override suspend fun nip44Encrypt(plaintext: String, peer: String): String =
        call("nip44_encrypt", listOf(peer, plaintext), INTERACTIVE_TIMEOUT) ?: throw SignerUnavailable("The signer didn't encrypt the message")

    override suspend fun nip44Decrypt(ciphertext: String, peer: String, interactive: Boolean): String? =
        runCatching { call("nip44_decrypt", listOf(peer, ciphertext), if (interactive) INTERACTIVE_TIMEOUT else QUIET_TIMEOUT) }
            .getOrElse { if (interactive) throw it else null }

    fun close() {
        stopListening?.invoke()
        stopListening = null
        pool.closeAll()
    }

    companion object {
        private const val CONNECT_TIMEOUT = 60_000L
        /** Long enough to approve on another device. */
        private const val INTERACTIVE_TIMEOUT = 90_000L
        private const val QUIET_TIMEOUT = 8_000L
    }
}
