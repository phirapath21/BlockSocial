package com.example.btcwidget.ui.social

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.ui.main.EInk
import kotlinx.coroutines.delay

/** Where each video was left, so the full-screen view and the timeline carry on from there. */
internal object VideoPositions {
    private val positions = java.util.concurrent.ConcurrentHashMap<String, Long>()
    operator fun get(url: String): Long = positions[url] ?: 0L
    operator fun set(url: String, ms: Long) { positions[url] = ms.coerceAtLeast(0) }
}

/** Builds a player for [url], starting where it was left, and reports the video's shape. */
internal fun newPlayer(context: Context, url: String, onRatio: (Float) -> Unit): ExoPlayer =
    ExoPlayer.Builder(context).build().apply {
        setMediaItem(MediaItem.fromUri(url))
        repeatMode = Player.REPEAT_MODE_ONE
        prepare()
        seekTo(VideoPositions[url])
        addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) onRatio(videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height)
            }
        })
    }

/** "-3:48": time left, as X shows it. */
internal fun remainingLabel(durationMs: Long, positionMs: Long): String {
    if (durationMs <= 0) return ""
    val s = ((durationMs - positionMs).coerceAtLeast(0) + 999) / 1000
    return "-" + (if (s >= 3600) "${s / 3600}:" + "%02d".format((s % 3600) / 60) else "${s / 60}") + ":" + "%02d".format(s % 60)
}

/** The video picture itself, without controls (they're drawn in Compose). */
@Composable
internal fun VideoSurface(player: ExoPlayer, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = false } },
        update = { it.player = player },
        modifier = modifier
    )
}

/**
 * A video in a note, like X's timeline: when [VideoAutoplay] allows it, it plays muted
 * while mostly on screen and pauses when scrolled away, showing the time left and a sound
 * button. Otherwise nothing downloads until it's tapped. Tapping opens the full-screen view.
 */
@Composable
fun VideoPlayer(url: String, note: Event, actions: NoteActions) {
    val context = LocalContext.current
    var onScreen by remember(url) { mutableStateOf(false) }
    var ratio by remember(url) { mutableFloatStateOf(16f / 9f) }
    var muted by remember(url) { mutableStateOf(true) }
    var remaining by remember(url) { mutableStateOf("") }
    var player by remember(url) { mutableStateOf<ExoPlayer?>(null) }
    val play = actions.autoplayVideos && onScreen && actions.openVideoUrl != url

    LaunchedEffect(play) {
        val p = player
        if (play) {
            val active = p ?: newPlayer(context, url) { ratio = it }.also { player = it }
            active.seekTo(VideoPositions[url])
            active.volume = if (muted) 0f else 1f
            active.play()
            while (true) {
                remaining = remainingLabel(active.duration, active.currentPosition)
                VideoPositions[url] = active.currentPosition
                delay(500)
            }
        } else if (p != null) {
            VideoPositions[url] = p.currentPosition
            p.pause()
        }
    }
    DisposableEffect(url) {
        onDispose { player?.let { VideoPositions[url] = it.currentPosition; it.release() }; player = null }
    }

    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .aspectRatio(ratio.coerceIn(0.8f, 2f))
            .clip(shape)
            .background(Color.Black)
            .border(0.5.dp, EInk.BodyStroke, shape)
            .onGloballyPositioned { c ->
                // "On screen" once at least 60% of it shows, as on X.
                val shown = c.boundsInWindow().height
                onScreen = c.size.height > 0 && shown / c.size.height >= 0.6f
            }
            .clickable(onClickLabel = "Open video") { actions.openVideo(url, note) },
        contentAlignment = Alignment.Center
    ) {
        val p = player
        if (p != null) VideoSurface(p, Modifier.fillMaxSize())
        if (p == null || !play) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                Icon(Glyphs.Play, contentDescription = "Play video", tint = Color.White, modifier = Modifier.size(34.dp))
            }
        }
        if (p != null && play) {
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (remaining.isNotEmpty()) VideoBadge { Text(remaining, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                Box(Modifier.weight(1f))
                VideoBadge(onClick = { muted = !muted; p.volume = if (muted) 0f else 1f }, label = if (muted) "Turn sound on" else "Mute") {
                    Icon(if (muted) Glyphs.SoundOff else Glyphs.SoundOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun VideoBadge(onClick: (() -> Unit)? = null, label: String? = null, content: @Composable () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(Color(0x99000000))
            .then(if (onClick != null) Modifier.clickable(onClickLabel = label, onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) { content() }
}

/**
 * Full-screen controls, as X shows them under a video: a seek bar, then play/pause, time
 * left, speed (1x › 1.5x › 2x › 0.5x), sound, and rotate to landscape.
 */
@Composable
internal fun VideoControls(player: ExoPlayer, url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var position by remember { mutableLongStateOf(player.currentPosition) }
    var duration by remember { mutableLongStateOf(player.duration) }
    var playing by remember { mutableStateOf(player.isPlaying) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    var speedIndex by remember { mutableStateOf(0) }
    var muted by remember { mutableStateOf(player.volume == 0f) }
    var landscape by remember { mutableStateOf(false) }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            duration = player.duration
            playing = player.isPlaying
            VideoPositions[url] = position
            delay(250)
        }
    }
    // Back to the phone's own orientation when the viewer closes.
    DisposableEffect(Unit) { onDispose { context.findActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED } }

    Column(modifier.fillMaxWidth()) {
        val total = duration.coerceAtLeast(1)
        Slider(
            value = dragging ?: (position.toFloat() / total).coerceIn(0f, 1f),
            onValueChange = { dragging = it },
            onValueChangeFinished = { dragging?.let { player.seekTo((it * total).toLong()) }; dragging = null },
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color(0x55FFFFFF)),
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ControlIcon(if (playing) Glyphs.Pause else Glyphs.Play, if (playing) "Pause" else "Play") {
                if (player.isPlaying) player.pause() else player.play()
            }
            Text(remainingLabel(duration, position), color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
            Box(Modifier.weight(1f))
            Text(
                SPEED_LABELS[speedIndex], color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClickLabel = "Playback speed") {
                    speedIndex = (speedIndex + 1) % SPEEDS.size
                    player.setPlaybackSpeed(SPEEDS[speedIndex])
                }.padding(horizontal = 12.dp, vertical = 8.dp)
            )
            ControlIcon(if (muted) Glyphs.SoundOff else Glyphs.SoundOn, if (muted) "Turn sound on" else "Mute") {
                muted = !muted
                player.volume = if (muted) 0f else 1f
            }
            ControlIcon(Glyphs.Rotate, if (landscape) "Back to portrait" else "Landscape") {
                landscape = !landscape
                context.findActivity()?.requestedOrientation =
                    if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
}

private val SPEEDS = listOf(1f, 1.5f, 2f, 0.5f)
private val SPEED_LABELS = listOf("1x", "1.5x", "2x", "0.5x")

@Composable
private fun ControlIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp)) }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
