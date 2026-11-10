package com.example.btcwidget.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.btcwidget.nostr.NoteStats
import com.example.btcwidget.nostr.core.Content
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.nostr.core.ThreadRef
import com.example.btcwidget.ui.main.EInk

/** Everything a note row can do; screens pass one shared instance. */
class NoteActions(
    val openNote: (Event) -> Unit,
    val openProfile: (String) -> Unit,
    val openHashtag: (String) -> Unit,
    val reply: (Event) -> Unit,
    val repost: (Event) -> Unit,
    val quote: (Event) -> Unit,
    val like: (Event) -> Unit,
    val zap: (Event) -> Unit,
    val mute: (String) -> Unit,
    val report: (Event) -> Unit,
    val delete: (Event) -> Unit,
    val share: (Event) -> Unit,
    val profileOf: (String) -> Profile?,
    val resolveNote: suspend (String) -> Event?,
    val me: String?
)

/** A timeline row, X-style: avatar, name · handle · time, text, media, quoted note, actions. */
@Composable
fun NoteCard(
    note: Event,
    stats: NoteStats,
    actions: NoteActions,
    repostedBy: String? = null,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    large: Boolean = false
) {
    val author = actions.profileOf(note.pubkey)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { actions.openNote(note) }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        if (repostedBy != null) {
            Text(
                text = "⟲ ${actions.profileOf(repostedBy)?.bestName ?: Profile.shortNpub(repostedBy)} reposted",
                color = EInk.Caption,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(start = 38.dp, bottom = 4.dp)
                    .clickable { actions.openProfile(repostedBy) }
            )
        }
        Row {
            Avatar(author, note.pubkey, size = 44, onClick = { actions.openProfile(note.pubkey) })
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                AuthorLine(note, author, actions)
                ThreadRef.of(note).parent?.let { parentId ->
                    val parentAuthor = note.tagValues("p").firstOrNull()
                    if (parentAuthor != null) {
                        Text(
                            text = "Replying to @" + (actions.profileOf(parentAuthor)?.bestName ?: Profile.shortNpub(parentAuthor)),
                            color = EInk.Caption,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                }
                NoteBody(note, actions, fontSize = if (large) 17 else 15)
                ActionRow(note, stats, actions)
            }
        }
    }
    if (showDivider) HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
}

@Composable
private fun AuthorLine(note: Event, author: Profile?, actions: NoteActions) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = author?.bestName ?: Profile.shortNpub(note.pubkey),
            color = EInk.Value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        val handle = author?.nip05?.removePrefix("_@") ?: author?.name?.let { "@$it" }
        Text(
            text = listOfNotNull(handle, relativeTime(note.createdAt)).joinToString(" · ", prefix = " "),
            color = EInk.Caption,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Box {
            Text(
                text = "⋯",
                color = EInk.Caption,
                fontSize = 18.sp,
                modifier = Modifier
                    .clickable(onClickLabel = "More options") { menu = true }
                    .padding(horizontal = 6.dp)
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Share") }, onClick = { menu = false; actions.share(note) })
                if (note.pubkey == actions.me) {
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; actions.delete(note) })
                } else {
                    DropdownMenuItem(text = { Text("Mute this account") }, onClick = { menu = false; actions.mute(note.pubkey) })
                    DropdownMenuItem(text = { Text("Report") }, onClick = { menu = false; actions.report(note) })
                }
            }
        }
    }
}

/** Note text with tappable links, hashtags and mentions; images and quoted notes below it. */
@Composable
fun NoteBody(note: Event, actions: NoteActions, fontSize: Int = 15, depth: Int = 0) {
    val segments = remember(note.id) {
        // Links that imeta tags mark as images show as images even without a file extension.
        val images = com.example.btcwidget.nostr.core.Imeta.imageUrls(note)
        Content.parse(note.content.trim()).map { seg ->
            if (seg is Content.Segment.Link && seg.url in images) Content.Segment.Image(seg.url) else seg
        }
    }
    val uriHandler = LocalUriHandler.current
    val linkStyle = TextLinkStyles(SpanStyle(color = EInk.Value, fontWeight = FontWeight.Bold))
    val text: AnnotatedString = buildAnnotatedString {
        for (seg in segments) when (seg) {
            is Content.Segment.Text -> append(seg.text)
            is Content.Segment.Link -> withLink(LinkAnnotation.Clickable("url", linkStyle) { uriHandler.openUri(seg.url) }) {
                append(seg.url.removePrefix("https://").removePrefix("http://").let { if (it.length > 40) it.take(38) + "…" else it })
            }
            is Content.Segment.Hashtag -> withLink(LinkAnnotation.Clickable("tag", linkStyle) { actions.openHashtag(seg.tag) }) {
                append("#" + seg.tag)
            }
            is Content.Segment.ProfileRef -> withLink(LinkAnnotation.Clickable("p", linkStyle) { actions.openProfile(seg.pubkey) }) {
                append("@" + (actions.profileOf(seg.pubkey)?.bestName ?: Profile.shortNpub(seg.pubkey)))
            }
            // Images and quoted notes are shown as blocks below.
            is Content.Segment.Image, is Content.Segment.NoteRef -> Unit
        }
    }
    val shown = text.text.trim()
    if (shown.isNotEmpty()) {
        Text(
            text = text,
            color = EInk.Value,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.35).sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
    segments.filterIsInstance<Content.Segment.Image>().take(4).forEach { img ->
        AsyncImage(
            model = img.url,
            contentDescription = "Image",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(14.dp))
                .clickable { uriHandler.openUri(img.url) }
        )
    }
    if (depth == 0) {
        segments.filterIsInstance<Content.Segment.NoteRef>().distinctBy { it.id }.take(2).forEach { ref ->
            QuotedNote(ref.id, actions)
        }
    }
}

/** A quoted note, boxed, as in a quote post. */
@Composable
private fun QuotedNote(id: String, actions: NoteActions) {
    val quoted by produceState<Event?>(null, id) { value = actions.resolveNote(id) }
    Column(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(14.dp))
            .clickable(enabled = quoted != null) { quoted?.let(actions.openNote) }
            .padding(10.dp)
    ) {
        val q = quoted
        if (q == null) {
            Text("Loading quoted note…", color = EInk.Caption, fontSize = 13.sp)
        } else {
            val author = actions.profileOf(q.pubkey)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(author, q.pubkey, size = 20, onClick = { actions.openProfile(q.pubkey) })
                Spacer(Modifier.width(6.dp))
                Text(author?.bestName ?: Profile.shortNpub(q.pubkey), color = EInk.Value, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(" · " + relativeTime(q.createdAt), color = EInk.Caption, fontSize = 13.sp)
            }
            NoteBody(q, actions, fontSize = 14, depth = 1)
        }
    }
}

/** Reply · Repost (menu: repost or quote) · Like · Zap, with counts. */
@Composable
private fun ActionRow(note: Event, stats: NoteStats, actions: NoteActions) {
    var repostMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ActionButton("💬", stats.replies, "Reply", highlighted = false) { actions.reply(note) }
        Box {
            ActionButton("⟲", stats.reposts, "Repost or quote", highlighted = stats.repostedByMe) { repostMenu = true }
            DropdownMenu(expanded = repostMenu, onDismissRequest = { repostMenu = false }) {
                DropdownMenuItem(text = { Text("Repost") }, onClick = { repostMenu = false; actions.repost(note) }, enabled = !stats.repostedByMe)
                DropdownMenuItem(text = { Text("Quote") }, onClick = { repostMenu = false; actions.quote(note) })
            }
        }
        ActionButton(if (stats.likedByMe) "♥" else "♡", stats.likes, if (stats.likedByMe) "Liked" else "Like", highlighted = stats.likedByMe) { actions.like(note) }
        ActionButton("⚡", (stats.zapMsat / 1000).toInt(), "Zap", highlighted = false) { actions.zap(note) }
        Spacer(Modifier.width(24.dp))
    }
}

@Composable
private fun ActionButton(icon: String, count: Int, label: String, highlighted: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(icon, color = if (highlighted) EInk.Value else EInk.Caption, fontSize = 15.sp)
        if (count > 0) {
            Text(
                text = " " + compactCount(count),
                color = if (highlighted) EInk.Value else EInk.Caption,
                fontSize = 13.sp
            )
        }
    }
}

/** Round profile picture, or the first letter of the name on a tinted disc. */
@Composable
fun Avatar(profile: Profile?, pubkey: String, size: Int, onClick: (() -> Unit)? = null) {
    val base = Modifier
        .size(size.dp)
        .clip(CircleShape)
        .background(EInk.Track)
        .let { if (onClick != null) it.clickable(onClickLabel = "Open profile", onClick = onClick) else it }
    val picture = profile?.picture
    if (picture != null) {
        AsyncImage(model = picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = base)
    } else {
        Box(base, contentAlignment = Alignment.Center) {
            Text(
                text = (profile?.bestName ?: pubkey).take(1).uppercase(),
                color = EInk.Value,
                fontSize = (size * 0.42).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** "now", "5m", "3h", "2d", then "12 Mar" / "12 Mar 2025". */
fun relativeTime(createdAt: Long, now: Long = System.currentTimeMillis() / 1000): String {
    val s = (now - createdAt).coerceAtLeast(0)
    return when {
        s < 60 -> "now"
        s < 3600 -> "${s / 60}m"
        s < 86_400 -> "${s / 3600}h"
        s < 7 * 86_400 -> "${s / 86_400}d"
        else -> {
            val date = java.util.Date(createdAt * 1000)
            val sameYear = java.text.SimpleDateFormat("yyyy", java.util.Locale.US).let { it.format(date) == it.format(java.util.Date(now * 1000)) }
            java.text.SimpleDateFormat(if (sameYear) "d MMM" else "d MMM yyyy", java.util.Locale.getDefault()).format(date)
        }
    }
}

/** 999, 1.2K, 3.4M. */
fun compactCount(n: Int): String = when {
    n < 1000 -> n.toString()
    n < 1_000_000 -> String.format(java.util.Locale.US, "%.1fK", n / 1000.0).replace(".0K", "K")
    else -> String.format(java.util.Locale.US, "%.1fM", n / 1_000_000.0).replace(".0M", "M")
}
