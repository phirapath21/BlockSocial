package com.example.btcwidget.ui.social

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation3.runtime.NavKey
import com.example.btcwidget.nostr.FeedItem
import com.example.btcwidget.nostr.NoteStats
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Imeta
import com.example.btcwidget.nostr.core.Profile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val Accent = Color(0xFFF7931A)

/**
 * Video posts one at a time, full screen, like Reels or Shorts: swipe up for the next, down
 * for the one before. The video on screen plays with sound and loops; the ones either side
 * are loaded and waiting so a swipe starts at once. Tap to pause, double-tap to like; the
 * buttons on the right are the usual note actions.
 */
@Composable
fun ShortsScreen(vm: SocialViewModel, startEveryone: Boolean, navigate: (NavKey) -> Unit, onBack: (() -> Unit)?) {
    // Following and Everyone side by side: swipe left or right between them (or tap the tabs).
    val feeds = androidx.compose.foundation.pager.rememberPagerState(initialPage = if (startEveryone) 1 else 0) { 2 }
    val scope = rememberCoroutineScope()
    SocialScaffold(vm, navigate) { actions ->
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            androidx.compose.foundation.pager.HorizontalPager(state = feeds, modifier = Modifier.fillMaxSize(), key = { it }) { page ->
                // Only the feed on screen plays; the other waits where it was.
                ShortsPager(vm, everyone = page == 1, actions, active = feeds.currentPage == page)
            }
            FirstTimeTip(
                "shorts-swipe", tr("Swipe up for the next video, left or right to switch feeds. Double-tap to like."),
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = LocalBottomOverlay.current).padding(start = 12.dp, end = 12.dp, bottom = 96.dp)
            )
            ShortsTopBar(
                position = feeds.currentPage + feeds.currentPageOffsetFraction,
                onBack = onBack,
                onSelect = { scope.launch { feeds.animateScrollToPage(it) } }
            )
        }
    }
}

/**
 * How much of the bottom of the screen something else covers (the bottom bar, drawn over the
 * video when Shorts is a tab), so captions and buttons sit above it.
 */
val LocalBottomOverlay = androidx.compose.runtime.compositionLocalOf { 0.dp }

/** Back, and the two feeds as tabs; the orange line slides between them with your swipe. */
@Composable
private fun ShortsTopBar(position: Float, onBack: (() -> Unit)?, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0x88000000), Color.Transparent)))
            .statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // As a tab of its own there's nothing to go back to.
        if (onBack != null) Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = tr("Back"), onClick = onBack),
            contentAlignment = Alignment.Center
        ) { Icon(Glyphs.Back, contentDescription = tr("Back"), tint = Color.White, modifier = Modifier.size(24.dp)) }
        else Spacer(Modifier.width(44.dp))
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row {
                listOf("Following", "Everyone").forEachIndexed { i, label ->
                    // Brighter the closer the swipe is to this tab.
                    val near = (1f - kotlin.math.abs(position - i)).coerceIn(0f, 1f)
                    Text(
                        tr(label), color = Color.White.copy(alpha = 0.6f + 0.4f * near), fontSize = 16.sp,
                        fontWeight = if (near > 0.5f) FontWeight.Bold else FontWeight.Normal, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.width(TAB_WIDTH).clip(RoundedCornerShape(8.dp))
                            .clickable(onClickLabel = tr(label)) { onSelect(i) }.padding(vertical = 6.dp)
                    )
                }
            }
            Box(Modifier.width(TAB_WIDTH * 2).height(3.dp)) {
                Box(
                    Modifier.offset(x = TAB_WIDTH * position.coerceIn(0f, 1f) + (TAB_WIDTH - 24.dp) / 2)
                        .size(width = 24.dp, height = 3.dp).clip(RoundedCornerShape(2.dp)).background(Accent)
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(44.dp))
    }
}

private val TAB_WIDTH = 104.dp

/** A video post and the address of its video. */
private class Short(val note: Event, val url: String)

private fun shortOf(item: FeedItem): Short? {
    if (item.repostedBy != null) return null
    val url = Imeta.videoUrls(item.note).firstOrNull() ?: Imeta.urls(item.note).firstOrNull() ?: return null
    return Short(item.note, url)
}

@Composable
private fun ShortsPager(vm: SocialViewModel, everyone: Boolean, actions: NoteActions, active: Boolean = true) {
    val context = LocalContext.current
    val stats by vm.stats.collectAsState()
    val shorts = remember { mutableStateListOf<Short>() }
    var loading by remember { mutableStateOf(true) }
    var end by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadMore() {
        if (end) return
        loading = true
        scope.launch {
            val until = shorts.lastOrNull()?.note?.createdAt?.minus(1)
            val page = vm.videoFeed(everyone, until).mapNotNull(::shortOf).filter { s -> shorts.none { it.note.id == s.note.id } }
            if (page.isEmpty()) end = true
            shorts.addAll(page)
            loading = false
        }
    }
    LaunchedEffect(Unit) { loadMore() }

    if (shorts.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loading) Spinner()
            else Text(
                if (everyone) tr("No videos on your relays right now.") else tr("No videos from the people you follow yet. Try Everyone."),
                color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(32.dp)
            )
        }
        return
    }

    val pager = rememberPagerState { shorts.size }
    // Sound is one setting for the whole session, as in Reels.
    var muted by rememberSaveable { mutableStateOf(false) }
    var userPaused by remember { mutableStateOf(false) }
    var appVisible by remember { mutableStateOf(true) }
    // Players for the video on screen and its neighbours, so a swipe starts playing at once.
    val players = remember { mutableStateMapOf<Int, ExoPlayer>() }

    // Pause while the app is in the background; the screen stays on while watching.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> appVisible = false
                androidx.lifecycle.Lifecycle.Event.ON_START -> appVisible = true
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        // Only one thing plays sound: a live stream left in the mini player pauses.
        PipVideo.player?.pause()
        onDispose {
            view.keepScreenOn = false
            players.values.forEach { it.release() }
            players.clear()
        }
    }

    val current = pager.currentPage
    LaunchedEffect(current) {
        userPaused = false
        if (current >= shorts.size - 3 && !loading) loadMore()
    }
    LaunchedEffect(current, shorts.size, userPaused, appVisible, muted, active) {
        val keep = (current - 1)..(current + 1)
        players.keys.filter { it !in keep }.forEach { players.remove(it)?.release() }
        keep.filter { it in shorts.indices && it !in players }.forEach { i ->
            players[i] = newPlayer(context, shorts[i].url) {}.apply { seekTo(0) }
        }
        players.forEach { (i, p) ->
            p.volume = if (muted) 0f else 1f
            if (i == current && !userPaused && appVisible && active) p.play()
            else {
                p.pause()
                // A video swiped away starts from the top when it comes back.
                if (i != current) p.seekTo(0)
            }
        }
    }

    VerticalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { shorts[it].note.id }) { page ->
        val short = shorts[page]
        ShortPage(
            short = short,
            player = players[page],
            active = page == current,
            paused = userPaused,
            muted = muted,
            stats = stats[short.note.id] ?: NoteStats(),
            actions = actions,
            onTogglePause = { userPaused = !userPaused },
            onToggleMute = { muted = !muted }
        )
    }
    if (loading) Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = LocalBottomOverlay.current).padding(bottom = 8.dp), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.size(width = 40.dp, height = 3.dp).clip(RoundedCornerShape(2.dp)).background(Accent))
    }
}

@Composable
private fun ShortPage(
    short: Short,
    player: ExoPlayer?,
    active: Boolean,
    paused: Boolean,
    muted: Boolean,
    stats: NoteStats,
    actions: NoteActions,
    onTogglePause: () -> Unit,
    onToggleMute: () -> Unit,
) {
    val note = short.note
    val author = actions.profileOf(note.pubkey)
    // The tap handler outlives recompositions: read the latest like state from here.
    val liked by androidx.compose.runtime.rememberUpdatedState(stats.likedByMe)
    val density = LocalDensity.current
    // The heart that pops where you double-tap.
    var heartAt by remember { mutableStateOf<Offset?>(null) }
    val heartScale = remember { Animatable(0f) }
    var heartKey by remember { mutableStateOf(0) }
    LaunchedEffect(heartKey) {
        if (heartKey == 0) return@LaunchedEffect
        heartScale.snapTo(0.4f)
        heartScale.animateTo(1.15f, tween(160))
        heartScale.animateTo(1f, tween(90))
        delay(380)
        heartScale.animateTo(0f, tween(180))
        heartAt = null
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black).pointerInput(note.id) {
            detectTapGestures(
                onTap = { onTogglePause() },
                onDoubleTap = { at ->
                    heartAt = at
                    heartKey++
                    if (!liked) actions.like(note)
                }
            )
        }
    ) {
        if (player != null) VideoSurface(player, Modifier.fillMaxSize(), inFeed = true)
        else Spinner(Modifier.align(Alignment.Center))

        // Paused: a big play mark in the middle.
        if (active && paused) Box(
            Modifier.align(Alignment.Center).size(72.dp).clip(CircleShape).background(Color(0x66000000)),
            contentAlignment = Alignment.Center
        ) { Icon(Glyphs.Play, contentDescription = tr("Paused"), tint = Color.White, modifier = Modifier.size(44.dp)) }

        heartAt?.let { at ->
            val half = with(density) { 48.dp.toPx() }
            Icon(
                Glyphs.Liked, contentDescription = null, tint = Accent,
                modifier = Modifier.offset { IntOffset((at.x - half).roundToInt(), (at.y - half).roundToInt()) }
                    .size(96.dp).scale(heartScale.value).graphicsLayer { rotationZ = -12f }
            )
        }

        // Dark fade at the bottom so the text reads over any video.
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(260.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000))))
        )

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = LocalBottomOverlay.current).padding(bottom = 14.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            ShortCaption(note, author, actions, Modifier.weight(1f).padding(start = 14.dp, end = 8.dp))
            ShortSideBar(note, author, stats, muted, actions, onToggleMute, Modifier.padding(end = 8.dp))
        }

        if (active && player != null) ShortProgress(player, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = LocalBottomOverlay.current))
    }
}

@Composable
private fun ShortCaption(note: Event, author: Profile?, actions: NoteActions, modifier: Modifier) {
    var expanded by remember(note.id) { mutableStateOf(false) }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { actions.openProfile(note.pubkey) }) {
            DisplayName(author, note.pubkey, Color.White, 16.sp, Modifier.weight(1f, fill = false))
            Text("  · " + relativeTime(note.createdAt), color = Color(0xCCFFFFFF), fontSize = 13.sp, maxLines = 1)
        }
        val text = remember(note.id) { captionOf(note) }
        if (text.isNotBlank()) Text(
            text, color = Color.White, fontSize = 14.sp, lineHeight = 19.sp,
            maxLines = if (expanded) 12 else 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp).clickable(onClickLabel = if (expanded) "Show less" else "Show more") { expanded = !expanded }
        )
    }
}

/** The post's words without the video's own link, or its title (NIP-71) when there are none. */
private fun captionOf(note: Event): String {
    val urls = Imeta.urls(note).toSet()
    val words = note.content.split(Regex("\\s+")).filter { it !in urls }.joinToString(" ").trim()
    if (words.isNotEmpty()) return words
    return note.tags.firstOrNull { it.firstOrNull() == "title" }?.getOrNull(1).orEmpty()
}

@Composable
private fun ShortSideBar(
    note: Event, author: Profile?, stats: NoteStats, muted: Boolean, actions: NoteActions,
    onToggleMute: () -> Unit, modifier: Modifier
) {
    // On its side the screen is short: smaller buttons, closer together, so all of them fit.
    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 520
    CompositionLocalProvider(LocalCompactSide provides compact) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 14.dp)) {
        Box(contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.padding(bottom = 8.dp).clip(CircleShape).background(Color.White).padding(1.5.dp)) {
                Avatar(author, note.pubkey, size = if (compact) 36 else 44, onClick = { actions.openProfile(note.pubkey) })
            }
            if (note.pubkey != actions.me && !actions.isFollowing(note.pubkey)) Box(
                Modifier.size(20.dp).clip(CircleShape).background(Accent)
                    .clickable(onClickLabel = tr("Follow")) { actions.follow(note.pubkey, true) },
                contentAlignment = Alignment.Center
            ) { Text("+", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        }
        SideButton(if (stats.likedByMe) Glyphs.Liked else Glyphs.Like, stats.likes, if (stats.likedByMe) "Liked" else "Like", tint = if (stats.likedByMe) Accent else Color.White) {
            if (!stats.likedByMe) actions.like(note)
        }
        SideButton(Glyphs.Comment, stats.replies, "Reply") { actions.reply(note) }
        SideButton(Glyphs.Repost, stats.reposts, if (stats.repostedByMe) "Reposted" else "Repost", tint = if (stats.repostedByMe) Accent else Color.White) {
            if (!stats.repostedByMe) actions.repost(note)
        }
        SideButton(Glyphs.Zap, (stats.zapMsat / 1000).toInt(), "Zap", tint = Accent) { actions.zap(note) }
        SideButton(Glyphs.Share, null, "Share") { actions.share(note) }
        SideButton(if (muted) Glyphs.SoundOff else Glyphs.SoundOn, null, if (muted) "Turn sound on" else "Mute", onClick = onToggleMute)
    }
    }
}

/** Whether the side buttons are drawn small (a short, sideways screen). */
private val LocalCompactSide = androidx.compose.runtime.compositionLocalOf { false }

@Composable
private fun SideButton(icon: ImageVector, count: Int?, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = label, onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val compact = LocalCompactSide.current
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(if (compact) 24.dp else 30.dp))
        if (count != null && !(compact && count == 0)) Text(if (count > 0) compactCount(count) else "", color = Color.White, fontSize = if (compact) 11.sp else 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** A thin accent line along the bottom: how far through the video is. */
@Composable
private fun ShortProgress(player: ExoPlayer, modifier: Modifier) {
    var progress by remember(player) { mutableFloatStateOf(0f) }
    LaunchedEffect(player) {
        while (true) {
            val d = player.duration
            progress = if (d > 0) (player.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
            delay(100)
        }
    }
    Box(modifier.fillMaxWidth().height(2.dp).background(Color(0x33FFFFFF))) {
        Box(Modifier.fillMaxWidth(progress).height(2.dp).background(Accent))
    }
}
