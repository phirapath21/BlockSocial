package com.example.btcwidget.nostr.core

import org.json.JSONObject

/** Profile metadata (kind 0, NIP-01 / NIP-24). */
data class Profile(
    val pubkey: String,
    val name: String? = null,
    val displayName: String? = null,
    val about: String? = null,
    val picture: String? = null,
    val banner: String? = null,
    val nip05: String? = null,
    val lud16: String? = null,
    val lud06: String? = null,
    val website: String? = null,
    val createdAt: Long = 0
) {
    /** Best name to show: display name, then name, then a shortened npub. */
    val bestName: String
        get() = displayName?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() } ?: shortNpub(pubkey)

    companion object {
        fun fromEvent(event: Event): Profile? = runCatching {
            val o = JSONObject(event.content)
            fun s(key: String) = o.optString(key, "").trim().takeIf { it.isNotEmpty() }
            Profile(
                pubkey = event.pubkey,
                name = s("name"),
                displayName = s("display_name") ?: s("displayName"),
                about = s("about"),
                picture = s("picture"),
                banner = s("banner"),
                nip05 = s("nip05"),
                lud16 = s("lud16"),
                lud06 = s("lud06"),
                website = s("website"),
                createdAt = event.createdAt
            )
        }.getOrNull()

        fun shortNpub(pubkey: String): String =
            runCatching { Nip19.npub(pubkey).let { it.take(10) + "…" + it.takeLast(4) } }.getOrDefault(pubkey.take(8))
    }
}

/** NIP-10 thread position of a kind-1 note. */
data class ThreadRef(val root: String?, val reply: String?, val rootRelay: String? = null) {
    val isReply get() = reply != null || root != null

    /** The note this one answers directly. */
    val parent get() = reply ?: root

    companion object {
        /** Reads marked "e" tags, falling back to the deprecated positional scheme. */
        fun of(event: Event): ThreadRef {
            val es = event.tags.filter { it.size >= 2 && it[0] == "e" }
            val marked = es.filter { it.size >= 4 && (it[3] == "root" || it[3] == "reply") }
            if (marked.isNotEmpty()) {
                val root = marked.firstOrNull { it[3] == "root" }
                val reply = marked.firstOrNull { it[3] == "reply" }
                return ThreadRef(root?.get(1), reply?.get(1) ?: root?.get(1), root?.getOrNull(2))
            }
            // Positional: first = root, last = reply. "mention"-marked tags are not thread links.
            val positional = es.filter { it.getOrNull(3) != "mention" }
            return when (positional.size) {
                0 -> ThreadRef(null, null)
                1 -> ThreadRef(positional[0][1], positional[0][1])
                else -> ThreadRef(positional.first()[1], positional.last()[1])
            }
        }
    }
}

/** Replies below a note, as a tree flattened in reading order. */
object ReplyTree {
    data class Row(val event: Event, val depth: Int)

    /**
     * Every reply under [focusId] found in [events], depth-first: each reply is followed by
     * its own replies (oldest first at each level). Depth 0 answers the focus directly.
     */
    fun flatten(focusId: String, events: List<Event>, maxRows: Int = 500): List<Row> {
        val children = events.distinctBy { it.id }.filter { it.id != focusId }.groupBy { ThreadRef.of(it).parent }
        val out = ArrayList<Row>()
        val seen = HashSet<String>()
        fun walk(parent: String, depth: Int) {
            for (e in children[parent].orEmpty().sortedBy { it.createdAt }) {
                if (out.size >= maxRows || !seen.add(e.id)) continue
                out.add(Row(e, depth))
                walk(e.id, depth + 1)
            }
        }
        walk(focusId, 0)
        return out
    }
}

/** Builders for the events this app publishes. All return unsigned events for a signer. */
object Events {
    fun now(): Long = System.currentTimeMillis() / 1000

    /** Hashtags and nostr: mentions in [content] become "t" and "p"/"q" tags. */
    private fun contentTags(content: String): List<List<String>> {
        val tags = ArrayList<List<String>>()
        Content.parse(content).forEach { seg ->
            when (seg) {
                is Content.Segment.Hashtag -> tags += listOf("t", seg.tag.lowercase())
                is Content.Segment.ProfileRef -> tags += listOf("p", seg.pubkey)
                is Content.Segment.NoteRef ->
                    tags.add(listOfNotNull("q", seg.id, seg.relays.firstOrNull() ?: "", seg.author))
                is Content.Segment.AddressRef -> with(seg.address) {
                    tags.add(listOf("q", "$kind:$pubkey:$identifier", relays.firstOrNull() ?: ""))
                    tags.add(listOf("p", pubkey))
                }
                else -> Unit
            }
        }
        return tags.distinct()
    }

    fun note(pubkey: String, content: String, extraTags: List<List<String>> = emptyList()) =
        UnsignedEvent(pubkey, now(), Kind.TEXT_NOTE, extraTags + contentTags(content), content)

    /** A reply with NIP-10 marked tags, tagging everyone in the thread. */
    fun reply(pubkey: String, content: String, parent: Event, relayHint: String = "", extraTags: List<List<String>> = emptyList()): UnsignedEvent {
        val thread = ThreadRef.of(parent)
        val tags = ArrayList<List<String>>()
        val rootId = thread.root ?: parent.id
        if (rootId != parent.id) {
            tags += listOf("e", rootId, thread.rootRelay ?: "", "root")
            tags += listOf("e", parent.id, relayHint, "reply", parent.pubkey)
        } else {
            tags += listOf("e", parent.id, relayHint, "root", parent.pubkey)
        }
        val people = (listOf(parent.pubkey) + parent.tagValues("p")).filter { it != pubkey && Hex.isKey(it) }.distinct()
        people.forEach { tags += listOf("p", it) }
        return note(pubkey, content, tags + extraTags)
    }

    /** A quote (NIP-18): the note's nevent is appended to the text, with a "q" tag. */
    fun quote(pubkey: String, content: String, quoted: Event, relayHint: String = "", extraTags: List<List<String>> = emptyList()): UnsignedEvent {
        val ref = "nostr:" + Nip19.nevent(quoted.id, listOfNotNull(relayHint.takeIf { it.isNotEmpty() }), quoted.pubkey)
        val text = if (content.isBlank()) ref else content.trimEnd() + "\n\n" + ref
        return note(pubkey, text, listOf(listOf("p", quoted.pubkey)) + extraTags)
    }

    /** A repost (kind 6) carrying the reposted note's JSON. */
    fun repost(pubkey: String, reposted: Event, relayHint: String = "") = UnsignedEvent(
        pubkey, now(), Kind.REPOST,
        listOf(listOf("e", reposted.id, relayHint), listOf("p", reposted.pubkey)),
        reposted.toJson()
    )

    /** A like (kind 7, "+"). */
    /** A reaction (NIP-25): "+" is a like, an emoji, or ":code:" with [emojiUrl] for a custom emoji (NIP-30). */
    fun reaction(pubkey: String, target: Event, content: String = "+", emojiUrl: String? = null) = UnsignedEvent(
        pubkey, now(), Kind.REACTION,
        listOf(listOf("e", target.id), listOf("p", target.pubkey), listOf("k", target.kind.toString())) +
            listOfNotNull(emojiUrl?.let { url -> CustomEmoji.code(content)?.let { listOf("emoji", it, url) } }),
        content
    )

    /** NIP-09 deletion request for the user's own events. */
    fun deletion(pubkey: String, ids: List<String>, reason: String = "") =
        UnsignedEvent(pubkey, now(), Kind.DELETION, ids.map { listOf("e", it) }, reason)

    /**
     * A new contact list (kind 3) with [target] followed or unfollowed. Keeps every other
     * tag and the content (some clients store relays there) from [current].
     */
    fun contacts(pubkey: String, current: Event?, target: String, follow: Boolean): UnsignedEvent {
        val others = current?.tags.orEmpty().filterNot { it.size >= 2 && it[0] == "p" && it[1] == target }
        val tags = if (follow) others + listOf(listOf("p", target)) else others
        return UnsignedEvent(pubkey, now(), Kind.CONTACTS, tags, current?.content.orEmpty())
    }

    /** The contact list with hashtag [tag] followed or not (NIP-02 "t" tags, as Amethyst writes them). */
    fun contactsHashtag(pubkey: String, current: Event?, tag: String, follow: Boolean): UnsignedEvent {
        val t = tag.trim().removePrefix("#").lowercase()
        val others = current?.tags.orEmpty().filterNot { it.size >= 2 && it[0] == "t" && it[1].equals(t, ignoreCase = true) }
        val tags = if (follow && t.isNotEmpty()) others + listOf(listOf("t", t)) else others
        return UnsignedEvent(pubkey, now(), Kind.CONTACTS, tags, current?.content.orEmpty())
    }

    fun metadata(pubkey: String, profile: Profile, previous: Event? = null): UnsignedEvent {
        // Keep fields this app doesn't edit.
        val o = previous?.let { runCatching { JSONObject(it.content) }.getOrNull() } ?: JSONObject()
        fun set(key: String, value: String?) { if (value.isNullOrBlank()) o.remove(key) else o.put(key, value) }
        set("name", profile.name)
        set("display_name", profile.displayName)
        set("about", profile.about)
        set("picture", profile.picture)
        set("banner", profile.banner)
        set("nip05", profile.nip05)
        set("lud16", profile.lud16)
        set("website", profile.website)
        return UnsignedEvent(pubkey, now(), Kind.METADATA, emptyList(), o.toString())
    }
}

/** Custom emoji (NIP-30): ":shortcode:" in text, with an "emoji" tag giving its image. */
object CustomEmoji {
    val SHORTCODE = Regex(""":([A-Za-z0-9_-]{1,64}):""")

    /** Shortcode to image URL from an event's "emoji" tags (https only). */
    fun of(event: Event): Map<String, String> =
        event.tags.filter { it.size >= 3 && it[0] == "emoji" && SHORTCODE.matches(":" + it[1] + ":") && it[2].startsWith("https://") }
            .associate { it[1] to it[2] }

    /** "blob" for ":blob:", null for anything else. */
    fun code(content: String): String? = SHORTCODE.matchEntire(content)?.groupValues?.get(1)

    /** How a reaction reads: a like is ♥, otherwise its emoji or ":code:". */
    fun reactionLabel(content: String): String = if (content == "+" || content.isBlank()) "♥" else content
}

/** Splits note text into displayable pieces: text, links, images, hashtags and nostr: references. */
object Content {
    sealed interface Segment {
        data class Text(val text: String) : Segment
        data class Link(val url: String) : Segment
        data class Image(val url: String) : Segment
        data class Video(val url: String) : Segment
        data class Hashtag(val tag: String) : Segment
        data class ProfileRef(val pubkey: String, val raw: String) : Segment
        data class NoteRef(val id: String, val relays: List<String>, val author: String?, val raw: String) : Segment
        data class AddressRef(val address: Nip19.Address, val raw: String) : Segment
        data class Emoji(val code: String, val url: String) : Segment
    }

    private val TOKEN = Regex(
        """(nostr:(?:npub|nprofile|note|nevent|naddr)1[02-9ac-hj-np-z]+)|(https?://[^\s<>"']+)|(?<![\w#])#([\p{L}\p{M}\p{N}_]+)"""
    )
    private val IMAGE = Regex("""\.(png|jpe?g|gif|webp|avif)(\?.*)?$""", RegexOption.IGNORE_CASE)
    private val VIDEO = Regex("""\.(mp4|webm|mov|m4v|m3u8)(\?.*)?$""", RegexOption.IGNORE_CASE)

    /** Like [parse], with ":code:" turned into [Segment.Emoji] for the given custom emoji. */
    fun parse(content: String, emojis: Map<String, String>): List<Segment> =
        if (emojis.isEmpty()) parse(content) else parse(content).flatMap { seg ->
            if (seg !is Segment.Text) return@flatMap listOf(seg)
            val parts = ArrayList<Segment>()
            var last = 0
            for (m in CustomEmoji.SHORTCODE.findAll(seg.text)) {
                val url = emojis[m.groupValues[1]] ?: continue
                if (m.range.first > last) parts += Segment.Text(seg.text.substring(last, m.range.first))
                parts += Segment.Emoji(m.groupValues[1], url)
                last = m.range.last + 1
            }
            if (last < seg.text.length) parts += Segment.Text(seg.text.substring(last))
            parts
        }

    fun parse(content: String): List<Segment> {
        val out = ArrayList<Segment>()
        var last = 0
        for (m in TOKEN.findAll(content)) {
            if (m.range.first > last) out += Segment.Text(content.substring(last, m.range.first))
            val nostr = m.groups[1]?.value
            val url = m.groups[2]?.value?.trimEnd('.', ',', ')', '!', '?', ';', ':')
            val tag = m.groups[3]?.value
            var consumedTo = m.range.last + 1
            when {
                nostr != null -> out += when (val e = Nip19.decode(nostr)) {
                    is Nip19.Pubkey -> Segment.ProfileRef(e.hex, nostr)
                    is Nip19.Note -> Segment.NoteRef(e.id, e.relays, e.author, nostr)
                    is Nip19.Address -> Segment.AddressRef(e, nostr)
                    else -> Segment.Text(nostr)
                }
                url != null -> {
                    consumedTo = m.range.first + url.length
                    out += when {
                        IMAGE.containsMatchIn(url) -> Segment.Image(url)
                        VIDEO.containsMatchIn(url) -> Segment.Video(url)
                        else -> Segment.Link(url)
                    }
                }
                tag != null -> out += Segment.Hashtag(tag)
            }
            last = consumedTo
        }
        if (last < content.length) out += Segment.Text(content.substring(last))
        return out
    }
}
