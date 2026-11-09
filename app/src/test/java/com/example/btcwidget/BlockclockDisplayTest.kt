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
        assertEquals(14, BlockclockMetrics.MODE_COUNT)
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
        assertEquals(listOf("STACK", "PRICE", "SATS", "MSCW"), default.take(4))
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
        assertEquals(listOf("SATS", "MSCW", "PRICE"), market(BlockclockMetrics.move(default, "PRICE", "MSCW")))
        // Drag up onto an earlier item: lands before it.
        assertEquals(listOf("MSCW", "PRICE", "SATS"), market(BlockclockMetrics.move(default, "MSCW", "PRICE")))
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
        assertEquals(listOf("SATS", "PRICE", "MSCW"), market(BlockclockMetrics.moveAmongVisible(default, visible, "PRICE", +1)))
        // First in section: no-op.
        assertEquals(default, BlockclockMetrics.moveAmongVisible(default, visible, "BLOCK", -1))
    }

    @Test
    fun nextActiveMode_followsGroupedUserOrder() {
        val order = BlockclockMetrics.sanitizeOrder(listOf("FEES", "BLOCK"))
        val allOn = { _: Int -> true }
        val start = BlockclockMetrics.MODE_KEYS.indexOf(order.first())
        val sequence = generateSequence(start) { BlockclockMetrics.nextActiveMode(it, "USD", order, hasStack = true, isEnabled = allOn) }
            .take(BlockclockMetrics.MODE_COUNT + 1).map { BlockclockMetrics.MODE_KEYS[it] }.toList()
        assertEquals(order + order.first(), sequence)
        assertEquals("FEES", order[4]) // Network section starts with the user's FEES
    }

    private fun market(order: List<String>) =
        order.filter { BlockclockMetrics.categoryOf(it) == MetricCategory.MARKET }

    // ---- your stack ----

    @Test
    fun stackMode_onlyAvailableWithHoldings() {
        val stack = BlockclockMetrics.MODE_STACK
        assertEquals("STACK", BlockclockMetrics.MODE_KEYS[stack])
        assertEquals(false, BlockclockMetrics.isAvailable(stack, "USD"))
        assertEquals(true, BlockclockMetrics.isAvailable(stack, "USD", hasStack = true))
        // Cycling skips it without holdings.
        val allOn = { _: Int -> true }
        val withoutStack = generateSequence(0) { BlockclockMetrics.nextActiveMode(it, "USD", isEnabled = allOn) }
            .take(30).toSet()
        assertEquals(false, stack in withoutStack)
    }

    @Test
    fun stackDisplay_valueInCurrency() {
        val withChange = data.copy(change24hUsd = 2.0, change24hThb = 2.0)
        val usd = BlockclockMetrics.forMode(BlockclockMetrics.MODE_STACK, withChange, "USD", stackSats = 50_000_000)
        assertEquals("STACK", usd.labelTop)
        assertEquals("$48562", usd.value) // 0.5 * 97,123.4
        assertEquals("0.5 BTC · +2.0% 24h", usd.caption)
        val thb = BlockclockMetrics.forMode(BlockclockMetrics.MODE_STACK, withChange, "THB", stackSats = 50_000_000)
        assertEquals("1.61", thb.value) // 0.5 * 3,215,000 = 1.6075M
        assertEquals("MIL", thb.suffix)
        val small = BlockclockMetrics.forMode(BlockclockMetrics.MODE_STACK, data, "THB", stackSats = 10_000)
        assertEquals("322", small.value)
        assertEquals("", small.suffix)
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

