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
        lightningCapacity = 5100.0
    )

    @Test
    fun allSourcesSucceed_usesFreshValues() {
        val result = PriceRepository.mergeWithCache(allFresh, cached, now = 2_000L)!!
        assertEquals(100_000.0, result.btcUsd, 0.0)
        assertEquals(1000, result.moscowTime)
        assertEquals(0.004, result.mstrBtc, 1e-9)
        assertEquals(900_100, result.blockHeight)
        assertEquals(10, result.feeFastest)
        assertEquals(2_000L, result.timestamp)
        assertTrue(result.lastFetchSuccessful)
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
}
