package com.example.btcwidget.ui.social

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Drag a video down to shrink it into the mini player, as on YouTube: it follows your finger,
 * getting smaller; let go past a short distance and [onMinimize] runs, otherwise it springs
 * back. [progress] (0..1) lets what's around the video fade as it goes.
 */
internal class DragToMinimize(val modifier: Modifier, private val offset: Animatable<Float, *>, private val distance: Float) {
    val progress: Float get() = (offset.value / distance).coerceIn(0f, 1f)
}

@Composable
internal fun dragToMinimize(enabled: Boolean = true, onMinimize: () -> Unit): DragToMinimize {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val threshold = with(density) { 110.dp.toPx() }
    val distance = with(density) { 320.dp.toPx() }
    val minimize by rememberUpdatedState(onMinimize)
    val modifier = if (!enabled) Modifier else Modifier
        .graphicsLayer {
            val p = (offset.value / distance).coerceIn(0f, 1f)
            translationY = offset.value
            val s = 1f - 0.5f * p
            scaleX = s; scaleY = s
            // Shrinks toward the bottom right, where the mini player sits.
            transformOrigin = TransformOrigin(0.9f, 0f)
        }
        .pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragEnd = {
                    if (offset.value > threshold) {
                        haptics.tick()
                        minimize()
                        scope.launch { offset.snapTo(0f) }
                    } else scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.75f)) }
                },
                onDragCancel = { scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.75f)) } }
            ) { change, dy ->
                val next = (offset.value + dy).coerceAtLeast(0f)
                if (next > 0f || dy > 0f) change.consume()
                scope.launch { offset.snapTo(next) }
            }
        }
    return DragToMinimize(modifier, offset, distance)
}

/** The modifier alone, for places that don't fade anything with the drag. */
@Composable
internal fun rememberDragToMinimize(enabled: Boolean = true, onMinimize: () -> Unit): Modifier =
    dragToMinimize(enabled, onMinimize).modifier
