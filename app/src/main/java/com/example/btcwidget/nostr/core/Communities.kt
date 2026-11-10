package com.example.btcwidget.nostr.core

/**
 * Communities (NIP-72): a community is defined by its owner (kind 34550) with moderators;
 * posts (NIP-22 kind 1111, or kind 1 from older apps) point at it with an "a"/"A" tag and
 * show once a moderator approves them (kind 4550).
 */
object Communities {
    const val DEFINITION = 34550
    const val APPROVAL = 4550
    const val POST = 1111
    /** The user's joined communities (NIP-51). */
    const val LIST = 10004

    data class Community(
        val event: Event,
        val id: String,
        val name: String,
        val description: String?,
        val image: String?,
        val moderators: Set<String>,
        val relays: List<String>
    ) {
        val owner get() = event.pubkey
        val coordinate get() = "$DEFINITION:$owner:$id"
        fun canApprove(pubkey: String) = pubkey == owner || pubkey in moderators
    }

    fun of(e: Event): Community? {
        if (e.kind != DEFINITION) return null
        val d = e.firstTag("d")?.get(1)?.takeIf { it.isNotEmpty() } ?: return null
        fun tag(n: String) = e.firstTag(n)?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
        return Community(
            event = e,
            id = d,
            name = tag("name") ?: d,
            description = tag("description"),
            image = tag("image")?.takeIf { it.startsWith("https://") },
            moderators = e.tags.filter { it.size >= 4 && it[0] == "p" && it[3].equals("moderator", true) && Hex.isKey(it[1]) }.map { it[1] }.toSet(),
            relays = e.tags.filter { it.size >= 2 && it[0] == "relay" }.mapNotNull { normalizeRelayUrl(it[1]) }.distinct()
        )
    }

    /** The community coordinate a post belongs to, if any. */
    fun communityOf(post: Event): String? =
        post.tags.firstOrNull { it.size >= 2 && (it[0] == "A" || it[0] == "a") && it[1].startsWith("$DEFINITION:") }?.get(1)

    /** Top-level posts (not replies to other posts) in [community] that a moderator approved. */
    fun approved(community: Community, posts: List<Event>, approvals: List<Event>): Set<String> {
        val ok = approvals.filter { it.kind == APPROVAL && community.canApprove(it.pubkey) && it.tagValues("a").contains(community.coordinate) }
            .flatMap { it.tagValues("e") }.toSet()
        return posts.filter { it.id in ok || community.canApprove(it.pubkey) }.map { it.id }.toSet()
    }

    /** A new top-level post in [community] (NIP-22 comment on the community). */
    fun post(pubkey: String, community: Community, content: String, extraTags: List<List<String>> = emptyList(), now: Long = Events.now()): UnsignedEvent {
        val relay = community.relays.firstOrNull() ?: ""
        val tags = listOf(
            listOf("A", community.coordinate, relay), listOf("a", community.coordinate, relay),
            listOf("P", community.owner), listOf("p", community.owner),
            listOf("K", DEFINITION.toString()), listOf("k", DEFINITION.toString())
        ) + extraTags
        return UnsignedEvent(pubkey, now, POST, tags, content)
    }

    /** The joined-communities list with [community] added or removed, keeping other entries. */
    fun listWith(pubkey: String, current: Event?, coordinate: String, join: Boolean, now: Long = Events.now()): UnsignedEvent {
        val others = current?.tags.orEmpty().filterNot { it.size >= 2 && it[0] == "a" && it[1] == coordinate }
        return UnsignedEvent(pubkey, now, LIST, if (join) others + listOf(listOf("a", coordinate)) else others, current?.content.orEmpty())
    }
}

/** Public chat channels (NIP-28): created by kind 40, renamed by its creator with kind 41, messages are kind 42. */
object PublicChat {
    const val CREATE = 40
    const val METADATA = 41
    const val MESSAGE = 42

    data class Channel(val id: String, val creator: String, val name: String, val about: String?, val picture: String?, val relay: String?)

    /** A channel from its creation event, updated by the creator's newest metadata event. */
    fun channel(create: Event, metadata: List<Event> = emptyList()): Channel? {
        if (create.kind != CREATE) return null
        val latest = metadata.filter { it.kind == METADATA && it.pubkey == create.pubkey && it.tagValues("e").contains(create.id) }
            .maxByOrNull { it.createdAt }
        val o = runCatching { org.json.JSONObject((latest ?: create).content) }.getOrNull()
            ?: runCatching { org.json.JSONObject(create.content) }.getOrNull() ?: return null
        val name = o.optString("name").trim().takeIf { it.isNotEmpty() } ?: return null
        return Channel(
            id = create.id, creator = create.pubkey, name = name.take(80),
            about = o.optString("about").trim().takeIf { it.isNotEmpty() },
            picture = o.optString("picture").takeIf { it.startsWith("https://") },
            relay = latest?.firstTag("e")?.getOrNull(2)?.takeIf { it.startsWith("wss://") }
        )
    }

    /** The channel a message belongs to: its root "e" tag (or the first "e"). */
    fun channelOf(message: Event): String? =
        message.tags.firstOrNull { it.size >= 4 && it[0] == "e" && it[3] == "root" }?.get(1) ?: message.tagValues("e").firstOrNull()

    fun message(pubkey: String, channelId: String, content: String, relay: String = "", now: Long = Events.now()) =
        UnsignedEvent(pubkey, now, MESSAGE, listOf(listOf("e", channelId, relay, "root")), content)
}

/**
 * Relay-based groups (NIP-29): a group lives on one relay and is named "host'id"; the relay
 * publishes its metadata (kind 39000) and members chat with kind 9 events tagged "h".
 */
object RelayGroups {
    const val CHAT = 9
    const val JOIN = 9021
    const val METADATA = 39000

    data class Address(val relay: String, val id: String) {
        override fun toString() = relay.removePrefix("wss://") + "'" + id
    }

    data class Group(val address: Address, val name: String, val about: String?, val picture: String?, val closed: Boolean)

    /** "groups.example.com'abc" or "wss://groups.example.com'abc". */
    fun parse(input: String): Address? {
        val t = input.trim()
        val i = t.lastIndexOf('\'')
        if (i <= 0 || i == t.length - 1) return null
        val host = t.substring(0, i)
        val id = t.substring(i + 1)
        if (!id.matches(Regex("[a-zA-Z0-9_-]{1,64}"))) return null
        val relay = normalizeRelayUrl(if (host.startsWith("ws")) host else "wss://$host") ?: return null
        return Address(relay, id)
    }

    fun metadata(address: Address, events: List<Event>): Group {
        val e = events.filter { it.kind == METADATA && it.firstTag("d")?.get(1) == address.id }.maxByOrNull { it.createdAt }
        fun tag(n: String) = e?.firstTag(n)?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
        return Group(
            address, tag("name") ?: address.id, tag("about"), tag("picture")?.takeIf { it.startsWith("https://") },
            closed = e?.tags?.any { it.firstOrNull() == "closed" } == true
        )
    }

    fun chat(pubkey: String, group: Address, content: String, now: Long = Events.now()) =
        UnsignedEvent(pubkey, now, CHAT, listOf(listOf("h", group.id)), content)

    fun joinRequest(pubkey: String, group: Address, now: Long = Events.now()) =
        UnsignedEvent(pubkey, now, JOIN, listOf(listOf("h", group.id)), "")

    /** NIP-42 answer to [challenge] from [relay]. */
    fun auth(pubkey: String, relay: String, challenge: String, now: Long = Events.now()) =
        UnsignedEvent(pubkey, now, 22242, listOf(listOf("relay", relay), listOf("challenge", challenge)), "")
}
