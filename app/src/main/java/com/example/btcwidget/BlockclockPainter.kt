package com.example.btcwidget

import kotlin.math.max
import kotlin.math.min

/**
 * Minimal drawing API the blockclock painter needs. Android implements it on
 * android.graphics.Canvas (widgets and app); tests and previews can use any backend.
 * Colors are ARGB ints.
 */
interface DrawSurface {
    enum class Font { DIGIT, MONO, MONO_BOLD }
    enum class Align { LEFT, CENTER, RIGHT }

    fun roundRect(l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int)
    fun roundRectGradient(l: Float, t: Float, r: Float, b: Float, radius: Float, top: Int, bottom: Int)
    fun roundRectStroke(l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int, width: Float)
    fun circle(cx: Float, cy: Float, radius: Float, color: Int)
    fun text(text: String, x: Float, baseline: Float, font: Font, size: Float, color: Int, align: Align, tracking: Float = 0f)

    /** Advance width of [text]; [tracking] is extra spacing per character in ems. */
    fun measure(text: String, font: Font, size: Float, tracking: Float = 0f): Float

    /** Height of a capital/digit glyph ("8") at [size]. */
    fun capHeight(font: Font, size: Float): Float
}

/** Everything one blockclock device shows. */
data class BlockclockScene(
    val display: BlockclockDisplay,
    val stale: Boolean = false,
    /** 0..1 progress bar under the screens, e.g. halving epoch. */
    val progress: Float? = null,
    /** Position among the modes a tap cycles through; dots are drawn when count > 1. */
    val modeIndex: Int = 0,
    val modeCount: Int = 0
)

/**
 * Paints a blockclock: a black body holding a stacked label screen, one e-ink screen
 * per character, an optional unit screen, and a caption strip. All geometry scales
 * from the screen height H so the device looks the same at every size.
 */
object BlockclockPainter {
    // Palette (mirrors res/values/colors.xml)
    const val BODY_TOP = 0xFF181818.toInt()
    const val BODY_BOTTOM = 0xFF070707.toInt()
    const val BODY_EDGE = 0xFF2C2C2C.toInt()
    const val PAPER_TOP = 0xFFEEEBE3.toInt()
    const val PAPER_BOTTOM = 0xFFDCD8CD.toInt()
    const val PAPER_OFF_TOP = 0xFFDFDCD3.toInt()
    const val PAPER_OFF_BOTTOM = 0xFFCFCBC0.toInt()
    const val SCREEN_EDGE = 0x33000000
    const val INK = 0xFF161616.toInt()
    const val CAPTION = 0xFF8D8A82.toInt()
    const val DOT_OFF = 0xFF3A3A3A.toInt()
    const val TRACK = 0xFF2A2A2A.toInt()
    const val ALERT = 0xFFE5534B.toInt()

    // Geometry in units of the screen height H
    private const val TILE_W = 0.64f
    private const val GAP = 0.065f
    private const val PAD = 0.13f
    private const val CAPTION_H = 0.36f
    private const val PROGRESS_GAP = 0.09f
    private const val PROGRESS_H = 0.045f
    private const val BODY_RADIUS = 0.2f
    private const val TILE_RADIUS = 0.05f

    private fun slotCount(scene: BlockclockScene): Int =
        1 + scene.display.valueTiles().size + if (scene.display.suffix.isNotEmpty()) 1 else 0

    private fun hasCaption(scene: BlockclockScene) =
        scene.display.caption.isNotEmpty() || scene.stale || scene.modeCount > 1

    private fun widthUnits(scene: BlockclockScene): Float {
        val n = slotCount(scene)
        return n * TILE_W + (n - 1) * GAP + 2 * PAD
    }

    private fun heightUnits(scene: BlockclockScene): Float =
        PAD + 1f +
            (if (scene.progress != null) PROGRESS_GAP + PROGRESS_H else 0f) +
            (if (hasCaption(scene)) CAPTION_H else PAD)

    /** Width / height of the device. */
    fun aspectRatio(scene: BlockclockScene): Float = widthUnits(scene) / heightUnits(scene)

    /** Paints the device as large as fits in [width] x [height], centered. */
    fun paint(s: DrawSurface, width: Float, height: Float, scene: BlockclockScene) {
        val h = min(width / widthUnits(scene), height / heightUnits(scene))
        val devW = widthUnits(scene) * h
        val devH = heightUnits(scene) * h
        val x0 = (width - devW) / 2f
        val y0 = (height - devH) / 2f
        val hair = max(1f, 0.012f * h)

        // Body
        s.roundRectGradient(x0, y0, x0 + devW, y0 + devH, BODY_RADIUS * h, BODY_TOP, BODY_BOTTOM)
        s.roundRectStroke(x0 + hair / 2, y0 + hair / 2, x0 + devW - hair / 2, y0 + devH - hair / 2, BODY_RADIUS * h, BODY_EDGE, hair)

        // Screens
        val d = scene.display
        val tileW = TILE_W * h
        val top = y0 + PAD * h
        var x = x0 + PAD * h
        fun nextSlot(): Float = x.also { x += tileW + GAP * h }

        drawLabelScreen(s, nextSlot(), top, tileW, h, d.labelTop, d.labelBottom)
        for (tile in d.valueTiles()) drawCharScreen(s, nextSlot(), top, tileW, h, tile)
        if (d.suffix.isNotEmpty()) drawLabelScreen(s, nextSlot(), top, tileW, h, d.suffix, "")

        var y = top + h

        // Progress bar
        scene.progress?.let { p ->
            y += PROGRESS_GAP * h
            val l = x0 + PAD * h
            val r = x0 + devW - PAD * h
            val barH = PROGRESS_H * h
            s.roundRect(l, y, r, y + barH, barH / 2, TRACK)
            val fill = l + (r - l) * p.coerceIn(0f, 1f)
            if (fill - l >= barH) s.roundRect(l, y, fill, y + barH, barH / 2, PAPER_TOP)
            y += barH
        }

        if (hasCaption(scene)) drawCaptionStrip(s, x0, y, devW, h, scene)
    }

    private fun screen(s: DrawSurface, l: Float, t: Float, w: Float, h: Float, on: Boolean) {
        val r = TILE_RADIUS * h
        if (on) s.roundRectGradient(l, t, l + w, t + h, r, PAPER_TOP, PAPER_BOTTOM)
        else s.roundRectGradient(l, t, l + w, t + h, r, PAPER_OFF_TOP, PAPER_OFF_BOTTOM)
        val edge = max(1f, 0.01f * h)
        s.roundRectStroke(l + edge / 2, t + edge / 2, l + w - edge / 2, t + h - edge / 2, r, SCREEN_EDGE, edge)
    }

    /** Font size at which [font]'s cap height equals [cap]. */
    private fun sizeForCap(s: DrawSurface, font: DrawSurface.Font, cap: Float): Float =
        100f * cap / s.capHeight(font, 100f)

    private fun drawCharScreen(s: DrawSurface, l: Float, t: Float, w: Float, h: Float, tile: String) {
        screen(s, l, t, w, h, on = tile.isNotEmpty())
        if (tile.isEmpty()) return

        val hasDot = tile.length > 1 && tile.endsWith(".")
        val glyph = if (hasDot) tile.dropLast(1) else tile
        val font = DrawSurface.Font.DIGIT

        var size = sizeForCap(s, font, 0.56f * h)
        val maxW = (if (hasDot) 0.66f else 0.8f) * w
        val glyphW = s.measure(glyph, font, size)
        if (glyphW > maxW) size *= maxW / glyphW

        val cap = s.capHeight(font, size)
        val baseline = t + h / 2 + cap / 2
        val cx = l + w / 2 - (if (hasDot) 0.08f * w else 0f)
        s.text(glyph, cx, baseline, font, size, INK, DrawSurface.Align.CENTER)

        if (hasDot) {
            val r = 0.042f * h
            s.circle(l + w - 0.12f * w, baseline - r, r, INK)
        }
    }

    private fun drawLabelScreen(s: DrawSurface, l: Float, t: Float, w: Float, h: Float, top: String, bottom: String) {
        screen(s, l, t, w, h, on = true)
        val font = DrawSurface.Font.MONO_BOLD
        var size = sizeForCap(s, font, 0.14f * h)
        val widest = max(s.measure(top, font, size), s.measure(bottom, font, size))
        val maxW = 0.8f * w
        if (widest > maxW) size *= maxW / widest
        val cap = s.capHeight(font, size)
        val cx = l + w / 2
        val cy = t + h / 2

        if (bottom.isEmpty()) {
            s.text(top, cx, cy + cap / 2, font, size, INK, DrawSurface.Align.CENTER)
            return
        }
        val gap = max(cap * 0.75f, 0.07f * h)
        s.text(top, cx, cy - gap, font, size, INK, DrawSurface.Align.CENTER)
        val line = max(1f, 0.014f * h)
        s.roundRect(l + 0.2f * w, cy - line / 2, l + 0.8f * w, cy + line / 2, 0f, INK)
        s.text(bottom, cx, cy + gap + cap, font, size, INK, DrawSurface.Align.CENTER)
    }

    private fun drawCaptionStrip(s: DrawSurface, x0: Float, top: Float, devW: Float, h: Float, scene: BlockclockScene) {
        val font = DrawSurface.Font.MONO
        val tracking = 0.1f
        val left = x0 + PAD * h
        var right = x0 + devW - PAD * h
        val mid = top + CAPTION_H * h / 2

        // Mode dots, right-aligned: show that a tap cycles modes and where we are.
        if (scene.modeCount > 1) {
            val r = 0.03f * h
            val step = 0.1f * h
            var cx = right - r
            for (i in scene.modeCount - 1 downTo 0) {
                s.circle(cx, mid, r, if (i == scene.modeIndex) PAPER_TOP else DOT_OFF)
                cx -= step
            }
            right = cx - 0.06f * h
        }

        var size = sizeForCap(s, font, 0.13f * h)
        val cap = s.capHeight(font, size)
        val baseline = mid + cap / 2
        var x = left

        if (scene.stale) {
            val r = 0.034f * h
            s.circle(x + r, mid, r, ALERT)
            x += 2 * r + 0.05f * h
            val label = "STALE"
            s.text(label, x, baseline, font, size, ALERT, DrawSurface.Align.LEFT, tracking)
            x += s.measure(label, font, size, tracking) + 0.08f * h
        }

        var caption = scene.display.caption.uppercase()
        if (caption.isEmpty()) return
        val room = right - x
        if (room <= 0f) return
        val full = s.measure(caption, font, size, tracking)
        if (full > room) {
            // Shrink a little first, then truncate.
            size = max(size * room / full, size * 0.8f)
            while (caption.length > 1 && s.measure("$caption…", font, size, tracking) > room) {
                caption = caption.dropLast(1)
            }
            if (s.measure(caption, font, size, tracking) > room) return
            if (caption.length < scene.display.caption.length) caption = caption.trimEnd() + "…"
        }
        val centered = scene.modeCount <= 1 && !scene.stale
        if (centered) {
            s.text(caption, (left + right) / 2, baseline, font, size, CAPTION, DrawSurface.Align.CENTER, tracking)
        } else {
            s.text(caption, x, baseline, font, size, CAPTION, DrawSurface.Align.LEFT, tracking)
        }
    }
}
