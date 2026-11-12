package com.example.btcwidget.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk

// ---- Pieces of the composer (new post, reply, quote) ----

/** A button on the composer's toolbar: an orange line icon; a soft orange disc while it's on. */
@Composable
internal fun ComposeToolButton(icon: ImageVector, label: String, enabled: Boolean = true, on: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (on) SettingsAccent.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = SettingsAccent.copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(22.dp))
    }
}

/** A note on what the post will do (scheduled, sensitive…), with ✕ to undo it when [onClear] is given. */
@Composable
internal fun ComposeStatusChip(icon: ImageVector, text: String, onClear: (() -> Unit)? = null, alert: Boolean = false) {
    val tint = if (alert) EInk.Alert else SettingsAccent
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.12f))
            .padding(start = 10.dp, end = if (onClear != null) 4.dp else 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Text(text, color = EInk.Value, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
        if (onClear != null) Box(
            Modifier.padding(start = 2.dp).size(24.dp).clip(CircleShape).clickable(onClickLabel = tr("Undo"), onClick = onClear),
            contentAlignment = Alignment.Center
        ) { Icon(Glyphs.CloseLine, contentDescription = tr("Undo"), tint = EInk.Caption, modifier = Modifier.size(13.dp)) }
    }
}

/** A round-ended choice (poll length, one or many answers): orange when chosen. */
@Composable
internal fun ChoicePill(label: String, selected: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .then(if (selected) Modifier.background(SettingsAccent) else Modifier.border(1.dp, EInk.BodyStroke, RoundedCornerShape(50)))
            .clickable(onClick = onClick)
            .padding(start = if (icon != null) 10.dp else 14.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val ink = if (selected) Color.Black else EInk.Value
        if (icon != null) Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.padding(end = 6.dp).size(15.dp))
        Text(label, color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * The post being answered, above the reply box, as on X: its author's picture with a line
 * running down to yours, the name, handle and time, and its words.
 */
@Composable
internal fun ReplyParent(note: Event, author: Profile?) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(top = 12.dp)) {
        Column(Modifier.width(40.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(author, note.pubkey, size = 40)
            Box(Modifier.padding(top = 4.dp).width(2.dp).weight(1f).clip(RoundedCornerShape(1.dp)).background(EInk.BodyStroke))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp, bottom = 14.dp)) {
            NameLine(note, author)
            Text(note.content.trim().take(500), color = EInk.Value, fontSize = 15.sp, lineHeight = 20.sp, maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** Name, handle and how long ago, on one line. */
@Composable
private fun NameLine(note: Event, author: Profile?, small: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (small) Box(Modifier.padding(end = 6.dp)) { Avatar(author, note.pubkey, size = 20) }
        Text(
            author?.bestName ?: Profile.shortNpub(note.pubkey), color = EInk.Value, fontSize = if (small) 14.sp else 15.sp,
            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            " · " + relativeTime(note.createdAt), color = EInk.Caption, fontSize = if (small) 13.sp else 14.sp, maxLines = 1
        )
    }
}

/** The post being quoted, under yours, in a rounded box as on X. */
@Composable
internal fun QuotePreview(note: Event, author: Profile?) {
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(16.dp))
            .border(1.dp, EInk.BodyStroke, RoundedCornerShape(16.dp)).padding(12.dp)
    ) {
        NameLine(note, author, small = true)
        Text(note.content.trim().take(500), color = EInk.Value, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

/** A photo or video added to the post, with a round ✕ to take it off. Null [attachment]: still uploading. */
@Composable
internal fun AttachmentTile(attachment: SocialViewModel.Attachment?, single: Boolean, onRemove: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val size: Modifier = if (single) Modifier.fillMaxWidth().heightIn(max = 280.dp).aspectRatio(16f / 10f) else Modifier.size(150.dp)
    Box(size.clip(shape).background(EInk.Body)) {
        when {
            attachment == null -> CircularProgressIndicator(color = SettingsAccent, strokeWidth = 2.dp, modifier = Modifier.align(Alignment.Center).size(24.dp))
            attachment.isVideo -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                    Icon(Glyphs.PlayLine, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Text(tr("Video"), color = EInk.Label, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
            else -> AsyncImage(model = attachment.url, contentDescription = tr("Attached image"), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (attachment != null) Box(
            Modifier.align(Alignment.TopEnd).padding(8.dp).size(30.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
                .clickable(onClickLabel = tr("Remove image"), onClick = onRemove),
            contentAlignment = Alignment.Center
        ) { Icon(Glyphs.CloseLine, contentDescription = tr("Remove image"), tint = Color.White, modifier = Modifier.size(15.dp)) }
    }
}

/** People to @mention as you type: picture, name with its badge, and handle, in a card. */
@Composable
internal fun MentionSuggestions(people: List<Profile>, handleOf: (Profile) -> String, onPick: (Profile) -> Unit) {
    if (people.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(16.dp)).background(EInk.Body).padding(vertical = 4.dp)
    ) {
        people.forEach { p ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                    .clickable(onClickLabel = tr("Mention {0}", p.bestName)) { onPick(p) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Avatar(p, p.pubkey, size = 34)
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    DisplayName(p, p.pubkey, EInk.Value, 15.sp)
                    Text("@" + handleOf(p), color = EInk.Caption, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Space between the composer's blocks. */
internal val ComposeGap: Dp = 12.dp

/** Hashtags to finish what you're typing, as round pills. */
@Composable
internal fun HashtagSuggestions(tags: List<String>, onPick: (String) -> Unit) {
    if (tags.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState())
    ) { tags.forEach { tag -> ChoicePill("#$tag", selected = false, icon = null) { onPick(tag) } } }
}
