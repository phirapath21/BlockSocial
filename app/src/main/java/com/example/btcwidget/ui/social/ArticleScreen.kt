package com.example.btcwidget.ui.social

import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import coil3.compose.AsyncImage
import com.example.btcwidget.NoteKey
import com.example.btcwidget.ProfileKey
import com.example.btcwidget.nostr.NoteStats
import com.example.btcwidget.nostr.core.Article
import com.example.btcwidget.nostr.core.Markdown
import com.example.btcwidget.nostr.core.Nip19
import com.example.btcwidget.ui.main.EInk

/** A long-form article (NIP-23), rendered from its Markdown. */
@Composable
fun ArticleScreen(vm: SocialViewModel, naddr: String, navigate: (NavKey) -> Unit, onBack: () -> Unit) {
    val address = remember(naddr) { Nip19.decode(naddr) as? Nip19.Address }
    val stats by vm.stats.collectAsState()
    // Unit until loaded, then the article or null if no relay had it.
    val loaded by produceKept<Any?>(Unit, address) { value = address?.let { vm.article(it) } }
    Column(Modifier.fillMaxSize().background(EInk.Background)) {
        SocialTopBar(tr("Article"), onBack)
        SocialScaffold(vm, navigate) { actions ->
            when (val a = loaded) {
                Unit -> Spinner(Modifier.padding(top = 40.dp))
                is Article -> ArticleBody(vm, a, stats[a.event.id] ?: NoteStats(), actions, navigate)
                else -> EmptyNote(tr("This article couldn't be found on your relays."))
            }
        }
    }
}

@Composable
private fun ArticleBody(vm: SocialViewModel, article: Article, stats: NoteStats, actions: NoteActions, navigate: (NavKey) -> Unit) {
    val blocks = remember(article.event.id) { Markdown.parse(article.content) }
    val author = actions.profileOf(article.pubkey)
    val context = LocalContext.current
    val account by vm.account.collectAsState()
    val profiles by vm.profiles.collectAsState()
    // Highlights (NIP-84) by anyone; reloaded after the user adds one.
    var reload by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    val highlights by produceKept(emptyList<com.example.btcwidget.nostr.core.Highlights.Highlight>(), article.event.id, reload) { value = vm.highlightsOf(article) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var commentFor by remember { mutableStateOf<String?>(null) }
    fun highlightNow(text: String, comment: String?) {
        if (account == null) { navigate(com.example.btcwidget.LoginKey); return }
        vm.highlight(article, text, comment) { reload++ }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        article.image?.let { url ->
            item(key = "cover") {
                AsyncImage(
                    model = url, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)
                )
            }
        }
        item(key = "head") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(article.title, color = EInk.Value, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 10.dp).clickable { actions.openProfile(article.pubkey) }
                ) {
                    Avatar(author, article.pubkey, size = 32)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(author?.bestName ?: com.example.btcwidget.nostr.core.Profile.shortNpub(article.pubkey), color = EInk.Value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(fullTimestamp(article.publishedAt), color = EInk.Caption, fontSize = 12.sp)
                    }
                }
                article.summary?.let {
                    Text(it, color = EInk.Label, fontSize = 15.sp, fontStyle = FontStyle.Italic, modifier = Modifier.padding(top = 10.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(top = 12.dp)) {
                    ArticleAction((if (stats.likedByMe) "♥ " else "♡ ") + stats.likes) { actions.like(article.event) }
                    ArticleAction("⚡ " + stats.zapMsat / 1000) { actions.zap(article.event) }
                    ArticleAction("Share") { shareArticle(context, article) }
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
        }
        items(blocks.size, key = { "b$it" }) { i ->
            val block = blocks[i]
            val text = remember(block) { blockText(block) }
            val marked = text != null && highlights.any { h -> text.contains(h.text) || (h.text.length > 20 && h.text.contains(text)) }
            Box(
                Modifier
                    .then(if (marked) Modifier.padding(horizontal = 10.dp).background(HighlightTint, RoundedCornerShape(8.dp)) else Modifier)
                    .then(if (text != null) Modifier.onLongPress { menuFor = text } else Modifier)
            ) { MarkdownBlock(block, actions, navigate) }
        }
        if (highlights.isNotEmpty()) item(key = "highlights") {
            HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke, modifier = Modifier.padding(top = 16.dp))
            Text(tr("Highlights"), color = EInk.Value, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            highlights.take(30).forEach { h ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Avatar(profiles[h.event.pubkey], h.event.pubkey, size = 28, onClick = { actions.openProfile(h.event.pubkey) })
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(profiles[h.event.pubkey]?.bestName ?: com.example.btcwidget.nostr.core.Profile.shortNpub(h.event.pubkey), color = EInk.Value, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.padding(top = 4.dp)) {
                            Spacer(Modifier.width(3.dp).heightIn(min = 18.dp).background(HighlightOrange))
                            Text(h.text, color = EInk.Label, fontSize = 14.sp, fontStyle = FontStyle.Italic, maxLines = 4, modifier = Modifier.padding(start = 8.dp))
                        }
                        h.comment?.let { Text(it, color = EInk.Value, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)) }
                    }
                }
            }
        }
        item(key = "end") {
            Text(
                tr("Long-press a paragraph to highlight it."), color = EInk.Caption, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
            )
            Spacer(Modifier.padding(bottom = 40.dp))
        }
    }
    menuFor?.let { text ->
        ActionSheet(onDismiss = { menuFor = null }, actions = listOf(
            SheetAction(Glyphs.Quote, tr("Highlight")) { highlightNow(text, null) },
            SheetAction(Glyphs.Comment, tr("Highlight with a comment")) { commentFor = text },
            SheetAction(Glyphs.Copy, tr("Copy text")) {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                    .setPrimaryClip(android.content.ClipData.newPlainText("text", text))
            }
        )) {
            Text(
                "“" + text.take(200) + (if (text.length > 200) "…" else "") + "”", color = EInk.Label, fontSize = 14.sp, fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)
            )
        }
    }
    commentFor?.let { text ->
        var comment by remember { mutableStateOf("") }
        BlockDialog(
            onDismiss = { commentFor = null }, title = tr("Highlight with a comment"),
            text = "“" + text.take(160) + (if (text.length > 160) "…" else "") + "”",
            confirmLabel = tr("Highlight"), confirmEnabled = comment.isNotBlank(),
            onConfirm = { commentFor = null; highlightNow(text, comment) }
        ) {
            androidx.compose.material3.OutlinedTextField(value = comment, onValueChange = { comment = it.take(500) }, placeholder = { Text(tr("Your comment")) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

private val HighlightOrange = androidx.compose.ui.graphics.Color(0xFFF7931A)
private val HighlightTint = androidx.compose.ui.graphics.Color(0x26F7931A)

/** A block's plain text, for highlighting (null for images, code and rules). */
private fun blockText(block: Markdown.Block): String? = when (block) {
    is Markdown.Block.Paragraph -> com.example.btcwidget.nostr.core.Highlights.textOf(block.spans)
    is Markdown.Block.ListItem -> com.example.btcwidget.nostr.core.Highlights.textOf(block.spans)
    is Markdown.Block.Quote -> com.example.btcwidget.nostr.core.Highlights.textOf(block.spans)
    is Markdown.Block.Heading -> com.example.btcwidget.nostr.core.Highlights.textOf(block.spans)
    else -> null
}?.takeIf { it.isNotBlank() }

/**
 * Calls [onLongPress] when held, without taking normal taps (so links in the text still work):
 * it never consumes a tap, only the rest of a gesture that became a long press.
 */
private fun Modifier.onLongPress(onLongPress: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }
        if (up == null) {
            onLongPress()
            // Swallow the rest of this press so letting go doesn't also tap a link.
            do {
                val event = awaitPointerEvent()
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}

@Composable
private fun ArticleAction(label: String, onClick: () -> Unit) {
    Text(label, color = EInk.Value, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp))
}

@Composable
private fun MarkdownBlock(block: Markdown.Block, actions: NoteActions, navigate: (NavKey) -> Unit) {
    val pad = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    when (block) {
        is Markdown.Block.Heading -> Text(
            spans(block.spans, actions, navigate), color = EInk.Value, fontWeight = FontWeight.Bold,
            fontSize = when (block.level) { 1 -> 22.sp; 2 -> 19.sp; else -> 17.sp },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)
        )
        is Markdown.Block.Paragraph -> Text(spans(block.spans, actions, navigate), color = EInk.Value, fontSize = 16.sp, lineHeight = 24.sp, modifier = pad)
        is Markdown.Block.ListItem -> Row(pad) {
            Text(block.marker, color = EInk.Value, fontSize = 16.sp, modifier = Modifier.width(24.dp))
            Text(spans(block.spans, actions, navigate), color = EInk.Value, fontSize = 16.sp, lineHeight = 24.sp)
        }
        is Markdown.Block.Quote -> Row(pad) {
            Spacer(Modifier.width(3.dp).heightIn(min = 20.dp).background(EInk.BodyStroke))
            Text(spans(block.spans, actions, navigate), color = EInk.Label, fontSize = 16.sp, fontStyle = FontStyle.Italic, lineHeight = 24.sp, modifier = Modifier.padding(start = 10.dp))
        }
        is Markdown.Block.Code -> Text(
            block.text, color = EInk.Value, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
            modifier = pad.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(EInk.Body).border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(8.dp)).padding(10.dp)
        )
        is Markdown.Block.Image -> if (block.url.startsWith("https://")) {
            AsyncImage(
                model = block.url, contentDescription = block.alt.ifBlank { null }, contentScale = ContentScale.FillWidth,
                modifier = pad.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            )
        }
        Markdown.Block.Rule -> HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
    }
}

/** Inline Markdown as styled text; nostr: links open in the app, web links in the browser. */
@Composable
private fun spans(spans: List<Markdown.Span>, actions: NoteActions, navigate: (NavKey) -> Unit): AnnotatedString {
    val uriHandler = LocalUriHandler.current
    val linkStyle = TextLinkStyles(SpanStyle(color = EInk.Value, fontWeight = FontWeight.Bold))
    return buildAnnotatedString {
        spans.forEach { s ->
            when (s) {
                is Markdown.Span.Plain -> append(s.text)
                is Markdown.Span.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s.text) }
                is Markdown.Span.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(s.text) }
                is Markdown.Span.Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = EInk.Body)) { append(s.text) }
                is Markdown.Span.Link -> withLink(LinkAnnotation.Clickable("link", linkStyle) { openLink(s.url, actions, navigate) { actions.openLink(it, null) } }) {
                    append(s.text)
                }
            }
        }
    }
}

private fun openLink(url: String, actions: NoteActions, navigate: (NavKey) -> Unit, openWeb: (String) -> Unit) {
    when (val e = if (url.startsWith("nostr:")) Nip19.decode(url) else null) {
        is Nip19.Pubkey -> navigate(ProfileKey(e.hex))
        is Nip19.Note -> navigate(NoteKey(e.id))
        is Nip19.Address -> actions.openArticle(e)
        else -> if (url.startsWith("https://") || url.startsWith("http://")) runCatching { openWeb(url) }
    }
}

/** Shares a link to the article (njump.me opens it in any browser or Nostr app). */
private fun shareArticle(context: Context, article: Article) {
    val link = "https://njump.me/" + article.naddr()
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, article.title + "\n" + link)
    context.startActivity(Intent.createChooser(send, "Share article").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** An article in a list: cover, title, summary and date. */
@Composable
internal fun ArticleRow(article: Article, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(article.title, color = EInk.Value, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            article.summary?.let { Text(it, color = EInk.Label, fontSize = 14.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
            Text(relativeTime(article.publishedAt), color = EInk.Caption, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        article.image?.let { url ->
            Spacer(Modifier.width(10.dp))
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.width(72.dp).heightIn(min = 72.dp, max = 72.dp).clip(RoundedCornerShape(8.dp)))
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
}
