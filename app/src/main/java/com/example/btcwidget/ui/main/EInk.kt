package com.example.btcwidget.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.BlockclockDisplay
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
 * A blockclock device: black body, a stacked label screen, one e-ink screen per
 * character of the value, an optional unit screen, and a caption underneath.
 *
 * @param tileAspect width / height of each screen; lower is taller.
 * @param progress optional 0..1 bar shown under the screens.
 */
@Composable
fun EInkDevice(
    display: BlockclockDisplay,
    modifier: Modifier = Modifier,
    tileAspect: Float = 0.72f,
    stale: Boolean = false,
    progress: Float? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(EInk.Body, EInk.BodyShape)
            .border(1.dp, EInk.BodyStroke, EInk.BodyShape)
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            val tileModifier = Modifier
                .weight(1f)
                .aspectRatio(tileAspect)
            LabelTile(display.labelTop, display.labelBottom, tileModifier)
            display.valueTiles().forEach { CharTile(it, tileModifier) }
            if (display.suffix.isNotEmpty()) {
                LabelTile(display.suffix, "", tileModifier)
            }
        }

        if (progress != null) {
            Box(
                modifier = Modifier
                    .padding(top = 7.dp)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(EInk.Track, RoundedCornerShape(2.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(EInk.Paper, RoundedCornerShape(2.dp))
                )
            }
        }

        if (display.caption.isNotEmpty() || stale) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (stale) {
                    Text(
                        text = "STALE",
                        color = EInk.Alert,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = display.caption.uppercase(Locale.US),
                    color = EInk.Caption,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** One e-ink screen showing a single character (a digit may carry its decimal point). */
@Composable
private fun CharTile(char: String, modifier: Modifier) {
    BoxWithConstraints(
        modifier = modifier.background(EInk.PaperBrush, EInk.TileShape),
        contentAlignment = Alignment.Center
    ) {
        val isPair = char.length > 1
        val byHeight = maxHeight * (if (isPair) 0.5f else 0.62f)
        val byWidth = maxWidth * (if (isPair) 0.75f else 1.1f)
        Text(
            text = char,
            color = EInk.Ink,
            fontSize = minOf(byHeight, byWidth).asFontSize(),
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** E-ink screen with a two-line stacked label such as BTC over USD. */
@Composable
private fun LabelTile(top: String, bottom: String, modifier: Modifier) {
    BoxWithConstraints(
        modifier = modifier.background(EInk.PaperBrush, EInk.TileShape),
        contentAlignment = Alignment.Center
    ) {
        // Monospace glyphs are ~0.6em wide; keep the longest word inside the screen.
        val longest = maxOf(top.length, bottom.length, 3)
        val fontSize = minOf(maxWidth * (1.4f / longest), maxHeight * 0.2f).asFontSize()
        val dividerWidth = maxWidth * 0.7f
        val dividerGap = maxHeight * 0.04f

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText(top, fontSize)
            if (bottom.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .padding(vertical = dividerGap)
                        .width(dividerWidth)
                        .height(1.dp)
                        .background(EInk.Ink)
                )
                LabelText(bottom, fontSize)
            }
        }
    }
}

@Composable
private fun LabelText(text: String, fontSize: TextUnit) {
    Text(
        text = text,
        color = EInk.Ink,
        fontSize = fontSize,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false
    )
}

/** A dp size as a font size that ignores the system font scale, so glyphs fit their screen. */
@Composable
private fun Dp.asFontSize(): TextUnit {
    val size = this
    return with(LocalDensity.current) { size.toSp() }
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
