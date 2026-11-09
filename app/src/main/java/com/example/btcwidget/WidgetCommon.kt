package com.example.btcwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.RemoteViews
import kotlin.math.sqrt

internal const val WIDGET_PREFS = "blockclock_prefs"

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
        val surface = AndroidDrawSurface(Canvas(bitmap), AndroidDrawSurface.condensedTypeface(context))
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
        WidgetBitmapRenderer.render(context, appWidgetManager, appWidgetId, views, scene)
        val retry = Intent(context, BtcWidgetProvider::class.java).setAction(BtcWidgetProvider.ACTION_REFRESH)
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getBroadcast(context, 77, retry, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        )
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}

/** Soft click feedback for widget taps; respects the in-app haptics toggle. */
object Haptics {
    fun click(context: Context) {
        val prefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("haptic_enabled", true)) return

        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator == null || !vibrator.hasVibrator()) return

            val attributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .build()
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK), attributes)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE), attributes)
                else -> {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(50)
                }
            }
        } catch (e: Exception) {
            // Missing hardware or permission: haptics are best-effort.
        }
    }
}
