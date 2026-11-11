package com.example.btcwidget.ui.social

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.example.btcwidget.nostr.NoteStats
import com.example.btcwidget.nostr.core.Content
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Imeta
import com.example.btcwidget.nostr.core.Profile
import kotlinx.coroutines.launch

private val Scrim = Color(0xCC000000)
private val ButtonFill = Color(0xFF2A2F36)
private val OnDark = Color(0xFFEDEFF1)
private val OnDarkDim = Color(0xFF9AA0A6)

/**
 * Full-screen image, laid out like X: back and ⋮ (Save image, Open in browser) on top;
 * the author, the note's text and Reply · Repost · Like · Bookmark · Share at the bottom.
 * Pinch or double-tap to zoom, drag to pan.
 */
@Composable
fun ImageViewer(
    url: String,
    note: Event,
    stats: NoteStats,
    actions: NoteActions,
    onDismiss: () -> Unit,
    /** The reply card under the actions, told when it opens; null hides it. */
    replyBar: (@Composable (onActiveChange: (Boolean) -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var replying by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var menu by remember { mutableStateOf(false) }
    var repostMenu by remember { mutableStateOf(false) }

    fun save() = scope.launch {
        val result = ImageSaver.save(context, url)
        Toast.makeText(context, if (result.isSuccess) "Saved to Pictures/Blockface" else "Couldn't save the image", Toast.LENGTH_SHORT).show()
    }
    // Only Android 9 and below need a permission to save.
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) save() else Toast.makeText(context, "Storage permission is needed to save", Toast.LENGTH_SHORT).show()
    }

    val author = actions.profileOf(note.pubkey)
    val isMe = note.pubkey == actions.me
    val following = actions.isFollowing(note.pubkey)
    // The note's words without the image links themselves.
    val caption = remember(note.id) {
        val images = Imeta.imageUrls(note)
        Content.parse(note.content).filterNot { it is Content.Segment.Image || (it is Content.Segment.Link && it.url in images) }
            .joinToString("") { seg ->
                when (seg) {
                    is Content.Segment.Text -> seg.text
                    is Content.Segment.Link -> seg.url
                    is Content.Segment.Hashtag -> "#" + seg.tag
                    is Content.Segment.ProfileRef -> "@" + (actions.profileOf(seg.pubkey)?.bestName ?: Profile.shortNpub(seg.pubkey))
                    else -> ""
                }
            }.trim()
    }

    // Not fitting system windows, so the reply bar can rise above the keyboard (imePadding).
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = url,
                contentDescription = "Image",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                        })
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            )

            // Top: back and more.
            Row(
                Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundIcon(Glyphs.Back, "Back", onDismiss)
                Spacer(Modifier.weight(1f))
                Box {
                    RoundIcon(Glyphs.More, "More options") { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Save image") }, onClick = {
                            menu = false
                            if (ImageSaver.needsPermission) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else save()
                        })
                        DropdownMenuItem(text = { Text("Open in browser") }, onClick = {
                            menu = false
                            uriHandler.openUri(url)
                        })
                    }
                }
            }

            // Bottom: author, text, actions.
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Scrim)
                    .safeDrawingPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(author, note.pubkey, size = 44, onClick = { onDismiss(); actions.openProfile(note.pubkey) })
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(author?.bestName ?: Profile.shortNpub(note.pubkey), color = OnDark, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val handle = handleText(author, note.pubkey)
                        Text(handle, color = OnDarkDim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (!isMe) {
                        Text(
                            if (following) "Following" else "Follow",
                            color = OnDark, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(ButtonFill)
                                .clickable(onClickLabel = if (following) "Unfollow" else "Follow") { actions.follow(note.pubkey, !following) }
                                .padding(horizontal = 18.dp, vertical = 9.dp)
                        )
                    }
                }
                if (caption.isNotEmpty()) {
                    Text(
                        caption, color = OnDark, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .clickable(onClickLabel = "Open post") { onDismiss(); actions.openNote(note) }
                    )
                }
                // While replying, as on X: the photo, author and text stay; the buttons make way.
                if (!replying) Spacer(Modifier.height(12.dp))
                if (!replying) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PillAction(Glyphs.Comment, stats.replies, "Reply", Modifier.weight(1f)) { onDismiss(); actions.reply(note) }
                    Box(Modifier.weight(1f)) {
                        PillAction(Glyphs.Repost, stats.reposts, "Repost or quote", Modifier.fillMaxWidth(), active = stats.repostedByMe) { repostMenu = true }
                        DropdownMenu(expanded = repostMenu, onDismissRequest = { repostMenu = false }) {
                            DropdownMenuItem(text = { Text("Repost") }, enabled = !stats.repostedByMe, onClick = { repostMenu = false; actions.repost(note) })
                            DropdownMenuItem(text = { Text("Quote") }, onClick = { repostMenu = false; onDismiss(); actions.quote(note) })
                        }
                    }
                    PillAction(
                        if (stats.likedByMe) Glyphs.Liked else Glyphs.Like, stats.likes,
                        if (stats.likedByMe) "Liked" else "Like", Modifier.weight(1f), active = stats.likedByMe
                    ) { actions.like(note) }
                    val marked = actions.isBookmarked(note.id)
                    PillAction(if (marked) Glyphs.Bookmarked else Glyphs.Bookmark, null, if (marked) "Remove bookmark" else "Bookmark", active = marked) { actions.bookmark(note) }
                    PillAction(Glyphs.Share, null, "Share") { actions.share(note) }
                }
                if (replyBar != null) {
                    Spacer(Modifier.height(12.dp))
                    replyBar { replying = it }
                }
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(ButtonFill.copy(alpha = 0.85f))
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
    ) { Icon(icon, contentDescription = label, tint = OnDark, modifier = Modifier.size(24.dp)) }
}

/** A rounded action button with an optional count, as in X's media viewer. */
@Composable
private fun PillAction(icon: ImageVector, count: Int?, label: String, modifier: Modifier = Modifier, active: Boolean = false, onClick: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(50))
            .background(ButtonFill)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp)
    ) {
        Icon(icon, contentDescription = label, tint = if (active && label == "Liked") Color(0xFFF91880) else OnDark, modifier = Modifier.size(22.dp))
        if (count != null && count > 0) {
            Spacer(Modifier.width(6.dp))
            Text(compactCount(count), color = OnDark, fontSize = 15.sp)
        }
    }
}
