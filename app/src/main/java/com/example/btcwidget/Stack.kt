package com.example.btcwidget

import java.math.BigDecimal
import java.math.RoundingMode

/** Units the user can enter their holdings in. */
enum class StackUnit { BTC, SATS }

/** Parsing and formatting of the user's holdings, kept in satoshis. */
object Stack {
    const val SATS_PER_BTC = 100_000_000L
    private const val MAX_SATS = 21_000_000L * SATS_PER_BTC

    /**
     * Parses user input into sats: BTC allows up to 8 decimals ("0.5", "1,25" too),
     * sats must be whole. Returns null for empty, invalid, negative or impossible
     * (> 21M BTC) amounts.
     */
    fun parse(input: String, unit: StackUnit): Long? {
        val text = input.trim().replace(",", ".").replace("_", "").replace(" ", "")
        if (text.isEmpty()) return null
        val amount = text.toBigDecimalOrNull() ?: return null
        if (amount.signum() < 0) return null
        val sats = when (unit) {
            StackUnit.SATS -> if (amount.stripTrailingZeros().scale() > 0) return null else amount
            StackUnit.BTC -> if (amount.stripTrailingZeros().scale() > 8) return null else amount.movePointRight(8)
        }
        val value = sats.setScale(0, RoundingMode.DOWN).toLong()
        return if (value > MAX_SATS) null else value
    }

    /** "0.5", "0.00012345", "21" — up to 8 decimals, no trailing zeros. */
    fun formatBtc(sats: Long): String =
        BigDecimal.valueOf(sats).movePointLeft(8).stripTrailingZeros().toPlainString()

    /** Text for an input field showing [sats] in [unit]. */
    fun toInput(sats: Long, unit: StackUnit): String =
        if (sats <= 0) "" else when (unit) {
            StackUnit.BTC -> formatBtc(sats)
            StackUnit.SATS -> sats.toString()
        }

    /** Portfolio cost basis and unrealized profit/loss metrics. */
    data class ProfitLoss(
        val currentValue: Double,
        val costBasis: Double,
        val avgBuyPrice: Double,
        val profitAmount: Double,
        val profitPercent: Double
    )

    /** Computes average buy price and unrealized P/L for [stackSats] at [currentBtcPrice] vs [totalCostBasis]. */
    fun profitLoss(stackSats: Long, currentBtcPrice: Double, totalCostBasis: Double): ProfitLoss? {
        if (stackSats <= 0 || currentBtcPrice <= 0.0 || totalCostBasis <= 0.0) return null
        val btc = stackSats.toDouble() / SATS_PER_BTC
        val currentValue = btc * currentBtcPrice
        val avgBuyPrice = totalCostBasis / btc
        val profitAmount = currentValue - totalCostBasis
        val profitPercent = (profitAmount / totalCostBasis) * 100.0
        return ProfitLoss(currentValue, totalCostBasis, avgBuyPrice, profitAmount, profitPercent)
    }
}
