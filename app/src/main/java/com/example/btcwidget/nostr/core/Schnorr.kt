package com.example.btcwidget.nostr.core

import fr.acinq.secp256k1.Secp256k1
import java.security.SecureRandom

/** BIP-340 Schnorr keys and signatures over secp256k1 (ACINQ's libsecp256k1 binding). */
object Schnorr {
    private val random = SecureRandom()

    /** A new random secret key (hex). */
    fun newSecretKey(): String {
        while (true) {
            val key = ByteArray(32).also { random.nextBytes(it) }
            if (Secp256k1.secKeyVerify(key)) return Hex.encode(key)
        }
    }

    fun isValidSecretKey(seckey: String): Boolean =
        Hex.isKey(seckey) && runCatching { Secp256k1.secKeyVerify(Hex.decode(seckey)) }.getOrDefault(false)

    /** The x-only public key (hex) for [seckey]. */
    fun publicKey(seckey: String): String {
        val full = Secp256k1.pubkeyCreate(Hex.decode(seckey))
        // Uncompressed 65 bytes (0x04 | x | y) or compressed 33 (prefix | x): x is the next 32 bytes.
        return Hex.encode(full.copyOfRange(1, 33))
    }

    /** Signs a 32-byte message (an event id) with fresh auxiliary randomness. */
    fun sign(messageHex: String, seckey: String): String {
        val aux = ByteArray(32).also { random.nextBytes(it) }
        return Hex.encode(Secp256k1.signSchnorr(Hex.decode(messageHex), Hex.decode(seckey), aux))
    }

    fun verify(sigHex: String, messageHex: String, pubkeyHex: String): Boolean = runCatching {
        Secp256k1.verifySchnorr(Hex.decode(sigHex), Hex.decode(messageHex), Hex.decode(pubkeyHex))
    }.getOrDefault(false)
}
