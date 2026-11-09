package com.example.btcwidget.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.example.btcwidget.PriceData
import com.example.btcwidget.alerts.AlertKind
import com.example.btcwidget.alerts.AlertRule
import com.example.btcwidget.alerts.PriceAlerts
import java.util.Locale

/** Price and fee alerts, with the current values shown for reference. */
@Composable
fun AlertsScreen(
    alerts: List<AlertRule>,
    onAlertChange: (AlertRule) -> Unit,
    currencySetting: String,
    refreshInterval: Int,
    notificationsAllowed: Boolean,
    data: PriceData?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        HeaderSection(title = "ALERTS", subtitle = "Get notified on a crossing")

        if (data != null) {
            EInkPanel(title = "Now") {
                NowLine("BTC", PriceAlerts.formatPrice(PriceAlerts.price(data, currencySetting), currencySetting))
                NowLine("Fastest fee", "${data.feeFastest} sat/vB")
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        EInkPanel(title = "Price") {
            alerts.filter { it.kind != AlertKind.FEE_AT_OR_BELOW }.forEach { rule ->
                AlertRow(rule = rule, currency = currencySetting, onChange = onAlertChange)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        EInkPanel(title = "Fees") {
            alerts.filter { it.kind == AlertKind.FEE_AT_OR_BELOW }.forEach { rule ->
                AlertRow(rule = rule, currency = currencySetting, onChange = onAlertChange)
            }
        }

        val blocked = alerts.any { it.enabled } && !notificationsAllowed
        Text(
            text = if (blocked) {
                "Notifications are off for Blockface. Allow them in system settings to get alerts."
            } else {
                "Checked with each background refresh (every $refreshInterval min). " +
                    "One notification each time a line is crossed."
            },
            color = if (blocked) EInk.Alert else EInk.Caption,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 20.dp)
        )
    }
}

@Composable
private fun NowLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = EInk.Label, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
        Text(value, color = EInk.Value, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
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
