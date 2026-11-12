package com.example.btcwidget.ui.social

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Tapping the tab you're already on, as on X: the first tap scrolls back to the top, a tap
 * when you're already at the top refreshes. The bars send the tab's name; its screen reacts.
 */
object TabTaps {
    private val taps = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val flow = taps.asSharedFlow()
    fun tap(tab: String) { taps.tryEmit(tab) }
}

/**
 * Scrolls [list] to the top when [tab] is tapped again, or calls [onRefresh] when it's already
 * there. Far down, it jumps most of the way first so the animation stays short.
 */
@Composable
fun ScrollToTopOnTabTap(tab: String, list: LazyListState, onRefresh: (() -> Unit)? = null) {
    val refresh by rememberUpdatedState(onRefresh)
    LaunchedEffect(tab, list) {
        TabTaps.flow.collect { tapped ->
            if (tapped != tab) return@collect
            if (list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0) refresh?.invoke()
            else {
                if (list.firstVisibleItemIndex > 12) list.scrollToItem(8)
                list.animateScrollToItem(0)
            }
        }
    }
}

/** Whether the top and bottom bars are showing (they slide away while you scroll down a feed). */
val LocalBarsVisible = compositionLocalOf { true }

/**
 * Small taps of the phone's vibration, as X gives them: [tick] for a like or a toggle,
 * [confirm] when something is sent, [heavy] for a long press. Follows the phone's
 * touch-feedback setting.
 */
class Haptics(private val view: View) {
    fun tick() { view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 27) HapticFeedbackConstants.KEYBOARD_TAP else HapticFeedbackConstants.VIRTUAL_KEY) }
    fun confirm() { view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY) }
    fun heavy() { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
