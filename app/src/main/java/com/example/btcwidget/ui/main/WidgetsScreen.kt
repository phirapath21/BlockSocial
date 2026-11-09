package com.example.btcwidget.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.BlockclockMetrics
import com.example.btcwidget.MetricCategory
import com.example.btcwidget.PriceData

/**
 * Everything about the home-screen widgets: how to add them, a live preview, and which
 * modes a tap cycles through (grouped like the dashboard, in the same order).
 */
@Composable
fun WidgetsScreen(
    modeStates: List<Boolean>,
    onModeToggle: (Int, Boolean) -> Unit,
    currencySetting: String,
    modeOrder: List<String>,
    data: PriceData?,
    hasStack: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        HeaderSection(title = "WIDGETS", subtitle = "On your home screen")

        if (data != null) {
            EInkDevice(
                display = BlockclockMetrics.forMode(BlockclockMetrics.MODE_PRICE, data, currencySetting),
                aspectRatio = 2f,
                stale = !data.lastFetchSuccessful
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        EInkPanel(title = "Add a widget") {
            Step(1, "Long-press an empty spot on your home screen.")
            Step(2, "Open Widgets and find Blockface.")
            Step(3, "Drag Bitcoin metrics, Halving countdown or Bitcoin quotes onto the screen, then pick what it shows.")
            Step(4, "Tap a widget to cycle modes. On Android 12+, long-press it to choose again.")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Modes the Bitcoin metrics widget cycles through. The order follows the dashboard.",
            color = EInk.Caption,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
        )

        val ordered = BlockclockMetrics.orderedModes(modeOrder)
        MetricCategory.entries.forEach { category ->
            val modes = ordered.filter { BlockclockMetrics.categoryOf(it) == category }
            if (modes.isEmpty()) return@forEach
            EInkPanel(title = category.title) {
                modes.forEach { index ->
                    val available = BlockclockMetrics.isAvailable(index, currencySetting, hasStack)
                    val checked = available && modeStates.getOrElse(index) { true }
                    SettingRow(
                        label = BlockclockMetrics.MODE_NAMES[index],
                        note = when {
                            available -> null
                            index == BlockclockMetrics.MODE_STACK -> "Set your stack in Settings first"
                            else -> "USD only · off while THB is selected"
                        },
                        dimmed = !available,
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
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "$number",
            color = EInk.Value,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(20.dp)
        )
        Text(
            text = text,
            color = EInk.Label,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
