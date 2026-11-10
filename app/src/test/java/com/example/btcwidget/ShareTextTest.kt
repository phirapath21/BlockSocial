package com.example.btcwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareTextTest {
    private val data = PriceData(
        btcUsd = 74_455.0, btcThb = 2_415_000.0, moscowTime = 1343, mstrUsd = 412.6,
        mstrBtc = 0.005574, blockHeight = 925_000, mstrBtcHeld = 640_031.0,
        btcCirculation = 19_912_345.0, timestamp = 0L, feeFastest = 7, feeHalfHour = 5,
        feeHour = 3, change24hUsd = 1.24, change24hThb = 0.8
    )

    private fun line(key: String, currency: String = "USD") =
        ShareText.forKey(key, data, currency)!!.lines().first()

    @Test
    fun priceIncludesChangeAndBlock() {
        assertEquals("Bitcoin: \$74,455 (+1.2% in 24h) · block 925,000", line("PRICE"))
        assertEquals("Bitcoin: ฿2,415,000 (+0.8% in 24h) · block 925,000", line("PRICE", "THB"))
    }

    @Test
    fun priceWithoutChangeOmitsIt() {
        val text = ShareText.forKey("PRICE", data.copy(change24hUsd = Double.NaN), "USD")!!
        assertEquals("Bitcoin: \$74,455 · block 925,000", text.lines().first())
    }

    @Test
    fun halvingCountsBlocksDaysAndTargetBlock() {
        assertEquals("Next bitcoin halving in 125,000 blocks (≈ 868 days), at block 1,050,000", line("HALVING"))
    }

    @Test
    fun otherMetrics() {
        assertEquals("Bitcoin block height: 925,000", line("BLOCK"))
        assertEquals("1 USD = 1,343 sats · block 925,000", line("SATS"))
        assertEquals("1 THB = 41 sats · block 925,000", line("SATS", "THB"))
        assertEquals("Bitcoin fees: high 7 · mid 5 · low 3 sat/vB · block 925,000", line("FEES"))
    }

    @Test
    fun stackIsNeverShared() {
        assertNull(ShareText.forKey("STACK", data, "USD"))
        assertTrue("STACK" !in ShareText.SHAREABLE)
    }

    @Test
    fun everyShareableKeyHasTextAndTag() {
        for (key in ShareText.SHAREABLE) {
            assertTrue(key, ShareText.forKey(key, data, "USD")!!.endsWith("#bitcoin"))
        }
    }
}
