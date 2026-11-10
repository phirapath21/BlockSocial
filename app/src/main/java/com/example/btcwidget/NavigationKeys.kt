package com.example.btcwidget

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

/** A note and its thread. */
@Serializable data class NoteKey(val id: String) : NavKey

/** A profile and its notes. */
@Serializable data class ProfileKey(val pubkey: String) : NavKey

/** Notes with a hashtag. */
@Serializable data class HashtagKey(val tag: String) : NavKey

/** Writing a note; at most one of [replyTo] / [quote] (note ids). */
@Serializable data class ComposeKey(val replyTo: String? = null, val quote: String? = null) : NavKey

/** Logging in or creating a Nostr account. */
@Serializable data object LoginKey : NavKey

/** Editing the user's own profile. */
@Serializable data object EditProfileKey : NavKey
