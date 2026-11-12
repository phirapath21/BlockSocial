package com.example.btcwidget.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import coil3.compose.AsyncImage
import com.example.btcwidget.nostr.NostrRepository
import com.example.btcwidget.nostr.core.LiveStreams
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk

/**
 * The column beside the posts on a wide screen, as X has on the right: what's trending on your
 * relays and who's live now. Scrolls on its own; each part shows once it has something.
 */
@Composable
fun SidePanel(vm: SocialViewModel, navigate: (NavKey) -> Unit) {
    val profiles by vm.profiles.collectAsState()
    val trends by produceState<List<NostrRepository.Trend>?>(null) { value = vm.trendingCached() }
    val lives by produceState<List<LiveStreams.Live>?>(null) { value = vm.liveStreamsCached() }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        PanelCard(tr("Trending")) {
            when (val l = trends) {
                null -> NotesSkeleton(2)
                else -> if (l.isEmpty()) Text(tr("Nothing trending on your relays right now."), color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                else l.take(5).forEachIndexed { i, t ->
                    Column(Modifier.fillMaxWidth().clickable { navigate(com.example.btcwidget.HashtagKey(t.tag)) }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(tr("{0} · Trending", i + 1), color = EInk.Caption, fontSize = 12.sp)
                        Text("#" + t.tag, color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(tr("{0} people · {1} posts", compactCount(t.people), compactCount(t.notes)), color = EInk.Caption, fontSize = 12.sp)
                    }
                }
            }
            if (!trends.isNullOrEmpty()) Text(
                tr("Show more"), color = SettingsAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().clickable { navigate(com.example.btcwidget.SearchKey) }.padding(horizontal = 14.dp, vertical = 12.dp)
            )
        }
        val live = lives.orEmpty().filter { it.isLive }.take(3)
        if (live.isNotEmpty()) PanelCard(tr("Live now"), Modifier.padding(top = 14.dp)) {
            live.forEach { l ->
                Row(
                    Modifier.fillMaxWidth().clickable { navigate(com.example.btcwidget.LiveKey(l.address)) }.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.width(76.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(EInk.Track)) {
                        l.image?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        LiveBadge(Modifier.align(Alignment.TopStart).padding(4.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(l.title, color = EInk.Value, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            (profiles[l.host]?.bestName ?: Profile.shortNpub(l.host)) + (l.viewers?.let { " · " + tr("{0} watching", compactCount(it)) } ?: ""),
                            color = EInk.Caption, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/** A rounded card with a bold title, as X's right-column boxes. */
@Composable
private fun PanelCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(EInk.Body).padding(vertical = 6.dp)) {
        Text(title, color = EInk.Value, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
        content()
    }
}
