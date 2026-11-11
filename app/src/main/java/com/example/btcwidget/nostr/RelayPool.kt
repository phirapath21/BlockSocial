package com.example.btcwidget.nostr

import android.util.Log
import com.example.btcwidget.nostr.core.ClientMessage
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Filter
import com.example.btcwidget.nostr.core.RelayMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Outcome of publishing one event to one relay. */
data class PublishResult(val relay: String, val accepted: Boolean, val message: String)

/** How one relay connection is doing, for the relay status screen. */
data class RelayStatus(
    val url: String,
    val state: State,
    /** One of the user's own relays (else opened to read someone's outbox relays). */
    val primary: Boolean,
    /** How long the last successful connection took to open. */
    val connectMs: Long?,
    /** Failed attempts in a row. */
    val failures: Int,
    val lastError: String?,
    /** Events received since the app started. */
    val events: Int
) {
    enum class State { CONNECTED, CONNECTING, RETRYING, FAILED }
}

/**
 * WebSocket connections to a set of relays. Subscriptions are sent to every connected
 * relay; incoming events are signature-checked and de-duplicated before delivery.
 * Connections open lazily and reconnect with backoff while something needs them.
 */
class RelayPool(private val client: OkHttpClient = defaultClient) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val relays = ConcurrentHashMap<String, Relay>()
    private val subs = ConcurrentHashMap<String, Sub>()
    private val okWaiters = ConcurrentHashMap<String, CompletableDeferred<PublishResult>>()
    private val nextId = AtomicInteger()
    private var sleepJob: kotlinx.coroutines.Job? = null

    init { synchronized(all) { all.add(this) } }

    /**
     * Closes every connection without forgetting anything: relays don't reconnect until they're
     * needed (something is sent or subscribed) or the app comes back. Live subscriptions are
     * sent again when a relay reconnects.
     */
    fun sleep() {
        sleepJob?.cancel(); sleepJob = null
        relays.values.forEach { it.sleep() }
    }

    /** Reconnects every sleeping relay. */
    fun wake() {
        sleepJob?.cancel(); sleepJob = null
        relays.values.forEach { it.wake() }
    }

    /** In the background, a relay woken by use goes back to sleep after a couple of minutes. */
    private fun sleepAgainLater() {
        if (!backgrounded || sleepJob?.isActive == true) return
        sleepJob = scope.launch { delay(BACKGROUND_AWAKE_MS); if (backgrounded) sleep() }
    }

    private class Sub(
        val filters: List<Filter>,
        /** Relays this subscription is sent to; null = all. */
        val only: Set<String>?,
        val onEvent: (Event, String) -> Unit,
        /** (relay, gotEose): false when the relay closed the subscription or dropped. */
        val onEose: (String, Boolean) -> Unit,
        val seen: MutableSet<String> = ConcurrentHashMap.newKeySet()
    )

    /** Relay URLs currently in use. */
    val urls: Set<String> get() = relays.keys

    private val primary: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Every open relay's connection state; the user's own relays first. */
    fun status(): List<RelayStatus> = relays.values.map { it.status() }.sortedWith(compareBy({ !it.primary }, { it.url }))

    /** Drops a failing relay's backoff and tries it again now. */
    fun retry(url: String) { relays[url]?.retryNow() }

    /** Uses exactly [urls]: opens new ones, closes dropped ones. */
    fun setRelays(urls: Collection<String>) {
        val wanted = urls.toSet()
        primary.clear(); primary.addAll(wanted)
        relays.keys.filter { it !in wanted }.forEach { relays.remove(it)?.close() }
        wanted.forEach { url -> relays.getOrPut(url) { Relay(url).also { it.connect() } } }
    }

    /**
     * Opens a subscription on every relay. Call the returned function to close it.
     * [onEose] is called once per relay that finishes sending stored events (true), or that
     * closed the subscription or dropped (false).
     */
    fun subscribe(
        filters: List<Filter>,
        onEvent: (Event, String) -> Unit,
        onEose: (String, Boolean) -> Unit = { _, _ -> },
        only: Set<String>? = null
    ): () -> Unit {
        val id = "bf" + nextId.incrementAndGet()
        subs[id] = Sub(filters, only, onEvent, onEose)
        val req = ClientMessage.req(id, filters)
        targets(only).forEach { it.send(req) }
        return {
            subs.remove(id)
            val close = ClientMessage.close(id)
            targets(only).forEach { it.send(close) }
        }
    }

    private fun targets(only: Set<String>?) = if (only == null) relays.values else relays.filterKeys { it in only }.values

    /** Adds [urls] without dropping others; beyond [max] relays, the oldest unused ones close. */
    fun addRelays(urls: Collection<String>, max: Int = 24) {
        urls.forEach { url -> relays.getOrPut(url) { Relay(url).also { it.connect() } } }
        val busy = subs.values.flatMap { it.only ?: emptySet() }.toSet() + urls
        relays.keys.filter { it !in busy }.take((relays.size - max).coerceAtLeast(0)).forEach { relays.remove(it)?.close() }
    }

    /**
     * Stored events matching [filters]. Returns when every relay has answered, or [graceMs]
     * after the first relay finished (slow relays don't hold the screen up: their events
     * are mostly duplicates), or at [timeoutMs]. Relays that are down and retrying aren't
     * waited for at all.
     */
    suspend fun query(filters: List<Filter>, timeoutMs: Long = 6_000, graceMs: Long = 1_000, only: Set<String>? = null): List<Event> {
        val out = ConcurrentHashMap<String, Event>()
        val pending = ConcurrentHashMap.newKeySet<String>().apply {
            addAll(relays.filter { (url, r) -> r.reachable && (only == null || url in only) }.keys)
        }
        if (pending.isEmpty()) return emptyList()
        val done = CompletableDeferred<Unit>()
        val firstAnswer = java.util.concurrent.atomic.AtomicBoolean(false)
        val close = subscribe(filters, { e, _ -> out[e.id] = e }, { relay, gotEose ->
            pending.remove(relay)
            if (pending.isEmpty()) done.complete(Unit)
            else if (gotEose && firstAnswer.compareAndSet(false, true)) {
                scope.launch { delay(graceMs); done.complete(Unit) }
            }
        }, only)
        withTimeoutOrNull(timeoutMs) { done.await() }
        close()
        return out.values.sortedByDescending { it.createdAt }
    }

    /** Live events for [filters] while collected. */
    fun stream(filters: List<Filter>): Flow<Event> = callbackFlow {
        val close = subscribe(filters, { e, _ -> trySend(e) })
        awaitClose { close() }
    }

    /** Sends [event] to every relay and waits for their OK (or [timeoutMs]). */
    suspend fun publish(
        event: Event,
        timeoutMs: Long = 8_000,
        only: Set<String>? = null,
        untilFirstAccepted: Boolean = false,
        /** Called with how many relays it was sent to, before any answers. */
        onSent: (Int) -> Unit = {},
        /** Called as each relay answers (also after this returns early with [untilFirstAccepted]). */
        onAnswer: (PublishResult) -> Unit = {}
    ): List<PublishResult> {
        val targets = relays.values.filter { only == null || it.url in only }
        val waiters = targets.associateWith { r ->
            CompletableDeferred<PublishResult>().also { okWaiters[r.url + " " + event.id] = it }
        }
        onSent(targets.size)
        waiters.values.forEach { d -> d.invokeOnCompletion { e -> if (e == null) runCatching { onAnswer(d.getCompleted()) } } }
        val msg = ClientMessage.event(event)
        targets.forEach { it.send(msg) }
        if (untilFirstAccepted && waiters.isNotEmpty()) {
            // Return as soon as one relay has it; the others keep receiving it in the background.
            val first = CompletableDeferred<PublishResult>()
            waiters.values.forEach { d -> d.invokeOnCompletion { if (d.getCompleted().accepted) first.complete(d.getCompleted()) } }
            val accepted = withTimeoutOrNull(timeoutMs) {
                kotlinx.coroutines.selects.select<PublishResult?> {
                    first.onAwait { it }
                    scope.async { waiters.values.forEach { it.await() }; null }.onAwait { it }
                }
            }
            if (accepted != null) {
                scope.launch { delay(timeoutMs); targets.forEach { okWaiters.remove(it.url + " " + event.id) } }
                return listOf(accepted)
            }
        }
        val results = waiters.map { (relay, deferred) ->
            withTimeoutOrNull(timeoutMs) { deferred.await() } ?: PublishResult(relay.url, false, "timed out")
        }
        targets.forEach { okWaiters.remove(it.url + " " + event.id) }
        return results
    }

    fun closeAll() {
        synchronized(all) { all.remove(this) }
        sleepJob?.cancel()
        relays.values.forEach { it.close() }
        relays.clear()
        subs.clear()
    }

    /**
     * NIP-42: signs a relay's AUTH challenge. Null (the default) never authenticates, so
     * relays don't learn who is reading; set only for relays the user chose to join (groups).
     */
    @Volatile var authenticator: (suspend (relay: String, challenge: String) -> Event?)? = null

    private fun authenticate(url: String, challenge: String) {
        val auth = authenticator ?: return
        scope.launch {
            val signed = runCatching { auth(url, challenge) }.getOrNull() ?: return@launch
            val relay = relays[url] ?: return@launch
            relay.send(ClientMessage.auth(signed))
            // Ask again for what the relay refused before authenticating.
            delay(500)
            subs.forEach { (id, sub) -> if (sub.only == null || url in sub.only) relay.send(ClientMessage.req(id, sub.filters)) }
        }
    }

    private fun handle(url: String, text: String) {
        when (val m = RelayMessage.parse(text)) {
            is RelayMessage.Auth -> authenticate(url, m.challenge)
            is RelayMessage.EventMsg -> {
                val sub = subs[m.subId] ?: return
                if (!sub.seen.add(m.event.id)) return
                if (sub.filters.none { it.matches(m.event) } || !m.event.verify()) return
                sub.onEvent(m.event, url)
            }
            is RelayMessage.Eose -> subs[m.subId]?.onEose?.invoke(url, true)
            is RelayMessage.Closed -> subs[m.subId]?.onEose?.invoke(url, false)
            is RelayMessage.Ok -> okWaiters[url + " " + m.eventId]?.complete(PublishResult(url, m.accepted, m.message))
            is RelayMessage.Notice -> Log.d(TAG, "$url: ${m.message.take(200)}")
            else -> Unit
        }
    }

    private inner class Relay(val url: String) {
        @Volatile private var socket: WebSocket? = null
        @Volatile private var open = false
        @Volatile private var closed = false
        @Volatile private var badAddress = false
        @Volatile private var sleeping = false
        @Volatile private var startedAt = 0L
        @Volatile private var connectMs: Long? = null
        @Volatile private var lastError: String? = null
        @Volatile private var retryJob: kotlinx.coroutines.Job? = null
        private val received = AtomicInteger()
        private val generation = AtomicInteger()
        private val queue = java.util.concurrent.ConcurrentLinkedQueue<String>()
        private var attempts = 0

        /** Open, or still on its first connection attempt (not failing and backing off). */
        val reachable: Boolean get() = !closed && (open || attempts == 0)

        fun connect() {
            if (closed) return
            // A malformed address (e.g. typed into Settings) must not crash the app.
            val request = runCatching { Request.Builder().url(url).build() }.getOrElse {
                Log.d(TAG, "$url: bad address")
                closed = true
                badAddress = true
                lastError = "Not a relay address"
                return
            }
            startedAt = System.currentTimeMillis()
            val gen = generation.incrementAndGet()
            socket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    open = true
                    attempts = 0
                    connectMs = System.currentTimeMillis() - startedAt
                    lastError = null
                    // Resend live subscriptions, then anything queued while connecting.
                    subs.forEach { (id, sub) -> if (sub.only == null || url in sub.only) webSocket.send(ClientMessage.req(id, sub.filters)) }
                    while (true) webSocket.send(queue.poll() ?: break)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    // Never let one bad frame take the connection (or the app) down.
                    received.incrementAndGet()
                    try { handle(url, text) } catch (e: Exception) { Log.d(TAG, "$url: ${e.message}") }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (gen != generation.get()) return // an older connection, replaced by a retry
                    Log.d(TAG, "$url failed: ${t.message}")
                    lastError = response?.let { "HTTP ${it.code}" } ?: t.message?.take(80) ?: t.javaClass.simpleName
                    dropped()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { if (gen == generation.get()) dropped() }
            })
        }

        private fun dropped() {
            open = false
            // Pending subscriptions on this relay won't get EOSE now.
            subs.values.forEach { if (it.only == null || url in it.only) it.onEose(url, false) }
            if (closed || sleeping) return
            attempts++
            val wait = (1_000L shl attempts.coerceAtMost(6)).coerceAtMost(60_000)
            retryJob = scope.launch { delay(wait); runCatching { connect() } }
        }

        /** Disconnects until [wake]; nothing is lost (queued messages and live subscriptions stay). */
        fun sleep() {
            if (closed) return
            sleeping = true
            retryJob?.cancel()
            attempts = 0
            generation.incrementAndGet() // the closing socket's callbacks are ignored
            open = false
            runCatching { socket?.close(1000, null) }
            socket = null
        }

        fun wake() {
            if (closed || !sleeping) return
            sleeping = false
            connect()
        }

        fun retryNow() {
            if (closed || open) return
            retryJob?.cancel()
            runCatching { socket?.cancel() }
            connect()
        }

        fun status() = RelayStatus(
            url = url,
            state = when {
                badAddress -> RelayStatus.State.FAILED
                open -> RelayStatus.State.CONNECTED
                attempts == 0 -> RelayStatus.State.CONNECTING
                attempts >= 5 -> RelayStatus.State.FAILED
                else -> RelayStatus.State.RETRYING
            },
            primary = url in primary,
            connectMs = connectMs,
            failures = attempts,
            lastError = lastError,
            events = received.get()
        )

        fun send(message: String) {
            if (open && socket?.send(message) == true) return
            // Asleep (app in the background): something needs this relay, so connect now.
            if (sleeping) { wake(); sleepAgainLater() }
            // A live subscription's REQ is sent again on open anyway: queueing it too made the relay
            // send all its stored events twice. Queue the rest (EVENT, CLOSE).
            if (message.startsWith("[\"REQ\",\"")) {
                val id = message.substring(8, message.indexOf('"', 8).coerceAtLeast(8))
                if (subs.containsKey(id)) return
            }
            if (queue.size < 200) queue.add(message)
        }

        fun close() {
            closed = true
            socket?.close(1000, null)
        }
    }

    companion object {
        private const val TAG = "RelayPool"
        private const val BACKGROUND_AWAKE_MS = 120_000L

        /** Every open pool, to put them all to sleep when the app goes to the background. */
        private val all: MutableSet<RelayPool> = java.util.Collections.newSetFromMap(java.util.WeakHashMap())
        @Volatile private var backgrounded = false

        /**
         * The app left the screen: close relay connections (they'd otherwise stay open, pinging
         * and receiving live events, using battery and data). Background work still gets the
         * relays it uses: sending to a sleeping relay wakes it for a couple of minutes.
         */
        fun enterBackground() {
            backgrounded = true
            synchronized(all) { all.toList() }.forEach { it.sleep() }
        }

        /** The app is back: reconnect everything, live subscriptions included. */
        fun enterForeground() {
            backgrounded = false
            synchronized(all) { all.toList() }.forEach { it.wake() }
        }

        val DEFAULT_RELAYS = listOf("wss://relay.damus.io", "wss://nos.lol", "wss://relay.primal.net")

        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build()
        }

        /** For media uploads: big videos on slow connections need longer than the default. */
        val uploadClient: OkHttpClient by lazy {
            defaultClient.newBuilder()
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .callTimeout(15, TimeUnit.MINUTES)
                .build()
        }
    }
}
