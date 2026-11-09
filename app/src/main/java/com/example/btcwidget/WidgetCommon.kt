package com.example.btcwidget

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.RemoteViews

internal const val WIDGET_PREFS = "blockclock_prefs"

/** Draws a [BlockclockDisplay] into the tile row of a widget layout. */
object TileRenderer {
    fun render(context: Context, views: RemoteViews, display: BlockclockDisplay, stale: Boolean) {
        views.removeAllViews(R.id.tile_row)
        views.addView(R.id.tile_row, labelTile(context, display.labelTop, display.labelBottom))
        for (tile in display.valueTiles()) {
            val tileViews = RemoteViews(context.packageName, R.layout.widget_tile)
            tileViews.setTextViewText(R.id.tile_text, tile)
            views.addView(R.id.tile_row, tileViews)
        }
        if (display.suffix.isNotEmpty()) {
            views.addView(R.id.tile_row, labelTile(context, display.suffix, ""))
        }
        views.setTextViewText(R.id.txt_caption, display.caption)
        views.setViewVisibility(R.id.txt_warning, if (stale) View.VISIBLE else View.GONE)
    }

    private fun labelTile(context: Context, top: String, bottom: String): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_tile_label).apply {
            setTextViewText(R.id.tile_label_top, top)
            setTextViewText(R.id.tile_label_bottom, bottom)
            val hasBottom = if (bottom.isEmpty()) View.GONE else View.VISIBLE
            setViewVisibility(R.id.tile_label_bottom, hasBottom)
            setViewVisibility(R.id.tile_label_divider, hasBottom)
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
