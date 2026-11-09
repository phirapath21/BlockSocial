package com.example.btcwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews

class BtcWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_NEXT_MODE = "com.example.btcwidget.ACTION_NEXT_MODE"
        const val ACTION_REFRESH = "com.example.btcwidget.ACTION_REFRESH"

        fun updateWidgetLayout(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            data: PriceData
        ) {
            val prefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
            val currency = prefs.getString("currency", "USD") ?: "USD"
            val mode = getWidgetMode(context, appWidgetId)
            val display = BlockclockMetrics.forMode(mode, data, currency)
            val enabledModes = (0 until BlockclockMetrics.MODE_COUNT).filter { prefs.getBoolean("mode_enabled_$it", true) }
            val scene = BlockclockScene(
                display = display,
                stale = !data.lastFetchSuccessful,
                progress = if (BlockclockMetrics.MODE_KEYS[mode.coerceIn(0, BlockclockMetrics.MODE_COUNT - 1)] == "HALVING") {
                    BlockclockMetrics.halvingProgress(data.blockHeight).toFloat()
                } else {
                    null
                },
                modeIndex = enabledModes.indexOf(mode),
                modeCount = enabledModes.size
            )

            val views = RemoteViews(context.packageName, R.layout.btc_widget_blockclock)
            WidgetBitmapRenderer.render(context, appWidgetManager, appWidgetId, views, scene)

            // Tapping anywhere on the device cycles to the next enabled mode.
            views.setOnClickPendingIntent(R.id.widget_root, getPendingSelfIntent(context, appWidgetId, ACTION_NEXT_MODE))

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun getPendingSelfIntent(context: Context, appWidgetId: Int, action: String): PendingIntent {
            val intent = Intent(context, BtcWidgetProvider::class.java).apply {
                this.action = action
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val requestCode = appWidgetId * 10 + when (action) {
                ACTION_NEXT_MODE -> 2
                ACTION_REFRESH -> 3
                else -> 0
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            return PendingIntent.getBroadcast(context, requestCode, intent, flags)
        }

        private fun getWidgetMode(context: Context, appWidgetId: Int): Int {
            val prefs = context.getSharedPreferences("blockclock_prefs", Context.MODE_PRIVATE)
            return prefs.getInt("mode_$appWidgetId", 0) // Default to mode 0 (BTC/USD)
        }

        private fun saveWidgetMode(context: Context, appWidgetId: Int, mode: Int) {
            val prefs = context.getSharedPreferences("blockclock_prefs", Context.MODE_PRIVATE)
            prefs.edit().putInt("mode_$appWidgetId", mode).apply()
        }

        private fun getNextActiveMode(context: Context, currentMode: Int): Int {
            val prefs = context.getSharedPreferences("blockclock_prefs", Context.MODE_PRIVATE)
            val count = BlockclockMetrics.MODE_COUNT
            for (i in 1..count) {
                val candidate = (currentMode + i) % count
                if (prefs.getBoolean("mode_enabled_$candidate", true)) {
                    return candidate
                }
            }
            return currentMode
        }

    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Draw cached data immediately, then fetch fresh data in the background.
        PriceRepository.getFromPrefs(context)?.let { data ->
            for (id in appWidgetIds) {
                updateWidgetLayout(context, appWidgetManager, id, data)
            }
        }
        WidgetRefreshScheduler.schedulePeriodic(context)
        WidgetRefreshScheduler.refreshNow(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

        if (action == ACTION_NEXT_MODE) {
            Haptics.click(context)
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val currentMode = getWidgetMode(context, appWidgetId)
                val nextMode = getNextActiveMode(context, currentMode)
                saveWidgetMode(context, appWidgetId, nextMode)

                // Instantly update the widget view using cached data
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val data = PriceRepository.getFromPrefs(context)
                if (data != null) {
                    updateWidgetLayout(context, appWidgetManager, appWidgetId, data)
                }
            }
        } else if (action == ACTION_REFRESH) {
            WidgetRefreshScheduler.refreshNow(context)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val data = PriceRepository.getFromPrefs(context) ?: return
        updateWidgetLayout(context, appWidgetManager, appWidgetId, data)
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
