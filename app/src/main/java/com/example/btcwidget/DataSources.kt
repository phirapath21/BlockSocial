package com.example.btcwidget

import android.content.Context

/** The user's data-source choices (Settings > Data sources). */
data class DataSources(val price: PriceSource, val thb: ThbSource) {
    companion object {
        private const val KEY_PRICE = "price_source"
        private const val KEY_THB = "thb_source"

        fun read(context: Context): DataSources {
            val prefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
            return DataSources(
                price = PriceSource.from(prefs.getString(KEY_PRICE, null)),
                thb = ThbSource.from(prefs.getString(KEY_THB, null))
            )
        }

        fun save(context: Context, sources: DataSources) {
            context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_PRICE, sources.price.name)
                .putString(KEY_THB, sources.thb.name)
                .apply()
        }
    }
}
