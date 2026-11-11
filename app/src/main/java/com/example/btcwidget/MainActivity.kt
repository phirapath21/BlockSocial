package com.example.btcwidget

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.example.btcwidget.nostr.SignerBridge
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.btcwidget.theme.BTCWidgetTheme
import com.example.btcwidget.ui.main.EInk

/** A tab to open, set from a tapped notification and consumed by the main screen. */
object OpenTabRequest {
  val tab = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
}

private const val PIP_CMD = "cmd"
private const val PIP_TOGGLE = "toggle"
private const val PIP_MUTE = "mute"

class MainActivity : ComponentActivity() {
  /** Results from Nostr signer apps (NIP-55), handed to the waiting signer. */
  private val signerLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { SignerBridge.onResult(it) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    CrashLog.install(this)
    SignerBridge.launch = { signerLauncher.launch(it) }
    intent?.getStringExtra(com.example.btcwidget.nostr.NostrBackground.EXTRA_OPEN_TAB)?.let { OpenTabRequest.tab.value = it }
    EInk.theme = AppPrefs.theme(this)
    // PiP: the stream's own PiP button, and the PiP window's play/pause and mute buttons.
    if (hasPip()) {
      com.example.btcwidget.ui.social.PipVideo.requestPip = {
        if (android.os.Build.VERSION.SDK_INT >= 26) runCatching { pipParams()?.let { enterPictureInPictureMode(it) } }
      }
      androidx.core.content.ContextCompat.registerReceiver(
        this, pipReceiver, android.content.IntentFilter(pipAction), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
      )
      pipReceiverOn = true
    }

    // The e-ink UI is always dark, so keep light system bar icons regardless of system theme.
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    )
    setContent {
      BTCWidgetTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          // After a crash, show what happened (to copy into a bug report) before the app.
          var crash by remember { mutableStateOf(CrashLog.read(this)) }
          val report = crash
          if (report != null) {
            CrashReportScreen(report, onContinue = { CrashLog.clear(this); crash = null })
          } else {
            MainNavigation()
            PipOverlay()
          }
        }
      }
    }
  }

  /**
   * Picture-in-Picture for the full-screen video. The PiP window shows only this activity (not
   * the viewer's own window), so while in PiP it draws the video over everything; the app keeps
   * its place underneath. Keeps Android's PiP settings in step with the video playing.
   */
  @androidx.compose.runtime.Composable
  private fun PipOverlay() {
    val player = com.example.btcwidget.ui.social.PipVideo.player
    androidx.compose.runtime.DisposableEffect(player) {
      updatePipParams()
      val listener = object : androidx.media3.common.Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = updatePipParams()
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = updatePipParams()
        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) = updatePipParams()
      }
      player?.addListener(listener)
      onDispose { player?.removeListener(listener); updatePipParams() }
    }
    // The mute button in the PiP window shows the current state.
    val muted = com.example.btcwidget.ui.social.PipVideo.muted
    val live = com.example.btcwidget.ui.social.PipVideo.live
    androidx.compose.runtime.LaunchedEffect(muted, live) { updatePipParams() }
    if (com.example.btcwidget.ui.social.PipVideo.inPip && player != null) {
      androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)
      ) {
        com.example.btcwidget.ui.social.VideoSurface(player, Modifier.fillMaxSize())
        if (live != null) com.example.btcwidget.ui.social.LiveBadge(
          Modifier.align(androidx.compose.ui.Alignment.TopStart)
            .padding(androidx.compose.ui.unit.Dp(6f))
        )
      }
    }
  }

  private fun hasPip() = android.os.Build.VERSION.SDK_INT >= 26 &&
    packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)

  private val pipAction get() = "$packageName.PIP_CONTROL"
  private var pipReceiverOn = false

  /** The PiP window's buttons (only this app can send these: the receiver isn't exported). */
  private val pipReceiver = object : android.content.BroadcastReceiver() {
    override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
      val pip = com.example.btcwidget.ui.social.PipVideo
      when (intent.getStringExtra(PIP_CMD)) {
        PIP_TOGGLE -> pip.togglePlay()
        PIP_MUTE -> pip.mute(!pip.muted)
      }
      updatePipParams()
    }
  }

  @androidx.annotation.RequiresApi(26)
  private fun pipButton(cmd: String, code: Int, icon: Int, title: String): android.app.RemoteAction {
    val intent = android.content.Intent(pipAction).setPackage(packageName).putExtra(PIP_CMD, cmd)
    val pending = android.app.PendingIntent.getBroadcast(
      this, code, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
    )
    return android.app.RemoteAction(android.graphics.drawable.Icon.createWithResource(this, icon), title, title, pending)
  }

  private fun pipParams(): android.app.PictureInPictureParams? {
    if (android.os.Build.VERSION.SDK_INT < 26) return null
    val size = com.example.btcwidget.ui.social.PipVideo.player?.videoSize
    // PiP accepts shapes between 1:2.39 and 2.39:1.
    val ratio = if (size != null && size.width > 0 && size.height > 0)
      android.util.Rational(size.width, size.height).let { r ->
        when {
          r.toFloat() > 2.39f -> android.util.Rational(239, 100)
          r.toFloat() < 1 / 2.39f -> android.util.Rational(100, 239)
          else -> r
        }
      } else android.util.Rational(16, 9)
    val pip = com.example.btcwidget.ui.social.PipVideo
    val playing = pip.player?.let { it.isPlaying || it.playWhenReady } == true
    val b = android.app.PictureInPictureParams.Builder().setAspectRatio(ratio).setActions(
      listOf(
        if (playing) pipButton(PIP_TOGGLE, 1, android.R.drawable.ic_media_pause, "Pause")
        else pipButton(PIP_TOGGLE, 1, android.R.drawable.ic_media_play, "Play"),
        if (pip.muted) pipButton(PIP_MUTE, 2, android.R.drawable.ic_lock_silent_mode, "Sound on")
        else pipButton(PIP_MUTE, 2, android.R.drawable.ic_lock_silent_mode_off, "Mute"),
      )
    )
    if (android.os.Build.VERSION.SDK_INT >= 33 && pip.live != null) b.setTitle(pip.live?.title).setSubtitle(pip.live?.host)
    // Android 12+: shrink to PiP by itself when the user leaves while the video plays.
    if (android.os.Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(com.example.btcwidget.ui.social.PipVideo.wantsPip).setSeamlessResizeEnabled(true)
    return b.build()
  }

  private fun updatePipParams() {
    if (hasPip()) {
      runCatching { pipParams()?.let { setPictureInPictureParams(it) } }
    }
  }

  /** Before Android 12, leaving the app (Home) while the full-screen video plays enters PiP. */
  override fun onUserLeaveHint() {
    super.onUserLeaveHint()
    if (android.os.Build.VERSION.SDK_INT in 26..30 && com.example.btcwidget.ui.social.PipVideo.wantsPip &&
      packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
    ) {
      runCatching { pipParams()?.let { enterPictureInPictureMode(it) } }
    }
  }

  override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
    super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    com.example.btcwidget.ui.social.PipVideo.inPip = isInPictureInPictureMode
    // Leaving PiP while not on screen means the PiP window was closed (✕ or swiped away):
    // the video ends. (Tapping it to go back to full size leaves PiP while started.)
    if (!isInPictureInPictureMode && !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
      com.example.btcwidget.ui.social.PipVideo.release { url, ms -> com.example.btcwidget.ui.social.VideoPositions[url] = ms }
      com.example.btcwidget.ui.social.PipVideo.closedInPip = true
    }
  }

  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    intent.getStringExtra(com.example.btcwidget.nostr.NostrBackground.EXTRA_OPEN_TAB)?.let { OpenTabRequest.tab.value = it }
  }

  override fun onDestroy() {
    SignerBridge.launch = null
    com.example.btcwidget.ui.social.PipVideo.requestPip = null
    if (pipReceiverOn) runCatching { unregisterReceiver(pipReceiver) }
    pipReceiverOn = false
    super.onDestroy()
  }
}
