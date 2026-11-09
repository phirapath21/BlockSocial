package com.example.btcwidget

import java.util.Locale

/**
 * What one blockclock shows: a stacked two-line label, a value with an optional
 * unit, and a caption underneath.
 */
data class BlockclockDisplay(
    val labelTop: String,
    val labelBottom: String,
    val value: String,
    val suffix: String = "",
    val caption: String = ""
)

object BlockclockMetrics {
    /** Stable keys for the 13 modes, in widget mode order. */
    val MODE_KEYS = listOf(
        "PRICE", "SATS", "BLOCK", "MSCW", "MSTR_HELD", "MSTR_RATIO", "MSTR_PRICE",
        "CIRCULATION", "HALVING", "FEES", "HASHRATE", "DIFF_ADJ", "LN_CAP"
    )

    val MODE_NAMES = listOf(
        "BTC Price",
        "Sats per Fiat",
        "Block Height",
        "Moscow Time Clock",
        "MSTR BTC Holdings",
        "MSTR Share-to-BTC Ratio",
        "MSTR Share Price",
        "BTC Circulating Supply",
        "Halving Countdown",
        "Mempool Priority Fees",
        "Network Hash Rate",
        "Difficulty Adjustment",
        "Lightning Network Capacity"
    )

    val MODE_COUNT = MODE_KEYS.size

    private const val HALVING_INTERVAL = 210_000
    private const val BLOCKS_PER_DAY = 144.0

    fun forKey(key: String, data: PriceData, currency: String): BlockclockDisplay =
        forMode(MODE_KEYS.indexOf(key).coerceAtLeast(0), data, currency)

    fun forMode(mode: Int, data: PriceData, currency: String): BlockclockDisplay {
        val isThb = currency == "THB"
        val satsPerUnit = if (isThb) {
            if (data.btcThb > 0) (100_000_000.0 / data.btcThb).toInt() else 0
        } else {
            data.moscowTime
        }
        return when (mode) {
            1 -> BlockclockDisplay(
                "SATS", if (isThb) "1THB" else "1USD",
                "%,d".f(satsPerUnit),
                caption = if (isThb) "Sats per 1 baht" else "Sats per 1 dollar"
            )
            2 -> BlockclockDisplay("BLOCK", "HGHT", "%,d".f(data.blockHeight), caption = "Bitcoin block height")
            3 -> BlockclockDisplay(
                "MSCW", "TIME", moscowClock(satsPerUnit),
                caption = if (isThb) "Moscow time (THB)" else "Moscow time"
            )
            4 -> BlockclockDisplay("MSTR", "BTC", "%,.0f".f(data.mstrBtcHeld), caption = "Strategy BTC holdings")
            5 -> BlockclockDisplay("MSTR", "BTC", "%.5f".f(data.mstrBtc), caption = "Strategy BTC per share")
            6 -> BlockclockDisplay("MSTR", "USD", "%,.0f".f(data.mstrUsd), caption = "Strategy share price")
            7 -> BlockclockDisplay(
                "BTC", "CIRC", "%.3f".f(data.btcCirculation / 1_000_000.0), "MIL",
                caption = "Bitcoin in circulation"
            )
            8 -> BlockclockDisplay(
                "HALV", "BLKS", "%,d".f(blocksToHalving(data.blockHeight)),
                caption = "Blocks to next halving"
            )
            9 -> BlockclockDisplay(
                "FEES", "SAT/VB", "%d".f(data.feeFastest),
                caption = "Mid %d · Low %d sat/vB".f(data.feeHalfHour, data.feeHour)
            )
            10 -> BlockclockDisplay("HASH", "RATE", "%.1f".f(data.hashRate), "EH/S", caption = "Network hash rate (3d)")
            11 -> BlockclockDisplay(
                "DIFF", "ADJ", "%+.1f%%".f(data.difficultyChange),
                caption = "Epoch progress %.1f%%".f(data.difficultyProgress)
            )
            12 -> BlockclockDisplay("LN", "CAP", "%,.0f".f(data.lightningCapacity), "BTC", caption = "Lightning capacity")
            else -> if (isThb) {
                BlockclockDisplay("BTC", "THB", "%.2f".f(data.btcThb / 1_000_000.0), "MIL", caption = "Market price of bitcoin")
            } else {
                BlockclockDisplay("BTC", "USD", "%,.0f".f(data.btcUsd), caption = "Market price of bitcoin")
            }
        }
    }

    /** Halving widget modes: 0 = epoch progress %, 1 = days left, 2 = blocks left. */
    fun halving(mode: Int, blockHeight: Int): BlockclockDisplay {
        val blocksLeft = blocksToHalving(blockHeight)
        val percent = halvingProgress(blockHeight) * 100.0
        val days = blocksLeft / BLOCKS_PER_DAY
        return when (mode) {
            1 -> BlockclockDisplay("HALV", "ETA", "%,.0f".f(days), "DAYS", caption = "%.1f%% of epoch done".f(percent))
            2 -> BlockclockDisplay("HALV", "BLKS", "%,d".f(blocksLeft), caption = "≈ %.0f days to halving".f(days))
            else -> BlockclockDisplay("HALV", "PCT", "%.2f%%".f(percent), caption = "%,d blocks to halving".f(blocksLeft))
        }
    }

    fun blocksToHalving(blockHeight: Int): Int = HALVING_INTERVAL - (blockHeight % HALVING_INTERVAL)

    fun halvingProgress(blockHeight: Int): Double =
        (blockHeight % HALVING_INTERVAL).toDouble() / HALVING_INTERVAL

    /** Sats per unit shown as a clock face: 1030 sats reads "10:30". */
    fun moscowClock(sats: Int): String = "%d:%02d".f(sats / 100, sats % 100)

    private fun String.f(vararg args: Any): String = String.format(Locale.US, this, *args)
}
