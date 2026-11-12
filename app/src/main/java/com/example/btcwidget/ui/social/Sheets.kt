package com.example.btcwidget.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.btcwidget.ui.main.EInk
import kotlinx.coroutines.launch

/** The raised surface of sheets and dialogs: a step above the black background. */
internal val SheetColor = Color(0xFF16181C)
private val PillShape = RoundedCornerShape(50)
private val AccentOrange = Color(0xFFF7931A)

/** One row of an [ActionSheet]. [destructive] ones (delete, report) are red. */
class SheetAction(
    val icon: ImageVector,
    val label: String,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

/**
 * A menu that slides up from the bottom, like X's: a handle, then one large row per action
 * with a line icon. Tapping a row slides the sheet away, then runs the action (so a dialog
 * or screen it opens isn't covered by the closing sheet).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(onDismiss: () -> Unit, actions: List<SheetAction>, header: (@Composable ColumnScope.() -> Unit)? = null) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = SheetColor,
        contentColor = EInk.Value,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 8.dp).size(width = 40.dp, height = 4.dp).clip(PillShape).background(EInk.Track))
        }
    ) {
        // Scrolls when the screen is short (a phone on its side) and the menu is long.
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
            header?.invoke(this)
            actions.forEach { a ->
                val color = if (a.destructive) EInk.Alert else EInk.Value
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = a.enabled, onClickLabel = a.label) {
                            scope.launch { state.hide() }.invokeOnCompletion { onDismiss(); a.onClick() }
                        }
                        .alpha(if (a.enabled) 1f else 0.4f)
                        .padding(horizontal = 22.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(a.icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(18.dp))
                    Text(a.label, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** A bottom sheet with its own content (a picker, a search), styled like [ActionSheet]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetColor,
        contentColor = EInk.Value,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 8.dp).size(width = 40.dp, height = 4.dp).clip(PillShape).background(EInk.Track))
        },
        content = content
    )
}

/**
 * A dialog in Block's style, like X's: a title, optional text and content, and full-width pill
 * buttons, the main one on top (white, or red when [destructive]) and Cancel outlined below.
 * Without [confirmLabel], only the outlined [dismissLabel] button shows.
 */
@Composable
fun BlockDialog(
    onDismiss: () -> Unit,
    title: String,
    text: String? = null,
    confirmLabel: String? = null,
    onConfirm: () -> Unit = {},
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    dismissLabel: String? = "Cancel",
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(SheetColor)
                .padding(24.dp)
        ) {
            Text(title, color = EInk.Value, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp)
            // Long content (a list of choices, a QR code) scrolls; the buttons stay put.
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                text?.let { Text(it, color = EInk.Caption, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp)) }
                if (content != null) Column(Modifier.padding(top = 14.dp)) { content() }
            }
            // Stacked, the main one on top; side by side on a short screen so the content keeps room.
            if (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 520) {
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Cancel first, as on Android's own dialogs, the main action at the right.
                    if (dismissLabel != null) Box(Modifier.weight(1f)) { PillButtonFull(dismissLabel, outlined = true, onClick = onDismiss) }
                    if (confirmLabel != null) Box(Modifier.weight(1f)) {
                        PillButtonFull(
                            confirmLabel, enabled = confirmEnabled,
                            background = if (destructive) EInk.Alert else EInk.Value,
                            foreground = if (destructive) Color.White else Color.Black,
                            onClick = onConfirm
                        )
                    }
                }
            } else Column(Modifier.padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (confirmLabel != null) PillButtonFull(
                    confirmLabel, enabled = confirmEnabled,
                    background = if (destructive) EInk.Alert else EInk.Value,
                    foreground = if (destructive) Color.White else Color.Black,
                    onClick = onConfirm
                )
                if (dismissLabel != null) PillButtonFull(dismissLabel, outlined = true, onClick = onDismiss)
            }
        }
    }
}

/**
 * A small pop-up menu for two or three quick choices (the browser's ⋮, video quality, reactions):
 * Block's raised surface, rounded, with a hairline border instead of Material's tinted shadow.
 */
@Composable
fun BlockDropdown(expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.material3.DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = SheetColor,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, EInk.BodyStroke),
        content = content
    )
}

/** One line of a [BlockDropdown]: optional icon, then the label. */
@Composable
fun BlockDropdownItem(label: String, icon: ImageVector? = null, enabled: Boolean = true, selected: Boolean = false, onClick: () -> Unit) {
    androidx.compose.material3.DropdownMenuItem(
        text = {
            Text(label, color = EInk.Value, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null, tint = EInk.Value, modifier = Modifier.size(20.dp)) } },
        trailingIcon = if (selected) ({ Text("✓", color = AccentOrange, fontWeight = FontWeight.Bold) }) else null,
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.widthIn(min = 180.dp)
    )
}

/** Yes-or-no question for something that can't be undone. */
@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit, destructive: Boolean = true) =
    BlockDialog(onDismiss = onDismiss, title = title, text = text, confirmLabel = confirmLabel, onConfirm = onConfirm, destructive = destructive)

/** A choice inside a dialog or sheet: one tappable line, ✓ when selected. */
@Composable
fun ChoiceRow(label: String, selected: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = EInk.Value, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (selected) Text("✓", color = AccentOrange, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PillButtonFull(
    label: String,
    enabled: Boolean = true,
    outlined: Boolean = false,
    background: Color = EInk.Value,
    foreground: Color = Color.Black,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(PillShape)
            .then(if (outlined) Modifier.border(1.dp, EInk.Track, PillShape) else Modifier.background(background))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (outlined) EInk.Value else foreground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
