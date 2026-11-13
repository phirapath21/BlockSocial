package com.example.btcwidget.ui.social

import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.btcwidget.nostr.FeedItem
import com.example.btcwidget.nostr.NostrNotification
import com.example.btcwidget.nostr.NostrRepository
import com.example.btcwidget.nostr.SignerRejected
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Profile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A paged list with its loading state. */
data class Paged<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val endReached: Boolean = false
)

/**
 * State and actions for the Nostr screens. Data comes from [NostrRepository]; each
 * action reports failures as a short message on [messages] (shown as a toast).
 */
class SocialViewModel(application: Application) : AndroidViewModel(application) {
    val repo = NostrRepository.get(application)

    val account = repo.account
    val profiles = repo.profiles
    val stats = repo.stats
    val following = repo.following
    val muted = repo.muted

    private val _home = MutableStateFlow(Paged<FeedItem>())
    val home: StateFlow<Paged<FeedItem>> = _home.asStateFlow()

    private val _notifications = MutableStateFlow(Paged<NostrNotification>())
    val notifications: StateFlow<Paged<NostrNotification>> = _notifications.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var accountLoaded = false

    // Scroll positions of the main lists. Kept here, not in the screen, so switching tabs,
    // opening a note and coming back, or the Activity being recreated leaves you where you were.
    private val listStates = HashMap<String, androidx.compose.foundation.lazy.LazyListState>()
    fun listState(key: String): androidx.compose.foundation.lazy.LazyListState =
        listStates.getOrPut(key) { androidx.compose.foundation.lazy.LazyListState() }
    /** A different account's lists start from the top. */
    private fun forgetScroll() = listStates.clear()

    // ---- Home feeds (Primal-style menu) ----

    private val _homeFeeds = MutableStateFlow(loadFeeds())
    /** The feeds in the Home menu, in order. */
    val homeFeeds: StateFlow<List<com.example.btcwidget.nostr.core.HomeFeed>> = _homeFeeds.asStateFlow()
    private val _homeFeed = MutableStateFlow(loadFeed(_homeFeeds.value))
    /** The feed Home shows. */
    val homeFeed: StateFlow<com.example.btcwidget.nostr.core.HomeFeed> = _homeFeed.asStateFlow()

    private fun loadFeeds() = com.example.btcwidget.nostr.NostrAccountStore.homeFeeds(getApplication())?.mapNotNull { com.example.btcwidget.nostr.core.HomeFeed.parse(it) }?.ifEmpty { null }
        ?: com.example.btcwidget.nostr.core.HomeFeed.DEFAULTS

    private fun loadFeed(feeds: List<com.example.btcwidget.nostr.core.HomeFeed>) =
        com.example.btcwidget.nostr.NostrAccountStore.homeFeed(getApplication())?.let { com.example.btcwidget.nostr.core.HomeFeed.parse(it) }?.takeIf { it in feeds } ?: feeds.first()

    /** Switches Home to [feed] and loads it. */
    fun selectFeed(feed: com.example.btcwidget.nostr.core.HomeFeed) {
        if (feed == _homeFeed.value) return
        _homeFeed.value = feed
        listStates.remove("home:" + feed.id) // a freshly loaded feed starts at the top
        store.setHomeFeed(getApplication(), feed.id)
        stopLive?.invoke(); stopLive = null
        _newPosts.value = emptyList()
        _home.value = Paged(loading = true)
        refreshHome()
    }

    private fun saveFeeds(list: List<com.example.btcwidget.nostr.core.HomeFeed>) {
        _homeFeeds.value = list
        store.setHomeFeeds(getApplication(), list.map { it.id })
    }

    fun addFeed(feed: com.example.btcwidget.nostr.core.HomeFeed, select: Boolean = true) {
        if (feed !in _homeFeeds.value) saveFeeds(_homeFeeds.value + feed)
        if (select) selectFeed(feed)
    }

    /**
     * People who post about [tags] lately (for onboarding): authors of recent notes with
     * those hashtags, most active first. Real accounts from relays; nothing hand-picked.
     */
    suspend fun topicPeople(tags: List<String>): List<Profile> = runCatching {
        val me = repo.pubkey
        // One quick query for recent notes with any of the topics (not a full hashtag search each).
        val ranked = repo.topicAuthors(tags.take(6)).filter { it != me && it !in repo.muted.value }.take(30)
        repo.loadProfiles(ranked)
        ranked.mapNotNull { repo.profiles.value[it] }.take(20)
    }.getOrDefault(emptyList())

    /** Well-known Bitcoin and Nostr people (each shown only if their profile matches who we expect). */
    suspend fun starterPeople(): List<Profile> = runCatching {
        val known = com.example.btcwidget.nostr.core.StarterPeople.LIST
        repo.loadProfiles(known.map { it.pubkey })
        val profiles = repo.profiles.value
        known.mapNotNull { k -> profiles[k.pubkey]?.takeIf { p -> k.matches(p) } }
    }.getOrDefault(emptyList())

    /** Saves the new account's profile, showing its progress; [onDone] gets whether it was saved. */
    fun saveProfile(name: String, about: String, picture: String?, onDone: (Boolean) -> Unit) {
        val me = repo.pubkey ?: return onDone(false)
        var ok = false
        tracked(tr("Saving your profile"), tr("Profile saved"), tr("Couldn't save your profile"), finally = { onDone(ok) }) {
            val current = repo.profiles.value[me] ?: Profile(me)
            repo.updateProfile(current.copy(name = name.trim().ifEmpty { current.name }, about = about.trim().ifEmpty { current.about }, picture = picture ?: current.picture))
            ok = true
        }
    }

    fun removeFeed(feed: com.example.btcwidget.nostr.core.HomeFeed) {
        val next = _homeFeeds.value - feed
        if (next.isEmpty()) return
        saveFeeds(next)
        if (_homeFeed.value == feed) selectFeed(next.first())
    }

    fun moveFeed(feed: com.example.btcwidget.nostr.core.HomeFeed, by: Int) {
        val list = _homeFeeds.value.toMutableList()
        val i = list.indexOf(feed)
        val j = (i + by).coerceIn(0, list.lastIndex)
        if (i < 0 || i == j) return
        list.removeAt(i); list.add(j, feed)
        saveFeeds(list)
    }

    fun resetFeeds() {
        saveFeeds(com.example.btcwidget.nostr.core.HomeFeed.DEFAULTS)
        if (_homeFeed.value !in _homeFeeds.value) selectFeed(_homeFeeds.value.first())
    }

    private val followingFeed get() = _homeFeed.value is com.example.btcwidget.nostr.core.HomeFeed.Following

    /** Loads the account (follows, relays) once, then the home feed. */
    fun start() {
        if (repo.pubkey == null) return
        if (_home.value.items.isEmpty() && !_home.value.loading) {
            viewModelScope.launch {
                // Last session's timeline first, then fresh notes replace it.
                val cached = if (followingFeed) repo.cachedHome() else emptyList()
                if (cached.isNotEmpty() && _home.value.items.isEmpty()) _home.value = Paged(cached)
                refreshHome()
            }
        }
    }

    fun refreshHome() {
        if (repo.pubkey == null) return
        _home.update { it.copy(refreshing = it.items.isNotEmpty(), loading = it.items.isEmpty(), error = null) }
        viewModelScope.launch {
            try {
                if (!accountLoaded) {
                    repo.cacheReady.join()
                    val load = async { repo.loadAccount() }
                    // With follows from the cache, fetch the feed now and refresh the lists
                    // alongside; on the very first run the follows are needed first.
                    if (!repo.hasCachedAccount) load.await()
                    accountLoaded = true
                }
                val feed = _homeFeed.value
                val items = repo.feed(feed)
                if (feed != _homeFeed.value) return@launch // switched meanwhile
                _home.value = Paged(items, endReached = items.isEmpty() || feed is com.example.btcwidget.nostr.core.HomeFeed.Dvm)
                if (feed is com.example.btcwidget.nostr.core.HomeFeed.Following) {
                    startLiveHome()
                    if (items.isNotEmpty()) repo.saveHome(items)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _home.update { it.copy(loading = false, refreshing = false, error = e.message ?: "Couldn't load the feed") }
            }
        }
    }

    fun loadMoreHome() {
        val state = _home.value
        if (state.loading || state.endReached || state.items.isEmpty()) return
        _home.update { it.copy(loading = true) }
        viewModelScope.launch {
            val until = state.items.minOf { it.sortTime } - 1
            val feed = _homeFeed.value
            val more = runCatching { repo.feed(feed, until = until) }.getOrDefault(emptyList())
            if (feed != _homeFeed.value) return@launch
            _home.update { s ->
                val merged = (s.items + more).distinctBy { it.key }
                s.copy(items = merged, loading = false, endReached = more.isEmpty())
            }
        }
    }

    fun refreshNotifications() {
        if (repo.pubkey == null) return
        _notifications.update { it.copy(loading = it.items.isEmpty(), refreshing = it.items.isNotEmpty(), error = null) }
        viewModelScope.launch {
            try {
                _notifications.value = Paged(repo.notifications(), endReached = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _notifications.update { it.copy(loading = false, refreshing = false, error = e.message) }
            }
        }
    }

    // ---- Account ----

    fun loginWithAmber(onDone: () -> Unit) = act(tr("Couldn't connect to the signer")) {
        repo.loginWithAmber()
        afterLogin(); onDone()
    }

    /** Logs in with a bunker:// link; [onResult] gets an error message, or null on success. */
    fun loginWithBunker(uri: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val error = try {
                repo.loginWithBunker(uri); afterLogin(); null
            } catch (e: CancellationException) {
                throw e
            } catch (e: SignerRejected) {
                "The signer declined"
            } catch (e: Exception) {
                Log.d("SocialViewModel", "bunker login failed", e)
                e.message ?: "Couldn't connect to the signer"
            }
            onResult(error)
        }
    }

    /** Returns an error message, or null on success. */
    fun loginWithSecret(input: String): String? = try {
        repo.loginWithSecret(input)
        afterLogin()
        null
    } catch (e: IllegalArgumentException) {
        e.message ?: "That isn't a valid nsec"
    } catch (e: Exception) {
        // Keystore or crypto failure on this device.
        Log.w("SocialViewModel", "login failed", e)
        "Couldn't store the key on this phone: ${e.javaClass.simpleName}"
    } catch (e: LinkageError) {
        "This phone can't load the signing library"
    }

    /** Creates a new key; returns the nsec to show once, or null (with a message) on failure. */
    fun createAccount(name: String): String? {
        val nsec = try {
            repo.createAccount()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Log.w("SocialViewModel", "create account failed", e)
            say(tr("Couldn't create the account on this phone: {0}", e.javaClass.simpleName))
            return null
        }
        afterLogin()
        val me = repo.pubkey
        if (name.isNotBlank() && me != null) act(tr("Couldn't publish your profile")) {
            repo.updateProfile(Profile(me, name = name.trim()))
        }
        return nsec
    }

    // ---- Unread state and phone notifications ----

    private val store = com.example.btcwidget.nostr.NostrAccountStore
    private val app get() = getApplication<Application>()

    private val _notifSeen = MutableStateFlow(store.notificationsSeen(application))
    private val _dmRead = MutableStateFlow(store.dmRead(application))
    private val _push = MutableStateFlow(store.pushEnabled(application))

    /** Phone notifications for replies, zaps and messages while the app is closed. */
    val pushEnabled: StateFlow<Boolean> = _push.asStateFlow()

    // ---- Account settings ----

    private val _notifOff = MutableStateFlow(store.notificationsOff(application))
    /** Phone notification types turned off ("reply", "mention", "repost", "like", "zap", "dm"). */
    val notificationsOff: StateFlow<Set<String>> = _notifOff.asStateFlow()

    fun setNotificationType(type: String, on: Boolean) {
        val next = if (on) _notifOff.value - type else _notifOff.value + type
        store.setNotificationsOff(getApplication(), next)
        _notifOff.value = next
    }

    /** The nsec of an account whose key is on this phone; null for signer-app accounts. */
    fun exportNsec(): String? = store.secretKey(getApplication())
        ?.takeIf { store.account(getApplication())?.type == com.example.btcwidget.nostr.SignerType.LOCAL }
        ?.let { com.example.btcwidget.nostr.core.Nip19.nsec(it) }

    // ---- Safety: muted words, hashtags, threads; web of trust ----

    val mutes get() = repo.mutes

    fun muteEntry(type: String, value: String, mute: Boolean) =
        act(tr("Couldn't update mutes")) { repo.setMuteEntry(type, value, mute); if (mute) say(tr("Muted")) }

    fun unmuteAllThreads() = act(tr("Couldn't update mutes")) { repo.unmuteAllThreads() }

    /** Mutes the conversation [note] belongs to (its root note). */
    fun muteThread(note: Event) = muteEntry("e", com.example.btcwidget.nostr.core.ThreadRef.of(note).root ?: note.id, true)

    private val _wot = MutableStateFlow(store.wotFilter(application))
    val wotFilter: StateFlow<Boolean> = _wot.asStateFlow()
    val trustSize get() = repo.trustSize

    fun setWotFilter(on: Boolean) {
        repo.setWotFilter(on)
        _wot.value = on
        if (on) act(tr("Couldn't load your network")) { repo.loadTrust(); say(tr("Showing replies and mentions from your network only")) }
    }

    // ---- Communities, public chats, groups ----

    val spaces get() = repo.spaces

    suspend fun communities() = runCatching { repo.spaces.communities() }.getOrDefault(emptyList())
    suspend fun community(coordinate: String) = runCatching { repo.spaces.community(coordinate) }.getOrNull()
    suspend fun communityPosts(c: com.example.btcwidget.nostr.core.Communities.Community, includePending: Boolean, until: Long? = null) =
        runCatching { repo.spaces.posts(c, includePending, until) }.getOrDefault(emptyList())

    fun postToCommunity(c: com.example.btcwidget.nostr.core.Communities.Community, text: String, onResult: (Boolean) -> Unit) {
        var ok = false
        act(tr("Couldn't post"), finally = { onResult(ok) }) { repo.spaces.post(c, text); ok = true; say(tr("Posted. It shows here once a moderator approves it.")) }
    }

    fun setCommunityJoined(c: com.example.btcwidget.nostr.core.Communities.Community, join: Boolean) =
        act(if (join) tr("Couldn't join") else tr("Couldn't leave")) { repo.spaces.setJoined(c, join) }

    suspend fun channels() = runCatching { repo.spaces.channels() }.getOrDefault(emptyList())
    suspend fun channel(id: String) = runCatching { repo.spaces.channel(id) }.getOrNull()

    fun sendToChannel(channel: com.example.btcwidget.nostr.core.PublicChat.Channel, text: String, onResult: (Boolean) -> Unit) {
        var ok = false
        act(tr("Couldn't send"), finally = { onResult(ok) }) { repo.spaces.sendToChannel(channel, text); ok = true }
    }

    suspend fun liveStreams() = runCatching { repo.spaces.liveStreams() }.getOrDefault(emptyList())
    suspend fun liveStream(address: String) = runCatching { repo.spaces.liveStream(address) }.getOrNull()
    fun sendLiveChat(live: com.example.btcwidget.nostr.core.LiveStreams.Live, text: String, onResult: (Boolean) -> Unit) {
        var ok = false
        act(tr("Couldn't send"), finally = { onResult(ok) }) { repo.spaces.sendLiveChat(live, text); ok = true }
    }

    suspend fun group(address: com.example.btcwidget.nostr.core.RelayGroups.Address) = runCatching { repo.spaces.group(address) }.getOrNull()

    /** [onResult] gets null when sent, or the relay's reason. */
    fun sendToGroup(address: com.example.btcwidget.nostr.core.RelayGroups.Address, text: String, onResult: (String?) -> Unit) {
        var error: String? = "Couldn't send"
        act(tr("Couldn't send"), finally = { onResult(error) }) { error = repo.spaces.sendToGroup(address, text) }
    }

    fun joinGroup(address: com.example.btcwidget.nostr.core.RelayGroups.Address) =
        act(tr("Couldn't ask to join")) { val e = repo.spaces.joinGroup(address); say(e ?: tr("Asked to join")) }

    suspend fun feedServices() = runCatching { repo.feedServices() }.getOrDefault(emptyList())
    /** The feed, or the reason it couldn't load. */
    suspend fun dvmFeed(service: com.example.btcwidget.nostr.core.Dvm.Service): Result<List<FeedItem>> = runCatching { repo.dvmFeed(service) }

    // ---- Video autoplay (a device setting, in AppPrefs) ----

    private val _videoAutoplay = MutableStateFlow(VideoAutoplay.fromName(com.example.btcwidget.AppPrefs.videoAutoplay(application)))
    val videoAutoplay: StateFlow<VideoAutoplay> = _videoAutoplay.asStateFlow()

    private val _videoQuality = MutableStateFlow(VideoQuality.fromName(com.example.btcwidget.AppPrefs.videoQuality(application)))
    val videoQuality: StateFlow<VideoQuality> = _videoQuality.asStateFlow()

    fun setVideoQuality(q: VideoQuality) {
        com.example.btcwidget.AppPrefs.setVideoQuality(getApplication(), q.name)
        _videoQuality.value = q
    }

    private val _linksInApp = MutableStateFlow(com.example.btcwidget.AppPrefs.linksInApp(application))
    /** Web links open in Block's browser (true) or the phone's. */
    val linksInApp: StateFlow<Boolean> = _linksInApp.asStateFlow()

    private val _privateLists = MutableStateFlow(com.example.btcwidget.AppPrefs.privateLists(application))
    /** New bookmarks and mutes are private (encrypted to you) or public. */
    val privateLists: StateFlow<Boolean> = _privateLists.asStateFlow()
    fun setPrivateLists(private: Boolean) {
        com.example.btcwidget.AppPrefs.setPrivateLists(getApplication(), private)
        _privateLists.value = private
    }

    fun setLinksInApp(inApp: Boolean) {
        com.example.btcwidget.AppPrefs.setLinksInApp(getApplication(), inApp)
        _linksInApp.value = inApp
    }

    fun setVideoAutoplay(mode: VideoAutoplay) {
        com.example.btcwidget.AppPrefs.setVideoAutoplay(getApplication(), mode.name)
        _videoAutoplay.value = mode
    }

    suspend fun followSuggestions() = runCatching { repo.followSuggestions() }.getOrDefault(emptyList())
    suspend fun badgesOf(pubkey: String) = runCatching { repo.badgesOf(pubkey) }.getOrDefault(emptyList())

    fun mediaServers(): List<String> = repo.mediaServerUrls()
    fun setMediaServers(urls: List<String>, onDone: () -> Unit) =
        act(tr("Couldn't save media servers")) { repo.setMediaServers(urls); say(tr("Media servers saved")); onDone() }

    fun setDmInbox(urls: List<String>, onDone: () -> Unit) =
        act(tr("Couldn't save inbox relays")) { repo.dms.setUpInbox(urls); say(tr("Inbox relays saved")); onDone() }

    /** Notifications newer than the last time the tab was open. */
    val unreadNotifications: StateFlow<Int> = combine(_notifications, _notifSeen) { n, seen ->
        n.items.count { it.event.createdAt > seen }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Conversations whose newest message is from someone else and not read yet. */
    val unreadConversations: StateFlow<Set<String>> = combine(repo.dms.conversations, _dmRead, repo.account) { convs, read, account ->
        val me = account?.pubkey
        convs.filter { it.last.sender != me && it.last.createdAt > (read[it.key] ?: 0) }.map { it.key }.toSet()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun markNotificationsSeen() {
        val newest = _notifications.value.items.maxOfOrNull { it.event.createdAt } ?: return
        if (newest <= _notifSeen.value) return
        _notifSeen.value = newest
        store.setNotificationsSeen(app, newest)
        // Seen in the app: don't announce them again from the background.
        if (newest > store.notificationsNotified(app)) store.setNotificationsNotified(app, newest)
    }

    fun markConversationRead(key: String, newest: Long) {
        if (newest <= (_dmRead.value[key] ?: 0)) return
        _dmRead.update { it + (key to newest) }
        store.setDmRead(app, _dmRead.value)
        if (newest > store.dmNotified(app)) store.setDmNotified(app, newest)
    }

    fun setPushEnabled(on: Boolean) {
        store.setPushEnabled(app, on)
        _push.value = on
        if (on) com.example.btcwidget.WidgetRefreshScheduler.schedulePeriodic(app)
        else com.example.btcwidget.WidgetRefreshScheduler.cancelIfUnused(app)
    }

    /** Loads what the badges need when the app opens (and on resume). */
    fun refreshBadges() {
        if (repo.pubkey == null) return
        startDms()
        refreshNotifications()
    }

    /** Re-reads per-account state after logging in or out (the store is cleared then). */
    private fun reloadReadState() {
        _notifSeen.value = store.notificationsSeen(app)
        _dmRead.value = store.dmRead(app)
        _push.value = store.pushEnabled(app)
        _notifOff.value = store.notificationsOff(app)
        _wot.value = store.wotFilter(app)
        refreshScheduled()
        _homeFeeds.value = loadFeeds()
        _homeFeed.value = loadFeed(_homeFeeds.value)
    }

    private fun afterLogin() {
        // Another account may have been in use (Add account, Create account): nothing of its
        // feeds, live audio or scroll positions carries over.
        forgetScroll()
        stopLive?.invoke(); stopLive = null
        _newPosts.value = emptyList()
        reloadReadState()
        accountLoaded = false
        _home.value = Paged()
        _notifications.value = Paged()
        refreshHome()
    }

    // ---- Multiple accounts ----

    val savedAccounts get() = repo.savedAccounts

    fun switchAccount(pubkey: String) {
        forgetScroll()
        try {
            stopLive?.invoke(); stopLive = null
            _newPosts.value = emptyList()
            repo.switchAccount(pubkey)
            afterLogin()
            repo.pubkey?.let { me -> viewModelScope.launch { runCatching { repo.refreshProfile(me) } } }
            say(tr("Switched account"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("SocialViewModel", "switch failed", e)
            say(tr("Couldn't switch account"))
        }
    }

    fun removeSavedAccount(pubkey: String) = repo.removeSavedAccount(pubkey)

    fun logout() {
        forgetScroll()
        repo.logout()
        reloadReadState()
        stopLive?.invoke(); stopLive = null
        _newPosts.value = emptyList()
        _home.value = Paged()
        _notifications.value = Paged()
    }

    fun setRelays(urls: List<String>?) {
        repo.setRelays(urls)
        refreshHome()
    }

    // ---- Reading for detail screens ----

    suspend fun thread(id: String) = runCatching { repo.thread(id) }.getOrNull()
    suspend fun note(id: String) = runCatching { repo.note(id) }.getOrNull()
    fun hashtagSuggestions(prefix: String) = repo.hashtagSuggestions(prefix)
    suspend fun article(address: com.example.btcwidget.nostr.core.Nip19.Address) = runCatching { repo.article(address) }.getOrNull()
    suspend fun articlesBy(pubkey: String) = runCatching { repo.articlesBy(pubkey) }.getOrDefault(emptyList())
    /** Video posts for Shorts: by the people you follow, or by everyone on your relays. */
    suspend fun videoFeed(everyone: Boolean, until: Long? = null) =
        runCatching { repo.mediaFeed(com.example.btcwidget.nostr.core.HomeFeed.Media(videos = true, everyone = everyone), until) }.getOrDefault(emptyList())

    suspend fun profileFeed(pubkey: String, until: Long? = null) = runCatching { repo.profileFeed(pubkey, until) }.getOrDefault(emptyList())
    suspend fun hashtagFeed(tag: String) = runCatching { repo.hashtagFeed(tag) }.getOrDefault(emptyList())
    suspend fun trendingHashtags() = runCatching { repo.trendingHashtags() }.getOrDefault(emptyList())

    // The side panel (wide screens) is on every page: ask the relays now and then, not on each one.
    private var trendsCache: Pair<Long, List<com.example.btcwidget.nostr.NostrRepository.Trend>>? = null
    private var livesCache: Pair<Long, List<com.example.btcwidget.nostr.core.LiveStreams.Live>>? = null
    suspend fun trendingCached(): List<com.example.btcwidget.nostr.NostrRepository.Trend> {
        trendsCache?.let { (at, l) -> if (System.currentTimeMillis() - at < 10 * 60_000 && l.isNotEmpty()) return l }
        return trendingHashtags().also { trendsCache = System.currentTimeMillis() to it }
    }
    suspend fun liveStreamsCached(): List<com.example.btcwidget.nostr.core.LiveStreams.Live> {
        livesCache?.let { (at, l) -> if (System.currentTimeMillis() - at < 2 * 60_000) return l }
        return liveStreams().also { livesCache = System.currentTimeMillis() to it }
    }
    suspend fun refreshProfile(pubkey: String) = runCatching { repo.refreshProfile(pubkey) }.getOrNull()
    suspend fun followingOf(pubkey: String) = runCatching { repo.followingOf(pubkey) }.getOrDefault(emptySet())
    fun loadProfiles(pubkeys: Collection<String>) = viewModelScope.launch { runCatching { repo.loadProfiles(pubkeys) } }

    // ---- Images ----

    /** An uploaded image waiting to be posted. */
    data class Attachment(val url: String, val mime: String, val sha256: String, val width: Int, val height: Int, val blurhash: String? = null) {
        val isVideo get() = mime.startsWith("video/")
    }

    /** Uploads a picked video as-is (up to 100 MB). */
    fun uploadVideo(uri: android.net.Uri, onResult: (Attachment?) -> Unit) {
        var result: Attachment? = null
        act(tr("Couldn't upload the video"), finally = { onResult(result) }) {
            val prepared = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.example.btcwidget.nostr.VideoPrep.prepare(getApplication(), uri)
            }
            val blob = repo.uploadVideo(prepared)
            result = Attachment(blob.url, prepared.mime, blob.sha256, prepared.width, prepared.height)
        }
    }

    /** Prepares (strips metadata) and uploads a picked image; [onResult] gets it, or null on failure. */
    fun uploadImage(uri: android.net.Uri, onResult: (Attachment?) -> Unit) {
        var result: Attachment? = null
        act(tr("Couldn't upload the image"), finally = { onResult(result) }) {
            val prepared = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                com.example.btcwidget.nostr.ImagePrep.prepare(getApplication(), uri)
            }
            val blob = repo.uploadImage(prepared)
            result = Attachment(blob.url, prepared.mime, blob.sha256, prepared.width, prepared.height, prepared.blurhash)
        }
    }

    // ---- Direct messages (NIP-17) ----

    val dmConversations get() = repo.dms.conversations
    val dmMessages get() = repo.dms.messages
    val dmLoading get() = repo.dms.loading
    val dmLocked get() = repo.dms.locked
    val dmInbox get() = repo.dms.inbox

    fun startDms() { if (repo.pubkey != null) viewModelScope.launch { runCatching { repo.dms.start() } } }

    /** Sends a private message; [onResult] gets whether it was delivered. */
    fun sendDm(peers: List<String>, text: String, replyTo: String? = null, onResult: (Boolean) -> Unit) {
        var ok = false
        act(tr("Couldn't send the message"), finally = { onResult(ok) }) { repo.dms.send(peers, text, replyTo); ok = true }
    }

    fun reactDm(peers: List<String>, target: com.example.btcwidget.nostr.core.Nip17.Message, emoji: String) =
        act(tr("Couldn't react")) { repo.dms.react(peers, target, emoji) }

    /** Sends a photo, encrypted on the phone (location data removed first). */
    fun sendDmImage(peers: List<String>, uri: android.net.Uri, onResult: (Boolean) -> Unit) {
        var ok = false
        act(tr("Couldn't send the photo"), finally = { onResult(ok) }) {
            val prepared = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                com.example.btcwidget.nostr.ImagePrep.prepare(getApplication(), uri)
            }
            repo.dms.sendImage(peers, prepared)
            ok = true
        }
    }

    suspend fun openDmFile(info: com.example.btcwidget.nostr.core.DmFile.Info): ByteArray? =
        runCatching { repo.dms.openFile(info) }.getOrNull()

    fun unlockDms() = act(tr("Couldn't open messages")) { repo.dms.unlock() }
    fun setUpDmInbox() = act(tr("Couldn't set up your inbox")) { repo.dms.setUpInbox(); say(tr("Inbox ready: people can message you")) }

    suspend fun linkPreview(url: String) = repo.linkPreview(url)

    suspend fun engagement(noteId: String) = runCatching { repo.engagement(noteId) }.getOrNull()

    // ---- Follow lists ----

    suspend fun followersOf(pubkey: String) = runCatching { repo.followersOf(pubkey) }.getOrDefault(emptyList())

    suspend fun globalFeed(relay: String?, until: Long? = null) = runCatching { repo.globalFeed(relay, until) }.getOrDefault(emptyList())
    val relays: List<String> get() = repo.pool.urls.toList().sorted()

    // ---- Search ----

    suspend fun resolve(query: String) = runCatching { repo.resolve(query) }.getOrNull()
    suspend fun searchArticles(query: String) = runCatching { repo.searchArticles(query) }.getOrDefault(emptyList())
    suspend fun recentArticles(following: Boolean, until: Long? = null) = runCatching { repo.recentArticles(following, until) }.getOrDefault(emptyList())
    suspend fun searchPeople(query: String) = runCatching { repo.searchPeople(query) }.getOrDefault(emptyList())
    suspend fun searchNotes(query: String) = runCatching { repo.searchNotes(query) }.getOrDefault(emptyList())

    // ---- Actions ----

    fun like(note: Event) = act(tr("Couldn't like")) { repo.like(note) }
    fun react(note: Event, content: String, emojiUrl: String?) = act(tr("Couldn't react")) { repo.react(note, content, emojiUrl) }
    val emojis get() = repo.emojis
    fun repost(note: Event) = tracked(tr("Reposting"), tr("Reposted"), tr("Couldn't repost")) { repo.repost(note) }
    fun follow(pubkey: String, follow: Boolean) = act(if (follow) tr("Couldn't follow") else tr("Couldn't unfollow")) { repo.setFollowing(pubkey, follow) }
    fun mute(pubkey: String, mute: Boolean) = act(tr("Couldn't update mutes")) {
        repo.setMuted(pubkey, mute)
        if (mute) _home.update { s -> s.copy(items = s.items.filter { it.note.pubkey != pubkey && it.repostedBy != pubkey }) }
        say(if (mute) tr("Muted") else tr("Unmuted"))
    }
    fun report(note: Event, reason: String) = act(tr("Couldn't send the report")) { repo.report(note, reason); say(tr("Reported")) }
    fun delete(note: Event) = act(tr("Couldn't delete")) {
        repo.delete(note)
        _home.update { s -> s.copy(items = s.items.filter { it.note.id != note.id }) }
        say(tr("Deletion requested"))
    }
    fun updateProfile(profile: Profile, onDone: () -> Unit) = act(tr("Couldn't save your profile")) { repo.updateProfile(profile); onDone() }

    /** Posts a note, reply or quote with any uploaded images; [onResult] gets whether it was published. */
    fun send(
        text: String,
        replyTo: Event?,
        quote: Event?,
        images: List<Attachment> = emptyList(),
        contentWarning: String? = null,
        onResult: (Boolean) -> Unit
    ) {
        var ok = false
        val (working, done) = when {
            replyTo != null -> "Replying" to "Replied"
            quote != null -> "Quoting" to "Quoted"
            else -> "Posting" to "Posted"
        }
        tracked(working, done, tr("Couldn't post"), finally = { onResult(ok) }) {
            // Image URLs go in the text (so every client shows them) with imeta tags (NIP-92).
            val body = (listOf(text.trim()) + images.map { it.url }).filter { it.isNotEmpty() }.joinToString("\n")
            val tags = images.map { com.example.btcwidget.nostr.core.Imeta.tag(it.url, it.mime, it.sha256, it.width, it.height, it.blurhash) } +
                // NIP-36: other apps hide the note behind a warning until tapped.
                listOfNotNull(contentWarning?.let { listOf("content-warning", it) })
            val event = when {
                replyTo != null -> repo.reply(body, replyTo, tags)
                quote != null -> repo.quote(body, quote, tags)
                else -> repo.post(body, tags)
            }
            if (replyTo == null) _home.update { it.copy(items = listOf(FeedItem(event)) + it.items) }
            ok = true
        }
    }

    // ---- Scheduled posts ----

    private val _scheduled = MutableStateFlow<List<com.example.btcwidget.nostr.ScheduledPosts.Post>>(emptyList())
    /** The active account's posts waiting to be published. */
    val scheduled: StateFlow<List<com.example.btcwidget.nostr.ScheduledPosts.Post>> = _scheduled.asStateFlow()

    fun refreshScheduled() {
        _scheduled.value = repo.pubkey?.let { com.example.btcwidget.nostr.ScheduledPosts.forAccount(getApplication(), it) }.orEmpty()
    }

    /** Saves a new post to publish at [at] (seconds), with the same tags [send] would add. */
    fun schedule(text: String, images: List<Attachment>, contentWarning: String?, at: Long): Boolean {
        val me = repo.pubkey ?: return false
        val body = (listOf(text.trim()) + images.map { it.url }).filter { it.isNotEmpty() }.joinToString("\n")
        val tags = images.map { com.example.btcwidget.nostr.core.Imeta.tag(it.url, it.mime, it.sha256, it.width, it.height, it.blurhash) } +
            listOfNotNull(contentWarning?.let { listOf("content-warning", it) })
        com.example.btcwidget.nostr.ScheduledPosts.schedule(getApplication(), me, at, body, tags)
        refreshScheduled()
        say(tr("Scheduled for ") + java.text.SimpleDateFormat("EEE, MMM d - h:mm a", java.util.Locale.ENGLISH).format(java.util.Date(at * 1000)))
        return true
    }

    fun cancelScheduled(id: String) {
        com.example.btcwidget.nostr.ScheduledPosts.cancel(getApplication(), id)
        refreshScheduled()
    }

    /** Publishes a scheduled post right away (e.g. one the signer couldn't approve in the background). */
    fun postScheduledNow(post: com.example.btcwidget.nostr.ScheduledPosts.Post) = act(tr("Couldn't post")) {
        val event = repo.publish(com.example.btcwidget.nostr.core.Events.note(post.pubkey, post.content, post.tags))
        com.example.btcwidget.nostr.ScheduledPosts.cancel(getApplication(), post.id)
        _home.update { it.copy(items = listOf(FeedItem(event)) + it.items) }
        refreshScheduled()
        say(tr("Posted"))
    }

    // ---- New posts while reading (the "↑ N new posts" pill) ----

    private val _newPosts = MutableStateFlow<List<FeedItem>>(emptyList())
    val newPosts: StateFlow<List<FeedItem>> = _newPosts.asStateFlow()
    private var stopLive: (() -> Unit)? = null

    private fun startLiveHome() {
        stopLive?.invoke()
        val authors = repo.homeAuthors()
        if (authors.isEmpty()) return
        val newest = _home.value.items.maxOfOrNull { it.sortTime } ?: (System.currentTimeMillis() / 1000)
        _newPosts.value = emptyList()
        stopLive = repo.liveFeed(authors, newest) { item ->
            val shown = _home.value.items.any { it.key == item.key }
            // At most the newest 300 wait (it kept growing while the app sat on another screen).
            if (!shown) _newPosts.update { pending -> (pending + item).distinctBy { it.key }.takeLast(300) }
        }
    }

    /** Puts the waiting new posts at the top of the timeline. */
    fun showNewPosts() {
        val fresh = _newPosts.value
        if (fresh.isEmpty()) return
        _newPosts.value = emptyList()
        _home.update { s -> s.copy(items = (fresh + s.items).distinctBy { it.key }.sortedByDescending { it.sortTime }) }
        repo.saveHome(_home.value.items)
    }

    override fun onCleared() {
        stopLive?.invoke()
        super.onCleared()
    }

    // ---- Bookmarks ----

    val bookmarks get() = repo.bookmarks

    fun toggleBookmark(note: Event) {
        val on = note.id !in repo.bookmarks.value
        act(if (on) tr("Couldn't bookmark") else tr("Couldn't remove the bookmark")) {
            repo.setBookmarked(note, on)
            say(if (on) tr("Bookmarked") else tr("Bookmark removed"))
        }
    }

    val pinned get() = repo.pinned

    // ---- Lists of people (NIP-51) ----

    suspend fun followPacks() = runCatching { repo.followPacks() }.getOrDefault(emptyList())
    suspend fun followPack(address: String) = runCatching { repo.followPack(address) }.getOrNull()
    fun followAll(pubkeys: Collection<String>) = act(tr("Couldn't follow them")) {
        repo.followAll(pubkeys)
        say(tr("Following {0} people", pubkeys.size))
    }

    suspend fun highlightsOf(article: com.example.btcwidget.nostr.core.Article) = runCatching { repo.highlightsOf(article) }.getOrDefault(emptyList())
    fun highlight(article: com.example.btcwidget.nostr.core.Article, text: String, comment: String?, onDone: () -> Unit) =
        tracked(tr("Highlighting"), tr("Highlighted"), tr("Couldn't highlight"), finally = onDone) { repo.highlight(article, text, comment) }

    val followedTags get() = repo.followedTags
    fun followHashtag(tag: String, follow: Boolean) =
        act(if (follow) tr("Couldn't follow #{0}", tag) else tr("Couldn't unfollow #{0}", tag)) { repo.setHashtagFollowed(tag, follow) }

    val lists get() = repo.lists
    fun loadLists() { viewModelScope.launch { repo.pubkey?.let { runCatching { repo.peopleLists(it) } } } }
    suspend fun listsBy(owner: String) = runCatching { repo.peopleLists(owner) }.getOrDefault(emptyList())
    suspend fun peopleList(owner: String, d: String) = runCatching { repo.peopleList(owner, d) }.getOrNull()
    suspend fun listFeed(list: com.example.btcwidget.nostr.core.PeopleList) =
        runCatching { repo.feed(com.example.btcwidget.nostr.core.HomeFeed.People(list.owner, list.d, list.title)) }.getOrDefault(emptyList())

    /** Makes a new list, optionally with someone in it already; [onDone] gets it once saved. */
    fun createList(title: String, firstMember: String? = null, onDone: (com.example.btcwidget.nostr.core.PeopleList) -> Unit = {}) {
        val me = repo.pubkey ?: return
        val name = title.trim().ifBlank { return }
        act(tr("Couldn't create the list")) {
            var list = com.example.btcwidget.nostr.core.PeopleList.create(me, name)
            if (firstMember != null) list = list.with(firstMember)
            val saved = repo.updateList(list)
            say(tr("List created"))
            onDone(saved)
        }
    }

    fun setInList(list: com.example.btcwidget.nostr.core.PeopleList, pubkey: String, inList: Boolean) {
        act(tr("Couldn't update the list")) {
            repo.updateList(list) { if (inList) it.with(pubkey) else it.without(pubkey) }
            say(if (inList) tr("Added to {0}", list.title) else tr("Removed from {0}", list.title))
        }
    }

    fun renameList(list: com.example.btcwidget.nostr.core.PeopleList, title: String, description: String?) {
        val name = title.trim().ifBlank { return }
        act(tr("Couldn't rename the list")) { repo.updateList(list) { it.copy(title = name.take(80), description = description?.trim()?.ifBlank { null }) } }
        // Its Home feed carries the name too.
        val renamed = { f: com.example.btcwidget.nostr.core.HomeFeed ->
            if (f is com.example.btcwidget.nostr.core.HomeFeed.People && f.owner == list.owner && f.d == list.d) f.copy(name = name.take(80)) else f
        }
        if (_homeFeeds.value.any { renamed(it) != it }) {
            saveFeeds(_homeFeeds.value.map(renamed))
            _homeFeed.value = renamed(_homeFeed.value)
            store.setHomeFeed(getApplication(), _homeFeed.value.id)
        }
    }

    fun deleteList(list: com.example.btcwidget.nostr.core.PeopleList) {
        // Out of the Home feed menu too.
        _homeFeeds.value.filter { it is com.example.btcwidget.nostr.core.HomeFeed.People && it.owner == list.owner && it.d == list.d }.forEach { removeFeed(it) }
        act(tr("Couldn't delete the list")) { repo.deleteList(list); say(tr("List deleted")) }
    }

    /** Adds the list to Home's feed menu and switches to it. */
    fun showListOnHome(list: com.example.btcwidget.nostr.core.PeopleList) =
        addFeed(com.example.btcwidget.nostr.core.HomeFeed.People(list.owner, list.d, list.title))

    // ---- Relay lists (NIP-65, search, blocked) and relay info (NIP-11) ----

    val relayEntries get() = repo.relayEntries
    val searchRelays get() = repo.searchRelays
    val blockedRelays get() = repo.blockedRelays
    suspend fun relayInfo(url: String) = repo.relayInfo(url)
    fun saveRelayList(entries: List<com.example.btcwidget.nostr.core.RelayLists.Entry>) =
        tracked(tr("Saving your relays"), tr("Relays saved"), tr("Couldn't save your relays")) { repo.saveRelayList(entries) }
    fun saveSearchRelays(urls: List<String>) =
        tracked(tr("Saving search relays"), tr("Search relays saved"), tr("Couldn't save search relays")) { repo.saveSearchRelays(urls) }
    fun saveBlockedRelays(urls: List<String>) =
        tracked(tr("Saving blocked relays"), tr("Blocked relays saved"), tr("Couldn't save blocked relays")) { repo.saveBlockedRelays(urls) }

    // ---- Relay status ----

    fun relayStatus() = repo.pool.status()
    fun retryRelay(url: String) = repo.pool.retry(url)

    // ---- Polls (NIP-88) ----

    suspend fun pollTally(poll: com.example.btcwidget.nostr.core.Poll) = runCatching { repo.pollTally(poll) }.getOrNull()

    fun vote(poll: com.example.btcwidget.nostr.core.Poll, optionIds: List<String>, onResult: (Boolean) -> Unit) {
        var ok = false
        act(tr("Couldn't vote"), finally = { onResult(ok) }) { repo.vote(poll, optionIds); ok = true }
    }

    fun createPoll(question: String, options: List<String>, multipleChoice: Boolean, durationSeconds: Long?, onResult: (Boolean) -> Unit) {
        var ok = false
        act(tr("Couldn't post the poll"), finally = { onResult(ok) }) {
            val endsAt = durationSeconds?.let { System.currentTimeMillis() / 1000 + it }
            val event = repo.createPoll(question, options, multipleChoice, endsAt)
            _home.update { it.copy(items = listOf(FeedItem(event)) + it.items) }
            ok = true
        }
    }

    fun togglePin(note: Event) {
        val on = note.id !in repo.pinned.value
        act(if (on) tr("Couldn't pin") else tr("Couldn't unpin")) { repo.setPinned(note, on); say(if (on) tr("Pinned to your profile") else tr("Unpinned")) }
    }

    suspend fun pinnedNoteOf(pubkey: String) = runCatching { repo.pinnedNoteOf(pubkey) }.getOrNull()

    suspend fun bookmarkedNotes() = runCatching { repo.bookmarkedNotes() }.getOrDefault(emptyList())

    val nwc get() = repo.nwc

    private val _nwcBalanceMsat = MutableStateFlow<Long?>(null)
    val nwcBalanceMsat: StateFlow<Long?> = _nwcBalanceMsat.asStateFlow()

    private val _nwcTransactions = MutableStateFlow<List<com.example.btcwidget.nostr.core.Nwc.Transaction>>(emptyList())
    val nwcTransactions: StateFlow<List<com.example.btcwidget.nostr.core.Nwc.Transaction>> = _nwcTransactions.asStateFlow()

    private val _nwcLoading = MutableStateFlow(false)
    val nwcLoading: StateFlow<Boolean> = _nwcLoading.asStateFlow()

    /** Refreshes balance and transaction history from the connected NWC wallet. */
    fun refreshWallet() {
        if (repo.nwc.value == null || _nwcLoading.value) return
        _nwcLoading.value = true
        viewModelScope.launch {
            try {
                val bal = async { runCatching { repo.nwcBalance() }.getOrNull() }
                val txs = async { runCatching { repo.nwcTransactions(20) }.getOrNull() }
                bal.await()?.let { _nwcBalanceMsat.value = it }
                txs.await()?.let { _nwcTransactions.value = it }
            } finally {
                _nwcLoading.value = false
            }
        }
    }

    /** Creates an invoice via NWC `make_invoice` and passes the BOLT11 string to [onResult]. */
    fun makeNwcInvoice(amountSat: Long, description: String, onResult: (String?) -> Unit) {
        var inv: String? = null
        act(tr("Couldn't create invoice"), finally = { onResult(inv) }) {
            inv = repo.nwcMakeInvoice(amountSat, description)
        }
    }

    /** Pays a BOLT11 invoice via NWC and refreshes wallet balance/history. */
    fun payInvoiceWithNwc(invoice: String, onDone: (Boolean) -> Unit = {}) {
        var ok = false
        act(tr("Payment failed"), finally = { onDone(ok) }) {
            say(tr("⚡ Paying invoice…"))
            repo.payWithNwc(invoice.trim().removePrefix("lightning:").removePrefix("LIGHTNING:"))
            ok = true
            say(tr("⚡ Payment sent"))
            refreshWallet()
        }
    }

    /**
     * Zaps [amountSat]: with a connected wallet (NWC) it pays right here; otherwise the
     * invoice goes to [onInvoice], which opens a wallet app.
     */
    fun zap(recipient: String, amountSat: Long, note: Event?, comment: String, address: String? = null, onInvoice: (String) -> Unit) =
        act(tr("Couldn't zap")) {
            val invoice = repo.zapInvoice(recipient, amountSat, note, comment, address)
            if (repo.nwc.value != null) {
                say(tr("⚡ Paying {0} sats…", amountSat))
                repo.payWithNwc(invoice)
                say(tr("⚡ Zapped {0} sats", amountSat))
                note?.let { n -> runCatching { repo.loadStats(listOf(n.id)) } }
                refreshWallet()
            } else {
                onInvoice(invoice)
            }
        }

    /** Returns an error message, or null when connected. */
    fun connectNwc(uri: String): String? = try {
        repo.connectNwc(uri); say(tr("Wallet connected")); refreshWallet(); null
    } catch (e: Exception) {
        e.message ?: "Couldn't connect the wallet"
    }

    fun disconnectNwc() {
        repo.disconnectNwc()
        _nwcBalanceMsat.value = null
        _nwcTransactions.value = emptyList()
        say(tr("Wallet disconnected"))
    }

    private fun say(message: String) { _messages.tryEmit(message) }

    // ---- Progress of what's being sent (repost, reply, post) ----

    /** What the status bar at the top shows while something is being published. */
    data class PublishActivity(
        val id: Long,
        val title: String,
        val status: String,
        /** 0..1, or null while waiting with no way to tell how long (signing). */
        val progress: Float?,
        val state: State
    ) {
        enum class State { WORKING, DONE, FAILED }
    }

    private val _activity = MutableStateFlow<PublishActivity?>(null)
    /** The latest publish in progress (or just finished), or null when there's nothing to show. */
    val activity: StateFlow<PublishActivity?> = _activity.asStateFlow()
    private val nextActivity = java.util.concurrent.atomic.AtomicLong()

    /**
     * Runs [block] like [act], showing its progress in the status bar: signing (and with which
     * signer), then relays answering, then [done] with a tick, or [failure] in red.
     */
    private fun tracked(working: String, done: String, failure: String, finally: () -> Unit = {}, block: suspend () -> Unit) {
        val id = nextActivity.incrementAndGet()
        // A newer action replaces an older one in the bar; an older one never covers a newer.
        fun show(a: PublishActivity) = _activity.update { cur -> if (cur == null || cur.id <= id) a else cur }
        fun clearLater(ms: Long) = viewModelScope.launch { delay(ms); _activity.update { if (it?.id == id) null else it } }
        val finishedFlag = java.util.concurrent.atomic.AtomicBoolean(false)
        val tracker = com.example.btcwidget.nostr.PublishTracker { stage ->
            when (stage) {
                is com.example.btcwidget.nostr.PublishTracker.Stage.Signing -> if (!finishedFlag.get()) show(
                    PublishActivity(id, working, when (stage.signer) {
                        com.example.btcwidget.nostr.SignerType.AMBER -> "Waiting for your signer app…"
                        com.example.btcwidget.nostr.SignerType.BUNKER -> "Waiting for your remote signer…"
                        else -> "Signing…"
                    }, null, PublishActivity.State.WORKING)
                )
                is com.example.btcwidget.nostr.PublishTracker.Stage.Sending -> {
                    val total = stage.total.coerceAtLeast(1)
                    if (!finishedFlag.get()) show(PublishActivity(
                        id, working,
                        if (stage.answered == 0) "Sending to ${stage.total} relays…" else "Sending · ${stage.answered} of ${stage.total} relays answered",
                        // Never quite full until it's done: the first acceptance finishes it.
                        (0.1f + 0.85f * stage.answered / total).coerceAtMost(0.95f), PublishActivity.State.WORKING
                    ))
                    // Done already: keep counting the relays that have it while the tick shows.
                    else _activity.update { cur ->
                        if (cur?.id == id && cur.state == PublishActivity.State.DONE)
                            cur.copy(status = "On ${stage.accepted} of ${stage.total} relays") else cur
                    }
                }
            }
        }
        show(PublishActivity(id, working, "Preparing…", null, PublishActivity.State.WORKING))
        viewModelScope.launch {
            try {
                withContext(tracker) { block() }
                finishedFlag.set(true)
                show(PublishActivity(id, "$done ✓", "Sent", 1f, PublishActivity.State.DONE))
                clearLater(1_800)
            } catch (e: CancellationException) {
                _activity.update { if (it?.id == id) null else it }
                throw e
            } catch (e: SignerRejected) {
                finishedFlag.set(true)
                show(PublishActivity(id, failure, "Cancelled in the signer", 1f, PublishActivity.State.FAILED))
                clearLater(3_000)
            } catch (e: Exception) {
                finishedFlag.set(true)
                Log.d("SocialViewModel", failure, e)
                show(PublishActivity(id, failure, e.message ?: "Check your connection and try again.", 1f, PublishActivity.State.FAILED))
                clearLater(4_000)
            } finally {
                finally()
            }
        }
    }

    private fun act(failure: String, finally: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SignerRejected) {
                say(tr("Cancelled in the signer"))
            } catch (e: Exception) {
                Log.d("SocialViewModel", failure, e)
                say(e.message?.let { "$failure: $it" } ?: failure)
            } finally {
                finally()
            }
        }
    }
}
