package com.example.btcwidget.alerts

import com.example.btcwidget.BlockclockMetrics
import com.example.btcwidget.PriceData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceAlertsTest {

    private fun data(usd: Double, thb: Double = usd * 33, fee: Int = 10, ok: Boolean = true) = PriceData(
        btcUsd = usd, btcThb = thb, moscowTime = 0, mstrUsd = 0.0, mstrBtc = 0.0, blockHeight = 0,
        mstrBtcHeld = 0.0, btcCirculation = 0.0, timestamp = 0L, feeFastest = fee, lastFetchSuccessful = ok
    )

    private val above = AlertRule(AlertKind.PRICE_ABOVE, 100_000.0, "USD", enabled = true)
    private val below = AlertRule(AlertKind.PRICE_BELOW, 60_000.0, "USD", enabled = true)
    private val fees = AlertRule(AlertKind.FEE_AT_OR_BELOW, 5.0, enabled = true)

    @Test
    fun firesOnceOnCrossingThenRearms() {
        var met = emptySet<AlertKind>()
        fun step(usd: Double): List<AlertKind> {
            val r = PriceAlerts.check(listOf(above), data(usd), met)
            met = r.nowMet
            return r.toNotify.map { it.kind }
        }
        assertEquals(emptyList<AlertKind>(), step(95_000.0))
        assertEquals(listOf(AlertKind.PRICE_ABOVE), step(101_000.0)) // crossing
        assertEquals(emptyList<AlertKind>(), step(105_000.0))         // still above: quiet
        assertEquals(emptyList<AlertKind>(), step(99_000.0))          // back below: re-arms
        assertEquals(listOf(AlertKind.PRICE_ABOVE), step(100_500.0)) // crosses again
    }

    @Test
    fun belowAndFeeRules() {
        val r = PriceAlerts.check(listOf(above, below, fees), data(59_000.0, fee = 4), emptySet())
        assertEquals(setOf(AlertKind.PRICE_BELOW, AlertKind.FEE_AT_OR_BELOW), r.toNotify.map { it.kind }.toSet())
        // Fee rule is inclusive.
        assertTrue(PriceAlerts.isMet(fees, data(70_000.0, fee = 5)))
    }

    @Test
    fun thbRuleUsesThbPrice() {
        val thb = AlertRule(AlertKind.PRICE_ABOVE, 3_000_000.0, "THB", enabled = true)
        assertTrue(!PriceAlerts.isMet(thb, data(usd = 95_000.0, thb = 2_900_000.0)))
        assertTrue(PriceAlerts.isMet(thb, data(usd = 95_000.0, thb = 3_100_000.0)))
    }

    @Test
    fun staleDataNeverNotifiesAndKeepsState() {
        val r = PriceAlerts.check(listOf(above), data(150_000.0, ok = false), emptySet())
        assertEquals(emptyList<AlertRule>(), r.toNotify)
        assertEquals(emptySet<AlertKind>(), r.nowMet)
    }

    @Test
    fun disabledOrUnsetRulesAreIgnored() {
        val rules = listOf(above.copy(enabled = false), below.copy(threshold = 0.0))
        assertEquals(emptyList<AlertRule>(), PriceAlerts.check(rules, data(10_000.0), emptySet()).toNotify)
    }

    @Test
    fun textFormatting() {
        assertEquals("BTC above $100,000", PriceAlerts.title(above))
        assertEquals("Now ฿3,100,000", PriceAlerts.body(above.copy(currency = "THB"), data(95_000.0, thb = 3_100_000.0)))
        assertEquals("Fees down to 5 sat/vB", PriceAlerts.title(fees))
    }

    // ---- network events ----

    private fun atHeight(h: Int, ok: Boolean = true) = data(70_000.0, ok = ok).copy(blockHeight = h, difficultyChange = 2.5)

    @Test
    fun halvingSoon_firesOnceADayAheadAndRearmsNextEpoch() {
        val rule = AlertRule(AlertKind.HALVING_SOON, AlertKind.HALVING_SOON.defaultThreshold, enabled = true)
        var met = emptySet<AlertKind>()
        fun step(h: Int): Boolean {
            val r = PriceAlerts.check(listOf(rule), atHeight(h), met)
            met = r.nowMet
            return r.toNotify.isNotEmpty()
        }
        assertEquals(false, step(1_049_800)) // 200 blocks to go
        assertEquals(true, step(1_049_860))  // 140 to go: notify
        assertEquals(false, step(1_049_950)) // still close: quiet
        assertEquals(false, step(1_050_010)) // halving passed: re-armed, far away
        assertEquals(true, step(1_259_900))  // next epoch, 100 to go
    }

    @Test
    fun retargetSoon_usesTheAdjustmentPeriod() {
        val rule = AlertRule(AlertKind.RETARGET_SOON, AlertKind.RETARGET_SOON.defaultThreshold, enabled = true)
        assertEquals(144, BlockclockMetrics.blocksToRetarget(2016 * 500 - 144))
        assertTrue(PriceAlerts.isMet(rule, atHeight(2016 * 500 - 144)))
        assertTrue(!PriceAlerts.isMet(rule, atHeight(2016 * 500 - 145)))
        assertTrue(!PriceAlerts.isMet(rule, atHeight(2016 * 500))) // just adjusted: 2016 to go
        assertEquals("100 blocks to go. Estimated change +2.5%.", PriceAlerts.body(rule, atHeight(2016 * 500 - 100)))
    }

    @Test
    fun networkEvents_ignoreMissingHeightAndStaleData() {
        val rule = AlertRule(AlertKind.HALVING_SOON, 144.0, enabled = true)
        assertTrue(!PriceAlerts.isMet(rule, atHeight(0)))
        assertEquals(emptyList<AlertRule>(), PriceAlerts.check(listOf(rule), atHeight(1_049_900, ok = false), emptySet()).toNotify)
    }
}

