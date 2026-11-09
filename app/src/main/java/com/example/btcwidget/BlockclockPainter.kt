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
 * Paints a blockclock: a black body with a stacked label on the left, the value in
 * large white type, an optional unit after it, and a caption strip underneath.
 * All geometry scales from the value-row height H, and the device keeps the same
 * proportions in every mode so cycling modes never changes its size.
 */
object BlockclockPainter {
    // Palette (mirrors res/values/colors.xml)
    const val BODY_TOP = 0xFF141414.toInt()
    const val BODY_BOTTOM = 0xFF030303.toInt()
    const val BODY_EDGE = 0xFF2A2A2A.toInt()
    const val VALUE = 0xFFF4F4F2.toInt()
    const val LABEL = 0xFFB4B4B0.toInt()
    const val RULE = 0xFF303030.toInt()
    const val CAPTION = 0xFF7E7E7A.toInt()
    const val DOT_ON = 0xFFF4F4F2.toInt()
    const val DOT_OFF = 0xFF3A3A3A.toInt()
    const val TRACK = 0xFF2A2A2A.toInt()
    const val ALERT = 0xFFE5534B.toInt()

    // Geometry in units of the value-row height H
    private const val PAD_X = 0.24f
    private const val PAD_TOP = 0.2f
    private const val LABEL_W = 0.82f
    private const val RULE_GAP = 0.2f
    private const val VALUE_W = 3.75f
    private const val CAPTION_H = 0.38f
    private const val PROGRESS_GAP = 0.1f
    private const val PROGRESS_H = 0.045f
    private const val BODY_RADIUS = 0.24f

    private fun hasCaption(scene: BlockclockScene) =
        scene.display.caption.isNotEmpty() || scene.stale || scene.modeCount > 1

    private const val WIDTH_UNITS = PAD_X + LABEL_W + 2 * RULE_GAP + VALUE_W + PAD_X

    private fun heightUnits(scene: BlockclockScene): Float =
        PAD_TOP + 1f +
            (if (scene.progress != null) PROGRESS_GAP + PROGRESS_H else 0f) +
            (if (hasCaption(scene)) CAPTION_H else PAD_TOP)

    /** Width / height of the device. */
    fun aspectRatio(scene: BlockclockScene): Float = WIDTH_UNITS / heightUnits(scene)

    /** Paints the device as large as fits in [width] x [height], centered. */
    fun paint(s: DrawSurface, width: Float, height: Float, scene: BlockclockScene) {
        val h = min(width / WIDTH_UNITS, height / heightUnits(scene))
        val devW = WIDTH_UNITS * h
        val devH = heightUnits(scene) * h
        val x0 = (width - devW) / 2f
        val y0 = (height - devH) / 2f
        val hair = max(1f, 0.012f * h)

        // Body
        s.roundRectGradient(x0, y0, x0 + devW, y0 + devH, BODY_RADIUS * h, BODY_TOP, BODY_BOTTOM)
        s.roundRectStroke(x0 + hair / 2, y0 + hair / 2, x0 + devW - hair / 2, y0 + devH - hair / 2, BODY_RADIUS * h, BODY_EDGE, hair)

        val d = scene.display
        val top = y0 + PAD_TOP * h
        val labelL = x0 + PAD_X * h
        val ruleX = labelL + (LABEL_W + RULE_GAP) * h
        val valueL = ruleX + RULE_GAP * h

        drawLabel(s, labelL, top, LABEL_W * h, h, d.labelTop, d.labelBottom)
        s.roundRect(ruleX - hair / 2, top + 0.12f * h, ruleX + hair / 2, top + 0.88f * h, 0f, RULE)
        drawValue(s, valueL, top, VALUE_W * h, h, d.value, d.suffix)

        var y = top + h

        scene.progress?.let { p ->
            y += PROGRESS_GAP * h
            val l = x0 + PAD_X * h
            val r = x0 + devW - PAD_X * h
            val barH = PROGRESS_H * h
            s.roundRect(l, y, r, y + barH, barH / 2, TRACK)
            val fill = l + (r - l) * p.coerceIn(0f, 1f)
            if (fill - l >= barH) s.roundRect(l, y, fill, y + barH, barH / 2, VALUE)
            y += barH
        }

        if (hasCaption(scene)) drawCaptionStrip(s, x0, y, devW, h, scene)
    }

    /** Font size at which [font]'s cap height equals [cap]. */
    private fun sizeForCap(s: DrawSurface, font: DrawSurface.Font, cap: Float): Float =
        100f * cap / s.capHeight(font, 100f)

    /** Value in large type with an optional smaller unit, centered in the value area. */
    private fun drawValue(s: DrawSurface, l: Float, t: Float, w: Float, h: Float, value: String, suffix: String) {
        val valueFont = DrawSurface.Font.DIGIT
        val unitFont = DrawSurface.Font.MONO_BOLD
        var valueSize = sizeForCap(s, valueFont, 0.62f * h)
        var unitSize = sizeForCap(s, unitFont, 0.17f * h)
        var unitGap = if (suffix.isEmpty()) 0f else 0.1f * h

        fun total() = s.measure(value, valueFont, valueSize) + unitGap + s.measure(suffix, unitFont, unitSize)
        val full = total()
        if (full > w) {
            val k = w / full
            valueSize *= k
            unitSize *= k
            unitGap *= k
        }

        val baseline = t + h / 2 + s.capHeight(valueFont, valueSize) / 2
        val x = l + (w - total()) / 2
        s.text(value, x, baseline, valueFont, valueSize, VALUE, DrawSurface.Align.LEFT)
        if (suffix.isNotEmpty()) {
            val ux = x + s.measure(value, valueFont, valueSize) + unitGap
            s.text(suffix, ux, baseline, unitFont, unitSize, LABEL, DrawSurface.Align.LEFT)
        }
    }

    /** Two-line stacked label such as BTC over USD, with a short rule between. */
    private fun drawLabel(s: DrawSurface, l: Float, t: Float, w: Float, h: Float, top: String, bottom: String) {
        val font = DrawSurface.Font.MONO_BOLD
        var size = sizeForCap(s, font, 0.15f * h)
        val widest = max(s.measure(top, font, size), s.measure(bottom, font, size))
        if (widest > w) size *= w / widest
        val cap = s.capHeight(font, size)
        val cx = l + w / 2
        val cy = t + h / 2

        if (bottom.isEmpty()) {
            s.text(top, cx, cy + cap / 2, font, size, LABEL, DrawSurface.Align.CENTER)
            return
        }
        val gap = max(cap * 0.8f, 0.08f * h)
        s.text(top, cx, cy - gap, font, size, LABEL, DrawSurface.Align.CENTER)
        val line = max(1f, 0.012f * h)
        s.roundRect(l + 0.22f * w, cy - line / 2, l + 0.78f * w, cy + line / 2, 0f, RULE)
        s.text(bottom, cx, cy + gap + cap, font, size, LABEL, DrawSurface.Align.CENTER)
    }

    private fun drawCaptionStrip(s: DrawSurface, x0: Float, top: Float, devW: Float, h: Float, scene: BlockclockScene) {
        val font = DrawSurface.Font.MONO
        val tracking = 0.1f
        val left = x0 + PAD_X * h
        var right = x0 + devW - PAD_X * h
        val mid = top + CAPTION_H * h / 2

        // Mode dots, right-aligned: show that a tap cycles modes and where we are.
        if (scene.modeCount > 1) {
            val r = 0.03f * h
            val step = 0.1f * h
            var cx = right - r
            for (i in scene.modeCount - 1 downTo 0) {
                s.circle(cx, mid, r, if (i == scene.modeIndex) DOT_ON else DOT_OFF)
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
