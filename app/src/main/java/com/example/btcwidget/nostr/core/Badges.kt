package com.example.btcwidget.nostr.core

/**
 * Badges (NIP-58). An issuer defines a badge (kind 30009) and awards it (kind 8) to people;
 * each person chooses which awards to show on their profile (kind 30008, d = "profile_badges").
 * A badge is shown only when the award really came from the badge's issuer to that person.
 */
object Badges {
    const val AWARD = 8
    const val PROFILE = 30008
    const val DEFINITION = 30009
    const val PROFILE_D = "profile_badges"

    /** "30009:<issuer>:<d>" */
    data class Ref(val issuer: String, val id: String) {
        val coordinate get() = "$DEFINITION:$issuer:$id"

        companion object {
            fun parse(a: String): Ref? {
                val parts = a.split(':', limit = 3)
                if (parts.size != 3 || parts[0] != DEFINITION.toString() || !Hex.isKey(parts[1]) || parts[2].isEmpty()) return null
                return Ref(parts[1], parts[2])
            }
        }
    }

    data class Definition(val ref: Ref, val name: String, val description: String?, val image: String?) {
        companion object {
            fun of(e: Event): Definition? {
                if (e.kind != DEFINITION) return null
                val d = e.firstTag("d")?.get(1)?.takeIf { it.isNotEmpty() } ?: return null
                fun tag(n: String) = e.firstTag(n)?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
                val image = (tag("thumb") ?: tag("image"))?.takeIf { it.startsWith("https://") }
                return Definition(Ref(e.pubkey, d), tag("name") ?: d, tag("description"), image)
            }
        }
    }

    /** The (badge, award id) pairs a profile badges event lists, in order. */
    fun shown(profileBadges: Event?): List<Pair<Ref, String>> {
        if (profileBadges == null || profileBadges.kind != PROFILE || profileBadges.firstTag("d")?.get(1) != PROFILE_D) return emptyList()
        val out = ArrayList<Pair<Ref, String>>()
        val tags = profileBadges.tags
        var i = 0
        while (i < tags.size) {
            val t = tags[i]
            if (t.size >= 2 && t[0] == "a") {
                val next = tags.getOrNull(i + 1)
                val ref = Ref.parse(t[1])
                if (ref != null && next != null && next.size >= 2 && next[0] == "e" && Hex.isKey(next[1])) {
                    out += ref to next[1]
                    i += 2
                    continue
                }
            }
            i++
        }
        return out.distinctBy { it.first }
    }

    /** True when [award] is [ref]'s issuer awarding that badge to [owner]. */
    fun isValidAward(award: Event, ref: Ref, owner: String): Boolean =
        award.kind == AWARD && award.pubkey == ref.issuer &&
            award.tagValues("a").contains(ref.coordinate) && award.tagValues("p").contains(owner)
}

/**
 * Who to follow: accounts that many of the people you follow also follow, most shared first.
 * [contactLists] maps each followed account to whom they follow.
 */
object FollowSuggestions {
    fun rank(me: String, following: Set<String>, contactLists: Map<String, Collection<String>>, exclude: Set<String> = emptySet(), limit: Int = 30): List<Pair<String, Int>> {
        val counts = HashMap<String, Int>()
        contactLists.forEach { (owner, list) ->
            if (owner !in following) return@forEach
            list.toSet().forEach { pk ->
                if (pk != me && pk !in following && pk !in exclude && Hex.isKey(pk)) counts[pk] = (counts[pk] ?: 0) + 1
            }
        }
        return counts.entries.filter { it.value >= 2 }.sortedByDescending { it.value }.take(limit).map { it.key to it.value }
    }
}
