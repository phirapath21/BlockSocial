package com.example.btcwidget.nostr.core

import fr.acinq.secp256k1.Secp256k1
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64

/**
 * NIP-04 encryption (AES-256-CBC with the raw ECDH x as key). Old and weaker than NIP-44,
 * but most Nostr Wallet Connect wallets still speak it, so NWC needs it.
 */
object Nip04 {
    private val random = SecureRandom()

    fun sharedKey(seckey: String, pubkey: String): ByteArray =
        Secp256k1.pubKeyTweakMul(Hex.decode("02$pubkey"), Hex.decode(seckey)).copyOfRange(1, 33)

    fun encrypt(plaintext: String, seckey: String, pubkey: String): String {
        val iv = ByteArray(16).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sharedKey(seckey, pubkey), "AES"), IvParameterSpec(iv))
        return Base64.encode(cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))) + "?iv=" + Base64.encode(iv)
    }

    fun decrypt(payload: String, seckey: String, pubkey: String): String {
        val parts = payload.split("?iv=")
        require(parts.size == 2) { "not a NIP-04 payload" }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(sharedKey(seckey, pubkey), "AES"), IvParameterSpec(Base64.decode(parts[1])))
        return String(cipher.doFinal(Base64.decode(parts[0])), Charsets.UTF_8)
    }
}

/**
 * Nostr Wallet Connect (NIP-47): the app asks the user's wallet to pay an invoice by sending
 * an encrypted kind-23194 request, signed with the connection's own secret (not the user's
 * Nostr key), to the wallet's relay, and waits for the kind-23195 response.
 */
object Nwc {
    const val INFO = 13194
    const val REQUEST = 23194
    const val RESPONSE = 23195

    /** A parsed `nostr+walletconnect://` connection. */
    data class Connection(val walletPubkey: String, val relays: List<String>, val secret: String, val lud16: String?) {
        /** The app's key for this connection (derived from [secret]). */
        val clientPubkey: String get() = Schnorr.publicKey(secret)
    }

    fun parse(uri: String): Connection? = runCatching {
        val s = uri.trim()
        val rest = when {
            s.startsWith("nostr+walletconnect://") -> s.removePrefix("nostr+walletconnect://")
            s.startsWith("nostrwalletconnect://") -> s.removePrefix("nostrwalletconnect://")
            s.startsWith("nostr+walletconnect:") -> s.removePrefix("nostr+walletconnect:")
            else -> return null
        }
        val wallet = rest.substringBefore('?').trimEnd('/').lowercase()
        val query = rest.substringAfter('?', "")
        val params = query.split('&').filter { '=' in it }.map {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        val relays = params.filter { it.first == "relay" }.mapNotNull { normalizeRelayUrl(it.second) }
        val secret = params.firstOrNull { it.first == "secret" }?.second?.lowercase()
        if (!Hex.isKey(wallet) || secret == null || !Schnorr.isValidSecretKey(secret) || relays.isEmpty()) return null
        Connection(wallet, relays, secret, params.firstOrNull { it.first == "lud16" }?.second)
    }.getOrNull()

    /** Whether the wallet's info event (kind 13194) says it supports NIP-44 encryption. */
    fun supportsNip44(info: Event?): Boolean =
        info?.firstTag("encryption")?.getOrNull(1)?.split(' ')?.contains("nip44_v2") == true

    /** A signed pay_invoice request; returns the event and whether it used NIP-44. */
    fun payInvoiceRequest(c: Connection, invoice: String, nip44: Boolean, now: Long = Events.now()): Event {
        val body = JSONObject().put("method", "pay_invoice").put("params", JSONObject().put("invoice", invoice)).toString()
        val content = if (nip44) Nip44.encrypt(body, c.secret, c.walletPubkey) else Nip04.encrypt(body, c.secret, c.walletPubkey)
        val tags = buildList {
            add(listOf("p", c.walletPubkey))
            if (nip44) add(listOf("encryption", "nip44_v2"))
            // Don't let a stale request be paid much later.
            add(listOf("expiration", (now + 60).toString()))
        }
        val unsigned = UnsignedEvent(c.clientPubkey, now, REQUEST, tags, content)
        return unsigned.signed(Schnorr.sign(unsigned.id, c.secret))
    }

    /** The wallet's answer: the payment preimage, or its error message. */
    fun parseResponse(c: Connection, response: Event): Result<String> = runCatching {
        require(response.kind == RESPONSE && response.pubkey == c.walletPubkey) { "Not from your wallet" }
        val json = runCatching { Nip44.decrypt(response.content, c.secret, c.walletPubkey) }
            .recoverCatching { Nip04.decrypt(response.content, c.secret, c.walletPubkey) }
            .getOrThrow()
        val o = JSONObject(json)
        o.optJSONObject("error")?.let { error(it.optString("message", it.optString("code", "Payment failed"))) }
        o.getJSONObject("result").optString("preimage", "")
    }
}
