package com.example.btcwidget.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.nostr.RelayStatus
import com.example.btcwidget.ui.main.EInk
import kotlinx.coroutines.delay

private val Green = Color(0xFF3FB950)
private val Orange = Color(0xFFF7931A)
private val Red = Color(0xFFE5534B)

/**
 * How each relay connection is doing, refreshed every second: your relays first, then the
 * ones opened to read people who post elsewhere (outbox). Tap a failing relay to retry now.
 */
@Composable
fun RelaysScreen(vm: SocialViewModel, onBack: () -> Unit) {
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    var info by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(EInk.Background)) {
        SocialTopBar("Relays", onBack)
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            listOf("STATUS", "MY RELAYS", "SEARCH", "BLOCKED").forEachIndexed { i, t ->
                com.example.btcwidget.ui.main.EInkChip(text = t, selected = tab == i, onClick = { tab = i })
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
        when (tab) {
            0 -> RelayStatusTab(vm, onInfo = { info = it })
            1 -> MyRelaysTab(vm, onInfo = { info = it })
            2 -> RelaySetTab(vm, blocked = false, onInfo = { info = it })
            else -> RelaySetTab(vm, blocked = true, onInfo = { info = it })
        }
    }
    info?.let { RelayInfoSheet(vm, it, onDismiss = { info = null }) }
}

/** Live connection state of every relay, refreshed every second. */
@Composable
private fun RelayStatusTab(vm: SocialViewModel, onInfo: (String) -> Unit) {
    var status by remember { mutableStateOf(vm.relayStatus()) }
    var showOthers by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) { status = vm.relayStatus(); delay(1_000) }
    }
    val mine = status.filter { it.primary }
    val others = status.filter { !it.primary }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "summary") {
                val up = mine.count { it.state == RelayStatus.State.CONNECTED }
                Column(Modifier.padding(16.dp)) {
                    Text("$up of ${mine.size} connected", color = EInk.Value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            mine.isEmpty() -> "No relays set."
                            up == 0 -> "Block can't reach any of your relays. Check your connection."
                            up < mine.size -> "Some relays are down; the rest keep things working."
                            else -> "All your relays are working."
                        },
                        color = EInk.Caption, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)
                    )
                    Text("Tap a relay to see what it says about itself.", color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                }
                HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
            }
            items(mine, key = { "m" + it.url }) { RelayRow(it, onInfo = { onInfo(it.url) }) { vm.retryRelay(it.url) } }
            if (others.isNotEmpty()) item(key = "others") {
                Row(
                    Modifier.fillMaxWidth().clickable { showOthers = !showOthers }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Other relays (${others.count { it.state == RelayStatus.State.CONNECTED }}/${others.size})", color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Opened to read people who post on their own relays", color = EInk.Caption, fontSize = 13.sp)
                    }
                    Text(if (showOthers) "⌃" else "⌄", color = EInk.Value, fontSize = 18.sp)
                }
                HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
            }
            if (showOthers) items(others, key = { "o" + it.url }) { RelayRow(it, onInfo = { onInfo(it.url) }) { vm.retryRelay(it.url) } }
        }
    }
}

@Composable
private fun RelayRow(s: RelayStatus, onInfo: () -> Unit, onRetry: () -> Unit) {
    val (color, label) = when (s.state) {
        RelayStatus.State.CONNECTED -> Green to "Connected" + (s.connectMs?.let { " · ${it} ms" } ?: "")
        RelayStatus.State.CONNECTING -> Orange to "Connecting…"
        RelayStatus.State.RETRYING -> Orange to "Retrying (${s.failures} failed)"
        RelayStatus.State.FAILED -> Red to if (s.failures > 0) "Not reachable (${s.failures} tries)" else "Not reachable"
    }
    val canRetry = s.state == RelayStatus.State.RETRYING || (s.state == RelayStatus.State.FAILED && s.failures > 0)
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "About this relay", onClick = onInfo).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(s.url.removePrefix("wss://").removeSuffix("/"), color = EInk.Value, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                label + (if (s.events > 0) " · ${s.events} messages" else ""),
                color = EInk.Caption, fontSize = 13.sp, maxLines = 1
            )
            if (s.state != RelayStatus.State.CONNECTED) s.lastError?.let {
                Text(it, color = EInk.Caption, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (canRetry) Text(
            "Retry", color = EInk.Value, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(EInk.Track).clickable(onClick = onRetry).padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
}
