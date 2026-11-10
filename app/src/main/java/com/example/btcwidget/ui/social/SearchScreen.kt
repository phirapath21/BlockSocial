package com.example.btcwidget.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.example.btcwidget.HashtagKey
import com.example.btcwidget.NoteKey
import com.example.btcwidget.ProfileKey
import com.example.btcwidget.nostr.FeedItem
import com.example.btcwidget.nostr.NoteStats
import com.example.btcwidget.nostr.NostrRepository
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk
import com.example.btcwidget.ui.main.EInkChip
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * Search: an npub, note id, nevent, "#tag" or name@domain opens directly; anything else
 * searches people and posts on NIP-50 search relays.
 */
@Composable
fun SearchScreen(vm: SocialViewModel, navigate: (NavKey) -> Unit, onBack: (() -> Unit)? = null) {
    val stats by vm.stats.collectAsState()
    val following by vm.following.collectAsState()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf("people") }
    var searched by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var notesLoading by remember { mutableStateOf(false) }
    var people by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var notes by remember { mutableStateOf<List<FeedItem>>(emptyList()) }
    var job by remember { mutableStateOf<Job?>(null) }
    val focus = remember { FocusRequester() }
    // Opened as its own screen: start typing. As a tab, don't pop the keyboard on every visit.
    LaunchedEffect(Unit) { if (onBack != null) runCatching { focus.requestFocus() } }

    fun run() {
        val q = query.trim()
        if (q.isEmpty()) return
        keyboard?.hide()
        job?.cancel()
        loading = true
        searched = q
        job = scope.launch {
            when (val direct = vm.resolve(q)) {
                is NostrRepository.Direct.ProfileTarget -> { loading = false; navigate(ProfileKey(direct.pubkey)); return@launch }
                is NostrRepository.Direct.NoteTarget -> { loading = false; navigate(NoteKey(direct.id)); return@launch }
                is NostrRepository.Direct.HashtagTarget -> { loading = false; navigate(HashtagKey(direct.tag)); return@launch }
                is NostrRepository.Direct.ArticleTarget -> { loading = false; navigate(com.example.btcwidget.ArticleKey(direct.address.let { com.example.btcwidget.nostr.core.Nip19.naddr(it.kind, it.pubkey, it.identifier, it.relays) })); return@launch }
                null -> Unit
            }
            // Both at once; each list shows as soon as it arrives.
            notesLoading = true
            val p = async { vm.searchPeople(q) }
            val n = async { vm.searchNotes(q) }
            people = p.await()
            loading = false
            notes = n.await()
            notesLoading = false
        }
    }

    Column(Modifier.fillMaxSize().background(EInk.Background)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .then(if (onBack != null) Modifier.statusBarsPadding() else Modifier)
                .fillMaxWidth()
                .padding(start = if (onBack == null) 12.dp else 0.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
        ) {
            if (onBack != null) {
                Text(
                    "←", color = EInk.Value, fontSize = 22.sp,
                    modifier = Modifier.clickable(onClickLabel = "Back", onClick = onBack).padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search people, posts, #tags, npub…") },
                singleLine = true,
                shape = RoundedCornerShape(50),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onSearch = { run() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EInk.Value, unfocusedBorderColor = EInk.BodyStroke,
                    focusedTextColor = EInk.Value, unfocusedTextColor = EInk.Value, cursorColor = EInk.Value,
                    focusedPlaceholderColor = EInk.Caption, unfocusedPlaceholderColor = EInk.Caption,
                    focusedContainerColor = EInk.Body, unfocusedContainerColor = EInk.Body
                ),
                modifier = Modifier.weight(1f).focusRequester(focus)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            EInkChip(text = "PEOPLE" + if (searched != null && !loading) " ${people.size}" else "", selected = tab == "people", onClick = { tab = "people" })
            EInkChip(text = "POSTS" + if (searched != null && !notesLoading) " ${notes.size}" else "", selected = tab == "posts", onClick = { tab = "posts" })
        }
        HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)

        SocialScaffold(vm, navigate) { actions ->
            when {
                loading -> Spinner(Modifier.padding(top = 30.dp))
                // Before searching: Explore, recent notes from everyone or from one relay.
                searched == null -> ExploreFeed(vm, actions)
                tab == "people" -> if (people.isEmpty()) EmptyNote("No people found for \"$searched\".") else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(people, key = { it.pubkey }) { p ->
                            PersonRow(p, isFollowing = p.pubkey in following, onClick = { navigate(ProfileKey(p.pubkey)) })
                        }
                    }
                }
                notesLoading && notes.isEmpty() -> Spinner(Modifier.padding(top = 30.dp))
                else -> if (notes.isEmpty()) EmptyNote("No posts found for \"$searched\".") else NoteList(notes, stats, actions)
            }
        }
    }
}

/** Recent notes from all relays, or one chosen relay: to find people beyond your follows. */
@Composable
private fun ExploreFeed(vm: SocialViewModel, actions: NoteActions) {
    val stats by vm.stats.collectAsState()
    val scope = rememberCoroutineScope()
    val account by vm.account.collectAsState()
    var suggestions by rememberSaveable { mutableStateOf(false) }
    var feeds by rememberSaveable { mutableStateOf(false) }
    var relay by rememberSaveable { mutableStateOf<String?>(null) }
    var items by remember(relay) { mutableStateOf<List<FeedItem>?>(null) }
    var loadingMore by remember(relay) { mutableStateOf(false) }
    var end by remember(relay) { mutableStateOf(false) }
    LaunchedEffect(relay) { items = vm.globalFeed(relay) }
    Column(Modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            EInkChip(text = "FEEDS", selected = feeds, onClick = { feeds = true; suggestions = false })
            if (account != null) EInkChip(text = "WHO TO FOLLOW", selected = suggestions, onClick = { suggestions = true; feeds = false })
            EInkChip(text = "GLOBAL", selected = !suggestions && !feeds && relay == null, onClick = { suggestions = false; feeds = false; relay = null })
            vm.relays.forEach { url ->
                EInkChip(text = url.removePrefix("wss://").removePrefix("ws://").uppercase(), selected = !suggestions && !feeds && relay == url, onClick = { suggestions = false; feeds = false; relay = url })
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
        if (suggestions) { WhoToFollow(vm, actions); return@Column }
        if (feeds) { DvmFeeds(vm, actions); return@Column }
        when (val list = items) {
            null -> Spinner(Modifier.padding(top = 30.dp))
            else -> if (list.isEmpty()) EmptyNote("Nothing here right now.") else NoteList(
                list, stats, actions,
                loadingMore = loadingMore,
                onLoadMore = {
                    if (!loadingMore && !end) {
                        loadingMore = true
                        scope.launch {
                            val more = vm.globalFeed(relay, until = list.minOf { it.sortTime } - 1)
                            items = (list + more).distinctBy { it.key }
                            end = more.isEmpty()
                            loadingMore = false
                        }
                    }
                }
            )
        }
    }
}

/** Feeds made by Data Vending Machines (NIP-90): pick one to see what it suggests. */
@Composable
private fun DvmFeeds(vm: SocialViewModel, actions: NoteActions) {
    val stats by vm.stats.collectAsState()
    val services by androidx.compose.runtime.produceState<List<com.example.btcwidget.nostr.core.Dvm.Service>?>(null) { value = vm.feedServices() }
    var chosen by remember { mutableStateOf<com.example.btcwidget.nostr.core.Dvm.Service?>(null) }
    var reload by remember { mutableStateOf(0) }
    val c = chosen
    if (c == null) {
        when (val l = services) {
            null -> Spinner(Modifier.padding(top = 30.dp))
            else -> if (l.isEmpty()) EmptyNote("No feeds found on your relays.") else LazyColumn(Modifier.fillMaxSize()) {
                item(key = "note") {
                    Text(
                        "Feeds from Data Vending Machines: services that pick notes for you. Asking one doesn't reveal who you are, except feeds marked personalized.",
                        color = EInk.Caption, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
                items(l, key = { it.key }) { svc ->
                    Row(Modifier.fillMaxWidth().clickable { chosen = svc }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.layout.Box(
                            Modifier.size(40.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).background(EInk.Body),
                            contentAlignment = Alignment.Center
                        ) {
                            if (svc.picture != null) coil3.compose.AsyncImage(model = svc.picture, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else Text("✦", color = EInk.Value, fontSize = 18.sp)
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(svc.name + if (svc.personalized) "  · personalized" else "", color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            svc.about?.let { Text(it, color = EInk.Label, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
                }
            }
        }
        return
    }
    val result by androidx.compose.runtime.produceState<Result<List<FeedItem>>?>(null, c, reload) { value = null; value = vm.dvmFeed(c) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            EInkChip(text = "‹ FEEDS", selected = false, onClick = { chosen = null })
            Text(c.name, color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
            EInkChip(text = "↻", selected = false, onClick = { reload++ })
        }
        when (val r = result) {
            null -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spinner(Modifier.padding(top = 30.dp))
                Text("Asking ${c.name}…", color = EInk.Caption, fontSize = 12.sp)
            }
            else -> r.fold(
                onSuccess = { items -> if (items.isEmpty()) EmptyNote("This feed has nothing right now.") else NoteList(items, stats, actions) },
                onFailure = { EmptyNote(it.message ?: "Couldn't load this feed.") }
            )
        }
    }
}

/** People followed by several of the people you follow, with a Follow button. */
@Composable
private fun WhoToFollow(vm: SocialViewModel, actions: NoteActions) {
    val following by vm.following.collectAsState()
    val list by androidx.compose.runtime.produceState<List<Pair<Profile, Int>>?>(null) { value = vm.followSuggestions() }
    when (val l = list) {
        null -> Spinner(Modifier.padding(top = 30.dp))
        else -> if (l.isEmpty()) EmptyNote("No suggestions yet. Follow a few people first.") else LazyColumn(Modifier.fillMaxSize()) {
            items(l, key = { it.first.pubkey }) { (p, shared) ->
                val on = p.pubkey in following
                Column {
                    Text(
                        "Followed by $shared people you follow", color = EInk.Caption, fontSize = 12.sp,
                        modifier = Modifier.padding(start = 68.dp, top = 8.dp)
                    )
                    PersonRow(p, isFollowing = on, onClick = { actions.openProfile(p.pubkey) }) {
                        EInkChip(text = if (on) "FOLLOWING" else "FOLLOW", selected = !on, onClick = { actions.follow(p.pubkey, !on) })
                    }
                }
            }
        }
    }
}

@Composable
internal fun PersonRow(profile: Profile, isFollowing: Boolean, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Avatar(profile, profile.pubkey, size = 44)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(profile.bestName, color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (isFollowing) Text("  · Following", color = EInk.Caption, fontSize = 12.sp)
            }
            Text(handleText(profile, profile.pubkey), color = EInk.Caption, fontSize = 13.sp, maxLines = 1)
            profile.about?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, color = EInk.Label, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
}
