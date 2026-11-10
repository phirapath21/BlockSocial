package com.example.btcwidget.ui.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.AppTheme
import java.util.Locale

/** Shared building blocks for the dashboard and widgets screens. */

internal val CardShape = RoundedCornerShape(10.dp)

@Composable
fun HeaderSection(title: String, subtitle: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 14.dp)
    ) {
        Text(
            text = title,
            color = EInk.Value,
            // The terminal face is light and small for its size; set it larger, unbolded.
            fontSize = if (EInk.theme == AppTheme.RETRO) 26.sp else 17.sp,
            fontWeight = if (EInk.theme == AppTheme.RETRO) FontWeight.Normal else FontWeight.Bold,
            fontFamily = EInk.TitleFont,
            letterSpacing = 4.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle.uppercase(Locale.US),
            color = EInk.Caption,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp
        )
    }
}

/** Invitation to enter holdings; replaces the empty "Your stack" section. */
@Composable
internal fun StackSetupCard(onSetUp: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .border(1.dp, EInk.BodyStroke, CardShape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            SectionLabel("Your stack")
            Text(
                text = "See what your bitcoin is worth here and on the widget.",
                color = EInk.Label,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        EInkChip(text = "SET UP", selected = true, onClick = onSetUp)
    }
}

/** Tappable section title that collapses or expands its metrics. */
@Composable
internal fun CategoryHeader(title: String, count: Int, collapsed: Boolean, onClick: () -> Unit) {
    val rotation by animateFloatAsState(if (collapsed) -90f else 0f, label = "chevron")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(EInk.ChipShape)
            .clickable(onClickLabel = if (collapsed) "Expand" else "Collapse", onClick = onClick)
            .semantics { stateDescription = if (collapsed) "Collapsed" else "Expanded" }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionLabel(title)
        Text(
            text = "  $count",
            color = EInk.Caption,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = EInk.Label,
            modifier = Modifier.rotate(rotation)
        )
    }
}

/** A grid item spanning both columns. */
internal fun LazyGridScope.fullWidth(key: String, content: @Composable LazyGridItemScope.() -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }, content = content)
}
