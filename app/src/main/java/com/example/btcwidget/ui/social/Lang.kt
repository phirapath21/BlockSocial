package com.example.btcwidget.ui.social

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The app's language: English, or Thai. Every text the app shows goes through [tr] with its
 * English wording, which is also the key into the Thai table ([THAI]); a text missing from the
 * table shows in English. Follows the phone's language unless chosen in Settings.
 *
 * Kept in Kotlin rather than Android string resources so texts built outside the screens
 * (menus, messages, toasts) translate the same way, and switching language redraws at once.
 */
object Lang {
    enum class Choice(val label: String) { SYSTEM("Phone's language"), ENGLISH("English"), THAI("ไทย") }

    /** What the user picked (the phone's language unless changed in Settings). */
    var choice by mutableStateOf(Choice.SYSTEM)
        private set

    /** Whether texts show in Thai right now. Compose state: screens redraw when it changes. */
    var thai by mutableStateOf(false)
        private set

    /** Applies the saved choice, against the phone's language (Android 13+: or this app's, set in system settings). */
    fun load(context: android.content.Context) =
        apply(com.example.btcwidget.AppPrefs.language(context), context.resources.configuration.locales[0].language)

    /** Saves and applies [choice]; every screen redraws in it. */
    fun choose(context: android.content.Context, choice: Choice) {
        com.example.btcwidget.AppPrefs.setLanguage(context, choice)
        load(context)
    }

    /** Sets the language from [choice]; [phoneLanguage] is the phone's (e.g. "th", "en"). */
    fun apply(choice: Choice, phoneLanguage: String) {
        this.choice = choice
        thai = when (choice) {
            Choice.SYSTEM -> phoneLanguage == "th"
            Choice.ENGLISH -> false
            Choice.THAI -> true
        }
    }
}

/**
 * [en] in the app's language. "{0}", "{1}"… in it are replaced by [args] in order, so the
 * translation can put them where Thai needs them.
 */
fun tr(en: String, vararg args: Any?): String {
    val text = if (Lang.thai) THAI[en] ?: en else en
    if (args.isEmpty()) return text
    var out = text
    args.forEachIndexed { i, a -> out = out.replace("{$i}", a?.toString() ?: "") }
    return out
}

/** "s" after a count in English; nothing in Thai, which has no plural. */
fun plural(n: Number): String = if (Lang.thai || n.toLong() == 1L) "" else "s"

/** A Home feed's name in the app's language (the feeds themselves keep English names). */
fun feedTitle(f: com.example.btcwidget.nostr.core.HomeFeed): String = when (f) {
    is com.example.btcwidget.nostr.core.HomeFeed.Following -> tr(if (f.replies) "Latest with replies" else "Latest")
    is com.example.btcwidget.nostr.core.HomeFeed.Global ->
        f.relay?.let { tr("Relay: {0}", it.removePrefix("wss://").removePrefix("ws://")) } ?: tr("Global")
    is com.example.btcwidget.nostr.core.HomeFeed.Hashtag -> tr("Topic: {0}", f.tag)
    is com.example.btcwidget.nostr.core.HomeFeed.Media -> tr(if (f.videos) "Videos" else "Pictures") + if (f.everyone) tr(": everyone") else ""
    is com.example.btcwidget.nostr.core.HomeFeed.People -> tr("List: {0}", f.name)
    else -> f.title
}

/** The line under a Home feed's name, in the app's language. */
fun feedSubtitle(f: com.example.btcwidget.nostr.core.HomeFeed): String = when (f) {
    is com.example.btcwidget.nostr.core.HomeFeed.Following -> tr(if (f.replies) "Notes and replies by your follows" else "Latest notes by your follows")
    is com.example.btcwidget.nostr.core.HomeFeed.Global ->
        f.relay?.let { tr("Recent notes on {0}", it) } ?: tr("Recent notes from everyone on your relays")
    is com.example.btcwidget.nostr.core.HomeFeed.Hashtag -> tr("All notes tagged #{0}", f.tag)
    is com.example.btcwidget.nostr.core.HomeFeed.Dvm -> tr("Picked by a feed service (DVM)")
    is com.example.btcwidget.nostr.core.HomeFeed.Media ->
        tr(if (f.videos) "Video posts" else "Picture posts") + tr(if (f.everyone) " from everyone on your relays" else " by your follows")
    is com.example.btcwidget.nostr.core.HomeFeed.People -> tr("Notes by the people in this list")
    else -> f.subtitle
}
