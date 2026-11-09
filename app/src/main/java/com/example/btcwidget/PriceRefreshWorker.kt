package com.example.btcwidget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Fetches fresh data in the background and redraws every widget. */
class PriceRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val data = PriceRepository.fetchPriceData(applicationContext)
            ?: return if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()

        PriceRepository.saveToPrefs(applicationContext, data)
        WidgetUpdater.updateAllWidgets(applicationContext)

        // Core sources failed: try again sooner than the next periodic run.
        return if (!data.lastFetchSuccessful && runAttemptCount < MAX_RETRIES) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}
