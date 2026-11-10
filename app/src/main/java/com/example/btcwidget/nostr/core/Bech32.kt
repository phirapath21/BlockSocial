package com.example.btcwidget.nostr.core

/** Lowercase hex helpers. */
object Hex {
    private const val DIGITS = "0123456789abcdef"

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            out.append(DIGITS[v ushr 4]).append(DIGITS[v and 0x0F])
        }
        return out.toString()
    }

    fun decode(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "odd-length hex" }
        return ByteArray(hex.length / 2) { i ->
            val hi = Character.digit(hex[2 * i], 16)
            val lo = Character.digit(hex[2 * i + 1], 16)
            require(hi >= 0 && lo >= 0) { "not hex" }
            ((hi shl 4) or lo).toByte()
        }
    }

    /** True for a 32-byte key or id in lowercase hex. */
    fun isKey(value: String): Boolean = value.length == 64 && value.all { it in DIGITS }
}

/** Bech32 (BIP-173), as used by NIP-19. Nostr strings can exceed 90 chars, so no length cap. */
object Bech32 {
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val GEN = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)

    private fun polymod(values: IntArray): Int {
        var chk = 1
        for (v in values) {
            val top = chk ushr 25
            chk = ((chk and 0x1ffffff) shl 5) xor v
            for (i in 0 until 5) if ((top ushr i) and 1 == 1) chk = chk xor GEN[i]
        }
        return chk
    }

    private fun hrpExpand(hrp: String): IntArray =
        IntArray(hrp.length * 2 + 1).also { out ->
            hrp.forEachIndexed { i, c ->
                out[i] = c.code ushr 5
                out[i + hrp.length + 1] = c.code and 31
            }
        }

    fun encode(hrp: String, data: ByteArray): String {
        val values = convertBits(data, 8, 5, pad = true)
        val checksumInput = hrpExpand(hrp) + values + IntArray(6)
        val mod = polymod(checksumInput) xor 1
        val checksum = IntArray(6) { (mod ushr (5 * (5 - it))) and 31 }
        return buildString {
            append(hrp).append('1')
            for (v in values + checksum) append(CHARSET[v])
        }
    }

    /** Returns (hrp, bytes); throws on a bad checksum or format. */
    fun decode(input: String): Pair<String, ByteArray> {
        val s = input.trim()
        require(s == s.lowercase() || s == s.uppercase()) { "mixed case" }
        val lower = s.lowercase()
        val sep = lower.lastIndexOf('1')
        require(sep >= 1 && sep + 7 <= lower.length) { "no separator" }
        val hrp = lower.substring(0, sep)
        val values = IntArray(lower.length - sep - 1) { i ->
            CHARSET.indexOf(lower[sep + 1 + i]).also { require(it >= 0) { "bad character" } }
        }
        require(polymod(hrpExpand(hrp) + values) == 1) { "bad checksum" }
        val data = convertBits(values.copyOf(values.size - 6).map { it.toByte() }.toByteArray(), 5, 8, pad = false)
        return hrp to ByteArray(data.size) { data[it].toByte() }
    }

    private fun convertBits(data: ByteArray, from: Int, to: Int, pad: Boolean): IntArray {
        var acc = 0
        var bits = 0
        val out = ArrayList<Int>()
        val maxv = (1 shl to) - 1
        for (b in data) {
            val value = b.toInt() and 0xFF
            require(value ushr from == 0) { "bad value" }
            acc = (acc shl from) or value
            bits += from
            while (bits >= to) {
                bits -= to
                out += (acc ushr bits) and maxv
            }
        }
        if (pad) {
            if (bits > 0) out += (acc shl (to - bits)) and maxv
        } else {
            require(bits < from && ((acc shl (to - bits)) and maxv) == 0) { "bad padding" }
        }
        return out.toIntArray()
    }
}

/** NIP-19 identifiers: npub, nsec, note, and the TLV forms nprofile and nevent. */
object Nip19 {
    sealed interface Entity
    data class Pubkey(val hex: String, val relays: List<String> = emptyList()) : Entity
    data class SecretKey(val hex: String) : Entity
    data class Note(val id: String, val relays: List<String> = emptyList(), val author: String? = null, val kind: Int? = null) : Entity

    fun npub(pubkey: String) = Bech32.encode("npub", Hex.decode(pubkey))
    fun nsec(seckey: String) = Bech32.encode("nsec", Hex.decode(seckey))
    fun note(id: String) = Bech32.encode("note", Hex.decode(id))

    fun nevent(id: String, relays: List<String> = emptyList(), author: String? = null, kind: Int? = null): String {
        val tlv = ArrayList<Byte>()
        fun put(type: Int, value: ByteArray) {
            tlv += type.toByte(); tlv += value.size.toByte(); value.forEach { tlv += it }
        }
        put(0, Hex.decode(id))
        relays.forEach { put(1, it.toByteArray()) }
        author?.let { put(2, Hex.decode(it)) }
        kind?.let { put(3, byteArrayOf((it ushr 24).toByte(), (it ushr 16).toByte(), (it ushr 8).toByte(), it.toByte())) }
        return Bech32.encode("nevent", tlv.toByteArray())
    }

    fun nprofile(pubkey: String, relays: List<String> = emptyList()): String {
        val tlv = ArrayList<Byte>()
        fun put(type: Int, value: ByteArray) {
            tlv += type.toByte(); tlv += value.size.toByte(); value.forEach { tlv += it }
        }
        put(0, Hex.decode(pubkey))
        relays.forEach { put(1, it.toByteArray()) }
        return Bech32.encode("nprofile", tlv.toByteArray())
    }

    /** Decodes a NIP-19 string, with or without a "nostr:" prefix; null if it isn't one. */
    fun decode(value: String): Entity? = runCatching {
        val (hrp, data) = Bech32.decode(value.removePrefix("nostr:"))
        when (hrp) {
            "npub" -> Pubkey(Hex.encode(data.requireKey()))
            "nsec" -> SecretKey(Hex.encode(data.requireKey()))
            "note" -> Note(Hex.encode(data.requireKey()))
            "nprofile" -> {
                val tlv = parseTlv(data)
                Pubkey(Hex.encode(tlv.first(0).requireKey()), tlv.all(1).map { String(it) })
            }
            "nevent" -> {
                val tlv = parseTlv(data)
                Note(
                    id = Hex.encode(tlv.first(0).requireKey()),
                    relays = tlv.all(1).map { String(it) },
                    author = tlv.firstOrNull(2)?.let { Hex.encode(it.requireKey()) },
                    kind = tlv.firstOrNull(3)?.takeIf { it.size == 4 }?.let {
                        ((it[0].toInt() and 0xFF) shl 24) or ((it[1].toInt() and 0xFF) shl 16) or
                            ((it[2].toInt() and 0xFF) shl 8) or (it[3].toInt() and 0xFF)
                    }
                )
            }
            else -> null
        }
    }.getOrNull()

    private fun ByteArray.requireKey(): ByteArray = also { require(size == 32) { "not 32 bytes" } }

    private class Tlv(val entries: List<Pair<Int, ByteArray>>) {
        fun all(type: Int) = entries.filter { it.first == type }.map { it.second }
        fun firstOrNull(type: Int) = all(type).firstOrNull()
        fun first(type: Int) = requireNotNull(firstOrNull(type)) { "missing TLV $type" }
    }

    private fun parseTlv(data: ByteArray): Tlv {
        val out = ArrayList<Pair<Int, ByteArray>>()
        var i = 0
        while (i + 2 <= data.size) {
            val type = data[i].toInt() and 0xFF
            val len = data[i + 1].toInt() and 0xFF
            require(i + 2 + len <= data.size) { "truncated TLV" }
            out += type to data.copyOfRange(i + 2, i + 2 + len)
            i += 2 + len
        }
        return Tlv(out)
    }
}
