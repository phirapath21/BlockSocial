package com.example.btcwidget

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

internal const val WIDGET_PREFS = "blockclock_prefs"

/** Colors and background shared by every widget for the selected theme. */
data class WidgetStyle(
    val background: Int,
    val labelColor: Int,
    val valueColor: Int,
    val subTextColor: Int,
    val dividerColor: Int
) {
    companion object {
        const val THEME_DARK = 0
        const val THEME_WHITE = 1
        const val THEME_ORANGE_PILL = 9

        fun current(context: Context): WidgetStyle {
            val prefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
            return forTheme(prefs.getInt("blockclock_theme", THEME_DARK))
        }

        fun forTheme(theme: Int): WidgetStyle = when (theme) {
            THEME_WHITE -> WidgetStyle(
                background = R.drawable.widget_background_ios_light,
                labelColor = 0xFF8E8E93.toInt(),
                valueColor = 0xFF000000.toInt(),
                subTextColor = 0xFF8E8E93.toInt(),
                dividerColor = 0xFF007AFF.toInt() // System Blue
            )
            THEME_ORANGE_PILL -> WidgetStyle(
                background = R.drawable.widget_background_ios_orange,
                labelColor = 0xFFA66210.toInt(),
                valueColor = 0xFFF7931A.toInt(),
                subTextColor = 0xFFA66210.toInt(),
                dividerColor = 0xFFF7931A.toInt() // Bitcoin Orange
            )
            else -> WidgetStyle( // Dark (default)
                background = R.drawable.widget_background_ios_dark,
                labelColor = 0xFF8E8E93.toInt(),
                valueColor = 0xFFFFFFFF.toInt(),
                subTextColor = 0xFF8E8E93.toInt(),
                dividerColor = 0xFF007AFF.toInt() // System Blue
            )
        }
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
