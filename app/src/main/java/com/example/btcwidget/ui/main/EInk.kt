package com.example.btcwidget.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.AndroidDrawSurface
import com.example.btcwidget.BlockclockDisplay
import com.example.btcwidget.BlockclockPainter
import com.example.btcwidget.BlockclockScene
import java.util.Locale

/** E-ink blockclock palette; mirrors res/values/colors.xml used by the widgets. */
object EInk {
    val Background = Color(0xFF0B0B0B)
    val Body = Color(0xFF050505)
    val BodyStroke = Color(0xFF262626)
    val Paper = Color(0xFFE7E4DB)
    val PaperShade = Color(0xFFD6D2C6)
    val Ink = Color(0xFF141414)
    val InkSoft = Color(0xFF5C5A54)
    val Caption = Color(0xFF8A877F)
    val Track = Color(0xFF2A2A2A)
    val Alert = Color(0xFFD9534F)

    val BodyShape = RoundedCornerShape(16.dp)
    val TileShape = RoundedCornerShape(3.dp)
    val ChipShape = RoundedCornerShape(4.dp)
    val PaperBrush = Brush.verticalGradient(listOf(Paper, PaperShade))
}

/**
 * A blockclock device drawn by [BlockclockPainter], the same code that paints the
 * home-screen widgets, so the app and widgets always match.
 */
@Composable
fun EInkDevice(
    display: BlockclockDisplay,
    modifier: Modifier = Modifier,
    stale: Boolean = false,
    progress: Float? = null
) {
    val scene = BlockclockScene(display = display, stale = stale, progress = progress)
    val description = listOf(display.labelTop, display.labelBottom, display.value, display.suffix)
        .filter { it.isNotEmpty() }
        .joinToString(" ") + ". " + display.caption
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(BlockclockPainter.aspectRatio(scene))
            .semantics { contentDescription = description }
    ) {
        drawIntoCanvas { canvas ->
            BlockclockPainter.paint(AndroidDrawSurface(canvas.nativeCanvas), size.width, size.height, scene)
        }
    }
}

/** Black device body used as a section container. */
@Composable
fun EInkPanel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(EInk.Body, EInk.BodyShape)
            .border(1.dp, EInk.BodyStroke, EInk.BodyShape)
            .padding(16.dp)
    ) {
        SectionLabel(title)
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(Locale.US),
        color = EInk.Caption,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        modifier = modifier
    )
}

/** Selectable chip: a lit e-ink screen when selected, a dark outline otherwise. */
@Composable
fun EInkChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val surface = if (selected) {
        Modifier.background(EInk.PaperBrush, EInk.ChipShape)
    } else {
        Modifier.border(1.dp, EInk.BodyStroke, EInk.ChipShape)
    }
    Box(
        modifier = modifier
            .then(surface)
            .clip(EInk.ChipShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) EInk.Ink else EInk.Caption,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun EInkSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = EInk.Ink,
            checkedTrackColor = EInk.Paper,
            checkedBorderColor = EInk.Paper,
            uncheckedThumbColor = EInk.Caption,
            uncheckedTrackColor = EInk.Body,
            uncheckedBorderColor = EInk.BodyStroke
        )
    )
}
