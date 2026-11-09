package com.example.btcwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import kotlin.random.Random

/** [DrawSurface] backed by an Android [Canvas]; used by widgets and the app. */
class AndroidDrawSurface(
    private val canvas: Canvas,
    private val condensed: Typeface
) : DrawSurface {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val rect = RectF()
    private val bounds = Rect()

    private fun prepareText(font: DrawSurface.Font, size: Float, tracking: Float): Paint =
        textPaint.apply {
            typeface = if (font == DrawSurface.Font.CONDENSED) condensed else Typeface.MONOSPACE
            textSize = size
            letterSpacing = tracking
        }

    override fun roundRect(l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int) {
        fill.shader = null
        fill.color = color
        rect.set(l, t, r, b)
        canvas.drawRoundRect(rect, radius, radius, fill)
    }

    override fun roundRectGradient(l: Float, t: Float, r: Float, b: Float, radius: Float, top: Int, bottom: Int) {
        fill.shader = LinearGradient(0f, t, 0f, b, top, bottom, Shader.TileMode.CLAMP)
        rect.set(l, t, r, b)
        canvas.drawRoundRect(rect, radius, radius, fill)
        fill.shader = null
    }

    override fun grain(l: Float, t: Float, r: Float, b: Float, radius: Float) {
        fill.shader = grainShader
        rect.set(l, t, r, b)
        canvas.drawRoundRect(rect, radius, radius, fill)
        fill.shader = null
    }

    override fun circle(cx: Float, cy: Float, radius: Float, color: Int) {
        fill.shader = null
        fill.color = color
        canvas.drawCircle(cx, cy, radius, fill)
    }

    override fun text(
        text: String, x: Float, baseline: Float, font: DrawSurface.Font,
        size: Float, color: Int, align: DrawSurface.Align, tracking: Float
    ) {
        val paint = prepareText(font, size, tracking)
        paint.color = color
        paint.textAlign = when (align) {
            DrawSurface.Align.LEFT -> Paint.Align.LEFT
            DrawSurface.Align.CENTER -> Paint.Align.CENTER
            DrawSurface.Align.RIGHT -> Paint.Align.RIGHT
        }
        canvas.drawText(text, x, baseline, paint)
    }

    override fun measure(text: String, font: DrawSurface.Font, size: Float, tracking: Float): Float =
        prepareText(font, size, tracking).measureText(text)

    override fun capHeight(font: DrawSurface.Font, size: Float): Float {
        prepareText(font, size, 0f).getTextBounds("8", 0, 1, bounds)
        return bounds.height().toFloat()
    }

    companion object {
        @Volatile private var cachedCondensed: Typeface? = null

        /** Oswald Light, bundled in res/font; falls back to the system condensed face. */
        fun condensedTypeface(context: Context): Typeface =
            cachedCondensed ?: (
                runCatching { ResourcesCompat.getFont(context, R.font.oswald_light) }.getOrNull()
                    ?: Typeface.create("sans-serif-condensed-light", Typeface.NORMAL)
                ).also { cachedCondensed = it }

        /** Tileable monochrome noise, generated once. */
        private val grainShader: BitmapShader by lazy {
            val size = 96
            val random = Random(7)
            val pixels = IntArray(size * size) {
                val v = random.nextInt(256)
                Color.argb(random.nextInt(10, 26), v, v, v)
            }
            val tile = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
            BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
    }
}
