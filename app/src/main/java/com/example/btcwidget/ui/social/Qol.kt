package com.example.btcwidget.ui.social

import android.os.Build
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
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

/**
 * A one-time tip, the first time someone meets a feature (as X shows them): a small card with
 * an orange edge and "Got it". Once dismissed it never shows again on this phone.
 */
@Composable
fun FirstTimeTip(id: String, text: String, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var shown by androidx.compose.runtime.remember(id) { androidx.compose.runtime.mutableStateOf(!com.example.btcwidget.AppPrefs.tipSeen(context, id)) }
    androidx.compose.animation.AnimatedVisibility(
        visible = shown, modifier = modifier,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
    ) {
        val shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
        androidx.compose.foundation.layout.Row(
            androidx.compose.ui.Modifier
                .shadow(8.dp, shape)
                .clip(shape)
                .background(SheetColor)
                .border(1.dp, SettingsAccent.copy(alpha = 0.6f), shape)
                .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            androidx.compose.material3.Text("💡", fontSize = 18.sp)
            androidx.compose.material3.Text(
                text, color = com.example.btcwidget.ui.main.EInk.Value, fontSize = 14.sp, lineHeight = 19.sp,
                modifier = androidx.compose.ui.Modifier.weight(1f).padding(horizontal = 10.dp)
            )
            androidx.compose.material3.Text(
                tr("Got it"), color = SettingsAccent, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = androidx.compose.ui.Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .clickable { shown = false; com.example.btcwidget.AppPrefs.setTipSeen(context, id) }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
    }
}

/** The widest a column of posts gets on a wide screen, as on X (more makes lines hard to read). */
val READABLE_WIDTH = 640.dp

/**
 * On a wide screen (landscape, tablet, unfolded), [content] in a column of [READABLE_WIDTH] in
 * the middle with hairlines either side, as X lays out its timeline; otherwise the full width.
 */
@Composable
fun ReadableColumn(enabled: Boolean = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600, content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    androidx.compose.foundation.layout.Box(
        androidx.compose.ui.Modifier.fillMaxSize().background(com.example.btcwidget.ui.main.EInk.Background),
        contentAlignment = androidx.compose.ui.Alignment.TopCenter
    ) {
        androidx.compose.foundation.layout.Box(
            androidx.compose.ui.Modifier
                .widthIn(max = READABLE_WIDTH)
                .fillMaxSize()
                .drawBehind {
                    val c = com.example.btcwidget.ui.main.EInk.BodyStroke
                    val w = 0.5.dp.toPx()
                    drawLine(c, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, size.height), w)
                    drawLine(c, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(size.width, size.height), w)
                }
        ) { content() }
    }
}
