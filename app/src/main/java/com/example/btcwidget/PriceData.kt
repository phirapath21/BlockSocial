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
    val lightningCapacity: Double = 0.0
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

    private const val TIMEOUT_MS = 8000
    private const val SATS_PER_BTC = 100_000_000.0
    private const val FALLBACK_BTC_CIRCULATION = 19_850_000.0
    private const val MSTR_BTC_HELD = 843_738.0

    /**
     * Fetches every data source in parallel. Each source fails independently:
     * a failed source keeps its last cached value instead of discarding the whole update.
     *
     * Returns null only when the BTC price could not be fetched and nothing is cached.
     */
    suspend fun fetchPriceData(context: Context): PriceData? = withContext(Dispatchers.IO) {
        val fresh = coroutineScope {
            val rates = async { fetchSafely("exchange rates") { fetchRates() } }
            val mstr = async { fetchSafely("MSTR price") { fetchMstrPrice() } }
            val height = async { fetchSafely("block height") { fetchBlockHeight() } }
            val circulation = async { fetchSafely("circulating supply") { fetchCirculation() } }
            val fees = async { fetchSafely("recommended fees") { fetchFees() } }
            val difficulty = async { fetchSafely("difficulty adjustment") { fetchDifficulty() } }
            val hashRate = async { fetchSafely("hashrate") { fetchHashRate() } }
            val lightning = async { fetchSafely("lightning capacity") { fetchLightningCapacity() } }
            FetchResults(
                rates = rates.await(),
                mstrUsd = mstr.await(),
                blockHeight = height.await(),
                btcCirculation = circulation.await(),
                fees = fees.await(),
                difficulty = difficulty.await(),
                hashRate = hashRate.await(),
                lightningCapacity = lightning.await()
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
        val lightningCapacity: Double? = null
    )

    /**
     * Combines freshly fetched values with the cache, field by field.
     * The data counts as fresh (timestamp updated, no stale badge) only when the
     * core sources — BTC price and block height — both succeeded.
     */
    fun mergeWithCache(fresh: FetchResults, cached: PriceData?, now: Long): PriceData? {
        val btcUsd = fresh.rates?.usd ?: cached?.btcUsd ?: return null
        val btcThb = fresh.rates?.thb ?: cached?.btcThb ?: 0.0
        val mstrUsd = fresh.mstrUsd ?: cached?.mstrUsd ?: 0.0
        val coreOk = fresh.rates != null && fresh.blockHeight != null

        return PriceData(
            btcUsd = btcUsd,
            btcThb = btcThb,
            moscowTime = if (btcUsd > 0) (SATS_PER_BTC / btcUsd).toInt() else 0,
            mstrUsd = mstrUsd,
            mstrBtc = if (btcUsd > 0) mstrUsd / btcUsd else 0.0,
            blockHeight = fresh.blockHeight ?: cached?.blockHeight ?: 0,
            mstrBtcHeld = MSTR_BTC_HELD,
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
            lightningCapacity = fresh.lightningCapacity ?: cached?.lightningCapacity ?: 0.0
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

    private fun fetchRates(): Rates {
        val rates = JSONObject(httpGet("https://api.coinbase.com/v2/exchange-rates?currency=BTC"))
            .getJSONObject("data").getJSONObject("rates")
        return Rates(usd = rates.getString("USD").toDouble(), thb = rates.getString("THB").toDouble())
    }

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

    /** Public Lightning capacity, converted from sats to BTC. */
    private fun fetchLightningCapacity(): Double =
        JSONObject(httpGet("https://mempool.space/api/v1/lightning/statistics/latest"))
            .getJSONObject("latest").getDouble("total_capacity") / SATS_PER_BTC

    fun saveToPrefs(context: Context, data: PriceData) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putFloat(KEY_BTC_USD, data.btcUsd.toFloat())
            putFloat(KEY_BTC_THB, data.btcThb.toFloat())
            putInt(KEY_MOSCOW_TIME, data.moscowTime)
            putFloat(KEY_MSTR_USD, data.mstrUsd.toFloat())
            putFloat(KEY_MSTR_BTC, data.mstrBtc.toFloat())
            putInt(KEY_BLOCK_HEIGHT, data.blockHeight)
            putFloat(KEY_MSTR_BTC_HELD, data.mstrBtcHeld.toFloat())
            putFloat(KEY_BTC_CIRCULATION, data.btcCirculation.toFloat())
            putLong(KEY_TIMESTAMP, data.timestamp)
            putString(KEY_CURRENCY, data.currency)
            putBoolean(KEY_LAST_FETCH_SUCCESSFUL, data.lastFetchSuccessful)
            putInt(KEY_FEE_FASTEST, data.feeFastest)
            putInt(KEY_FEE_HALF_HOUR, data.feeHalfHour)
            putInt(KEY_FEE_HOUR, data.feeHour)
            putFloat(KEY_DIFFICULTY_CHANGE, data.difficultyChange.toFloat())
            putFloat(KEY_DIFFICULTY_PROGRESS, data.difficultyProgress.toFloat())
            putFloat(KEY_HASH_RATE, data.hashRate.toFloat())
            putFloat(KEY_LIGHTNING_CAPACITY, data.lightningCapacity.toFloat())
            apply()
        }
    }

    fun getFromPrefs(context: Context): PriceData? {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_TIMESTAMP)) return null
        
        return PriceData(
            btcUsd = prefs.getFloat(KEY_BTC_USD, 0f).toDouble(),
            btcThb = prefs.getFloat(KEY_BTC_THB, 0f).toDouble(),
            moscowTime = prefs.getInt(KEY_MOSCOW_TIME, 0),
            mstrUsd = prefs.getFloat(KEY_MSTR_USD, 0f).toDouble(),
            mstrBtc = prefs.getFloat(KEY_MSTR_BTC, 0f).toDouble(),
            blockHeight = prefs.getInt(KEY_BLOCK_HEIGHT, 0),
            mstrBtcHeld = prefs.getFloat(KEY_MSTR_BTC_HELD, 0f).toDouble(),
            btcCirculation = prefs.getFloat(KEY_BTC_CIRCULATION, 0f).toDouble(),
            timestamp = prefs.getLong(KEY_TIMESTAMP, 0L),
            currency = prefs.getString(KEY_CURRENCY, "USD") ?: "USD",
            lastFetchSuccessful = prefs.getBoolean(KEY_LAST_FETCH_SUCCESSFUL, true),
            feeFastest = prefs.getInt(KEY_FEE_FASTEST, 1),
            feeHalfHour = prefs.getInt(KEY_FEE_HALF_HOUR, 1),
            feeHour = prefs.getInt(KEY_FEE_HOUR, 1),
            difficultyChange = prefs.getFloat(KEY_DIFFICULTY_CHANGE, 0f).toDouble(),
            difficultyProgress = prefs.getFloat(KEY_DIFFICULTY_PROGRESS, 0f).toDouble(),
            hashRate = prefs.getFloat(KEY_HASH_RATE, 0f).toDouble(),
            lightningCapacity = prefs.getFloat(KEY_LIGHTNING_CAPACITY, 0f).toDouble()
        )
    }
}
