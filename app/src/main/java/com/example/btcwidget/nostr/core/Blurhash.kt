package com.example.btcwidget.nostr.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.withSign

/**
 * BlurHash (blurha.sh): a picture's colours in ~30 characters, sent in a note's imeta tag
 * ("blurhash …", NIP-92) so apps can show a soft blurred preview, at the right size, while
 * the real image loads. Decoding and encoding as the reference implementation does.
 */
object Blurhash {
    private const val CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#\$%*+,-.:;=?@[]^_{|}~"

    /** Whether [hash] has a valid shape (length matches its component count). */
    fun isValid(hash: String?): Boolean {
        if (hash == null || hash.length < 6) return false
        val size = decode83(hash, 0, 1) ?: return false
        val nx = size % 9 + 1
        val ny = size / 9 + 1
        return hash.length == 4 + 2 * nx * ny && hash.all { it in CHARS }
    }

    /**
     * [width] × [height] ARGB pixels for [hash], or null if it isn't a valid hash. Small sizes
     * (32 × 32) are plenty: the result is stretched smoothly over the image's box.
     */
    fun decode(hash: String, width: Int = 32, height: Int = 32, punch: Float = 1f): IntArray? {
        if (!isValid(hash) || width <= 0 || height <= 0) return null
        val size = decode83(hash, 0, 1)!!
        val nx = size % 9 + 1
        val ny = size / 9 + 1
        val quantMax = decode83(hash, 1, 2)!!
        val maxValue = (quantMax + 1) / 166f * punch
        val colors = Array(nx * ny) { i ->
            if (i == 0) {
                val v = decode83(hash, 2, 6)!!
                floatArrayOf(toLinear(v shr 16), toLinear((v shr 8) and 255), toLinear(v and 255))
            } else {
                val v = decode83(hash, 4 + i * 2, 6 + i * 2)!!
                floatArrayOf(
                    signPow(((v / (19 * 19)) - 9) / 9f, 2f) * maxValue,
                    signPow((((v / 19) % 19) - 9) / 9f, 2f) * maxValue,
                    signPow(((v % 19) - 9) / 9f, 2f) * maxValue
                )
            }
        }
        // Cosines per column and row, worked out once.
        val cosX = Array(nx) { i -> FloatArray(width) { x -> cos(PI * x * i / width).toFloat() } }
        val cosY = Array(ny) { j -> FloatArray(height) { y -> cos(PI * y * j / height).toFloat() } }
        val pixels = IntArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            var r = 0f; var g = 0f; var b = 0f
            for (j in 0 until ny) for (i in 0 until nx) {
                val basis = cosX[i][x] * cosY[j][y]
                val c = colors[i + j * nx]
                r += c[0] * basis; g += c[1] * basis; b += c[2] * basis
            }
            pixels[y * width + x] = (0xFF shl 24) or (toSrgb(r) shl 16) or (toSrgb(g) shl 8) or toSrgb(b)
        }
        return pixels
    }

    /**
     * The hash of a [width] × [height] picture given as ARGB [pixels] (pass a small copy, around
     * 32 px wide: the result is the same and it's much faster). [cx] × [cy] components, 1..9.
     */
    fun encode(pixels: IntArray, width: Int, height: Int, cx: Int = 4, cy: Int = 3): String {
        require(cx in 1..9 && cy in 1..9) { "components must be 1..9" }
        require(pixels.size >= width * height && width > 0 && height > 0) { "bad image" }
        val factors = Array(cx * cy) { FloatArray(3) }
        for (j in 0 until cy) for (i in 0 until cx) {
            val norm = if (i == 0 && j == 0) 1f else 2f
            var r = 0f; var g = 0f; var b = 0f
            for (y in 0 until height) for (x in 0 until width) {
                val basis = norm * cos(PI * i * x / width).toFloat() * cos(PI * j * y / height).toFloat()
                val p = pixels[y * width + x]
                r += basis * toLinear((p shr 16) and 255)
                g += basis * toLinear((p shr 8) and 255)
                b += basis * toLinear(p and 255)
            }
            val scale = 1f / (width * height)
            factors[i + j * cx].apply { this[0] = r * scale; this[1] = g * scale; this[2] = b * scale }
        }
        val sb = StringBuilder()
        encode83(sb, (cx - 1) + (cy - 1) * 9, 1)
        val maxValue: Float
        if (factors.size > 1) {
            val actualMax = factors.drop(1).maxOf { f -> f.maxOf { abs(it) } }
            val quantMax = floor(actualMax * 166 - 0.5).toInt().coerceIn(0, 82)
            maxValue = (quantMax + 1) / 166f
            encode83(sb, quantMax, 1)
        } else {
            maxValue = 1f
            encode83(sb, 0, 1)
        }
        val dc = factors[0]
        encode83(sb, (toSrgb(dc[0]) shl 16) + (toSrgb(dc[1]) shl 8) + toSrgb(dc[2]), 4)
        factors.drop(1).forEach { f ->
            fun q(v: Float) = floor(signPow(v / maxValue, 0.5f) * 9 + 9.5).toInt().coerceIn(0, 18)
            encode83(sb, q(f[0]) * 19 * 19 + q(f[1]) * 19 + q(f[2]), 2)
        }
        return sb.toString()
    }

    private fun decode83(s: String, from: Int, to: Int): Int? {
        var v = 0
        for (i in from until to) {
            val d = CHARS.indexOf(s.getOrNull(i) ?: return null)
            if (d < 0) return null
            v = v * 83 + d
        }
        return v
    }

    private fun encode83(sb: StringBuilder, value: Int, length: Int) {
        for (i in 1..length) {
            val digit = (value / 83.0.pow(length - i).toInt()) % 83
            sb.append(CHARS[digit])
        }
    }

    private fun toLinear(v: Int): Float {
        val c = v / 255f
        return if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    }

    private fun toSrgb(v: Float): Int {
        val c = v.coerceIn(0f, 1f)
        return if (c <= 0.0031308f) (c * 12.92f * 255 + 0.5f).toInt()
        else ((1.055f * c.pow(1 / 2.4f) - 0.055f) * 255 + 0.5f).toInt().coerceIn(0, 255)
    }

    private fun signPow(v: Float, e: Float): Float = abs(v).pow(e).withSign(v)

}
