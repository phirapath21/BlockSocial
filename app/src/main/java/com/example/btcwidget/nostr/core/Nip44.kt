package com.example.btcwidget.nostr.core

import fr.acinq.secp256k1.Secp256k1
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64

/**
 * NIP-44 v2 encryption: secp256k1 ECDH, HKDF-SHA256, ChaCha20 and HMAC-SHA256, with
 * length-hiding padding. Used to seal private messages (NIP-17). Checked against the
 * official test vectors.
 */
object Nip44 {
    private const val VERSION: Byte = 2
    private val SALT = "nip44-v2".toByteArray()
    private val random = SecureRandom()

    /** The shared key between [seckey] and [pubkey] (both hex); the same from either side. */
    fun conversationKey(seckey: String, pubkey: String): ByteArray {
        // ECDH: multiply their point (taken with even y) by our secret; NIP-44 uses the raw x.
        val point = Secp256k1.pubKeyTweakMul(Hex.decode("02$pubkey"), Hex.decode(seckey))
        val sharedX = point.copyOfRange(1, 33)
        return hmac(SALT, sharedX)
    }

    class MessageKeys(val chachaKey: ByteArray, val chachaNonce: ByteArray, val hmacKey: ByteArray)

    fun messageKeys(conversationKey: ByteArray, nonce: ByteArray): MessageKeys {
        require(conversationKey.size == 32 && nonce.size == 32) { "bad key or nonce" }
        val okm = hkdfExpand(conversationKey, nonce, 76)
        return MessageKeys(okm.copyOfRange(0, 32), okm.copyOfRange(32, 44), okm.copyOfRange(44, 76))
    }

    fun paddedLength(length: Int): Int {
        require(length >= 1) { "empty message" }
        if (length <= 32) return 32
        val nextPower = 1 shl (32 - Integer.numberOfLeadingZeros(length - 1))
        val chunk = if (nextPower <= 256) 32 else nextPower / 8
        return chunk * ((length - 1) / chunk + 1)
    }

    fun encrypt(plaintext: String, conversationKey: ByteArray, nonce: ByteArray = ByteArray(32).also { random.nextBytes(it) }): String {
        val bytes = plaintext.toByteArray(Charsets.UTF_8)
        require(bytes.size in 1..65535) { "message must be 1 to 65535 bytes" }
        val padded = ByteArray(2 + paddedLength(bytes.size))
        padded[0] = (bytes.size ushr 8).toByte()
        padded[1] = bytes.size.toByte()
        bytes.copyInto(padded, 2)
        val keys = messageKeys(conversationKey, nonce)
        val ciphertext = ChaCha20.xor(keys.chachaKey, keys.chachaNonce, padded)
        val mac = hmac(keys.hmacKey, nonce + ciphertext)
        return Base64.encode(byteArrayOf(VERSION) + nonce + ciphertext + mac)
    }

    fun decrypt(payload: String, conversationKey: ByteArray): String {
        require(payload.isNotEmpty() && payload[0] != '#') { "unknown encryption version" }
        require(payload.length in 132..87472) { "invalid payload length" }
        val data = Base64.decode(payload)
        require(data.size in 99..65603) { "invalid data length" }
        require(data[0] == VERSION) { "unknown encryption version ${data[0]}" }
        val nonce = data.copyOfRange(1, 33)
        val ciphertext = data.copyOfRange(33, data.size - 32)
        val mac = data.copyOfRange(data.size - 32, data.size)
        val keys = messageKeys(conversationKey, nonce)
        require(MessageDigest.isEqual(mac, hmac(keys.hmacKey, nonce + ciphertext))) { "invalid MAC" }
        val padded = ChaCha20.xor(keys.chachaKey, keys.chachaNonce, ciphertext)
        val length = ((padded[0].toInt() and 0xFF) shl 8) or (padded[1].toInt() and 0xFF)
        require(length in 1..65535 && padded.size == 2 + paddedLength(length)) { "invalid padding" }
        return String(padded, 2, length, Charsets.UTF_8)
    }

    /** Convenience: encrypt from [seckey] to [pubkey]. */
    fun encrypt(plaintext: String, seckey: String, pubkey: String) = encrypt(plaintext, conversationKey(seckey, pubkey))
    fun decrypt(payload: String, seckey: String, pubkey: String) = decrypt(payload, conversationKey(seckey, pubkey))

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)

    /** HKDF-Expand (RFC 5869) with SHA-256. */
    private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val out = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            previous = hmac(prk, previous + info + byteArrayOf(counter.toByte()))
            val n = minOf(previous.size, length - offset)
            previous.copyInto(out, offset, 0, n)
            offset += n
            counter++
        }
        return out
    }
}

/** ChaCha20 stream cipher (RFC 8439, 96-bit nonce, counter from 0), in plain Kotlin. */
internal object ChaCha20 {
    fun xor(key: ByteArray, nonce: ByteArray, input: ByteArray): ByteArray {
        require(key.size == 32 && nonce.size == 12)
        val state = IntArray(16)
        state[0] = 0x61707865; state[1] = 0x3320646e; state[2] = 0x79622d32; state[3] = 0x6b206574
        for (i in 0 until 8) state[4 + i] = le(key, i * 4)
        state[12] = 0
        for (i in 0 until 3) state[13 + i] = le(nonce, i * 4)
        val out = ByteArray(input.size)
        val block = IntArray(16)
        var pos = 0
        while (pos < input.size) {
            state.copyInto(block)
            repeat(10) {
                quarter(block, 0, 4, 8, 12); quarter(block, 1, 5, 9, 13); quarter(block, 2, 6, 10, 14); quarter(block, 3, 7, 11, 15)
                quarter(block, 0, 5, 10, 15); quarter(block, 1, 6, 11, 12); quarter(block, 2, 7, 8, 13); quarter(block, 3, 4, 9, 14)
            }
            for (i in 0 until 16) {
                val word = block[i] + state[i]
                for (b in 0 until 4) {
                    val idx = pos + i * 4 + b
                    if (idx < input.size) out[idx] = (input[idx].toInt() xor (word ushr (8 * b))).toByte()
                }
            }
            state[12]++
            pos += 64
        }
        return out
    }

    private fun le(b: ByteArray, o: Int) =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)

    private fun quarter(x: IntArray, a: Int, b: Int, c: Int, d: Int) {
        x[a] += x[b]; x[d] = Integer.rotateLeft(x[d] xor x[a], 16)
        x[c] += x[d]; x[b] = Integer.rotateLeft(x[b] xor x[c], 12)
        x[a] += x[b]; x[d] = Integer.rotateLeft(x[d] xor x[a], 8)
        x[c] += x[d]; x[b] = Integer.rotateLeft(x[b] xor x[c], 7)
    }
}
