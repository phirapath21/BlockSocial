package com.example.btcwidget.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.example.btcwidget.ComposeKey
import com.example.btcwidget.LoginKey
import com.example.btcwidget.nostr.FeedItem
import com.example.btcwidget.nostr.NostrRepository
import com.example.btcwidget.nostr.NoteStats
import com.example.btcwidget.ui.main.EInk

// ---- Thread ----

@Composable
fun ThreadScreen(vm: SocialViewModel, noteId: String, navigate: (NavKey) -> Unit, onBack: () -> Unit) {
    val stats by vm.stats.collectAsState()
    // Saved, so Back to this thread after replying shows the version with your reply.
    var reload by rememberSaveable { mutableStateOf(0) }
    val thread by produceKept<NostrRepository.Thread?>(null, noteId, reload) { value = vm.thread(noteId) }
    Column(Modifier.fillMaxSize().background(EInk.Background)) {
        SocialTopBar(tr("Post"), onBack)
        SocialScaffold(vm, navigate) { actions ->
            val t = thread
            if (t == null) {
                Spinner(Modifier.padding(top = 40.dp))
            } else {
                Box(Modifier.fillMaxSize()) {
                    // Room at the end so the last reply can scroll above the floating card.
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                        items(t.ancestors, key = { "a" + it.id }) { NoteCard(it, stats[it.id] ?: NoteStats(), actions) }
                        item(key = "focus") { NoteCard(t.focus, stats[t.focus.id] ?: NoteStats(), actions, large = true) }
                        items(t.replies, key = { "r" + it.event.id }) { row ->
                            NoteCard(row.event, stats[row.event.id] ?: NoteStats(), actions, modifier = Modifier.threadIndent(row.depth))
                        }
                        if (t.replies.isEmpty()) item { EmptyNote(tr("No replies yet.")) }
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
    val feed by produceKept<List<FeedItem>?>(null, tag) { value = vm.hashtagFeed(tag) }
    val followed by vm.followedTags.collectAsState()
    val account by vm.account.collectAsState()
    val isFollowed = tag.lowercase().removePrefix("#") in followed
    Column(Modifier.fillMaxSize().background(EInk.Background)) {
        SocialTopBar("#$tag", onBack)
        SocialScaffold(vm, navigate) { actions ->
            NoteList(feed.orEmpty(), stats, actions, header = {
                // Follow the topic: its notes join Latest, and other apps see it in your follow list.
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isFollowed) tr("Notes with #{0} are in your Latest.", tag) else tr("Follow to see notes with #{0} in your Latest.", tag),
                        color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.weight(1f)
                    )
                    PillButton(if (isFollowed) tr("Following") else tr("Follow"), filled = !isFollowed) {
                        if (account == null) navigate(LoginKey) else vm.followHashtag(tag, !isFollowed)
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
                when (val f = feed) {
                    null -> Spinner(Modifier.padding(top = 40.dp))
                    else -> if (f.isEmpty()) EmptyNote(tr("No notes with #{0} yet.", tag))
                }
            })
        }
    }
}
