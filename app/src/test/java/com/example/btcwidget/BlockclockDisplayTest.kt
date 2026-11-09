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

    @Test
    fun moscowTime_unavailableInThb() {
        val moscow = BlockclockMetrics.MODE_MOSCOW_TIME
        assertEquals(true, BlockclockMetrics.isAvailable(moscow, "USD"))
        assertEquals(false, BlockclockMetrics.isAvailable(moscow, "THB"))
        assertEquals(false, BlockclockMetrics.isActive(moscow, enabled = true, currency = "THB"))
        // Sats per baht is still meaningful.
        assertEquals(true, BlockclockMetrics.isAvailable(1, "THB"))
    }

    @Test
    fun cyclingSkipsMoscowTimeInThb() {
        val allOn = { _: Int -> true }
        assertEquals(3, BlockclockMetrics.nextActiveMode(2, "USD", isEnabled = allOn))
        assertEquals(4, BlockclockMetrics.nextActiveMode(2, "THB", isEnabled = allOn))
    }

    @Test
    fun cyclingFallsBackToPriceWhenNothingIsActive() {
        // Only Moscow Time enabled, then the user switches to THB.
        val onlyMoscow = { m: Int -> m == BlockclockMetrics.MODE_MOSCOW_TIME }
        assertEquals(BlockclockMetrics.MODE_PRICE, BlockclockMetrics.nextActiveMode(3, "THB", isEnabled = onlyMoscow))
        assertEquals(3, BlockclockMetrics.nextActiveMode(3, "USD", isEnabled = onlyMoscow))
    }

    // ---- ordering ----

    private val keys = BlockclockMetrics.MODE_KEYS

    @Test
    fun sanitizeOrder_dropsUnknownAndAppendsMissing() {
        val order = BlockclockMetrics.sanitizeOrder(listOf("FEES", "NOPE", "PRICE", "FEES"))
        assertEquals(listOf("FEES", "PRICE"), order.take(2))
        assertEquals(keys.size, order.size)
        assertEquals(keys.toSet(), order.toSet())
        assertEquals(keys, BlockclockMetrics.parseOrder(null))
        assertEquals(keys, BlockclockMetrics.parseOrder("garbage,,"))
    }

    @Test
    fun serializeRoundTrip() {
        val order = BlockclockMetrics.move(keys, "LN_CAP", "PRICE")
        assertEquals(order, BlockclockMetrics.parseOrder(BlockclockMetrics.serializeOrder(order)))
        assertEquals(false, BlockclockMetrics.isDefaultOrder(order))
        assertEquals(true, BlockclockMetrics.isDefaultOrder(keys))
    }

    @Test
    fun move_swapsLikeAGrid() {
        val abc = listOf("PRICE", "SATS", "BLOCK")
        // Drag down onto a later item: lands after it.
        assertEquals(listOf("SATS", "BLOCK", "PRICE"), BlockclockMetrics.move(abc, "PRICE", "BLOCK").take(3))
        // Drag up onto an earlier item: lands before it.
        assertEquals(listOf("BLOCK", "PRICE", "SATS"), BlockclockMetrics.move(abc, "BLOCK", "PRICE").take(3))
    }

    @Test
    fun moveAmongVisible_skipsHiddenModes() {
        // MSCW hidden (e.g. THB): moving BLOCK later jumps over it to MSTR_HELD.
        val visible = keys.filter { it != "MSCW" }
        val order = BlockclockMetrics.moveAmongVisible(keys, visible, "BLOCK", +1)
        assertEquals(listOf("PRICE", "SATS", "MSCW", "MSTR_HELD", "BLOCK"), order.take(5))
        // Ends are no-ops.
        assertEquals(keys, BlockclockMetrics.moveAmongVisible(keys, visible, "PRICE", -1))
    }

    @Test
    fun nextActiveMode_followsUserOrder() {
        val order = listOf("FEES", "PRICE", "BLOCK") // rest appended
        val allOn = { _: Int -> true }
        assertEquals(0, BlockclockMetrics.nextActiveMode(9, "USD", order, allOn))  // FEES -> PRICE
        assertEquals(2, BlockclockMetrics.nextActiveMode(0, "USD", order, allOn))  // PRICE -> BLOCK
        assertEquals(1, BlockclockMetrics.nextActiveMode(2, "USD", order, allOn))  // BLOCK -> SATS (first appended)
        val last = BlockclockMetrics.orderedModes(order).last()
        assertEquals(9, BlockclockMetrics.nextActiveMode(last, "USD", order, allOn)) // wraps to FEES
    }
}
