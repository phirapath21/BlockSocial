package com.example.btcwidget.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.example.btcwidget.ChannelKey
import com.example.btcwidget.CommunityKey
import com.example.btcwidget.GroupKey
import com.example.btcwidget.LoginKey
import com.example.btcwidget.nostr.FeedItem
import com.example.btcwidget.nostr.NoteStats
import com.example.btcwidget.nostr.core.Communities
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.nostr.core.PublicChat
import com.example.btcwidget.nostr.core.RelayGroups
import com.example.btcwidget.ui.main.EInk
import com.example.btcwidget.ui.main.EInkChip

/** Communities (NIP-72), public chats (NIP-28) and groups (NIP-29), in the side bar. */
@Composable
fun SpacesScreen(vm: SocialViewModel, navigate: (NavKey) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().background(EInk.Background)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("COMMUNITIES", "CHANNELS", "GROUPS", "● LIVE").forEachIndexed { i, t -> EInkChip(text = t, selected = tab == i, onClick = { tab = i }) }
        }
        HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
        when (tab) {
            0 -> CommunityList(vm, navigate)
            1 -> ChannelList(vm, navigate)
            2 -> GroupList(vm, navigate)
            else -> LiveList(vm, navigate)
        }
    }
}

@Composable
private fun CommunityList(vm: SocialViewModel, navigate: (NavKey) -> Unit) {
    val joined by vm.spaces.joined.collectAsState()
    val list by produceState<List<Communities.Community>?>(null) { value = vm.communities() }
    when (val l = list) {
        null -> Spinner(Modifier.padding(top = 30.dp))
        else -> if (l.isEmpty()) EmptyNote(tr("No communities found on your relays.")) else LazyColumn(Modifier.fillMaxSize()) {
            items(l, key = { it.coordinate }) { c ->
                SpaceRow(c.image, c.name, c.description, if (c.coordinate in joined) "Joined" else null) { navigate(CommunityKey(c.coordinate)) }
            }
        }
    }
}

@Composable
private fun ChannelList(vm: SocialViewModel, navigate: (NavKey) -> Unit) {
    val list by produceState<List<PublicChat.Channel>?>(null) { value = vm.channels() }
    Column {
        Text(
            tr("Public chats: anyone can read and write. Messages are public and permanent."),
            color = EInk.Caption, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
        when (val l = list) {
            null -> Spinner(Modifier.padding(top = 30.dp))
            else -> if (l.isEmpty()) EmptyNote(tr("No public chats found on your relays.")) else LazyColumn(Modifier.fillMaxSize()) {
                items(l, key = { it.id }) { ch -> SpaceRow(ch.picture, ch.name, ch.about, null) { navigate(ChannelKey(ch.id)) } }
            }
        }
    }
}

@Composable
private fun GroupList(vm: SocialViewModel, navigate: (NavKey) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(vm.spaces.savedGroups()) }
    Column(Modifier.fillMaxSize()) {
        Text(
            tr("Groups live on one relay, which decides who can join and post. Paste a group address such as groups.0xchat.com'abc."),
            color = EInk.Caption, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it.trim(); error = null },
                placeholder = { Text(tr("host'group-id")) }, singleLine = true, isError = error != null,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EInk.Value, unfocusedBorderColor = EInk.BodyStroke, focusedTextColor = EInk.Value, unfocusedTextColor = EInk.Value, cursorColor = EInk.Value),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            EInkChip(text = "OPEN", selected = input.isNotEmpty(), onClick = {
                val a = RelayGroups.parse(input)
                if (a == null) error = tr("That isn't a group address") else {
                    vm.spaces.rememberGroup(a); saved = vm.spaces.savedGroups(); input = ""
                    navigate(GroupKey(a.toString()))
                }
            })
        }
        error?.let { Text(it, color = EInk.Alert, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) }
        LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(saved, key = { it.toString() }) { a ->
                Row(Modifier.fillMaxWidth().clickable { navigate(GroupKey(a.toString())) }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(a.toString(), color = EInk.Value, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("✕", color = EInk.Caption, fontSize = 14.sp, modifier = Modifier.clickable(onClickLabel = tr("Remove")) { vm.spaces.forgetGroup(a); saved = vm.spaces.savedGroups() }.padding(6.dp))
                }
                HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
            }
        }
    }
}

@Composable
private fun SpaceRow(image: String?, name: String, about: String?, badge: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(EInk.Body), contentAlignment = Alignment.Center) {
            if (image != null) AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Text(name.take(1).uppercase(), color = EInk.Value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                badge?.let { Text("  · $it", color = EInk.Caption, fontSize = 12.sp) }
            }
            about?.let { Text(it, color = EInk.Label, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
}

/** A community: its description, Join, and approved posts (or every post, to moderate or browse). */
@Composable
fun CommunityScreen(vm: SocialViewModel, coordinate: String, navigate: (NavKey) -> Unit, onBack: () -> Unit) {
    val stats by vm.stats.collectAsState()
    val account by vm.account.collectAsState()
    val joined by vm.spaces.joined.collectAsState()
    val community by produceState<Communities.Community?>(null, coordinate) { value = vm.community(coordinate) }
    var pending by rememberSaveable { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    val posts by produceState<List<FeedItem>?>(null, community, pending, reload) {
        value = null
        value = community?.let { vm.communityPosts(it, pending) } ?: emptyList()
    }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(EInk.Background)) {
        SocialTopBar(community?.name ?: tr("Community"), onBack)
        SocialScaffold(vm, navigate) { actions ->
            val c = community
            if (c == null) { Spinner(Modifier.padding(top = 40.dp)); return@SocialScaffold }
            NoteList(
                items = posts.orEmpty(), stats = stats, actions = actions,
                header = {
                    Column(Modifier.padding(14.dp)) {
                        c.image?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp).clip(RoundedCornerShape(12.dp))) }
                        Text(c.name, color = EInk.Value, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                        c.description?.let { Text(it, color = EInk.Label, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)) }
                        Text(tr("Moderated by {0} people. Posts appear once a moderator approves them.", (c.moderators + c.owner).size), color = EInk.Caption, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val isJoined = c.coordinate in joined
                            EInkChip(text = if (isJoined) tr("JOINED ✓") else "JOIN", selected = !isJoined, onClick = {
                                if (account == null) navigate(LoginKey) else vm.setCommunityJoined(c, !isJoined)
                            })
                            EInkChip(text = if (pending) tr("ALL POSTS") else tr("APPROVED"), selected = pending, onClick = { pending = !pending })
                        }
                        if (account != null) Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = text, onValueChange = { text = it }, placeholder = { Text(tr("Post to {0}", c.name)) }, maxLines = 4,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EInk.Value, unfocusedBorderColor = EInk.BodyStroke, focusedTextColor = EInk.Value, unfocusedTextColor = EInk.Value, cursorColor = EInk.Value),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            PillButton(if (sending) "…" else tr("Post"), filled = text.isNotBlank() && !sending) {
                                if (text.isBlank() || sending) return@PillButton
                                sending = true
                                vm.postToCommunity(c, text) { ok -> sending = false; if (ok) { text = ""; reload++ } }
                            }
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
                    when {
                        posts == null -> Spinner()
                        posts!!.isEmpty() -> EmptyNote(if (pending) tr("No posts yet.") else tr("No approved posts yet. Try ALL POSTS."))
                    }
                }
            )
        }
    }
}

/** A public chat channel (NIP-28). */
@Composable
fun ChannelScreen(vm: SocialViewModel, id: String, navigate: (NavKey) -> Unit, onBack: () -> Unit) {
    val channel by produceState<PublicChat.Channel?>(null, id) { value = vm.channel(id) }
    var messages by remember { mutableStateOf<List<Event>?>(null) }
    val ch = channel
    if (ch != null) DisposableEffect(ch.id) {
        val stop = vm.spaces.watchChannel(ch) { messages = it }
        onDispose { stop() }
    }
    SpaceChat(
        vm, navigate, onBack,
        title = ch?.name ?: tr("Public chat"),
        note = tr("Public chat: everyone can read this, and messages can't be deleted from every relay."),
        messages = messages,
        onSend = { text, done -> ch?.let { vm.sendToChannel(it, text) { ok -> done(if (ok) null else tr("Couldn't send")) } } ?: done(tr("Still loading")) }
    )
}

/** A relay-based group (NIP-29). */
@Composable
fun GroupScreen(vm: SocialViewModel, address: String, navigate: (NavKey) -> Unit, onBack: () -> Unit) {
    val a = remember(address) { RelayGroups.parse(address) }
    if (a == null) { SocialTopBar(tr("Group"), onBack); EmptyNote(tr("That isn't a group address.")); return }
    val group by produceState<RelayGroups.Group?>(null, a) { value = vm.group(a) }
    var messages by remember { mutableStateOf<List<Event>?>(null) }
    DisposableEffect(a) {
        val stop = vm.spaces.watchGroup(a) { messages = it }
        onDispose { stop() }
    }
    SpaceChat(
        vm, navigate, onBack,
        title = group?.name ?: a.id,
        note = (group?.about?.let { "$it\n" } ?: "") + tr("Group on {0}. The relay sees your key when you join or post.", a.relay.removePrefix("wss://")) +
            if (group?.closed == true) " Joining needs approval." else "",
        messages = messages,
        action = "JOIN" to { vm.joinGroup(a) },
        onSend = { text, done -> vm.sendToGroup(a, text, done) }
    )
}

/** Shared chat layout for channels and groups: messages with names, and a composer. */
@Composable
internal fun SpaceChat(
    vm: SocialViewModel,
    navigate: (NavKey) -> Unit,
    onBack: () -> Unit,
    title: String,
    note: String,
    messages: List<Event>?,
    action: Pair<String, () -> Unit>? = null,
    /** Shown above the messages and kept in view (a live stream's video). */
    pinned: (@Composable () -> Unit)? = null,
    onSend: (String, (String?) -> Unit) -> Unit
) {
    val account by vm.account.collectAsState()
    val profiles by vm.profiles.collectAsState()
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(messages?.size) { messages?.let { if (it.isNotEmpty()) listState.animateScrollToItem(it.size) } }
    Column(Modifier.fillMaxSize().background(EInk.Background).imePadding()) {
        SocialTopBar(title, onBack)
        SocialScaffold(vm, navigate) { _ ->
            Column(Modifier.fillMaxSize()) {
                pinned?.invoke()
                LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
                    item(key = "head") {
                        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            Text(note, color = EInk.Caption, fontSize = 12.sp)
                            action?.let { (label, run) ->
                                if (account != null) Row(Modifier.padding(top = 6.dp)) { EInkChip(text = label, selected = false, onClick = run) }
                            }
                        }
                    }
                    if (messages == null) item(key = "loading") { Spinner(Modifier.padding(top = 20.dp)) }
                    else if (messages.isEmpty()) item(key = "empty") { EmptyNote(tr("No messages yet.")) }
                    else items(messages, key = { it.id }) { m ->
                        val mine = m.pubkey == account?.pubkey
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                            Avatar(profiles[m.pubkey], m.pubkey, size = 30, onClick = { navigate(com.example.btcwidget.ProfileKey(m.pubkey)) })
                            Column(Modifier.padding(start = 8.dp)) {
                                Row {
                                    Text(if (mine) tr("You") else profiles[m.pubkey]?.bestName ?: Profile.shortNpub(m.pubkey), color = EInk.Value, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("  " + relativeTime(m.createdAt), color = EInk.Caption, fontSize = 12.sp)
                                }
                                Text(m.content, color = EInk.Value, fontSize = 15.sp)
                            }
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
                error?.let { Text(it, color = EInk.Alert, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
                if (account == null) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.Center) { PillButton(tr("Log in to chat")) { navigate(LoginKey) } }
                } else Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text, onValueChange = { text = it; error = null }, placeholder = { Text(tr("Message")) },
                        shape = RoundedCornerShape(24.dp), maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EInk.Value, unfocusedBorderColor = EInk.BodyStroke, focusedTextColor = EInk.Value, unfocusedTextColor = EInk.Value, cursorColor = EInk.Value),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    PillButton(if (sending) "…" else tr("Send"), filled = text.isNotBlank() && !sending) {
                        if (text.isBlank() || sending) return@PillButton
                        sending = true
                        val outgoing = text
                        text = ""
                        onSend(outgoing) { e -> sending = false; if (e != null) { error = e; text = outgoing } }
                    }
                }
            }
        }
    }
}
