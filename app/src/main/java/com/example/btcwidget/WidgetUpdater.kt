package com.example.btcwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

object WidgetUpdater {
    fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val data = PriceRepository.getFromPrefs(context)

        // 1. Single Widget
        try {
            val singleWidget = ComponentName(context, BtcWidgetProvider::class.java)
            val singleIds = appWidgetManager.getAppWidgetIds(singleWidget)
            for (id in singleIds) {
                if (data != null) BtcWidgetProvider.updateWidgetLayout(context, appWidgetManager, id, data)
                else WidgetPlaceholder.show(context, appWidgetManager, id)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }


        // 3. Quotes Widget
        try {
            val quoteWidget = ComponentName(context, BtcQuoteWidgetProvider::class.java)
            val quoteIds = appWidgetManager.getAppWidgetIds(quoteWidget)
            for (id in quoteIds) {
                BtcQuoteWidgetProvider.updateWidgetLayout(context, appWidgetManager, id)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. Halving Widget
        try {
            val halvingWidget = ComponentName(context, BtcHalvingWidgetProvider::class.java)
            val halvingIds = appWidgetManager.getAppWidgetIds(halvingWidget)
            for (id in halvingIds) {
                if (data != null) BtcHalvingWidgetProvider.updateWidgetLayout(context, appWidgetManager, id, data)
                else WidgetPlaceholder.show(context, appWidgetManager, id)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
