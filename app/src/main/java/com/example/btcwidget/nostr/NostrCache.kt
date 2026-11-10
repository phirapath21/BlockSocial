package com.example.btcwidget.nostr

import com.example.btcwidget.nostr.core.Event
import org.json.JSONObject
import java.io.File

/**
 * Small on-disk cache so Nostr screens open instantly with what was seen last time, while
 * relays refresh in the background (Amethyst keeps a local event cache for the same reason).
 * Plain JSON lines in the app's private cache dir: Android may clear it, which only costs a
 * slower first load. Everything here is public Nostr data; it's deleted on logout.
 */
class NostrCache(private val dir: File) {
    init { dir.mkdirs() }

    /** Signed events saved under [name]; events are re-verified, so a damaged file is harmless. */
    fun loadEvents(name: String): List<Event> = readLines(name).mapNotNull { line ->
        runCatching { Event.fromJson(line) }.getOrNull()?.takeIf { it.verify() }
    }

    fun saveEvents(name: String, events: Collection<Event>) = writeLines(name, events.map { it.toJson() })

    /** The home timeline as last shown. */
    fun loadFeed(name: String): List<FeedItem> = readLines(name).mapNotNull { line ->
        runCatching {
            val o = JSONObject(line)
            val note = Event.fromJson(o.getJSONObject("n")).takeIf { it.verify() } ?: return@runCatching null
            FeedItem(note, repostedBy = o.optString("by", "").ifEmpty { null }, sortTime = o.optLong("t", note.createdAt))
        }.getOrNull()
    }

    fun saveFeed(name: String, items: List<FeedItem>) = writeLines(name, items.map { item ->
        // Written by hand so the note's JSON stays exactly as signed.
        "{\"n\":" + item.note.toJson() + ",\"by\":\"" + (item.repostedBy ?: "") + "\",\"t\":" + item.sortTime + "}"
    })

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun file(name: String) = File(dir, "$name.jsonl")

    private fun readLines(name: String): List<String> =
        runCatching { file(name).takeIf { it.exists() }?.readLines().orEmpty() }.getOrDefault(emptyList())

    /** Writes to a temp file then renames, so a crash mid-write never leaves half a file. */
    private fun writeLines(name: String, lines: List<String>) {
        runCatching {
            val tmp = File(dir, "$name.tmp")
            tmp.writeText(lines.joinToString("\n"))
            tmp.renameTo(file(name))
        }
    }
}
