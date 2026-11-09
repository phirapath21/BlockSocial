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
            val currency = AppPrefs.currency(context)
            val savedMode = AppPrefs.widgetMode(context, appWidgetId)
            val stackSats = AppPrefs.stackSats(context)
            val hasStack = stackSats > 0
            val enabled = AppPrefs.enabledModes(context)
            val isEnabled = { m: Int -> enabled.getOrElse(m) { true } }
            // A widget left on a mode that is now off or unavailable (e.g. Moscow Time
            // after switching to THB) shows the next active mode instead.
            val mode = if (BlockclockMetrics.isActive(savedMode, isEnabled(savedMode), currency, hasStack)) {
                savedMode
            } else {
                BlockclockMetrics.nextActiveMode(savedMode, currency, AppPrefs.modeOrder(context), hasStack, isEnabled)
            }
            val display = BlockclockMetrics.forMode(mode, data, currency, stackSats)
            val scene = BlockclockScene(
                display = display,
                stale = !data.lastFetchSuccessful,
                progress = if (BlockclockMetrics.MODE_KEYS.getOrNull(mode) == "HALVING") {
                    BlockclockMetrics.halvingProgress(data.blockHeight).toFloat()
                } else {
                    null
                }
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


        private fun getNextActiveMode(context: Context, currentMode: Int): Int {
            val enabled = AppPrefs.enabledModes(context)
            return BlockclockMetrics.nextActiveMode(
                currentMode,
                AppPrefs.currency(context),
                AppPrefs.modeOrder(context),
                hasStack = AppPrefs.stackSats(context) > 0
            ) { enabled.getOrElse(it) { true } }
        }

    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Draw cached data immediately (or a placeholder), then fetch fresh data.
        val data = PriceRepository.getFromPrefs(context)
        for (id in appWidgetIds) {
            if (data != null) updateWidgetLayout(context, appWidgetManager, id, data)
            else WidgetPlaceholder.show(context, appWidgetManager, id)
        }
        WidgetRefreshScheduler.schedulePeriodic(context)
        WidgetRefreshScheduler.refreshNow(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

        if (action == ACTION_NEXT_MODE) {
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val currentMode = AppPrefs.widgetMode(context, appWidgetId)
                val nextMode = getNextActiveMode(context, currentMode)
                AppPrefs.setWidgetMode(context, appWidgetId, nextMode)

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
        val data = PriceRepository.getFromPrefs(context)
            ?: return WidgetPlaceholder.show(context, appWidgetManager, appWidgetId)
        updateWidgetLayout(context, appWidgetManager, appWidgetId, data)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshScheduler.schedulePeriodic(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        AppPrefs.forgetWidgets(context, appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefreshScheduler.cancelIfUnused(context)
    }
}
