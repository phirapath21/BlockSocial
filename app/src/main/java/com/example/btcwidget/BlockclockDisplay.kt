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

/** Dashboard sections, in display order. */
enum class MetricCategory(val title: String) {
    MARKET("Market"),
    NETWORK("Network"),
    SUPPLY("Supply & Lightning"),
    STRATEGY("Strategy (MSTR)")
}

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

    private val CATEGORY = mapOf(
        "PRICE" to MetricCategory.MARKET,
        "SATS" to MetricCategory.MARKET,
        "MSCW" to MetricCategory.MARKET,
        "BLOCK" to MetricCategory.NETWORK,
        "FEES" to MetricCategory.NETWORK,
        "HASHRATE" to MetricCategory.NETWORK,
        "DIFF_ADJ" to MetricCategory.NETWORK,
        "HALVING" to MetricCategory.NETWORK,
        "CIRCULATION" to MetricCategory.SUPPLY,
        "LN_CAP" to MetricCategory.SUPPLY,
        "MSTR_HELD" to MetricCategory.STRATEGY,
        "MSTR_RATIO" to MetricCategory.STRATEGY,
        "MSTR_PRICE" to MetricCategory.STRATEGY
    )

    fun categoryOf(key: String): MetricCategory = CATEGORY[key] ?: MetricCategory.MARKET

    fun categoryOf(mode: Int): MetricCategory = categoryOf(MODE_KEYS.getOrElse(mode) { "" })

    const val MODE_PRICE = 0
    const val MODE_MOSCOW_TIME = 3

    /**
     * Moscow Time is by definition sats per US dollar, so it has no meaning in THB.
     * It is hidden while THB is selected; the user's own on/off choice is kept and
     * applies again after switching back to USD.
     */
    fun isAvailable(mode: Int, currency: String): Boolean =
        !(mode == MODE_MOSCOW_TIME && currency == "THB")

    /** Whether a mode is shown: enabled by the user and meaningful for [currency]. */
    fun isActive(mode: Int, enabled: Boolean, currency: String): Boolean =
        enabled && isAvailable(mode, currency)

    /**
     * The next active mode after [current] in the user's [order] (mode keys), or
     * [MODE_PRICE] when none is active (e.g. only Moscow Time was enabled and THB is
     * selected).
     */
    fun nextActiveMode(
        current: Int,
        currency: String,
        order: List<String> = MODE_KEYS,
        isEnabled: (Int) -> Boolean
    ): Int {
        val modes = orderedModes(order)
        val start = modes.indexOf(current).coerceAtLeast(0)
        for (i in 1..modes.size) {
            val candidate = modes[(start + i) % modes.size]
            if (isActive(candidate, isEnabled(candidate), currency)) return candidate
        }
        return MODE_PRICE
    }

    // ---- User ordering of modes (dashboard drag & drop; also widget tap order) ----

    /**
     * A complete order of all mode keys from a saved one: unknown keys are dropped,
     * duplicates removed, modes missing from it (e.g. added in a later version) are
     * appended, and the result is grouped by [MetricCategory] while keeping the user's
     * order inside each category.
     */
    fun sanitizeOrder(saved: List<String>): List<String> {
        val known = saved.filter { it in MODE_KEYS }.distinct()
        return (known + MODE_KEYS.filter { it !in known }).sortedBy { categoryOf(it).ordinal }
    }

    /** The default order: categories in order, modes in definition order within each. */
    val DEFAULT_ORDER: List<String> get() = sanitizeOrder(MODE_KEYS)

    fun parseOrder(saved: String?): List<String> =
        sanitizeOrder(saved?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty())

    fun serializeOrder(order: List<String>): String = sanitizeOrder(order).joinToString(",")

    /** Mode indices in the order given by [order]. */
    fun orderedModes(order: List<String>): List<Int> = sanitizeOrder(order).map { MODE_KEYS.indexOf(it) }

    fun isDefaultOrder(order: List<String>): Boolean = sanitizeOrder(order) == DEFAULT_ORDER

    /**
     * Moves [fromKey] to the position of [toKey]. Dragging down lands after [toKey],
     * dragging up lands before it, which matches swapping neighbours in a grid. Keys
     * hidden from the dashboard keep their relative place. Moves across categories are
     * ignored: each metric stays in its own section.
     */
    fun move(order: List<String>, fromKey: String, toKey: String): List<String> {
        val list = sanitizeOrder(order).toMutableList()
        val from = list.indexOf(fromKey)
        val to = list.indexOf(toKey)
        if (from < 0 || to < 0 || from == to) return list
        if (categoryOf(fromKey) != categoryOf(toKey)) return list
        list.add(to, list.removeAt(from))
        return list
    }

    /**
     * Moves [key] one step among [visible] keys (for accessibility "move earlier/later"
     * actions); [step] is -1 or +1. Returns the order unchanged at either end.
     */
    fun moveAmongVisible(order: List<String>, visible: List<String>, key: String, step: Int): List<String> {
        val sameSection = visible.filter { categoryOf(it) == categoryOf(key) }
        val i = sameSection.indexOf(key)
        val j = i + step
        if (i < 0 || j !in sameSection.indices) return sanitizeOrder(order)
        return move(order, key, sameSection[j])
    }

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
                "%d".f(satsPerUnit),
                caption = if (isThb) "Sats per 1 baht" else "Sats per 1 dollar"
            )
            2 -> BlockclockDisplay("BLOCK", "HGHT", "%d".f(data.blockHeight), caption = "Bitcoin block height")
            // Always sats per US dollar; hidden in THB (see isAvailable).
            3 -> BlockclockDisplay("MSCW", "TIME", "%d".f(data.moscowTime), caption = "Moscow time")
            4 -> BlockclockDisplay("MSTR", "BTC", "%.0f".f(data.mstrBtcHeld), caption = "Strategy BTC holdings")
            5 -> BlockclockDisplay("MSTR", "BTC", "%.5f".f(data.mstrBtc), caption = "Strategy BTC per share")
            6 -> BlockclockDisplay("MSTR", "USD", "$%.0f".f(data.mstrUsd), caption = "Strategy share price")
            7 -> BlockclockDisplay(
                "BTC", "CIRC", "%.3f".f(data.btcCirculation / 1_000_000.0), "MIL",
                caption = "Bitcoin in circulation"
            )
            8 -> BlockclockDisplay(
                "HALV", "BLKS", "%d".f(blocksToHalving(data.blockHeight)),
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
            12 -> BlockclockDisplay("LN", "CAP", "%.0f".f(data.lightningCapacity), "BTC", caption = "Lightning capacity")
            else -> if (isThb) {
                BlockclockDisplay("BTC", "THB", "%.2f".f(data.btcThb / 1_000_000.0), "MIL", caption = "Market price of bitcoin")
            } else {
                BlockclockDisplay("BTC", "USD", "$%.0f".f(data.btcUsd), caption = "Market price of bitcoin")
            }
        }
    }

    /** Halving widget modes: 0 = epoch progress %, 1 = days left, 2 = blocks left. */
    fun halving(mode: Int, blockHeight: Int): BlockclockDisplay {
        val blocksLeft = blocksToHalving(blockHeight)
        val percent = halvingProgress(blockHeight) * 100.0
        val days = blocksLeft / BLOCKS_PER_DAY
        return when (mode) {
            1 -> BlockclockDisplay("HALV", "ETA", "%.0f".f(days), "DAYS", caption = "%.1f%% of epoch done".f(percent))
            2 -> BlockclockDisplay("HALV", "BLKS", "%d".f(blocksLeft), caption = "≈ %.0f days to halving".f(days))
            else -> BlockclockDisplay("HALV", "PCT", "%.2f%%".f(percent), caption = "%,d blocks to halving".f(blocksLeft))
        }
    }

    fun blocksToHalving(blockHeight: Int): Int = HALVING_INTERVAL - (blockHeight % HALVING_INTERVAL)

    fun halvingProgress(blockHeight: Int): Double =
        (blockHeight % HALVING_INTERVAL).toDouble() / HALVING_INTERVAL

    private fun String.f(vararg args: Any): String = String.format(Locale.US, this, *args)
}
