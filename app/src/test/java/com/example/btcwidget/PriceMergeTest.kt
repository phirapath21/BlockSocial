package com.example.btcwidget

import com.example.btcwidget.PriceRepository.Difficulty
import com.example.btcwidget.PriceRepository.FetchResults
import com.example.btcwidget.PriceRepository.Fees
import com.example.btcwidget.PriceRepository.Rates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceMergeTest {

    private val cached = PriceData(
        btcUsd = 90_000.0,
        btcThb = 3_000_000.0,
        moscowTime = 1111,
        mstrUsd = 300.0,
        mstrBtc = 300.0 / 90_000.0,
        blockHeight = 900_000,
        mstrBtcHeld = 843_738.0,
        btcCirculation = 19_900_000.0,
        timestamp = 1_000L,
        feeFastest = 5,
        feeHalfHour = 4,
        feeHour = 3,
        difficultyChange = 1.5,
        difficultyProgress = 40.0,
        hashRate = 800.0,
        lightningCapacity = 5000.0
    )

    private val allFresh = FetchResults(
        rates = Rates(usd = 100_000.0, thb = 3_500_000.0),
        mstrUsd = 400.0,
        blockHeight = 900_100,
        btcCirculation = 19_910_000.0,
        fees = Fees(10, 8, 6),
        difficulty = Difficulty(-2.0, 55.0),
        hashRate = 850.0,
        lightningCapacity = 5100.0,
        mstrBtcHeld = 850_000.0
    )

    @Test
    fun allSourcesSucceed_usesFreshValues() {
        val result = PriceRepository.mergeWithCache(allFresh, cached, now = 2_000L)!!
        assertEquals(100_000.0, result.btcUsd, 0.0)
        assertEquals(1000, result.moscowTime)
        assertEquals(0.004, result.mstrBtc, 1e-9)
        assertEquals(900_100, result.blockHeight)
        assertEquals(10, result.feeFastest)
        assertEquals(850_000.0, result.mstrBtcHeld, 0.0)
        assertEquals(2_000L, result.timestamp)
        assertTrue(result.lastFetchSuccessful)
    }

    @Test
    fun mstrHoldingsFail_usesCacheThenFallback() {
        val fresh = allFresh.copy(mstrBtcHeld = null)
        assertEquals(843_738.0, PriceRepository.mergeWithCache(fresh, cached, 2_000L)!!.mstrBtcHeld, 0.0)
        val olderCache = cached.copy(mstrBtcHeld = 0.0) // pref missing on older installs
        assertEquals(843_738.0, PriceRepository.mergeWithCache(fresh, olderCache, 2_000L)!!.mstrBtcHeld, 0.0)
        val updatedCache = cached.copy(mstrBtcHeld = 860_000.0)
        assertEquals(860_000.0, PriceRepository.mergeWithCache(fresh, updatedCache, 2_000L)!!.mstrBtcHeld, 0.0)
    }

    @Test
    fun nonCoreSourceFails_keepsCachedValueAndStaysFresh() {
        val fresh = allFresh.copy(mstrUsd = null, fees = null)
        val result = PriceRepository.mergeWithCache(fresh, cached, now = 2_000L)!!
        assertEquals(100_000.0, result.btcUsd, 0.0)
        assertEquals(300.0, result.mstrUsd, 0.0)
        assertEquals(300.0 / 100_000.0, result.mstrBtc, 1e-9) // ratio uses the new BTC price
        assertEquals(5, result.feeFastest)
        assertEquals(2_000L, result.timestamp)
        assertTrue(result.lastFetchSuccessful)
    }

    @Test
    fun priceSourceFails_keepsCachedPriceAndMarksStale() {
        val fresh = allFresh.copy(rates = null)
        val result = PriceRepository.mergeWithCache(fresh, cached, now = 2_000L)!!
        assertEquals(90_000.0, result.btcUsd, 0.0)
        assertEquals(900_100, result.blockHeight) // other fresh values still applied
        assertEquals(1_000L, result.timestamp)     // timestamp reflects the last good price
        assertFalse(result.lastFetchSuccessful)
    }

    @Test
    fun blockHeightFails_marksStale() {
        val result = PriceRepository.mergeWithCache(allFresh.copy(blockHeight = null), cached, 2_000L)!!
        assertEquals(900_000, result.blockHeight)
        assertFalse(result.lastFetchSuccessful)
    }

    @Test
    fun priceFailsWithNoCache_returnsNull() {
        assertNull(PriceRepository.mergeWithCache(FetchResults(), cached = null, now = 2_000L))
    }

    @Test
    fun onlyPriceWithNoCache_fillsDefaults() {
        val fresh = FetchResults(rates = Rates(100_000.0, 3_500_000.0))
        val result = PriceRepository.mergeWithCache(fresh, cached = null, now = 2_000L)!!
        assertEquals(100_000.0, result.btcUsd, 0.0)
        assertEquals(19_850_000.0, result.btcCirculation, 0.0)
        assertEquals(2_000L, result.timestamp)
        assertFalse(result.lastFetchSuccessful)
    }

    // ---- data sources & 24h change ----

    @Test
    fun krakenPriceWinsAndThbIsConvertedFromIt() {
        val fresh = allFresh.copy(krakenUsd = 110_000.0) // Coinbase rates: 100k USD / 3.5M THB
        val result = PriceRepository.mergeWithCache(fresh, cached, now = 2_000L)!!
        assertEquals(110_000.0, result.btcUsd, 0.0)
        assertEquals(3_850_000.0, result.btcThb, 1e-6) // 110k * 35
        assertTrue(result.lastFetchSuccessful)
    }

    @Test
    fun bitkubPriceAndChangeUsedForThb() {
        val fresh = allFresh.copy(bitkub = BitkubTicker(last = 3_600_000.0, percentChange = 1.25),
            dayStats = DayStats(open = 100_000.0, last = 102_000.0))
        val result = PriceRepository.mergeWithCache(fresh, cached, now = 2_000L)!!
        assertEquals(3_600_000.0, result.btcThb, 0.0)
        assertEquals(1.25, result.change24hThb, 1e-9)
        assertEquals(2.0, result.change24hUsd, 1e-9)
    }

    @Test
    fun convertedThbFollowsUsdChange() {
        val fresh = allFresh.copy(dayStats = DayStats(open = 100_000.0, last = 97_000.0))
        val result = PriceRepository.mergeWithCache(fresh, cached, now = 2_000L)!!
        assertEquals(-3.0, result.change24hUsd, 1e-9)
        assertEquals(-3.0, result.change24hThb, 1e-9)
    }

    @Test
    fun changeKeepsCachedValueWhenStatsFail() {
        val withChange = cached.copy(change24hUsd = 1.5, change24hThb = 1.4)
        val result = PriceRepository.mergeWithCache(allFresh, withChange, now = 2_000L)!!
        assertEquals(1.5, result.change24hUsd, 1e-9)
        assertEquals(1.4, result.change24hThb, 1e-9)
        assertTrue(PriceRepository.mergeWithCache(allFresh, cached, 2_000L)!!.change24hUsd.isNaN())
    }

    @Test
    fun krakenAloneCountsAsFreshPrice() {
        val fresh = allFresh.copy(rates = null, krakenUsd = 105_000.0)
        val result = PriceRepository.mergeWithCache(fresh, cached, now = 2_000L)!!
        assertEquals(105_000.0, result.btcUsd, 0.0)
        assertEquals(3_000_000.0, result.btcThb, 0.0) // no fx: keeps cached THB
        assertTrue(result.lastFetchSuccessful)
    }
}

