package com.example.btcwidget.ui.main

import com.example.btcwidget.ui.social.tr
import com.example.btcwidget.ui.social.SettingsButton
import com.example.btcwidget.ui.social.SettingsDivider
import com.example.btcwidget.ui.social.SettingsHeader
import com.example.btcwidget.ui.social.SettingsNote
import com.example.btcwidget.ui.social.SettingsRadioRow
import com.example.btcwidget.ui.social.SettingsTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.AppTheme
import com.example.btcwidget.DataSources
import com.example.btcwidget.PriceSource
import com.example.btcwidget.Stack
import com.example.btcwidget.StackUnit
import com.example.btcwidget.ThbSource

/**
 * Settings › Bitcoin and widgets: your stack, display currency, widget theme, refresh cadence and
 * price sources. Its state lives in [MainScreenViewModel] with the price data.
 */
@Composable
fun BitcoinSettingsRoute(viewModel: MainScreenViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val settings by viewModel.settings.collectAsState()
    val state by viewModel.uiState.collectAsState()
    val data = (state as? MainScreenUiState.Success)?.data
    val stackValue = data?.takeIf { settings.stackSats > 0 }?.let {
        val btc = settings.stackSats / Stack.SATS_PER_BTC.toDouble()
        com.example.btcwidget.alerts.PriceAlerts.formatPrice(btc * com.example.btcwidget.alerts.PriceAlerts.price(it, settings.currency), settings.currency)
    }
    BitcoinSettings(
        stackSats = settings.stackSats, stackUnit = settings.stackUnit, stackValue = stackValue, onStackChange = viewModel::setStack,
        currency = settings.currency, onCurrency = viewModel::setCurrency,
        theme = settings.theme, onTheme = viewModel::setTheme,
        refreshInterval = settings.refreshInterval, onRefreshInterval = viewModel::setRefreshInterval,
        dataSources = settings.dataSources, onDataSources = viewModel::setDataSources
    )
}

@Composable
fun BitcoinSettings(
    stackSats: Long, stackUnit: StackUnit, stackValue: String?, onStackChange: (Long, StackUnit) -> Unit,
    currency: String, onCurrency: (String) -> Unit,
    theme: AppTheme, onTheme: (AppTheme) -> Unit,
    refreshInterval: Int, onRefreshInterval: (Int) -> Unit,
    dataSources: DataSources, onDataSources: (DataSources) -> Unit,
) {
    SettingsHeader(tr("Your stack"))
    SettingsNote(
        if (stackSats > 0 && stackValue != null) tr("Worth {0} · stored only on this device", stackValue)
        else tr("How much bitcoin you hold. Stored only on this device.")
    )
    StackInput(stackSats, stackUnit, onStackChange)

    SettingsDivider()
    SettingsHeader(tr("Currency"))
    SettingsNote(tr("Prices, sats and alerts"))
    listOf("USD" to "US dollar (USD)", "THB" to "Thai baht (THB)").forEach { (code, name) ->
        SettingsRadioRow(tr(name), selected = currency == code) { onCurrency(code) }
    }

    SettingsDivider()
    SettingsHeader(tr("Refresh every"))
    SettingsNote(tr("Widgets, price alerts and phone notifications check in the background this often."))
    listOf(15, 30, 60).forEach { m -> SettingsRadioRow(tr("{0} minutes", m), selected = refreshInterval == m) { onRefreshInterval(m) } }

    SettingsDivider()
    SettingsHeader(tr("Widget theme"))
    SettingsNote(tr("Home-screen widgets and their previews; the app keeps its own look"))
    AppTheme.entries.forEach { t -> SettingsRadioRow(tr(t.label), selected = theme == t) { onTheme(t) } }

    SettingsDivider()
    SettingsHeader(tr("BTC price"))
    SettingsNote(tr("Spot price in USD"))
    PriceSource.entries.forEach { src -> SettingsRadioRow(src.label, selected = dataSources.price == src) { onDataSources(dataSources.copy(price = src)) } }
    SettingsHeader(tr("THB price"))
    SettingsRadioRow(tr("USD price × exchange rate"), selected = dataSources.thb == ThbSource.CONVERTED) { onDataSources(dataSources.copy(thb = ThbSource.CONVERTED)) }
    SettingsRadioRow(tr("Bitkub's BTC/THB market"), selected = dataSources.thb == ThbSource.BITKUB) { onDataSources(dataSources.copy(thb = ThbSource.BITKUB)) }
    SettingsNote(tr("If a source is unreachable, Block falls back to Coinbase or the last known price."))
}

/** Amount field with a BTC / sats switch; saves every valid edit. */
@Composable
private fun StackInput(stackSats: Long, stackUnit: StackUnit, onStackChange: (Long, StackUnit) -> Unit) {
    var unit by remember { mutableStateOf(stackUnit) }
    var text by remember { mutableStateOf(Stack.toInput(stackSats, stackUnit)) }
    val invalid = text.isNotBlank() && Stack.parse(text, unit) == null
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsTextField(
                value = text,
                onValueChange = { input ->
                    text = input.filter { it.isDigit() || it == '.' || it == ',' }.take(20)
                    if (text.isBlank()) onStackChange(0, unit)
                    else Stack.parse(text, unit)?.let { onStackChange(it, unit) }
                },
                placeholder = if (unit == StackUnit.BTC) "0.05" else "5000000",
                isError = invalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            StackUnit.entries.forEach { option ->
                Spacer(Modifier.width(8.dp))
                SettingsButton(if (option == StackUnit.BTC) "BTC" else "sats", primary = unit == option) {
                    if (unit != option) {
                        // Keep the amount, show it in the new unit.
                        val sats = Stack.parse(text, unit) ?: stackSats
                        unit = option
                        text = Stack.toInput(sats, option)
                        onStackChange(sats, option)
                    }
                }
            }
        }
        if (invalid) Text(
            text = if (unit == StackUnit.BTC) tr("Up to 8 decimals, max 21,000,000") else tr("Whole sats only"),
            color = EInk.Alert, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)
        )
    }
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

/** Published copy of docs/privacy-policy.md (also the URL for the Play listing). */
const val PRIVACY_POLICY_URL = "https://github.com/phirapath21/btcwidget/blob/main/docs/privacy-policy.md"
