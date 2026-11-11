package com.example.btcwidget.ui.social

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.transformable
import androidx.compose.runtime.DisposableEffect
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
 * Full-screen image or ([video]) video, laid out like X: back and ⋮ (Save image, Open in browser) on top;
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
    /** Every picture in the note, to swipe between (starting at [url]). */
    urls: List<String> = listOf(url),
    /** Show [url] as a video with X-style controls instead of an image. Tap shows or hides everything around it. */
    video: Boolean = false,
    /** The reply card under the actions, told when it opens; null hides it. */
    replyBar: (@Composable (onActiveChange: (Boolean) -> Unit) -> Unit)? = null,
    /** Shown over the top while a reply or repost made here is being sent (the viewer is its own window). */
    statusBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var replying by remember { mutableStateOf(false) }
    val pictures = remember(urls, url) { urls.ifEmpty { listOf(url) } }
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = pictures.indexOf(url).coerceAtLeast(0)) { pictures.size }
    // The picture on screen (Save and Open in browser act on it).
    val current = if (video) url else pictures.getOrElse(pager.currentPage) { url }
    var zoomed by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(pager.currentPage) { zoomed = false }
    var menu by remember { mutableStateOf(false) }
    var repostMenu by remember { mutableStateOf(false) }

    fun save() = scope.launch {
        val result = ImageSaver.save(context, current)
        Toast.makeText(context, if (result.isSuccess) "Saved to Pictures/Block" else "Couldn't save the image", Toast.LENGTH_SHORT).show()
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
        val videos = Imeta.videoUrls(note)
        Content.parse(note.content).filterNot {
            it is Content.Segment.Image || it is Content.Segment.Video || (it is Content.Segment.Link && (it.url in images || it.url in videos))
        }
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
        // The dialog has its own window: let it report the keyboard too, so the reply card rides above it.
        val dialogWindow = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        androidx.compose.runtime.SideEffect {
            @Suppress("DEPRECATION")
            dialogWindow?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        // Video: its own player, carrying on from the timeline, with sound on.
        var chrome by remember { mutableStateOf(true) }
        val (rotated, rotate) = rememberVideoOrientation()
        // Landscape video: only the picture and its controls, nothing else over it.
        val landscape = video && androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        // Landscape video fills the screen: the system bars hide (a swipe brings them back).
        if (dialogWindow != null) DisposableEffect(landscape) {
            val bars = androidx.core.view.WindowCompat.getInsetsController(dialogWindow, dialogWindow.decorView)
            bars.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            val types = androidx.core.view.WindowInsetsCompat.Type.systemBars()
            if (landscape) bars.hide(types) else bars.show(types)
            onDispose { bars.show(types) }
        }
        val player = if (video) {
            // Owned by PipVideo so it can keep playing in Picture-in-Picture after this hides.
            val p = remember(url) { PipVideo.acquire(context, url) { ctx, u -> newPlayer(ctx, u, fullScreen = true) {}.apply { volume = 1f; playWhenReady = true } } }
            DisposableEffect(p) { onDispose { if (!PipVideo.inPip) PipVideo.release { u, pos -> VideoPositions[u] = pos } } }
            p
        } else null
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (player != null) VideoSurface(
                player,
                Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { chrome = !chrome }) }
            ) else androidx.compose.foundation.pager.HorizontalPager(
                state = pager,
                // Swipe between pictures; a zoomed picture pans instead.
                userScrollEnabled = !zoomed,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                ZoomableImage(pictures[page], com.example.btcwidget.nostr.core.Imeta.blurhash(note, pictures[page]), onZoom = { if (page == pager.currentPage) zoomed = it })
            }
            // "2 / 4" when the note has several pictures.
            if (!video && pictures.size > 1 && chrome) Text(
                "${pager.currentPage + 1} / ${pictures.size}", color = OnDark, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 18.dp)
                    .clip(RoundedCornerShape(50)).background(Scrim).padding(horizontal = 10.dp, vertical = 4.dp)
            )

            // Top: back and more.
            if (chrome && !landscape) Row(
                Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundIcon(Glyphs.Back, "Back", onDismiss)
                Spacer(Modifier.weight(1f))
                Box {
                    RoundIcon(Glyphs.More, "More options") { menu = true }
                    if (menu) ActionSheet(onDismiss = { menu = false }, actions = buildList {
                        if (!video) add(SheetAction(Glyphs.Download, "Save image") {
                            if (ImageSaver.needsPermission) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else save()
                        })
                        add(SheetAction(Glyphs.OpenInBrowser, "Open in browser") { uriHandler.openUri(current) })
                        add(SheetAction(Glyphs.Link, "Copy link") {
                            (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                                .setPrimaryClip(android.content.ClipData.newPlainText("link", current))
                        })
                    })
                }
            }

            // Bottom: author, text, actions (and the video controls).
            if (chrome && landscape && player != null) VideoControls(
                player, url, landscape = true, onRotate = rotate,
                Modifier.align(Alignment.BottomCenter).background(Scrim).safeDrawingPadding().padding(horizontal = 16.dp, vertical = 4.dp)
            )
            if (chrome && !landscape) Column(
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
                        DisplayName(author, note.pubkey, OnDark, 16.sp)
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
                        if (repostMenu) RepostSheet(note, stats, actions, onDismiss = { repostMenu = false }, beforeQuote = onDismiss)
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
                if (player != null && !replying) VideoControls(player, url, landscape = rotated, onRotate = rotate, modifier = Modifier.padding(top = 8.dp))
            }
        }
        statusBar()
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
        Icon(icon, contentDescription = label, tint = if (active && (label == "Liked" || label == "Repost or quote")) Color(0xFFF7931A) else OnDark, modifier = Modifier.size(22.dp))
        if (count != null && count > 0) {
            Spacer(Modifier.width(6.dp))
            Text(compactCount(count), color = OnDark, fontSize = 15.sp)
        }
    }
}

/** One picture: pinch or double-tap to zoom, drag to pan once zoomed. [onZoom] says when it's zoomed. */
@Composable
private fun ZoomableImage(url: String, blurhash: String?, onZoom: (Boolean) -> Unit) {
    var scale by remember(url) { mutableFloatStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }
    fun set(s: Float, o: Offset) {
        scale = s; offset = if (s == 1f) Offset.Zero else o
        onZoom(s > 1f)
    }
    val transform = androidx.compose.foundation.gestures.rememberTransformableState { zoom, pan, _ ->
        set((scale * zoom).coerceIn(1f, 5f), offset + pan)
    }
    val blur = rememberBlurhashPainter(blurhash)
    AsyncImage(
        model = url,
        placeholder = blur,
        contentDescription = "Image",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            // Panning only when zoomed, so a plain swipe moves to the next picture.
            .transformable(transform, canPan = { scale > 1f })
            .pointerInput(url) {
                detectTapGestures(onDoubleTap = { if (scale > 1f) set(1f, Offset.Zero) else set(2.5f, offset) })
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
    )
}
