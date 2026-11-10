package com.example.btcwidget.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.btcwidget.ui.main.EInk

/**
 * A video in a note. Nothing downloads until it's tapped (data and battery); then it plays
 * inline with standard controls, and the player is released when it scrolls away.
 */
@Composable
fun VideoPlayer(url: String) {
    var started by remember(url) { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(shape)
            .background(Color.Black)
            .border(0.5.dp, EInk.BodyStroke, shape),
        contentAlignment = Alignment.Center
    ) {
        if (!started) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize().clickable(onClickLabel = "Play video") { started = true },
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Text("▶", color = Color.White, fontSize = 42.sp)
                Text(url.substringAfter("://").substringBefore('/'), color = Color(0xFFBBBBBB), fontSize = 12.sp)
            }
        } else {
            val context = LocalContext.current
            val player = remember(url) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(url))
                    prepare()
                    playWhenReady = true
                }
            }
            DisposableEffect(player) { onDispose { player.release() } }
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
