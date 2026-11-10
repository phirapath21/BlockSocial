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

    private fun afterLogin() {
        accountLoaded = false
        _home.value = Paged()
        _notifications.value = Paged()
        refreshHome()
    }

    fun logout() {
        repo.logout()
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
    fun send(text: String, replyTo: Event?, quote: Event?, images: List<Attachment> = emptyList(), onResult: (Boolean) -> Unit) {
        var ok = false
        act("Couldn't post", finally = { onResult(ok) }) {
            // Image URLs go in the text (so every client shows them) with imeta tags (NIP-92).
            val body = (listOf(text.trim()) + images.map { it.url }).filter { it.isNotEmpty() }.joinToString("\n")
            val tags = images.map { com.example.btcwidget.nostr.core.Imeta.tag(it.url, it.mime, it.sha256, it.width, it.height) }
            val event = when {
                replyTo != null -> repo.reply(body, replyTo, tags)
                quote != null -> repo.quote(body, quote, tags)
                else -> repo.post(body, tags)
            }
            if (replyTo == null) _home.update { it.copy(items = listOf(FeedItem(event)) + it.items) }
            ok = true
        }
    }

    /** Gets a zap invoice and passes it to [onInvoice] (which opens a wallet). */
    fun zap(recipient: String, amountSat: Long, note: Event?, comment: String, onInvoice: (String) -> Unit) =
        act("Couldn't zap") { onInvoice(repo.zapInvoice(recipient, amountSat, note, comment)) }

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
