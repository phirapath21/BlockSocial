package com.example.btcwidget.ui.social

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProduceStateScope
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow

/**
 * What screens under the top one had loaded, so Back shows them exactly as they were (the same
 * posts, the same scroll position) instead of an empty screen loading again, as on X.
 *
 * Navigation forgets a screen's state when another screen opens over it; only values saved
 * with rememberSaveable come back. So each place that keeps something saves just an id that
 * way, and the value itself waits here under that id. Screens that are closed for good leave
 * their values behind, so only the most recently used ones are kept.
 */
internal object ScreenMemory {
    private const val MAX = 600
    private val values = object : LinkedHashMap<String, Any?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Any?>?) = size > MAX
    }

    @Synchronized fun has(key: String) = values.containsKey(key)
    @Synchronized fun get(key: String): Any? = values[key]
    @Synchronized fun put(key: String, value: Any?) { values[key] = value }
    @Synchronized fun <T> getOrPut(key: String, init: () -> T): T {
        @Suppress("UNCHECKED_CAST")
        return if (values.containsKey(key)) values[key] as T else init().also { values[key] = it }
    }
    @Synchronized fun clear() = values.clear()
}

/** A key for this call site on this screen, kept across Back, plus [inputs]. */
@Composable
private fun keptKey(inputs: Array<out Any?>): String {
    val id = rememberSaveable { java.util.UUID.randomUUID().toString() }
    return id + "|" + inputs.contentDeepHashCode()
}

/**
 * Like `remember`, but the value is still there when you come Back to this screen (until
 * [inputs] change). Use it for what a screen loaded and how far it scrolled, not for dialogs.
 */
@Composable
fun <T> rememberKept(vararg inputs: Any?, init: () -> T): T {
    val key = keptKey(inputs)
    return remember(key) { ScreenMemory.getOrPut(key, init) }
}

/** `mutableStateOf` kept across Back (see [rememberKept]). */
@Composable
fun <T> rememberKeptState(vararg inputs: Any?, initial: () -> T): MutableState<T> =
    rememberKept(*inputs) { mutableStateOf(initial()) }

/**
 * Like `produceState`, but what it loaded is still there when you come Back: [producer] runs
 * only the first time for these [keys], not every time the screen is shown again.
 */
@Composable
fun <T> produceKept(initialValue: T, vararg keys: Any?, producer: suspend ProduceStateScope<T>.() -> Unit): State<T> {
    val key = keptKey(keys)
    val loaded = ScreenMemory.has(key)
    @Suppress("UNCHECKED_CAST")
    val state = produceState(if (loaded) ScreenMemory.get(key) as T else initialValue, key) {
        if (!loaded) producer()
    }
    // Remember every value it reaches (a load can set a placeholder first, then the result).
    LaunchedEffect(key) {
        snapshotFlow { state.value }.collect { v -> if (loaded || v !== initialValue) ScreenMemory.put(key, v) }
    }
    return state
}
