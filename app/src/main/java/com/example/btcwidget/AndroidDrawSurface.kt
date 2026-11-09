package com.example.btcwidget

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/** [DrawSurface] backed by an Android [Canvas]; used by widgets and the app. */
class AndroidDrawSurface(private val canvas: Canvas) : DrawSurface {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val rect = RectF()
    private val bounds = Rect()

    private fun typeface(font: DrawSurface.Font): Typeface = when (font) {
        DrawSurface.Font.DIGIT -> DIGIT_TYPEFACE
        DrawSurface.Font.MONO -> Typeface.MONOSPACE
        DrawSurface.Font.MONO_BOLD -> MONO_BOLD_TYPEFACE
    }

    private fun prepareText(font: DrawSurface.Font, size: Float, tracking: Float): Paint =
        textPaint.apply {
            typeface = typeface(font)
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

    override fun roundRectStroke(l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int, width: Float) {
        stroke.color = color
        stroke.strokeWidth = width
        rect.set(l, t, r, b)
        canvas.drawRoundRect(rect, radius, radius, stroke)
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

    private companion object {
        val DIGIT_TYPEFACE: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
        val MONO_BOLD_TYPEFACE: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
}
