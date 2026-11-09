package com.example.btcwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Redraws every widget right after the app is updated, so placed widgets pick up
 * new layouts immediately instead of waiting for the next background refresh.
 */
class AppUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            WidgetUpdater.updateAllWidgets(context)
            WidgetRefreshScheduler.schedulePeriodic(context)
        }
    }
}
