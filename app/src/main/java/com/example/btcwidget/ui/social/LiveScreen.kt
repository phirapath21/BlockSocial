package com.example.btcwidget.ui.social

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import com.example.btcwidget.LoginKey
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import coil3.compose.AsyncImage
import com.example.btcwidget.LiveKey
import com.example.btcwidget.ProfileKey
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.LiveStreams
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk

private val LiveRed = Color(0xFFE5534B)

@Composable
private fun RoundControl(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Color(0x99000000)).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp)) }
}

/** Streams live now (most watched first) and coming up, for the Communities page's LIVE tab. */
@Composable
internal fun LiveList(vm: SocialViewModel, navigate: (NavKey) -> Unit) {
    val profiles by vm.profiles.collectAsState()
    val list by produceKept<List<LiveStreams.Live>?>(null) { value = vm.liveStreams() }
    when (val l = list) {
        null -> Spinner(Modifier.padding(top = 30.dp))
        else -> if (l.isEmpty()) EmptyNote(tr("Nobody is streaming on your relays right now.")) else LazyColumn(Modifier.fillMaxSize()) {
            items(l, key = { it.address }) { live -> LiveRow(live, profiles[live.host]) { navigate(LiveKey(live.address)) } }
        }
    }
}

@Composable
internal fun LiveRow(live: LiveStreams.Live, host: Profile?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(120.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(EInk.Body)) {
            live.image?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            if (live.isLive) LiveBadge(Modifier.align(Alignment.TopStart).padding(6.dp))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(live.title, color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(host?.bestName ?: Profile.shortNpub(live.host), color = EInk.Caption, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(statusLine(live), color = if (live.isLive) LiveRed else EInk.Caption, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
}

private fun statusLine(live: LiveStreams.Live): String = when (live.status) {
    LiveStreams.Status.LIVE -> "● LIVE" + (live.viewers?.let { " · $it watching" } ?: "")
    LiveStreams.Status.PLANNED -> "Starts " + (live.starts?.let { fullTimestamp(it) } ?: "soon")
    LiveStreams.Status.ENDED -> if (live.recording != null) "Ended · Replay" else "Ended"
}

@Composable
internal fun LiveBadge(modifier: Modifier = Modifier) {
    Text(
        "LIVE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.clip(RoundedCornerShape(4.dp)).background(LiveRed).padding(horizontal = 5.dp, vertical = 1.dp)
    )
}

/**
 * A live stream, as on YouTube: the video at the top (tap for controls, drag down for the mini
 * player, a button for full screen with the chat over it), the title, viewers, the streamer
 * with Follow and Zap, then the live chat with zaps in it. Kept up to date while open: if the
 * stream ends, it says so (and plays the replay when there is one).
 */
@Composable
fun LiveScreen(vm: SocialViewModel, address: String, navigate: (NavKey) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val profiles by vm.profiles.collectAsState()
    val following by vm.following.collectAsState()
    val account by vm.account.collectAsState()
    val nwc by vm.nwc.collectAsState()
    val loaded by produceKept<Any?>(Unit, address) { value = vm.liveStream(address) }
    // Asked again now and then while open: title, viewers, and whether it's still live.
    var fresh by remember(address) { mutableStateOf<LiveStreams.Live?>(null) }
    LaunchedEffect(address) {
        while (true) {
            kotlinx.coroutines.delay(REFRESH_MS)
            vm.liveStream(address)?.let { fresh = it }
        }
    }
    val live = fresh ?: loaded as? LiveStreams.Live
    // Marked before any player is made, so the mini player doesn't show this stream meanwhile
    // (it used to, for a frame, and took the picture: the stream stayed black).
    remember(address) { PipVideo.liveScreenOpen = address }
    DisposableEffect(address) {
        PipVideo.liveScreenOpen = address
        onDispose { if (PipVideo.liveScreenOpen == address) PipVideo.liveScreenOpen = null }
    }
    // It ended while you watched: let the stream go (the replay takes over if there is one).
    LaunchedEffect(live?.isLive) { if (live != null && !live.isLive && PipVideo.live?.address == live.address) PipVideo.release() }
    var messages by rememberKeptState<List<Event>?> { null }
    if (live != null) DisposableEffect(live.address) {
        val stop = vm.spaces.watchLiveChat(live) { messages = it }
        onDispose { stop() }
    }
    // The replay of an ended stream: one player for this screen, full screen or not.
    val replay = live?.takeIf { !it.isLive }?.recording
    val replayPlayer = replay?.let { rec ->
        remember(rec) { newPlayer(context, rec, fullScreen = true) {}.apply { repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF; playWhenReady = true } }
    }
    if (replayPlayer != null) DisposableEffect(replayPlayer) { onDispose { replayPlayer.release() } }

    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var chatOver by rememberSaveable { mutableStateOf(true) }
    val canFullscreen = live != null && (live.isLive && live.streaming != null || replayPlayer != null)
    FullscreenMode(fullscreen && canFullscreen)
    androidx.activity.compose.BackHandler(enabled = fullscreen) { fullscreen = false }

    var zapping by remember { mutableStateOf(false) }
    if (zapping && live != null) ZapDialog(
        recipientName = profiles[live.host]?.bestName ?: tr("the streamer"),
        inApp = nwc != null,
        onDismiss = { zapping = false },
        onZap = { sats, comment ->
            zapping = false
            vm.zap(live.host, sats, live.event, comment, address = live.address) { invoice -> payInvoice(context, invoice) }
        }
    )
    val onZap: () -> Unit = { if (account == null) navigate(LoginKey) else zapping = true }

    val chat: @Composable (pinned: (@Composable () -> Unit)?, overVideo: Boolean) -> Unit = { pinned, overVideo ->
        SpaceChat(
            vm, navigate, onBack,
            title = live?.title ?: tr("Live"),
            note = if (loaded == null) tr("This stream couldn't be found on your relays.") else tr("Live chat: messages and zaps are public."),
            messages = if (loaded == null) emptyList() else messages,
            pinned = pinned,
            hosts = setOfNotNull(live?.host),
            showTopBar = !overVideo,
            onSend = { text, done -> live?.let { vm.sendLiveChat(it, text) { ok -> done(if (ok) null else tr("Couldn't send")) } } ?: done(tr("Still loading")) }
        )
    }

    if (fullscreen && canFullscreen && live != null) {
        // Full screen on its side, the chat (if you want it) see-through over the right side.
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            LiveVideo(
                live, profiles[live.host], replayPlayer, Modifier.fillMaxSize(),
                fullscreen = true, onFullscreen = { fullscreen = false }, onMinimize = {},
                chatShown = chatOver, onChat = { chatOver = !chatOver }
            )
            if (chatOver) Box(
                Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.36f)
                    .background(Color(0xCC000000)).displayCutoutPadding().navigationBarsPadding()
            ) { chat(null, true) }
        }
        return
    }

    val player: (@Composable () -> Unit)? = live?.let { l ->
        {
            LivePlayer(
                l, profiles[l.host], replayPlayer,
                isMe = account?.pubkey == l.host, isFollowing = l.host in following,
                onFollow = { if (account == null) navigate(LoginKey) else vm.follow(l.host, l.host !in following) },
                onZap = onZap,
                onFullscreen = { fullscreen = true },
                onMinimize = onBack,
                onHost = { navigate(ProfileKey(l.host)) }
            )
        }
    }
    // On its side (or a wide screen), as on Twitch or YouTube: the stream on the left, the chat
    // beside it, instead of the chat squeezed under the video.
    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600
    if (wide && player != null) {
        Row(Modifier.fillMaxSize().background(EInk.Background).displayCutoutPadding()) {
            Column(Modifier.weight(1.6f).fillMaxHeight().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())) { player() }
            androidx.compose.material3.VerticalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
            Box(Modifier.weight(1f).fillMaxHeight()) { chat(null, false) }
        }
    } else chat(player, false)
}

/** The video and what's under it: title, viewers, the streamer with Follow and Zap, the summary. */
@Composable
private fun LivePlayer(
    live: LiveStreams.Live, host: Profile?, replayPlayer: androidx.media3.exoplayer.ExoPlayer?,
    isMe: Boolean, isFollowing: Boolean,
    onFollow: () -> Unit, onZap: () -> Unit, onFullscreen: () -> Unit, onMinimize: () -> Unit, onHost: () -> Unit
) {
    // Typing in the chat: a shorter video and no details, so the chat and its box fit above the keyboard.
    val compact = LocalChatCompact.current
    Column(Modifier.fillMaxWidth().background(EInk.Background)) {
        LiveVideo(
            live, host, replayPlayer,
            Modifier.fillMaxWidth().then(if (compact) Modifier.height(COMPACT_VIDEO) else Modifier.aspectRatio(16f / 9f)),
            fullscreen = false, onFullscreen = onFullscreen, onMinimize = onMinimize
        )
        if (!compact) LiveInfo(live, host, isMe, isFollowing, onFollow, onZap, onHost)
    }
}

/**
 * The stream (or its replay) in a box: drag it down for the mini player (when not full screen),
 * tap for the controls, which hold picture-in-picture, full screen and sound. Shows a spinner
 * while it loads and Try again when it can't play.
 */
@Composable
private fun LiveVideo(
    live: LiveStreams.Live, host: Profile?, replayPlayer: androidx.media3.exoplayer.ExoPlayer?, modifier: Modifier,
    fullscreen: Boolean, onFullscreen: () -> Unit, onMinimize: () -> Unit,
    chatShown: Boolean = false, onChat: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val url = live.streaming
    val drag = rememberDragToMinimize(enabled = !fullscreen && live.isLive && url != null, onMinimize = onMinimize)
    Box(modifier.background(Color.Black).then(drag), contentAlignment = Alignment.Center) {
        val screenButtons: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
            if (onChat != null) {
                RoundControl(Glyphs.Chat, if (chatShown) "Hide chat" else "Show chat", onChat)
                Spacer(Modifier.width(8.dp))
            }
            RoundControl(if (fullscreen) Glyphs.ExitFullscreen else Glyphs.Fullscreen, if (fullscreen) "Exit full screen" else "Full screen", onFullscreen)
            Spacer(Modifier.width(8.dp))
        }
        if (live.isLive && url != null) {
            val info = PipVideo.Live(live.address, live.title, host?.bestName ?: Profile.shortNpub(live.host))
            val player = remember(url) {
                PipVideo.acquireLive(context, url, info) { ctx, u -> newPlayer(ctx, u, fullScreen = true, live = true) {}.apply { playWhenReady = true } }
            }
            if (!PipVideo.inPip) {
                VideoSurface(player, Modifier.fillMaxSize())
                PlayerStatus(player, Modifier.fillMaxSize())
                // Tap for YouTube-style controls: ±10 s, play/pause, LIVE, and the time bar.
                StreamControls(player, live = true, onTogglePlay = { PipVideo.togglePlay(catchUp = false) }) {
                    screenButtons()
                    if (!fullscreen) PipVideo.requestPip?.let { pip -> RoundControl(Glyphs.Pip, "Picture-in-picture", pip); Spacer(Modifier.width(8.dp)) }
                    RoundControl(if (PipVideo.muted) Glyphs.SoundOff else Glyphs.SoundOn, if (PipVideo.muted) "Sound on" else "Mute") { PipVideo.mute(!PipVideo.muted) }
                }
            }
            LiveBadge(Modifier.align(Alignment.TopStart).padding(10.dp))
        } else if (replayPlayer != null) {
            // Ended, with a recording: watch it again, with the whole time bar to drag.
            var soundOff by remember(replayPlayer) { mutableStateOf(replayPlayer.volume == 0f) }
            VideoSurface(replayPlayer, Modifier.fillMaxSize())
            PlayerStatus(replayPlayer, Modifier.fillMaxSize())
            StreamControls(replayPlayer, live = false) {
                screenButtons()
                RoundControl(if (soundOff) Glyphs.SoundOff else Glyphs.SoundOn, if (soundOff) "Sound on" else "Mute") {
                    soundOff = !soundOff; replayPlayer.volume = if (soundOff) 0f else 1f
                }
            }
            Text(
                tr("REPLAY"), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x99000000)).padding(horizontal = 5.dp, vertical = 1.dp)
            )
        } else {
            // Not playing: the stream's picture, dimmed, with what's going on.
            live.image?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            Box(Modifier.fillMaxSize().background(Color(0x99000000)))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when (live.status) {
                        LiveStreams.Status.PLANNED -> tr("Starting soon")
                        else -> tr("This stream has ended")
                    },
                    color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold
                )
                Text(statusLine(live), color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Title, viewers, the streamer with Follow and Zap, and the summary (tap more to read it all). */
@Composable
private fun LiveInfo(live: LiveStreams.Live, host: Profile?, isMe: Boolean, isFollowing: Boolean, onFollow: () -> Unit, onZap: () -> Unit, onHost: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(live.title, color = EInk.Value, fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (live.isLive) {
                PulsingDot()
                Text(
                    live.viewers?.let { tr("{0} watching", compactCount(it)) } ?: tr("Live now"),
                    color = EInk.Label, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp)
                )
            } else Text(statusLine(live), color = EInk.Caption, fontSize = 13.sp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(host, live.host, size = 38, onClick = onHost)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp).clickable(onClick = onHost)) {
                DisplayName(host, live.host, EInk.Value, 15.sp)
                Text(handleText(host, live.host), color = EInk.Caption, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!isMe) {
                Text(
                    if (isFollowing) tr("Following") else tr("Follow"),
                    color = if (isFollowing) EInk.Value else Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .then(if (isFollowing) Modifier.border(1.dp, EInk.BodyStroke, RoundedCornerShape(50)) else Modifier.background(EInk.Value))
                        .clickable(onClick = onFollow).padding(horizontal = 14.dp, vertical = 7.dp)
                )
                Row(
                    Modifier.padding(start = 8.dp).clip(RoundedCornerShape(50)).background(SettingsAccent)
                        .clickable(onClickLabel = tr("Zap the streamer"), onClick = onZap).padding(start = 10.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Glyphs.Zap, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Text(tr("Zap"), color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        live.summary?.let { summary ->
            var expanded by remember(live.address) { mutableStateOf(false) }
            var cut by remember(live.address) { mutableStateOf(false) }
            Column(
                Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(12.dp)).background(EInk.Body)
                    .clickable { expanded = !expanded }.padding(12.dp)
            ) {
                Text(
                    summary, color = EInk.Value, fontSize = 14.sp, lineHeight = 19.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!expanded) cut = it.hasVisualOverflow }
                )
                if (cut || expanded) Text(
                    if (expanded) tr("Show less") else tr("Show more"), color = SettingsAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
}

/** A small red dot that gently pulses, beside "watching". */
@Composable
private fun PulsingDot() {
    val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "live")
        .animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "dot")
    Box(Modifier.size(8.dp).graphicsLayer { alpha = pulse }.clip(CircleShape).background(LiveRed))
}

/** How often an open stream is asked again (title, viewers, ended). */
private const val REFRESH_MS = 45_000L

/** The stream's height while the keyboard is up. */
private val COMPACT_VIDEO = 150.dp
