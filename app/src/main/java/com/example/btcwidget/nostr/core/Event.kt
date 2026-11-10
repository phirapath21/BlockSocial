package com.example.btcwidget.nostr.core

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Event kinds this app reads or writes. */
object Kind {
    const val METADATA = 0
    const val TEXT_NOTE = 1
    const val CONTACTS = 3
    const val DELETION = 5
    const val REPOST = 6
    const val REACTION = 7
    const val ZAP_REQUEST = 9734
    const val ZAP_RECEIPT = 9735
    const val MUTE_LIST = 10000
    const val BOOKMARKS = 10003
    const val RELAY_LIST = 10002
}

/** An event before signing: everything but id and sig. */
data class UnsignedEvent(
    val pubkey: String,
    val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String
) {
    /** NIP-01 id: sha256 of [0, pubkey, created_at, kind, tags, content], serialized without spaces. */
    val id: String
        get() = Hex.encode(MessageDigest.getInstance("SHA-256").digest(serializeForId().toByteArray(Charsets.UTF_8)))

    fun serializeForId(): String = buildString {
        append("[0,")
        Json.string(this, pubkey); append(',')
        append(createdAt); append(',')
        append(kind); append(',')
        Json.tags(this, tags); append(',')
        Json.string(this, content)
        append(']')
    }

    fun signed(sig: String) = Event(id, pubkey, createdAt, kind, tags, content, sig)

    /** The unsigned event as JSON, as signer apps (NIP-55) expect it. */
    fun toJson(): String = buildString {
        append("{\"pubkey\":"); Json.string(this, pubkey)
        append(",\"created_at\":").append(createdAt)
        append(",\"kind\":").append(kind)
        append(",\"tags\":"); Json.tags(this, tags)
        append(",\"content\":"); Json.string(this, content)
        append('}')
    }
}

/** A signed Nostr event (NIP-01). */
data class Event(
    val id: String,
    val pubkey: String,
    val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: String
) {
    val unsigned get() = UnsignedEvent(pubkey, createdAt, kind, tags, content)

    /** Values of every tag named [name], e.g. all "p" pubkeys. */
    fun tagValues(name: String): List<String> = tags.filter { it.size >= 2 && it[0] == name }.map { it[1] }
    fun firstTag(name: String): List<String>? = tags.firstOrNull { it.size >= 2 && it[0] == name }

    /** True when the id matches the content and the signature is valid. */
    fun verify(): Boolean =
        Hex.isKey(id) && Hex.isKey(pubkey) && sig.length == 128 &&
            unsigned.id == id && Schnorr.verify(sig, id, pubkey)

    fun toJson(): String = buildString {
        append("{\"id\":"); Json.string(this, id)
        append(",\"pubkey\":"); Json.string(this, pubkey)
        append(",\"created_at\":").append(createdAt)
        append(",\"kind\":").append(kind)
        append(",\"tags\":"); Json.tags(this, tags)
        append(",\"content\":"); Json.string(this, content)
        append(",\"sig\":"); Json.string(this, sig)
        append('}')
    }

    companion object {
        fun fromJson(json: String): Event = fromJson(JSONObject(json))

        fun fromJson(o: JSONObject): Event {
            val tagsJson = o.getJSONArray("tags")
            val tags = (0 until tagsJson.length()).map { i ->
                val t = tagsJson.getJSONArray(i)
                (0 until t.length()).map { t.getString(it) }
            }
            return Event(
                id = o.getString("id"),
                pubkey = o.getString("pubkey"),
                createdAt = o.getLong("created_at"),
                kind = o.getInt("kind"),
                tags = tags,
                content = o.getString("content"),
                sig = o.getString("sig")
            )
        }
    }
}

/** Minimal JSON writing with NIP-01's exact escaping, so ids match other clients. */
object Json {
    fun string(out: StringBuilder, s: String) {
        out.append('"')
        for (c in s) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (c < ' ') out.append(String.format("\\u%04x", c.code)) else out.append(c)
            }
        }
        out.append('"')
    }

    fun tags(out: StringBuilder, tags: List<List<String>>) {
        out.append('[')
        tags.forEachIndexed { i, tag ->
            if (i > 0) out.append(',')
            out.append('[')
            tag.forEachIndexed { j, v ->
                if (j > 0) out.append(',')
                string(out, v)
            }
            out.append(']')
        }
        out.append(']')
    }

    fun quote(s: String): String = StringBuilder().also { string(it, s) }.toString()

    fun stringArray(values: List<String>): JSONArray = JSONArray().apply { values.forEach { put(it) } }
}
