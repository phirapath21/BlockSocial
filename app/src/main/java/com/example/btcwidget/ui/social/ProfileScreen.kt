package com.example.btcwidget.ui.social

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import coil3.compose.AsyncImage
import com.example.btcwidget.ChatKey
import com.example.btcwidget.EditProfileKey
import com.example.btcwidget.FollowsKey
import com.example.btcwidget.ListsKey
import com.example.btcwidget.LoginKey
import com.example.btcwidget.nostr.FeedItem
import com.example.btcwidget.nostr.NoteStats
import com.example.btcwidget.nostr.core.Article
import com.example.btcwidget.nostr.core.Badges
import com.example.btcwidget.nostr.core.Content
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Imeta
import com.example.btcwidget.nostr.core.Nip19
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.nostr.core.ThreadRef
import com.example.btcwidget.ui.main.EInk
import kotlinx.coroutines.launch

// ---- Profile ----

@Composable
fun ProfileScreen(vm: SocialViewModel, pubkey: String, navigate: (NavKey) -> Unit, onBack: (() -> Unit)?) {
    val profiles by vm.profiles.collectAsState()
    val stats by vm.stats.collectAsState()
    val following by vm.following.collectAsState()
    val muted by vm.muted.collectAsState()
    val account by vm.account.collectAsState()
    val scope = rememberCoroutineScope()
    // Kept across Back: opening a post or their followers and coming back shows the same notes.
    var items by rememberKeptState<List<FeedItem>?>(pubkey) { null }
    var loadingMore by remember(pubkey) { mutableStateOf(false) }
    var end by rememberKeptState(pubkey) { false }
    val theirFollowing by produceKept<Set<String>?>(null, pubkey) { value = vm.followingOf(pubkey) }
    // X shows the pinned note first on the Posts tab. My own pins reload when I change them.
    val myPins by vm.pinned.collectAsState()
    val pinnedNote by produceKept<Event?>(null, pubkey, if (account?.pubkey == pubkey) myPins else null) {
        value = vm.pinnedNoteOf(pubkey)
    }
    var tab by rememberSaveable(pubkey) { mutableStateOf(0) }
    var addToList by remember(pubkey) { mutableStateOf(false) }
    var profileMenu by remember(pubkey) { mutableStateOf(false) }
    var showQr by remember(pubkey) { mutableStateOf(false) }
    val badges by produceKept<List<Badges.Definition>>(emptyList(), pubkey) { value = vm.badgesOf(pubkey) }
    // Loaded the first time the Articles tab opens.
    var articles by rememberKeptState<List<Article>?>(pubkey) { null }
    LaunchedEffect(pubkey, tab) { if (tab == 3 && articles == null) articles = vm.articlesBy(pubkey) }
    // Until the first answer, the header shows grey shapes (a person may have no profile at all).
    var profileTried by remember(pubkey) { mutableStateOf(false) }
    LaunchedEffect(pubkey) {
        // Header and notes load at the same time; coming Back, the notes are already here.
        launch { vm.refreshProfile(pubkey); profileTried = true }
        if (items == null) items = vm.profileFeed(pubkey)
    }
    val profile = profiles[pubkey]
    val isMe = account?.pubkey == pubkey
    // X-style profile tabs, filtered from the same notes.
    val shown = remember(items, tab) {
        items.orEmpty().filter { item ->
            val isReply = item.repostedBy == null && ThreadRef.of(item.note).isReply
            when (tab) {
                0 -> !isReply
                1 -> isReply
                3 -> false
                else -> item.repostedBy == null && Content.parse(item.note.content).any { it is Content.Segment.Image } ||
                    item.repostedBy == null && Imeta.imageUrls(item.note).isNotEmpty()
            }
        }
    }

    // The bar over the banner fills in as the banner scrolls away, as on X.
    val listState = rememberKept(pubkey) { LazyListState() }
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.getTop(density)
    var widthPx by remember { mutableStateOf(0) }
    val topInset = onBack != null
    val bannerPx = widthPx / 3 + if (topInset) statusTop else 0
    val barPx = statusTop + with(density) { 52.dp.roundToPx() }
    val fadePx = with(density) { 28.dp.roundToPx() }
    val titlePx = with(density) { 96.dp.roundToPx() }
    val collapse by remember(bannerPx, barPx) {
        derivedStateOf {
            when {
                widthPx == 0 -> 0f
                listState.firstVisibleItemIndex > 0 -> 1f
                else -> ((listState.firstVisibleItemScrollOffset - (bannerPx - barPx - fadePx)).toFloat() / fadePx).coerceIn(0f, 1f)
            }
        }
    }
    val showTitle by remember(bannerPx, barPx) {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > bannerPx - barPx + titlePx }
    }
    val postsLine = items?.let { list ->
        val n = list.count { it.repostedBy == null && !ThreadRef.of(it.note).isReply }
        if (n == 0) null else tr("{0} posts", compactCount(n) + if (end) "" else "+")
    }

    Box(Modifier.fillMaxSize().background(EInk.Background).onSizeChanged { widthPx = it.width }) {
        if (addToList) AddToListSheet(vm, pubkey, onClose = { addToList = false })
        if (showQr) ProfileQrDialog(vm, pubkey, navigate, onDismiss = { showQr = false })
        if (profileMenu) {
            val context = LocalContext.current
            val name = profile?.bestName ?: Profile.shortNpub(pubkey)
            val loggedIn = account != null
            ActionSheet(onDismiss = { profileMenu = false }, actions = buildList {
                if (loggedIn && !isMe) add(SheetAction(Glyphs.AddToList, tr("Add or remove from Lists")) { addToList = true })
                add(SheetAction(Glyphs.Share, tr("Share profile")) { shareProfile(context, pubkey, name) })
                add(SheetAction(Glyphs.Qr, tr("QR code")) { showQr = true })
                add(SheetAction(Glyphs.Copy, tr("Copy npub")) {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("npub", Nip19.npub(pubkey)))
                    Toast.makeText(context, tr("npub copied"), Toast.LENGTH_SHORT).show()
                })
                add(SheetAction(Glyphs.Person, tr("Lists by {0}", name)) { navigate(ListsKey(pubkey)) })
                if (loggedIn && !isMe) {
                    if (pubkey in muted) add(SheetAction(Glyphs.SoundOn, tr("Unmute {0}", name)) { vm.mute(pubkey, false) })
                    else add(SheetAction(Glyphs.SoundOff, tr("Mute {0}", name), destructive = true) { vm.mute(pubkey, true) })
                }
            })
        }
        SocialScaffold(vm, navigate) { actions ->
            NoteList(
                items = shown, stats = stats, actions = actions,
                state = listState,
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
                    if (profile == null && !profileTried) ProfileHeaderSkeleton(topInset)
                    else ProfileHeader(
                        profile = profile, pubkey = pubkey, isMe = isMe,
                        followsYou = account?.pubkey?.let { theirFollowing?.contains(it) } == true,
                        followingCount = theirFollowing?.size,
                        isFollowing = pubkey in following,
                        isMuted = pubkey in muted,
                        onFollow = { if (account == null) navigate(LoginKey) else vm.follow(pubkey, pubkey !in following) },
                        onEdit = { navigate(EditProfileKey) },
                        onMessage = { if (account == null) navigate(LoginKey) else navigate(ChatKey(pubkey)) },
                        onOpenFollowing = { navigate(FollowsKey(pubkey, followers = false)) },
                        onOpenFollowers = { navigate(FollowsKey(pubkey, followers = true)) },
                        onUnmute = { vm.mute(pubkey, false) },
                        onMore = { profileMenu = true },
                        onOpenLists = { navigate(ListsKey(pubkey)) },
                        actions = actions,
                        topInset = topInset
                    )
                    BadgesRow(badges)
                    FeedTabs(listOf(tr("Posts"), tr("Replies"), tr("Media"), tr("Articles")), selected = tab) { tab = it }
                    pinnedNote?.takeIf { tab == 0 }?.let { pin ->
                        PinnedLabel()
                        NoteCard(pin, stats[pin.id] ?: NoteStats(), actions)
                    }
                    val loaded = items
                    if (tab == 3) {
                        val list = articles
                        if (list == null) Spinner()
                        else if (list.isEmpty()) EmptyNote(tr("No articles yet."))
                        else list.forEach { ArticleRow(it, onClick = { actions.openArticle(Nip19.Address(it.event.kind, it.pubkey, it.identifier)) }) }
                    }
                    else if (loaded == null) NotesSkeleton()
                    else if (shown.isEmpty()) EmptyNote(listOf("No posts yet.", "No replies yet.", "No photos yet.")[tab] + if (!end && loaded.isNotEmpty()) tr(" Scroll loads older notes.") else "")
                }
            )
        }
        if (onBack != null) ProfileTopBar(profile, pubkey, postsLine, collapse, showTitle, onBack)
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
    onMore: () -> Unit,
    onOpenLists: () -> Unit,
    actions: NoteActions,
    topInset: Boolean = false
) {
    Column {
        Box {
            val banner = profile?.banner
            // Under the status bar too when the screen has no bar of its own, as on X.
            Box(Modifier.fillMaxWidth().background(EInk.Track)) {
                if (banner != null) AsyncImage(banner, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                Column {
                    if (topInset) Spacer(Modifier.statusBarsPadding())
                    Spacer(Modifier.fillMaxWidth().aspectRatio(3f))
                }
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
            CircleIconButton(Glyphs.MoreHoriz, tr("More"), Modifier.padding(end = 8.dp).align(Alignment.CenterVertically), onClick = onMore)
            when {
                isMe -> PillButton(tr("Edit profile"), filled = false, onClick = onEdit)
                isMuted -> PillButton(tr("Unmute"), filled = false, onClick = onUnmute)
                else -> {
                    CircleIconButton(Glyphs.Chat, tr("Message"), Modifier.padding(end = 8.dp).align(Alignment.CenterVertically), onClick = onMessage)
                    if (profile?.lud16 != null || profile?.lud06 != null) {
                        CircleIconButton(Glyphs.Zap, tr("Zap"), Modifier.padding(end = 8.dp).align(Alignment.CenterVertically), onClick = { actions.zap(fakeProfileNote(pubkey)) })
                    }
                    PillButton(if (isFollowing) tr("Following") else tr("Follow"), filled = !isFollowing, onClick = onFollow)
                }
            }
        }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Spacer(Modifier.height(10.dp))
            DisplayName(profile, pubkey, EInk.Value, 21.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    handleText(profile, pubkey),
                    color = EInk.Caption, fontSize = 14.sp
                )
                if (followsYou) {
                    Text(
                        tr("Follows you"), color = EInk.Caption, fontSize = 11.sp,
                        modifier = Modifier.padding(start = 8.dp).background(EInk.Track, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
            profile?.about?.takeIf { it.isNotBlank() }?.let { ProfileBio(it, actions, Modifier.padding(top = 10.dp)) }
            Column(Modifier.padding(top = 6.dp)) {
                profile?.website?.trim()?.takeIf { it.isNotEmpty() }?.let { site ->
                    val url = if ("://" in site) site else "https://$site"
                    ProfileInfoRow(
                        Glyphs.Link, site.removePrefix("https://").removePrefix("http://").removeSuffix("/"), url, tr("Link copied"),
                        color = SettingsAccent, onClick = { actions.openLink(url, null) }
                    )
                }
                profile?.lud16?.trim()?.takeIf { it.isNotEmpty() }?.let { address ->
                    ProfileInfoRow(
                        Glyphs.Zap, address, address, tr("Lightning address copied"),
                        onClick = if (isMe) null else ({ actions.zap(fakeProfileNote(pubkey)) })
                    )
                }
            }
            Row(Modifier.padding(top = 8.dp)) {
                Text(
                    (followingCount?.let { compactCount(it) } ?: "…") + tr(" Following"),
                    color = EInk.Label, fontSize = 14.sp,
                    modifier = Modifier.clickable(onClickLabel = tr("Show who they follow"), onClick = onOpenFollowing).padding(end = 18.dp, top = 2.dp, bottom = 2.dp)
                )
                Text(
                    tr("Followers"),
                    color = EInk.Label, fontSize = 14.sp,
                    modifier = Modifier.clickable(onClickLabel = tr("Show their followers"), onClick = onOpenFollowers).padding(end = 18.dp, top = 2.dp, bottom = 2.dp)
                )
                Text(
                    tr("Lists"),
                    color = EInk.Label, fontSize = 14.sp,
                    modifier = Modifier.clickable(onClickLabel = tr("Show their lists"), onClick = onOpenLists).padding(vertical = 2.dp)
                )
            }
            val npub = Nip19.npub(pubkey)
            Box(Modifier.padding(top = 2.dp)) {
                ProfileInfoRow(null, npub.take(16) + "…" + npub.takeLast(6), npub, tr("npub copied"), color = EInk.Caption, fontSize = 12)
            }
        }
    }
}

/** Shares a link to the profile (njump.me opens in any browser or Nostr app). */
private fun shareProfile(context: Context, pubkey: String, name: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, "$name on Nostr: https://njump.me/" + Nip19.npub(pubkey))
    context.startActivity(Intent.createChooser(send, "Share profile").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Zapping a profile (no note): a stand-in event that only carries the recipient. */
private fun fakeProfileNote(pubkey: String) =
    Event("", pubkey, 0, -1, emptyList(), "", "")

/** NIP-58 badges the person shows; tap one for its name and description. */
@Composable
private fun BadgesRow(badges: List<Badges.Definition>) {
    if (badges.isEmpty()) return
    var open by remember { mutableStateOf<Badges.Definition?>(null) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        badges.forEach { b ->
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(EInk.Body).clickable(onClickLabel = b.name) { open = b },
                contentAlignment = Alignment.Center
            ) {
                if (b.image != null) AsyncImage(model = b.image, contentDescription = b.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Text("🏅", fontSize = 16.sp)
            }
        }
    }
    open?.let { b ->
        BlockDialog(onDismiss = { open = null }, title = b.name, text = b.description ?: "A badge awarded on Nostr.", dismissLabel = tr("Close")) {
            if (b.image != null) AsyncImage(
                model = b.image, contentDescription = b.name, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)
            )
        }
    }
}
