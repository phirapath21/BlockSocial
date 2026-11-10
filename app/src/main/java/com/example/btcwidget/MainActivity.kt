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
import androidx.compose.ui.Modifier
import com.example.btcwidget.theme.BTCWidgetTheme
import com.example.btcwidget.ui.main.EInk

class MainActivity : ComponentActivity() {
  /** Results from Nostr signer apps (NIP-55), handed to the waiting signer. */
  private val signerLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { SignerBridge.onResult(it) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    SignerBridge.launch = { signerLauncher.launch(it) }
    EInk.theme = AppPrefs.theme(this)

    // The e-ink UI is always dark, so keep light system bar icons regardless of system theme.
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    )
    setContent {
      BTCWidgetTheme(darkTheme = true, dynamicColor = false) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() }
      }
    }
  }

  override fun onDestroy() {
    SignerBridge.launch = null
    super.onDestroy()
  }
}
