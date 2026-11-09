package com.example.btcwidget.ui.main

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.example.btcwidget.BtcWidgetProvider
import com.example.btcwidget.R
import com.example.btcwidget.WidgetUpdater
import com.example.btcwidget.WidgetRefreshScheduler
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.compose.ui.tooling.preview.Preview
import com.example.btcwidget.theme.BTCWidgetTheme
import com.example.btcwidget.PriceData
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings


private fun formatLabelForTheme(label: String, currentTheme: Int): String {
    if (currentTheme == 11) {
        if (label.isEmpty()) return ""
        val words = label.split(" ")
        return words.joinToString(" ") { word ->
            if (word.contains("/")) {
                word.split("/").joinToString("/") { sub ->
                    sub.lowercase(Locale.US).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
                }
            } else if (word.lowercase(Locale.US) == "usd" || word.lowercase(Locale.US) == "btc" || word.lowercase(Locale.US) == "thb") {
                word.uppercase(Locale.US)
            } else {
                word.lowercase(Locale.US).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            }
        }
    }
    return label
}

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val prefs = remember { context.getSharedPreferences("blockclock_prefs", Context.MODE_PRIVATE) }
    val initialTheme = prefs.getInt("blockclock_theme", 0)
    val sanitizedTheme = if (initialTheme == 0 || initialTheme == 1 || initialTheme == 9) initialTheme else 0
    var currentTheme by remember { mutableStateOf(sanitizedTheme) }
    if (initialTheme != sanitizedTheme) {
        prefs.edit().putInt("blockclock_theme", sanitizedTheme).apply()
    }
    var showSettings by remember { mutableStateOf(false) }
    var heroMetric by remember { mutableStateOf(prefs.getString("hero_metric", "BLOCK") ?: "BLOCK") }
    var currencySetting by remember { mutableStateOf(prefs.getString("currency", "USD") ?: "USD") }
    var hapticEnabled by remember { mutableStateOf(prefs.getBoolean("haptic_enabled", true)) }
    var refreshInterval by remember { mutableStateOf(prefs.getInt("refresh_interval", 30)) }

    // Mode states for Settings
    val modeStates = remember {
        mutableStateListOf<Boolean>().apply {
            addAll((0..12).map { prefs.getBoolean("mode_enabled_$it", true) })
        }
    }

    val onHeroMetricSelected: (String) -> Unit = { metric ->
        prefs.edit().putString("hero_metric", metric).apply()
        heroMetric = metric
    }

    LaunchedEffect(Unit) {
        viewModel.loadInitialData(context)
        WidgetRefreshScheduler.schedulePeriodic(context)
    }

    val onThemeSelected: (Int) -> Unit = { newTheme ->
        prefs.edit().putInt("blockclock_theme", newTheme).apply()
        currentTheme = newTheme
        WidgetUpdater.updateAllWidgets(context)
    }

    val onCurrencySelected: (String) -> Unit = { newCurrency ->
        prefs.edit().putString("currency", newCurrency).apply()
        currencySetting = newCurrency
        WidgetUpdater.updateAllWidgets(context)
    }

    val onRefreshIntervalSelected: (Int) -> Unit = { newInterval ->
        prefs.edit().putInt("refresh_interval", newInterval).apply()
        refreshInterval = newInterval
        WidgetRefreshScheduler.schedulePeriodic(context, intervalChanged = true)
    }

    val onHapticToggle: (Boolean) -> Unit = { enabled ->
        prefs.edit().putBoolean("haptic_enabled", enabled).apply()
        hapticEnabled = enabled
    }

    val onModeToggle: (Int, Boolean) -> Unit = { index, checked ->
        val enabledCount = modeStates.count { it }
        if (checked || enabledCount > 1) {
            prefs.edit().putBoolean("mode_enabled_$index", checked).apply()
            modeStates[index] = checked
            WidgetUpdater.updateAllWidgets(context)
            viewModel.refreshData(context)
        }
    }

    MainScreenContent(
        state = state,
        currentTheme = currentTheme,
        showSettings = showSettings,
        heroMetric = heroMetric,
        modeStates = modeStates,
        currencySetting = currencySetting,
        onCurrencySelected = onCurrencySelected,
        refreshInterval = refreshInterval,
        onRefreshIntervalSelected = onRefreshIntervalSelected,
        hapticEnabled = hapticEnabled,
        onHapticToggle = onHapticToggle,
        onHeroMetricSelected = onHeroMetricSelected,
        onThemeSelected = onThemeSelected,
        onShowSettingsChange = { newValue: Boolean -> showSettings = newValue },
        onModeToggle = onModeToggle,
        onRefresh = { viewModel.refreshData(context) },
        onItemClick = onItemClick,
        modifier = modifier
    )
}

@Composable
fun ThemeSelectorSection(
    currentTheme: Int,
    onThemeSelected: (Int) -> Unit,
    labelColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "Personalize Display",
            color = labelColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            val themesList = listOf(
                Triple(0, "Dark", Color(0xFF1C1C1E)),
                Triple(1, "White", Color(0xFFFFFFFF)),
                Triple(9, "Orange Pill", Color(0xFFF7931A))
            )

            themesList.forEach { (index, name, color) ->
                val isSelected = currentTheme == index
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = color,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) {
                                if (index == 1) Color.Black else Color.White
                            } else {
                                Color(0xFF8E8E93)
                            },
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clickable { onThemeSelected(index) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Text(
                            text = "✓",
                            color = if (index == 1) {
                                Color.Black
                            } else {
                                Color.White
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

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
fun PreferencesPanelCard(
    currencySetting: String,
    onCurrencySelected: (String) -> Unit,
    refreshInterval: Int,
    onRefreshIntervalSelected: (Int) -> Unit,
    hapticEnabled: Boolean,
    onHapticToggle: (Boolean) -> Unit,
    cardBgColor: Color,
    borderColor: Color,
    valueColor: Color,
    labelColor: Color,
    subTextColor: Color,
    currentTheme: Int
) {
    val isDynamic = currentTheme == 10
    val isIos = currentTheme == 11
    val isModern = isDynamic || isIos
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        shape = if (isIos) RoundedCornerShape(14.dp) else if (isDynamic) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = if (isModern) (if (isIos) BorderStroke(1.dp, if (isSystemInDarkTheme()) Color(0x2BFFFFFF) else Color(0x52FFFFFF)) else null) else BorderStroke(1.5.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isModern) "Preferences" else "PREFERENCES",
                color = valueColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = if (isModern) FontFamily.Default else FontFamily.Serif,
                letterSpacing = if (isModern) 0.sp else 1.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Currency row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Currency",
                    color = valueColor,
                    fontSize = 12.sp,
                    fontFamily = if (isModern) FontFamily.Default else FontFamily.Serif
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("USD", "THB").forEach { curr ->
                        val isSelected = currencySetting == curr
                        val shape = if (isModern) RoundedCornerShape(16.dp) else RoundedCornerShape(4.dp)
                        val itemBorder = if (isModern) null else BorderStroke(1.dp, if (isSelected) borderColor else labelColor.copy(alpha = 0.4f))
                        Box(
                            modifier = Modifier
                                .then(if (itemBorder != null) Modifier.border(itemBorder, shape) else Modifier)
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
                                .clickable { onCurrencySelected(curr) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = curr,
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
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Refresh Interval row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Refresh Interval",
                    color = valueColor,
                    fontSize = 12.sp,
                    fontFamily = if (isModern) FontFamily.Default else FontFamily.Serif
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15, 30, 60).forEach { mins ->
                        val isSelected = refreshInterval == mins
                        val displayStr = "${mins}m"
                        val shape = if (isModern) RoundedCornerShape(16.dp) else RoundedCornerShape(4.dp)
                        val itemBorder = if (isModern) null else BorderStroke(1.dp, if (isSelected) borderColor else labelColor.copy(alpha = 0.4f))
                        Box(
                            modifier = Modifier
                                .then(if (itemBorder != null) Modifier.border(itemBorder, shape) else Modifier)
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
                                .clickable { onRefreshIntervalSelected(mins) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = displayStr,
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
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Vibration & Haptic feedback row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onHapticToggle(!hapticEnabled) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Vibration & Haptics",
                    color = valueColor,
                    fontSize = 12.sp,
                    fontFamily = if (isModern) FontFamily.Default else FontFamily.Serif
                )
                if (isModern) {
                    Switch(
                        checked = hapticEnabled,
                        onCheckedChange = { checked ->
                            onHapticToggle(checked)
                        },
                        colors = if (isIos) {
                            SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF34C759),
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = if (isSystemInDarkTheme()) Color(0xFF3A3A3C) else Color(0xFFE9E9EB)
                            )
                        } else {
                            SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                } else {
                    Checkbox(
                        checked = hapticEnabled,
                        onCheckedChange = { checked ->
                            onHapticToggle(checked)
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = borderColor,
                            uncheckedColor = labelColor,
                            checkmarkColor = cardBgColor
                        ),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsPanelCard(
    onModeToggle: (Int, Boolean) -> Unit,
    modeStates: List<Boolean>,
    cardBgColor: Color,
    borderColor: Color,
    valueColor: Color,
    labelColor: Color,
    subTextColor: Color,
    currentTheme: Int
) {
    val isDynamic = currentTheme == 10
    val isIos = currentTheme == 11
    val isModern = isDynamic || isIos
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        shape = if (isIos) RoundedCornerShape(14.dp) else if (isDynamic) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = if (isModern) (if (isIos) BorderStroke(1.dp, if (isSystemInDarkTheme()) Color(0x2BFFFFFF) else Color(0x52FFFFFF)) else null) else BorderStroke(1.5.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isModern) "Active Modes" else "SYSTEM SETTINGS",
                color = valueColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = if (isModern) FontFamily.Default else FontFamily.Serif,
                letterSpacing = if (isModern) 0.sp else 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Mode Customization
            Text(
                text = "Active Widget Modes",
                color = labelColor,
                fontSize = 11.sp,
                fontFamily = if (isModern) FontFamily.Default else FontFamily.Serif,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(6.dp))

            val modeNames = listOf(
                "BTC Price",
                "Sats per Fiat (Moscow Time)",
                "Block Height",
                "Moscow Time Clock Face",
                "MSTR BTC holdings",
                "MSTR Share-to-BTC ratio",
                "MSTR Share Price",
                "BTC Circulating Supply",
                "Halving Countdown",
                "Mempool Priority Fees",
                "Network Hash Rate",
                "Difficulty Adjustment",
                "Lightning Network Capacity"
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                modeNames.forEachIndexed { index, name ->
                    val isChecked = if (index < modeStates.size) modeStates[index] else true
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (index < modeStates.size) {
                                    onModeToggle(index, !modeStates[index])
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = name,
                            color = valueColor,
                            fontSize = 12.sp,
                            fontFamily = if (isModern) FontFamily.Default else FontFamily.Serif,
                            modifier = Modifier.weight(1f)
                        )
                        if (isModern) {
                            Switch(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    onModeToggle(index, checked)
                                },
                                colors = if (isIos) {
                                    SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF34C759),
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = if (isSystemInDarkTheme()) Color(0xFF3A3A3C) else Color(0xFFE9E9EB)
                                    )
                                } else {
                                    SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                        } else {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    onModeToggle(index, checked)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = borderColor,
                                    uncheckedColor = labelColor,
                                    checkmarkColor = cardBgColor
                                ),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
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

@Composable
fun SettingsScreen(
    currentTheme: Int,
    onThemeSelected: (Int) -> Unit,
    onModeToggle: (Int, Boolean) -> Unit,
    modeStates: List<Boolean>,
    currencySetting: String,
    onCurrencySelected: (String) -> Unit,
    refreshInterval: Int,
    onRefreshIntervalSelected: (Int) -> Unit,
    hapticEnabled: Boolean,
    onHapticToggle: (Boolean) -> Unit,
    cardBgColor: Color,
    borderColor: Color,
    valueColor: Color,
    labelColor: Color,
    subTextColor: Color
) {
    val isModern = currentTheme == 10 || currentTheme == 11
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Text(
                text = if (isModern) "Console Settings" else "CONSOLE SETTINGS",
                color = borderColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = if (isModern) FontFamily.Default else FontFamily.Serif,
                letterSpacing = if (isModern) 0.sp else 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Personalization & System Settings",
                color = labelColor,
                fontSize = 13.sp,
                fontFamily = if (isModern) FontFamily.Default else FontFamily.Serif
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        ThemeSelectorSection(
            currentTheme = currentTheme,
            onThemeSelected = onThemeSelected,
            labelColor = labelColor
        )

        Spacer(modifier = Modifier.height(16.dp))

        PreferencesPanelCard(
            currencySetting = currencySetting,
            onCurrencySelected = onCurrencySelected,
            refreshInterval = refreshInterval,
            onRefreshIntervalSelected = onRefreshIntervalSelected,
            hapticEnabled = hapticEnabled,
            onHapticToggle = onHapticToggle,
            cardBgColor = cardBgColor,
            borderColor = borderColor,
            valueColor = valueColor,
            labelColor = labelColor,
            subTextColor = subTextColor,
            currentTheme = currentTheme
        )

        Spacer(modifier = Modifier.height(16.dp))

        SettingsPanelCard(
            onModeToggle = onModeToggle,
            modeStates = modeStates,
            cardBgColor = cardBgColor,
            borderColor = borderColor,
            valueColor = valueColor,
            labelColor = labelColor,
            subTextColor = subTextColor,
            currentTheme = currentTheme
        )
    }
}

@Composable
fun LoadingScreen() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = Color(0xFFC5A059))
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Fetching market data...",
            color = Color(0xFF8E8E93),
            fontSize = 14.sp,
            fontFamily = FontFamily.Serif
        )
    }
}

@Composable
fun ErrorScreen(error: Throwable, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "⚠️",
            fontSize = 48.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = error.message ?: "An unexpected error occurred",
            color = Color(0xFFEF4444),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Serif,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRefresh,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFEF4444),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .width(180.dp)
                .height(48.dp)
        ) {
            Text(
                text = "Retry",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                fontFamily = FontFamily.Serif
            )
        }
    }
}

@Composable
fun MainScreenContent(
    state: MainScreenUiState,
    currentTheme: Int,
    showSettings: Boolean,
    heroMetric: String,
    modeStates: List<Boolean>,
    currencySetting: String,
    onCurrencySelected: (String) -> Unit,
    refreshInterval: Int,
    onRefreshIntervalSelected: (Int) -> Unit,
    hapticEnabled: Boolean,
    onHapticToggle: (Boolean) -> Unit,
    onHeroMetricSelected: (String) -> Unit,
    onThemeSelected: (Int) -> Unit,
    onShowSettingsChange: (Boolean) -> Unit,
    onModeToggle: (Int, Boolean) -> Unit,
    onRefresh: () -> Unit,
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeBgColor: Color
    val themeCardBgColor: Color
    val themeValueColor: Color
    val themeLabelColor: Color
    val themeSubTextColor: Color
    val themeBorderColor: Color

    when (currentTheme) {
        1 -> { // White / Light Theme
            themeBgColor = Color(0xFFF2F2F7)
            themeCardBgColor = Color(0x73FFFFFF)
            themeValueColor = Color(0xFF000000)
            themeLabelColor = Color(0xFF8E8E93)
            themeSubTextColor = Color(0xFF8E8E93)
            themeBorderColor = Color(0xFF007AFF)
        }
        9 -> { // Orange Pill Theme
            themeBgColor = Color(0xFF050200)
            themeCardBgColor = Color(0x731E0E05)
            themeValueColor = Color(0xFFF7931A)
            themeLabelColor = Color(0xFFA66210)
            themeSubTextColor = Color(0xFFA66210)
            themeBorderColor = Color(0xFFF7931A)
        }
        else -> { // Dark Theme (Default / Theme 0)
            themeBgColor = Color(0xFF000000)
            themeCardBgColor = Color(0x661C1C1E)
            themeValueColor = Color(0xFFFFFFFF)
            themeLabelColor = Color(0xFF8E8E93)
            themeSubTextColor = Color(0xFF8E8E93)
            themeBorderColor = Color(0xFF007AFF)
        }
    }

    val backgroundModifier = when (currentTheme) {
        1 -> { // White / Light Theme
            Modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(
                listOf(Color(0xFFF5E6E8), Color(0xFFD4E6F1), Color(0xFFEAF2F8))
            ))
        }
        9 -> { // Orange Pill Theme
            Modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(
                listOf(Color(0xFF2C0F00), Color(0xFF140700), Color(0xFF050200))
            ))
        }
        else -> { // Dark Theme (Default / Theme 0)
            Modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(
                listOf(Color(0xFF141527), Color(0xFF2A1B4E), Color(0xFF0B0C16))
            ))
        }
    }

    Box(
        modifier = backgroundModifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (showSettings) {
                    SettingsScreen(
                        currentTheme = currentTheme,
                        onThemeSelected = onThemeSelected,
                        onModeToggle = onModeToggle,
                        modeStates = modeStates,
                        currencySetting = currencySetting,
                        onCurrencySelected = onCurrencySelected,
                        refreshInterval = refreshInterval,
                        onRefreshIntervalSelected = onRefreshIntervalSelected,
                        hapticEnabled = hapticEnabled,
                        onHapticToggle = onHapticToggle,
                        cardBgColor = themeCardBgColor,
                        borderColor = themeBorderColor,
                        valueColor = themeValueColor,
                        labelColor = themeLabelColor,
                        subTextColor = themeSubTextColor
                    )
                } else {
                    Column(
                        modifier = modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        HeaderSection(
                            titleColor = themeBorderColor,
                            subtitleColor = themeLabelColor,
                            currentTheme = currentTheme
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        when (val currentState = state) {
                            is MainScreenUiState.Loading -> {
                                LoadingScreen()
                            }
                            is MainScreenUiState.Success -> {
                                DashboardContent(
                                    data = currentState.data,
                                    heroMetric = heroMetric,
                                    onHeroMetricSelected = onHeroMetricSelected,
                                    cardBgColor = themeCardBgColor,
                                    borderColor = themeBorderColor,
                                    valueColor = themeValueColor,
                                    labelColor = themeLabelColor,
                                    subTextColor = themeSubTextColor,
                                    enabledModes = modeStates,
                                    currencySetting = currencySetting,
                                    currentTheme = currentTheme
                                )
                            }
                            is MainScreenUiState.Error -> {
                                ErrorScreen(
                                    error = currentState.throwable,
                                    onRefresh = onRefresh
                                )
                            }
                        }
                    }
                }
            }

            val selectedAccent = if (currentTheme == 9) Color(0xFFF7931A) else Color(0xFF007AFF)
            val navBg = when (currentTheme) {
                1 -> Color(0x99F2F2F7)
                9 -> Color(0x99140700)
                else -> Color(0x991C1C1E)
            }
            NavigationBar(
                containerColor = navBg,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home", fontFamily = FontFamily.Default) },
                    selected = !showSettings,
                    onClick = { onShowSettingsChange(false) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = selectedAccent,
                        selectedTextColor = selectedAccent,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = Color(0xFF8E8E93),
                        unselectedTextColor = Color(0xFF8E8E93)
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontFamily = FontFamily.Default) },
                    selected = showSettings,
                    onClick = { onShowSettingsChange(true) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = selectedAccent,
                        selectedTextColor = selectedAccent,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = Color(0xFF8E8E93),
                        unselectedTextColor = Color(0xFF8E8E93)
                    )
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    val sampleData = PriceData(
        btcUsd = 65000.0,
        btcThb = 2200000.0,
        moscowTime = 1538,
        mstrUsd = 1600.0,
        mstrBtc = 0.00012,
        blockHeight = 840000,
        mstrBtcHeld = 214400.0,
        btcCirculation = 19680000.0,
        timestamp = System.currentTimeMillis(),
        feeFastest = 25,
        feeHalfHour = 20,
        feeHour = 10
    )

    BTCWidgetTheme {
        MainScreenContent(
            state = MainScreenUiState.Success(sampleData),
            currentTheme = 0,
            showSettings = false,
            heroMetric = "BLOCK",
            modeStates = List(13) { true },
            currencySetting = "USD",
            onCurrencySelected = {},
            refreshInterval = 30,
            onRefreshIntervalSelected = {},
            hapticEnabled = true,
            onHapticToggle = {},
            onHeroMetricSelected = {},
            onThemeSelected = {},
            onShowSettingsChange = {},
            onModeToggle = { _, _ -> },
            onRefresh = {},
            onItemClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenSettingsPreview() {
    val sampleData = PriceData(
        btcUsd = 65000.0,
        btcThb = 2200000.0,
        moscowTime = 1538,
        mstrUsd = 1600.0,
        mstrBtc = 0.00012,
        blockHeight = 840000,
        mstrBtcHeld = 214400.0,
        btcCirculation = 19680000.0,
        timestamp = System.currentTimeMillis(),
        feeFastest = 25,
        feeHalfHour = 20,
        feeHour = 10
    )

    BTCWidgetTheme {
        MainScreenContent(
            state = MainScreenUiState.Success(sampleData),
            currentTheme = 0,
            showSettings = true,
            heroMetric = "BLOCK",
            modeStates = List(13) { true },
            currencySetting = "USD",
            onCurrencySelected = {},
            refreshInterval = 30,
            onRefreshIntervalSelected = {},
            hapticEnabled = true,
            onHapticToggle = {},
            onHeroMetricSelected = {},
            onThemeSelected = {},
            onShowSettingsChange = {},
            onModeToggle = { _, _ -> },
            onRefresh = {},
            onItemClick = {}
        )
    }
}
