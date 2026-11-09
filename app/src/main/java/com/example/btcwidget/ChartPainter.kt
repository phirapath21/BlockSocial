package com.example.btcwidget

import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** What the price-chart widget shows. */
data class ChartScene(
    /** Price history in the display currency, oldest first. */
    val points: List<PricePoint>,
    val currency: String,
    val range: ChartRange,
    /** Current price; falls back to the last history point. */
    val price: Double?,
    val stale: Boolean = false
)

/**
 * Paints the price-chart widget in the blockclock style: the same dark grained panel,
 * a small pair label, the price in the condensed face, the change over the range, and
 * a sparkline (2-unit line, faint area, end dot) across the lower part. A single
 * series, so no legend or axes: the header names it.
 */
object ChartPainter {
    private val F = DrawSurface.Font.CONDENSED
    private val M = DrawSurface.Font.MONO

    private fun sizeForCap(s: DrawSurface, font: DrawSurface.Font, cap: Float): Float =
        100f * cap / s.capHeight(font, 100f)

    fun formatPrice(value: Double, currency: String): String =
        // The bundled face has no baht sign; the THB label says the currency.
        if (currency == "THB") String.format(Locale.US, "%,.0f", value)
        else String.format(Locale.US, "$%,.0f", value)

    fun paint(s: DrawSurface, width: Float, height: Float, scene: ChartScene) {
        val radius = 0.08f * min(width, height)
        s.roundRectGradient(0f, 0f, width, height, radius, BlockclockPainter.BG_TOP, BlockclockPainter.BG_BOTTOM)
        s.grain(0f, 0f, width, height, radius)

        val unit = min(height, width * 0.6f)
        val padX = max(0.06f * width, 0.08f * unit)
        val padTop = 0.1f * unit
        val summary = ChartMath.summary(scene.points)
        val price = scene.price ?: summary?.last

        // Header: pair label, then price; change and range on the right.
        val labelCap = 0.065f * unit
        val labelSize = sizeForCap(s, M, labelCap)
        var y = padTop + labelCap
        val pair = "BTC/${scene.currency}"
        s.text(pair, padX, y, M, labelSize, BlockclockPainter.CAPTION, DrawSurface.Align.LEFT, 0.08f)
        if (scene.stale) {
            val r = 0.3f * labelCap
            s.circle(padX + s.measure(pair, M, labelSize, 0.08f) + 3 * r, y - labelCap / 2, r, BlockclockPainter.ALERT)
        }
        val change = summary?.let { BlockclockMetrics.formatChange(it.changePct) }
        val right = listOfNotNull(change, scene.range.label).joinToString(" · ")
        s.text(right, width - padX, y, M, labelSize, BlockclockPainter.CAPTION, DrawSurface.Align.RIGHT, 0.08f)

        val priceCap = 0.2f * unit
        var priceSize = sizeForCap(s, F, priceCap)
        val priceText = price?.let { formatPrice(it, scene.currency) } ?: "--"
        val maxPriceW = width - 2 * padX
        val w = s.measure(priceText, F, priceSize)
        if (w > maxPriceW) priceSize *= maxPriceW / w
        y += 0.05f * unit + s.capHeight(F, priceSize)
        s.text(priceText, padX, y, F, priceSize, BlockclockPainter.INK, DrawSurface.Align.LEFT)

        // Plot area below the header.
        val top = y + 0.1f * unit
        val bottom = height - 0.1f * unit
        val left = padX
        val plotRight = width - padX
        if (summary == null || bottom - top < 0.12f * unit) {
            if (summary == null && bottom > top) {
                val text = "No history yet"
                val cap = 0.06f * unit
                var size = sizeForCap(s, M, cap)
                val tw = s.measure(text, M, size)
                if (tw > plotRight - left) size *= (plotRight - left) / tw
                s.text(
                    text, width / 2, (top + bottom) / 2 + s.capHeight(M, size) / 2,
                    M, size, BlockclockPainter.CAPTION, DrawSurface.Align.CENTER
                )
            }
            return
        }
        val plotW = plotRight - left
        val plotH = bottom - top
        val n = scene.points.size
        val xs = FloatArray(n) { left + ChartMath.x(scene.points, it, plotW) }
        val ys = FloatArray(n) { top + ChartMath.y(scene.points[it].price, summary.low, summary.high, plotH, 0.06f) }

        // Area wash down to the bottom edge, then the 2-unit line and an end dot.
        val ax = FloatArray(n + 2)
        val ay = FloatArray(n + 2)
        xs.copyInto(ax)
        ys.copyInto(ay)
        ax[n] = xs.last(); ay[n] = bottom
        ax[n + 1] = xs.first(); ay[n + 1] = bottom
        s.polygon(ax, ay, WASH)
        val line = max(1.5f, 0.012f * unit)
        s.polyline(xs, ys, BlockclockPainter.INK, line)
        val r = max(2.5f, 0.02f * unit)
        s.circle(xs.last(), ys.last(), r + line, BlockclockPainter.BG_BOTTOM)
        s.circle(xs.last(), ys.last(), r, BlockclockPainter.INK)
    }

    /** INK at ~10% opacity. */
    private const val WASH = 0x1AD7DCDF
}
