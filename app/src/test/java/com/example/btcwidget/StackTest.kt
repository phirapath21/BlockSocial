package com.example.btcwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StackTest {

    @Test
    fun parseBtc() {
        assertEquals(50_000_000L, Stack.parse("0.5", StackUnit.BTC))
        assertEquals(125_000_000L, Stack.parse("1,25", StackUnit.BTC))
        assertEquals(1L, Stack.parse("0.00000001", StackUnit.BTC))
        assertEquals(2_100_000_000_000_000L, Stack.parse("21000000", StackUnit.BTC))
    }

    @Test
    fun parseSats() {
        assertEquals(5_000_000L, Stack.parse("5000000", StackUnit.SATS))
        assertEquals(5_000_000L, Stack.parse(" 5 000 000 ", StackUnit.SATS))
    }

    @Test
    fun rejectsInvalidAmounts() {
        assertNull(Stack.parse("", StackUnit.BTC))
        assertNull(Stack.parse("abc", StackUnit.BTC))
        assertNull(Stack.parse("-1", StackUnit.BTC))
        assertNull(Stack.parse("0.000000001", StackUnit.BTC)) // 9 decimals
        assertNull(Stack.parse("1.5", StackUnit.SATS))
        assertNull(Stack.parse("21000000.00000001", StackUnit.BTC))
    }

    @Test
    fun formatting() {
        assertEquals("0.5", Stack.formatBtc(50_000_000))
        assertEquals("0.00012345", Stack.formatBtc(12_345))
        assertEquals("21", Stack.formatBtc(2_100_000_000))
        assertEquals("", Stack.toInput(0, StackUnit.BTC))
        assertEquals("12345", Stack.toInput(12_345, StackUnit.SATS))
    }
}
