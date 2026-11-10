package com.example.btcwidget.ui.social

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
import androidx.compose.runtime.produceState
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
    val loaded by produceState<Any?>(Unit, address) { value = address?.let { vm.article(it) } }
    Column(Modifier.fillMaxSize().background(EInk.Background)) {
        SocialTopBar("Article", onBack)
        SocialScaffold(vm, navigate) { actions ->
            when (val a = loaded) {
                Unit -> Spinner(Modifier.padding(top = 40.dp))
                is Article -> ArticleBody(a, stats[a.event.id] ?: NoteStats(), actions, navigate)
                else -> EmptyNote("This article couldn't be found on your relays.")
            }
        }
    }
}

@Composable
private fun ArticleBody(article: Article, stats: NoteStats, actions: NoteActions, navigate: (NavKey) -> Unit) {
    val blocks = remember(article.event.id) { Markdown.parse(article.content) }
    val author = actions.profileOf(article.pubkey)
    val context = LocalContext.current
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
            MarkdownBlock(blocks[i], actions, navigate)
        }
        item(key = "end") { Spacer(Modifier.padding(bottom = 40.dp)) }
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
                is Markdown.Span.Link -> withLink(LinkAnnotation.Clickable("link", linkStyle) { openLink(s.url, actions, navigate) { uriHandler.openUri(it) } }) {
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
