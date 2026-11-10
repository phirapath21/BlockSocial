package com.example.btcwidget.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
    val bookmark: (Event) -> Unit,
    val pin: (Event) -> Unit,
    val isPinned: (String) -> Boolean,
    val isBookmarked: (String) -> Boolean,
    /** Opens an image full screen, with its note's author and actions. */
    val openImage: (url: String, note: Event) -> Unit,
    val follow: (pubkey: String, follow: Boolean) -> Unit,
    /** Opens who reposted (0), liked (1) or zapped (2) a note. */
    val openEngagement: (note: Event, tab: Int) -> Unit,
    val isFollowing: (String) -> Boolean,
    val profileOf: (String) -> Profile?,
    val resolveNote: suspend (String) -> Event?,
    val resolveArticle: suspend (com.example.btcwidget.nostr.core.Nip19.Address) -> com.example.btcwidget.nostr.core.Article?,
    val openArticle: (com.example.btcwidget.nostr.core.Nip19.Address) -> Unit,
    val linkPreview: suspend (String) -> com.example.btcwidget.nostr.core.LinkPreview?,
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
                // The opened post shows its full time, as X does under a post.
                if (large) {
                    Text(
                        fullTimestamp(note.createdAt),
                        color = EInk.Caption, fontSize = 14.sp,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    EngagementCounts(note, stats, actions)
                }
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
        val handle = handleText(author, note.pubkey)
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
                DropdownMenuItem(
                    text = { Text(if (actions.isBookmarked(note.id)) "Remove bookmark" else "Bookmark") },
                    onClick = { menu = false; actions.bookmark(note) }
                )
                if (note.pubkey == actions.me) {
                    DropdownMenuItem(
                        text = { Text(if (actions.isPinned(note.id)) "Unpin from profile" else "Pin to profile") },
                        onClick = { menu = false; actions.pin(note) }
                    )
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
    // NIP-36: hide behind a warning until the reader chooses to see it.
    val warning = note.firstTag("content-warning") ?: note.tags.firstOrNull { it.firstOrNull() == "content-warning" }
    var revealed by remember(note.id) { mutableStateOf(false) }
    if (warning != null && !revealed) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(EInk.Body)
                .padding(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text("⚠ Sensitive content", color = EInk.Value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                warning.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { Text(it, color = EInk.Label, fontSize = 13.sp) }
            }
            Text(
                "Show", color = EInk.Value, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClickLabel = "Show sensitive content") { revealed = true }.padding(8.dp)
            )
        }
        return
    }
    val segments = remember(note.id) {
        // Links that imeta tags mark as images show as images even without a file extension.
        val images = com.example.btcwidget.nostr.core.Imeta.imageUrls(note)
        val videos = com.example.btcwidget.nostr.core.Imeta.videoUrls(note)
        Content.parse(note.content.trim()).map { seg ->
            when {
                seg is Content.Segment.Link && seg.url in images -> Content.Segment.Image(seg.url)
                seg is Content.Segment.Link && seg.url in videos -> Content.Segment.Video(seg.url)
                else -> seg
            }
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
            is Content.Segment.Image, is Content.Segment.Video, is Content.Segment.NoteRef, is Content.Segment.AddressRef -> Unit
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
                .clickable(onClickLabel = "View image") { actions.openImage(img.url, note) }
        )
    }
    segments.filterIsInstance<Content.Segment.Video>().take(2).forEach { VideoPlayer(it.url) }
    // A card for the first web link (not for posts that already show photos, video or a quote).
    if (depth == 0 && segments.none { it is Content.Segment.Image || it is Content.Segment.Video || it is Content.Segment.NoteRef || it is Content.Segment.AddressRef }) {
        segments.filterIsInstance<Content.Segment.Link>().firstOrNull()?.let { LinkCard(it.url, actions) }
    }
    if (depth == 0) {
        segments.filterIsInstance<Content.Segment.NoteRef>().distinctBy { it.id }.take(2).forEach { ref ->
            QuotedNote(ref.id, actions)
        }
        segments.filterIsInstance<Content.Segment.AddressRef>().filter { it.address.kind == com.example.btcwidget.nostr.core.Kind.ARTICLE }
            .distinctBy { it.address.identifier + it.address.pubkey }.take(2).forEach { ArticleCard(it.address, actions) }
    }
}

/** Link preview: image, site and title, boxed like X's cards. Nothing shows until it loads. */
@Composable
private fun LinkCard(url: String, actions: NoteActions) {
    val uriHandler = LocalUriHandler.current
    val preview by produceState<com.example.btcwidget.nostr.core.LinkPreview?>(null, url) { value = actions.linkPreview(url) }
    val p = preview ?: return
    Column(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(14.dp))
            .clickable(onClickLabel = "Open link") { uriHandler.openUri(url) }
    ) {
        p.image?.let {
            AsyncImage(
                model = it, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(1.91f).background(EInk.Track)
            )
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(p.site, color = EInk.Caption, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(p.title, color = EInk.Value, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            p.description?.let { Text(it, color = EInk.Label, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** A linked long-form article: cover, title and summary, boxed. */
@Composable
private fun ArticleCard(address: com.example.btcwidget.nostr.core.Nip19.Address, actions: NoteActions) {
    val article by produceState<com.example.btcwidget.nostr.core.Article?>(null, address) { value = actions.resolveArticle(address) }
    Column(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(14.dp))
            .clickable { actions.openArticle(address) }
    ) {
        val a = article
        a?.image?.let { url ->
            coil3.compose.AsyncImage(
                model = url, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(140.dp)
            )
        }
        Column(Modifier.padding(10.dp)) {
            Text("ARTICLE", color = EInk.Caption, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(a?.title ?: "Loading article…", color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            a?.summary?.let { Text(it, color = EInk.Label, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
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

/** "11 Reposts  51 Likes  2.1K sats" under an opened post; each opens the list of people. */
@Composable
private fun EngagementCounts(note: Event, stats: NoteStats, actions: NoteActions) {
    val parts = listOf(
        Triple(stats.reposts, if (stats.reposts == 1) "Repost" else "Reposts", 0),
        Triple(stats.likes, if (stats.likes == 1) "Like" else "Likes", 1),
        Triple((stats.zapMsat / 1000).toInt(), "sats", 2)
    ).filter { it.first > 0 }
    if (parts.isEmpty()) return
    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke, modifier = Modifier.padding(top = 10.dp))
    Row(Modifier.padding(vertical = 8.dp)) {
        parts.forEach { (count, label, tab) ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClickLabel = "See who: $label") { actions.openEngagement(note, tab) }
                    .padding(end = 16.dp, top = 2.dp, bottom = 2.dp)
            ) {
                Text(compactCount(count), color = EInk.Value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(" $label", color = EInk.Caption, fontSize = 14.sp)
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
}

/** "Tue, Nov 10, 2026 - 2:10 PM" in the phone's time zone (English, 12-hour). */
fun fullTimestamp(createdAt: Long, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): String =
    java.text.SimpleDateFormat("EEE, MMM d, yyyy - h:mm a", java.util.Locale.US)
        .apply { timeZone = zone }
        .format(java.util.Date(createdAt * 1000))

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
