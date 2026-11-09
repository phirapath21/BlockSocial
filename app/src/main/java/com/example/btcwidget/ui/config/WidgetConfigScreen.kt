package com.example.btcwidget.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.BlockclockDisplay
import com.example.btcwidget.ui.main.EInk
import com.example.btcwidget.ui.main.EInkDevice
import com.example.btcwidget.ui.main.HeaderSection

/** One option in the widget picker; [preview] is null when no data is cached yet. */
data class ModeChoice(val mode: Int, val name: String, val preview: BlockclockDisplay?)

@Composable
fun WidgetConfigScreen(
    choices: List<ModeChoice>,
    selectedMode: Int,
    halvingProgress: Float?,
    isHalving: Boolean,
    onPick: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EInk.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        HeaderSection(title = "CHOOSE A DISPLAY", subtitle = "Tap the widget later to cycle modes")

        choices.forEach { choice ->
            val selected = choice.mode == selectedMode
            val shape = RoundedCornerShape(18.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .border(2.dp, if (selected) EInk.Value else Color.Transparent, shape)
                    .clickable { onPick(choice.mode) }
                    .padding(4.dp)
            ) {
                if (choice.preview != null) {
                    EInkDevice(
                        display = choice.preview,
                        aspectRatio = 2.6f,
                        progress = if (isHalving || choice.preview.labelTop == "HALV") halvingProgress else null
                    )
                } else {
                    // No data yet: plain rows, the widget fills in after the first fetch.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(EInk.Body, RoundedCornerShape(14.dp))
                            .padding(horizontal = 16.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(choice.name, color = EInk.Value, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}
