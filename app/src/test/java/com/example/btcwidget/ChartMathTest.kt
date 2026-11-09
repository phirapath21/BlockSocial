package com.example.btcwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartMathTest {

    // Hourly points over 8 days, price = hour index.
    private val week = (0..192).map { PricePoint(time = 1_700_000_000L + it * 3600L, price = 100.0 + it) }

    @Test
    fun slice_keepsTheWindowAndConverts() {
        val day = ChartMath.slice(week, ChartRange.DAY)
        assertEquals(25, day.size) // inclusive of both ends
        assertEquals(week.last(), day.last())
        val thb = ChartMath.slice(week, ChartRange.DAY, factor = 35.0)
        assertEquals(week.last().price * 35.0, thb.last().price, 1e-9)
        assertEquals(169, ChartMath.slice(week, ChartRange.WEEK).size)
        assertEquals(emptyList<PricePoint>(), ChartMath.slice(emptyList(), ChartRange.DAY))
    }

    @Test
    fun summary() {
        val pts = listOf(PricePoint(0, 100.0), PricePoint(1, 120.0), PricePoint(2, 90.0), PricePoint(3, 110.0))
        val s = ChartMath.summary(pts)!!
        assertEquals(120.0, s.high, 0.0)
        assertEquals(90.0, s.low, 0.0)
        assertEquals(10.0, s.changePct, 1e-9)
        assertNull(ChartMath.summary(pts.take(1)))
    }

    @Test
    fun scaling() {
        val pts = listOf(PricePoint(0, 0.0), PricePoint(10, 0.0), PricePoint(40, 0.0))
        assertEquals(0f, ChartMath.x(pts, 0, 200f), 0f)
        assertEquals(50f, ChartMath.x(pts, 1, 200f), 1e-4f)
        assertEquals(200f, ChartMath.x(pts, 2, 200f), 0f)
        // High at the top padding, low at the bottom padding.
        assertEquals(12f, ChartMath.y(200.0, 100.0, 200.0, 100f), 1e-4f)
        assertEquals(88f, ChartMath.y(100.0, 100.0, 200.0, 100f), 1e-4f)
        // Flat series doesn't divide by zero.
        assertEquals(12f, ChartMath.y(5.0, 5.0, 5.0, 100f), 1e-4f)
    }

    @Test
    fun nearestIndex_snapsToClosestPoint() {
        val pts = listOf(PricePoint(0, 1.0), PricePoint(10, 1.0), PricePoint(40, 1.0))
        assertEquals(0, ChartMath.nearestIndex(pts, 10f, 200f))
        assertEquals(1, ChartMath.nearestIndex(pts, 60f, 200f))
        assertEquals(2, ChartMath.nearestIndex(pts, 199f, 200f))
        assertEquals(-1, ChartMath.nearestIndex(emptyList(), 5f, 200f))
    }

    @Test
    fun cacheRoundTrip() {
        val pts = week.take(5)
        assertEquals(pts, ChartMath.decode(ChartMath.encode(pts)))
        assertEquals(emptyList<PricePoint>(), ChartMath.decode(null))
        assertEquals(listOf(PricePoint(1, 2.0)), ChartMath.decode("1:2.0,bad,3:x"))
    }
}
