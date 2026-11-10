package com.example.btcwidget.nostr

import android.content.Context

/**
 * Unsent posts, kept on the phone so leaving the composer loses nothing. One draft per
 * account and target: a new post, or a reply to / quote of a given note. Not backed up;
 * cleared on logout.
 */
object Drafts {
    private const val FILE = "nostr_drafts"
    private const val MAX = 50

    /** Drafts belong to the account that wrote them ([pubkey]). */
    fun key(pubkey: String?, replyTo: String?, quote: String?): String = (pubkey ?: "") + "/" + when {
        replyTo != null -> "reply:$replyTo"
        quote != null -> "quote:$quote"
        else -> "new"
    }

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun get(context: Context, key: String): String? = prefs(context).getString(key, null)

    /** Saves [text] (or removes the draft when blank), keeping only the newest [MAX]. */
    fun put(context: Context, key: String, text: String) {
        val p = prefs(context)
        if (text.isBlank()) { p.edit().remove(key).remove("$key@").apply(); return }
        val e = p.edit().putString(key, text).putLong("$key@", System.currentTimeMillis())
        val stamps = p.all.filterKeys { it.endsWith("@") && it != "$key@" }.mapValues { it.value as? Long ?: 0L }
        if (stamps.size >= MAX) {
            stamps.entries.sortedBy { it.value }.take(stamps.size - MAX + 1).forEach { e.remove(it.key).remove(it.key.removeSuffix("@")) }
        }
        e.apply()
    }

    fun remove(context: Context, key: String) = put(context, key, "")

    /** Deletes [pubkey]'s drafts (on logout or removing the account). */
    fun clear(context: Context, pubkey: String) {
        val p = prefs(context)
        val e = p.edit()
        p.all.keys.filter { it.startsWith("$pubkey/") }.forEach { e.remove(it) }
        e.apply()
    }
}
