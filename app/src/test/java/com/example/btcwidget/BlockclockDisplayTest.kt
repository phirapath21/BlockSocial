package com.example.btcwidget

import org.junit.Assert.assertEquals
import org.junit.Test

class BlockclockDisplayTest {

    private val data = PriceData(
        btcUsd = 97_123.4,
        btcThb = 3_215_000.0,
        moscowTime = 1029,
        mstrUsd = 412.6,
        mstrBtc = 0.004123,
        blockHeight = 925_000,
        mstrBtcHeld = 640_031.0,
        btcCirculation = 19_912_345.0,
        timestamp = 0L,
        feeFastest = 7,
        feeHalfHour = 5,
        feeHour = 3,
        difficultyChange = 1.54,
        difficultyProgress = 40.0,
        hashRate = 812.34,
        lightningCapacity = 4_900.0
    )

    @Test
    fun values_matchBlockclockStyle() {
        // No thousands separators; USD prices carry a dollar sign.
        assertEquals("$97123", BlockclockMetrics.forMode(0, data, "USD").value)
        assertEquals("925000", BlockclockMetrics.forMode(2, data, "USD").value)
        assertEquals("19.912", BlockclockMetrics.forMode(7, data, "USD").value)
        assertEquals("MIL", BlockclockMetrics.forMode(7, data, "USD").suffix)
    }

    @Test
    fun thbPrice_isShownInMillions() {
        val d = BlockclockMetrics.forMode(0, data, "THB")
        assertEquals("THB", d.labelBottom)
        assertEquals("3.22", d.value)
        assertEquals("MIL", d.suffix)
    }

    @Test
    fun moscowTime_isSatsPerDollar() {
        assertEquals("1029", BlockclockMetrics.forMode(3, data, "USD").value)
        assertEquals("TIME", BlockclockMetrics.forMode(3, data, "USD").labelBottom)
    }

    @Test
    fun forKey_matchesModeOrder() {
        assertEquals(13, BlockclockMetrics.MODE_COUNT)
        assertEquals(BlockclockMetrics.MODE_COUNT, BlockclockMetrics.MODE_NAMES.size)
        assertEquals("925000", BlockclockMetrics.forKey("BLOCK", data, "USD").value)
        assertEquals("7", BlockclockMetrics.forKey("FEES", data, "USD").value)
    }

    @Test
    fun halving_modes() {
        // 925,000 % 210,000 = 85,000 done; 125,000 left.
        assertEquals("125000", BlockclockMetrics.halving(2, 925_000).value)
        assertEquals("40.48%", BlockclockMetrics.halving(0, 925_000).value)
        assertEquals("868", BlockclockMetrics.halving(1, 925_000).value)
        assertEquals(0.40476, BlockclockMetrics.halvingProgress(925_000), 1e-4)
    }
}
