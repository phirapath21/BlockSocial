package com.example.btcwidget

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class PriceData(
    val btcUsd: Double,
    val btcThb: Double,
    val moscowTime: Int,
    val mstrUsd: Double,
    val mstrBtc: Double,
    val blockHeight: Int,
    val mstrBtcHeld: Double,
    val btcCirculation: Double,
    val timestamp: Long,
    val currency: String = "USD",
    val lastFetchSuccessful: Boolean = true,
    val feeFastest: Int = 1,
    val feeHalfHour: Int = 1,
    val feeHour: Int = 1,
    val difficultyChange: Double = 0.0,
    val difficultyProgress: Double = 0.0,
    val hashRate: Double = 0.0,
    val lightningCapacity: Double = 0.0,
    /** BTC/USD change over the last 24 hours in percent; NaN when unknown. */
    val change24hUsd: Double = Double.NaN,
    /** BTC/THB change over the last 24 hours in percent; NaN when unknown. */
    val change24hThb: Double = Double.NaN
)

object PriceRepository {
    private const val TAG = "PriceRepository"
    private const val PREFS_NAME = "btc_widget_prefs"
    private const val KEY_BTC_USD = "btc_usd"
    private const val KEY_BTC_THB = "btc_thb"
    private const val KEY_MOSCOW_TIME = "moscow_time"
    private const val KEY_MSTR_USD = "mstr_usd"
    private const val KEY_MSTR_BTC = "mstr_btc"
    private const val KEY_BLOCK_HEIGHT = "block_height"
    private const val KEY_MSTR_BTC_HELD = "mstr_btc_held"
    private const val KEY_BTC_CIRCULATION = "btc_circulation"
    private const val KEY_TIMESTAMP = "timestamp"
    private const val KEY_CURRENCY = "currency"
    private const val KEY_LAST_FETCH_SUCCESSFUL = "last_fetch_successful"
    private const val KEY_FEE_FASTEST = "fee_fastest"
    private const val KEY_FEE_HALF_HOUR = "fee_half_hour"
    private const val KEY_FEE_HOUR = "fee_hour"
    private const val KEY_DIFFICULTY_CHANGE = "difficulty_change"
    private const val KEY_DIFFICULTY_PROGRESS = "difficulty_progress"
    private const val KEY_HASH_RATE = "hash_rate"
    private const val KEY_LIGHTNING_CAPACITY = "lightning_capacity"
    private const val KEY_CHANGE_24H_USD = "change_24h_usd"
    private const val KEY_CHANGE_24H_THB = "change_24h_thb"

    private const val TIMEOUT_MS = 8000
    private const val SATS_PER_BTC = 100_000_000.0
    private const val FALLBACK_BTC_CIRCULATION = 19_850_000.0
    /** Last known Strategy (MSTR) holdings, used only until the first successful fetch. */
    private const val FALLBACK_MSTR_BTC_HELD = 843_738.0

    /**
     * Fetches every data source in parallel. Each source fails independently:
     * a failed source keeps its last cached value instead of discarding the whole update.
     *
     * Returns null only when the BTC price could not be fetched and nothing is cached.
     */
    suspend fun fetchPriceData(context: Context): PriceData? = withContext(Dispatchers.IO) {
        val sources = AppPrefs.dataSources(context)
        val fresh = coroutineScope {
            // Coinbase rates also supply the USD->THB conversion, so they are always fetched.
            val rates = async { fetchSafely("exchange rates") { fetchRates() } }
            val krakenUsd = async {
                if (sources.price == PriceSource.KRAKEN) fetchSafely("Kraken price") { fetchKrakenUsd() } else null
            }
            val bitkub = async {
                if (sources.thb == ThbSource.BITKUB) fetchSafely("Bitkub ticker") { fetchBitkub() } else null
            }
            val dayStats = async { fetchSafely("24h stats") { fetchDayStats() } }
            val mstr = async { fetchSafely("MSTR price") { fetchMstrPrice() } }
            val height = async { fetchSafely("block height") { fetchBlockHeight() } }
            val circulation = async { fetchSafely("circulating supply") { fetchCirculation() } }
            val fees = async { fetchSafely("recommended fees") { fetchFees() } }
            val difficulty = async { fetchSafely("difficulty adjustment") { fetchDifficulty() } }
            val hashRate = async { fetchSafely("hashrate") { fetchHashRate() } }
            val lightning = async { fetchSafely("lightning capacity") { fetchLightningCapacity() } }
            val mstrHeld = async { fetchSafely("MSTR holdings") { fetchMstrHoldings() } }
            FetchResults(
                rates = rates.await(),
                mstrUsd = mstr.await(),
                blockHeight = height.await(),
                btcCirculation = circulation.await(),
                fees = fees.await(),
                difficulty = difficulty.await(),
                hashRate = hashRate.await(),
                lightningCapacity = lightning.await(),
                mstrBtcHeld = mstrHeld.await(),
                krakenUsd = krakenUsd.await(),
                bitkub = bitkub.await(),
                dayStats = dayStats.await()
            )
        }
        mergeWithCache(fresh, getFromPrefs(context), System.currentTimeMillis())
    }

    data class Rates(val usd: Double, val thb: Double)
    data class Fees(val fastest: Int, val halfHour: Int, val hour: Int)
    data class Difficulty(val change: Double, val progress: Double)

    /** Result of one refresh round; null means that source failed. */
    data class FetchResults(
        val rates: Rates? = null,
        val mstrUsd: Double? = null,
        val blockHeight: Int? = null,
        val btcCirculation: Double? = null,
        val fees: Fees? = null,
        val difficulty: Difficulty? = null,
        val hashRate: Double? = null,
        val lightningCapacity: Double? = null,
        val mstrBtcHeld: Double? = null,
        /** Spot price from Kraken when that source is selected. */
        val krakenUsd: Double? = null,
        /** Bitkub THB ticker when that source is selected. */
        val bitkub: BitkubTicker? = null,
        /** Coinbase 24h open/last, for the 24h change. */
        val dayStats: DayStats? = null
    )

    /**
     * Combines freshly fetched values with the cache, field by field.
     * The data counts as fresh (timestamp updated, no stale badge) only when the
     * core sources — BTC price and block height — both succeeded.
     *
     * USD: Kraken when selected and available, else Coinbase. THB: Bitkub when selected
     * and available, else the USD price converted with Coinbase's USD/THB rate.
     */
    fun mergeWithCache(fresh: FetchResults, cached: PriceData?, now: Long): PriceData? {
        val btcUsd = fresh.krakenUsd ?: fresh.rates?.usd ?: cached?.btcUsd ?: return null
        val converted = fresh.rates?.let { if (it.usd > 0) it.thb / it.usd * btcUsd else null }
        val btcThb = fresh.bitkub?.last ?: converted ?: cached?.btcThb ?: 0.0
        val mstrUsd = fresh.mstrUsd ?: cached?.mstrUsd ?: 0.0
        val coreOk = (fresh.krakenUsd != null || fresh.rates != null) && fresh.blockHeight != null

        val changeUsd = fresh.dayStats?.changePct?.takeUnless { it.isNaN() }
            ?: cached?.change24hUsd ?: Double.NaN
        // Bitkub reports its own 24h change; a converted THB price moves with USD.
        val changeThb = fresh.bitkub?.percentChange
            ?: if (fresh.dayStats != null) changeUsd else cached?.change24hThb ?: changeUsd

        return PriceData(
            btcUsd = btcUsd,
            btcThb = btcThb,
            moscowTime = if (btcUsd > 0) (SATS_PER_BTC / btcUsd).toInt() else 0,
            mstrUsd = mstrUsd,
            mstrBtc = if (btcUsd > 0) mstrUsd / btcUsd else 0.0,
            blockHeight = fresh.blockHeight ?: cached?.blockHeight ?: 0,
            mstrBtcHeld = fresh.mstrBtcHeld
                ?: cached?.mstrBtcHeld?.takeIf { it > 0 }
                ?: FALLBACK_MSTR_BTC_HELD,
            btcCirculation = fresh.btcCirculation ?: cached?.btcCirculation ?: FALLBACK_BTC_CIRCULATION,
            timestamp = if (coreOk || cached == null) now else cached.timestamp,
            currency = cached?.currency ?: "USD",
            lastFetchSuccessful = coreOk,
            feeFastest = fresh.fees?.fastest ?: cached?.feeFastest ?: 1,
            feeHalfHour = fresh.fees?.halfHour ?: cached?.feeHalfHour ?: 1,
            feeHour = fresh.fees?.hour ?: cached?.feeHour ?: 1,
            difficultyChange = fresh.difficulty?.change ?: cached?.difficultyChange ?: 0.0,
            difficultyProgress = fresh.difficulty?.progress ?: cached?.difficultyProgress ?: 0.0,
            hashRate = fresh.hashRate ?: cached?.hashRate ?: 0.0,
            lightningCapacity = fresh.lightningCapacity ?: cached?.lightningCapacity ?: 0.0,
            change24hUsd = changeUsd,
            change24hThb = changeThb
        )
    }

    private inline fun <T> fetchSafely(name: String, block: () -> T): T? = try {
        block()
    } catch (e: Exception) {
        Log.e(TAG, "Error fetching $name", e)
        null
    }

    private fun httpGet(url: String, userAgent: String? = null): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            if (userAgent != null) conn.setRequestProperty("User-Agent", userAgent)
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code from $url")
            return conn.inputStream.bufferedReader().use { it.readText() }.trim()
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchRates(): Rates =
        ApiParsers.coinbaseRates(httpGet("https://api.coinbase.com/v2/exchange-rates?currency=BTC"))

    private fun fetchDayStats(): DayStats =
        ApiParsers.coinbaseStats(httpGet("https://api.exchange.coinbase.com/products/BTC-USD/stats", userAgent = USER_AGENT))

    private fun fetchKrakenUsd(): Double =
        ApiParsers.krakenLast(httpGet("https://api.kraken.com/0/public/Ticker?pair=XBTUSD"))

    private fun fetchBitkub(): BitkubTicker =
        ApiParsers.bitkubTicker(httpGet("https://api.bitkub.com/api/v3/market/ticker?sym=btc_thb"))

    /**
     * Hourly closes for the price chart, oldest first, covering the last [hours] hours.
     * Without start/end Coinbase returns the latest 300 candles (12.5 days), which
     * covers the 7-day view; filtering here avoids date formatting below API 26.
     */
    fun fetchHistory(hours: Int): List<PricePoint> {
        val since = System.currentTimeMillis() / 1000 - hours * 3600L
        return ApiParsers.coinbaseCandles(
            httpGet("https://api.exchange.coinbase.com/products/BTC-USD/candles?granularity=3600", userAgent = USER_AGENT)
        ).filter { it.time >= since }
    }

    /** Coinbase Exchange rejects requests without a User-Agent. */
    private const val USER_AGENT = "Block-Android"

    private fun fetchMstrPrice(): Double {
        val response = httpGet(
            "https://query1.finance.yahoo.com/v8/finance/chart/MSTR",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )
        return JSONObject(response).getJSONObject("chart").getJSONArray("result")
            .getJSONObject(0).getJSONObject("meta").getDouble("regularMarketPrice")
    }

    private fun fetchBlockHeight(): Int =
        httpGet("https://mempool.space/api/blocks/tip/height").toInt()

    private fun fetchCirculation(): Double =
        httpGet("https://blockchain.info/q/totalbc").toDouble() / SATS_PER_BTC

    private fun fetchFees(): Fees {
        val obj = JSONObject(httpGet("https://mempool.space/api/v1/fees/recommended"))
        return Fees(obj.getInt("fastestFee"), obj.getInt("halfHourFee"), obj.getInt("hourFee"))
    }

    private fun fetchDifficulty(): Difficulty {
        val obj = JSONObject(httpGet("https://mempool.space/api/v1/difficulty-adjustment"))
        return Difficulty(obj.getDouble("difficultyChange"), obj.getDouble("progressPercent"))
    }

    /** Network hashrate estimate (3d), converted from H/s to EH/s. */
    private fun fetchHashRate(): Double =
        JSONObject(httpGet("https://mempool.space/api/v1/mining/hashrate/3d"))
            .getDouble("currentHashrate") / 1e18

    /** Strategy (MSTR) bitcoin holdings from CoinGecko's public treasury list. */
    private fun fetchMstrHoldings(): Double {
        val companies = JSONObject(httpGet("https://api.coingecko.com/api/v3/companies/public_treasury/bitcoin"))
            .getJSONArray("companies")
        for (i in 0 until companies.length()) {
            val company = companies.getJSONObject(i)
            if (company.optString("symbol").startsWith("MSTR")) {
                return company.getDouble("total_holdings").also { require(it > 0) }
            }
        }
        throw IOException("MSTR not found in treasury list")
    }

    /** Public Lightning capacity, converted from sats to BTC. */
    private fun fetchLightningCapacity(): Double =
        JSONObject(httpGet("https://mempool.space/api/v1/lightning/statistics/latest"))
            .getJSONObject("latest").getDouble("total_capacity") / SATS_PER_BTC

    // Prices are stored as doubles (raw long bits under "<key>_d"); a Float loses
    // precision on THB prices in the millions. Older installs stored floats under
    // "<key>", which getDouble still reads until the next save.
    private fun SharedPreferences.Editor.putDouble(key: String, value: Double): SharedPreferences.Editor =
        putLong("${key}_d", java.lang.Double.doubleToRawLongBits(value))

    private fun SharedPreferences.getDouble(key: String, default: Double): Double =
        if (contains("${key}_d")) java.lang.Double.longBitsToDouble(getLong("${key}_d", 0L))
        else if (contains(key)) getFloat(key, default.toFloat()).toDouble()
        else default

    fun saveToPrefs(context: Context, data: PriceData) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putDouble(KEY_BTC_USD, data.btcUsd)
            putDouble(KEY_BTC_THB, data.btcThb)
            putInt(KEY_MOSCOW_TIME, data.moscowTime)
            putDouble(KEY_MSTR_USD, data.mstrUsd)
            putDouble(KEY_MSTR_BTC, data.mstrBtc)
            putInt(KEY_BLOCK_HEIGHT, data.blockHeight)
            putDouble(KEY_MSTR_BTC_HELD, data.mstrBtcHeld)
            putDouble(KEY_BTC_CIRCULATION, data.btcCirculation)
            putLong(KEY_TIMESTAMP, data.timestamp)
            putString(KEY_CURRENCY, data.currency)
            putBoolean(KEY_LAST_FETCH_SUCCESSFUL, data.lastFetchSuccessful)
            putInt(KEY_FEE_FASTEST, data.feeFastest)
            putInt(KEY_FEE_HALF_HOUR, data.feeHalfHour)
            putInt(KEY_FEE_HOUR, data.feeHour)
            putDouble(KEY_DIFFICULTY_CHANGE, data.difficultyChange)
            putDouble(KEY_DIFFICULTY_PROGRESS, data.difficultyProgress)
            putDouble(KEY_HASH_RATE, data.hashRate)
            putDouble(KEY_LIGHTNING_CAPACITY, data.lightningCapacity)
            putDouble(KEY_CHANGE_24H_USD, data.change24hUsd)
            putDouble(KEY_CHANGE_24H_THB, data.change24hThb)
            apply()
        }
    }

    fun getFromPrefs(context: Context): PriceData? {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_TIMESTAMP)) return null
        
        return PriceData(
            btcUsd = prefs.getDouble(KEY_BTC_USD, 0.0),
            btcThb = prefs.getDouble(KEY_BTC_THB, 0.0),
            moscowTime = prefs.getInt(KEY_MOSCOW_TIME, 0),
            mstrUsd = prefs.getDouble(KEY_MSTR_USD, 0.0),
            mstrBtc = prefs.getDouble(KEY_MSTR_BTC, 0.0),
            blockHeight = prefs.getInt(KEY_BLOCK_HEIGHT, 0),
            mstrBtcHeld = prefs.getDouble(KEY_MSTR_BTC_HELD, 0.0),
            btcCirculation = prefs.getDouble(KEY_BTC_CIRCULATION, 0.0),
            timestamp = prefs.getLong(KEY_TIMESTAMP, 0L),
            currency = prefs.getString(KEY_CURRENCY, "USD") ?: "USD",
            lastFetchSuccessful = prefs.getBoolean(KEY_LAST_FETCH_SUCCESSFUL, true),
            feeFastest = prefs.getInt(KEY_FEE_FASTEST, 1),
            feeHalfHour = prefs.getInt(KEY_FEE_HALF_HOUR, 1),
            feeHour = prefs.getInt(KEY_FEE_HOUR, 1),
            difficultyChange = prefs.getDouble(KEY_DIFFICULTY_CHANGE, 0.0),
            difficultyProgress = prefs.getDouble(KEY_DIFFICULTY_PROGRESS, 0.0),
            hashRate = prefs.getDouble(KEY_HASH_RATE, 0.0),
            lightningCapacity = prefs.getDouble(KEY_LIGHTNING_CAPACITY, 0.0),
            change24hUsd = prefs.getDouble(KEY_CHANGE_24H_USD, Double.NaN),
            change24hThb = prefs.getDouble(KEY_CHANGE_24H_THB, Double.NaN)
        )
    }
}
