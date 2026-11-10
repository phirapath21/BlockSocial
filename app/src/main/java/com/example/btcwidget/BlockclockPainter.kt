package com.example.btcwidget

import kotlin.math.max
import kotlin.math.min

/**
 * Minimal drawing API the blockclock painter needs. Android implements it on
 * android.graphics.Canvas (widgets and app); tests and previews can use any backend.
 * Colors are ARGB ints.
 */
interface DrawSurface {
    /** CONDENSED: tall narrow display face (Oswald Light). MONO: captions. PIXEL: terminal face (VT323). */
    enum class Font { CONDENSED, MONO, PIXEL }
    enum class Align { LEFT, CENTER, RIGHT }

    fun roundRect(l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int)
    fun roundRectGradient(l: Float, t: Float, r: Float, b: Float, radius: Float, top: Int, bottom: Int)

    /** Fine film-grain texture over the rounded rect. */
    fun grain(l: Float, t: Float, r: Float, b: Float, radius: Float)
    fun circle(cx: Float, cy: Float, radius: Float, color: Int)
    fun text(text: String, x: Float, baseline: Float, font: Font, size: Float, color: Int, align: Align, tracking: Float = 0f)

    /** Advance width of [text]; [tracking] is extra spacing per character in ems. */
    fun measure(text: String, font: Font, size: Float, tracking: Float = 0f): Float

    /** Height of a capital/digit glyph ("8") at [size]. */
    fun capHeight(font: Font, size: Float): Float

    /** Open line through the points, round joins and caps. */
    fun polyline(xs: FloatArray, ys: FloatArray, color: Int, width: Float) {}

    /** Filled closed shape through the points. */
    fun polygon(xs: FloatArray, ys: FloatArray, color: Int) {}

    /** Horizontal lines of [color], one every [pitch], over the rounded rect. */
    fun scanlines(l: Float, t: Float, r: Float, b: Float, radius: Float, pitch: Float, color: Int) {}

    /** Soft halo of [color] around text and lines drawn after this; radius 0 turns it off. */
    fun glow(color: Int, radius: Float) {}
}

/** Everything one blockclock shows. */
data class BlockclockScene(
    val display: BlockclockDisplay,
    val stale: Boolean = false,
    /** 0..1 progress bar between the value and the caption, e.g. halving epoch. */
    val progress: Float? = null,
    val palette: Palette = Palette.CLASSIC
)

/**
 * Paints a blockclock filling the given box: a dark panel with a stacked label beside
 * a tall value, and a small caption underneath, in the scene's [Palette].
 * Sizes derive from the value's cap height V, so the composition is identical at
 * every size; the value shrinks only when a long number would not fit the width.
 */
object BlockclockPainter {
    private const val VALUE_OF_HEIGHT = 0.5f      // value cap height / panel height
    private const val VALUE_OF_HEIGHT_BAR = 0.44f // ... when a progress bar is shown
    private const val MAX_CONTENT_W = 0.86f       // of panel width
    private const val LABEL_CAP = 0.4f            // of V, per label line
    private const val LABEL_GAP = 0.07f           // label -> value, of V
    private const val SUFFIX_GAP = 0.06f          // value -> unit, of V
    private const val CAPTION_CAP = 0.18f         // of V
    private const val CAPTION_CAP_MAX = 0.075f    // of panel height
    private const val CAPTION_GAP = 0.12f         // value baseline -> caption top, of panel height

    private fun sizeForCap(s: DrawSurface, font: DrawSurface.Font, cap: Float): Float =
        100f * cap / s.capHeight(font, 100f)

    /** Width of label + value + unit when the value cap height is [v]. */
    private fun rowWidth(s: DrawSurface, d: BlockclockDisplay, v: Float, F: DrawSurface.Font): Float {
        val labelSize = sizeForCap(s, F, LABEL_CAP * v)
        val labelW = max(s.measure(d.labelTop, F, labelSize), s.measure(d.labelBottom, F, labelSize))
        val valueW = s.measure(d.value, F, sizeForCap(s, F, v))
        val suffixW = if (d.suffix.isEmpty()) 0f else SUFFIX_GAP * v + s.measure(d.suffix, F, labelSize)
        val labelPart = if (labelW > 0f) labelW + LABEL_GAP * v else 0f
        return labelPart + valueW + suffixW
    }

    fun paint(s: DrawSurface, width: Float, height: Float, scene: BlockclockScene) {
        val radius = 0.08f * min(width, height)
        val p = scene.palette
        val F = p.displayFont
        val ink = p.ink
        val d = scene.display
        PanelEffects.background(s, width, height, radius, p, seed = d.value.hashCode() * 31 + d.labelTop.hashCode())

        val hasBar = scene.progress != null
        val hasCaption = d.caption.isNotEmpty() || scene.stale

        // Value cap height: as tall as the panel allows, shrunk to fit long values.
        var v = (if (hasBar) VALUE_OF_HEIGHT_BAR else VALUE_OF_HEIGHT) * height
        val w100 = rowWidth(s, d, 100f, F)
        v = min(v, MAX_CONTENT_W * width * 100f / w100)

        val captionCap = min(CAPTION_CAP * v, CAPTION_CAP_MAX * height)
        val barH = max(1f, 0.012f * height)
        val barGap = 0.07f * height
        val blockH = v +
            (if (hasBar) barGap + barH else 0f) +
            (if (hasCaption) CAPTION_GAP * height + captionCap - (if (hasBar) barGap * 0.4f else 0f) else 0f)
        val top = (height - blockH) / 2f
        val baseline = top + v

        PanelEffects.glowOn(s, p, height)

        // Row: label | value | unit, centered as a group.
        val rowW = rowWidth(s, d, v, F)
        var x = (width - rowW) / 2f
        val labelSize = sizeForCap(s, F, LABEL_CAP * v)
        val labelCap = s.capHeight(F, labelSize)
        val labelW = max(s.measure(d.labelTop, F, labelSize), s.measure(d.labelBottom, F, labelSize))

        if (labelW > 0f) {
            val cx = x + labelW / 2
            s.text(d.labelTop, cx, top + labelCap, F, labelSize, ink, DrawSurface.Align.CENTER)
            if (d.labelBottom.isNotEmpty()) {
                val rule = max(1f, 0.028f * v)
                s.roundRect(x, top + v / 2 - rule / 2, x + labelW, top + v / 2 + rule / 2, 0f, ink)
                s.text(d.labelBottom, cx, baseline, F, labelSize, ink, DrawSurface.Align.CENTER)
            }
            x += labelW + LABEL_GAP * v
        }

        val valueSize = sizeForCap(s, F, v)
        s.text(d.value, x, baseline, F, valueSize, ink, DrawSurface.Align.LEFT)
        x += s.measure(d.value, F, valueSize)

        if (d.suffix.isNotEmpty()) {
            s.text(d.suffix, x + SUFFIX_GAP * v, top + labelCap, F, labelSize, ink, DrawSurface.Align.LEFT)
        }

        var y = baseline
        scene.progress?.let { fraction ->
            y += barGap
            val l = (width - rowW) / 2f
            val r = l + rowW
            s.roundRect(l, y, r, y + barH, barH / 2, p.track)
            s.roundRect(l, y, l + (r - l) * fraction.coerceIn(0f, 1f), y + barH, barH / 2, ink)
            y += barH - barGap * 0.4f
        }

        // Small type stays crisp: no glow on the caption.
        PanelEffects.glowOff(s, p)
        if (hasCaption) drawCaption(s, width, y + CAPTION_GAP * height, captionCap, scene)
        PanelEffects.overlay(s, width, height, radius, p)
    }

    private fun drawCaption(s: DrawSurface, width: Float, top: Float, cap: Float, scene: BlockclockScene) {
        val font = scene.palette.captionFont
        var size = sizeForCap(s, font, cap)
        val baseline = top + cap
        val room = 0.92f * width
        val dotR = 0.32f * cap
        val dotSpace = if (scene.stale) 2 * dotR + 0.6f * cap else 0f

        var caption = scene.display.caption
        val full = s.measure(caption, font, size) + dotSpace
        if (full > room) {
            size = max(size * room / full, size * 0.8f)
            while (caption.length > 1 && s.measure("$caption…", font, size) + dotSpace > room) caption = caption.dropLast(1)
            if (caption.length < scene.display.caption.length) caption = caption.trimEnd() + "…"
        }
        val textW = s.measure(caption, font, size)
        val left = (width - textW - dotSpace) / 2f
        if (scene.stale) s.circle(left + dotR, baseline - cap / 2, dotR, scene.palette.alert)
        s.text(caption, left + dotSpace, baseline, font, size, scene.palette.caption, DrawSurface.Align.LEFT)
    }
}
