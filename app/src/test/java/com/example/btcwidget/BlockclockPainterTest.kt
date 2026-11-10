package com.example.btcwidget

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Paints every mode with fake font metrics at several widget shapes and checks the
 * composition: text stays inside the panel, the value clears the label, and the
 * caption sits below the value.
 */
class BlockclockPainterTest {

    private class Box(val l: Float, val t: Float, val r: Float, val b: Float)

    /** Fixed metrics: condensed glyphs 0.42em wide, mono 0.6em; caps 0.72em tall. */
    private class RecordingSurface : DrawSurface {
        val texts = mutableListOf<Pair<String, Box>>()
        var panel: Box? = null

        override fun roundRect(l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int) = Unit
        override fun roundRectGradient(l: Float, t: Float, r: Float, b: Float, radius: Float, top: Int, bottom: Int) {
            panel = Box(l, t, r, b)
        }
        override fun grain(l: Float, t: Float, r: Float, b: Float, radius: Float) = Unit
        override fun circle(cx: Float, cy: Float, radius: Float, color: Int) = Unit

        override fun text(
            text: String, x: Float, baseline: Float, font: DrawSurface.Font,
            size: Float, color: Int, align: DrawSurface.Align, tracking: Float
        ) {
            val w = measure(text, font, size, tracking)
            val left = when (align) {
                DrawSurface.Align.LEFT -> x
                DrawSurface.Align.CENTER -> x - w / 2
                DrawSurface.Align.RIGHT -> x - w
            }
            texts += text to Box(left, baseline - capHeight(font, size), left + w, baseline)
        }

        override fun measure(text: String, font: DrawSurface.Font, size: Float, tracking: Float): Float {
            val em = if (font == DrawSurface.Font.CONDENSED) 0.42f else 0.6f
            return text.length * size * (em + tracking)
        }

        override fun capHeight(font: DrawSurface.Font, size: Float) = 0.72f * size
    }

    private val data = PriceData(
        btcUsd = 74_455.0, btcThb = 2_415_000.0, moscowTime = 1343, mstrUsd = 412.6,
        mstrBtc = 0.005574, blockHeight = 925_000, mstrBtcHeld = 640_031.0,
        btcCirculation = 19_912_345.0, timestamp = 0L, feeFastest = 127, feeHalfHour = 5,
        feeHour = 3, difficultyChange = -12.54, difficultyProgress = 40.0, hashRate = 1812.3,
        lightningCapacity = 4_900.0
    )

    private fun allScenes(): List<BlockclockScene> {
        val metrics = listOf("USD", "THB").flatMap { currency ->
            (0 until BlockclockMetrics.MODE_COUNT).map { mode ->
                // A large stack checks the widest values fit.
                BlockclockScene(BlockclockMetrics.forMode(mode, data, currency, stackSats = 123_456_789_012), stale = true)
            }
        }
        val halving = (0..2).map {
            BlockclockScene(BlockclockMetrics.halving(it, data.blockHeight), progress = 0.4f)
        }
        return metrics + halving
    }

    // 2x1, 4x1, 2x2 and a narrow 1x1-ish shape
    private val shapes = listOf(395f to 197f, 560f to 140f, 300f to 260f, 160f to 150f)

    @Test
    fun compositionHoldsForEveryModeShapeAndTheme() {
        val scenes = AppTheme.entries.flatMap { theme -> allScenes().map { it.copy(palette = theme.palette) } }
        for (scene in scenes) {
            for ((w, h) in shapes) {
                val s = RecordingSurface()
                BlockclockPainter.paint(s, w, h, scene)
                val panel = s.panel!!
                val where = "${scene.display} at ${w}x$h, ${if (scene.palette.crt) "retro" else "classic"}"

                for ((text, box) in s.texts) {
                    assertTrue(
                        "'$text' leaves the panel: $where",
                        box.l >= panel.l && box.r <= panel.r && box.t >= panel.t && box.b <= panel.b
                    )
                }
                val d = scene.display
                val value = s.texts.first { it.first == d.value }.second
                val label = s.texts.first { it.first == d.labelTop }.second
                assertTrue("value overlaps label: $where", value.l >= label.r)

                val caption = s.texts.firstOrNull { it.first.startsWith(d.caption.take(3)) && it.second.t > value.t }
                if (caption != null) assertTrue("caption overlaps value: $where", caption.second.t > value.b)
            }
        }
    }
}
