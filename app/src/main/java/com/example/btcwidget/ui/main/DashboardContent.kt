package com.example.btcwidget.ui.main

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.BlockclockMetrics
import com.example.btcwidget.PriceData
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Metrics that can be pinned to the large device at the top of the dashboard. */
private val HERO_CHOICES = listOf("BLOCK", "PRICE", "SATS", "FEES")

private val CardShape = RoundedCornerShape(10.dp)

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
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
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

/**
 * The dashboard: header, hero panel and a two-column grid of metric panels the user
 * can reorder by long-pressing and dragging. The order is saved through
 * [onOrderChange] and also sets the order the home-screen widget cycles through.
 */
@Composable
fun DashboardContent(
    data: PriceData,
    heroMetric: String,
    onHeroMetricSelected: (String) -> Unit,
    enabledModes: List<Boolean>,
    currencySetting: String,
    order: List<String> = BlockclockMetrics.MODE_KEYS,
    onOrderChange: (List<String>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current

    // Working copy so the grid reflows instantly while dragging; saved on drop.
    var workingOrder by remember { mutableStateOf(BlockclockMetrics.sanitizeOrder(order)) }
    LaunchedEffect(order) { workingOrder = BlockclockMetrics.sanitizeOrder(order) }

    val visibleKeys = BlockclockMetrics.orderedModes(workingOrder)
        .filter { index ->
            BlockclockMetrics.isActive(index, enabledModes.getOrElse(index) { true }, currencySetting) &&
                BlockclockMetrics.MODE_KEYS[index] != heroMetric
        }
        .map { BlockclockMetrics.MODE_KEYS[it] }

    val gridState = rememberLazyGridState()
    val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
        // Only metric cards are ReorderableItems, so both keys are mode keys.
        val fromKey = from.key as? String ?: return@rememberReorderableLazyGridState
        val toKey = to.key as? String ?: return@rememberReorderableLazyGridState
        workingOrder = BlockclockMetrics.move(workingOrder, fromKey, toKey)
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        fullWidth("header") { HeaderSection(title = "BLOCKFACE", subtitle = "Bitcoin at a glance") }

        if (!data.lastFetchSuccessful) {
            fullWidth("offline") {
                Text(
                    text = "OFFLINE · SHOWING CACHED DATA",
                    color = EInk.Alert,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, EInk.Alert, EInk.ChipShape)
                        .padding(vertical = 8.dp, horizontal = 12.dp)
                )
            }
        }

        // Hero panel, same proportions as a 2x1 widget.
        fullWidth("hero") {
            EInkDevice(
                display = BlockclockMetrics.forKey(heroMetric, data, currencySetting),
                aspectRatio = 2f,
                stale = !data.lastFetchSuccessful
            )
        }

        fullWidth("hero_choices") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                HERO_CHOICES.forEach { key ->
                    EInkChip(
                        text = key,
                        selected = heroMetric == key,
                        onClick = { onHeroMetricSelected(key) }
                    )
                }
            }
        }

        fullWidth("metrics_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                    SectionLabel("All metrics")
                    Text(
                        text = "Hold and drag to reorder · widget taps follow this order",
                        color = EInk.Caption,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (!BlockclockMetrics.isDefaultOrder(workingOrder)) {
                    EInkChip(
                        text = "RESET",
                        selected = false,
                        onClick = {
                            workingOrder = BlockclockMetrics.MODE_KEYS
                            onOrderChange(BlockclockMetrics.MODE_KEYS)
                        }
                    )
                }
            }
        }

        items(visibleKeys, key = { it }) { key ->
            ReorderableItem(reorderState, key = key) { isDragging ->
                val scale by animateFloatAsState(if (isDragging) 1.05f else 1f, label = "lift")
                val elevation by animateDpAsState(if (isDragging) 16.dp else 0.dp, label = "shadow")
                val index = BlockclockMetrics.MODE_KEYS.indexOf(key)

                EInkDevice(
                    display = BlockclockMetrics.forMode(index, data, currencySetting),
                    aspectRatio = 1.7f,
                    progress = if (key == "HALVING") {
                        BlockclockMetrics.halvingProgress(data.blockHeight).toFloat()
                    } else {
                        null
                    },
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .shadow(elevation, CardShape)
                        .then(if (isDragging) Modifier.border(1.dp, EInk.Label, CardShape) else Modifier)
                        .longPressDraggableHandle(
                            onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                            onDragStopped = { onOrderChange(workingOrder) }
                        )
                        .semantics {
                            // TalkBack alternative to dragging.
                            customActions = listOf(
                                CustomAccessibilityAction("Move earlier") {
                                    workingOrder = BlockclockMetrics.moveAmongVisible(workingOrder, visibleKeys, key, -1)
                                    onOrderChange(workingOrder)
                                    true
                                },
                                CustomAccessibilityAction("Move later") {
                                    workingOrder = BlockclockMetrics.moveAmongVisible(workingOrder, visibleKeys, key, +1)
                                    onOrderChange(workingOrder)
                                    true
                                }
                            )
                        }
                )
            }
        }

        fullWidth("updated") {
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            Text(
                text = "LAST UPDATED ${formatter.format(Date(data.timestamp))}",
                color = EInk.Caption,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

/** A grid item spanning both columns. */
private fun LazyGridScope.fullWidth(key: String, content: @Composable LazyGridItemScope.() -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }, content = content)
}
