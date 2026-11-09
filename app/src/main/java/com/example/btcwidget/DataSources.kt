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

/** The user's holdings, stored only on this device (in satoshis). */
object StackStore {
    private const val KEY_SATS = "stack_sats"
    private const val KEY_UNIT = "stack_unit"

    fun sats(context: Context): Long =
        context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE).getLong(KEY_SATS, 0L)

    fun unit(context: Context): StackUnit =
        context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE).getString(KEY_UNIT, null)
            ?.let { name -> StackUnit.entries.firstOrNull { it.name == name } } ?: StackUnit.BTC

    fun save(context: Context, sats: Long, unit: StackUnit) {
        context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_SATS, sats.coerceAtLeast(0))
            .putString(KEY_UNIT, unit.name)
            .apply()
    }
}

/** Last fetched price history, kept so the chart has something to show offline. */
object HistoryStore {
    private const val KEY = "history_cache"

    fun load(context: Context): List<PricePoint> =
        ChartMath.decode(context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE).getString(KEY, null))

    fun save(context: Context, points: List<PricePoint>) {
        context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, ChartMath.encode(points))
            .apply()
    }
}
