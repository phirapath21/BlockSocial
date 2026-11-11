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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.AndroidDrawSurface
import com.example.btcwidget.AppTheme
import com.example.btcwidget.BlockclockDisplay
import com.example.btcwidget.BlockclockPainter
import com.example.btcwidget.BlockclockScene
import java.util.Locale

/**
 * App palette. The app itself (Nostr screens, menus, settings) always uses the Classic
 * colours; the chosen [theme] only changes how widgets are drawn: the home-screen widgets,
 * their previews in the app ([EInkDevice]) and share cards. [theme] is snapshot state, so
 * those previews update when it changes.
 */
object EInk {
    /** The widget theme. Not used for the app's own colours. */
    var theme: AppTheme by mutableStateOf(AppTheme.CLASSIC)

    private val ui get() = AppTheme.CLASSIC.palette.ui
    val Background get() = Color(ui.background)
    val Body get() = Color(ui.body)
    val BodyStroke get() = Color(ui.bodyStroke)
    val Value get() = Color(ui.value)
    val Label get() = Color(ui.label)
    val Caption get() = Color(ui.caption)
    val Track get() = Color(ui.track)
    val Alert get() = Color(ui.alert)

    /** Face for screen titles (the app's own look, whatever the widget theme). */
    val TitleFont: FontFamily get() = FontFamily.Monospace

    /** Block's one accent (Bitcoin orange): marks what you did, never decoration. */
    val Accent get() = Color(0xFFF7931A)

    /** Blockclock-style panels: nearly square, thin border. */
    val PanelShape = RoundedCornerShape(6.dp)

    /** Labels, names and numbers read like the blockclock: monospace capitals. */
    val Mono: FontFamily get() = FontFamily.Monospace

    val BodyShape = RoundedCornerShape(16.dp)
    val ChipShape = RoundedCornerShape(50)
}

/**
 * A blockclock panel drawn by [BlockclockPainter], the same code that paints the
 * home-screen widgets, so the app and widgets always match.
 *
 * @param aspectRatio width / height of the panel.
 */
@Composable
fun EInkDevice(
    display: BlockclockDisplay,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 2f,
    stale: Boolean = false,
    progress: Float? = null
) {
    val context = LocalContext.current
    val condensed = remember { AndroidDrawSurface.condensedTypeface(context) }
    val pixel = remember { AndroidDrawSurface.pixelTypeface(context) }
    val scene = BlockclockScene(display = display, stale = stale, progress = progress, palette = EInk.theme.palette)
    val description = listOf(display.labelTop, display.labelBottom, display.value, display.suffix)
        .filter { it.isNotEmpty() }
        .joinToString(" ") + ". " + display.caption
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .semantics { contentDescription = description }
    ) {
        drawIntoCanvas { canvas ->
            BlockclockPainter.paint(AndroidDrawSurface(canvas.nativeCanvas, condensed, pixel), size.width, size.height, scene)
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

/** Selectable chip: white outline and type when selected, dim otherwise. */
@Composable
fun EInkChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .border(1.dp, if (selected) EInk.Value else EInk.BodyStroke, EInk.ChipShape)
            .clip(EInk.ChipShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) EInk.Value else EInk.Caption,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun EInkSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = EInk.Body,
            checkedTrackColor = EInk.Value,
            checkedBorderColor = EInk.Value,
            uncheckedThumbColor = EInk.Caption,
            uncheckedTrackColor = EInk.Body,
            uncheckedBorderColor = EInk.BodyStroke,
            disabledUncheckedThumbColor = EInk.BodyStroke,
            disabledUncheckedTrackColor = EInk.Body,
            disabledUncheckedBorderColor = EInk.BodyStroke
        )
    )
}
