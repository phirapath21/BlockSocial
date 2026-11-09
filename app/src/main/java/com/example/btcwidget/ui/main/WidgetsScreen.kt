package com.example.btcwidget.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.BlockclockMetrics
import com.example.btcwidget.MetricCategory
import com.example.btcwidget.PriceData

/**
 * Everything about the home-screen widgets: a live preview, how to add one, and the
 * metrics the Bitcoin metrics widget cycles through, as panels with an on/off switch,
 * grouped and ordered like the dashboard.
 */
@Composable
fun WidgetsScreen(
    currencySetting: String,
    data: PriceData?,
    modeStates: List<Boolean>,
    onModeToggle: (Int, Boolean) -> Unit,
    modeOrder: List<String>,
    stackSats: Long,
    onWidgetCount: Int,
    availableCount: Int
) {
    val hasStack = stackSats > 0
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        HeaderSection(title = "WIDGETS", subtitle = "On your home screen")

        EInkPanel(title = "Add a widget") {
            Step(1, "Long-press an empty spot on your home screen.")
            Step(2, "Open Widgets and find Blockface.")
            Step(3, "Drag Bitcoin metrics, Halving countdown or Bitcoin quotes onto the screen, then pick what it shows.")
            Step(4, "Tap a widget to cycle modes. On Android 12+, long-press it to choose again.")
        }

        Spacer(modifier = Modifier.height(16.dp))

        SectionLabel("On the widget", modifier = Modifier.padding(start = 4.dp))
        Text(
            text = "$onWidgetCount of $availableCount metrics · tap the widget to cycle. Order follows the dashboard.",
            color = EInk.Caption,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        )

        if (data == null) {
            Text(
                text = "Metrics appear after the first refresh.",
                color = EInk.Label,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 4.dp, top = 12.dp)
            )
        } else {
            val ordered = BlockclockMetrics.orderedModes(modeOrder)
                .filter { BlockclockMetrics.isAvailable(it, currencySetting, hasStack) }
            MetricCategory.entries.forEach { category ->
                val modes = ordered.filter { BlockclockMetrics.categoryOf(it) == category }
                if (modes.isEmpty()) return@forEach
                SectionLabel(category.title, modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp))
                modes.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { index ->
                            val on = modeStates.getOrElse(index) { true }
                            Column(modifier = Modifier.weight(1f)) {
                                EInkDevice(
                                    display = BlockclockMetrics.forMode(index, data, currencySetting, stackSats),
                                    aspectRatio = 1.7f,
                                    progress = if (BlockclockMetrics.MODE_KEYS[index] == "HALVING") {
                                        BlockclockMetrics.halvingProgress(data.blockHeight).toFloat()
                                    } else {
                                        null
                                    },
                                    modifier = Modifier.alpha(if (on) 1f else OFF_ALPHA)
                                )
                                WidgetToggle(on = on, onToggle = { onModeToggle(index, it) })
                            }
                        }
                        if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

/** Opacity of a metric switched off for the widget. */
private const val OFF_ALPHA = 0.38f

/**
 * "On widget" switch under a metric panel: whether tapping the home-screen widget
 * cycles through this metric. The whole row toggles, with Switch semantics.
 */
@Composable
private fun WidgetToggle(on: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
            .clip(EInk.ChipShape)
            .toggleable(value = on, role = Role.Switch, onValueChange = onToggle)
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (on) "ON WIDGET" else "OFF WIDGET",
            color = if (on) EInk.Label else EInk.Caption,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp,
            modifier = Modifier.weight(1f)
        )
        EInkSwitch(checked = on, onCheckedChange = null, modifier = Modifier.scale(0.7f))
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
