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

    /** A wallet transaction from NIP-47 `list_transactions`. */
    data class Transaction(
        val type: String,
        val invoice: String,
        val description: String,
        val amountSat: Long,
        val feesPaidSat: Long,
        val createdAt: Long,
        val settledAt: Long
    ) {
        val isIncoming: Boolean get() = type.equals("incoming", ignoreCase = true)
    }

    /** Whether the wallet's info event (kind 13194) says it supports NIP-44 encryption. */
    fun supportsNip44(info: Event?): Boolean =
        info?.firstTag("encryption")?.getOrNull(1)?.split(' ')?.contains("nip44_v2") == true

    private fun buildRequest(c: Connection, method: String, params: JSONObject, nip44: Boolean, now: Long = Events.now()): Event {
        val body = JSONObject().put("method", method).put("params", params).toString()
        val content = if (nip44) Nip44.encrypt(body, c.secret, c.walletPubkey) else Nip04.encrypt(body, c.secret, c.walletPubkey)
        val tags = buildList {
            add(listOf("p", c.walletPubkey))
            if (nip44) add(listOf("encryption", "nip44_v2"))
            // Don't let a stale request be executed much later.
            add(listOf("expiration", (now + 60).toString()))
        }
        val unsigned = UnsignedEvent(c.clientPubkey, now, REQUEST, tags, content)
        return unsigned.signed(Schnorr.sign(unsigned.id, c.secret))
    }

    /** A signed pay_invoice request; returns the event and whether it used NIP-44. */
    fun payInvoiceRequest(c: Connection, invoice: String, nip44: Boolean, now: Long = Events.now()): Event =
        buildRequest(c, "pay_invoice", JSONObject().put("invoice", invoice), nip44, now)

    /** A signed get_balance request (NIP-47). */
    fun getBalanceRequest(c: Connection, nip44: Boolean, now: Long = Events.now()): Event =
        buildRequest(c, "get_balance", JSONObject(), nip44, now)

    /** A signed make_invoice request for [amountSat] sats (sent as msat per NIP-47). */
    fun makeInvoiceRequest(c: Connection, amountSat: Long, description: String = "", nip44: Boolean, now: Long = Events.now()): Event {
        require(amountSat > 0) { "Amount must be positive" }
        val params = JSONObject().put("amount", amountSat * 1000L)
        if (description.isNotBlank()) params.put("description", description.trim())
        return buildRequest(c, "make_invoice", params, nip44, now)
    }

    /** A signed list_transactions request for the latest [limit] transactions. */
    fun listTransactionsRequest(c: Connection, limit: Int = 20, nip44: Boolean, now: Long = Events.now()): Event =
        buildRequest(c, "list_transactions", JSONObject().put("limit", limit.coerceIn(1, 100)), nip44, now)

    private fun decryptResult(c: Connection, response: Event): JSONObject {
        require(response.kind == RESPONSE && response.pubkey == c.walletPubkey) { "Not from your wallet" }
        val json = runCatching { Nip44.decrypt(response.content, c.secret, c.walletPubkey) }
            .recoverCatching { Nip04.decrypt(response.content, c.secret, c.walletPubkey) }
            .getOrThrow()
        val o = JSONObject(json)
        o.optJSONObject("error")?.let { error(it.optString("message", it.optString("code", "Wallet request failed"))) }
        return o.getJSONObject("result")
    }

    /** The wallet's answer: the payment preimage, or its error message. */
    fun parseResponse(c: Connection, response: Event): Result<String> = runCatching {
        decryptResult(c, response).optString("preimage", "")
    }

    /** Parses `get_balance` response into satoshis (NIP-47 returns millisatoshis). */
    fun parseBalance(c: Connection, response: Event): Result<Long> = runCatching {
        decryptResult(c, response).optLong("balance", 0L) / 1000L
    }

    /** Parses `make_invoice` response into a BOLT11 invoice string. */
    fun parseMakeInvoice(c: Connection, response: Event): Result<String> = runCatching {
        val inv = decryptResult(c, response).optString("invoice", "").trim()
        require(inv.isNotEmpty()) { "Wallet returned an empty invoice" }
        inv
    }

    /** Parses `list_transactions` response into a newest-first list of [Transaction]. */
    fun parseTransactions(c: Connection, response: Event): Result<List<Transaction>> = runCatching {
        val arr = decryptResult(c, response).optJSONArray("transactions") ?: return@runCatching emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                val tx = arr.optJSONObject(i) ?: continue
                val amountMsat = tx.optLong("amount", 0L)
                if (amountMsat <= 0L) continue
                val created = tx.optLong("created_at", 0L)
                val settled = tx.optLong("settled_at", created)
                add(
                    Transaction(
                        type = tx.optString("type", "outgoing"),
                        invoice = tx.optString("invoice", ""),
                        description = tx.optString("description", "").trim(),
                        amountSat = amountMsat / 1000L,
                        feesPaidSat = tx.optLong("fees_paid", 0L) / 1000L,
                        createdAt = created,
                        settledAt = settled
                    )
                )
            }
        }.sortedByDescending { if (it.settledAt > 0) it.settledAt else it.createdAt }
    }
}
