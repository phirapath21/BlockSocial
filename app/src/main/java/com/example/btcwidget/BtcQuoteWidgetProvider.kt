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

class BtcQuoteWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_NEXT_QUOTE = "com.example.btcwidget.ACTION_NEXT_QUOTE"
        const val ACTION_REFRESH = "com.example.btcwidget.ACTION_REFRESH"

        data class Quote(val text: String, val author: String)

        val QUOTES = listOf(
            Quote("If you don't believe me or don't get it, I don't have time to try to convince you, sorry.", "Satoshi Nakamoto"),
            Quote("The computer can be used as a tool to liberate and protect people.", "Hal Finney"),
            Quote("Privacy is necessary for an open society in the electronic age.", "Eric Hughes"),
            Quote("Cypherpunks write code.", "Eric Hughes"),
            Quote("Lost coins only make everyone else's coins worth slightly more.", "Satoshi Nakamoto"),
            Quote("The root problem with conventional currency is all the trust that's required.", "Satoshi Nakamoto"),
            Quote("We are writing code to defend our privacy.", "Eric Hughes"),
            Quote("Don't trust. Verify.", "Bitcoin Motto"),
            Quote("Fix the money, fix the world.", "Bitcoin Motto"),
            Quote("It's very attractive to the libertarian perspective if we can explain it properly.", "Satoshi Nakamoto"),
            Quote("I've been working on a new electronic cash system that's fully peer-to-peer...", "Satoshi Nakamoto"),
            Quote("We are heading into a future where everyone has the power of strong cryptography.", "Hal Finney")
        )

        fun updateWidgetLayout(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val quoteIndex = getWidgetQuoteIndex(context, appWidgetId)
            val quote = QUOTES[quoteIndex % QUOTES.size]

            val layoutId = R.layout.btc_widget_quote
            val views = RemoteViews(context.packageName, layoutId)

            val style = WidgetStyle.current(context)

            // Apply theme styles to views
            views.setInt(R.id.widget_root, "setBackgroundResource", style.background)
            views.setTextColor(R.id.txt_quote_text, style.valueColor)
            views.setTextColor(R.id.txt_quote_author, style.labelColor)

            // Format quotation with typography quotes
            views.setTextViewText(R.id.txt_quote_text, "\"${quote.text}\"")
            views.setTextViewText(R.id.txt_quote_author, "— ${quote.author.uppercase()}")

            // Tap layout to cycle to next quote
            views.setOnClickPendingIntent(R.id.quote_container, getPendingSelfIntent(context, appWidgetId, ACTION_NEXT_QUOTE))

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun getPendingSelfIntent(context: Context, appWidgetId: Int, action: String): PendingIntent {
            val intent = Intent(context, BtcQuoteWidgetProvider::class.java).apply {
                this.action = action
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val requestCode = appWidgetId * 30 + when (action) {
                ACTION_NEXT_QUOTE -> 2
                else -> 0
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            return PendingIntent.getBroadcast(context, requestCode, intent, flags)
        }

        private fun getWidgetQuoteIndex(context: Context, appWidgetId: Int): Int {
            val prefs = context.getSharedPreferences("blockclock_prefs", Context.MODE_PRIVATE)
            return prefs.getInt("quote_index_$appWidgetId", 0)
        }

        private fun saveWidgetQuoteIndex(context: Context, appWidgetId: Int, index: Int) {
            val prefs = context.getSharedPreferences("blockclock_prefs", Context.MODE_PRIVATE)
            prefs.edit().putInt("quote_index_$appWidgetId", index).apply()
        }

    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val data = PriceRepository.getFromPrefs(context)
        for (id in appWidgetIds) {
            updateWidgetLayout(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

        if (action == ACTION_NEXT_QUOTE) {
            Haptics.click(context)
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val currentIndex = getWidgetQuoteIndex(context, appWidgetId)
                saveWidgetQuoteIndex(context, appWidgetId, currentIndex + 1)

                val appWidgetManager = AppWidgetManager.getInstance(context)
                updateWidgetLayout(context, appWidgetManager, appWidgetId)
            }
        } else if (action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = android.content.ComponentName(context, BtcQuoteWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (id in appWidgetIds) {
                updateWidgetLayout(context, appWidgetManager, id)
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
        updateWidgetLayout(context, appWidgetManager, appWidgetId)
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
