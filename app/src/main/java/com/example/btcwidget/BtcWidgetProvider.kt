package com.example.btcwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import java.util.Locale

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
            val mode = getWidgetMode(context, appWidgetId)
            val layoutId = R.layout.btc_widget_blockclock
            val views = RemoteViews(context.packageName, layoutId)

            val style = WidgetStyle.current(context)

            // Apply theme styles to views
            views.setInt(R.id.widget_root, "setBackgroundResource", style.background)
            views.setTextColor(R.id.txt_blockclock_label_top, style.labelColor)
            views.setTextColor(R.id.txt_blockclock_label_bottom, style.labelColor)
            views.setTextColor(R.id.txt_blockclock_value, style.valueColor)
            views.setTextColor(R.id.txt_blockclock_suffix, style.labelColor)
            views.setTextColor(R.id.txt_blockclock_sub, style.subTextColor)
            views.setInt(R.id.lbl_divider, "setBackgroundColor", style.dividerColor)

            // Bind data based on active mode
            val prefs = context.getSharedPreferences("blockclock_prefs", Context.MODE_PRIVATE)
            val currency = prefs.getString("currency", "USD") ?: "USD"
            val symbol = if (currency == "THB") "฿" else "$"

            val labelTopText: String
            val labelBottomText: String
            val valueText: String
            val subText: String
            val showLabel: Boolean
            val showSubText: Boolean
            val showLbl_divider: Boolean
            var suffixText = ""
            var showSuffix = false

            when (mode) {
                0 -> {
                    labelTopText = "BTC"
                    labelBottomText = currency
                    valueText = if (currency == "THB") {
                        String.format(Locale.US, "%.2f mil", data.btcThb / 1_000_000.0)
                    } else {
                        String.format(Locale.US, "%s%,.0f", symbol, data.btcUsd)
                    }
                    subText = if (currency == "THB") "Market price of BTC (THB)" else "Market price of BTC"
                    showLabel = true
                    showSubText = true
                    showLbl_divider = true
                }
                1 -> {
                    labelTopText = "SATS"
                    labelBottomText = if (currency == "THB") "1 THB" else "1$currency"
                    val moscowTimeVal = if (currency == "THB") {
                        if (data.btcThb > 0) (100_000_000.0 / data.btcThb).toInt() else 0
                    } else {
                        data.moscowTime
                    }
                    valueText = String.format(Locale.US, "%,d", moscowTimeVal)
                    subText = if (currency == "THB") "฿1 per Satoshis" else "1$symbol per Satoshis"
                    showLabel = true
                    showSubText = true
                    showLbl_divider = true
                }
                2 -> {
                    labelTopText = ""
                    labelBottomText = ""
                    valueText = String.format(Locale.US, "%,d", data.blockHeight)
                    subText = "Bitcoin block height"
                    showLabel = false
                    showSubText = true
                    showLbl_divider = false
                }
                3 -> {
                    labelTopText = "MSCW"
                    labelBottomText = "TIME"
                    val moscowTimeVal = if (currency == "THB") {
                        if (data.btcThb > 0) (100_000_000.0 / data.btcThb).toInt() else 0
                    } else {
                        data.moscowTime
                    }
                    valueText = String.format(Locale.US, "%d", moscowTimeVal)
                    subText = ""
                    showLabel = true
                    showSubText = false
                    showLbl_divider = true
                }
                4 -> {
                    labelTopText = "MSTR"
                    labelBottomText = "BTC"
                    valueText = String.format(Locale.US, "%,.0f", data.mstrBtcHeld)
                    subText = "Strategy Inc. - BTC held"
                    showLabel = true
                    showSubText = true
                    showLbl_divider = true
                }
                5 -> {
                    labelTopText = "MSTR"
                    labelBottomText = "BTC"
                    valueText = String.format(Locale.US, "%.5f", data.mstrBtc)
                    subText = "Strategy Inc. - BTC per share"
                    showLabel = true
                    showSubText = true
                    showLbl_divider = true
                }
                6 -> {
                    labelTopText = "MSTR"
                    labelBottomText = "USD"
                    valueText = String.format(Locale.US, "$%,.0f", data.mstrUsd)
                    subText = "Strategy Inc. - Share price"
                    showLabel = true
                    showSubText = true
                    showLbl_divider = true
                }
                7 -> {
                    labelTopText = "BTC"
                    labelBottomText = ""
                    val circInMillions = data.btcCirculation / 1_000_000.0
                    valueText = String.format(Locale.US, "%.3f", circInMillions)
                    suffixText = "MIL"
                    showSuffix = true
                    subText = "Bitcoin in circulation"
                    showLabel = true
                    showSubText = true
                    showLbl_divider = false
                }
                8 -> { // Halving Countdown
                    labelTopText = "HALV"
                    labelBottomText = "BLKS"
                    val halvingRemaining = 210000 - (data.blockHeight % 210000)
                    valueText = String.format(Locale.US, "%,d", halvingRemaining)
                    subText = "Blocks to next halving"
                    showLabel = true
                    showSubText = true
                    showLbl_divider = true
                }
                9 -> { // Mempool Priority Fees
                    labelTopText = "FEES"
                    labelBottomText = "sat/vB"
                    valueText = String.format(Locale.US, "%d", data.feeFastest)
                    subText = String.format(Locale.US, "Mid: %d / Low: %d sat/vB", data.feeHalfHour, data.feeHour)
                    showLabel = true
                    showSubText = true
                    showLbl_divider = true
                }
                10 -> { // Hash Rate
                    labelTopText = "HASH"
                    labelBottomText = "RATE"
                    valueText = String.format(Locale.US, "%.1f", data.hashRate)
                    suffixText = "EH/s"
                    showSuffix = true
                    subText = "Network Hash Rate (3d)"
                    showLabel = true
                    showSubText = true
                    showLbl_divider = true
                }
                11 -> { // Difficulty Adjustment
                    labelTopText = "DIFF"
                    labelBottomText = "ADJ"
                    valueText = String.format(Locale.US, "%+.1f%%", data.difficultyChange)
                    subText = String.format(Locale.US, "Progress: %.1f%%", data.difficultyProgress)
                    showLabel = true
                    showSubText = true
                    showLbl_divider = true
                }
                12 -> { // Lightning Capacity
                    labelTopText = "LN"
                    labelBottomText = "CAP"
                    valueText = String.format(Locale.US, "%,.0f", data.lightningCapacity)
                    suffixText = "BTC"
                    showSuffix = true
                    subText = "Lightning Network Capacity"
                    showLabel = true
                    showSubText = true
                    showLbl_divider = true
                }
                else -> {
                    labelTopText = "BTC"
                    labelBottomText = currency
                    valueText = if (currency == "THB") {
                        String.format(Locale.US, "%.2f mil", data.btcThb / 1_000_000.0)
                    } else {
                        String.format(Locale.US, "%s%,.0f", symbol, data.btcUsd)
                    }
                    subText = if (currency == "THB") "Market price of BTC (THB)" else "Market price of BTC"
                    showLabel = true
                    showSubText = true
                    showLbl_divider = true
                }
            }

            // Set label texts
            views.setTextViewText(R.id.txt_blockclock_label_top, labelTopText)
            views.setTextViewText(R.id.txt_blockclock_label_bottom, labelBottomText)
            
            // Toggle stacked label container visibility
            if (showLabel) {
                views.setViewVisibility(R.id.lbl_stacked_container, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.lbl_stacked_container, View.GONE)
            }

            // Toggle subtext container visibility
            if (showSubText) {
                views.setViewVisibility(R.id.txt_blockclock_sub, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.txt_blockclock_sub, View.GONE)
            }

            // Toggle lbl divider visilibity
            if (showLbl_divider) {
                views.setViewVisibility(R.id.lbl_divider, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.lbl_divider, View.GONE)
            }

            views.setTextViewText(R.id.txt_blockclock_value, valueText)
            views.setTextViewText(R.id.txt_blockclock_sub, subText)

            // Set suffix text and visibility
            views.setTextViewText(R.id.txt_blockclock_suffix, suffixText)
            if (showSuffix) {
                views.setViewVisibility(R.id.lbl_suffix_container, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.lbl_suffix_container, View.GONE)
            }

            // Show/hide the offline warning symbol based on whether lastFetchSuccessful is false
            if (!data.lastFetchSuccessful) {
                views.setViewVisibility(R.id.txt_warning, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.txt_warning, View.GONE)
            }

            // Bind navigation click intents (Tapping on central layout cycles mode)
            views.setOnClickPendingIntent(R.id.center_layout, getPendingSelfIntent(context, appWidgetId, ACTION_NEXT_MODE))
            views.setOnClickPendingIntent(R.id.linear_layout, getPendingSelfIntent(context, appWidgetId, ACTION_NEXT_MODE))

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
            for (i in 1..13) {
                val candidate = (currentMode + i) % 13
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
