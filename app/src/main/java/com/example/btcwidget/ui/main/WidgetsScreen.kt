package com.example.btcwidget.ui.main

import com.example.btcwidget.ui.social.tr
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.BlockclockMetrics
import com.example.btcwidget.MetricCategory
import com.example.btcwidget.PriceData
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

/**
 * The home-screen widgets: how to add one, then every metric as a panel, grouped into
 * collapsible sections. Each panel has an "On widget" switch (whether tapping the
 * widget cycles through it) and can be reordered within its section by long-pressing
 * and dragging; that order is the widget's cycling order.
 */
@Composable
fun WidgetsScreen(
    data: PriceData?,
    currencySetting: String,
    modeStates: List<Boolean>,
    onModeToggle: (Int, Boolean) -> Unit,
    order: List<String>,
    onOrderChange: (List<String>) -> Unit,
    collapsedCategories: Set<MetricCategory>,
    onToggleCategory: (MetricCategory) -> Unit,
    stackSats: Long,
    onSetUpStack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasStack = stackSats > 0

    // Working copy so the grid reflows instantly while dragging; saved on drop.
    var workingOrder by remember { mutableStateOf(BlockclockMetrics.sanitizeOrder(order)) }
    LaunchedEffect(order) { workingOrder = BlockclockMetrics.sanitizeOrder(order) }

    val visibleKeys = BlockclockMetrics.orderedModes(workingOrder)
        .filter { BlockclockMetrics.isAvailable(it, currencySetting, hasStack) }
        .map { BlockclockMetrics.MODE_KEYS[it] }
    val onCount = visibleKeys.count { modeStates.getOrElse(BlockclockMetrics.MODE_KEYS.indexOf(it)) { true } }

    val gridState = rememberLazyGridState()
    val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
        // Only metric panels are ReorderableItems, so both keys are mode keys. A drop on
        // another section is ignored by move(): metrics stay in their category.
        val fromKey = from.key as? String ?: return@rememberReorderableLazyGridState
        val toKey = to.key as? String ?: return@rememberReorderableLazyGridState
        workingOrder = BlockclockMetrics.move(workingOrder, fromKey, toKey)
    }

    LazyVerticalGrid(
        // Two columns on a phone; more as the screen gets wider (on its side, a tablet).
        columns = GridCells.Adaptive(minSize = 170.dp),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        fullWidth("header") { HeaderSection(title = tr("WIDGETS"), subtitle = tr("On your home screen")) }

        fullWidth("how_to") {
            EInkPanel(title = tr("Add a widget")) {
                Step(1, "Long-press an empty spot on your home screen.")
                Step(2, "Open Widgets and find Block.")
                Step(3, "Drag Bitcoin metrics, Price chart, Halving countdown or Bitcoin quotes onto the screen.")
                Step(4, "Tap Bitcoin metrics to cycle modes, or the chart to switch 24H / 7D.")
            }
        }

        fullWidth("metrics_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(start = 4.dp, end = 8.dp)) {
                    SectionLabel(tr("On the widget · {0} of {1}", onCount, visibleKeys.size))
                    Text(
                        text = tr("Switch metrics on or off. Hold and drag to set the order taps cycle through."),
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
                            workingOrder = BlockclockMetrics.DEFAULT_ORDER
                            onOrderChange(BlockclockMetrics.DEFAULT_ORDER)
                        }
                    )
                }
            }
        }

        if (!hasStack) {
            fullWidth("stack_setup") { StackSetupCard(onSetUp = onSetUpStack) }
        }

        if (data == null) {
            fullWidth("no_data") {
                Text(
                    text = tr("Metrics appear after the first refresh."),
                    color = EInk.Label,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }
            return@LazyVerticalGrid
        }

        MetricCategory.entries.forEach { category ->
            val keys = visibleKeys.filter { BlockclockMetrics.categoryOf(it) == category }
            if (keys.isEmpty()) return@forEach
            val collapsed = category in collapsedCategories

            fullWidth("section_${category.name}") {
                CategoryHeader(
                    title = tr(category.title),
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
                    val on = modeStates.getOrElse(index) { true }

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
                                .alpha(if (on || isDragging) 1f else OFF_ALPHA)
                                .shadow(elevation, CardShape)
                                .then(if (isDragging) Modifier.border(1.dp, EInk.Label, CardShape) else Modifier)
                                .longPressDraggableHandle(
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
                        WidgetToggle(on = on, onToggle = { onModeToggle(index, it) })
                    }
                }
            }
        }
    }
}

/** Opacity of a metric switched off for the widget. */
private const val OFF_ALPHA = 0.38f

/**
 * "On widget" switch under a metric panel: whether tapping the home-screen widget
 * cycles through this metric. The whole row toggles, with Switch semantics.
 */
@Composable
private fun WidgetToggle(on: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
            .clip(EInk.ChipShape)
            .toggleable(value = on, role = Role.Switch, onValueChange = onToggle)
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (on) tr("ON WIDGET") else tr("OFF WIDGET"),
            color = if (on) EInk.Label else EInk.Caption,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp,
            modifier = Modifier.weight(1f)
        )
        EInkSwitch(checked = on, onCheckedChange = null, modifier = Modifier.scale(0.7f))
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
