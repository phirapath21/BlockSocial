package com.example.btcwidget.ui.main

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.PriceData
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HeaderSection(
    titleColor: Color,
    subtitleColor: Color,
    currentTheme: Int
) {
    val isModern = currentTheme == 10 || currentTheme == 11
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = if (isModern) "Satoshi Dashboard" else "SATOSHI DASHBOARD",
            color = titleColor,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = if (isModern) FontFamily.Default else FontFamily.Serif,
            letterSpacing = if (isModern) 0.sp else 1.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Blockclock Micro Console",
            color = subtitleColor,
            fontSize = 13.sp,
            fontFamily = if (isModern) FontFamily.Default else FontFamily.Serif
        )
    }
}

data class MetricItem(
    val key: String,
    val labelTop: String,
    val labelBottom: String,
    val value: String,
    val subtext: String,
    val isMstr: Boolean,
    val suffix: String = ""
)

@Composable
fun DashboardContent(
    data: PriceData,
    heroMetric: String,
    onHeroMetricSelected: (String) -> Unit,
    cardBgColor: Color,
    borderColor: Color,
    valueColor: Color,
    labelColor: Color,
    subTextColor: Color,
    enabledModes: List<Boolean>,
    currencySetting: String,
    currentTheme: Int
) {
    val isDynamic = currentTheme == 10
    val isIos = currentTheme == 11
    val isModern = isDynamic || isIos
    val isThb = currencySetting == "THB"
    val formattedBtcPrice = if (isThb) {
        String.format(Locale.US, "%.2f mil", data.btcThb / 1_000_000.0)
    } else {
        String.format(Locale.US, "$%,.0f", data.btcUsd)
    }
    val priceLabelBottom = if (isThb) "THB" else "USD"
    val priceSubtext = if (isThb) "Market price of bitcoin (THB)" else "Market price of bitcoin"

    val moscowTimeVal = if (isThb) {
        if (data.btcThb > 0) (100_000_000.0 / data.btcThb).toInt() else 0
    } else {
        data.moscowTime
    }
    val satsLabelBottom = if (isThb) "1 THB" else "1\$USD"
    val satsSubtext = if (isThb) "฿1 per Satoshis (Moscow Time)" else "$1 per Satoshis (Moscow Time)"

    val allMetrics = listOf(
        MetricItem(key = "PRICE", labelTop = "BTC", labelBottom = priceLabelBottom, value = formattedBtcPrice, subtext = priceSubtext, isMstr = false),
        MetricItem(key = "SATS", labelTop = "SATS", labelBottom = satsLabelBottom, value = String.format(Locale.US, "%,d", moscowTimeVal), subtext = satsSubtext, isMstr = false),
        MetricItem(key = "BLOCK", labelTop = "BLOCK", labelBottom = "HGHT", value = String.format(Locale.US, "%,d", data.blockHeight), subtext = "Current bitcoin block height", isMstr = false),
        MetricItem(key = "MSCW", labelTop = "MSCW", labelBottom = "TIME", value = String.format(Locale.US, "%d", moscowTimeVal), subtext = "Moscow Time clock face", isMstr = false),
        MetricItem(key = "MSTR_HELD", labelTop = "MSTR", labelBottom = "BTC", value = String.format(Locale.US, "%,.0f", data.mstrBtcHeld), subtext = "Strategy Inc. - BTC held", isMstr = true),
        MetricItem(key = "MSTR_RATIO", labelTop = "MSTR", labelBottom = "BTC", value = String.format(Locale.US, "%.5f", data.mstrBtc), subtext = "Strategy Inc. - BTC per share", isMstr = true),
        MetricItem(key = "MSTR_PRICE", labelTop = "MSTR", labelBottom = "USD", value = String.format(Locale.US, "$%,.0f", data.mstrUsd), subtext = "Strategy Inc. - Share price", isMstr = true),
        MetricItem(key = "CIRCULATION", labelTop = "BTC", labelBottom = "CIRC", value = String.format(Locale.US, "%.3f", data.btcCirculation / 1_000_000.0), suffix = "MIL", subtext = "Bitcoin in circulation", isMstr = false),
        MetricItem(key = "HALVING", labelTop = "HALV", labelBottom = "BLKS", value = String.format(Locale.US, "%,d", 210000 - (data.blockHeight % 210000)), subtext = "Blocks to next halving", isMstr = false),
        MetricItem(key = "FEES", labelTop = "FEES", labelBottom = "sat/vB", value = String.format(Locale.US, "%d", data.feeFastest), subtext = String.format(Locale.US, "Mid: %d / Low: %d", data.feeHalfHour, data.feeHour), isMstr = false),
        MetricItem(key = "HASHRATE", labelTop = "HASH", labelBottom = "RATE", value = String.format(Locale.US, "%.1f", data.hashRate), suffix = "EH/s", subtext = "Network Hash Rate (3d)", isMstr = false),
        MetricItem(key = "DIFF_ADJ", labelTop = "DIFF", labelBottom = "ADJ", value = String.format(Locale.US, "%+.1f%%", data.difficultyChange), subtext = String.format(Locale.US, "Progress: %.1f%%", data.difficultyProgress), isMstr = false),
        MetricItem(key = "LN_CAP", labelTop = "LN", labelBottom = "CAP", value = String.format(Locale.US, "%,.0f", data.lightningCapacity), suffix = "BTC", subtext = "Lightning Network Capacity", isMstr = false)
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Offline/Stale Data Warning Card
        if (!data.lastFetchSuccessful) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = if (isModern) (if (isIos) RoundedCornerShape(14.dp) else RoundedCornerShape(24.dp)) else RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D)),
                border = BorderStroke(1.dp, Color(0xFFEF4444))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚠️",
                        fontSize = 16.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "Offline Mode: Showing cached data. Check network status.",
                        color = Color(0xFFFCA5A5),
                        fontSize = 11.sp,
                        fontFamily = if (isModern) FontFamily.Default else FontFamily.Serif
                    )
                }
            }
        }

        // Hero Metric Card
        val heroData = when (heroMetric) {
            "PRICE" -> {
                if (isThb) {
                    Triple("BTC", "THB", String.format(Locale.US, "%.2f mil", data.btcThb / 1_000_000.0)) to "Market price of bitcoin"
                } else {
                    Triple("BTC", "USD", String.format(Locale.US, "$%,.0f", data.btcUsd)) to "Market price of bitcoin"
                }
            }
            "SATS" -> {
                if (isThb) {
                    val satsPerThb = if (data.btcThb > 0) (100_000_000.0 / data.btcThb).toInt() else 0
                    Triple("SATS", "1 THB", String.format(Locale.US, "%,d", satsPerThb)) to "฿1 per Satoshis"
                } else {
                    Triple("SATS", "1\$USD", String.format(Locale.US, "%,d", data.moscowTime)) to "$1 per Satoshis"
                }
            }
            "FEES" -> Triple("FEES", "sat/vB", String.format(Locale.US, "%d", data.feeFastest)) to String.format(Locale.US, "Mid: %d / Low: %d sat/vB", data.feeHalfHour, data.feeHour)
            else -> Triple("BLOCK", "HGHT", String.format(Locale.US, "%,d", data.blockHeight)) to "Bitcoin block height"
        }

        HeroBlockclockCard(
            labelTop = heroData.first.first,
            labelBottom = heroData.first.second,
            value = heroData.first.third,
            subtext = heroData.second,
            cardBgColor = cardBgColor,
            borderColor = borderColor,
            valueColor = valueColor,
            labelColor = labelColor,
            subTextColor = subTextColor,
            currentTheme = currentTheme
        )

        // Hero Selector row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val chips = listOf(
                "BLOCK" to "BLOCK",
                "PRICE" to "PRICE",
                "SATS" to "SATS",
                "FEES" to "FEES"
            )
            chips.forEach { (label, key) ->
                val isSelected = heroMetric == key
                val shape = if (isModern) RoundedCornerShape(16.dp) else RoundedCornerShape(4.dp)
                val chipBorder = if (isModern) null else BorderStroke(1.dp, if (isSelected) borderColor else labelColor.copy(alpha = 0.4f))
                Box(
                    modifier = Modifier
                        .then(if (chipBorder != null) Modifier.border(chipBorder, shape) else Modifier)
                        .background(
                            color = if (isSelected) {
                                if (isDynamic) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else if (isIos) {
                                    Color(0xFF007AFF)
                                } else {
                                    borderColor.copy(alpha = 0.15f)
                                }
                            } else {
                                if (isDynamic) {
                                    MaterialTheme.colorScheme.surfaceVariant
                                } else if (isIos) {
                                    if (isSystemInDarkTheme()) Color(0x551C1C1E) else Color(0x55E5E5EA)
                                } else {
                                    Color.Transparent
                                }
                            },
                            shape = shape
                        )
                        .clickable { onHeroMetricSelected(key) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isSelected && !isModern) "[$label]" else {
                            if (isIos) formatLabelForTheme(label, currentTheme) else label
                        },
                        color = if (isSelected) {
                            if (isDynamic) MaterialTheme.colorScheme.onPrimaryContainer
                            else if (isIos) Color.White
                            else borderColor
                        } else {
                            if (isDynamic) MaterialTheme.colorScheme.onSurfaceVariant
                            else if (isIos) Color(0xFF8E8E93)
                            else labelColor
                        },
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = if (isModern) FontFamily.Default else FontFamily.Monospace
                    )
                }
            }
        }

        // Secondary Metrics Grid
        val visibleMetrics = allMetrics.filterIndexed { index, metric ->
            val isEnabled = enabledModes.getOrElse(index) { true }
            val isHero = metric.key == heroMetric
            isEnabled && !isHero
        }

        val rows = visibleMetrics.chunked(2)
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { item ->
                    BlockclockCard(
                        labelTop = item.labelTop,
                        labelBottom = item.labelBottom,
                        value = item.value,
                        suffix = item.suffix,
                        subtext = item.subtext,
                        showLabel = item.labelTop.isNotEmpty(),
                        showLbl_divider = item.labelBottom.isNotEmpty(),
                        showSubText = item.subtext.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        cardBgColor = cardBgColor,
                        borderColor = borderColor,
                        valueColor = valueColor,
                        labelColor = labelColor,
                        subTextColor = subTextColor,
                        currentTheme = currentTheme
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Last updated text in monospaced design
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        Text(
            text = "Last updated: ${sdf.format(Date(data.timestamp))}",
            color = subTextColor,
            fontSize = 11.sp,
            fontFamily = if (isModern) FontFamily.Default else FontFamily.Serif,
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}

@Composable
fun HeroBlockclockCard(
    labelTop: String,
    labelBottom: String,
    value: String,
    subtext: String,
    suffix: String = "",
    cardBgColor: Color,
    borderColor: Color,
    valueColor: Color,
    labelColor: Color,
    subTextColor: Color,
    currentTheme: Int = 10
) {
    val isDynamic = currentTheme == 10
    val isIos = currentTheme == 11
    val isModern = isDynamic || isIos
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        shape = if (isIos) RoundedCornerShape(14.dp) else if (isDynamic) RoundedCornerShape(28.dp) else RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = if (isModern) (if (isIos) BorderStroke(1.dp, if (isSystemInDarkTheme()) Color(0x2BFFFFFF) else Color(0x52FFFFFF)) else null) else BorderStroke(2.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (labelBottom.isNotEmpty()) "${formatLabelForTheme(labelTop, currentTheme)} // ${formatLabelForTheme(labelBottom, currentTheme)}" else formatLabelForTheme(labelTop, currentTheme),
                    color = labelColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = if (isModern) FontFamily.Default else FontFamily.Monospace,
                    letterSpacing = if (isModern) 0.sp else 1.sp
                )

                Text(
                    text = if (isModern) "Hero Metric" else "◆ HERO STATUS ◆",
                    color = if (isModern) (if (isIos) Color(0xFF007AFF) else MaterialTheme.colorScheme.primary) else borderColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = if (isModern) FontFamily.Default else FontFamily.Monospace
                )
            }

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = value,
                    color = valueColor,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = if (isModern) FontFamily.Default else FontFamily.Serif,
                    letterSpacing = (-0.5).sp,
                    maxLines = 1
                )
                if (suffix.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isIos) formatLabelForTheme(suffix, currentTheme) else suffix,
                        color = labelColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = if (isModern) FontFamily.Default else FontFamily.Serif
                    )
                }
            }

            Text(
                text = subtext,
                color = subTextColor,
                fontSize = 10.sp,
                fontFamily = if (isModern) FontFamily.Default else FontFamily.Serif,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
fun BlockclockCard(
    labelTop: String,
    labelBottom: String,
    value: String,
    subtext: String,
    showLabel: Boolean,
    showLbl_divider: Boolean,
    showSubText: Boolean,
    modifier: Modifier = Modifier,
    suffix: String = "",
    cardBgColor: Color = Color(0xFF121214),
    borderColor: Color = Color(0xFFC5A059),
    valueColor: Color = Color(0xFFE4E4E7),
    labelColor: Color = Color(0xFFE4E4E7),
    subTextColor: Color = Color(0xFF8E8E93),
    currentTheme: Int = 10
) {
    val isDynamic = currentTheme == 10
    val isIos = currentTheme == 11
    val isModern = isDynamic || isIos
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(115.dp),
        shape = if (isIos) RoundedCornerShape(14.dp) else if (isDynamic) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = if (isModern) (if (isIos) BorderStroke(1.dp, if (isSystemInDarkTheme()) Color(0x2BFFFFFF) else Color(0x52FFFFFF)) else null) else BorderStroke(1.5.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showLabel) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = formatLabelForTheme(labelTop, currentTheme),
                            color = labelColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = if (isModern) FontFamily.Default else FontFamily.Monospace
                        )
                        if (labelBottom.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Box(
                                modifier = Modifier
                                    .width(20.dp)
                                    .height(1.dp)
                                    .background(labelColor.copy(alpha = 0.5f))
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatLabelForTheme(labelBottom, currentTheme),
                                color = labelColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = if (isModern) FontFamily.Default else FontFamily.Monospace
                            )
                        }
                    }
                }
                Text(
                    text = value,
                    color = valueColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = if (isModern) FontFamily.Default else FontFamily.Serif,
                    letterSpacing = (-0.5).sp,
                    maxLines = 1
                )

                if (suffix.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isIos) formatLabelForTheme(suffix, currentTheme) else suffix,
                        color = labelColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = if (isModern) FontFamily.Default else FontFamily.Serif
                    )
                }
            }

            if (showSubText) {
                Text(
                    text = subtext,
                    color = subTextColor,
                    fontSize = 8.sp,
                    fontFamily = if (isModern) FontFamily.Default else FontFamily.Serif,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}
