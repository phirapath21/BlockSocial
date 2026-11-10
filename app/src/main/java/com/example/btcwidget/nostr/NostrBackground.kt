package com.example.btcwidget.nostr

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.btcwidget.MainActivity
import com.example.btcwidget.R
import com.example.btcwidget.alerts.AlertNotifier
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Filter
import com.example.btcwidget.nostr.core.Kind
import com.example.btcwidget.nostr.core.Nip17
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.nostr.core.ThreadRef
import com.example.btcwidget.nostr.core.Zaps
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The background part of Nostr notifications: run with each periodic refresh (every 15–60
 * minutes, as Android allows), it checks the user's relays for new replies, mentions,
 * reposts, zaps and private messages, and posts phone notifications. Not instant: it
 * depends on the refresh interval and Android's battery rules.
 */
object NostrBackground {
    private const val CHANNEL_SOCIAL = "nostr_activity"
    private const val CHANNEL_DM = "nostr_messages"
    private const val ID_SOCIAL = 200
    private const val ID_DM_BASE = 300

    /** Extra on the notification's intent: which tab to open. */
    const val EXTRA_OPEN_TAB = "open_tab"

    /** Whether the periodic work should keep running for Nostr (logged in, notifications on). */
    fun wanted(context: Context): Boolean =
        NostrAccountStore.account(context) != null && NostrAccountStore.pushEnabled(context)

    suspend fun check(context: Context) {
        if (!wanted(context) || !AlertNotifier.canNotify(context)) return
        val me = NostrAccountStore.account(context)?.pubkey ?: return
        val pool = RelayPool()
        try {
            val relays = (NostrAccountStore.dmInbox(context) + (NostrAccountStore.relays(context) ?: RelayPool.DEFAULT_RELAYS)).distinct().take(8)
            pool.setRelays(relays)
            withTimeoutOrNull(25_000) {
                val names = cachedNames(context)
                checkActivity(context, pool, me, names)
                checkMessages(context, pool, me, names)
            }
        } finally {
            pool.closeAll()
        }
    }

    private fun cachedNames(context: Context): Map<String, String> =
        NostrCache(java.io.File(context.cacheDir, "nostr")).loadEvents("profiles")
            .mapNotNull { Profile.fromEvent(it) }.associate { it.pubkey to it.bestName }

    private fun name(names: Map<String, String>, pubkey: String) = names[pubkey] ?: Profile.shortNpub(pubkey)

    private suspend fun checkActivity(context: Context, pool: RelayPool, me: String, names: Map<String, String>) {
        val now = System.currentTimeMillis() / 1000
        // First run: only the last hour, so a new install doesn't announce old history.
        val since = NostrAccountStore.notificationsNotified(context).takeIf { it > 0 } ?: (now - 3600)
        val events = pool.query(
            listOf(Filter(kinds = listOf(Kind.TEXT_NOTE, Kind.REPOST, Kind.REACTION, Kind.ZAP_RECEIPT), tags = mapOf("p" to listOf(me)), since = since + 1, limit = 50)),
            timeoutMs = 10_000
        ).filter { it.pubkey != me && it.createdAt > since }
        if (events.isEmpty()) return
        val off = NostrAccountStore.notificationsOff(context)
        val lines = events.sortedByDescending { it.createdAt }.filter { typeOf(it) !in off }.mapNotNull { describe(it, names) }
        NostrAccountStore.setNotificationsNotified(context, events.maxOf { it.createdAt })
        if (lines.isEmpty()) return
        post(
            context, CHANNEL_SOCIAL, ID_SOCIAL,
            title = if (lines.size == 1) "Blockface" else "${lines.size} new on Nostr",
            text = lines.first(),
            lines = lines.take(6),
            tab = "NOTIFICATIONS",
            private = false
        )
    }

    /** The notification type name used in settings. */
    private fun typeOf(e: Event): String = when (e.kind) {
        Kind.TEXT_NOTE -> if (ThreadRef.of(e).isReply) "reply" else "mention"
        Kind.REPOST -> "repost"
        Kind.REACTION -> "like"
        Kind.ZAP_RECEIPT -> "zap"
        else -> "other"
    }

    private fun describe(e: Event, names: Map<String, String>): String? = when (e.kind) {
        Kind.TEXT_NOTE -> {
            val who = name(names, e.pubkey)
            val verb = if (ThreadRef.of(e).isReply) "replied" else "mentioned you"
            "$who $verb: ${e.content.take(80).replace('\n', ' ')}"
        }
        Kind.REPOST -> "${name(names, e.pubkey)} reposted your note"
        Kind.REACTION -> if (e.content == "-") null
            else "${name(names, e.pubkey)} " + (if (e.content == "+" || e.content.isBlank()) "liked your note" else "reacted ${e.content} to your note")
        Kind.ZAP_RECEIPT -> Zaps.parseReceipt(e)?.let { r ->
            "⚡ ${r.sender?.let { name(names, it) } ?: "Someone"} zapped ${r.amountMsat / 1000} sats"
        }
        else -> null
    }

    private suspend fun checkMessages(context: Context, pool: RelayPool, me: String, names: Map<String, String>) {
        if ("dm" in NostrAccountStore.notificationsOff(context)) return
        val signer = NostrAccountStore.signer(context) ?: return
        val now = System.currentTimeMillis() / 1000
        val notified = NostrAccountStore.dmNotified(context).takeIf { it > 0 } ?: (now - 3600)
        // Wraps are back-dated up to two days, so look back that far and filter on the real time inside.
        val wraps = pool.query(
            listOf(Filter(kinds = listOf(Nip17.GIFT_WRAP), tags = mapOf("p" to listOf(me)), since = now - 2 * 24 * 3600 - 60, limit = 200)),
            timeoutMs = 10_000
        )
        val fresh = wraps.mapNotNull { wrap ->
            // A signer app that would need to ask returns null: those wait until the app is opened.
            runCatching { Nip17.unwrap(wrap) { c, peer -> signer.nip44Decrypt(c, peer, interactive = false) ?: error("locked") } }.getOrNull()
        }.filter { it.sender != me && it.createdAt > notified && it.isBubble }.distinctBy { it.id }
        if (fresh.isEmpty()) return
        NostrAccountStore.setDmNotified(context, fresh.maxOf { it.createdAt })
        fresh.groupBy { it.peers(me) }.forEach { (peers, msgs) ->
            val latest = msgs.maxBy { it.createdAt }
            post(
                context, CHANNEL_DM, ID_DM_BASE + Math.floorMod(peers.joinToString(",").hashCode(), 1000),
                title = name(names, latest.sender) + if (msgs.size > 1) " (${msgs.size})" else "",
                text = preview(latest).take(200),
                lines = msgs.sortedBy { it.createdAt }.takeLast(5).map { preview(it).take(100) },
                tab = "MESSAGES",
                private = true
            )
        }
    }

    private fun preview(m: Nip17.Message) = if (m.kind == Nip17.FILE) "📷 Photo" else m.content

    private fun post(context: Context, channel: String, id: Int, title: String, text: String, lines: List<String>, tab: String, private: Boolean) {
        ensureChannels(context)
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_TAB, tab)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val style = NotificationCompat.InboxStyle().also { s -> lines.forEach { s.addLine(it) } }
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(if (private) NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_SOCIAL)
        if (private) {
            // Message text stays off the lock screen.
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(
                    NotificationCompat.Builder(context, channel)
                        .setSmallIcon(R.drawable.ic_stat_alert)
                        .setContentTitle("New private message")
                        .build()
                )
        }
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (e: SecurityException) {
            // Permission revoked meanwhile.
        }
    }

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_SOCIAL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_SOCIAL, "Nostr activity", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Replies, mentions, reposts, likes and zaps"
                }
            )
        }
        if (manager.getNotificationChannel(CHANNEL_DM) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_DM, "Private messages", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "New Nostr direct messages"
                }
            )
        }
    }
}
