package com.example.btcwidget.nostr.core

/** A long-form article (NIP-23, kind 30023). Its body is Markdown. */
data class Article(
    val event: Event,
    val identifier: String,
    val title: String,
    val summary: String?,
    val image: String?,
    val publishedAt: Long
) {
    val pubkey get() = event.pubkey
    val content get() = event.content

    /** Link to this article (njump.me opens it in any browser or Nostr app). */
    fun naddr(relays: List<String> = emptyList()) = Nip19.naddr(Kind.ARTICLE, pubkey, identifier, relays)

    companion object {
        fun of(event: Event): Article? {
            if (event.kind != Kind.ARTICLE) return null
            val d = event.firstTag("d")?.get(1) ?: ""
            fun tag(name: String) = event.firstTag(name)?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
            return Article(
                event = event,
                identifier = d,
                title = tag("title") ?: firstHeading(event.content) ?: "Untitled",
                summary = tag("summary"),
                image = tag("image")?.takeIf { it.startsWith("https://") },
                publishedAt = tag("published_at")?.toLongOrNull() ?: event.createdAt
            )
        }

        private fun firstHeading(md: String) =
            md.lineSequence().firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim()?.takeIf { it.isNotEmpty() }
    }
}

/**
 * A small Markdown reader for articles: headings, paragraphs, lists, quotes, code blocks,
 * images and rules, with bold, italic, inline code and links inside text. No raw HTML.
 */
object Markdown {
    sealed interface Block {
        data class Heading(val level: Int, val spans: List<Span>) : Block
        data class Paragraph(val spans: List<Span>) : Block
        data class ListItem(val marker: String, val spans: List<Span>) : Block
        data class Quote(val spans: List<Span>) : Block
        data class Code(val text: String) : Block
        data class Image(val url: String, val alt: String) : Block
        data object Rule : Block
    }

    sealed interface Span {
        data class Plain(val text: String) : Span
        data class Bold(val text: String) : Span
        data class Italic(val text: String) : Span
        data class Code(val text: String) : Span
        data class Link(val text: String, val url: String) : Span
    }

    private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
    private val BULLET = Regex("""^\s*[-*+]\s+(.*)$""")
    private val NUMBERED = Regex("""^\s*(\d+)[.)]\s+(.*)$""")
    private val IMAGE = Regex("""^!\[([^\]]*)]\((\S+?)(?:\s+"[^"]*")?\)$""")
    private val RULE = Regex("""^(\*\s*){3,}$|^(-\s*){3,}$|^(_\s*){3,}$""")

    fun parse(md: String): List<Block> {
        val out = ArrayList<Block>()
        val para = StringBuilder()
        fun flush() {
            if (para.isNotBlank()) out += Block.Paragraph(inline(para.toString().trim()))
            para.clear()
        }
        val lines = md.replace("\r\n", "\n").lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            when {
                trimmed.startsWith("```") -> {
                    flush()
                    val code = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        if (code.isNotEmpty()) code.append('\n')
                        code.append(lines[i]); i++
                    }
                    out += Block.Code(code.toString())
                }
                trimmed.isEmpty() -> flush()
                RULE.matches(trimmed) -> { flush(); out += Block.Rule }
                HEADING.matches(trimmed) -> {
                    flush()
                    val m = HEADING.find(trimmed)!!
                    out += Block.Heading(m.groupValues[1].length, inline(m.groupValues[2].trimEnd('#', ' ')))
                }
                IMAGE.matches(trimmed) -> {
                    flush()
                    val m = IMAGE.find(trimmed)!!
                    out += Block.Image(m.groupValues[2], m.groupValues[1])
                }
                BULLET.matches(line) -> { flush(); out += Block.ListItem("•", inline(BULLET.find(line)!!.groupValues[1])) }
                NUMBERED.matches(line) -> {
                    flush()
                    val m = NUMBERED.find(line)!!
                    out += Block.ListItem(m.groupValues[1] + ".", inline(m.groupValues[2]))
                }
                trimmed.startsWith(">") -> {
                    flush()
                    val quote = StringBuilder(trimmed.removePrefix(">").trim())
                    while (i + 1 < lines.size && lines[i + 1].trim().startsWith(">")) {
                        i++
                        quote.append(' ').append(lines[i].trim().removePrefix(">").trim())
                    }
                    out += Block.Quote(inline(quote.toString().trim()))
                }
                else -> {
                    if (para.isNotEmpty()) para.append(' ')
                    para.append(trimmed)
                }
            }
            i++
        }
        flush()
        return out
    }

    private val INLINE = Regex("""`([^`]+)`|!?\[([^\]]+)]\(([^)\s]+)(?:\s+"[^"]*")?\)|\*\*(.+?)\*\*|__(.+?)__|(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?!\w)|(?<!\w)_(?!\s)(.+?)(?<!\s)_(?!\w)""")

    fun inline(text: String): List<Span> {
        val out = ArrayList<Span>()
        var last = 0
        for (m in INLINE.findAll(text)) {
            if (m.range.first > last) out += Span.Plain(text.substring(last, m.range.first))
            val g = m.groups
            out += when {
                g[1] != null -> Span.Code(g[1]!!.value)
                g[2] != null -> Span.Link(g[2]!!.value, g[3]!!.value)
                g[4] != null -> Span.Bold(g[4]!!.value)
                g[5] != null -> Span.Bold(g[5]!!.value)
                g[6] != null -> Span.Italic(g[6]!!.value)
                else -> Span.Italic(g[7]!!.value)
            }
            last = m.range.last + 1
        }
        if (last < text.length) out += Span.Plain(text.substring(last))
        return out
    }
}

/**
 * Highlights (NIP-84, kind 9802): a passage someone marked in an article (or a note, or a web
 * page), the text as the content, pointing back at the source and its author, optionally with
 * the reader's own comment.
 */
object Highlights {
    const val KIND = 9802

    data class Highlight(val event: Event, val text: String, val comment: String?, val articleAddress: String?, val noteId: String?, val url: String?)

    /** A highlight of [text] from [article], with an optional [comment] (a "quote highlight"). */
    fun of(me: String, text: String, article: Article, comment: String? = null, relay: String = "", now: Long = Events.now()) = UnsignedEvent(
        me, now, KIND,
        listOfNotNull(
            listOf("a", "${Kind.ARTICLE}:${article.pubkey}:${article.identifier}", relay),
            listOf("p", article.pubkey, relay, "author"),
            comment?.trim()?.takeIf { it.isNotEmpty() }?.let { listOf("comment", it) }
        ),
        text.trim()
    )

    fun parse(e: Event): Highlight? {
        if (e.kind != KIND || e.content.isBlank()) return null
        return Highlight(
            e, e.content.trim(),
            comment = e.firstTag("comment")?.getOrNull(1)?.trim()?.ifBlank { null },
            articleAddress = e.tags.firstOrNull { it.size >= 2 && it[0] == "a" }?.get(1),
            noteId = e.tags.firstOrNull { it.size >= 2 && it[0] == "e" }?.get(1),
            url = e.tags.firstOrNull { it.size >= 2 && it[0] == "r" && (it[1].startsWith("https://") || it[1].startsWith("http://")) }?.get(1)
        )
    }

    /** Plain text of a Markdown block's spans, to highlight and to match highlights against. */
    fun textOf(spans: List<Markdown.Span>): String = spans.joinToString("") { s ->
        when (s) {
            is Markdown.Span.Plain -> s.text
            is Markdown.Span.Bold -> s.text
            is Markdown.Span.Italic -> s.text
            is Markdown.Span.Code -> s.text
            is Markdown.Span.Link -> s.text
        }
    }.trim()
}
