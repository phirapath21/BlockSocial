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

        /**
         * Quotes with a checkable original wording; the source is noted for each.
         * Only add quotes whose exact words appear in a primary or reputable source;
         * never paraphrase and attribute.
         */
        val QUOTES = listOf(
            // Bitcoin whitepaper, abstract (2008)
            Quote("A purely peer-to-peer version of electronic cash would allow online payments to be sent directly from one party to another without going through a financial institution.", "Satoshi Nakamoto"),
            // Coinbase text of the genesis block (3 Jan 2009)
            Quote("The Times 03/Jan/2009 Chancellor on brink of second bailout for banks", "Bitcoin genesis block"),
            // Tweet, 10 Jan 2009
            Quote("Running bitcoin", "Hal Finney"),
            // Announcement on the Cryptography mailing list (Oct 2008)
            Quote("I've been working on a new electronic cash system that's fully peer-to-peer, with no trusted third party.", "Satoshi Nakamoto"),
            // P2P Foundation post (Feb 2009)
            Quote("The root problem with conventional currency is all the trust that's required to make it work.", "Satoshi Nakamoto"),
            // Cryptography mailing list (Nov 2008)
            Quote("We can win a major battle in the arms race and gain a new territory of freedom for several years.", "Satoshi Nakamoto"),
            // Cryptography mailing list (Nov 2008)
            Quote("It's very attractive to the libertarian viewpoint if we can explain it properly.", "Satoshi Nakamoto"),
            // Email (Jan 2009)
            Quote("It might make sense just to get some in case it catches on.", "Satoshi Nakamoto"),
            // BitcoinTalk (Jun 2010)
            Quote("Lost coins only make everyone else's coins worth slightly more. Think of it as a donation to everyone.", "Satoshi Nakamoto"),
            // BitcoinTalk (Jun 2010)
            Quote("The nature of Bitcoin is such that once version 0.1 was released, the core design was set in stone for the rest of its lifetime.", "Satoshi Nakamoto"),
            // BitcoinTalk (Jul 2010)
            Quote("Writing a description for this thing for general audiences is bloody hard. There's nothing to relate it to.", "Satoshi Nakamoto"),
            // Cryptography mailing list (Nov 2008)
            Quote("If you don't believe me or don't get it, I don't have time to try to convince you, sorry.", "Satoshi Nakamoto"),
            // "Why remailers..." essay (1992)
            Quote("The computer can be used as a tool to liberate and protect people.", "Hal Finney"),
            // "Bitcoin and me" on BitcoinTalk (Mar 2013)
            Quote("I'm comfortable with my legacy.", "Hal Finney"),
            // A Cypherpunk's Manifesto (1993)
            Quote("Privacy is necessary for an open society in the electronic age.", "Eric Hughes"),
            Quote("Privacy is the power to selectively reveal oneself to the world.", "Eric Hughes"),
            Quote("Cypherpunks write code.", "Eric Hughes"),
            // The Crypto Anarchist Manifesto (1988)
            Quote("A specter is haunting the modern world, the specter of crypto anarchy.", "Timothy C. May"),
            // Essay title (2001)
            Quote("Trusted third parties are security holes.", "Nick Szabo"),
            // Added at the app owner's request
            Quote("เงินเฟ้อแม่งเหี้ย", "Wichit Saiklao"),
            Quote("Don't trust. Verify.", "Bitcoin proverb"),
            Quote("Fix the money, fix the world.", "Bitcoin proverb")
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

            // Format quotation with typography quotes
            views.setTextViewText(R.id.txt_quote_text, "“${quote.text}”")
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
        WidgetRefreshScheduler.cancelIfUnused(context)
    }
}
