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
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    intent.getStringExtra(com.example.btcwidget.nostr.NostrBackground.EXTRA_OPEN_TAB)?.let { OpenTabRequest.tab.value = it }
  }

  override fun onDestroy() {
    SignerBridge.launch = null
    super.onDestroy()
  }
}
