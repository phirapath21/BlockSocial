package com.example.btcwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.btcwidget.alerts.AlertStore
import java.util.concurrent.TimeUnit

/**
 * Schedules background refreshes with WorkManager, which survives Doze and reboots
 * and only runs when the device has a network connection.
 */
object WidgetRefreshScheduler {
    private const val PERIODIC_WORK = "price_refresh_periodic"
    private const val ONE_TIME_WORK = "price_refresh_now"

    /** Skip on-demand refreshes when the cache is younger than this. */
    private const val MIN_REFRESH_GAP_MS = 60_000L

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * Ensures the periodic refresh is scheduled.
     * @param intervalChanged pass true after the user changes the interval so the new one applies.
     */
    fun schedulePeriodic(context: Context, intervalChanged: Boolean = false) {
        // WorkManager's minimum periodic interval is 15 minutes.
        val minutes = AppPrefs.refreshInterval(context)
            .coerceAtLeast(15).toLong()

        val request = PeriodicWorkRequestBuilder<PriceRefreshWorker>(minutes, TimeUnit.MINUTES)
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            if (intervalChanged) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Requests a single refresh as soon as the network allows. Ignored if the cached data
     * is fresh or a refresh is already pending, which also breaks the known loop where
     * WorkManager toggling its components re-triggers the widgets' onUpdate.
     */
    fun refreshNow(context: Context) {
        val cached = PriceRepository.getFromPrefs(context)
        if (cached != null && cached.lastFetchSuccessful &&
            System.currentTimeMillis() - cached.timestamp < MIN_REFRESH_GAP_MS
        ) {
            return
        }

        val request = OneTimeWorkRequestBuilder<PriceRefreshWorker>()
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(ONE_TIME_WORK, ExistingWorkPolicy.KEEP, request)
    }

    /**
     * Cancels background refreshes once nothing needs them: no widget of any type is
     * placed and no price alert is enabled.
     */
    fun cancelIfUnused(context: Context) {
        if (AlertStore.anyEnabled(context)) return
        val manager = AppWidgetManager.getInstance(context)
        val providers = listOf(
            BtcWidgetProvider::class.java,
            BtcQuoteWidgetProvider::class.java,
            BtcHalvingWidgetProvider::class.java,
            BtcChartWidgetProvider::class.java
        )
        val hasWidgets = providers.any {
            manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty()
        }
        if (!hasWidgets) {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(PERIODIC_WORK)
                cancelUniqueWork(ONE_TIME_WORK)
            }
        }
    }
}
