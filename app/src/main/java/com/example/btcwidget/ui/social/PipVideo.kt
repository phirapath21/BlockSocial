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

    /** A live stream playing (its screen, the mini player or PiP), with what to show about it. */
    data class Live(val address: String, val title: String, val host: String)
    var live: Live? by mutableStateOf(null)
        private set

    /** The live stream whose screen is open right now (the mini player hides then). */
    var liveScreenOpen: String? by mutableStateOf(null)

    /** Sound off, kept across the stream's screen, the mini player and PiP. */
    var muted by mutableStateOf(false)
        private set

    fun mute(on: Boolean) {
        muted = on
        player?.volume = if (on) 0f else 1f
    }

    /**
     * Play or pause. A live stream picks up at the live edge again rather than where it was
     * paused (it would otherwise run behind the chat), unless [catchUp] is false: the stream's
     * own controls resume where you paused, with LIVE to catch up.
     */
    fun togglePlay(catchUp: Boolean = true) {
        val p = player ?: return
        if (p.isPlaying) p.pause()
        else {
            if (live != null && catchUp) p.seekToDefaultPosition()
            p.play()
        }
    }

    /** Shrinks the app into a PiP window now (set by MainActivity; null where PiP isn't available). */
    var requestPip: (() -> Unit)? = null

    /** A video dragged down out of the full-screen viewer, playing on in the mini player. */
    data class MiniVideo(val url: String, val note: com.example.btcwidget.nostr.core.Event, val title: String, val author: String)
    var miniVideo: MiniVideo? by mutableStateOf(null)

    /** When a video was last dragged down into the mini player: its screen then fades out instead of sliding. */
    @Volatile var minimizedAt = 0L
    /** The screen being left was just dragged down into the mini player. */
    val leavingForMini: Boolean get() = System.currentTimeMillis() - minimizedAt < 800

    /**
     * The in-app mini player shows: a live stream still playing after its screen was left, or a
     * video dragged down out of the full-screen viewer (as on YouTube).
     */
    val showMini: Boolean get() = player != null && !inPip &&
        (live != null && liveScreenOpen != live?.address || live == null && miniVideo != null)

    /** The player for live stream [info] (the one already playing it, else a new one). */
    fun acquireLive(context: Context, url: String, info: Live, create: (Context, String) -> ExoPlayer): ExoPlayer =
        acquire(context, url, create).also { live = info; it.volume = if (muted) 0f else 1f }

    /** Whether leaving the app should shrink it to PiP: a full-screen video that is playing. */
    val wantsPip: Boolean get() = player?.let { it.isPlaying || it.playWhenReady && it.playbackState != Player.STATE_ENDED } == true

    /** The player for [url] (the one already playing it if it's open, else a new one). */
    fun acquire(context: Context, url: String, create: (Context, String) -> ExoPlayer): ExoPlayer {
        player?.let { if (this.url == url) return it }
        release()
        closedInPip = false
        live = null
        // A new video starts with sound (muting is per stream).
        muted = false
        return create(context, url).also { player = it; this.url = url }
    }

    /** Ends the full-screen video. Hiding the viewer for PiP doesn't call this. */
    fun release(save: (String, Long) -> Unit = { _, _ -> }) {
        val p = player ?: return
        url?.let { save(it, p.currentPosition) }
        p.release()
        player = null
        url = null
        live = null
        miniVideo = null
    }
}
