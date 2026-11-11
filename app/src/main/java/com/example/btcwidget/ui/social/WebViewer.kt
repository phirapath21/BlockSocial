package com.example.btcwidget.ui.social

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.btcwidget.nostr.NoteStats
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk

/**
 * Block's in-app browser, like X's: ✕, the site and reload on top, the page, and the post
 * the link came from in a sheet underneath (drag or tap its handle to show more or less),
 * with Follow and the usual actions. Back steps back through the page first.
 */
@Composable
fun WebViewer(url: String, note: Event?, stats: NoteStats, actions: NoteActions, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val page = remember(url) { WebPageState() }
    var menu by remember { mutableStateOf(false) }
    // 0 = just the handle, 1 = author and actions, 2 = also the text.
    var sheet by remember { mutableStateOf(1) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false)) {
        BackHandler { if (!page.goBack()) onDismiss() }
        Column(Modifier.fillMaxSize().background(EInk.Background).statusBarsPadding()) {
            // Top bar.
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                BarButton("✕", "Close", onDismiss)
                Column(Modifier.weight(1f).padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(page.title.ifBlank { "Loading…" }, color = EInk.Value, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        (if ((page.url.ifBlank { url }).startsWith("https://")) "🔒 " else "") + hostOf(page.url.ifBlank { url }),
                        color = EInk.Caption, fontSize = 12.sp, maxLines = 1
                    )
                }
                BarButton("↻", "Reload") { page.reload() }
                Box {
                    BarButton("⋮", "More options") { menu = true }
                    BlockDropdown(expanded = menu, onDismiss = { menu = false }) {
                        BlockDropdownItem("Open in browser", Glyphs.OpenInBrowser) { menu = false; openExternally(context, page.url.ifBlank { url }) }
                        BlockDropdownItem("Copy link", Glyphs.Link) { menu = false; copyLink(context, page.url.ifBlank { url }) }
                        BlockDropdownItem("Share link", Glyphs.Share) { menu = false; shareLink(context, page.url.ifBlank { url }) }
                    }
                }
            }
            if (page.progress in 1..99) LinearProgressIndicator(
                progress = { page.progress / 100f }, color = Color(0xFFF7931A), trackColor = Color.Transparent,
                modifier = Modifier.fillMaxWidth().height(2.dp)
            ) else HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)

            // The page.
            WebPage(url, page, Modifier.weight(1f).fillMaxWidth())

            // The post the link came from.
            if (note != null) PostSheet(note, stats, actions, sheet, onSheet = { sheet = it }, onLeave = onDismiss)
        }
    }
}

@Composable
private fun PostSheet(note: Event, stats: NoteStats, actions: NoteActions, level: Int, onSheet: (Int) -> Unit, onLeave: () -> Unit) {
    val author = actions.profileOf(note.pubkey)
    val following = actions.isFollowing(note.pubkey)
    var drag by remember { mutableStateOf(0f) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(EInk.Body)
            .animateContentSize()
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { drag += it },
                onDragStopped = {
                    // Up shows more, down shows less.
                    if (drag < -40) onSheet((level + 1).coerceAtMost(2)) else if (drag > 40) onSheet((level - 1).coerceAtLeast(0))
                    drag = 0f
                }
            )
            .navigationBarsPadding()
            .padding(horizontal = 14.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().clickable(onClickLabel = "Show more or less of the post") { onSheet(if (level == 2) 0 else level + 1) }.padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) { Box(Modifier.size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(50)).background(EInk.Caption)) }
        if (level >= 1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(author, note.pubkey, size = 40, onClick = { onLeave(); actions.openProfile(note.pubkey) })
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(author?.bestName ?: Profile.shortNpub(note.pubkey), color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(handleText(author, note.pubkey), color = EInk.Caption, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (note.pubkey != actions.me) Text(
                    if (following) "Following" else "Follow",
                    color = if (following) EInk.Value else Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (following) EInk.Track else EInk.Value)
                        .clickable { actions.follow(note.pubkey, !following) }.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            if (level >= 2) Text(
                plainText(note, actions), color = EInk.Value, fontSize = 15.sp, maxLines = 8, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp).clickable(onClickLabel = "Open post") { onLeave(); actions.openNote(note) }
            )
            ActionRow(note, stats, actions)
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun BarButton(glyph: String, label: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(glyph, color = EInk.Value, fontSize = 20.sp)
    }
}

private fun hostOf(url: String): String = url.substringAfter("://").substringBefore('/').removePrefix("www.")

private fun copyLink(context: Context, url: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("link", url))
    Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
}

private fun shareLink(context: Context, url: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
    context.startActivity(Intent.createChooser(send, "Share link").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
