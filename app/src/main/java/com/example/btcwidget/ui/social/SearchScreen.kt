package com.example.btcwidget.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import kotlinx.coroutines.launch

/**
 * Search: an npub, note id, nevent, "#tag" or name@domain opens directly; anything else
 * searches people and posts on NIP-50 search relays.
 */
@Composable
fun SearchScreen(vm: SocialViewModel, navigate: (NavKey) -> Unit, onBack: () -> Unit) {
    val stats by vm.stats.collectAsState()
    val following by vm.following.collectAsState()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf("people") }
    var searched by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var people by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var notes by remember { mutableStateOf<List<FeedItem>>(emptyList()) }
    var job by remember { mutableStateOf<Job?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

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
                null -> Unit
            }
            people = vm.searchPeople(q)
            notes = vm.searchNotes(q)
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().background(EInk.Background)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.statusBarsPadding().fillMaxWidth().padding(end = 12.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Text(
                "←", color = EInk.Value, fontSize = 22.sp,
                modifier = Modifier.clickable(onClickLabel = "Back", onClick = onBack).padding(horizontal = 14.dp, vertical = 8.dp)
            )
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
            EInkChip(text = "POSTS" + if (searched != null && !loading) " ${notes.size}" else "", selected = tab == "posts", onClick = { tab = "posts" })
        }
        HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)

        SocialScaffold(vm, navigate) { actions ->
            when {
                loading -> Spinner(Modifier.padding(top = 30.dp))
                searched == null -> EmptyNote(
                    "Find people by name, npub or name@domain, posts by words, or a topic with #tag. " +
                        "Word search uses search relays (relay.nostr.band, search.nos.today)."
                )
                tab == "people" -> if (people.isEmpty()) EmptyNote("No people found for \"$searched\".") else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(people, key = { it.pubkey }) { p ->
                            PersonRow(p, isFollowing = p.pubkey in following, onClick = { navigate(ProfileKey(p.pubkey)) })
                        }
                    }
                }
                else -> if (notes.isEmpty()) EmptyNote("No posts found for \"$searched\".") else NoteList(notes, stats, actions)
            }
        }
    }
}

@Composable
private fun PersonRow(profile: Profile, isFollowing: Boolean, onClick: () -> Unit) {
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
            Text(profile.nip05?.removePrefix("_@") ?: Profile.shortNpub(profile.pubkey), color = EInk.Caption, fontSize = 13.sp, maxLines = 1)
            profile.about?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, color = EInk.Label, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
}
