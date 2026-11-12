package com.example.btcwidget.ui.social

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.ui.main.EInk

private val Accent = Color(0xFFF7931A)
private val Green = Color(0xFF3FB950)

/**
 * A card at the top of the screen while a repost, reply or post is being sent: what's happening
 * ("Waiting for your signer app…", "Sending · 2 of 5 relays answered") over an orange progress
 * bar, then a tick, or the reason in red if it failed. Hidden when nothing is being sent.
 */
@Composable
fun PublishStatusBar(vm: SocialViewModel, modifier: Modifier = Modifier) {
    val activity by vm.activity.collectAsState()
    // Keep the last one on screen while it slides away.
    val last = remember { arrayOfNulls<SocialViewModel.PublishActivity>(1) }
    activity?.let { last[0] = it }
    AnimatedVisibility(
        visible = activity != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        last[0]?.let { PublishCard(it) }
    }
}

@Composable
private fun PublishCard(a: SocialViewModel.PublishActivity) {
    val color = when (a.state) {
        SocialViewModel.PublishActivity.State.WORKING -> Accent
        SocialViewModel.PublishActivity.State.DONE -> Green
        SocialViewModel.PublishActivity.State.FAILED -> EInk.Alert
    }
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(SheetColor)
            .border(0.5.dp, EInk.BodyStroke, shape)
            // TalkBack reads the status as it changes.
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                when (a.state) {
                    SocialViewModel.PublishActivity.State.WORKING ->
                        CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    SocialViewModel.PublishActivity.State.DONE -> Box(Modifier.size(20.dp).clip(CircleShape).background(Green), contentAlignment = Alignment.Center) {
                        Text("✓", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    SocialViewModel.PublishActivity.State.FAILED -> Box(Modifier.size(20.dp).clip(CircleShape).background(EInk.Alert), contentAlignment = Alignment.Center) {
                        Text("!", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(a.title, color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(a.status, color = EInk.Caption, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        // The accent bar along the bottom: fills as relays answer; slides while signing.
        val barModifier = Modifier.fillMaxWidth().height(3.dp)
        val progress = a.progress
        if (progress == null) {
            LinearProgressIndicator(color = color, trackColor = EInk.Track, modifier = barModifier)
        } else {
            val shown by animateFloatAsState(progress, animationSpec = tween(300), label = tr("publish progress"))
            LinearProgressIndicator(progress = { shown }, color = color, trackColor = EInk.Track, drawStopIndicator = {}, gapSize = 0.dp, modifier = barModifier)
        }
    }
}
