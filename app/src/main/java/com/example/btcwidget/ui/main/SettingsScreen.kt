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
import com.example.btcwidget.BlockclockMetrics

@Composable
fun SettingsScreen(
    modeStates: List<Boolean>,
    onModeToggle: (Int, Boolean) -> Unit,
    currencySetting: String,
    onCurrencySelected: (String) -> Unit,
    refreshInterval: Int,
    onRefreshIntervalSelected: (Int) -> Unit,
    hapticEnabled: Boolean,
    onHapticToggle: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        HeaderSection(title = "SETTINGS", subtitle = "Console configuration")

        EInkPanel(title = "Preferences") {
            SettingRow("Currency") {
                listOf("USD", "THB").forEach { currency ->
                    EInkChip(
                        text = currency,
                        selected = currencySetting == currency,
                        onClick = { onCurrencySelected(currency) }
                    )
                }
            }
            SettingRow("Refresh") {
                listOf(15, 30, 60).forEach { minutes ->
                    EInkChip(
                        text = "${minutes}m",
                        selected = refreshInterval == minutes,
                        onClick = { onRefreshIntervalSelected(minutes) }
                    )
                }
            }
            SettingRow(
                label = "Haptics",
                modifier = Modifier.clickable { onHapticToggle(!hapticEnabled) }
            ) {
                EInkSwitch(checked = hapticEnabled, onCheckedChange = onHapticToggle)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        EInkPanel(title = "Widget modes") {
            BlockclockMetrics.MODE_NAMES.forEachIndexed { index, name ->
                val checked = modeStates.getOrElse(index) { true }
                SettingRow(
                    label = name,
                    modifier = Modifier.clickable { onModeToggle(index, !checked) }
                ) {
                    EInkSwitch(checked = checked, onCheckedChange = { onModeToggle(index, it) })
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun SettingRow(
    label: String,
    modifier: Modifier = Modifier,
    controls: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = EInk.Value,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = controls
        )
    }
}
