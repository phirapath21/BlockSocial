package com.example.btcwidget.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.btcwidget.LoginKey
import com.example.btcwidget.PackKey
import com.example.btcwidget.ProfileKey
import com.example.btcwidget.nostr.core.FollowPack
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk

/** Follow packs on your relays (Search › Follow packs): people someone recommends, to follow at once. */
@Composable
internal fun FollowPackList(vm: SocialViewModel, navigate: (NavKey) -> Unit) {
    val profiles by vm.profiles.collectAsState()
    val packs by produceKept<List<FollowPack>?>(null) { value = vm.followPacks() }
    when (val l = packs) {
        null -> Spinner(Modifier.padding(top = 30.dp))
        else -> if (l.isEmpty()) EmptyNote(tr("No follow packs found on your relays.")) else LazyColumn(Modifier.fillMaxSize()) {
            items(l, key = { it.address }) { pack ->
                Row(
                    Modifier.fillMaxWidth().clickable { navigate(PackKey(pack.address)) }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // A few faces from the pack, overlapping.
                    Box(Modifier.width((44 + 3 * 14).dp)) {
                        pack.members.take(4).forEachIndexed { i, pk -> Box(Modifier.padding(start = (i * 14).dp)) { Avatar(profiles[pk], pk, size = 44) } }
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(pack.title, color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            tr("{0} people · by ", pack.members.size) + (profiles[pack.owner]?.bestName ?: Profile.shortNpub(pack.owner)),
                            color = EInk.Caption, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        pack.description?.let { Text(it, color = EInk.Label, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
            }
        }
    }
}

/** One pack: its cover, description and people, with Follow all. */
@Composable
fun FollowPackScreen(vm: SocialViewModel, address: String, navigate: (NavKey) -> Unit, onBack: () -> Unit) {
    val account by vm.account.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val following by vm.following.collectAsState()
    val loaded by produceKept<Any?>(Unit, address) { value = vm.followPack(address) }
    val pack = loaded as? FollowPack
    Column(Modifier.fillMaxSize().background(EInk.Background)) {
        SocialTopBar(pack?.title ?: tr("Follow pack"), onBack)
        SocialScaffold(vm, navigate) { actions ->
            when {
                loaded == Unit -> Spinner(Modifier.padding(top = 30.dp))
                pack == null -> EmptyNote(tr("This follow pack couldn't be found on your relays."))
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item(key = "head") {
                        pack.image?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(3f)) }
                        Column(Modifier.padding(16.dp)) {
                            Text(pack.title, color = EInk.Value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            pack.description?.let { Text(it, color = EInk.Label, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp)) }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp).clickable { navigate(ProfileKey(pack.owner)) }) {
                                Avatar(profiles[pack.owner], pack.owner, size = 20)
                                Text(
                                    tr("by ") + (profiles[pack.owner]?.bestName ?: Profile.shortNpub(pack.owner)) + tr(" · {0} people", pack.members.size),
                                    color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp)
                                )
                            }
                            val toFollow = pack.members.filter { it !in following && it != account?.pubkey }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                                if (toFollow.isEmpty()) PillButton(tr("Following all"), filled = false) {}
                                else PillButton(tr("Follow all ({0})", toFollow.size)) {
                                    if (account == null) navigate(LoginKey) else vm.followAll(toFollow)
                                }
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
                    }
                    items(pack.members, key = { it }) { pk ->
                        val p = profiles[pk]
                        val on = pk in following
                        Row(Modifier.fillMaxWidth().clickable { navigate(ProfileKey(pk)) }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(p, pk, size = 44)
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(p?.bestName ?: Profile.shortNpub(pk), color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                p?.about?.let { Text(it, color = EInk.Label, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                            }
                            if (pk != account?.pubkey) Text(
                                if (on) tr("Following") else tr("Follow"), color = if (on) EInk.Value else androidx.compose.ui.graphics.Color.Black,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) EInk.Track else EInk.Value)
                                    .clickable { actions.follow(pk, !on) }.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
                    }
                }
            }
        }
    }
}
