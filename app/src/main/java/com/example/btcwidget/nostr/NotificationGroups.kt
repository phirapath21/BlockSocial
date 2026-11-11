package com.example.btcwidget.nostr

/**
 * Notifications as shown in the list: likes, reposts and zaps of the same note are folded
 * into one row ("Alice, Bob and 5 others liked your note"); replies and mentions stay one
 * per row, since each has its own text. Newest group first.
 */
data class NotificationGroup(val type: NostrNotification.Type, val noteId: String?, val items: List<NostrNotification>) {
    /** Stable across refreshes, so the list keeps its place. */
    val key: String get() = if (items.size == 1 && noteId == null) items[0].event.id else "${type.name}:${noteId ?: items[0].event.id}"
    val latest: NostrNotification get() = items[0]
    val createdAt: Long get() = latest.event.createdAt
    /** Who acted, newest first, each once. */
    val actors: List<String> get() = items.map { it.actor }.distinct()
    val totalMsat: Long get() = items.sumOf { it.zapMsat }

    companion object {
        private val FOLDED = setOf(NostrNotification.Type.LIKE, NostrNotification.Type.REPOST, NostrNotification.Type.ZAP)

        fun of(notifications: List<NostrNotification>): List<NotificationGroup> {
            val sorted = notifications.distinctBy { it.event.id }.sortedByDescending { it.event.createdAt }
            val groups = LinkedHashMap<String, MutableList<NostrNotification>>()
            sorted.forEach { n ->
                val k = if (n.type in FOLDED && n.noteId != null) "${n.type.name}:${n.noteId}" else "one:${n.event.id}"
                groups.getOrPut(k) { mutableListOf() }.add(n)
            }
            return groups.values.map { NotificationGroup(it[0].type, it[0].noteId, it) }
        }

        /** "Alice", "Alice and Bob", "Alice, Bob and 3 others". */
        fun names(names: List<String>, total: Int): String = when {
            total <= 1 -> names.firstOrNull().orEmpty()
            total == 2 && names.size >= 2 -> "${names[0]} and ${names[1]}"
            else -> {
                val others = total - 2
                "${names[0]}, ${names.getOrElse(1) { "" }} and $others " + if (others == 1) "other" else "others"
            }
        }
    }
}
