package com.example.btcwidget.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.ui.main.EInk

// Settings rows, as X lays them out: full-width rows you tap anywhere on, a title with a
// one-line explanation under it, and the control (chevron, switch, radio) at the right.

internal val SettingsAccent = Color(0xFFF7931A)
private val Destructive = Color(0xFFF4212E)

/** A heading over a group of rows. */
@Composable
fun SettingsHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text, color = EInk.Value, fontSize = 18.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp)
    )
}

/** Small print explaining a group or a choice. */
@Composable
fun SettingsNote(text: String, modifier: Modifier = Modifier) {
    Text(text, color = EInk.Caption, fontSize = 13.sp, lineHeight = 18.sp, modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
}

@Composable
fun SettingsDivider() = HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke, modifier = Modifier.padding(top = 8.dp))

@Composable
private fun RowText(title: String, summary: String?, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(title, color = color, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!summary.isNullOrBlank()) Text(summary, color = EInk.Caption, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

/**
 * A row that opens something: a page, a dialog or a link. [value] shows the current setting
 * on the right (as "English" next to Language); [icon] is for the top-level categories.
 */
@Composable
fun SettingsLinkRow(
    title: String,
    summary: String? = null,
    icon: ImageVector? = null,
    value: String? = null,
    destructive: Boolean = false,
    chevron: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClickLabel = title, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            leading != null -> { Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { leading() }; Spacer(Modifier.width(16.dp)) }
            icon != null -> { Icon(icon, contentDescription = null, tint = EInk.Value, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(20.dp)) }
        }
        RowText(title, summary, if (destructive) Destructive else EInk.Value, Modifier.weight(1f))
        if (value != null) Text(value, color = EInk.Caption, fontSize = 14.sp, maxLines = 1, modifier = Modifier.padding(start = 12.dp))
        if (chevron && !destructive) Icon(Glyphs.Chevron, contentDescription = null, tint = EInk.Caption, modifier = Modifier.padding(start = 4.dp).size(22.dp))
    }
}

/** An on/off setting: the whole row toggles it. */
@Composable
fun SettingsSwitchRow(title: String, summary: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowText(title, summary, if (enabled) EInk.Value else EInk.Caption, Modifier.weight(1f).padding(end = 12.dp))
        Switch(
            checked = checked, onCheckedChange = null, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = SettingsAccent, checkedBorderColor = SettingsAccent,
                uncheckedThumbColor = EInk.Caption, uncheckedTrackColor = EInk.Background, uncheckedBorderColor = EInk.BodyStroke
            )
        )
    }
}

/** One choice among several: the whole row picks it. */
@Composable
fun SettingsRadioRow(title: String, summary: String? = null, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowText(title, summary, EInk.Value, Modifier.weight(1f).padding(end = 12.dp))
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = SettingsAccent, unselectedColor = EInk.Caption))
    }
}

/** A text field styled for settings pages. */
@Composable
fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    isError: Boolean = false,
    textStyle: TextStyle = TextStyle(fontSize = 15.sp, color = EInk.Value),
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, singleLine = singleLine, isError = isError,
        placeholder = { Text(placeholder, color = EInk.Caption) },
        textStyle = textStyle, keyboardOptions = keyboardOptions,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = SettingsAccent, unfocusedBorderColor = EInk.BodyStroke, errorBorderColor = Destructive,
            cursorColor = SettingsAccent, focusedTextColor = EInk.Value, unfocusedTextColor = EInk.Value
        ),
        modifier = modifier
    )
}

/** A small rounded button for actions inside a row or under a field. */
@Composable
fun SettingsButton(label: String, primary: Boolean = false, enabled: Boolean = true, destructive: Boolean = false, onClick: () -> Unit) {
    val bg = when { !enabled -> EInk.Body; primary -> SettingsAccent; else -> Color.Transparent }
    val fg = when { !enabled -> EInk.Caption; primary -> Color.Black; destructive -> Destructive; else -> EInk.Value }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
    Box(
        Modifier.heightIn(min = 36.dp)
            .clip(shape)
            .background(bg)
            .then(if (!primary) Modifier.border(1.dp, if (destructive) Destructive else EInk.BodyStroke, shape) else Modifier)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}

/**
 * Addresses one per line (relays, media servers), shown as a list until Edit, then a text
 * field with Save. [valid] marks lines that can't be right before saving.
 */
@Composable
fun SettingsListEditor(
    initial: List<String>,
    placeholder: String,
    valid: (String) -> Boolean,
    emptyText: String = tr("Not set"),
    extraAction: (@Composable () -> Unit)? = null,
    onSave: (List<String>, () -> Unit) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var text by remember(initial) { mutableStateOf(initial.joinToString("\n")) }
    var saving by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        if (!editing) {
            if (initial.isEmpty()) Text(emptyText, color = EInk.Caption, fontSize = 14.sp)
            initial.forEach { Text(it, color = EInk.Value, fontSize = 14.sp, modifier = Modifier.padding(vertical = 3.dp)) }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsButton(tr("Edit")) { editing = true }
                extraAction?.invoke()
            }
            return@Column
        }
        val lines = text.split('\n', ' ', ',').map { it.trim().trimEnd('/') }.filter { it.isNotEmpty() }
        val bad = lines.filterNot(valid)
        SettingsTextField(text, { text = it }, placeholder, Modifier.fillMaxWidth(), singleLine = false, isError = bad.isNotEmpty())
        if (bad.isNotEmpty()) Text(tr("Not valid: ") + bad.joinToString(), color = Destructive, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsButton(if (saving) tr("Saving…") else tr("Save"), primary = true, enabled = bad.isEmpty() && !saving) {
                saving = true
                onSave(lines.distinct()) { saving = false; editing = false }
            }
            SettingsButton(tr("Cancel")) { editing = false; text = initial.joinToString("\n") }
        }
    }
}
