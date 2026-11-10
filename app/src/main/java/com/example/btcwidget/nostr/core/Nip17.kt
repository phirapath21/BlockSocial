package com.example.btcwidget.nostr.core

import org.json.JSONObject
import java.security.SecureRandom

/**
 * Private direct messages (NIP-17). A message is an unsigned kind-14 "rumor", encrypted
 * into a kind-13 seal signed by the sender, encrypted again into a kind-1059 gift wrap
 * signed by a throwaway key. Relays see only the wrap: not who sent it, nor when (times
 * are randomized up to two days back), nor what it says.
 *
 * Encryption and signing are passed in, so a signer app (NIP-55) can do the parts that
 * need the user's key.
 */
object Nip17 {
    const val CHAT = 14
    /** An encrypted file (photo) message: content is its URL, tags carry the key. */
    const val FILE = 15
    const val REACTION = 7
    /** Legacy NIP-04 direct message: readable here, never sent. */
    const val LEGACY_DM = 4
    const val SEAL = 13
    const val GIFT_WRAP = 1059
    /** The relays a user wants to receive DMs on. */
    const val DM_RELAYS = 10050

    private val random = SecureRandom()

    /** A decrypted message. */
    data class Message(
        val id: String,
        val sender: String,
        /** Everyone in the conversation except the sender ("p" tags). */
        val recipients: List<String>,
        val content: String,
        val createdAt: Long,
        val replyTo: String?,
        /** The gift wrap it arrived in. */
        val wrapId: String,
        /** [CHAT], [FILE], [REACTION] (to [replyTo]) or [LEGACY_DM]. */
        val kind: Int = CHAT,
        val tags: List<List<String>> = emptyList()
    ) {
        /** Shown as a bubble (reactions attach to the message they react to). */
        val isBubble get() = kind != REACTION
        /** Less private: relays can see who talked to whom, and when. */
        val isLegacy get() = kind == LEGACY_DM
        val file: DmFile.Info? get() = if (kind == FILE) DmFile.parse(content, tags) else null

        /** Everyone in the conversation except [me], sorted: the conversation's key. */
        fun peers(me: String): List<String> = (recipients + sender).filter { it != me }.distinct().sorted().ifEmpty { listOf(me) }
    }

    fun rumor(
        sender: String, recipients: List<String>, content: String, replyTo: String? = null,
        now: Long = Events.now(), kind: Int = CHAT, extraTags: List<List<String>> = emptyList()
    ) = UnsignedEvent(
        sender, now, kind,
        recipients.distinct().map { listOf("p", it) } + listOfNotNull(replyTo?.let { listOf("e", it) }) + extraTags,
        content
    )

    /** The rumor as JSON with its id but no signature, as NIP-17 sends it. */
    fun rumorJson(rumor: UnsignedEvent): String = buildString {
        append("{\"id\":\"").append(rumor.id).append('"')
        append(",\"pubkey\":"); Json.string(this, rumor.pubkey)
        append(",\"created_at\":").append(rumor.createdAt)
        append(",\"kind\":").append(rumor.kind)
        append(",\"tags\":"); Json.tags(this, rumor.tags)
        append(",\"content\":"); Json.string(this, rumor.content)
        append('}')
    }

    /** Up to two days before [now], so wraps don't reveal when a message was sent. */
    fun randomPast(now: Long = Events.now()): Long = now - random.nextInt(2 * 24 * 3600)

    /** The seal for [receiver]: the rumor encrypted with the sender's key, signed by the sender. */
    suspend fun seal(
        rumor: UnsignedEvent,
        receiver: String,
        encrypt: suspend (plaintext: String, peer: String) -> String,
        sign: suspend (UnsignedEvent) -> Event
    ): Event = sign(UnsignedEvent(rumor.pubkey, randomPast(), SEAL, emptyList(), encrypt(rumorJson(rumor), receiver)))

    /** The gift wrap for [receiver]: the seal encrypted and signed with a new random key. */
    fun wrap(seal: Event, receiver: String): Event {
        val throwaway = Schnorr.newSecretKey()
        val unsigned = UnsignedEvent(
            Schnorr.publicKey(throwaway), randomPast(), GIFT_WRAP,
            listOf(listOf("p", receiver)),
            Nip44.encrypt(seal.toJson(), throwaway, receiver)
        )
        return unsigned.signed(Schnorr.sign(unsigned.id, throwaway))
    }

    /**
     * Opens a gift wrap addressed to the user: [decrypt] uses the user's key with the given
     * peer. Null if anything doesn't check out (wrong recipient, forged seal, impersonation).
     */
    suspend fun unwrap(wrap: Event, decrypt: suspend (ciphertext: String, peer: String) -> String): Message? {
        if (wrap.kind != GIFT_WRAP) return null
        val seal = runCatching { Event.fromJson(decrypt(wrap.content, wrap.pubkey)) }.getOrNull() ?: return null
        if (seal.kind != SEAL || !seal.verify()) return null
        val o = runCatching { JSONObject(decrypt(seal.content, seal.pubkey)) }.getOrNull() ?: return null
        val rumor = runCatching {
            val tagsJson = o.getJSONArray("tags")
            UnsignedEvent(
                pubkey = o.getString("pubkey"),
                createdAt = o.getLong("created_at"),
                kind = o.getInt("kind"),
                tags = (0 until tagsJson.length()).map { i -> tagsJson.getJSONArray(i).let { t -> (0 until t.length()).map { t.getString(it) } } },
                content = o.getString("content")
            )
        }.getOrNull() ?: return null
        // The seal's signer must be the rumor's author, or anyone could put words in someone's mouth.
        if (rumor.kind !in setOf(CHAT, FILE, REACTION) || rumor.pubkey != seal.pubkey) return null
        return Message(
            id = rumor.id,
            sender = rumor.pubkey,
            recipients = rumor.tags.filter { it.size >= 2 && it[0] == "p" }.map { it[1] }.filter { Hex.isKey(it) },
            content = rumor.content,
            createdAt = rumor.createdAt,
            replyTo = rumor.tags.firstOrNull { it.size >= 2 && it[0] == "e" }?.get(1),
            wrapId = wrap.id,
            kind = rumor.kind,
            tags = rumor.tags
        )
    }

    /** A legacy NIP-04 message (kind 4) between the user and one person, already decrypted. */
    fun fromLegacy(event: Event, plaintext: String): Message? {
        if (event.kind != LEGACY_DM || !event.verify()) return null
        val to = event.tagValues("p").firstOrNull { Hex.isKey(it) } ?: return null
        return Message(event.id, event.pubkey, listOf(to), plaintext, event.createdAt, event.tagValues("e").firstOrNull(), event.id, LEGACY_DM)
    }

    /** The other person in a legacy message the user sent or received. */
    fun legacyPeer(event: Event, me: String): String? =
        if (event.pubkey == me) event.tagValues("p").firstOrNull() else event.pubkey

    /** Relays from a kind-10050 DM relay list. */
    fun relaysFrom(list: Event?): List<String> =
        list?.tags.orEmpty().filter { it.size >= 2 && it[0] == "relay" }.mapNotNull { normalizeRelayUrl(it[1]) }

    fun dmRelayList(pubkey: String, relays: List<String>) =
        UnsignedEvent(pubkey, Events.now(), DM_RELAYS, relays.map { listOf("relay", it) }, "")
}

/**
 * Encrypted files in private messages (NIP-17 kind 15): the file is encrypted on the phone
 * with a fresh AES-GCM key, uploaded, and the key travels only inside the encrypted message.
 */
object DmFile {
    data class Info(val url: String, val mime: String, val key: ByteArray, val nonce: ByteArray, val sha256: String?, val width: Int?, val height: Int?)

    data class Sealed(val bytes: ByteArray, val key: ByteArray, val nonce: ByteArray)

    private val random = SecureRandom()

    fun encrypt(plain: ByteArray): Sealed {
        val key = ByteArray(32).also { random.nextBytes(it) }
        val nonce = ByteArray(12).also { random.nextBytes(it) }
        return Sealed(cipher(javax.crypto.Cipher.ENCRYPT_MODE, key, nonce).doFinal(plain), key, nonce)
    }

    fun decrypt(encrypted: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray =
        cipher(javax.crypto.Cipher.DECRYPT_MODE, key, nonce).doFinal(encrypted)

    private fun cipher(mode: Int, key: ByteArray, nonce: ByteArray) = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(mode, javax.crypto.spec.SecretKeySpec(key, "AES"), javax.crypto.spec.GCMParameterSpec(128, nonce))
    }

    /** Tags for a kind-15 message about an uploaded encrypted file. */
    fun tags(mime: String, sealed: Sealed, encryptedSha256: String, originalSha256: String, width: Int, height: Int): List<List<String>> = listOf(
        listOf("file-type", mime),
        listOf("encryption-algorithm", "aes-gcm"),
        listOf("decryption-key", Hex.encode(sealed.key)),
        listOf("decryption-nonce", Hex.encode(sealed.nonce)),
        listOf("x", encryptedSha256),
        listOf("ox", originalSha256),
        listOf("size", sealed.bytes.size.toString()),
        listOf("dim", "${width}x$height")
    )

    fun parse(url: String, tags: List<List<String>>): Info? {
        fun tag(name: String) = tags.firstOrNull { it.size >= 2 && it[0] == name }?.get(1)
        if (!url.startsWith("https://") || tag("encryption-algorithm") != "aes-gcm") return null
        val key = tag("decryption-key")?.let { runCatching { Hex.decode(it) }.getOrNull() }?.takeIf { it.size == 32 } ?: return null
        val nonce = tag("decryption-nonce")?.let { runCatching { Hex.decode(it) }.getOrNull() }?.takeIf { it.size in 12..16 } ?: return null
        val dim = tag("dim")?.split('x')
        return Info(url, tag("file-type") ?: "application/octet-stream", key, nonce, tag("x"), dim?.getOrNull(0)?.toIntOrNull(), dim?.getOrNull(1)?.toIntOrNull())
    }
}
