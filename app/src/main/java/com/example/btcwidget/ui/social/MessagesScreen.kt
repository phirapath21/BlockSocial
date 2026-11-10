package com.example.btcwidget.ui.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.ui.main.EInk

/** Direct messages tab. Private messaging (NIP-17) isn't built yet; this says so plainly. */
@Composable
fun MessagesScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("✉", color = EInk.Value, fontSize = 40.sp)
        Text(
            "Direct messages are coming",
            color = EInk.Value, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            "Private, end-to-end encrypted messages (NIP-17) will appear here. Until then, reply or mention people in public posts.",
            color = EInk.Label, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp)
        )
    }
}
