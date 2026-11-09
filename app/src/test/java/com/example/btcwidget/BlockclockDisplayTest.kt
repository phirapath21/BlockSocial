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
        // Default (grouped) order starts PRICE, SATS, MSCW, BLOCK, ...
        assertEquals(3, BlockclockMetrics.nextActiveMode(1, "USD", isEnabled = allOn)) // SATS -> MSCW
        assertEquals(2, BlockclockMetrics.nextActiveMode(1, "THB", isEnabled = allOn)) // SATS -> BLOCK
    }

    @Test
    fun cyclingFallsBackToPriceWhenNothingIsActive() {
        // Only Moscow Time enabled, then the user switches to THB.
        val onlyMoscow = { m: Int -> m == BlockclockMetrics.MODE_MOSCOW_TIME }
        assertEquals(BlockclockMetrics.MODE_PRICE, BlockclockMetrics.nextActiveMode(3, "THB", isEnabled = onlyMoscow))
        assertEquals(3, BlockclockMetrics.nextActiveMode(3, "USD", isEnabled = onlyMoscow))
    }

    // ---- categories & ordering ----

    private val default = BlockclockMetrics.DEFAULT_ORDER

    @Test
    fun everyModeHasACategory_andDefaultOrderIsGrouped() {
        assertEquals(BlockclockMetrics.MODE_COUNT, default.size)
        val categories = default.map { BlockclockMetrics.categoryOf(it) }
        assertEquals(categories.sortedBy { it.ordinal }, categories)
        assertEquals(listOf("PRICE", "SATS", "MSCW"), default.take(3))
        assertEquals(MetricCategory.STRATEGY, BlockclockMetrics.categoryOf("MSTR_PRICE"))
    }

    @Test
    fun sanitizeOrder_keepsUserOrderInsideEachCategory() {
        val order = BlockclockMetrics.sanitizeOrder(listOf("LN_CAP", "NOPE", "FEES", "BLOCK", "FEES"))
        assertEquals(default.toSet(), order.toSet())
        assertEquals(default.size, order.size)
        val network = order.filter { BlockclockMetrics.categoryOf(it) == MetricCategory.NETWORK }
        assertEquals(listOf("FEES", "BLOCK"), network.take(2))
        val supply = order.filter { BlockclockMetrics.categoryOf(it) == MetricCategory.SUPPLY }
        assertEquals(listOf("LN_CAP", "CIRCULATION"), supply)
        assertEquals(default, BlockclockMetrics.parseOrder(null))
        assertEquals(default, BlockclockMetrics.parseOrder("garbage,,"))
    }

    @Test
    fun serializeRoundTrip() {
        val order = BlockclockMetrics.move(default, "LN_CAP", "CIRCULATION")
        assertEquals(order, BlockclockMetrics.parseOrder(BlockclockMetrics.serializeOrder(order)))
        assertEquals(false, BlockclockMetrics.isDefaultOrder(order))
        assertEquals(true, BlockclockMetrics.isDefaultOrder(default))
        assertEquals(true, BlockclockMetrics.isDefaultOrder(BlockclockMetrics.MODE_KEYS))
    }

    @Test
    fun move_swapsLikeAGridWithinACategory() {
        // Drag down onto a later item: lands after it.
        assertEquals(listOf("SATS", "MSCW", "PRICE"), BlockclockMetrics.move(default, "PRICE", "MSCW").take(3))
        // Drag up onto an earlier item: lands before it.
        assertEquals(listOf("MSCW", "PRICE", "SATS"), BlockclockMetrics.move(default, "MSCW", "PRICE").take(3))
    }

    @Test
    fun move_acrossCategoriesIsIgnored() {
        assertEquals(default, BlockclockMetrics.move(default, "PRICE", "BLOCK"))
        assertEquals(default, BlockclockMetrics.move(default, "MSTR_HELD", "LN_CAP"))
    }

    @Test
    fun moveAmongVisible_staysInSectionAndSkipsHidden() {
        // MSCW hidden (e.g. THB): moving SATS later has no visible neighbour left in Market.
        val visible = default.filter { it != "MSCW" }
        assertEquals(default, BlockclockMetrics.moveAmongVisible(default, visible, "SATS", +1))
        // Moving PRICE later swaps it with SATS.
        assertEquals(listOf("SATS", "PRICE"), BlockclockMetrics.moveAmongVisible(default, visible, "PRICE", +1).take(2))
        // First in section: no-op.
        assertEquals(default, BlockclockMetrics.moveAmongVisible(default, visible, "BLOCK", -1))
    }

    @Test
    fun nextActiveMode_followsGroupedUserOrder() {
        val order = BlockclockMetrics.sanitizeOrder(listOf("FEES", "BLOCK"))
        val allOn = { _: Int -> true }
        val sequence = generateSequence(0) { BlockclockMetrics.nextActiveMode(it, "USD", order, allOn) }
            .take(BlockclockMetrics.MODE_COUNT + 1).map { BlockclockMetrics.MODE_KEYS[it] }.toList()
        assertEquals(order + order.first(), sequence)
        assertEquals("FEES", order[3]) // Network section starts with the user's FEES
    }

    @Test
    fun captionsShowTheChangeWhenKnown() {
        val withChange = data.copy(change24hUsd = 2.41, change24hThb = -0.55)
        assertEquals("Market price · +2.4% 24h", BlockclockMetrics.forMode(0, withChange, "USD").caption)
        assertEquals("Market price · -0.6% 24h", BlockclockMetrics.forMode(0, withChange, "THB").caption)
        assertEquals("Sats per 1 dollar · -2.4% 24h", BlockclockMetrics.forMode(1, withChange, "USD").caption)
        // Unknown change: plain caption.
        assertEquals("Market price of bitcoin", BlockclockMetrics.forMode(0, data, "USD").caption)
    }
}

