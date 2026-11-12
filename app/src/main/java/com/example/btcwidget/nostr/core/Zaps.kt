package com.example.btcwidget.nostr.core

import org.json.JSONObject
import java.net.URLEncoder

/**
 * Lightning zaps (NIP-57). The flow: read the recipient's LNURL-pay endpoint from their
 * profile, sign a zap request, ask the endpoint for an invoice, then hand the invoice to a
 * wallet. The recipient's service publishes a zap receipt (kind 9735) once it is paid.
 */
object Zaps {
    /** LNURL-pay endpoint: lud16 "name@domain" or a bech32 lud06; null if neither is usable. */
    fun payEndpoint(profile: Profile): String? {
        profile.lud16?.let { address ->
            val parts = address.trim().split('@')
            if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].contains('.')) {
                return "https://${parts[1].lowercase()}/.well-known/lnurlp/${parts[0]}"
            }
        }
        profile.lud06?.let { lnurl ->
            return runCatching {
                val (hrp, data) = Bech32.decode(lnurl.trim())
                if (hrp == "lnurl") String(data, Charsets.UTF_8).takeIf { it.startsWith("https://") } else null
            }.getOrNull()
        }
        return null
    }

    /** bech32 "lnurl" encoding of [url], sent along with zap requests. */
    fun encodeLnurl(url: String): String = Bech32.encode("lnurl", url.toByteArray(Charsets.UTF_8))

    /** What an LNURL-pay endpoint answered. */
    data class PayInfo(
        val callback: String,
        val minSendableMsat: Long,
        val maxSendableMsat: Long,
        val allowsNostr: Boolean,
        val nostrPubkey: String?
    )

    fun parsePayInfo(json: String): PayInfo? = runCatching {
        val o = JSONObject(json)
        if (o.optString("status") == "ERROR") return null
        PayInfo(
            callback = o.getString("callback"),
            minSendableMsat = o.getLong("minSendable"),
            maxSendableMsat = o.getLong("maxSendable"),
            allowsNostr = o.optBoolean("allowsNostr", false),
            nostrPubkey = o.optString("nostrPubkey", "").takeIf { Hex.isKey(it) }
        )
    }.getOrNull()

    /** Zap request (kind 9734). Not published: it goes to the callback. */
    fun request(
        sender: String,
        recipient: String,
        amountMsat: Long,
        lnurl: String,
        relays: List<String>,
        noteId: String? = null,
        noteKind: Int? = null,
        comment: String = "",
        /** An addressable event zapped (a live stream's "30311:pubkey:d"), as NIP-57 allows. */
        address: String? = null
    ): UnsignedEvent {
        val tags = ArrayList<List<String>>()
        tags += listOf("relays") + relays.take(5)
        tags += listOf("amount", amountMsat.toString())
        tags += listOf("lnurl", lnurl)
        tags += listOf("p", recipient)
        if (noteId != null) tags += listOf("e", noteId)
        if (noteKind != null) tags += listOf("k", noteKind.toString())
        if (address != null) tags += listOf("a", address)
        return UnsignedEvent(sender, Events.now(), Kind.ZAP_REQUEST, tags, comment)
    }

    /** Callback URL that returns the invoice for a signed zap [request]. */
    fun callbackUrl(info: PayInfo, amountMsat: Long, request: Event, lnurl: String): String {
        val sep = if (info.callback.contains('?')) "&" else "?"
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        return info.callback + sep + "amount=$amountMsat&nostr=${enc(request.toJson())}&lnurl=${enc(lnurl)}"
    }

    /** The bolt11 invoice from the callback's answer, or an error message. */
    fun parseInvoice(json: String): Result<String> = runCatching {
        val o = JSONObject(json)
        if (o.optString("status") == "ERROR") error(o.optString("reason", "The wallet refused the zap"))
        o.getString("pr")
    }

    /** Amount of a bolt11 invoice in millisats, from its human-readable part; null if none. */
    fun invoiceAmountMsat(bolt11: String): Long? {
        val lower = bolt11.lowercase().removePrefix("lightning:")
        val hrp = lower.substringBeforeLast('1')
        val m = Regex("^ln(?:bc|tb|bcrt|tbs)(\\d+)([munp]?)$").find(hrp) ?: return null
        val amount = m.groupValues[1].toLongOrNull() ?: return null
        // 1 BTC = 100_000_000_000 msat.
        return when (m.groupValues[2]) {
            "" -> amount * 100_000_000_000L
            "m" -> amount * 100_000_000L
            "u" -> amount * 100_000L
            "n" -> amount * 100L
            "p" -> if (amount % 10 == 0L) amount / 10 else null
            else -> null
        }
    }

    /** A zap receipt (kind 9735) as shown in the app. */
    data class Receipt(
        val id: String,
        val sender: String?,
        val recipient: String?,
        val noteId: String?,
        val amountMsat: Long,
        val comment: String,
        val createdAt: Long
    )

    fun parseReceipt(event: Event): Receipt? {
        if (event.kind != Kind.ZAP_RECEIPT) return null
        val request = event.firstTag("description")?.getOrNull(1)?.let { runCatching { Event.fromJson(it) }.getOrNull() }
        val amount = event.firstTag("bolt11")?.getOrNull(1)?.let { invoiceAmountMsat(it) }
            ?: request?.firstTag("amount")?.getOrNull(1)?.toLongOrNull()
            ?: return null
        return Receipt(
            id = event.id,
            sender = request?.pubkey ?: event.firstTag("P")?.getOrNull(1),
            recipient = event.firstTag("p")?.getOrNull(1),
            noteId = event.firstTag("e")?.getOrNull(1),
            amountMsat = amount,
            comment = request?.content.orEmpty(),
            createdAt = event.createdAt
        )
    }
}
