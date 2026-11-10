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

    private class Sub(
        val filters: List<Filter>,
        val onEvent: (Event, String) -> Unit,
        val onEose: (String) -> Unit,
        val seen: MutableSet<String> = ConcurrentHashMap.newKeySet()
    )

    /** Relay URLs currently in use. */
    val urls: Set<String> get() = relays.keys

    /** Uses exactly [urls]: opens new ones, closes dropped ones. */
    fun setRelays(urls: Collection<String>) {
        val wanted = urls.toSet()
        relays.keys.filter { it !in wanted }.forEach { relays.remove(it)?.close() }
        wanted.forEach { url -> relays.getOrPut(url) { Relay(url).also { it.connect() } } }
    }

    /**
     * Opens a subscription on every relay. Call the returned function to close it.
     * [onEose] is called once per relay that finishes sending stored events.
     */
    fun subscribe(filters: List<Filter>, onEvent: (Event, String) -> Unit, onEose: (String) -> Unit = {}): () -> Unit {
        val id = "bf" + nextId.incrementAndGet()
        subs[id] = Sub(filters, onEvent, onEose)
        val req = ClientMessage.req(id, filters)
        relays.values.forEach { it.send(req) }
        return {
            subs.remove(id)
            val close = ClientMessage.close(id)
            relays.values.forEach { it.send(close) }
        }
    }

    /** Stored events matching [filters], gathered until every relay sends EOSE or [timeoutMs] passes. */
    suspend fun query(filters: List<Filter>, timeoutMs: Long = 6_000): List<Event> {
        val out = ConcurrentHashMap<String, Event>()
        val pending = ConcurrentHashMap.newKeySet<String>().apply { addAll(relays.keys) }
        val done = CompletableDeferred<Unit>()
        if (pending.isEmpty()) return emptyList()
        val close = subscribe(filters, { e, _ -> out[e.id] = e }) { relay ->
            pending.remove(relay)
            if (pending.isEmpty()) done.complete(Unit)
        }
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
    suspend fun publish(event: Event, timeoutMs: Long = 8_000): List<PublishResult> {
        val targets = relays.values.toList()
        val waiters = targets.associateWith { r ->
            CompletableDeferred<PublishResult>().also { okWaiters[r.url + " " + event.id] = it }
        }
        val msg = ClientMessage.event(event)
        targets.forEach { it.send(msg) }
        val results = waiters.map { (relay, deferred) ->
            withTimeoutOrNull(timeoutMs) { deferred.await() } ?: PublishResult(relay.url, false, "timed out")
        }
        targets.forEach { okWaiters.remove(it.url + " " + event.id) }
        return results
    }

    fun closeAll() {
        relays.values.forEach { it.close() }
        relays.clear()
        subs.clear()
    }

    private fun handle(url: String, text: String) {
        when (val m = RelayMessage.parse(text)) {
            is RelayMessage.EventMsg -> {
                val sub = subs[m.subId] ?: return
                if (!sub.seen.add(m.event.id)) return
                if (sub.filters.none { it.matches(m.event) } || !m.event.verify()) return
                sub.onEvent(m.event, url)
            }
            is RelayMessage.Eose -> subs[m.subId]?.onEose?.invoke(url)
            is RelayMessage.Closed -> subs[m.subId]?.onEose?.invoke(url)
            is RelayMessage.Ok -> okWaiters[url + " " + m.eventId]?.complete(PublishResult(url, m.accepted, m.message))
            is RelayMessage.Notice -> Log.d(TAG, "$url: ${m.message.take(200)}")
            else -> Unit
        }
    }

    private inner class Relay(val url: String) {
        @Volatile private var socket: WebSocket? = null
        @Volatile private var open = false
        @Volatile private var closed = false
        private val queue = java.util.concurrent.ConcurrentLinkedQueue<String>()
        private var attempts = 0

        fun connect() {
            if (closed) return
            // A malformed address (e.g. typed into Settings) must not crash the app.
            val request = runCatching { Request.Builder().url(url).build() }.getOrElse {
                Log.d(TAG, "$url: bad address")
                closed = true
                return
            }
            socket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    open = true
                    attempts = 0
                    // Resend live subscriptions, then anything queued while connecting.
                    subs.forEach { (id, sub) -> webSocket.send(ClientMessage.req(id, sub.filters)) }
                    while (true) webSocket.send(queue.poll() ?: break)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    // Never let one bad frame take the connection (or the app) down.
                    try { handle(url, text) } catch (e: Exception) { Log.d(TAG, "$url: ${e.message}") }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.d(TAG, "$url failed: ${t.message}")
                    dropped()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = dropped()
            })
        }

        private fun dropped() {
            open = false
            // Pending subscriptions on this relay won't get EOSE now.
            subs.values.forEach { it.onEose(url) }
            if (closed) return
            attempts++
            val wait = (1_000L shl attempts.coerceAtMost(6)).coerceAtMost(60_000)
            scope.launch { delay(wait); runCatching { connect() } }
        }

        fun send(message: String) {
            if (open && socket?.send(message) == true) return
            // REQ for live subs is replayed on open; queue the rest (EVENT, CLOSE, one-off REQ).
            if (queue.size < 200) queue.add(message)
        }

        fun close() {
            closed = true
            socket?.close(1000, null)
        }
    }

    companion object {
        private const val TAG = "RelayPool"

        val DEFAULT_RELAYS = listOf("wss://relay.damus.io", "wss://nos.lol", "wss://relay.primal.net")

        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build()
        }
    }
}
