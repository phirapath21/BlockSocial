package com.example.btcwidget.nostr

import com.example.btcwidget.nostr.core.Blurhash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BlurhashTest {
    // The example from blurha.sh.
    private val sample = "LEHV6nWB2yk8pyo0adR*.7kCMdnj"

    @Test
    fun decodesTheReferenceHash() {
        assertTrue(Blurhash.isValid(sample))
        val px = Blurhash.decode(sample, 32, 32)
        assertNotNull(px)
        assertEquals(32 * 32, px!!.size)
        assertTrue(px.all { it ushr 24 == 0xFF }) // opaque
        assertTrue(px.toSet().size > 50)          // a gradient, not one colour
    }

    @Test
    fun rejectsBadHashes() {
        assertFalse(Blurhash.isValid(null))
        assertFalse(Blurhash.isValid("abc"))
        assertFalse(Blurhash.isValid(sample.dropLast(2)))   // length doesn't match its components
        assertFalse(Blurhash.isValid(sample.replace('6', ' ')))
        assertNull(Blurhash.decode("nothing"))
    }

    @Test
    fun encodeThenDecodeKeepsTheColours() {
        // Left half orange, right half dark blue.
        val w = 32; val h = 20
        val px = IntArray(w * h) { i -> if (i % w < w / 2) 0xFFF7931A.toInt() else 0xFF102040.toInt() }
        val hash = Blurhash.encode(px, w, h, 4, 3)
        assertEquals(4 + 2 * 4 * 3, hash.length)
        assertTrue(Blurhash.isValid(hash))
        val back = Blurhash.decode(hash, w, h)!!
        fun near(a: Int, b: Int) = listOf(16, 8, 0).all { s -> abs(((a shr s) and 255) - ((b shr s) and 255)) < 60 }
        assertTrue("left stays orange", near(back[h / 2 * w + 2], 0xFFF7931A.toInt()))
        assertTrue("right stays dark blue", near(back[h / 2 * w + w * 3 / 4], 0xFF102040.toInt()))
    }

    @Test
    fun reEncodingTheReferenceLooksTheSame() {
        // Not byte-identical (the format doesn't promise that), but the same picture.
        val px = Blurhash.decode(sample, 32, 32)!!
        val again = Blurhash.decode(Blurhash.encode(px, 32, 32, 4, 3), 32, 32)!!
        val diff = px.indices.sumOf { i -> listOf(16, 8, 0).sumOf { s -> abs(((px[i] shr s) and 255) - ((again[i] shr s) and 255)) } } / (px.size * 3.0)
        assertTrue("mean channel difference $diff", diff < 12)
    }
}
