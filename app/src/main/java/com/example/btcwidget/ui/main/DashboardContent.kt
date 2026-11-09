package com.example.btcwidget.ui.main

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.BlockclockMetrics
import com.example.btcwidget.MetricCategory
import com.example.btcwidget.PriceData
import com.example.btcwidget.PricePoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

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
    order: List<String> = BlockclockMetrics.DEFAULT_ORDER,
    onOrderChange: (List<String>) -> Unit = {},
    collapsedCategories: Set<MetricCategory> = emptySet(),
    onToggleCategory: (MetricCategory) -> Unit = {},
    stackSats: Long = 0,
    onSetUpStack: () -> Unit = {},
    history: List<PricePoint> = emptyList(),
    modifier: Modifier = Modifier
) {
    val hasStack = stackSats > 0
    // A cleared stack can't stay the hero.
    val hero = if (heroMetric == "STACK" && !hasStack) "PRICE" else heroMetric
    val haptics = LocalHapticFeedback.current

    // Working copy so the grid reflows instantly while dragging; saved on drop.
    var workingOrder by remember { mutableStateOf(BlockclockMetrics.sanitizeOrder(order)) }
    LaunchedEffect(order) { workingOrder = BlockclockMetrics.sanitizeOrder(order) }

    val visibleKeys = BlockclockMetrics.orderedModes(workingOrder)
        .filter { index ->
            // The dashboard shows every available metric; the widget's on/off switches
            // live in the Widgets tab.
            BlockclockMetrics.isAvailable(index, currencySetting, hasStack) &&
                BlockclockMetrics.MODE_KEYS[index] != hero
        }
        .map { BlockclockMetrics.MODE_KEYS[it] }

    val gridState = rememberLazyGridState()
    val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
        // Only metric cards are ReorderableItems, so both keys are mode keys. A drop on
        // another section is ignored by move(): metrics stay in their category.
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
                display = BlockclockMetrics.forKey(hero, data, currencySetting, stackSats),
                aspectRatio = 2f,
                stale = !data.lastFetchSuccessful
            )
        }

        fullWidth("hero_choices") {
            val choices = if (hasStack) listOf("STACK") + HERO_CHOICES else HERO_CHOICES
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                choices.forEach { key ->
                    EInkChip(
                        text = key,
                        selected = hero == key,
                        onClick = { onHeroMetricSelected(key) }
                    )
                }
            }
        }

        fullWidth("price_chart") {
            PriceChartPanel(
                history = history,
                currency = currencySetting,
                usdToCurrency = if (currencySetting == "THB" && data.btcUsd > 0) data.btcThb / data.btcUsd else 1.0,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        fullWidth("reorder_hint") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hold and drag to reorder within a section",
                    color = EInk.Caption,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                )
                if (!BlockclockMetrics.isDefaultOrder(workingOrder)) {
                    EInkChip(
                        text = "RESET",
                        selected = false,
                        onClick = {
                            workingOrder = BlockclockMetrics.DEFAULT_ORDER
                            onOrderChange(BlockclockMetrics.DEFAULT_ORDER)
                        }
                    )
                }
            }
        }

        if (!hasStack) {
            fullWidth("stack_setup") {
                StackSetupCard(onSetUp = onSetUpStack)
            }
        }

        MetricCategory.entries.forEach { category ->
            val keys = visibleKeys.filter { BlockclockMetrics.categoryOf(it) == category }
            if (keys.isEmpty()) return@forEach
            val collapsed = category in collapsedCategories

            fullWidth("section_${category.name}") {
                CategoryHeader(
                    title = category.title,
                    count = keys.size,
                    collapsed = collapsed,
                    onClick = { onToggleCategory(category) }
                )
            }
            if (collapsed) return@forEach

            items(keys, key = { it }) { key ->
                ReorderableItem(reorderState, key = key) { isDragging ->
                    val scale by animateFloatAsState(if (isDragging) 1.05f else 1f, label = "lift")
                    val elevation by animateDpAsState(if (isDragging) 16.dp else 0.dp, label = "shadow")
                    val index = BlockclockMetrics.MODE_KEYS.indexOf(key)


                    Column(
                        modifier = Modifier.graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                    ) {
                        EInkDevice(
                            display = BlockclockMetrics.forMode(index, data, currencySetting, stackSats),
                            aspectRatio = 1.7f,
                            progress = if (key == "HALVING") {
                                BlockclockMetrics.halvingProgress(data.blockHeight).toFloat()
                            } else {
                                null
                            },
                            modifier = Modifier
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

/** Invitation to enter holdings; replaces the empty "Your stack" section. */
@Composable
private fun StackSetupCard(onSetUp: () -> Unit) {
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
private fun CategoryHeader(title: String, count: Int, collapsed: Boolean, onClick: () -> Unit) {
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
private fun LazyGridScope.fullWidth(key: String, content: @Composable LazyGridItemScope.() -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }, content = content)
}
