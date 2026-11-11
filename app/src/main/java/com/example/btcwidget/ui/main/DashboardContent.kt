package com.example.btcwidget.ui.main

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.BlockclockMetrics
import com.example.btcwidget.PriceData
import com.example.btcwidget.PricePoint
import com.example.btcwidget.ShareText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Metrics that can be pinned to the large panel at the top of the dashboard. */
private val HERO_CHOICES = listOf("BLOCK", "PRICE", "HALVING", "SATS", "FEES")

/**
 * The dashboard: one large panel for the chosen metric and the BTC price chart.
 * All metric panels (with reordering and widget switches) live in the Widgets tab.
 */
@Composable
fun DashboardContent(
    data: PriceData,
    heroMetric: String,
    onHeroMetricSelected: (String) -> Unit,
    currencySetting: String,
    stackSats: Long = 0,
    onSetUpStack: () -> Unit = {},
    history: List<PricePoint> = emptyList(),
    /** Shares the hero metric (key) through the system share sheet. */
    onShare: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val hasStack = stackSats > 0
    // A cleared stack can't stay the hero.
    val hero = if (heroMetric == "STACK" && !hasStack) "PRICE" else heroMetric

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "header") { HeaderSection(title = "BLOCK", subtitle = "Bitcoin at a glance") }

        if (!data.lastFetchSuccessful) {
            item(key = "offline") {
                Text(
                    text = "OFFLINE · SHOWING CACHED DATA",
                    color = EInk.Alert,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, EInk.Alert, EInk.ChipShape)
                        .padding(vertical = 8.dp, horizontal = 12.dp)
                )
            }
        }

        // Hero panel, same proportions as a 2x1 widget.
        item(key = "hero") {
            EInkDevice(
                display = BlockclockMetrics.forKey(hero, data, currencySetting, stackSats),
                aspectRatio = 2f,
                stale = !data.lastFetchSuccessful,
                progress = if (hero == "HALVING") BlockclockMetrics.halvingProgress(data.blockHeight).toFloat() else null
            )
        }

        // Your stack is never shared: holdings stay private.
        if (hero in ShareText.SHAREABLE) {
            item(key = "share") {
                Text(
                    text = "SHARE ›",
                    color = EInk.Value,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClickLabel = "Share ${BlockclockMetrics.MODE_NAMES.getOrElse(BlockclockMetrics.MODE_KEYS.indexOf(hero)) { hero }}") { onShare(hero) }
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                )
            }
        }

        item(key = "hero_choices") {
            val choices = if (hasStack) listOf("STACK") + HERO_CHOICES else HERO_CHOICES
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                choices.forEach { key ->
                    EInkChip(text = key, selected = hero == key, onClick = { onHeroMetricSelected(key) })
                }
            }
        }

        item(key = "price_chart") {
            PriceChartPanel(
                history = history,
                currency = currencySetting,
                usdToCurrency = if (currencySetting == "THB" && data.btcUsd > 0) data.btcThb / data.btcUsd else 1.0
            )
        }

        if (!hasStack) {
            item(key = "stack_setup") { StackSetupCard(onSetUp = onSetUpStack) }
        }

        item(key = "updated") {
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            Text(
                text = "LAST UPDATED ${formatter.format(Date(data.timestamp))}",
                color = EInk.Caption,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            )
        }
    }
}
