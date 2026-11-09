package com.example.btcwidget.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** General app settings: display currency, refresh cadence, haptics, and about. */
@Composable
fun SettingsScreen(
    currencySetting: String,
    onCurrencySelected: (String) -> Unit,
    refreshInterval: Int,
    onRefreshIntervalSelected: (Int) -> Unit,
    hapticEnabled: Boolean,
    onHapticToggle: (Boolean) -> Unit,
    appVersion: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        HeaderSection(title = "SETTINGS", subtitle = "App preferences")

        EInkPanel(title = "Display") {
            SettingRow("Currency", note = "Prices, sats and alerts") {
                listOf("USD", "THB").forEach { currency ->
                    EInkChip(
                        text = currency,
                        selected = currencySetting == currency,
                        onClick = { onCurrencySelected(currency) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        EInkPanel(title = "Updates") {
            SettingRow("Refresh every", note = "Widgets and alerts in the background") {
                listOf(15, 30, 60).forEach { minutes ->
                    EInkChip(
                        text = "${minutes}m",
                        selected = refreshInterval == minutes,
                        onClick = { onRefreshIntervalSelected(minutes) }
                    )
                }
            }
            Text(
                text = "Pull down on the dashboard to refresh now.",
                color = EInk.Caption,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        EInkPanel(title = "Feedback") {
            SettingRow(
                label = "Haptics",
                note = "Vibrate on widget taps and drag",
                modifier = Modifier.clickable { onHapticToggle(!hapticEnabled) }
            ) {
                EInkSwitch(checked = hapticEnabled, onCheckedChange = onHapticToggle)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        EInkPanel(title = "About") {
            AboutLine("Blockface" + (appVersion?.let { " $it" } ?: ""))
            AboutLine("Prices: Coinbase · Network: mempool.space")
            AboutLine("Supply: blockchain.info · MSTR: Yahoo Finance, CoinGecko")
            AboutLine("Display type: Oswald (SIL Open Font License)")
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun AboutLine(text: String) {
    Text(
        text = text,
        color = EInk.Label,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(vertical = 3.dp)
    )
}

/** A labelled settings row with controls on the right and an optional note under the label. */
@Composable
internal fun SettingRow(
    label: String,
    modifier: Modifier = Modifier,
    note: String? = null,
    dimmed: Boolean = false,
    controls: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (dimmed) EInk.Caption else EInk.Value,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
            if (note != null) {
                Text(
                    text = note,
                    color = EInk.Caption,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = controls
        )
    }
}
