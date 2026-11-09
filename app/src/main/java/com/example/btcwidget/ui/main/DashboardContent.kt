package com.example.btcwidget.ui.main

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.BlockclockMetrics
import com.example.btcwidget.PriceData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Metrics that can be pinned to the large device at the top of the dashboard. */
private val HERO_CHOICES = listOf("BLOCK", "PRICE", "SATS", "FEES")

@Composable
fun HeaderSection(title: String, subtitle: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 14.dp)
    ) {
        Text(
            text = title,
            color = EInk.Value,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 4.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle.uppercase(Locale.US),
            color = EInk.Caption,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp
        )
    }
}

@Composable
fun DashboardContent(
    data: PriceData,
    heroMetric: String,
    onHeroMetricSelected: (String) -> Unit,
    enabledModes: List<Boolean>,
    currencySetting: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!data.lastFetchSuccessful) {
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
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Hero panel, same proportions as a 2x1 widget.
        EInkDevice(
            display = BlockclockMetrics.forKey(heroMetric, data, currencySetting),
            aspectRatio = 2f,
            stale = !data.lastFetchSuccessful
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            HERO_CHOICES.forEach { key ->
                EInkChip(
                    text = key,
                    selected = heroMetric == key,
                    onClick = { onHeroMetricSelected(key) }
                )
            }
        }

        SectionLabel(
            text = "All metrics",
            modifier = Modifier
                .align(Alignment.Start)
                .padding(start = 4.dp, bottom = 10.dp)
        )

        // Remaining metrics as a two-column grid of smaller panels.
        val visible = BlockclockMetrics.MODE_KEYS.indices.filter { index ->
            enabledModes.getOrElse(index) { true } && BlockclockMetrics.MODE_KEYS[index] != heroMetric
        }
        visible.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pair.forEach { index ->
                    EInkDevice(
                        display = BlockclockMetrics.forMode(index, data, currencySetting),
                        modifier = Modifier.weight(1f),
                        aspectRatio = 1.7f,
                        progress = if (BlockclockMetrics.MODE_KEYS[index] == "HALVING") {
                            BlockclockMetrics.halvingProgress(data.blockHeight).toFloat()
                        } else {
                            null
                        }
                    )
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        Text(
            text = "LAST UPDATED ${formatter.format(Date(data.timestamp))}",
            color = EInk.Caption,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 10.dp, bottom = 20.dp)
        )
    }
}
