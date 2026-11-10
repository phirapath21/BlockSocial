package com.example.btcwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.RemoteViews
import kotlin.math.sqrt


/** Paints a blockclock into a bitmap sized for one widget instance. */
object WidgetBitmapRenderer {
    /** Stays well under the binder limit for RemoteViews bitmaps (~800 KB ARGB). */
    private const val MAX_PIXELS = 200_000f

    fun render(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        views: RemoteViews,
        scene: BlockclockScene
    ) = renderWith(context, appWidgetManager, appWidgetId, views, describe(scene)) { surface, w, h ->
        BlockclockPainter.paint(surface, w, h, scene)
    }

    /** Sizes a bitmap to the widget, lets [paint] draw it, and sets it on the ImageView. */
    fun renderWith(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        views: RemoteViews,
        description: String,
        paint: (DrawSurface, Float, Float) -> Unit
    ) {
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        // Portrait size: min width x max height (dp). Fall back to a 2x1 cell.
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: 250
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: 110
        val density = context.resources.displayMetrics.density

        // The panel fills the widget, like the launcher's own widget background.
        var w = widthDp * density
        var h = heightDp * density
        val pixels = w * h
        if (pixels > MAX_PIXELS) {
            val scale = sqrt(MAX_PIXELS / pixels)
            w *= scale
            h *= scale
        }

        val bitmap = Bitmap.createBitmap(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val surface = AndroidDrawSurface.create(Canvas(bitmap), context)
        paint(surface, bitmap.width.toFloat(), bitmap.height.toFloat())
        views.setImageViewBitmap(R.id.widget_image, bitmap)
        views.setContentDescription(R.id.widget_image, description)
    }

    /** Spoken description for TalkBack, e.g. "BTC USD 74020. Market price of bitcoin". */
    private fun describe(scene: BlockclockScene): String {
        val d = scene.display
        val parts = listOf(d.labelTop, d.labelBottom, d.value, d.suffix).filter { it.isNotEmpty() }
        val stale = if (scene.stale) " Data may be out of date." else ""
        return parts.joinToString(" ") + ". " + d.caption + "." + stale
    }
}

/**
 * What metrics and halving widgets show before the first successful fetch (fresh install
 * while offline, say): a dashed value and "Waiting for data", instead of a blank widget.
 * Tapping it asks for a refresh.
 */
object WidgetPlaceholder {
    private val scene = BlockclockScene(
        BlockclockDisplay(labelTop = "BTC", labelBottom = "", value = "--", caption = "Waiting for data · tap to retry")
    )

    fun show(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.btc_widget_blockclock)
        WidgetBitmapRenderer.render(context, appWidgetManager, appWidgetId, views, scene.copy(palette = AppPrefs.palette(context)))
        val retry = Intent(context, BtcWidgetProvider::class.java).setAction(BtcWidgetProvider.ACTION_REFRESH)
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getBroadcast(context, 77, retry, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        )
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
