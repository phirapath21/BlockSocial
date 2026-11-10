package com.example.btcwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.btcwidget.alerts.AlertNotifier

/** Fetches fresh data in the background and redraws every widget. */
class PriceRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Nostr replies, zaps and messages first: it doesn't depend on the price sources.
        if (runAttemptCount == 0) {
            runCatching { com.example.btcwidget.nostr.NostrBackground.check(applicationContext) }
                .onFailure { android.util.Log.w("PriceRefreshWorker", "Nostr check failed", it) }
        }
        val data = PriceRepository.fetchPriceData(applicationContext)
            ?: return if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()

        PriceRepository.saveToPrefs(applicationContext, data)
        WidgetUpdater.updateAllWidgets(applicationContext)
        AlertNotifier.checkAndNotify(applicationContext, data)
        refreshHistoryForChartWidgets()

        // Core sources failed: try again sooner than the next periodic run.
        return if (!data.lastFetchSuccessful && runAttemptCount < MAX_RETRIES) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    /** Hourly candles change slowly: refetch at most once an hour, and only for chart widgets. */
    private fun refreshHistoryForChartWidgets() {
        val manager = AppWidgetManager.getInstance(applicationContext)
        val ids = manager.getAppWidgetIds(ComponentName(applicationContext, BtcChartWidgetProvider::class.java))
        if (ids.isEmpty()) return
        if (System.currentTimeMillis() - AppPrefs.historyFetchedAt(applicationContext) < HISTORY_MAX_AGE_MS) return
        val fresh = runCatching { PriceRepository.fetchHistory(ChartRange.WEEK.hours) }.getOrNull()
        if (fresh != null && fresh.size >= 2) {
            AppPrefs.setHistory(applicationContext, fresh)
            for (id in ids) BtcChartWidgetProvider.updateWidgetLayout(applicationContext, manager, id)
        }
    }

    private companion object {
        const val MAX_RETRIES = 3
        const val HISTORY_MAX_AGE_MS = 55 * 60_000L
    }
}
