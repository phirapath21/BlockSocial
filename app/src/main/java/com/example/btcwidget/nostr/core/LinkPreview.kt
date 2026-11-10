package com.example.btcwidget.nostr.core

import java.net.URI

/** A link's card: OpenGraph (or Twitter card) tags, falling back to the page title. */
data class LinkPreview(
    val url: String,
    val title: String,
    val description: String?,
    val image: String?,
    val site: String
) {
    companion object {
        private val META = Regex("""<meta\s[^>]*>""", RegexOption.IGNORE_CASE)
        private val ATTR = Regex("""([a-zA-Z:-]+)\s*=\s*("([^"]*)"|'([^']*)')""")
        private val TITLE = Regex("""<title[^>]*>([^<]{1,300})</title>""", RegexOption.IGNORE_CASE)

        /** Reads a page's head; null when it has no usable title. */
        fun parse(html: String, url: String): LinkPreview? {
            val head = html.substringBefore("</head>", html).take(200_000)
            val meta = HashMap<String, String>()
            for (m in META.findAll(head)) {
                val attrs = ATTR.findAll(m.value).associate { a ->
                    a.groupValues[1].lowercase() to (a.groupValues[3].ifEmpty { a.groupValues[4] })
                }
                val key = (attrs["property"] ?: attrs["name"])?.lowercase() ?: continue
                val content = attrs["content"]?.let(::decode)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                meta.putIfAbsent(key, content)
            }
            val title = meta["og:title"] ?: meta["twitter:title"]
                ?: TITLE.find(head)?.groupValues?.get(1)?.let(::decode)?.trim()
                ?: return null
            if (title.isBlank()) return null
            val image = (meta["og:image"] ?: meta["og:image:url"] ?: meta["twitter:image"])?.let { resolve(url, it) }
                ?.takeIf { it.startsWith("https://") }
            val host = runCatching { URI(url).host?.removePrefix("www.") }.getOrNull().orEmpty()
            return LinkPreview(
                url = url,
                title = title.take(200),
                description = (meta["og:description"] ?: meta["twitter:description"] ?: meta["description"])?.take(300),
                image = image,
                site = meta["og:site_name"] ?: host
            )
        }

        private fun resolve(base: String, link: String): String? =
            runCatching { URI(base).resolve(link.trim()).toString() }.getOrNull()

        private fun decode(s: String): String = s
            .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
            .replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
            .replace(Regex("&#(\\d{1,6});")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value }
    }
}
