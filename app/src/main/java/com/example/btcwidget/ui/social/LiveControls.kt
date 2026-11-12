package com.example.btcwidget.ui.social

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

private val LiveRedDot = Color(0xFFE5534B)

/** How close to the newest part of a live stream counts as watching it live. */
private const val LIVE_EDGE_SLACK_MS = 8_000L

/**
 * YouTube-style controls over a live stream or its replay. Tap the video to show them (they
 * hide again after a few seconds while playing): back 10 s, play/pause, forward 10 s in the
 * middle; underneath, the time, LIVE (tap to catch up when behind), [extra] buttons, and a
 * time bar to drag. A live stream can only go back as far as its server keeps; when it keeps
 * nothing, there's no bar and the skip buttons are greyed out.
 */
@Composable
internal fun StreamControls(
    player: ExoPlayer,
    live: Boolean,
    modifier: Modifier = Modifier,
    onTogglePlay: () -> Unit = { if (player.isPlaying) player.pause() else player.play() },
    extra: @Composable RowScope.() -> Unit = {}
) {
    var shown by remember(player) { mutableStateOf(true) }
    var playing by remember(player) { mutableStateOf(player.isPlaying || player.playWhenReady) }
    var position by remember(player) { mutableLongStateOf(0L) }
    var duration by remember(player) { mutableLongStateOf(0L) }
    var seekable by remember(player) { mutableStateOf(false) }
    // How far behind live we are, and the smallest that's been seen (the server's live edge).
    var behind by remember(player) { mutableLongStateOf(0L) }
    var edge by remember(player) { mutableLongStateOf(Long.MAX_VALUE) }
    var scrub by remember(player) { mutableStateOf<Float?>(null) }
    var touched by remember(player) { mutableLongStateOf(0L) }
    val haptics = rememberHaptics()

    LaunchedEffect(player) {
        while (true) {
            playing = player.isPlaying || player.playWhenReady
            duration = player.duration.coerceAtLeast(0)
            if (scrub == null) position = player.currentPosition.coerceAtLeast(0)
            seekable = player.isCurrentMediaItemSeekable && duration > 0
            if (live) {
                val offset = player.currentLiveOffset
                if (offset >= 0) { behind = offset; if (offset < edge) edge = offset }
            }
            delay(250)
        }
    }
    // Hide again a few seconds after the last touch while it plays.
    LaunchedEffect(shown, playing, touched, scrub) {
        if (shown && playing && scrub == null) { delay(3_500); shown = false }
    }

    val atLive = !live || edge == Long.MAX_VALUE || behind - edge <= LIVE_EDGE_SLACK_MS
    fun seekBy(ms: Long) {
        if (!seekable) return
        haptics.tick(); touched = System.nanoTime()
        player.seekTo((player.currentPosition + ms).coerceIn(0, duration))
    }

    Box(
        modifier.fillMaxSize().clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null,
            onClickLabel = if (shown) tr("Hide controls") else tr("Show controls")
        ) { shown = !shown; touched = System.nanoTime() }
    ) {
        AnimatedVisibility(visible = shown || !playing, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000))) {
                // Back 10 s · play/pause · forward 10 s.
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SkipButton(Glyphs.Replay, tr("Back 10 seconds"), enabled = seekable) { seekBy(-10_000) }
                    Box(
                        Modifier.size(60.dp).clip(CircleShape).background(Color(0x99000000))
                            .clickable(onClickLabel = if (playing) tr("Pause") else tr("Play")) { haptics.tick(); touched = System.nanoTime(); onTogglePlay() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(if (playing) Glyphs.Pause else Glyphs.Play, contentDescription = if (playing) tr("Pause") else tr("Play"), tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    SkipButton(Glyphs.Forward, tr("Forward 10 seconds"), enabled = seekable && !(live && atLive)) { seekBy(10_000) }
                }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (live) {
                            // LIVE: red while watching live; grey when behind, tap to catch up.
                            Row(
                                Modifier.clip(RoundedCornerShape(50)).background(Color(0x99000000))
                                    .clickable(enabled = !atLive, onClickLabel = tr("Go to live")) {
                                        haptics.tick(); touched = System.nanoTime(); player.seekToDefaultPosition(); player.play()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(if (atLive) LiveRedDot else Color(0xFFAAAAAA)))
                                Text(tr("LIVE"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
                            }
                            if (!atLive) Text(
                                "-" + clockLabel(behind - edge.coerceAtMost(behind)), color = Color.White, fontSize = 13.sp,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        } else if (duration > 0) Text(
                            clockLabel(((scrub ?: (position.toFloat() / duration)) * duration).toLong()) + " / " + clockLabel(duration),
                            color = Color.White, fontSize = 13.sp
                        )
                        Spacer(Modifier.weight(1f))
                        extra()
                    }
                    if (seekable) SeekBar(
                        fraction = scrub ?: if (duration > 0) position.toFloat() / duration else 0f,
                        dragging = scrub != null,
                        onScrub = { f -> scrub = f; touched = System.nanoTime(); if (f != null) player.seekTo((f * duration).toLong()) },
                        onSeek = { f -> haptics.tick(); position = (f * duration).toLong(); player.seekTo(position); touched = System.nanoTime() }
                    ) else Spacer(Modifier.height(10.dp))
                }
            }
        }
        // While dragging: the time you'd land on, large in the middle.
        scrub?.let { f ->
            Text(
                if (live) "-" + clockLabel(((1f - f) * duration).toLong()) else clockLabel((f * duration).toLong()) + "  /  " + clockLabel(duration),
                color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
                    .clip(RoundedCornerShape(12.dp)).background(Color(0x99000000)).padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}

/** A round skip button: the circular arrow with "10" in it. */
@Composable
private fun SkipButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(enabled = enabled, onClickLabel = label, onClick = onClick).alpha(if (enabled) 1f else 0.35f),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(40.dp))
        Text("10", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
    }
}

/**
 * The orange time bar: tap to jump, drag to scrub (it thickens and shows a handle). [onScrub]
 * gets the finger's place while dragging, then null.
 */
@Composable
internal fun SeekBar(fraction: Float, dragging: Boolean, onScrub: (Float?) -> Unit, onSeek: (Float) -> Unit) {
    val barHeight by animateDpAsState(if (dragging) 6.dp else 3.dp, label = "bar")
    var width by remember { mutableFloatStateOf(1f) }
    Box(
        Modifier.fillMaxWidth().height(26.dp)
            .semantics { contentDescription = tr("Video position") }
            .pointerInput(Unit) {
                width = size.width.toFloat()
                detectTapGestures { at -> onSeek((at.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { at -> onScrub((at.x / size.width).coerceIn(0f, 1f)) },
                    onHorizontalDrag = { change, _ -> change.consume(); onScrub((change.position.x / size.width).coerceIn(0f, 1f)) },
                    onDragEnd = { onScrub(null) },
                    onDragCancel = { onScrub(null) }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
            Box(Modifier.fillMaxWidth().height(barHeight).clip(RoundedCornerShape(2.dp)).background(Color(0x55FFFFFF))) {
                Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(barHeight).background(VideoAccent))
            }
            Box(
                Modifier.offset(x = maxWidth * fraction.coerceIn(0f, 1f) - if (dragging) 8.dp else 6.dp, y = if (dragging) (-5).dp else (-4.5).dp)
                    .size(if (dragging) 16.dp else 12.dp).clip(CircleShape).background(VideoAccent)
            )
        }
    }
}

/**
 * Over a stream or replay: a spinner while it loads or buffers, and when it can't play, what
 * happened with Try again (which loads it afresh).
 */
@Composable
internal fun PlayerStatus(player: ExoPlayer, modifier: Modifier = Modifier) {
    var buffering by remember(player) { mutableStateOf(player.playbackState == androidx.media3.common.Player.STATE_BUFFERING) }
    var failed by remember(player) { mutableStateOf(false) }
    androidx.compose.runtime.DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == androidx.media3.common.Player.STATE_BUFFERING
                if (playbackState == androidx.media3.common.Player.STATE_READY) failed = false
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) { failed = true; buffering = false }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (failed) Column(
            Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xCC000000)).padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(tr("Couldn't play this"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(tr("The connection dropped or the stream is offline."), color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            Text(
                tr("Try again"), color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(50)).background(VideoAccent)
                    .clickable { failed = false; player.prepare(); player.play() }.padding(horizontal = 18.dp, vertical = 8.dp)
            )
        } else if (buffering) androidx.compose.material3.CircularProgressIndicator(color = VideoAccent, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
    }
}

