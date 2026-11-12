package com.example.btcwidget.ui.social

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenMemoryTest {
    @After fun clear() = ScreenMemory.clear()

    @Test fun keepsAValueUntilReplaced() {
        assertEquals("a", ScreenMemory.getOrPut("k") { "a" })
        assertEquals("a", ScreenMemory.getOrPut("k") { "b" })
        ScreenMemory.put("k", "c")
        assertEquals("c", ScreenMemory.get("k"))
    }

    @Test fun keepsNullAsLoaded() {
        ScreenMemory.put("gone", null)
        assertTrue(ScreenMemory.has("gone"))
    }

    @Test fun forgetsTheLeastRecentlyUsedPastItsLimit() {
        ScreenMemory.put("first", 1)
        repeat(700) { ScreenMemory.put("k$it", it) }
        assertFalse(ScreenMemory.has("first"))
        assertTrue(ScreenMemory.has("k699"))
    }
}
