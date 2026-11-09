package com.example.btcwidget

import org.json.JSONArray
import org.json.JSONObject

/** Where the BTC/USD spot price comes from (Settings > Data sources). */
enum class PriceSource(val label: String) {
    COINBASE("Coinbase"),
    KRAKEN("Kraken");

    companion object {
        fun from(name: String?): PriceSource = entries.firstOrNull { it.name == name } ?: COINBASE
    }
}

/** Where the BTC/THB price comes from: converted from USD, or the Bitkub THB market. */
enum class ThbSource(val label: String) {
    CONVERTED("Converted"),
    BITKUB("Bitkub");

    companion object {
        fun from(name: String?): ThbSource = entries.firstOrNull { it.name == name } ?: CONVERTED
    }
}

/** Open and last price over the trailing 24 hours. */
data class DayStats(val open: Double, val last: Double) {
    /** Percent change over the window, or NaN when the open is unknown. */
    val changePct: Double get() = PriceMath.changePct(open, last)
}

data class BitkubTicker(val last: Double, val percentChange: Double)

/** One point of price history: epoch seconds and closing price. */
data class PricePoint(val time: Long, val price: Double)

/** Response parsers for the price APIs. Pure functions, unit tested with sample JSON. */
object ApiParsers {

    /** Coinbase `/v2/exchange-rates?currency=BTC`: BTC priced in USD and THB. */
    fun coinbaseRates(json: String): PriceRepository.Rates {
        val rates = JSONObject(json).getJSONObject("data").getJSONObject("rates")
        return PriceRepository.Rates(usd = rates.getString("USD").toDouble(), thb = rates.getString("THB").toDouble())
    }

    /** Coinbase Exchange `/products/BTC-USD/stats`: 24h `open` and `last` as strings. */
    fun coinbaseStats(json: String): DayStats {
        val obj = JSONObject(json)
        return DayStats(open = obj.getString("open").toDouble(), last = obj.getString("last").toDouble())
    }

    /**
     * Kraken `/0/public/Ticker?pair=XBTUSD`: the result is keyed by Kraken's pair name
     * (XXBTZUSD); `c[0]` is the last trade price.
     */
    fun krakenLast(json: String): Double {
        val obj = JSONObject(json)
        val errors = obj.optJSONArray("error")
        if (errors != null && errors.length() > 0) error("Kraken: ${errors.getString(0)}")
        val result = obj.getJSONObject("result")
        val pair = result.keys().asSequence().first()
        return result.getJSONObject(pair).getJSONArray("c").getString(0).toDouble()
    }

    /** Bitkub `/api/v3/market/ticker?sym=btc_thb`: an array with one ticker object. */
    fun bitkubTicker(json: String): BitkubTicker {
        val ticker = JSONArray(json).getJSONObject(0)
        return BitkubTicker(
            last = ticker.getString("last").toDouble(),
            percentChange = ticker.getString("percent_change").toDouble()
        )
    }

    /**
     * Coinbase Exchange `/products/BTC-USD/candles`: arrays of
     * [time, low, high, open, close, volume], newest first. Returned oldest first.
     */
    fun coinbaseCandles(json: String): List<PricePoint> {
        val arr = JSONArray(json)
        return (0 until arr.length())
            .map { i ->
                val c = arr.getJSONArray(i)
                PricePoint(time = c.getLong(0), price = c.getDouble(4))
            }
            .sortedBy { it.time }
    }
}

object PriceMath {
    fun changePct(open: Double, last: Double): Double =
        if (open > 0 && !open.isNaN() && !last.isNaN()) (last - open) / open * 100.0 else Double.NaN

    /** Sats per unit move opposite to price: +10% price is about -9.1% sats. */
    fun satsChangePct(priceChangePct: Double): Double =
        if (priceChangePct.isNaN()) Double.NaN else (1.0 / (1.0 + priceChangePct / 100.0) - 1.0) * 100.0
}
