package com.example.btcwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Draws every mode with fake font metrics and checks that each glyph stays inside
 * the e-ink screen it belongs to and everything stays inside the device body.
 */
class BlockclockPainterTest {

    private class Box(val l: Float, val t: Float, val r: Float, val b: Float)

    /** Fixed metrics: glyphs are 0.62em wide (bold) / 0.6em (mono), digits 0.72em tall. */
    private class RecordingSurface : DrawSurface {
        val screens = mutableListOf<Box>()
        val texts = mutableListOf<Pair<String, Box>>()
        var body: Box? = null

        override fun roundRect(l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int) = Unit
        override fun roundRectGradient(l: Float, t: Float, r: Float, b: Float, radius: Float, top: Int, bottom: Int) {
            if (body == null) body = Box(l, t, r, b) else screens += Box(l, t, r, b)
        }
        override fun roundRectStroke(l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int, width: Float) = Unit
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
            val em = if (font == DrawSurface.Font.DIGIT) 0.62f else 0.6f
            return text.length * size * (em + tracking)
        }

        override fun capHeight(font: DrawSurface.Font, size: Float) = 0.72f * size
    }

    private val data = PriceData(
        btcUsd = 74_020.0, btcThb = 2_415_000.0, moscowTime = 1351, mstrUsd = 412.6,
        mstrBtc = 0.005574, blockHeight = 925_000, mstrBtcHeld = 640_031.0,
        btcCirculation = 19_912_345.0, timestamp = 0L, feeFastest = 127, feeHalfHour = 5,
        feeHour = 3, difficultyChange = -12.54, difficultyProgress = 40.0, hashRate = 1812.3,
        lightningCapacity = 4_900.0
    )

    private fun allScenes(): List<BlockclockScene> {
        val metrics = listOf("USD", "THB").flatMap { currency ->
            (0 until BlockclockMetrics.MODE_COUNT).map { mode ->
                BlockclockScene(BlockclockMetrics.forMode(mode, data, currency), stale = true, modeIndex = mode, modeCount = 13)
            }
        }
        val halving = (0..2).map {
            BlockclockScene(BlockclockMetrics.halving(it, data.blockHeight), progress = 0.4f, modeIndex = it, modeCount = 3)
        }
        return metrics + halving
    }

    @Test
    fun glyphsStayInsideTheirScreens() {
        for (scene in allScenes()) {
            for ((w, h) in listOf(400f to 110f, 1000f to 200f, 300f to 300f)) {
                val s = RecordingSurface()
                BlockclockPainter.paint(s, w, h, scene)
                val body = s.body!!

                for ((text, box) in s.texts) {
                    val screen = s.screens.firstOrNull { box.l >= it.l - 0.5f && box.r <= it.r + 0.5f && box.t >= it.t - 0.5f && box.b <= it.b + 0.5f }
                    val inBody = box.l >= body.l && box.r <= body.r && box.t >= body.t && box.b <= body.b
                    val isCaption = s.screens.none { box.t < it.b && box.b > it.t }
                    assertTrue("'$text' of ${scene.display} overflows at ${w}x$h", screen != null || (isCaption && inBody))
                }
            }
        }
    }

    @Test
    fun deviceFitsAndKeepsAspectRatio() {
        val scene = allScenes().first()
        val s = RecordingSurface()
        BlockclockPainter.paint(s, 1000f, 1000f, scene)
        val body = s.body!!
        assertEquals(BlockclockPainter.aspectRatio(scene), (body.r - body.l) / (body.b - body.t), 0.01f)
        assertEquals(1000f, body.r - body.l, 0.5f) // width-limited, centered vertically
        assertEquals(500f, (body.t + body.b) / 2, 0.5f)
    }

    @Test
    fun sevenScreensForStandardModes() {
        val s = RecordingSurface()
        BlockclockPainter.paint(s, 700f, 200f, BlockclockScene(BlockclockMetrics.forMode(0, data, "USD")))
        assertEquals(7, s.screens.size)
    }
}
