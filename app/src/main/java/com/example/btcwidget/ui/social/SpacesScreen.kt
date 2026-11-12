package com.example.btcwidget.ui.social

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.ime
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
    val list by produceKept<List<Communities.Community>?>(null) { value = vm.communities() }
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
    val list by produceKept<List<PublicChat.Channel>?>(null) { value = vm.channels() }
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
    val community by produceKept<Communities.Community?>(null, coordinate) { value = vm.community(coordinate) }
    var pending by rememberSaveable { mutableStateOf(false) }
    var reload by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    val posts by produceKept<List<FeedItem>?>(null, community, pending, reload) {
        value = null
        value = community?.let { vm.communityPosts(it, pending) } ?: emptyList()
    }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val sendHaptics = rememberHaptics()
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
                                sendHaptics.confirm()
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
    val channel by produceKept<PublicChat.Channel?>(null, id) { value = vm.channel(id) }
    var messages by rememberKeptState<List<Event>?> { null }
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
    val group by produceKept<RelayGroups.Group?>(null, a) { value = vm.group(a) }
    var messages by rememberKeptState<List<Event>?> { null }
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

/**
 * Shared chat layout for live streams, channels and groups, in the style of YouTube Live chat:
 * compact rows (picture, name, words), the host marked in orange, zaps as orange cards with the
 * top zappers above, "↓ New messages" when you've scrolled up, and a round box to type in.
 */
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
    /** People to mark as Host (a live stream's streamer). */
    hosts: Set<String> = emptySet(),
    /** False when drawn over a full-screen video: no title bar, see-through. */
    showTopBar: Boolean = true,
    /** 0..1 while the pinned video is dragged down to the mini player: everything else fades with it. */
    fade: () -> Float = { 0f },
    onSend: (String, (String?) -> Unit) -> Unit
) {
    val account by vm.account.collectAsState()
    val profiles by vm.profiles.collectAsState()
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val sendHaptics = rememberHaptics()
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // The keyboard is up: what's pinned above the chat (a live stream) shrinks so the box you
    // type in and the newest messages stay on screen.
    val keyboardOpen = androidx.compose.foundation.layout.WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    // Following the newest messages unless you've scrolled up to read; then a pill counts new ones.
    val atEnd by remember { androidx.compose.runtime.derivedStateOf { !listState.canScrollForward } }
    var unseen by remember { mutableStateOf(0) }
    var lastCount by remember { mutableStateOf(0) }
    LaunchedEffect(messages?.size, keyboardOpen) {
        val n = messages?.size ?: return@LaunchedEffect
        if (n == 0) return@LaunchedEffect
        if (atEnd || lastCount == 0 || keyboardOpen) { listState.animateScrollToItem(n); unseen = 0 } else unseen += n - lastCount
        lastCount = n
    }
    LaunchedEffect(atEnd) { if (atEnd) unseen = 0 }
    val zaps = remember(messages) {
        messages.orEmpty().filter { it.kind == com.example.btcwidget.nostr.core.Kind.ZAP_RECEIPT }.mapNotNull { com.example.btcwidget.nostr.core.Zaps.parseReceipt(it) }
    }
    val fill = if (showTopBar) EInk.Background else androidx.compose.ui.graphics.Color.Transparent
    Column(Modifier.fillMaxSize().background(fill).imePadding()) {
        if (showTopBar) Box(Modifier.graphicsLayer { alpha = 1f - fade() }) { SocialTopBar(title, onBack) }
        SocialScaffold(vm, navigate) { _ ->
            Column(Modifier.fillMaxSize()) {
                androidx.compose.runtime.CompositionLocalProvider(LocalChatCompact provides keyboardOpen) { pinned?.invoke() }
                Column(Modifier.weight(1f).fillMaxWidth().graphicsLayer { alpha = 1f - fade() }) {
                if (zaps.isNotEmpty()) TopZaps(zaps, profiles)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                        item(key = "head") {
                            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                                Text(note, color = EInk.Caption, fontSize = 12.sp)
                                action?.let { (label, run) ->
                                    if (account != null) Row(Modifier.padding(top = 6.dp)) { ChoicePill(label, selected = false) { run() } }
                                }
                            }
                        }
                        if (messages == null) item(key = "loading") { NotesSkeleton(3) }
                        else if (messages.isEmpty()) item(key = "empty") { EmptyNote(tr("No messages yet. Say hi!")) }
                        else items(messages, key = { it.id }) { m ->
                            if (m.kind == com.example.btcwidget.nostr.core.Kind.ZAP_RECEIPT) {
                                com.example.btcwidget.nostr.core.Zaps.parseReceipt(m)?.let { z -> ZapChatCard(z, profiles) { pk -> navigate(com.example.btcwidget.ProfileKey(pk)) } }
                            } else ChatRow(
                                m, profiles[m.pubkey], mine = m.pubkey == account?.pubkey, host = m.pubkey in hosts,
                                onProfile = { navigate(com.example.btcwidget.ProfileKey(m.pubkey)) }
                            )
                        }
                    }
                    // Scrolled up and new messages came: a pill to jump down to them.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = unseen > 0 && !atEnd,
                        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it },
                        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
                    ) {
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(SettingsAccent)
                                .clickable(onClickLabel = tr("Show new messages")) { scope.launch { listState.animateScrollToItem(messages?.size ?: 0) }; unseen = 0 }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.Icon(Glyphs.ArrowDown, contentDescription = null, tint = androidx.compose.ui.graphics.Color.Black, modifier = Modifier.size(16.dp))
                            Text(
                                tr("{0} new message{1}", unseen, plural(unseen)), color = androidx.compose.ui.graphics.Color.Black,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
                error?.let { Text(it, color = EInk.Alert, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
                if (account == null) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.Center) { PillButton(tr("Log in to chat")) { navigate(LoginKey) } }
                } else Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
                    account?.let { Box(Modifier.padding(bottom = 6.dp)) { Avatar(profiles[it.pubkey], it.pubkey, size = 30) } }
                    Box(
                        Modifier.weight(1f).padding(horizontal = 8.dp).clip(RoundedCornerShape(22.dp)).background(EInk.Body)
                            .padding(horizontal = 16.dp, vertical = 11.dp)
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = text, onValueChange = { text = it; error = null }, maxLines = 4,
                            textStyle = androidx.compose.ui.text.TextStyle(color = EInk.Value, fontSize = 15.sp),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(SettingsAccent),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { field ->
                                Box {
                                    if (text.isEmpty()) Text(tr("Chat…"), color = EInk.Caption, fontSize = 15.sp)
                                    field()
                                }
                            }
                        )
                    }
                    val ready = text.isNotBlank() && !sending
                    Box(
                        Modifier.size(42.dp).clip(CircleShape).background(if (ready || sending) SettingsAccent else EInk.Track)
                            .clickable(enabled = ready, onClickLabel = tr("Send")) {
                                sending = true
                                sendHaptics.confirm()
                                val outgoing = text
                                text = ""
                                onSend(outgoing) { e -> sending = false; if (e != null) { error = e; text = outgoing } }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (sending) androidx.compose.material3.CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        else androidx.compose.material3.Icon(Glyphs.Send, contentDescription = tr("Send"), tint = if (ready) androidx.compose.ui.graphics.Color.Black else EInk.Caption, modifier = Modifier.size(20.dp))
                    }
                }
                }
            }
        }
    }
}

/** One chat message: picture, name (orange with HOST for the host), time and words. */
@Composable
private fun ChatRow(m: Event, author: Profile?, mine: Boolean, host: Boolean, onProfile: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Avatar(author, m.pubkey, size = 28, onClick = onProfile)
        Column(Modifier.padding(start = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (mine) tr("You") else author?.bestName ?: Profile.shortNpub(m.pubkey),
                    color = if (host) SettingsAccent else EInk.Label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                )
                if (host) Text(
                    tr("HOST"), color = androidx.compose.ui.graphics.Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(4.dp)).background(SettingsAccent).padding(horizontal = 4.dp, vertical = 1.dp)
                )
                Text("  " + relativeTime(m.createdAt), color = EInk.Caption, fontSize = 11.sp)
            }
            Text(m.content, color = EInk.Value, fontSize = 15.sp, lineHeight = 20.sp)
        }
    }
}

/** A zap to the stream, like a Super Chat: an orange card with who, how much and what they said. */
@Composable
private fun ZapChatCard(z: com.example.btcwidget.nostr.core.Zaps.Receipt, profiles: Map<String, Profile>, onProfile: (String) -> Unit) {
    val sender = z.sender
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(14.dp))
            .background(SettingsAccent.copy(alpha = 0.14f)).border(1.dp, SettingsAccent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (sender != null) Avatar(profiles[sender], sender, size = 30, onClick = { onProfile(sender) })
        else Box(Modifier.size(30.dp).clip(CircleShape).background(SettingsAccent), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(Glyphs.Zap, contentDescription = null, tint = androidx.compose.ui.graphics.Color.Black, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    sender?.let { profiles[it]?.bestName ?: Profile.shortNpub(it) } ?: tr("Someone"), color = EInk.Value, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                )
                androidx.compose.material3.Icon(Glyphs.Zap, contentDescription = null, tint = SettingsAccent, modifier = Modifier.padding(start = 6.dp).size(14.dp))
                Text(
                    tr("{0} sats", "%,d".format(z.amountMsat / 1000)), color = SettingsAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
            if (z.comment.isNotBlank()) Text(z.comment, color = EInk.Value, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** The biggest zappers of the stream so far, above the chat. */
@Composable
private fun TopZaps(zaps: List<com.example.btcwidget.nostr.core.Zaps.Receipt>, profiles: Map<String, Profile>) {
    val top = remember(zaps) {
        zaps.groupBy { it.sender }.map { (who, list) -> who to list.sumOf { it.amountMsat } / 1000 }.sortedByDescending { it.second }.take(8)
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Text(tr("Top zaps"), color = EInk.Caption, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        top.forEach { (who, sats) ->
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(SettingsAccent.copy(alpha = 0.14f)).padding(start = 3.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (who != null) Avatar(profiles[who], who, size = 20)
                androidx.compose.material3.Icon(Glyphs.Zap, contentDescription = null, tint = SettingsAccent, modifier = Modifier.padding(start = 4.dp).size(12.dp))
                Text(compactCount(sats.toInt()), color = EInk.Value, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
}

/** True while the keyboard is up in a chat: what's pinned above it should take little room. */
internal val LocalChatCompact = androidx.compose.runtime.compositionLocalOf { false }
