package com.example.btcwidget

/** The look of the app and widgets, chosen in Settings and stored by [name]. */
enum class AppTheme(val label: String, val palette: Palette) {
    /** White type on a dark grained panel, like an e-ink blockclock. */
    CLASSIC("Classic", Palette.CLASSIC),

    /** Green phosphor terminal: pixel type, glow, scanlines and faint falling code. */
    RETRO("Retro", Palette.RETRO),

    /** Bitcoin orange: glossy orange pills with near-black type on a warm dark app. */
    ORANGE("Orange pill", Palette.ORANGE_PILL);

    companion object {
        fun fromName(name: String?): AppTheme = entries.firstOrNull { it.name == name } ?: CLASSIC
    }
}

/**
 * Colors (ARGB ints) and effects for one theme, shared by the painters (widgets and the
 * app's panels) and the app UI, so both always match.
 */
data class Palette(
    val bgTop: Int,
    val bgBottom: Int,
    /** Values, labels, the chart line. */
    val ink: Int,
    val caption: Int,
    /** Unfilled part of progress bars. */
    val track: Int,
    /** Stale-data dot. */
    val alert: Int,
    /** Area under the chart line. */
    val wash: Int,
    /** Face for values and labels. */
    val displayFont: DrawSurface.Font,
    /** Face for captions and small print. */
    val captionFont: DrawSurface.Font,
    /** Film grain over the panel. */
    val grain: Boolean,
    /** CRT effects: phosphor glow on type, scanlines, faint falling code. */
    val crt: Boolean,
    /** A soft highlight across the top of the panel, like light on a glossy pill. */
    val gloss: Boolean = false,
    val ui: UiColors
) {
    companion object {
        val CLASSIC = Palette(
            bgTop = 0xFF1E1F23.toInt(),
            bgBottom = 0xFF16171A.toInt(),
            ink = 0xFFD7DCDF.toInt(),
            caption = 0xFFE3E5E7.toInt(),
            track = 0xFF34363B.toInt(),
            alert = 0xFFE5534B.toInt(),
            wash = 0x1AD7DCDF,
            displayFont = DrawSurface.Font.CONDENSED,
            captionFont = DrawSurface.Font.MONO,
            grain = true,
            crt = false,
            ui = UiColors(
                background = 0xFF0E0F11.toInt(),
                body = 0xFF1A1B1F.toInt(),
                bodyStroke = 0xFF2A2C31.toInt(),
                value = 0xFFD7DCDF.toInt(),
                label = 0xFFA3A8AC.toInt(),
                caption = 0xFF8C9196.toInt(),
                track = 0xFF34363B.toInt(),
                alert = 0xFFE5534B.toInt()
            )
        )

        val RETRO = Palette(
            bgTop = 0xFF03120A.toInt(),
            bgBottom = 0xFF000503.toInt(),
            ink = 0xFF39FF6E.toInt(),
            caption = 0xFF22C552.toInt(),
            track = 0xFF0B3818.toInt(),
            // Amber, the other classic terminal phosphor, so the warning still stands out.
            alert = 0xFFFFB000.toInt(),
            wash = 0x2239FF6E,
            displayFont = DrawSurface.Font.PIXEL,
            captionFont = DrawSurface.Font.PIXEL,
            grain = false,
            crt = true,
            ui = UiColors(
                background = 0xFF000402.toInt(),
                body = 0xFF04110A.toInt(),
                bodyStroke = 0xFF0F3D1E.toInt(),
                value = 0xFF39FF6E.toInt(),
                label = 0xFF2BD95C.toInt(),
                caption = 0xFF1FA846.toInt(),
                track = 0xFF0F3D1E.toInt(),
                alert = 0xFFFFB000.toInt()
            )
        )

        /** Bitcoin orange (#F7931A) panels, dark type; the app around them is a warm near-black. */
        val ORANGE_PILL = Palette(
            bgTop = 0xFFFFA235.toInt(),
            bgBottom = 0xFFF0850C.toInt(),
            ink = 0xFF1A0E00.toInt(),
            caption = 0xFF3B2200.toInt(),
            track = 0x40000000,
            // White reads on orange where red would not.
            alert = 0xFFFFFFFF.toInt(),
            wash = 0x1F1A0E00,
            displayFont = DrawSurface.Font.CONDENSED,
            captionFont = DrawSurface.Font.MONO,
            grain = true,
            crt = false,
            gloss = true,
            ui = UiColors(
                background = 0xFF120A03.toInt(),
                body = 0xFF21150A.toInt(),
                bodyStroke = 0xFF3D2814.toInt(),
                value = 0xFFFF9F2E.toInt(),
                label = 0xFFE8B98A.toInt(),
                caption = 0xFFB38B66.toInt(),
                track = 0xFF3D2814.toInt(),
                alert = 0xFFFF6A5C.toInt()
            )
        )
    }
}

/** App UI colors (ARGB). */
data class UiColors(
    val background: Int,
    val body: Int,
    val bodyStroke: Int,
    val value: Int,
    val label: Int,
    val caption: Int,
    val track: Int,
    val alert: Int
)

/** Drawing shared by the painters for the panel and its CRT effects. */
internal object PanelEffects {
    /** Characters of the falling code: hex, as in a block hash. */
    private const val CODE = "0123456789abcdef"

    /**
     * Panel background: gradient, then grain or falling code.
     * @param codeOpacity scales the falling code, lower behind busy content such as a chart.
     */
    fun background(s: DrawSurface, width: Float, height: Float, radius: Float, p: Palette, seed: Int, codeOpacity: Float = 1f) {
        s.roundRectGradient(0f, 0f, width, height, radius, p.bgTop, p.bgBottom)
        if (p.grain) s.grain(0f, 0f, width, height, radius)
        if (p.gloss) s.roundRectGradient(0f, 0f, width, height * 0.55f, radius, 0x40FFFFFF, 0x00FFFFFF)
        if (p.crt) code(s, width, height, radius, p, seed, codeOpacity)
    }

    /** Scanlines over everything, drawn last. */
    fun overlay(s: DrawSurface, width: Float, height: Float, radius: Float, p: Palette) {
        if (p.crt) s.scanlines(0f, 0f, width, height, radius, pitch = kotlin.math.max(3f, height / 70f), color = 0x38000000)
    }

    /** Glow on for type and lines drawn after this; off again with [glowOff]. */
    fun glowOn(s: DrawSurface, p: Palette, height: Float) {
        if (p.crt) s.glow(p.ink and 0x00FFFFFF or (0x99 shl 24), kotlin.math.max(2f, height * 0.025f))
    }

    fun glowOff(s: DrawSurface, p: Palette) {
        if (p.crt) s.glow(0, 0f)
    }

    /**
     * Faint columns of falling hex, brightest at each run's head, as on a terminal raining
     * code. Laid out from [seed], so a panel looks the same until its value changes.
     */
    private fun code(s: DrawSurface, width: Float, height: Float, radius: Float, p: Palette, seed: Int, opacity: Float) {
        val random = kotlin.random.Random(seed)
        val font = p.captionFont
        val cap = kotlin.math.min(0.075f * height, 0.03f * width)
        val size = 100f * cap / s.capHeight(font, 100f)
        val colW = s.measure("0", font, size) * 1.6f
        val rowH = cap * 1.55f
        val rows = (height / rowH).toInt()
        val left = radius * 0.6f
        val cols = ((width - 2 * left) / colW).toInt()
        val rgb = p.ink and 0x00FFFFFF
        for (c in 0 until cols) {
            if (random.nextFloat() > 0.45f) continue
            val length = random.nextInt(3, 9)
            val head = random.nextInt(0, rows + length / 2)
            val x = left + (c + 0.5f) * colW
            for (k in 0 until length) {
                val row = head - k
                if (row < 0 || row >= rows) continue
                val alpha = ((if (k == 0) 0x48 else (0x30 - k * 0x05).coerceAtLeast(0x0C)) * opacity).toInt()
                val ch = CODE[random.nextInt(CODE.length)].toString()
                s.text(ch, x, (row + 1) * rowH, font, size, (alpha shl 24) or rgb, DrawSurface.Align.CENTER)
            }
        }
    }
}
