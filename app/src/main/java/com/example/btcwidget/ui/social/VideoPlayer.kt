package com.example.btcwidget.ui.social

import androidx.compose.runtime.key
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import android.view.SurfaceView
import android.view.TextureView
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.ui.main.EInk
import kotlinx.coroutines.delay

/** Where each video was left, so the full-screen view and the timeline carry on from there. */
internal object VideoPositions {
    private val positions = java.util.concurrent.ConcurrentHashMap<String, Long>()
    operator fun get(url: String): Long = positions[url] ?: 0L
    operator fun set(url: String, ms: Long) { positions[url] = ms.coerceAtLeast(0) }
}

/**
 * Builds a player for [url], starting where it was left, and reports the video's shape.
 *
 * Its buffer is kept small: ExoPlayer's default reads ahead up to 50 seconds or about 125 MB
 * of video, held in the app's memory, which with a few videos (a paused one in the feed and the
 * same one full screen) filled the whole heap and crashed. Short social videos need far less.
 */
internal fun newPlayer(context: Context, url: String, fullScreen: Boolean = false, live: Boolean = false, onRatio: (Float) -> Unit): ExoPlayer =
    ExoPlayer.Builder(context).setLoadControl(smallBuffer(fullScreen)).build().apply {
        // Data saver: the lowest quality whenever the video offers a choice.
        if (VideoQuality.fromName(com.example.btcwidget.AppPrefs.videoQuality(context)) == VideoQuality.DATA_SAVER) {
            trackSelectionParameters = trackSelectionParameters.buildUpon().setForceLowestBitrate(true).build()
        }
        setMediaItem(MediaItem.fromUri(url))
        // A live stream plays from the live edge; a video loops and carries on where it was.
        if (!live) repeatMode = Player.REPEAT_MODE_ONE
        prepare()
        if (!live) seekTo(VideoPositions[url])
        addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) onRatio(videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height)
            }
        })
    }

/** Reads 15 s ahead in the feed and 30 s full screen, and never more than 12 or 32 MB of data. */
private fun smallBuffer(fullScreen: Boolean): DefaultLoadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(
        /* minBufferMs = */ if (fullScreen) 10_000 else 5_000,
        /* maxBufferMs = */ if (fullScreen) 30_000 else 15_000,
        /* bufferForPlaybackMs = */ 1_000,
        /* bufferForPlaybackAfterRebufferMs = */ 2_000
    )
    .setTargetBufferBytes((if (fullScreen) 32 else 12) * 1024 * 1024)
    .setPrioritizeTimeOverSizeThresholds(false) // the byte limit wins over the time
    .build()

/** A resolution a video offers ("720p") and the override that picks it. */
internal class QualityOption(val height: Int, val pick: TrackSelectionOverride) {
    val label get() = "${height}p"
}

/** The resolutions this video offers, highest first; empty or one when there's no choice. */
internal fun ExoPlayer.qualityOptions(): List<QualityOption> =
    currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.flatMap { g ->
        (0 until g.length).filter { g.isTrackSupported(it) }.map { i -> QualityOption(g.getTrackFormat(i).height, TrackSelectionOverride(g.mediaTrackGroup, i)) }
    }.filter { it.height > 0 }.distinctBy { it.height }.sortedByDescending { it.height }

/** Picks [option], or Auto (the player chooses, or the lowest in data saver) when null. */
internal fun ExoPlayer.chooseQuality(option: QualityOption?, context: Context) {
    val b = trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
    if (option != null) b.setOverrideForType(option.pick).setForceLowestBitrate(false)
    else b.setForceLowestBitrate(VideoQuality.fromName(com.example.btcwidget.AppPrefs.videoQuality(context)) == VideoQuality.DATA_SAVER)
    trackSelectionParameters = b.build()
}

/** "-3:48": time left, as X shows it. */
internal fun remainingLabel(durationMs: Long, positionMs: Long): String {
    if (durationMs <= 0) return ""
    val s = ((durationMs - positionMs).coerceAtLeast(0) + 999) / 1000
    return "-" + (if (s >= 3600) "${s / 3600}:" + "%02d".format((s % 3600) / 60) else "${s / 60}") + ":" + "%02d".format(s % 60)
}

/**
 * The video picture itself, without controls (they're drawn in Compose), fitted inside
 * [modifier]'s bounds at the video's own shape, black around it. In a feed ([inFeed]) it
 * draws into a TextureView, which scrolls and clips to rounded corners like any view;
 * full screen it uses a SurfaceView, which costs less battery.
 */
@Composable
internal fun VideoSurface(player: ExoPlayer, modifier: Modifier = Modifier, inFeed: Boolean = false) {
    var ratio by remember(player) { mutableStateOf(player.videoSize.ratioOrNull()) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) { ratio = videoSize.ratioOrNull() }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        key(player, inFeed) {
            AndroidView(
                factory = { ctx ->
                    if (inFeed) TextureView(ctx).also { player.setVideoTextureView(it) }
                    else SurfaceView(ctx).also { player.setVideoSurfaceView(it) }
                },
                onRelease = { view ->
                    when (view) {
                        is TextureView -> player.clearVideoTextureView(view)
                        is SurfaceView -> player.clearVideoSurfaceView(view)
                    }
                },
                modifier = ratio?.let { Modifier.aspectRatio(it) } ?: Modifier.fillMaxSize()
            )
        }
    }
}

/** Width over height as shown (with non-square pixels), or null before the video's size is known. */
private fun VideoSize.ratioOrNull(): Float? =
    if (width > 0 && height > 0) width * pixelWidthHeightRatio / height else null

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
            // A stopped video lets go of its player and buffer: right away when it opens full
            // screen (which has its own player), else after a moment, so a small scroll back and
            // forth doesn't restart it. Playing again starts from the saved position.
            if (actions.openVideoUrl != url) delay(3_000)
            VideoPositions[url] = p.currentPosition
            p.release()
            player = null
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
        if (p != null) VideoSurface(p, Modifier.fillMaxSize(), inFeed = true)
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
internal fun VideoControls(player: ExoPlayer, url: String, landscape: Boolean, onRotate: () -> Unit, modifier: Modifier = Modifier) {
    var position by remember { mutableLongStateOf(player.currentPosition) }
    var duration by remember { mutableLongStateOf(player.duration) }
    var playing by remember { mutableStateOf(player.isPlaying) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    // Read back from the player: these controls are rebuilt when the screen turns.
    var speedIndex by remember { mutableStateOf(SPEEDS.indexOf(player.playbackParameters.speed).coerceAtLeast(0)) }
    var muted by remember { mutableStateOf(player.volume == 0f) }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            duration = player.duration
            playing = player.isPlaying
            VideoPositions[url] = position
            delay(250)
        }
    }

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
            QualityButton(player)
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
            ControlIcon(Glyphs.Rotate, if (landscape) "Back to portrait" else "Landscape", onRotate)
        }
    }
}

/**
 * "Auto ▾" / "720p ▾": shown only when the video offers more than one resolution (most
 * Nostr videos are a single file, so there's nothing to choose).
 */
@Composable
private fun QualityButton(player: ExoPlayer) {
    val context = LocalContext.current
    var options by remember { mutableStateOf(player.qualityOptions()) }
    var open by remember { mutableStateOf(false) }
    // Tracks arrive once the video starts loading.
    LaunchedEffect(player) { while (true) { options = player.qualityOptions(); delay(1_000) } }
    if (options.size < 2) return
    val manual = player.trackSelectionParameters.overrides.values.any { it.type == C.TRACK_TYPE_VIDEO }
    val label = if (manual) (player.videoSize.height.takeIf { it > 0 }?.let { "${it}p" } ?: "Auto") else "Auto"
    Box {
        Text(
            "$label ▾", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClickLabel = "Video quality") { open = true }.padding(horizontal = 10.dp, vertical = 8.dp)
        )
        BlockDropdown(expanded = open, onDismiss = { open = false }) {
            BlockDropdownItem("Auto", selected = !manual) { open = false; player.chooseQuality(null, context) }
            options.forEach { o ->
                BlockDropdownItem(o.label, selected = manual && player.videoSize.height == o.height) { open = false; player.chooseQuality(o, context) }
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

/**
 * Turns the screen for a full-screen video: [landscape] true locks it sideways, false gives
 * it back to the phone. Released (back to the phone) when the viewer closes.
 */
@Composable
internal fun rememberVideoOrientation(): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current
    var locked by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { context.findActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED } }
    return locked to {
        locked = !locked
        context.findActivity()?.requestedOrientation =
            if (locked) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
