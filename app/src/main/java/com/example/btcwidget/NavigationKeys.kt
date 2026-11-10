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

/** A private conversation; [peers] are pubkeys joined with commas. */
@Serializable data class ChatKey(val peers: String) : NavKey

/** Whom [pubkey] follows, or ([followers]) who follows them. */
@Serializable data class FollowsKey(val pubkey: String, val followers: Boolean) : NavKey

/** Who reposted, liked or zapped a note; [tab] 0 = reposts, 1 = likes, 2 = zaps. */
@Serializable data class EngagementKey(val noteId: String, val tab: Int) : NavKey

/** The community rules. */
@Serializable data object RulesKey : NavKey

/** Search for people, notes and hashtags. */
@Serializable data object SearchKey : NavKey

/** Editing the user's own profile. */
@Serializable data object EditProfileKey : NavKey

/** A long-form article (NIP-23), by its naddr. */
@Serializable data class ArticleKey(val naddr: String) : NavKey

/** Account, notification, media server and inbox settings for Nostr. */
@Serializable data object NostrSettingsKey : NavKey

/** A NIP-72 community, by its coordinate "34550:<owner>:<id>". */
@Serializable data class CommunityKey(val coordinate: String) : NavKey

/** A NIP-28 public chat channel, by its creation event id. */
@Serializable data class ChannelKey(val id: String) : NavKey

/** A NIP-29 group, as "host'id". */
@Serializable data class GroupKey(val address: String) : NavKey
