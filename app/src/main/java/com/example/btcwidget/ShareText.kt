package com.example.btcwidget

import java.util.Locale

/**
 * Post text for sharing a dashboard metric, e.g. to a Nostr client: one plain line a
 * reader can quote or repost, anchored to the block height. Never for [BlockclockMetrics.MODE_STACK]:
 * holdings stay private.
 */
object ShareText {
    /** Metrics that can be shared. */
    val SHAREABLE = setOf("PRICE", "BLOCK", "HALVING", "SATS", "FEES")

    private const val BLOCKS_PER_DAY = 144.0
    private const val TAGS = "#bitcoin"

    fun forKey(key: String, data: PriceData, currency: String): String? {
        val block = "%,d".f(data.blockHeight)
        val line = when (key) {
            "PRICE" -> {
                val thb = currency == "THB"
                val price = if (thb) "฿%,.0f".f(data.btcThb) else "$%,.0f".f(data.btcUsd)
                val change = BlockclockMetrics.formatChange(if (thb) data.change24hThb else data.change24hUsd)
                "Bitcoin: $price" + (change?.let { " ($it in 24h)" } ?: "") + " · block $block"
            }
            "BLOCK" -> "Bitcoin block height: $block"
            "HALVING" -> {
                val left = BlockclockMetrics.blocksToHalving(data.blockHeight)
                val days = left / BLOCKS_PER_DAY
                val at = data.blockHeight + left
                "Next bitcoin halving in %,d blocks (≈ %,.0f days), at block %,d".f(left, days, at)
            }
            "SATS" -> if (currency == "THB") {
                val sats = if (data.btcThb > 0) 100_000_000.0 / data.btcThb else 0.0
                "1 THB = %,.0f sats · block $block".f(sats)
            } else {
                "1 USD = %,d sats · block $block".f(data.moscowTime)
            }
            "FEES" -> "Bitcoin fees: high %d · mid %d · low %d sat/vB · block $block"
                .f(data.feeFastest, data.feeHalfHour, data.feeHour)
            else -> return null
        }
        return "$line\n\n$TAGS"
    }

    private fun String.f(vararg args: Any): String = String.format(Locale.US, this, *args)
}
