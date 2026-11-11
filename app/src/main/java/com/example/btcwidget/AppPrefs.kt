package com.example.btcwidget

import android.content.Context
import android.content.SharedPreferences

/**
 * The single place for the app's settings and per-widget state. All keys live here;
 * nothing else should read or write this file.
 *
 * Storage map (each file has one owner):
 * - [FILE] "blockclock_prefs": user settings and widget state — this object.
 * - "btc_widget_prefs": cached market data — [PriceRepository].
 * - "alert_prefs": alert rules and their last state — [com.example.btcwidget.alerts.AlertStore].
 * Keeping the cache and alerts in their own files means clearing either never touches
 * settings. File and key names are unchanged from earlier versions, so nothing migrates.
 */
object AppPrefs {
    const val FILE = "blockclock_prefs"

    private const val CURRENCY = "currency"
    private const val REFRESH_INTERVAL = "refresh_interval"
    private const val HERO_METRIC = "hero_metric"
    private const val THEME = "app_theme"
    private const val MODE_ORDER = "mode_order"
    private const val COLLAPSED = "collapsed_categories"
    private const val PRICE_SOURCE = "price_source"
    private const val THB_SOURCE = "thb_source"
    private const val STACK_SATS = "stack_sats"
    private const val STACK_UNIT = "stack_unit"
    private const val HISTORY = "history_cache"
    private const val HISTORY_AT = "history_fetched_at"
    private const val VIDEO_AUTOPLAY = "video_autoplay"
    private const val VIDEO_QUALITY = "video_quality"
    private const val LANDING_DONE = "landing_done"
    private const val LINKS_IN_APP = "links_in_app"
    private const val PRIVATE_LISTS = "private_lists"
    private fun modeEnabled(mode: Int) = "mode_enabled_$mode"
    private fun widgetMode(id: Int) = "mode_$id"
    private fun halvingMode(id: Int) = "halving_mode_$id"
    private fun quoteIndex(id: Int) = "quote_index_$id"
    private fun chartRange(id: Int) = "chart_range_$id"

    /** Keys written by older versions for features that no longer exist. */
    private val OBSOLETE = listOf("haptic_enabled", "blockclock_theme")

    const val DEFAULT_REFRESH_MINUTES = 30
    private const val DEFAULT_HERO = "BLOCK"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private inline fun edit(context: Context, block: SharedPreferences.Editor.() -> Unit) =
        prefs(context).edit().apply(block).apply()

    // ---- Display ----

    /** Open web links in Block's own browser (true, the default) or the phone's browser. */
    fun linksInApp(context: Context): Boolean = prefs(context).getBoolean(LINKS_IN_APP, true)
    fun setLinksInApp(context: Context, inApp: Boolean) = edit(context) { putBoolean(LINKS_IN_APP, inApp) }

    /** New bookmarks and mutes are private (encrypted, NIP-51; the default) or public. */
    fun privateLists(context: Context): Boolean = prefs(context).getBoolean(PRIVATE_LISTS, true)
    fun setPrivateLists(context: Context, private: Boolean) = edit(context) { putBoolean(PRIVATE_LISTS, private) }

    /** Whether the welcome page was passed (logged in, or chose to look around first). */
    fun landingDone(context: Context): Boolean = prefs(context).getBoolean(LANDING_DONE, false)
    fun setLandingDone(context: Context) = edit(context) { putBoolean(LANDING_DONE, true) }

    /** When videos in notes play by themselves (muted): a [com.example.btcwidget.ui.social.VideoAutoplay] name. */
    fun videoAutoplay(context: Context): String? = prefs(context).getString(VIDEO_AUTOPLAY, null)
    fun setVideoAutoplay(context: Context, value: String) = edit(context) { putString(VIDEO_AUTOPLAY, value) }

    /** Default video quality: a [com.example.btcwidget.ui.social.VideoQuality] name. */
    fun videoQuality(context: Context): String? = prefs(context).getString(VIDEO_QUALITY, null)
    fun setVideoQuality(context: Context, value: String) = edit(context) { putString(VIDEO_QUALITY, value) }

    fun currency(context: Context): String = prefs(context).getString(CURRENCY, "USD") ?: "USD"
    fun setCurrency(context: Context, value: String) = edit(context) { putString(CURRENCY, value) }

    fun refreshInterval(context: Context): Int = prefs(context).getInt(REFRESH_INTERVAL, DEFAULT_REFRESH_MINUTES)
    fun setRefreshInterval(context: Context, minutes: Int) = edit(context) { putInt(REFRESH_INTERVAL, minutes) }

    fun theme(context: Context): AppTheme = AppTheme.fromName(prefs(context).getString(THEME, null))
    fun setTheme(context: Context, value: AppTheme) = edit(context) { putString(THEME, value.name) }

    /** Colors and effects for widgets, from [theme]. */
    fun palette(context: Context): Palette = theme(context).palette

    fun heroMetric(context: Context): String = prefs(context).getString(HERO_METRIC, DEFAULT_HERO) ?: DEFAULT_HERO
    fun setHeroMetric(context: Context, key: String) = edit(context) { putString(HERO_METRIC, key) }

    // ---- Metrics on the widget ----

    fun isModeEnabled(context: Context, mode: Int): Boolean = prefs(context).getBoolean(modeEnabled(mode), true)
    fun setModeEnabled(context: Context, mode: Int, enabled: Boolean) = edit(context) { putBoolean(modeEnabled(mode), enabled) }

    /** On/off state of every mode, indexed by mode. */
    fun enabledModes(context: Context): List<Boolean> {
        val p = prefs(context)
        return (0 until BlockclockMetrics.MODE_COUNT).map { p.getBoolean(modeEnabled(it), true) }
    }

    fun modeOrder(context: Context): List<String> = BlockclockMetrics.parseOrder(prefs(context).getString(MODE_ORDER, null))
    fun setModeOrder(context: Context, order: List<String>) =
        edit(context) { putString(MODE_ORDER, BlockclockMetrics.serializeOrder(order)) }

    fun collapsedCategories(context: Context): Set<MetricCategory> =
        prefs(context).getStringSet(COLLAPSED, emptySet()).orEmpty()
            .mapNotNull { name -> MetricCategory.entries.firstOrNull { it.name == name } }
            .toSet()

    fun setCollapsedCategories(context: Context, value: Set<MetricCategory>) =
        edit(context) { putStringSet(COLLAPSED, value.map { it.name }.toSet()) }

    // ---- Data sources ----

    fun dataSources(context: Context): DataSources {
        val p = prefs(context)
        return DataSources(
            price = PriceSource.from(p.getString(PRICE_SOURCE, null)),
            thb = ThbSource.from(p.getString(THB_SOURCE, null))
        )
    }

    fun setDataSources(context: Context, value: DataSources) = edit(context) {
        putString(PRICE_SOURCE, value.price.name)
        putString(THB_SOURCE, value.thb.name)
    }

    // ---- Your stack (stored only on this device, in satoshis) ----

    fun stackSats(context: Context): Long = prefs(context).getLong(STACK_SATS, 0L)

    fun stackUnit(context: Context): StackUnit =
        prefs(context).getString(STACK_UNIT, null)
            ?.let { name -> StackUnit.entries.firstOrNull { it.name == name } } ?: StackUnit.BTC

    fun setStack(context: Context, sats: Long, unit: StackUnit) = edit(context) {
        putLong(STACK_SATS, sats.coerceAtLeast(0))
        putString(STACK_UNIT, unit.name)
    }

    // ---- Price history cache (for charts, works offline) ----

    fun history(context: Context): List<PricePoint> = ChartMath.decode(prefs(context).getString(HISTORY, null))

    /** When the cached history was fetched (epoch ms), 0 if never. */
    fun historyFetchedAt(context: Context): Long = prefs(context).getLong(HISTORY_AT, 0L)

    fun setHistory(context: Context, points: List<PricePoint>) = edit(context) {
        putString(HISTORY, ChartMath.encode(points))
        putLong(HISTORY_AT, System.currentTimeMillis())
    }

    // ---- Per-widget state ----

    fun widgetMode(context: Context, appWidgetId: Int): Int = prefs(context).getInt(widgetMode(appWidgetId), 0)
    fun setWidgetMode(context: Context, appWidgetId: Int, mode: Int) = edit(context) { putInt(widgetMode(appWidgetId), mode) }

    fun halvingMode(context: Context, appWidgetId: Int): Int = prefs(context).getInt(halvingMode(appWidgetId), 0)
    fun setHalvingMode(context: Context, appWidgetId: Int, mode: Int) = edit(context) { putInt(halvingMode(appWidgetId), mode) }

    fun quoteIndex(context: Context, appWidgetId: Int): Int = prefs(context).getInt(quoteIndex(appWidgetId), 0)
    fun setQuoteIndex(context: Context, appWidgetId: Int, index: Int) = edit(context) { putInt(quoteIndex(appWidgetId), index) }

    fun chartRange(context: Context, appWidgetId: Int): ChartRange {
        val name = prefs(context).getString(chartRange(appWidgetId), null)
        return ChartRange.entries.firstOrNull { it.name == name } ?: ChartRange.DAY
    }

    fun setChartRange(context: Context, appWidgetId: Int, range: ChartRange) =
        edit(context) { putString(chartRange(appWidgetId), range.name) }

    /** Removes the state of widgets the user has deleted from the home screen. */
    fun forgetWidgets(context: Context, appWidgetIds: IntArray) = edit(context) {
        for (id in appWidgetIds) {
            remove(widgetMode(id))
            remove(halvingMode(id))
            remove(quoteIndex(id))
            remove(chartRange(id))
        }
    }

    /** Drops keys left by features removed in earlier versions. */
    fun removeObsoleteKeys(context: Context) {
        val p = prefs(context)
        if (OBSOLETE.any { p.contains(it) }) edit(context) { OBSOLETE.forEach { remove(it) } }
    }
}
