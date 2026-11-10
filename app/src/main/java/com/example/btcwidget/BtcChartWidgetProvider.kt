package com.example.btcwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews

/**
 * Price chart widget: the BTC price, its change and a sparkline for the last 24 hours
 * or 7 days. Tapping switches between the two ranges.
 */
class BtcChartWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_NEXT_RANGE = "com.example.btcwidget.ACTION_NEXT_RANGE_CHART"

        fun updateWidgetLayout(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val data = PriceRepository.getFromPrefs(context)
                ?: return WidgetPlaceholder.show(context, appWidgetManager, appWidgetId)
            val currency = AppPrefs.currency(context)
            val range = AppPrefs.chartRange(context, appWidgetId)
            val factor = if (currency == "THB" && data.btcUsd > 0) data.btcThb / data.btcUsd else 1.0
            val scene = ChartScene(
                points = ChartMath.slice(AppPrefs.history(context), range, factor),
                currency = currency,
                range = range,
                price = if (currency == "THB") data.btcThb else data.btcUsd,
                stale = !data.lastFetchSuccessful,
                palette = AppPrefs.palette(context)
            )

            val views = RemoteViews(context.packageName, R.layout.btc_widget_blockclock)
            val summary = ChartMath.summary(scene.points)
            val description = "BTC ${ChartPainter.formatPrice(scene.price ?: 0.0, currency)} $currency" +
                (summary?.let { s -> BlockclockMetrics.formatChange(s.changePct)?.let { ", $it over ${range.label}" } } ?: "")
            WidgetBitmapRenderer.renderWith(context, appWidgetManager, appWidgetId, views, description) { surface, w, h ->
                ChartPainter.paint(surface, w, h, scene)
            }
            views.setOnClickPendingIntent(R.id.widget_root, rangeIntent(context, appWidgetId))
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun rangeIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, BtcChartWidgetProvider::class.java).apply {
                action = ACTION_NEXT_RANGE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            return PendingIntent.getBroadcast(
                context, appWidgetId * 50 + 5, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) updateWidgetLayout(context, appWidgetManager, id)
        WidgetRefreshScheduler.schedulePeriodic(context)
        WidgetRefreshScheduler.refreshNow(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_NEXT_RANGE) return
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val next = ChartRange.entries[(AppPrefs.chartRange(context, id).ordinal + 1) % ChartRange.entries.size]
        AppPrefs.setChartRange(context, id, next)
        updateWidgetLayout(context, AppWidgetManager.getInstance(context), id)
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

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        AppPrefs.forgetWidgets(context, appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefreshScheduler.cancelIfUnused(context)
    }
}
