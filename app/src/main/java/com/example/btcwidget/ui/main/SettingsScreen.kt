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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.DataSources
import com.example.btcwidget.PriceSource
import com.example.btcwidget.Stack
import com.example.btcwidget.StackUnit
import com.example.btcwidget.ThbSource

/** General app settings: display currency, refresh cadence, haptics, and about. */
@Composable
fun SettingsScreen(
    currencySetting: String,
    onCurrencySelected: (String) -> Unit,
    refreshInterval: Int,
    onRefreshIntervalSelected: (Int) -> Unit,
    hapticEnabled: Boolean,
    onHapticToggle: (Boolean) -> Unit,
    appVersion: String? = null,
    stackSats: Long = 0,
    stackUnit: StackUnit = StackUnit.BTC,
    onStackChange: (Long, StackUnit) -> Unit = { _, _ -> },
    stackValue: String? = null,
    dataSources: DataSources = DataSources(PriceSource.COINBASE, ThbSource.CONVERTED),
    onDataSourcesChange: (DataSources) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        HeaderSection(title = "SETTINGS", subtitle = "App preferences")

        EInkPanel(title = "Your stack") {
            StackInput(stackSats = stackSats, stackUnit = stackUnit, onStackChange = onStackChange)
            Text(
                text = if (stackSats > 0 && stackValue != null) {
                    "Worth $stackValue · stored only on this device"
                } else {
                    "How much bitcoin you hold. Stored only on this device."
                },
                color = EInk.Caption,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

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

        EInkPanel(title = "Data sources") {
            SettingRow("BTC price", note = "Spot price in USD") {
                PriceSource.entries.forEach { source ->
                    EInkChip(
                        text = source.label.uppercase(),
                        selected = dataSources.price == source,
                        onClick = { onDataSourcesChange(dataSources.copy(price = source)) }
                    )
                }
            }
            SettingRow(
                "THB price",
                note = if (dataSources.thb == ThbSource.BITKUB) "Bitkub's BTC/THB market" else "USD price × exchange rate"
            ) {
                ThbSource.entries.forEach { source ->
                    EInkChip(
                        text = source.label.uppercase(),
                        selected = dataSources.thb == source,
                        onClick = { onDataSourcesChange(dataSources.copy(thb = source)) }
                    )
                }
            }
            Text(
                text = "If a source is unreachable, Blockface falls back to Coinbase or the last known price.",
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
            AboutLine("Prices: ${dataSources.price.label}" + (if (dataSources.thb == ThbSource.BITKUB) ", Bitkub (THB)" else "") + " · Network: mempool.space")
            AboutLine("Supply: blockchain.info · MSTR: Yahoo Finance, CoinGecko")
            AboutLine("Display type: Oswald (SIL Open Font License)")
            val uriHandler = LocalUriHandler.current
            Text(
                text = "Privacy policy ›",
                color = EInk.Value,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clickable(onClickLabel = "Open privacy policy") { uriHandler.openUri(PRIVACY_POLICY_URL) }
                    .padding(vertical = 6.dp)
            )
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

/** Published copy of docs/privacy-policy.md (also the URL for the Play listing). */
const val PRIVACY_POLICY_URL = "https://github.com/phirapath21/btcwidget/blob/main/docs/privacy-policy.md"

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

/** Amount field with a BTC / SATS unit switch; saves every valid edit. */
@Composable
private fun StackInput(stackSats: Long, stackUnit: StackUnit, onStackChange: (Long, StackUnit) -> Unit) {
    var unit by remember { mutableStateOf(stackUnit) }
    var text by remember { mutableStateOf(Stack.toInput(stackSats, stackUnit)) }
    val invalid = text.isNotBlank() && Stack.parse(text, unit) == null

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                text = input.filter { it.isDigit() || it == '.' || it == ',' }.take(20)
                if (text.isBlank()) onStackChange(0, unit)
                else Stack.parse(text, unit)?.let { onStackChange(it, unit) }
            },
            singleLine = true,
            isError = invalid,
            placeholder = { Text(if (unit == StackUnit.BTC) "0.05" else "5000000", fontFamily = FontFamily.Monospace) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = EInk.Value),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = EInk.Value,
                unfocusedBorderColor = EInk.BodyStroke,
                errorBorderColor = EInk.Alert,
                focusedTextColor = EInk.Value,
                unfocusedTextColor = EInk.Value,
                errorTextColor = EInk.Value,
                cursorColor = EInk.Value,
                focusedPlaceholderColor = EInk.Caption,
                unfocusedPlaceholderColor = EInk.Caption
            ),
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp)
        )
        StackUnit.entries.forEach { option ->
            EInkChip(
                text = option.name,
                selected = unit == option,
                onClick = {
                    if (unit != option) {
                        // Keep the amount, show it in the new unit.
                        val sats = Stack.parse(text, unit) ?: stackSats
                        unit = option
                        text = Stack.toInput(sats, option)
                        onStackChange(sats, option)
                    }
                },
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
    if (invalid) {
        Text(
            text = if (unit == StackUnit.BTC) "Up to 8 decimals, max 21,000,000" else "Whole sats only",
            color = EInk.Alert,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

