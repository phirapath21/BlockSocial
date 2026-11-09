package com.example.btcwidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.btcwidget.theme.BTCWidgetTheme
import com.example.btcwidget.ui.config.ModeChoice
import com.example.btcwidget.ui.config.WidgetConfigScreen

/**
 * Shown when a blockclock or halving widget is placed, and on reconfigure: the user
 * taps a live preview to choose what this widget shows.
 */
class WidgetConfigActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var isHalving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // Backing out without choosing cancels placing the widget.
        setResult(RESULT_CANCELED, resultIntent())
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider
        isHalving = provider?.className == BtcHalvingWidgetProvider::class.java.name

        val prefs = getSharedPreferences(WIDGET_PREFS, MODE_PRIVATE)
        val currency = prefs.getString("currency", "USD") ?: "USD"
        val data = PriceRepository.getFromPrefs(this)
        val stackSats = StackStore.sats(this)

        val choices = if (isHalving) {
            listOf("Epoch progress", "Days to halving", "Blocks to halving").mapIndexed { mode, name ->
                ModeChoice(mode, name, data?.let { BlockclockMetrics.halving(mode, it.blockHeight) })
            }
        } else {
            // Listed in the user's dashboard order.
            BlockclockMetrics.orderedModes(BtcWidgetProvider.modeOrder(prefs))
                .filter { BlockclockMetrics.isAvailable(it, currency, hasStack = stackSats > 0) }
                .map { mode ->
                    ModeChoice(mode, BlockclockMetrics.MODE_NAMES[mode], data?.let { BlockclockMetrics.forMode(mode, it, currency, stackSats) })
                }
        }
        val current = if (isHalving) {
            BtcHalvingWidgetProvider.getWidgetMode(this, appWidgetId)
        } else {
            BtcWidgetProvider.getWidgetMode(this, appWidgetId)
        }
        val progress = data?.let { BlockclockMetrics.halvingProgress(it.blockHeight).toFloat() }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            BTCWidgetTheme(darkTheme = true, dynamicColor = false) {
                WidgetConfigScreen(
                    choices = choices,
                    selectedMode = current,
                    halvingProgress = progress,
                    isHalving = isHalving,
                    onPick = ::confirm
                )
            }
        }
    }

    private fun confirm(mode: Int) {
        val manager = AppWidgetManager.getInstance(this)
        val data = PriceRepository.getFromPrefs(this)
        if (isHalving) {
            BtcHalvingWidgetProvider.saveWidgetMode(this, appWidgetId, mode)
            data?.let { BtcHalvingWidgetProvider.updateWidgetLayout(this, manager, appWidgetId, it) }
        } else {
            // Choosing a mode the user had switched off turns it back on, so the
            // widget shows it instead of skipping ahead.
            getSharedPreferences(WIDGET_PREFS, MODE_PRIVATE).edit()
                .putBoolean("mode_enabled_$mode", true)
                .apply()
            BtcWidgetProvider.saveWidgetMode(this, appWidgetId, mode)
            data?.let { BtcWidgetProvider.updateWidgetLayout(this, manager, appWidgetId, it) }
        }
        WidgetRefreshScheduler.schedulePeriodic(this)
        WidgetRefreshScheduler.refreshNow(this)

        setResult(RESULT_OK, resultIntent())
        finish()
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}
