package com.example.btcwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import java.util.Locale

class BtcHalvingWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_NEXT_MODE = "com.example.btcwidget.ACTION_NEXT_MODE_HALVING"
        const val ACTION_REFRESH = "com.example.btcwidget.ACTION_REFRESH"

        fun updateWidgetLayout(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            data: PriceData
        ) {
            val mode = getWidgetMode(context, appWidgetId)
            val layoutId = R.layout.btc_widget_halving
            val views = RemoteViews(context.packageName, layoutId)

            val style = WidgetStyle.current(context)

            // Apply theme styles to views
            views.setInt(R.id.widget_root, "setBackgroundResource", style.background)
            views.setTextColor(R.id.txt_halving_label_top, style.labelColor)
            views.setTextColor(R.id.txt_halving_label_bottom, style.labelColor)
            views.setTextColor(R.id.txt_halving_value, style.valueColor)
            views.setTextColor(R.id.txt_halving_progress_bar, style.valueColor)
            views.setTextColor(R.id.txt_halving_sub, style.subTextColor)
            views.setInt(R.id.lbl_divider, "setBackgroundColor", style.dividerColor)

            // Computations
            val epochBlocksCompleted = data.blockHeight % 210000
            val fraction = epochBlocksCompleted.toDouble() / 210000.0
            val epochProgressPercent = fraction * 100.0
            val blocksRemaining = 210000 - epochBlocksCompleted
            val remainingDays = blocksRemaining / 144.0 // 144 blocks per day (10 min avg)

            // Generate progress bar [██████░░░░░] (width = 15)
            val barWidth = 15
            val filledCount = (fraction * barWidth).toInt().coerceIn(0, barWidth)
            val emptyCount = barWidth - filledCount
            val barText = "[" + "█".repeat(filledCount) + "░".repeat(emptyCount) + "]"

            val labelTop: String
            val labelBottom: String
            val valueText: String
            val footerText: String

            when (mode) {
                1 -> { // Estimated days remaining
                    labelTop = "HALV"
                    labelBottom = "ETA"
                    valueText = String.format(Locale.US, "%,.0fd", remainingDays)
                    footerText = String.format(Locale.US, "%.1f%% progress • %,d blks remaining", epochProgressPercent, blocksRemaining)
                }
                2 -> { // Blocks remaining
                    labelTop = "HALV"
                    labelBottom = "BLKS"
                    valueText = String.format(Locale.US, "%,d", blocksRemaining)
                    footerText = String.format(Locale.US, "%.1f%% progress • %,.0f days remaining", epochProgressPercent, remainingDays)
                }
                else -> { // Mode 0: Epoch progress percentage
                    labelTop = "HALV"
                    labelBottom = "PCT"
                    valueText = String.format(Locale.US, "%.2f%%", epochProgressPercent)
                    footerText = String.format(Locale.US, "%,d blks • %,.0f days remaining", blocksRemaining, remainingDays)
                }
            }

            views.setTextViewText(R.id.txt_halving_label_top, labelTop)
            views.setTextViewText(R.id.txt_halving_label_bottom, labelBottom)
            views.setTextViewText(R.id.txt_halving_value, valueText)
            
            views.setViewVisibility(R.id.txt_halving_progress_bar, View.GONE)
            views.setViewVisibility(R.id.progress_bar_visual, View.VISIBLE)
            views.setInt(R.id.progress_bar_visual, "setProgress", (fraction * 100).toInt())
            views.setTextViewText(R.id.txt_halving_sub, footerText.lowercase(Locale.US))

            // Tap layout to cycle display mode
            views.setOnClickPendingIntent(R.id.center_layout, getPendingSelfIntent(context, appWidgetId, ACTION_NEXT_MODE))

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun getPendingSelfIntent(context: Context, appWidgetId: Int, action: String): PendingIntent {
            val intent = Intent(context, BtcHalvingWidgetProvider::class.java).apply {
                this.action = action
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val requestCode = appWidgetId * 40 + when (action) {
                ACTION_NEXT_MODE -> 2
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
            return prefs.getInt("halving_mode_$appWidgetId", 0)
        }

        private fun saveWidgetMode(context: Context, appWidgetId: Int, mode: Int) {
            val prefs = context.getSharedPreferences("blockclock_prefs", Context.MODE_PRIVATE)
            prefs.edit().putInt("halving_mode_$appWidgetId", mode).apply()
        }

    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val data = PriceRepository.getFromPrefs(context) ?: return
        for (id in appWidgetIds) {
            updateWidgetLayout(context, appWidgetManager, id, data)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

        if (action == ACTION_NEXT_MODE) {
            Haptics.click(context)
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val currentMode = getWidgetMode(context, appWidgetId)
                val nextMode = (currentMode + 1) % 3
                saveWidgetMode(context, appWidgetId, nextMode)

                val appWidgetManager = AppWidgetManager.getInstance(context)
                val data = PriceRepository.getFromPrefs(context)
                if (data != null) {
                    updateWidgetLayout(context, appWidgetManager, appWidgetId, data)
                }
            }
        } else if (action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = android.content.ComponentName(context, BtcHalvingWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            val data = PriceRepository.getFromPrefs(context) ?: return
            for (id in appWidgetIds) {
                updateWidgetLayout(context, appWidgetManager, id, data)
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
