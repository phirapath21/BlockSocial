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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.BlockclockMetrics
import com.example.btcwidget.alerts.AlertKind
import com.example.btcwidget.alerts.AlertRule
import java.util.Locale

@Composable
fun SettingsScreen(
    modeStates: List<Boolean>,
    onModeToggle: (Int, Boolean) -> Unit,
    currencySetting: String,
    onCurrencySelected: (String) -> Unit,
    refreshInterval: Int,
    onRefreshIntervalSelected: (Int) -> Unit,
    hapticEnabled: Boolean,
    onHapticToggle: (Boolean) -> Unit,
    alerts: List<AlertRule> = emptyList(),
    onAlertChange: (AlertRule) -> Unit = {},
    notificationsAllowed: Boolean = true
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

        if (alerts.isNotEmpty()) {
            EInkPanel(title = "Price alerts") {
                alerts.forEach { rule ->
                    AlertRow(rule = rule, currency = currencySetting, onChange = onAlertChange)
                }
                val note = if (alerts.any { it.enabled } && !notificationsAllowed) {
                    "Notifications are off for Blockface. Allow them in system settings to get alerts."
                } else {
                    "Checked with each background refresh (every $refreshInterval min). One notification per crossing."
                }
                Text(
                    text = note,
                    color = if (notificationsAllowed) EInk.Caption else EInk.Alert,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        EInkPanel(title = "Widget modes") {
            BlockclockMetrics.MODE_NAMES.forEachIndexed { index, name ->
                val available = BlockclockMetrics.isAvailable(index, currencySetting)
                val checked = available && modeStates.getOrElse(index) { true }
                SettingRow(
                    label = name,
                    note = if (available) null else "USD only · off while THB is selected",
                    modifier = if (available) Modifier.clickable { onModeToggle(index, !checked) } else Modifier
                ) {
                    EInkSwitch(
                        checked = checked,
                        onCheckedChange = { onModeToggle(index, it) },
                        enabled = available
                    )
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
    note: String? = null,
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
                color = if (note == null) EInk.Value else EInk.Caption,
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

/**
 * One alert: label, threshold field and on/off switch. Price thresholds use the
 * currency selected in Preferences; editing an enabled alert saves as you type.
 */
@Composable
private fun AlertRow(rule: AlertRule, currency: String, onChange: (AlertRule) -> Unit) {
    val isFee = rule.kind == AlertKind.FEE_AT_OR_BELOW
    val label = when (rule.kind) {
        AlertKind.PRICE_ABOVE -> "BTC rises above"
        AlertKind.PRICE_BELOW -> "BTC falls below"
        AlertKind.FEE_AT_OR_BELOW -> "Fees at or below"
    }
    // A price alert set in the other currency is shown as such until edited.
    val ruleCurrency = if (isFee || !rule.enabled) currency else rule.currency
    var text by remember(rule.kind) {
        mutableStateOf(if (rule.threshold > 0) String.format(Locale.US, "%.0f", rule.threshold) else "")
    }
    fun updated(enabled: Boolean, input: String) = rule.copy(
        enabled = enabled,
        threshold = input.toDoubleOrNull() ?: 0.0,
        currency = if (isFee) rule.currency else currency
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = EInk.Value, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    val cleaned = input.filter { it.isDigit() }.take(10)
                    text = cleaned
                    if (rule.enabled) onChange(updated(enabled = cleaned.isNotEmpty(), input = cleaned))
                },
                singleLine = true,
                placeholder = { Text(if (isFee) "e.g. 5" else "e.g. 100000", fontFamily = FontFamily.Monospace) },
                prefix = if (isFee) null else ({ Text(if (ruleCurrency == "THB") "฿" else "$", fontFamily = FontFamily.Monospace) }),
                suffix = if (isFee) ({ Text("sat/vB", fontFamily = FontFamily.Monospace) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = EInk.Value),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EInk.Value,
                    unfocusedBorderColor = EInk.BodyStroke,
                    focusedTextColor = EInk.Value,
                    unfocusedTextColor = EInk.Value,
                    cursorColor = EInk.Value,
                    focusedPrefixColor = EInk.Label,
                    unfocusedPrefixColor = EInk.Label,
                    focusedSuffixColor = EInk.Label,
                    unfocusedSuffixColor = EInk.Label,
                    focusedPlaceholderColor = EInk.Caption,
                    unfocusedPlaceholderColor = EInk.Caption
                ),
                modifier = Modifier
                    .padding(top = 4.dp, end = 12.dp)
                    .fillMaxWidth()
            )
        }
        EInkSwitch(
            checked = rule.enabled,
            // Needs a threshold before it can be switched on.
            enabled = rule.enabled || text.isNotEmpty(),
            onCheckedChange = { on -> onChange(updated(enabled = on, input = text)) }
        )
    }
}

