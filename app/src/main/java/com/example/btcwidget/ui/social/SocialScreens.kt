package com.example.btcwidget.ui.social

import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import coil3.compose.AsyncImage
import com.example.btcwidget.ComposeKey
import com.example.btcwidget.EditProfileKey
import com.example.btcwidget.LoginKey
import com.example.btcwidget.nostr.FeedItem
import com.example.btcwidget.nostr.NoteStats
import com.example.btcwidget.nostr.NostrNotification
import com.example.btcwidget.nostr.core.Nip19
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk
import kotlinx.coroutines.launch

// ---- Shared pieces ----

/** Title bar for pushed screens: back arrow and a title. */
@Composable
fun SocialTopBar(title: String, onBack: () -> Unit) {
    Column(modifier = Modifier.background(EInk.Background).statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(
                text = "←",
                color = EInk.Value,
                fontSize = 22.sp,
                modifier = Modifier
                    .clickable(onClickLabel = "Back", onClick = onBack)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Text(title, color = EInk.Value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
    }
}

/** A feed of notes with infinite scroll. */
@Composable
fun NoteList(
    items: List<FeedItem>,
    stats: Map<String, NoteStats>,
    actions: NoteActions,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    loadingMore: Boolean = false,
    onLoadMore: (() -> Unit)? = null,
    header: (@Composable () -> Unit)? = null
) {
    val nearEnd by remember {
        derivedStateOf {
            val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.layoutInfo.totalItemsCount - 5
        }
    }
    if (onLoadMore != null) LaunchedEffect(nearEnd, items.size) { if (nearEnd && items.isNotEmpty()) onLoadMore() }

    LazyColumn(state = state, modifier = modifier.fillMaxSize()) {
        if (header != null) item(key = "header") { header() }
        items(items, key = { it.key }) { item ->
            NoteCard(item.note, stats[item.note.id] ?: NoteStats(), actions, repostedBy = item.repostedBy)
        }
        if (loadingMore) item(key = "more") { Spinner() }
    }
}

@Composable
fun Spinner(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = EInk.Value, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun EmptyNote(text: String) {
    Text(
        text = text,
        color = EInk.Caption,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(32.dp)
    )
}

/** Primary pill button, X-style. */
@Composable
fun PillButton(text: String, filled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .then(if (filled) Modifier.background(EInk.Value) else Modifier.border(1.dp, EInk.BodyStroke, RoundedCornerShape(50)))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (filled) EInk.Background else EInk.Value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

// ---- Home ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: SocialViewModel, navigate: (NavKey) -> Unit) {
    val account by vm.account.collectAsState()
    val home by vm.home.collectAsState()
    val stats by vm.stats.collectAsState()
    val newPosts by vm.newPosts.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(account) { vm.start() }

    SocialScaffold(vm, navigate) { actions ->
        Box(Modifier.fillMaxSize()) {
            if (account == null) {
                WelcomeScreen(vm, actions, onLogin = { navigate(LoginKey) })
            } else {
                val shown = home.items
                val feed by vm.homeFeed.collectAsState()
                var feedMenu by remember { mutableStateOf(false) }
                Column(Modifier.fillMaxSize()) {
                    FeedHeader(feed, open = feedMenu) { feedMenu = true }
                    PullToRefreshBox(isRefreshing = home.refreshing, onRefresh = vm::refreshHome, modifier = Modifier.fillMaxSize()) {
                        when {
                            home.loading && home.items.isEmpty() -> Spinner(Modifier.padding(top = 40.dp))
                            shown.isEmpty() && !home.loading -> LazyColumn(Modifier.fillMaxSize()) {
                                item { EmptyNote(home.error ?: "Nothing here yet. Follow people to fill your timeline, or write your first note.") }
                            }
                            else -> NoteList(
                                items = shown, stats = stats, actions = actions, state = listState,
                                loadingMore = home.loading, onLoadMore = vm::loadMoreHome
                            )
                        }
                    }
                }
                if (newPosts.isNotEmpty()) {
                    // "↑ N new posts": new notes wait here instead of shifting what you're reading.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(50))
                            .background(EInk.Value)
                            .clickable(onClickLabel = "Show new posts") {
                                vm.showNewPosts()
                                scope.launch { listState.animateScrollToItem(0) }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        newPosts.distinctBy { it.note.pubkey }.take(3).forEach { item ->
                            Box(Modifier.padding(end = 2.dp)) { Avatar(actions.profileOf(item.note.pubkey), item.note.pubkey, size = 20) }
                        }
                        Text(
                            "  ↑ ${newPosts.size} new post" + if (newPosts.size == 1) "" else "s",
                            color = EInk.Background, fontSize = 13.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (feedMenu) FeedMenu(vm, onClose = { feedMenu = false })
                FloatingActionButton(
                    onClick = { navigate(ComposeKey()) },
                    containerColor = EInk.Value,
                    contentColor = EInk.Background,
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                ) { Text("+", fontSize = 26.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** Underlined tab row, as at the top of X's timeline. */
@Composable
fun FeedTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { i, label ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClickLabel = label) { onSelect(i) }
                    .padding(top = 12.dp)
            ) {
                Text(
                    label,
                    color = if (i == selected) EInk.Value else EInk.Caption,
                    fontSize = 15.sp,
                    fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal
                )
                Box(
                    Modifier
                        .padding(top = 10.dp)
                        .width(56.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i == selected) EInk.Value else androidx.compose.ui.graphics.Color.Transparent)
                )
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
}

/** Logged out: what this is, how to join, and a public #bitcoin feed to browse. */
@Composable
private fun WelcomeScreen(vm: SocialViewModel, actions: NoteActions, onLogin: () -> Unit) {
    val stats by vm.stats.collectAsState()
    val feed by produceState<List<FeedItem>?>(null) { value = vm.hashtagFeed("bitcoin") }
    NoteList(items = feed.orEmpty(), stats = stats, actions = actions, header = {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Bitcoin people, on Nostr", color = EInk.Value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                "An open social network with no company in charge. Your account is a key you own; post, follow, reply and zap sats.",
                color = EInk.Label, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
            )
            PillButton("Log in or create account", onClick = onLogin)
            Text("#bitcoin right now", color = EInk.Caption, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp))
        }
        HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
        if (feed == null) Spinner()
    })
}

// ---- Notifications ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(vm: SocialViewModel, navigate: (NavKey) -> Unit) {
    val account by vm.account.collectAsState()
    val state by vm.notifications.collectAsState()
    val profiles by vm.profiles.collectAsState()
    LaunchedEffect(account) { if (account != null && state.items.isEmpty()) vm.refreshNotifications() }

    SocialScaffold(vm, navigate) { actions ->
        Column(Modifier.fillMaxSize()) {
            if (account == null) {
                EmptyNote("Log in to see replies, likes, reposts and zaps.")
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PillButton("Log in", onClick = { navigate(LoginKey) }) }
                return@Column
            }
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refreshNotifications, modifier = Modifier.fillMaxSize()) {
                when {
                    state.loading -> Spinner(Modifier.padding(top = 40.dp))
                    state.items.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) { item { EmptyNote(state.error ?: "No notifications yet.") } }
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(state.items, key = { it.event.id }) { n ->
                            NotificationRow(n, profiles[n.actor], actions)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: NostrNotification, actor: Profile?, actions: NoteActions) {
    val name = actor?.bestName ?: Profile.shortNpub(n.actor)
    val (icon, verb) = when (n.type) {
        NostrNotification.Type.REPLY -> "💬" to "replied"
        NostrNotification.Type.MENTION -> "@" to "mentioned you"
        NostrNotification.Type.REPOST -> "⟲" to "reposted your note"
        NostrNotification.Type.LIKE -> "♥" to "liked your note"
        NostrNotification.Type.ZAP -> "⚡" to "zapped ${n.zapMsat / 1000} sats"
    }
    if (n.type == NostrNotification.Type.REPLY || n.type == NostrNotification.Type.MENTION) {
        NoteCard(n.event, NoteStats(), actions)
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = n.noteId != null) { n.noteId?.let { id -> actions.openNote(n.event.copy(id = id)) } }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(icon, color = EInk.Value, fontSize = 20.sp, modifier = Modifier.width(34.dp))
        Column(Modifier.weight(1f)) {
            Avatar(actor, n.actor, size = 32, onClick = { actions.openProfile(n.actor) })
            Text(
                text = "$name $verb · ${relativeTime(n.event.createdAt)}",
                color = EInk.Value, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp)
            )
            if (n.type == NostrNotification.Type.ZAP && n.event.firstTag("description") != null) {
                val comment = com.example.btcwidget.nostr.core.Zaps.parseReceipt(n.event)?.comment.orEmpty()
                if (comment.isNotBlank()) Text(comment, color = EInk.Label, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
}

// ---- Home feed menu ----

/** "Latest ⌄ / Latest notes by your follows": tap to pick another feed. */
@Composable
private fun FeedHeader(feed: com.example.btcwidget.nostr.core.HomeFeed, open: Boolean, onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Choose feed", onClick = onOpen).padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(feed.title.uppercase() + if (open) "  ▴" else "  ▾", color = EInk.Value, fontSize = 18.sp, fontFamily = EInk.Mono, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(feed.subtitle, color = EInk.Caption, fontSize = 12.sp, fontFamily = EInk.Mono, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
}

/** Pick a feed; Edit to reorder, remove, or add hashtag, relay and DVM feeds. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun FeedMenu(vm: SocialViewModel, onClose: () -> Unit) {
    val feeds by vm.homeFeeds.collectAsState()
    val current by vm.homeFeed.collectAsState()
    var editing by remember { mutableStateOf(false) }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onClose, containerColor = EInk.Background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            feeds.forEachIndexed { i, f ->
                val selected = f == current
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (selected) EInk.Body else EInk.Background)
                        .clickable(enabled = !editing) { vm.selectFeed(f); onClose() }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(f.title, color = EInk.Value, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(f.subtitle, color = EInk.Caption, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (editing) {
                        MenuGlyph("↑", "Move up", enabled = i > 0) { vm.moveFeed(f, -1) }
                        MenuGlyph("↓", "Move down", enabled = i < feeds.lastIndex) { vm.moveFeed(f, 1) }
                        MenuGlyph("✕", "Remove", enabled = feeds.size > 1) { vm.removeFeed(f) }
                    } else if (selected) Text("✓", color = EInk.Value, fontSize = 18.sp)
                }
            }
            if (editing) AddFeed(vm, feeds)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (editing) "Done" else "Edit", color = EInk.Value, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { editing = !editing }.padding(8.dp)
                )
                if (editing) Text("Reset", color = EInk.Caption, fontSize = 16.sp, modifier = Modifier.clickable { vm.resetFeeds() }.padding(8.dp))
                Text("Close", color = EInk.Value, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onClose).padding(8.dp))
            }
        }
    }
}

@Composable
private fun MenuGlyph(glyph: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        glyph, color = if (enabled) EInk.Value else EInk.BodyStroke, fontSize = 18.sp,
        modifier = Modifier.clip(CircleShape).clickable(enabled = enabled, onClickLabel = label, onClick = onClick).padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/** Adds a hashtag, relay (global on one relay) or DVM feed to the menu. */
@Composable
private fun AddFeed(vm: SocialViewModel, existing: List<com.example.btcwidget.nostr.core.HomeFeed>) {
    var kind by remember { mutableStateOf(0) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text("Add a feed", color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("#HASHTAG", "RELAY", "DVM").forEachIndexed { i, t ->
                com.example.btcwidget.ui.main.EInkChip(text = t, selected = kind == i, onClick = { kind = i; input = ""; error = null })
            }
        }
        // Built-in feeds that were removed can come back.
        com.example.btcwidget.nostr.core.HomeFeed.DEFAULTS.filter { it !in existing }.forEach { f ->
            Text("+ " + f.title, color = EInk.Value, fontSize = 14.sp, modifier = Modifier.clickable { vm.addFeed(f) }.padding(vertical = 6.dp))
        }
        if (kind == 2) {
            val services by produceState<List<com.example.btcwidget.nostr.core.Dvm.Service>?>(null) { value = vm.feedServices() }
            when (val l = services) {
                null -> Spinner()
                else -> if (l.isEmpty()) Text("No feed services found on your relays.", color = EInk.Caption, fontSize = 13.sp)
                else Column(Modifier.heightIn(max = 260.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    l.forEach { svc ->
                        Text(
                            "+ " + svc.name + (svc.about?.let { " · $it" } ?: ""), color = EInk.Value, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().clickable { vm.addFeed(com.example.btcwidget.nostr.core.HomeFeed.Dvm(svc.pubkey, svc.id, svc.name)) }.padding(vertical = 6.dp)
                        )
                    }
                }
            }
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.OutlinedTextField(
                value = input, onValueChange = { input = it; error = null },
                placeholder = { Text(if (kind == 0) "siamstr" else "wss://relay.example.com") },
                singleLine = true, isError = error != null,
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedBorderColor = EInk.Value, unfocusedBorderColor = EInk.BodyStroke, focusedTextColor = EInk.Value, unfocusedTextColor = EInk.Value, cursorColor = EInk.Value),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            com.example.btcwidget.ui.main.EInkChip(text = "ADD", selected = input.isNotBlank(), onClick = {
                val feed = if (kind == 0) com.example.btcwidget.nostr.core.HomeFeed.cleanTag(input)?.let { com.example.btcwidget.nostr.core.HomeFeed.Hashtag(it) }
                else com.example.btcwidget.nostr.core.normalizeRelayUrl(input.trim().let { if (it.startsWith("ws")) it else "wss://$it" })?.let { com.example.btcwidget.nostr.core.HomeFeed.Global(it) }
                if (feed == null) error = if (kind == 0) "One word, letters and numbers only" else "Not a relay address"
                else { vm.addFeed(feed); input = "" }
            })
        }
        error?.let { Text(it, color = EInk.Alert, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
    }
}

// ---- Thread ----

@Composable
fun ThreadScreen(vm: SocialViewModel, noteId: String, navigate: (NavKey) -> Unit, onBack: () -> Unit) {
    val stats by vm.stats.collectAsState()
    var reload by remember { mutableStateOf(0) }
    val thread by produceState<com.example.btcwidget.nostr.NostrRepository.Thread?>(null, noteId, reload) { value = vm.thread(noteId) }
    Column(Modifier.fillMaxSize().background(EInk.Background)) {
        SocialTopBar("Post", onBack)
        SocialScaffold(vm, navigate) { actions ->
            val t = thread
            if (t == null) {
                Spinner(Modifier.padding(top = 40.dp))
            } else {
                Box(Modifier.fillMaxSize()) {
                    // Room at the end so the last reply can scroll above the floating card.
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
                        items(t.ancestors, key = { "a" + it.id }) { NoteCard(it, stats[it.id] ?: NoteStats(), actions) }
                        item(key = "focus") { NoteCard(t.focus, stats[t.focus.id] ?: NoteStats(), actions, large = true) }
                        items(t.replies, key = { "r" + it.event.id }) { row ->
                            NoteCard(row.event, stats[row.event.id] ?: NoteStats(), actions, modifier = Modifier.threadIndent(row.depth))
                        }
                        if (t.replies.isEmpty()) item { EmptyNote("No replies yet.") }
                    }
                    // Floats over the post and replies, as on X, and rides above the keyboard.
                    ReplyBar(
                        vm, t.focus,
                        onLogin = { navigate(LoginKey) },
                        onExpand = { navigate(ComposeKey(replyTo = t.focus.id)) },
                        onSent = { reload++ },
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/** Indents a nested reply and draws a line for each level above it. Deep threads stop indenting. */
private fun Modifier.threadIndent(depth: Int): Modifier {
    val levels = depth.coerceAtMost(MAX_INDENT)
    if (levels == 0) return this
    return drawBehind {
        val step = INDENT_STEP.dp.toPx()
        for (i in 0 until levels) {
            val x = step * i + step / 2
            drawLine(EInk.BodyStroke, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
        }
    }.padding(start = (INDENT_STEP * levels).dp)
}

private const val INDENT_STEP = 14
private const val MAX_INDENT = 5

// ---- Hashtag ----

@Composable
fun HashtagScreen(vm: SocialViewModel, tag: String, navigate: (NavKey) -> Unit, onBack: () -> Unit) {
    val stats by vm.stats.collectAsState()
    val feed by produceState<List<FeedItem>?>(null, tag) { value = vm.hashtagFeed(tag) }
    Column(Modifier.fillMaxSize().background(EInk.Background)) {
        SocialTopBar("#$tag", onBack)
        SocialScaffold(vm, navigate) { actions ->
            when (val f = feed) {
                null -> Spinner(Modifier.padding(top = 40.dp))
                else -> if (f.isEmpty()) EmptyNote("No notes with #$tag yet.") else NoteList(f, stats, actions)
            }
        }
    }
}

// ---- Profile ----

@Composable
fun ProfileScreen(vm: SocialViewModel, pubkey: String, navigate: (NavKey) -> Unit, onBack: (() -> Unit)?) {
    val profiles by vm.profiles.collectAsState()
    val stats by vm.stats.collectAsState()
    val following by vm.following.collectAsState()
    val muted by vm.muted.collectAsState()
    val account by vm.account.collectAsState()
    val scope = rememberCoroutineScope()
    var items by remember(pubkey) { mutableStateOf<List<FeedItem>?>(null) }
    var loadingMore by remember(pubkey) { mutableStateOf(false) }
    var end by remember(pubkey) { mutableStateOf(false) }
    val theirFollowing by produceState<Set<String>?>(null, pubkey) { value = vm.followingOf(pubkey) }
    // X shows the pinned note first on the Posts tab. My own pins reload when I change them.
    val myPins by vm.pinned.collectAsState()
    val pinnedNote by produceState<com.example.btcwidget.nostr.core.Event?>(null, pubkey, if (account?.pubkey == pubkey) myPins else null) {
        value = vm.pinnedNoteOf(pubkey)
    }
    var tab by androidx.compose.runtime.saveable.rememberSaveable(pubkey) { mutableStateOf(0) }
    val badges by produceState<List<com.example.btcwidget.nostr.core.Badges.Definition>>(emptyList(), pubkey) { value = vm.badgesOf(pubkey) }
    // Loaded the first time the Articles tab opens.
    var articles by remember(pubkey) { mutableStateOf<List<com.example.btcwidget.nostr.core.Article>?>(null) }
    LaunchedEffect(pubkey, tab) { if (tab == 3 && articles == null) articles = vm.articlesBy(pubkey) }
    LaunchedEffect(pubkey) {
        // Header and notes load at the same time.
        launch { vm.refreshProfile(pubkey) }
        items = vm.profileFeed(pubkey)
    }
    val profile = profiles[pubkey]
    val isMe = account?.pubkey == pubkey
    // X-style profile tabs, filtered from the same notes.
    val shown = remember(items, tab) {
        items.orEmpty().filter { item ->
            val isReply = item.repostedBy == null && com.example.btcwidget.nostr.core.ThreadRef.of(item.note).isReply
            when (tab) {
                0 -> !isReply
                1 -> isReply
                3 -> false
                else -> item.repostedBy == null && com.example.btcwidget.nostr.core.Content.parse(item.note.content).any { it is com.example.btcwidget.nostr.core.Content.Segment.Image } ||
                    item.repostedBy == null && com.example.btcwidget.nostr.core.Imeta.imageUrls(item.note).isNotEmpty()
            }
        }
    }

    Column(Modifier.fillMaxSize().background(EInk.Background)) {
        if (onBack != null) SocialTopBar(profile?.bestName ?: "Profile", onBack)
        SocialScaffold(vm, navigate) { actions ->
            NoteList(
                items = shown, stats = stats, actions = actions,
                loadingMore = loadingMore,
                onLoadMore = {
                    val list = items
                    if (!loadingMore && !end && !list.isNullOrEmpty()) {
                        loadingMore = true
                        scope.launch {
                            val more = vm.profileFeed(pubkey, until = list.minOf { it.sortTime } - 1)
                            items = (list + more).distinctBy { it.key }
                            end = more.isEmpty()
                            loadingMore = false
                        }
                    }
                },
                header = {
                    ProfileHeader(
                        profile = profile, pubkey = pubkey, isMe = isMe,
                        followsYou = account?.pubkey?.let { theirFollowing?.contains(it) } == true,
                        followingCount = theirFollowing?.size,
                        isFollowing = pubkey in following,
                        isMuted = pubkey in muted,
                        onFollow = { if (account == null) navigate(LoginKey) else vm.follow(pubkey, pubkey !in following) },
                        onEdit = { navigate(EditProfileKey) },
                        onMessage = { if (account == null) navigate(LoginKey) else navigate(com.example.btcwidget.ChatKey(pubkey)) },
                        onOpenFollowing = { navigate(com.example.btcwidget.FollowsKey(pubkey, followers = false)) },
                        onOpenFollowers = { navigate(com.example.btcwidget.FollowsKey(pubkey, followers = true)) },
                        onUnmute = { vm.mute(pubkey, false) },
                        actions = actions
                    )
                    BadgesRow(badges)
                    FeedTabs(listOf("Posts", "Replies", "Media", "Articles"), selected = tab) { tab = it }
                    pinnedNote?.takeIf { tab == 0 }?.let { pin ->
                        Text("📌 Pinned", color = EInk.Caption, fontSize = 12.sp, modifier = Modifier.padding(start = 14.dp, top = 8.dp))
                        NoteCard(pin, stats[pin.id] ?: NoteStats(), actions)
                    }
                    val loaded = items
                    if (tab == 3) {
                        val list = articles
                        if (list == null) Spinner()
                        else if (list.isEmpty()) EmptyNote("No articles yet.")
                        else list.forEach { ArticleRow(it, onClick = { actions.openArticle(com.example.btcwidget.nostr.core.Nip19.Address(it.event.kind, it.pubkey, it.identifier)) }) }
                    }
                    else if (loaded == null) Spinner()
                    else if (shown.isEmpty()) EmptyNote(listOf("No posts yet.", "No replies yet.", "No photos yet.")[tab] + if (!end && loaded.isNotEmpty()) " Scroll loads older notes." else "")
                }
            )
        }
    }
}

@Composable
private fun ProfileHeader(
    profile: Profile?,
    pubkey: String,
    isMe: Boolean,
    followsYou: Boolean,
    followingCount: Int?,
    isFollowing: Boolean,
    isMuted: Boolean,
    onFollow: () -> Unit,
    onEdit: () -> Unit,
    onMessage: () -> Unit,
    onOpenFollowing: () -> Unit,
    onOpenFollowers: () -> Unit,
    onUnmute: () -> Unit,
    actions: NoteActions
) {
    Column {
        Box {
            val banner = profile?.banner
            Box(Modifier.fillMaxWidth().aspectRatio(3f).background(EInk.Track)) {
                if (banner != null) AsyncImage(banner, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Box(
                Modifier
                    .padding(start = 14.dp)
                    .align(Alignment.BottomStart)
                    .offset(y = 36.dp)
                    .border(3.dp, EInk.Background, CircleShape)
            ) { Avatar(profile, pubkey, size = 76) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, end = 14.dp), horizontalArrangement = Arrangement.End) {
            when {
                isMe -> PillButton("Edit profile", filled = false, onClick = onEdit)
                isMuted -> PillButton("Unmute", filled = false, onClick = onUnmute)
                else -> {
                    PillButton("✉", filled = false, modifier = Modifier.padding(end = 8.dp), onClick = onMessage)
                    if (profile?.lud16 != null || profile?.lud06 != null) {
                        PillButton("⚡", filled = false, modifier = Modifier.padding(end = 8.dp), onClick = { actions.zap(fakeProfileNote(pubkey)) })
                    }
                    PillButton(if (isFollowing) "Following" else "Follow", filled = !isFollowing, onClick = onFollow)
                }
            }
        }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Spacer(Modifier.height(10.dp))
            Text(profile?.bestName ?: Profile.shortNpub(pubkey), color = EInk.Value, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    handleText(profile, pubkey),
                    color = EInk.Caption, fontSize = 14.sp
                )
                if (followsYou) {
                    Text(
                        "Follows you", color = EInk.Caption, fontSize = 11.sp,
                        modifier = Modifier.padding(start = 8.dp).background(EInk.Track, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
            profile?.about?.let { Text(it, color = EInk.Value, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp)) }
            profile?.website?.let { Text("🔗 " + it.removePrefix("https://"), color = EInk.Label, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp)) }
            profile?.lud16?.let { Text("⚡ $it", color = EInk.Label, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp)) }
            Row(Modifier.padding(top = 8.dp)) {
                Text(
                    (followingCount?.let { compactCount(it) } ?: "…") + " Following",
                    color = EInk.Label, fontSize = 14.sp,
                    modifier = Modifier.clickable(onClickLabel = "Show who they follow", onClick = onOpenFollowing).padding(end = 18.dp, top = 2.dp, bottom = 2.dp)
                )
                Text(
                    "Followers",
                    color = EInk.Label, fontSize = 14.sp,
                    modifier = Modifier.clickable(onClickLabel = "Show their followers", onClick = onOpenFollowers).padding(vertical = 2.dp)
                )
            }
            Text(
                Nip19.npub(pubkey).let { it.take(16) + "…" + it.takeLast(6) },
                color = EInk.Caption, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/** Zapping a profile (no note): a stand-in event that only carries the recipient. */
private fun fakeProfileNote(pubkey: String) =
    com.example.btcwidget.nostr.core.Event("", pubkey, 0, -1, emptyList(), "", "")

/** NIP-58 badges the person shows; tap one for its name and description. */
@Composable
private fun BadgesRow(badges: List<com.example.btcwidget.nostr.core.Badges.Definition>) {
    if (badges.isEmpty()) return
    var open by remember { mutableStateOf<com.example.btcwidget.nostr.core.Badges.Definition?>(null) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        badges.forEach { b ->
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(EInk.Body).clickable(onClickLabel = b.name) { open = b },
                contentAlignment = Alignment.Center
            ) {
                if (b.image != null) coil3.compose.AsyncImage(model = b.image, contentDescription = b.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Text("🏅", fontSize = 16.sp)
            }
        }
    }
    open?.let { b ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { open = null },
            title = { Text(b.name) },
            text = { Text(b.description ?: "A badge awarded on Nostr.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { open = null }) { Text("OK") } }
        )
    }
}
