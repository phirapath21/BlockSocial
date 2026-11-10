package com.example.btcwidget.ui.social

import androidx.compose.foundation.background
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
    LaunchedEffect(account) { vm.start() }

    SocialScaffold(vm, navigate) { actions ->
        Box(Modifier.fillMaxSize()) {
            if (account == null) {
                WelcomeScreen(vm, actions, onLogin = { navigate(LoginKey) })
            } else {
                PullToRefreshBox(isRefreshing = home.refreshing, onRefresh = vm::refreshHome, modifier = Modifier.fillMaxSize()) {
                    when {
                        home.loading && home.items.isEmpty() -> Spinner(Modifier.padding(top = 40.dp))
                        home.items.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                            item { EmptyNote(home.error ?: "Nothing here yet. Follow people to fill your timeline, or write your first note.") }
                        }
                        else -> NoteList(
                            items = home.items, stats = stats, actions = actions,
                            loadingMore = home.loading, onLoadMore = vm::loadMoreHome
                        )
                    }
                }
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
                LazyColumn(Modifier.fillMaxSize()) {
                    items(t.ancestors, key = { "a" + it.id }) { NoteCard(it, stats[it.id] ?: NoteStats(), actions) }
                    item(key = "focus") { NoteCard(t.focus, stats[t.focus.id] ?: NoteStats(), actions, large = true) }
                    item(key = "reply") {
                        Row(
                            Modifier.fillMaxWidth().clickable { actions.reply(t.focus) }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Post your reply", color = EInk.Caption, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            PillButton("Reply", onClick = { actions.reply(t.focus) })
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
                    }
                    items(t.replies, key = { "r" + it.id }) { NoteCard(it, stats[it.id] ?: NoteStats(), actions) }
                    if (t.replies.isEmpty()) item { EmptyNote("No replies yet.") }
                }
            }
        }
    }
}

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
    LaunchedEffect(pubkey) {
        // Header and notes load at the same time.
        launch { vm.refreshProfile(pubkey) }
        items = vm.profileFeed(pubkey)
    }
    val profile = profiles[pubkey]
    val isMe = account?.pubkey == pubkey

    Column(Modifier.fillMaxSize().background(EInk.Background)) {
        if (onBack != null) SocialTopBar(profile?.bestName ?: "Profile", onBack)
        SocialScaffold(vm, navigate) { actions ->
            NoteList(
                items = items.orEmpty(), stats = stats, actions = actions,
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
                        onUnmute = { vm.mute(pubkey, false) },
                        actions = actions
                    )
                    val loaded = items
                    if (loaded == null) Spinner() else if (loaded.isEmpty()) EmptyNote("No notes yet.")
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
                    profile?.nip05?.removePrefix("_@") ?: Profile.shortNpub(pubkey),
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
            followingCount?.let {
                Text(
                    "${compactCount(it)} Following", color = EInk.Caption, fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Text(
                Nip19.npub(pubkey).let { it.take(16) + "…" + it.takeLast(6) },
                color = EInk.Caption, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)
            )
        }
        Text(
            "Posts", color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
        HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
    }
}

/** Zapping a profile (no note): a stand-in event that only carries the recipient. */
private fun fakeProfileNote(pubkey: String) =
    com.example.btcwidget.nostr.core.Event("", pubkey, 0, -1, emptyList(), "", "")
