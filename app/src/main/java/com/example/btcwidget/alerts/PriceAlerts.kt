package com.example.btcwidget.alerts

import com.example.btcwidget.BlockclockMetrics
import com.example.btcwidget.PriceData
import java.util.Locale

enum class AlertKind {
    PRICE_ABOVE,
    PRICE_BELOW,
    FEE_AT_OR_BELOW,

    /** The next halving is at most `threshold` blocks away (144 ≈ one day). */
    HALVING_SOON,

    /** The next difficulty adjustment is at most `threshold` blocks away. */
    RETARGET_SOON;

    val isNetworkEvent: Boolean get() = this == HALVING_SOON || this == RETARGET_SOON

    /** Threshold used before the user sets one: network events warn a day ahead. */
    val defaultThreshold: Double get() = if (isNetworkEvent) BLOCKS_PER_DAY.toDouble() else 0.0

    companion object {
        const val BLOCKS_PER_DAY = 144
    }
}

/**
 * One user alert. Price thresholds are in [currency] (USD or THB); fee thresholds are
 * in sat/vB and ignore the currency.
 */
data class AlertRule(
    val kind: AlertKind,
    val threshold: Double,
    val currency: String = "USD",
    val enabled: Boolean = false
)

/** Rules that should notify now, and every rule whose condition currently holds. */
data class AlertCheck(val toNotify: List<AlertRule>, val nowMet: Set<AlertKind>)

/**
 * Alert logic, free of Android so it can be unit tested.
 *
 * Alerts fire on a crossing, not on every refresh: a rule notifies when its condition
 * becomes true after being false, then stays quiet until the condition turns false
 * again (e.g. price drops back below the "above" threshold) and re-arms.
 */
object PriceAlerts {

    fun isMet(rule: AlertRule, data: PriceData): Boolean = when (rule.kind) {
        AlertKind.PRICE_ABOVE -> price(data, rule.currency) > rule.threshold
        AlertKind.PRICE_BELOW -> price(data, rule.currency) < rule.threshold
        AlertKind.FEE_AT_OR_BELOW -> data.feeFastest <= rule.threshold
        // Each fires once per epoch: the count jumps back up after the event, re-arming it.
        AlertKind.HALVING_SOON ->
            data.blockHeight > 0 && BlockclockMetrics.blocksToHalving(data.blockHeight) <= rule.threshold
        AlertKind.RETARGET_SOON ->
            data.blockHeight > 0 && BlockclockMetrics.blocksToRetarget(data.blockHeight) <= rule.threshold
    }

    /**
     * Evaluates enabled rules against fresh [data]. [wasMet] is the result of the
     * previous check. Stale data (failed core fetch) never notifies and keeps the
     * previous state, so a recovering fetch cannot produce a false crossing.
     */
    fun check(rules: List<AlertRule>, data: PriceData, wasMet: Set<AlertKind>): AlertCheck {
        if (!data.lastFetchSuccessful) return AlertCheck(emptyList(), wasMet)
        val active = rules.filter { it.enabled && it.threshold > 0 }
        val met = active.filter { isMet(it, data) }
        return AlertCheck(
            toNotify = met.filter { it.kind !in wasMet },
            nowMet = met.map { it.kind }.toSet()
        )
    }

    fun price(data: PriceData, currency: String): Double =
        if (currency == "THB") data.btcThb else data.btcUsd

    fun formatPrice(value: Double, currency: String): String =
        if (currency == "THB") String.format(Locale.US, "฿%,.0f", value)
        else String.format(Locale.US, "$%,.0f", value)

    fun title(rule: AlertRule): String = when (rule.kind) {
        AlertKind.PRICE_ABOVE -> "BTC above ${formatPrice(rule.threshold, rule.currency)}"
        AlertKind.PRICE_BELOW -> "BTC below ${formatPrice(rule.threshold, rule.currency)}"
        AlertKind.FEE_AT_OR_BELOW -> "Fees down to ${rule.threshold.toInt()} sat/vB"
        AlertKind.HALVING_SOON -> "The halving is about a day away"
        AlertKind.RETARGET_SOON -> "Difficulty adjusts in about a day"
    }

    fun body(rule: AlertRule, data: PriceData): String = when (rule.kind) {
        AlertKind.PRICE_ABOVE, AlertKind.PRICE_BELOW ->
            "Now ${formatPrice(price(data, rule.currency), rule.currency)}"
        AlertKind.FEE_AT_OR_BELOW ->
            "Fastest ${data.feeFastest} · Mid ${data.feeHalfHour} · Low ${data.feeHour} sat/vB. A good time to move coins."
        AlertKind.HALVING_SOON -> {
            val left = BlockclockMetrics.blocksToHalving(data.blockHeight)
            String.format(Locale.US, "%,d blocks to go, at block %,d.", left, data.blockHeight + left)
        }
        AlertKind.RETARGET_SOON -> {
            val left = BlockclockMetrics.blocksToRetarget(data.blockHeight)
            val change = BlockclockMetrics.formatChange(data.difficultyChange)
            String.format(Locale.US, "%,d blocks to go.", left) + (change?.let { " Estimated change $it." } ?: "")
        }
    }
}
