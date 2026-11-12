package com.example.btcwidget.ui.social

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.draggable
import com.example.btcwidget.ui.main.EInk
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
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
private val Accent = Color(0xFFF7931A)
/** The panel under the picture: the app's own background, nearly opaque, rounded at the top. */
private val PanelShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

/**
 * Full-screen image or ([video]) video in Block's style: back and ⋮ (Save image, Open in browser)
 * on top; underneath, on a rounded panel in the app's colours with a handle to drag it taller, the
 * author with Follow, the note's text (Show more, or the handle, opens the rest to scroll), Reply ·
 * Repost · Like · Zap · Bookmark · Share, the reply box, and last the video controls. Pinch or double-tap to zoom, drag to pan.
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
        Toast.makeText(context, if (result.isSuccess) tr("Saved to Pictures/Block") else tr("Couldn't save the image"), Toast.LENGTH_SHORT).show()
    }
    // Only Android 9 and below need a permission to save.
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) save() else Toast.makeText(context, tr("Storage permission is needed to save"), Toast.LENGTH_SHORT).show()
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
                "${pager.currentPage + 1} / ${pictures.size}", color = EInk.Value, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 18.dp)
                    .clip(RoundedCornerShape(50)).background(EInk.Background.copy(alpha = 0.8f))
                    .border(1.dp, Accent.copy(alpha = 0.7f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp)
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
                        if (!video) add(SheetAction(Glyphs.Download, tr("Save image")) {
                            if (ImageSaver.needsPermission) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else save()
                        })
                        add(SheetAction(Glyphs.OpenInBrowser, tr("Open in browser")) { uriHandler.openUri(current) })
                        add(SheetAction(Glyphs.Link, tr("Copy link")) {
                            (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                                .setPrimaryClip(android.content.ClipData.newPlainText("link", current))
                        })
                    })
                }
            }

            // Bottom: author, text, actions (and the video controls).
            if (chrome && landscape && player != null) VideoControls(
                player, url, landscape = true, onRotate = rotate,
                Modifier.align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Scrim))).safeDrawingPadding().padding(horizontal = 16.dp, vertical = 4.dp)
            )
            if (chrome && !landscape) Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(PanelShape)
                    .background(EInk.Background.copy(alpha = 0.94f))
                    .border(0.5.dp, EInk.BodyStroke, PanelShape)
                    .safeDrawingPadding()
                    .imePadding()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp)
            ) {
                // Drag the handle up to read more of the post (pause first if you like), down to
                // give the picture back its room. Tapping it does the same in one go.
                val density = androidx.compose.ui.platform.LocalDensity.current
                val screenPx = with(density) { androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp.toPx() }
                val minPx = with(density) { 88.dp.toPx() }
                val maxPx = (screenPx * 0.6f).coerceAtLeast(minPx)
                var expanded by remember(note.id) { mutableStateOf(false) }
                var textPx by remember(note.id) { mutableStateOf(screenPx * 0.32f) }
                val drag = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
                    if (delta < 0 && !expanded) { expanded = true; textPx = minPx }
                    textPx = (textPx - delta).coerceIn(minPx, maxPx)
                    if (delta > 0 && textPx <= minPx) expanded = false
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .draggable(drag, androidx.compose.foundation.gestures.Orientation.Vertical)
                        .clickable(onClickLabel = if (expanded) tr("Show less") else tr("Show more")) {
                            expanded = !expanded; if (expanded) textPx = screenPx * 0.32f
                        }
                        .padding(bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) { Box(Modifier.size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(EInk.Track)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(author, note.pubkey, size = 44, onClick = { onDismiss(); actions.openProfile(note.pubkey) })
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        DisplayName(author, note.pubkey, EInk.Value, 16.sp)
                        val handle = handleText(author, note.pubkey)
                        Text(handle, color = EInk.Caption, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (!isMe) {
                        // Follow in the accent colour; Following as an outline.
                        Text(
                            if (following) tr("Following") else tr("Follow"),
                            color = if (following) EInk.Value else Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .then(if (following) Modifier.border(1.dp, EInk.BodyStroke, RoundedCornerShape(50)) else Modifier.background(Accent))
                                .clickable(onClickLabel = if (following) tr("Unfollow") else tr("Follow")) { actions.follow(note.pubkey, !following) }
                                .padding(horizontal = 18.dp, vertical = 8.dp)
                        )
                    }
                }
                if (caption.isNotEmpty()) {
                    var cut by remember(note.id) { mutableStateOf(false) }
                    if (expanded) {
                        // The whole post, scrolling inside the room the handle gives it.
                        Box(
                            Modifier.padding(top = 10.dp).fillMaxWidth()
                                .heightIn(max = with(density) { textPx.toDp() })
                                .verticalScroll(rememberScrollState())
                        ) { Text(caption, color = EInk.Value, fontSize = 15.sp, lineHeight = 21.sp) }
                        Text(
                            tr("Show less"), color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { expanded = false }.padding(vertical = 4.dp)
                        )
                    } else {
                        Text(
                            caption, color = EInk.Value, fontSize = 15.sp, lineHeight = 20.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            onTextLayout = { cut = it.hasVisualOverflow },
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .clickable(onClickLabel = tr("Open post")) { onDismiss(); actions.openNote(note) }
                        )
                        if (cut) Text(
                            tr("Show more"), color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                .clickable(onClickLabel = tr("Read the whole post")) { expanded = true; textPx = screenPx * 0.32f }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
                // While replying the buttons make way for the reply box.
                if (!replying) Spacer(Modifier.height(10.dp))
                if (!replying) Row(verticalAlignment = Alignment.CenterVertically) {
                    ViewerAction(Glyphs.Comment, stats.replies, "Reply", Modifier.weight(1f)) { onDismiss(); actions.reply(note) }
                    Box(Modifier.weight(1f)) {
                        ViewerAction(Glyphs.Repost, stats.reposts, if (stats.repostedByMe) "Reposted" else "Repost or quote", Modifier.fillMaxWidth(), active = stats.repostedByMe) { repostMenu = true }
                        if (repostMenu) RepostSheet(note, stats, actions, onDismiss = { repostMenu = false }, beforeQuote = onDismiss)
                    }
                    ViewerAction(
                        if (stats.likedByMe) Glyphs.Liked else Glyphs.Like, stats.likes,
                        if (stats.likedByMe) "Liked" else "Like", Modifier.weight(1f), active = stats.likedByMe
                    ) { actions.like(note) }
                    ViewerAction(Glyphs.Zap, (stats.zapMsat / 1000).toInt(), "Zap", Modifier.weight(1f), tint = Accent) { actions.zap(note) }
                    val marked = actions.isBookmarked(note.id)
                    ViewerAction(if (marked) Glyphs.Bookmarked else Glyphs.Bookmark, null, if (marked) "Remove bookmark" else "Bookmark", Modifier.weight(0.7f), active = marked) { actions.bookmark(note) }
                    ViewerAction(Glyphs.Share, null, "Share", Modifier.weight(0.7f)) { actions.share(note) }
                }
                if (replyBar != null) {
                    Spacer(Modifier.height(10.dp))
                    replyBar { replying = it }
                }
                // The video's controls at the very bottom, under everything else, within thumb's reach.
                if (player != null && !replying) {
                    androidx.compose.material3.HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                    VideoControls(player, url, landscape = rotated, onRotate = rotate)
                }
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
            .background(EInk.Background.copy(alpha = 0.75f))
            .border(0.5.dp, EInk.BodyStroke, CircleShape)
            .clickable(onClickLabel = tr(label), role = Role.Button, onClick = onClick)
    ) { Icon(icon, contentDescription = tr(label), tint = EInk.Value, modifier = Modifier.size(24.dp)) }
}

/** An action under the media: its icon and count, orange when it's yours (liked, reposted…). */
@Composable
private fun ViewerAction(icon: ImageVector, count: Int?, label: String, modifier: Modifier = Modifier, active: Boolean = false, tint: Color? = null, onClick: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClickLabel = tr(label), role = Role.Button, onClick = onClick)
    ) {
        Icon(icon, contentDescription = tr(label), tint = tint ?: if (active) Accent else EInk.Value, modifier = Modifier.size(22.dp))
        if (count != null && count > 0) {
            Spacer(Modifier.width(5.dp))
            Text(compactCount(count), color = if (active) Accent else EInk.Caption, fontSize = 14.sp)
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
        contentDescription = tr("Image"),
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
