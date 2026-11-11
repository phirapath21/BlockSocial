package com.example.btcwidget.ui.social

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * The full-screen video's player, kept here rather than in the viewer so it can carry on in
 * Picture-in-Picture: Android's PiP window shows only the activity, not the viewer's own
 * window, so while in PiP the viewer is hidden and the activity draws this player instead.
 */
object PipVideo {
    /** The video open full screen, if any. */
    var player: ExoPlayer? by mutableStateOf(null)
        private set
    private var url: String? = null

    /** Whether the app is shrunk to a PiP window right now. */
    var inPip by mutableStateOf(false)

    /** Set when the PiP window was closed: the viewer should close too. */
    var closedInPip by mutableStateOf(false)

    /** Whether leaving the app should shrink it to PiP: a full-screen video that is playing. */
    val wantsPip: Boolean get() = player?.let { it.isPlaying || it.playWhenReady && it.playbackState != Player.STATE_ENDED } == true

    /** The player for [url] (the one already playing it if it's open, else a new one). */
    fun acquire(context: Context, url: String, create: (Context, String) -> ExoPlayer): ExoPlayer {
        player?.let { if (this.url == url) return it }
        release()
        closedInPip = false
        return create(context, url).also { player = it; this.url = url }
    }

    /** Ends the full-screen video. Hiding the viewer for PiP doesn't call this. */
    fun release(save: (String, Long) -> Unit = { _, _ -> }) {
        val p = player ?: return
        url?.let { save(it, p.currentPosition) }
        p.release()
        player = null
        url = null
    }
}
