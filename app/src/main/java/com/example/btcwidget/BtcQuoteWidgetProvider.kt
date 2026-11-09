package com.example.btcwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.RemoteViews

class BtcQuoteWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_NEXT_QUOTE = "com.example.btcwidget.ACTION_NEXT_QUOTE"
        const val ACTION_REFRESH = "com.example.btcwidget.ACTION_REFRESH"

        data class Quote(val text: String, val author: String)

        val QUOTES = listOf(
            Quote("If you don't believe me or don't get it, I don't have time to try to convince you, sorry.", "Satoshi Nakamoto"),
            Quote("The computer can be used as a tool to liberate and protect people.", "Hal Finney"),
            Quote("Privacy is necessary for an open society in the electronic age.", "Eric Hughes"),
            Quote("Cypherpunks write code.", "Eric Hughes"),
            Quote("Lost coins only make everyone else's coins worth slightly more.", "Satoshi Nakamoto"),
            Quote("The root problem with conventional currency is all the trust that's required.", "Satoshi Nakamoto"),
            Quote("We are writing code to defend our privacy.", "Eric Hughes"),
            Quote("Don't trust. Verify.", "Bitcoin Motto"),
            Quote("Fix the money, fix the world.", "Bitcoin Motto"),
            Quote("It's very attractive to the libertarian perspective if we can explain it properly.", "Satoshi Nakamoto"),
            Quote("I've been working on a new electronic cash system that's fully peer-to-peer...", "Satoshi Nakamoto"),
            Quote("We are heading into a future where everyone has the power of strong cryptography.", "Hal Finney")
        )

        fun updateWidgetLayout(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val theme = getWidgetTheme(context)
            val quoteIndex = getWidgetQuoteIndex(context, appWidgetId)
            val quote = QUOTES[quoteIndex % QUOTES.size]

            val layoutId = R.layout.btc_widget_quote
            val views = RemoteViews(context.packageName, layoutId)

            // Theme colors and background resource mapping
            val bgResId: Int
            val labelColor: Int
            val valueColor: Int
            val subTextColor: Int

            when (theme) {
                1 -> { // White / Light Theme
                    bgResId = R.drawable.widget_background_ios_light
                    labelColor = 0xFF8E8E93.toInt()
                    valueColor = 0xFF000000.toInt()
                    subTextColor = 0xFF8E8E93.toInt()
                }
                9 -> { // Orange Pill Theme
                    bgResId = R.drawable.widget_background_ios_orange
                    labelColor = 0xFFA66210.toInt()
                    valueColor = 0xFFF7931A.toInt()
                    subTextColor = 0xFFA66210.toInt()
                }
                else -> { // Dark Theme (Default/Theme 0)
                    bgResId = R.drawable.widget_background_ios_dark
                    labelColor = 0xFF8E8E93.toInt()
                    valueColor = 0xFFFFFFFF.toInt()
                    subTextColor = 0xFF8E8E93.toInt()
                }
            }

            // Apply theme styles to views
            views.setInt(R.id.widget_root, "setBackgroundResource", bgResId)
            views.setTextColor(R.id.txt_quote_text, valueColor)
            views.setTextColor(R.id.txt_quote_author, labelColor)

            // Format quotation with typography quotes
            views.setTextViewText(R.id.txt_quote_text, "\"${quote.text}\"")
            views.setTextViewText(R.id.txt_quote_author, "— ${quote.author.uppercase()}")

            // Tap layout to cycle to next quote
            views.setOnClickPendingIntent(R.id.quote_container, getPendingSelfIntent(context, appWidgetId, ACTION_NEXT_QUOTE))

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun getPendingSelfIntent(context: Context, appWidgetId: Int, action: String): PendingIntent {
            val intent = Intent(context, BtcQuoteWidgetProvider::class.java).apply {
                this.action = action
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val requestCode = appWidgetId * 30 + when (action) {
                ACTION_NEXT_QUOTE -> 2
                else -> 0
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            return PendingIntent.getBroadcast(context, requestCode, intent, flags)
        }

        private fun getWidgetQuoteIndex(context: Context, appWidgetId: Int): Int {
            val prefs = context.getSharedPreferences("blockclock_prefs", Context.MODE_PRIVATE)
            return prefs.getInt("quote_index_$appWidgetId", 0)
        }

        private fun saveWidgetQuoteIndex(context: Context, appWidgetId: Int, index: Int) {
            val prefs = context.getSharedPreferences("blockclock_prefs", Context.MODE_PRIVATE)
            prefs.edit().putInt("quote_index_$appWidgetId", index).apply()
        }

        private fun getWidgetTheme(context: Context): Int {
            val prefs = context.getSharedPreferences("blockclock_prefs", Context.MODE_PRIVATE)
            return prefs.getInt("blockclock_theme", 10)
        }

        fun triggerVibration(context: Context) {
            val prefs = context.getSharedPreferences("blockclock_prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("haptic_enabled", true)) {
                return
            }
            try {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vibratorManager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }

                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                        val audioAttributes = AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .build()
                        vibrator.vibrate(effect, audioAttributes)
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val effect = VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                        val audioAttributes = AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .build()
                        vibrator.vibrate(effect, audioAttributes)
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(50)
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val data = PriceRepository.getFromPrefs(context)
        for (id in appWidgetIds) {
            updateWidgetLayout(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

        if (action == ACTION_NEXT_QUOTE) {
            triggerVibration(context)
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val currentIndex = getWidgetQuoteIndex(context, appWidgetId)
                saveWidgetQuoteIndex(context, appWidgetId, currentIndex + 1)

                val appWidgetManager = AppWidgetManager.getInstance(context)
                updateWidgetLayout(context, appWidgetManager, appWidgetId)
            }
        } else if (action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = android.content.ComponentName(context, BtcQuoteWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (id in appWidgetIds) {
                updateWidgetLayout(context, appWidgetManager, id)
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidgetLayout(context, appWidgetManager, appWidgetId)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshScheduler.schedulePeriodic(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefreshScheduler.cancelIfNoWidgets(context)
    }
}
