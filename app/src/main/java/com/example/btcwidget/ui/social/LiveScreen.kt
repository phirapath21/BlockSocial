package com.example.btcwidget.ui.social

import androidx.compose.foundation.background
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
import androidx.compose.runtime.produceState
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

/** Streams live now (most watched first) and coming up, for the Communities page's LIVE tab. */
@Composable
internal fun LiveList(vm: SocialViewModel, navigate: (NavKey) -> Unit) {
    val profiles by vm.profiles.collectAsState()
    val list by produceState<List<LiveStreams.Live>?>(null) { value = vm.liveStreams() }
    when (val l = list) {
        null -> Spinner(Modifier.padding(top = 30.dp))
        else -> if (l.isEmpty()) EmptyNote("Nobody is streaming on your relays right now.") else LazyColumn(Modifier.fillMaxSize()) {
            items(l, key = { it.address }) { live -> LiveRow(live, profiles[live.host]) { navigate(LiveKey(live.address)) } }
        }
    }
}

@Composable
private fun LiveRow(live: LiveStreams.Live, host: Profile?, onClick: () -> Unit) {
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
    LiveStreams.Status.ENDED -> "Ended"
}

@Composable
private fun LiveBadge(modifier: Modifier = Modifier) {
    Text(
        "LIVE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.clip(RoundedCornerShape(4.dp)).background(LiveRed).padding(horizontal = 5.dp, vertical = 1.dp)
    )
}

/** A live stream: the video pinned at the top, the live chat under it. */
@Composable
fun LiveScreen(vm: SocialViewModel, address: String, navigate: (NavKey) -> Unit, onBack: () -> Unit) {
    val profiles by vm.profiles.collectAsState()
    val loaded by produceState<Any?>(Unit, address) { value = vm.liveStream(address) }
    val live = loaded as? LiveStreams.Live
    var messages by remember { mutableStateOf<List<Event>?>(null) }
    if (live != null) DisposableEffect(live.address) {
        val stop = vm.spaces.watchLiveChat(live) { messages = it }
        onDispose { stop() }
    }
    SpaceChat(
        vm, navigate, onBack,
        title = live?.title ?: "Live",
        note = if (loaded == null) "This stream couldn't be found on your relays." else "Live chat: messages are public.",
        messages = if (loaded == null) emptyList() else messages,
        pinned = live?.let { l -> { LivePlayer(l, profiles[l.host]) { navigate(ProfileKey(l.host)) } } },
        onSend = { text, done -> live?.let { vm.sendLiveChat(it, text) { ok -> done(if (ok) null else "Couldn't send") } } ?: done("Still loading") }
    )
}

/** The stream itself (16:9, sound on), with its title, host and status underneath. */
@Composable
private fun LivePlayer(live: LiveStreams.Live, host: Profile?, onHost: () -> Unit) {
    val context = LocalContext.current
    var muted by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(EInk.Background)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black), contentAlignment = Alignment.Center) {
            val url = live.streaming
            if (live.isLive && url != null) {
                val player = remember(url) { newPlayer(context, url, fullScreen = true, live = true) {}.apply { playWhenReady = true } }
                DisposableEffect(player) { onDispose { player.release() } }
                player.volume = if (muted) 0f else 1f
                VideoSurface(player, Modifier.fillMaxSize())
                LiveBadge(Modifier.align(Alignment.TopStart).padding(10.dp))
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(8.dp).size(40.dp).clip(CircleShape).background(Color(0x99000000))
                        .clickable(onClickLabel = if (muted) "Sound on" else "Mute") { muted = !muted },
                    contentAlignment = Alignment.Center
                ) { Icon(if (muted) Glyphs.SoundOff else Glyphs.SoundOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp)) }
            } else {
                live.image?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                Text(
                    statusLine(live), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x99000000)).padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(host, live.host, size = 36, onClick = onHost)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(live.title, color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text((host?.bestName ?: Profile.shortNpub(live.host)) + " · " + statusLine(live), color = EInk.Caption, fontSize = 12.sp, maxLines = 1)
            }
        }
        live.summary?.let { Text(it, color = EInk.Label, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 14.dp)) }
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
    }
}
