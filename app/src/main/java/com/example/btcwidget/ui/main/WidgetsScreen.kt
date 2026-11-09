package com.example.btcwidget.ui.main

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.BlockclockMetrics
import com.example.btcwidget.PriceData

/**
 * Everything about the home-screen widgets: a live preview, how to add one, and where
 * to choose what it shows (the switch under each dashboard metric).
 */
@Composable
fun WidgetsScreen(
    currencySetting: String,
    data: PriceData?,
    onWidgetCount: Int,
    availableCount: Int,
    onOpenDashboard: () -> Unit
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

        EInkPanel(title = "What it cycles through") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                    Text(
                        text = "$onWidgetCount of $availableCount metrics on",
                        color = EInk.Value,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Use the switch under each metric on the dashboard. Order follows the dashboard too.",
                        color = EInk.Caption,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                EInkChip(text = "DASHBOARD", selected = true, onClick = onOpenDashboard)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
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
