package com.example.btcwidget.ui.social

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.ui.main.EInk
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The live stream keeps playing in a small window after you leave its screen, like YouTube's:
 * drag it anywhere (it settles at the nearest side), tap it to go back to the stream and its
 * chat, mute it, or ✕ to stop. Sits above the bottom bar; hidden in Picture-in-Picture.
 */
@Composable
fun LiveMiniPlayer(onOpen: (address: String) -> Unit, modifier: Modifier = Modifier) {
    val show = PipVideo.showMini
    val player = PipVideo.player
    val live = PipVideo.live
    AnimatedVisibility(
        visible = show && player != null && live != null,
        enter = scaleIn(initialScale = 0.85f) + fadeIn(),
        exit = scaleOut(targetScale = 0.85f) + fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        val p = player ?: return@AnimatedVisibility
        val l = live ?: return@AnimatedVisibility
        BoxWithConstraints(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 80.dp)) {
            val density = LocalDensity.current
            val margin = with(density) { 12.dp.toPx() }
            val widthPx = with(density) { MINI_WIDTH.toPx() }
            val heightPx = widthPx * 9f / 16f + with(density) { 34.dp.toPx() }
            val maxX = constraints.maxWidth - widthPx - margin
            val maxY = constraints.maxHeight - heightPx - margin
            // Starts bottom right; remembered while it's shown.
            val x = remember { Animatable(maxX) }
            val y = remember { Animatable(maxY) }
            val scope = rememberCoroutineScope()
            Column(
                Modifier
                    .offset { IntOffset(x.value.roundToInt(), y.value.roundToInt()) }
                    .width(MINI_WIDTH)
                    .shadow(12.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(SheetColor)
                    .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(14.dp))
                    .pointerInput(maxX, maxY) {
                        detectDragGestures(
                            onDragEnd = {
                                // Settle at the nearer side, kept on screen.
                                val targetX = if (x.value + widthPx / 2 < constraints.maxWidth / 2f) margin else maxX
                                scope.launch { x.animateTo(targetX, spring(dampingRatio = 0.8f)) }
                                scope.launch { y.animateTo(y.value.coerceIn(margin, maxY), spring(dampingRatio = 0.8f)) }
                            }
                        ) { change, drag ->
                            change.consume()
                            scope.launch { x.snapTo((x.value + drag.x).coerceIn(0f, constraints.maxWidth - widthPx)) }
                            scope.launch { y.snapTo((y.value + drag.y).coerceIn(0f, constraints.maxHeight - heightPx)) }
                        }
                    }
                    .clickable(onClickLabel = tr("Back to the stream")) { onOpen(l.address) }
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
                    // A TextureView: it moves and clips with the window, unlike a SurfaceView.
                    VideoSurface(p, Modifier.fillMaxSize(), inFeed = true)
                    Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent))).padding(6.dp)) {
                        LiveBadge(Modifier.align(Alignment.TopStart))
                        Row(Modifier.align(Alignment.TopEnd)) {
                            MiniButton(if (PipVideo.muted) Glyphs.SoundOff else Glyphs.SoundOn, if (PipVideo.muted) "Sound on" else "Mute") { PipVideo.mute(!PipVideo.muted) }
                            MiniButton(Glyphs.Close, "Stop the stream") { PipVideo.release() }
                        }
                    }
                }
                Column(Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text(l.title, color = EInk.Value, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(l.host, color = EInk.Caption, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun MiniButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.padding(start = 4.dp).size(28.dp).clip(CircleShape).background(Color(0x66000000)).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(16.dp)) }
}

private val MINI_WIDTH = 200.dp
