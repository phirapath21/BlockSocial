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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.runtime.setValue
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
    val margin = with(density) { 12.dp.toPx() }
    // Where the video sits on the screen, so it can't be dragged off the bottom of it.
    val top = remember { floatArrayOf(0f, 0f, Float.MAX_VALUE) } // top, height, screen height
    // Once let go past the line it stays where it is, small, while the screen fades away
    // (springing back to full size first looked like the animation running backwards).
    var done by remember { androidx.compose.runtime.mutableStateOf(false) }
    val modifier = if (!enabled) Modifier else Modifier
        .onGloballyPositioned { c ->
            top[0] = c.positionInRoot().y
            top[1] = c.size.height.toFloat()
            top[2] = c.findRootCoordinates().size.height.toFloat()
        }
        .graphicsLayer {
            val p = (offset.value / distance).coerceIn(0f, 1f)
            val s = 1f - 0.5f * p
            // Follows the finger, but the shrinking video always stays on screen.
            val room = (top[2] - top[0] - top[1] * s - margin).coerceAtLeast(0f)
            translationY = offset.value.coerceAtMost(room)
            scaleX = s; scaleY = s
            // Shrinks toward the bottom right, where the mini player sits.
            transformOrigin = TransformOrigin(0.9f, 0f)
        }
        .pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragEnd = {
                    if (done) return@detectVerticalDragGestures
                    if (offset.value > threshold) {
                        haptics.tick()
                        done = true
                        // The screen leaves with a quiet fade, not its usual slide.
                        PipVideo.minimizedAt = System.currentTimeMillis()
                        minimize()
                    } else scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.75f)) }
                },
                onDragCancel = { scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.75f)) } }
            ) { change, dy ->
                if (done) return@detectVerticalDragGestures
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
