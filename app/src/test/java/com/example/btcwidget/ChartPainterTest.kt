package com.example.btcwidget

import org.junit.Assert.assertTrue
import org.junit.Test

/** The chart widget keeps all text inside its panel at every shape, with or without history. */
class ChartPainterTest {

    private class Recorder : DrawSurface {
        val boxes = mutableListOf<Pair<String, FloatArray>>()
        var lines = 0
        override fun roundRect(l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int) = Unit
        override fun roundRectGradient(l: Float, t: Float, r: Float, b: Float, radius: Float, top: Int, bottom: Int) = Unit
        override fun grain(l: Float, t: Float, r: Float, b: Float, radius: Float) = Unit
        override fun circle(cx: Float, cy: Float, radius: Float, color: Int) = Unit
        override fun polyline(xs: FloatArray, ys: FloatArray, color: Int, width: Float) { lines++ }
        override fun text(text: String, x: Float, baseline: Float, font: DrawSurface.Font, size: Float, color: Int, align: DrawSurface.Align, tracking: Float) {
            val w = measure(text, font, size, tracking)
            val l = when (align) { DrawSurface.Align.LEFT -> x; DrawSurface.Align.CENTER -> x - w / 2; DrawSurface.Align.RIGHT -> x - w }
            boxes += text to floatArrayOf(l, baseline - capHeight(font, size), l + w, baseline)
        }
        override fun measure(text: String, font: DrawSurface.Font, size: Float, tracking: Float) =
            text.length * size * ((if (font == DrawSurface.Font.CONDENSED) 0.42f else 0.6f) + tracking)
        override fun capHeight(font: DrawSurface.Font, size: Float) = 0.72f * size
    }

    private val day = (0..24).map { PricePoint(1_700_000_000L + it * 3600L, 70_000.0 + it * 50) }

    @Test
    fun textFitsAtEveryShape() {
        val scenes = listOf(
            ChartScene(day, "USD", ChartRange.DAY, null),
            ChartScene(day.map { it.copy(price = it.price * 35) }, "THB", ChartRange.WEEK, null, stale = true),
            ChartScene(emptyList(), "USD", ChartRange.DAY, 74_455.0),
            ChartScene(emptyList(), "USD", ChartRange.DAY, null)
        )
        val themed = AppTheme.entries.flatMap { theme -> scenes.map { it.copy(palette = theme.palette) } }
        for (scene in themed) for ((w, h) in listOf(750f to 450f, 450f to 450f, 1000f to 230f, 250f to 250f)) {
            val r = Recorder()
            ChartPainter.paint(r, w, h, scene)
            for ((text, b) in r.boxes) {
                assertTrue("'$text' leaves the panel at ${w}x$h", b[0] >= 0 && b[2] <= w && b[1] >= 0 && b[3] <= h)
            }
            if (scene.points.size >= 2 && h >= 230f) assertTrue("no line drawn at ${w}x$h", r.lines == 1)
        }
    }
}
