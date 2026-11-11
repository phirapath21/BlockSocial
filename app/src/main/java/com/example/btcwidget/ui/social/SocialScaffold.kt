package com.example.btcwidget.ui.social

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import com.example.btcwidget.ui.main.EInk
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.btcwidget.ComposeKey
import com.example.btcwidget.HashtagKey
import com.example.btcwidget.LoginKey
import com.example.btcwidget.NoteKey
import com.example.btcwidget.ProfileKey
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Nip19
import com.example.btcwidget.ui.main.EInkChip
import androidx.navigation3.runtime.NavKey

/**
 * Wraps a Nostr screen: builds the shared [NoteActions], shows toasts from the view model,
 * and hosts the zap, report and confirm dialogs every screen needs.
 */
@Composable
fun SocialScaffold(
    vm: SocialViewModel,
    navigate: (NavKey) -> Unit,
    content: @Composable (NoteActions) -> Unit
) {
    val context = LocalContext.current
    val profiles by vm.profiles.collectAsState()
    val account by vm.account.collectAsState()
    var zapTarget by remember { mutableStateOf<Event?>(null) }
    var reportTarget by remember { mutableStateOf<Event?>(null) }
    var deleteTarget by remember { mutableStateOf<Event?>(null) }
    var viewImage by remember { mutableStateOf<Pair<String, Event>?>(null) }
    var viewVideo by remember { mutableStateOf<Pair<String, Event>?>(null) }
    var viewWeb by remember { mutableStateOf<Pair<String, Event?>?>(null) }
    val linksInApp by vm.linksInApp.collectAsState()
    val autoplay by vm.videoAutoplay.collectAsState()
    val bookmarks by vm.bookmarks.collectAsState()
    val pins by vm.pinned.collectAsState()
    val following by vm.following.collectAsState()
    val stats by vm.stats.collectAsState()
    val nwcWallet by vm.nwc.collectAsState()
    val customEmojis by vm.emojis.collectAsState()

    LaunchedEffect(vm) {
        vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    // Actions that change something need an account: send logged-out users to log in.
    fun signedIn(block: () -> Unit) = if (account == null) navigate(LoginKey) else block()

    val actions = NoteActions(
        openNote = { navigate(NoteKey(it.id)) },
        openProfile = { navigate(ProfileKey(it)) },
        openHashtag = { navigate(HashtagKey(it)) },
        reply = { n -> signedIn { navigate(ComposeKey(replyTo = n.id)) } },
        repost = { n -> signedIn { vm.repost(n) } },
        quote = { n -> signedIn { navigate(ComposeKey(quote = n.id)) } },
        like = { n -> signedIn { vm.like(n) } },
        zap = { n -> signedIn { zapTarget = n } },
        mute = { p -> signedIn { vm.mute(p, true) } },
        report = { n -> signedIn { reportTarget = n } },
        delete = { n -> deleteTarget = n },
        share = { n -> shareNote(context, n) },
        bookmark = { n -> signedIn { vm.toggleBookmark(n) } },
        isBookmarked = { it in bookmarks },
        pin = { n -> signedIn { vm.togglePin(n) } },
        isPinned = { it in pins },
        openImage = { url, note -> viewImage = url to note },
        openVideo = { url, note -> viewVideo = url to note },
        openLink = { url, note ->
            if (linksInApp && (url.startsWith("https://") || url.startsWith("http://"))) viewWeb = url to note
            else openExternally(context, url)
        },
        autoplayVideos = autoplay.allowedNow(context),
        openVideoUrl = viewVideo?.first,
        follow = { pk, on -> signedIn { vm.follow(pk, on) } },
        isFollowing = { it in following },
        openEngagement = { n, tab -> navigate(com.example.btcwidget.EngagementKey(n.id, tab)) },
        profileOf = { profiles[it] },
        resolveNote = { vm.note(it) },
        react = { n, content, url -> signedIn { vm.react(n, content, url) } },
        muteThread = { n -> signedIn { vm.muteThread(n) } },
        customEmojis = customEmojis,
        pollTally = { vm.pollTally(it) },
        vote = { poll, ids, done -> if (account == null) { navigate(LoginKey); done(false) } else vm.vote(poll, ids, done) },
        resolveArticle = { vm.article(it) },
        openArticle = { a -> navigate(com.example.btcwidget.ArticleKey(Nip19.naddr(a.kind, a.pubkey, a.identifier, a.relays))) },
        linkPreview = { vm.linkPreview(it) },
        me = account?.pubkey
    )

    content(actions)

    viewImage?.let { (url, note) ->
        ImageViewer(
            url, note, stats[note.id] ?: com.example.btcwidget.nostr.NoteStats(), actions,
            onDismiss = { viewImage = null },
            urls = noteImageUrls(note),
            statusBar = { PublishStatusBar(vm) },
            replyBar = { onActive ->
                ReplyBar(
                    vm, note, dark = true,
                    onLogin = { viewImage = null; navigate(com.example.btcwidget.LoginKey) },
                    onExpand = { viewImage = null; navigate(com.example.btcwidget.ComposeKey(replyTo = note.id)) },
                    onActiveChange = onActive
                )
            }
        )
    }

    viewVideo?.let { (url, note) ->
        ImageViewer(
            url, note, stats[note.id] ?: com.example.btcwidget.nostr.NoteStats(), actions,
            onDismiss = { viewVideo = null },
            video = true,
            statusBar = { PublishStatusBar(vm) },
            replyBar = { onActive ->
                ReplyBar(
                    vm, note, dark = true,
                    onLogin = { viewVideo = null; navigate(com.example.btcwidget.LoginKey) },
                    onExpand = { viewVideo = null; navigate(com.example.btcwidget.ComposeKey(replyTo = note.id)) },
                    onActiveChange = onActive
                )
            }
        )
    }

    viewWeb?.let { (url, note) ->
        WebViewer(url, note, note?.let { stats[it.id] } ?: com.example.btcwidget.nostr.NoteStats(), actions, onDismiss = { viewWeb = null })
    }

    zapTarget?.let { note ->
        ZapDialog(
            inApp = nwcWallet != null,
            recipientName = profiles[note.pubkey]?.bestName ?: "them",
            onDismiss = { zapTarget = null },
            onZap = { sats, comment ->
                zapTarget = null
                // A profile zap has no note (see fakeProfileNote).
                vm.zap(note.pubkey, sats, note.takeIf { it.id.isNotEmpty() }, comment) { invoice -> payInvoice(context, invoice) }
            }
        )
    }
    reportTarget?.let { note ->
        ReportDialog(onDismiss = { reportTarget = null }, onReport = { reason ->
            reportTarget = null
            vm.report(note, reason)
        })
    }
    deleteTarget?.let { note ->
        ConfirmDialog(
            title = "Delete post?",
            text = "Relays are asked to delete it, and it's removed from your profile and timelines. Copies others already saved may remain.",
            confirmLabel = "Delete",
            onConfirm = { deleteTarget = null; vm.delete(note) },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun ZapDialog(recipientName: String, inApp: Boolean, onDismiss: () -> Unit, onZap: (Long, String) -> Unit) {
    var amount by remember { mutableStateOf("21") }
    var comment by remember { mutableStateOf("") }
    val sats = amount.toLongOrNull()?.takeIf { it > 0 }
    BlockDialog(
        onDismiss = onDismiss,
        title = "Zap $recipientName",
        confirmLabel = sats?.let { "Zap ${"%,d".format(it)} sats" } ?: "Zap",
        confirmEnabled = sats != null,
        onConfirm = { sats?.let { onZap(it, comment) } }
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(21L, 100L, 500L, 1000L, 5000L, 21000L).forEach { preset ->
                val on = sats == preset
                Text(
                    "⚡ ${"%,d".format(preset)}", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = if (on) Color.Black else EInk.Value,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) ZapOrange else EInk.Track)
                        .clickable { amount = preset.toString() }.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
        OutlinedTextField(
            value = amount,
            onValueChange = { v -> amount = v.filter { it.isDigit() }.take(9) },
            label = { Text("Sats") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
        )
        OutlinedTextField(
            value = comment,
            onValueChange = { comment = it.take(140) },
            label = { Text("Comment (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        Text(
            if (inApp) "Pays from your connected wallet (NWC)." else "Opens your Lightning wallet to pay. Connect a wallet in Settings › Nostr to zap in one tap.",
            color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)
        )
    }
}

private val ZapOrange = Color(0xFFF7931A)

@Composable
private fun ReportDialog(onDismiss: () -> Unit, onReport: (String) -> Unit) {
    // NIP-56 report types.
    val reasons = listOf("spam" to "Spam", "nudity" to "Nudity", "profanity" to "Hateful or abusive", "illegal" to "Illegal", "impersonation" to "Impersonation", "other" to "Other")
    BlockDialog(
        onDismiss = onDismiss,
        title = "Report post",
        text = "Why are you reporting it? The report is public and goes to your relays (NIP-56)."
    ) {
        reasons.forEach { (code, label) -> ChoiceRow(label) { onReport(code) } }
    }
}

/** Opens a Lightning wallet for [invoice]; without one, copies it. */
fun payInvoice(context: Context, invoice: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("lightning:$invoice")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Lightning invoice", invoice))
        Toast.makeText(context, "No Lightning wallet found. Invoice copied.", Toast.LENGTH_LONG).show()
    }
}

/** Shares a link to the note (njump.me opens in any browser or Nostr app). */
fun shareNote(context: Context, note: Event) {
    val link = "https://njump.me/" + Nip19.nevent(note.id, author = note.pubkey)
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link)
    context.startActivity(Intent.createChooser(send, "Share note").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
