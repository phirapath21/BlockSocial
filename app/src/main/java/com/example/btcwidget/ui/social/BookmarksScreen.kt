package com.example.btcwidget.ui.social

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.example.btcwidget.LoginKey
import com.example.btcwidget.nostr.FeedItem

/** Saved notes (NIP-51 public bookmarks), newest bookmark first. */
@Composable
fun BookmarksScreen(vm: SocialViewModel, navigate: (NavKey) -> Unit) {
    val account by vm.account.collectAsState()
    val ids by vm.bookmarks.collectAsState()
    val stats by vm.stats.collectAsState()
    val items by produceState<List<FeedItem>?>(null, ids) { value = vm.bookmarkedNotes() }
    SocialScaffold(vm, navigate) { actions ->
        when {
            account == null -> {
                EmptyNote("Log in to save notes for later.")
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PillButton("Log in") { navigate(LoginKey) } }
            }
            ids.isEmpty() -> EmptyNote("No bookmarks yet. Use ⋯ › Bookmark on a note. Bookmarks are public on Nostr.")
            items == null -> Spinner(Modifier.padding(top = 30.dp))
            else -> NoteList(items.orEmpty(), stats, actions)
        }
    }
}
