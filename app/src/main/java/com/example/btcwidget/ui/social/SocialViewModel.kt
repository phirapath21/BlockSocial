package com.example.btcwidget.ui.social

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

    /** Loads the account (follows, relays) once, then the home feed. */
    fun start() {
        if (repo.pubkey == null) return
        if (_home.value.items.isEmpty() && !_home.value.loading) {
            viewModelScope.launch {
                // Last session's timeline first, then fresh notes replace it.
                val cached = repo.cachedHome()
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
                val items = repo.homeFeed()
                _home.value = Paged(items, endReached = items.isEmpty())
                startLiveHome()
                if (items.isNotEmpty()) repo.saveHome(items)
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
            val more = runCatching { repo.homeFeed(until = until) }.getOrDefault(emptyList())
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

    fun loginWithAmber(onDone: () -> Unit) = act("Couldn't connect to the signer") {
        repo.loginWithAmber()
        afterLogin(); onDone()
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
            say("Couldn't create the account on this phone: ${e.javaClass.simpleName}")
            return null
        }
        afterLogin()
        val me = repo.pubkey
        if (name.isNotBlank() && me != null) act("Couldn't publish your profile") {
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
    }

    private fun afterLogin() {
        reloadReadState()
        accountLoaded = false
        _home.value = Paged()
        _notifications.value = Paged()
        refreshHome()
    }

    fun logout() {
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
    suspend fun article(address: com.example.btcwidget.nostr.core.Nip19.Address) = runCatching { repo.article(address) }.getOrNull()
    suspend fun articlesBy(pubkey: String) = runCatching { repo.articlesBy(pubkey) }.getOrDefault(emptyList())
    suspend fun profileFeed(pubkey: String, until: Long? = null) = runCatching { repo.profileFeed(pubkey, until) }.getOrDefault(emptyList())
    suspend fun hashtagFeed(tag: String) = runCatching { repo.hashtagFeed(tag) }.getOrDefault(emptyList())
    suspend fun refreshProfile(pubkey: String) = runCatching { repo.refreshProfile(pubkey) }.getOrNull()
    suspend fun followingOf(pubkey: String) = runCatching { repo.followingOf(pubkey) }.getOrDefault(emptySet())
    fun loadProfiles(pubkeys: Collection<String>) = viewModelScope.launch { runCatching { repo.loadProfiles(pubkeys) } }

    // ---- Images ----

    /** An uploaded image waiting to be posted. */
    data class Attachment(val url: String, val mime: String, val sha256: String, val width: Int, val height: Int)

    /** Prepares (strips metadata) and uploads a picked image; [onResult] gets it, or null on failure. */
    fun uploadImage(uri: android.net.Uri, onResult: (Attachment?) -> Unit) {
        var result: Attachment? = null
        act("Couldn't upload the image", finally = { onResult(result) }) {
            val prepared = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                com.example.btcwidget.nostr.ImagePrep.prepare(getApplication(), uri)
            }
            val blob = repo.uploadImage(prepared)
            result = Attachment(blob.url, prepared.mime, blob.sha256, prepared.width, prepared.height)
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
    fun sendDm(peers: List<String>, text: String, onResult: (Boolean) -> Unit) {
        var ok = false
        act("Couldn't send the message", finally = { onResult(ok) }) { repo.dms.send(peers, text); ok = true }
    }

    fun unlockDms() = act("Couldn't open messages") { repo.dms.unlock() }
    fun setUpDmInbox() = act("Couldn't set up your inbox") { repo.dms.setUpInbox(); say("Inbox ready: people can message you") }

    suspend fun linkPreview(url: String) = repo.linkPreview(url)

    suspend fun engagement(noteId: String) = runCatching { repo.engagement(noteId) }.getOrNull()

    // ---- Follow lists ----

    suspend fun followersOf(pubkey: String) = runCatching { repo.followersOf(pubkey) }.getOrDefault(emptyList())

    suspend fun globalFeed(relay: String?, until: Long? = null) = runCatching { repo.globalFeed(relay, until) }.getOrDefault(emptyList())
    val relays: List<String> get() = repo.pool.urls.toList().sorted()

    // ---- Search ----

    suspend fun resolve(query: String) = runCatching { repo.resolve(query) }.getOrNull()
    suspend fun searchPeople(query: String) = runCatching { repo.searchPeople(query) }.getOrDefault(emptyList())
    suspend fun searchNotes(query: String) = runCatching { repo.searchNotes(query) }.getOrDefault(emptyList())

    // ---- Actions ----

    fun like(note: Event) = act("Couldn't like") { repo.like(note) }
    fun repost(note: Event) = act("Couldn't repost") { repo.repost(note); say("Reposted") }
    fun follow(pubkey: String, follow: Boolean) = act(if (follow) "Couldn't follow" else "Couldn't unfollow") { repo.setFollowing(pubkey, follow) }
    fun mute(pubkey: String, mute: Boolean) = act("Couldn't update mutes") {
        repo.setMuted(pubkey, mute)
        if (mute) _home.update { s -> s.copy(items = s.items.filter { it.note.pubkey != pubkey && it.repostedBy != pubkey }) }
        say(if (mute) "Muted" else "Unmuted")
    }
    fun report(note: Event, reason: String) = act("Couldn't send the report") { repo.report(note, reason); say("Reported") }
    fun delete(note: Event) = act("Couldn't delete") {
        repo.delete(note)
        _home.update { s -> s.copy(items = s.items.filter { it.note.id != note.id }) }
        say("Deletion requested")
    }
    fun updateProfile(profile: Profile, onDone: () -> Unit) = act("Couldn't save your profile") { repo.updateProfile(profile); onDone() }

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
        act("Couldn't post", finally = { onResult(ok) }) {
            // Image URLs go in the text (so every client shows them) with imeta tags (NIP-92).
            val body = (listOf(text.trim()) + images.map { it.url }).filter { it.isNotEmpty() }.joinToString("\n")
            val tags = images.map { com.example.btcwidget.nostr.core.Imeta.tag(it.url, it.mime, it.sha256, it.width, it.height) } +
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
            if (!shown) _newPosts.update { pending -> (pending + item).distinctBy { it.key } }
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
        act(if (on) "Couldn't bookmark" else "Couldn't remove the bookmark") {
            repo.setBookmarked(note, on)
            say(if (on) "Bookmarked" else "Bookmark removed")
        }
    }

    val pinned get() = repo.pinned

    // ---- Polls (NIP-88) ----

    suspend fun pollTally(poll: com.example.btcwidget.nostr.core.Poll) = runCatching { repo.pollTally(poll) }.getOrNull()

    fun vote(poll: com.example.btcwidget.nostr.core.Poll, optionIds: List<String>, onResult: (Boolean) -> Unit) {
        var ok = false
        act("Couldn't vote", finally = { onResult(ok) }) { repo.vote(poll, optionIds); ok = true }
    }

    fun createPoll(question: String, options: List<String>, multipleChoice: Boolean, durationSeconds: Long?, onResult: (Boolean) -> Unit) {
        var ok = false
        act("Couldn't post the poll", finally = { onResult(ok) }) {
            val endsAt = durationSeconds?.let { System.currentTimeMillis() / 1000 + it }
            val event = repo.createPoll(question, options, multipleChoice, endsAt)
            _home.update { it.copy(items = listOf(FeedItem(event)) + it.items) }
            ok = true
        }
    }

    // ---- Translation (on the device, ML Kit) ----

    sealed interface Translation {
        data object Loading : Translation
        data class Done(val text: String, val from: String) : Translation
    }

    private val _translations = MutableStateFlow<Map<String, Translation>>(emptyMap())
    val translations: StateFlow<Map<String, Translation>> = _translations.asStateFlow()

    /** Translates [note] into the phone's language, or shows the original again if it is translated. */
    fun toggleTranslation(note: Event, text: String) {
        when (_translations.value[note.id]) {
            is Translation.Done -> { _translations.update { it - note.id }; return }
            Translation.Loading -> return
            null -> Unit
        }
        _translations.update { it + (note.id to Translation.Loading) }
        viewModelScope.launch {
            val result = runCatching { com.example.btcwidget.nostr.NoteTranslator.translate(text) }
            _translations.update { map ->
                result.fold(
                    onSuccess = { map + (note.id to Translation.Done(it.text, it.fromLanguage)) },
                    onFailure = { map - note.id }
                )
            }
            result.exceptionOrNull()?.let { e ->
                if (e is CancellationException) throw e
                say((e as? com.example.btcwidget.nostr.NoteTranslator.Unsupported)?.message ?: "Couldn't translate. Check your connection for the first download.")
            }
        }
    }

    fun togglePin(note: Event) {
        val on = note.id !in repo.pinned.value
        act(if (on) "Couldn't pin" else "Couldn't unpin") { repo.setPinned(note, on); say(if (on) "Pinned to your profile" else "Unpinned") }
    }

    suspend fun pinnedNoteOf(pubkey: String) = runCatching { repo.pinnedNoteOf(pubkey) }.getOrNull()

    suspend fun bookmarkedNotes() = runCatching { repo.bookmarkedNotes() }.getOrDefault(emptyList())

    val nwc get() = repo.nwc

    /**
     * Zaps [amountSat]: with a connected wallet (NWC) it pays right here; otherwise the
     * invoice goes to [onInvoice], which opens a wallet app.
     */
    fun zap(recipient: String, amountSat: Long, note: Event?, comment: String, onInvoice: (String) -> Unit) =
        act("Couldn't zap") {
            val invoice = repo.zapInvoice(recipient, amountSat, note, comment)
            if (repo.nwc.value != null) {
                say("⚡ Paying $amountSat sats…")
                repo.payWithNwc(invoice)
                say("⚡ Zapped $amountSat sats")
                note?.let { n -> runCatching { repo.loadStats(listOf(n.id)) } }
            } else {
                onInvoice(invoice)
            }
        }

    /** Returns an error message, or null when connected. */
    fun connectNwc(uri: String): String? = try {
        repo.connectNwc(uri); say("Wallet connected"); null
    } catch (e: Exception) {
        e.message ?: "Couldn't connect the wallet"
    }

    fun disconnectNwc() { repo.disconnectNwc(); say("Wallet disconnected") }

    private fun say(message: String) { _messages.tryEmit(message) }

    private fun act(failure: String, finally: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SignerRejected) {
                say("Cancelled in the signer")
            } catch (e: Exception) {
                Log.d("SocialViewModel", failure, e)
                say(e.message?.let { "$failure: $it" } ?: failure)
            } finally {
                finally()
            }
        }
    }
}
