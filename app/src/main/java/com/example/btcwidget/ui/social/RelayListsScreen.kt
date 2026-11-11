package com.example.btcwidget.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.nostr.core.RelayInfo
import com.example.btcwidget.nostr.core.RelayLists
import com.example.btcwidget.nostr.core.normalizeRelayUrl
import com.example.btcwidget.ui.main.EInk
import com.example.btcwidget.ui.main.EInkChip

/**
 * Your NIP-65 relays: where you write (others read your notes there) and where you read (others
 * send you replies and mentions there). Edit, then Publish.
 */
@Composable
internal fun MyRelaysTab(vm: SocialViewModel, onInfo: (String) -> Unit) {
    val account by vm.account.collectAsState()
    val published by vm.relayEntries.collectAsState()
    var entries by remember(published) { mutableStateOf(published) }
    val changed = entries != published
    if (account == null) { EmptyNote("Log in to edit your relay list."); return }
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "about") {
            Text(
                "Your relay list (NIP-65) tells other apps where to find your notes (write) and where to send you replies and mentions (read). Block also reads from them.",
                color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.padding(16.dp)
            )
        }
        items(entries, key = { it.url }) { e ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    e.url.removePrefix("wss://"), color = EInk.Value, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClickLabel = "About this relay") { onInfo(e.url) }
                )
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    fun set(read: Boolean, write: Boolean) { entries = entries.map { if (it.url == e.url) it.copy(read = read, write = write) else it } }
                    EInkChip(text = (if (e.read && e.write) "✓ " else "") + "BOTH", selected = e.read && e.write, onClick = { set(true, true) })
                    EInkChip(text = (if (e.read && !e.write) "✓ " else "") + "READ", selected = e.read && !e.write, onClick = { set(true, false) })
                    EInkChip(text = (if (!e.read && e.write) "✓ " else "") + "WRITE", selected = !e.read && e.write, onClick = { set(false, true) })
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Remove", color = EInk.Alert, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { entries = entries.filter { it.url != e.url } }.padding(6.dp)
                    )
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
        }
        item(key = "add") {
            AddRelayRow(onAdd = { url -> if (entries.none { it.url == url }) entries = entries + RelayLists.Entry(url, read = true, write = true) })
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(if (changed) "Publish" else "Published", filled = changed) { if (changed) vm.saveRelayList(entries) }
                if (changed) PillButton("Undo", filled = false) { entries = published }
            }
            if (entries.isEmpty()) Text(
                "With no relays here, other apps can't find your notes reliably. Add two or three.",
                color = EInk.Alert, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

/** Search relays (kind 10007) or blocked relays (kind 10006): a list of addresses. */
@Composable
internal fun RelaySetTab(vm: SocialViewModel, blocked: Boolean, onInfo: (String) -> Unit) {
    val account by vm.account.collectAsState()
    val published by (if (blocked) vm.blockedRelays else vm.searchRelays).collectAsState()
    var urls by remember(published, blocked) { mutableStateOf(published) }
    val changed = urls != published
    if (account == null) { EmptyNote("Log in to edit your relay lists."); return }
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "about") {
            Text(
                if (blocked) "Block never connects to these relays, even to read someone who posts there. The list is public (NIP-51) so your other apps can use it."
                else "Search (people, notes, hashtags) asks these relays. Empty: Block's defaults, relay.nostr.band and search.nos.today.",
                color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.padding(16.dp)
            )
        }
        items(urls, key = { it }) { url ->
            Row(Modifier.fillMaxWidth().clickable { onInfo(url) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(url.removePrefix("wss://"), color = EInk.Value, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("Remove", color = EInk.Alert, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { urls = urls - url }.padding(6.dp))
            }
            HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
        }
        item(key = "add") {
            AddRelayRow(onAdd = { url -> if (url !in urls) urls = urls + url })
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(if (changed) "Publish" else "Published", filled = changed) {
                    if (changed) { if (blocked) vm.saveBlockedRelays(urls) else vm.saveSearchRelays(urls) }
                }
                if (changed) PillButton("Undo", filled = false) { urls = published }
            }
        }
    }
}

@Composable
private fun AddRelayRow(onAdd: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it.trim(); error = null }, singleLine = true,
                placeholder = { Text("relay.example.com") }, isError = error != null, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            EInkChip(text = "ADD", selected = input.isNotBlank(), onClick = {
                val url = normalizeRelayUrl(if (input.startsWith("ws")) input else "wss://$input")
                if (url == null) error = "Not a relay address" else { onAdd(url); input = "" }
            })
        }
        error?.let { Text(it, color = EInk.Alert, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
    }
}

/** What a relay says about itself (NIP-11): name, description, operator, rules and supported NIPs. */
@Composable
internal fun RelayInfoSheet(vm: SocialViewModel, url: String, onDismiss: () -> Unit) {
    val profiles by vm.profiles.collectAsState()
    val info by produceState<Any?>(Unit, url) { value = vm.relayInfo(url) }
    BlockSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(url.removePrefix("wss://"), color = EInk.Caption, fontSize = 13.sp)
            when (val i = info) {
                Unit -> Spinner(Modifier.padding(top = 20.dp))
                !is RelayInfo -> Text("This relay doesn't describe itself (no NIP-11 page).", color = EInk.Label, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
                else -> {
                    LaunchedEffect(i.operator) { i.operator?.let { vm.loadProfiles(listOf(it)) } }
                    Text(i.name ?: "Relay", color = EInk.Value, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    i.description?.let { Text(it, color = EInk.Label, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp)) }
                    FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (i.paymentRequired) Tag("Paid")
                        if (i.authRequired) Tag("Login required")
                        if (i.restrictedWrites) Tag("Restricted writing")
                        if (!i.paymentRequired && !i.authRequired && !i.restrictedWrites) Tag("Open")
                    }
                    i.operator?.let { op -> Line("Run by", profiles[op]?.bestName ?: Profile.shortNpub(op)) }
                    i.contact?.let { Line("Contact", it) }
                    (listOfNotNull(i.software?.substringAfterLast('/'), i.version).joinToString(" ").ifBlank { null })?.let { Line("Software", it) }
                    if (i.nips.isNotEmpty()) Line("Supports NIPs", i.nips.joinToString(", "))
                    i.paymentsUrl?.let { Line("Payments", it) }
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String) {
    Text(text, color = EInk.Value, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(50)).background(EInk.Track).padding(horizontal = 10.dp, vertical = 4.dp))
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.padding(top = 10.dp)) {
        Text(label, color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.width(110.dp))
        Text(value, color = EInk.Value, fontSize = 13.sp)
    }
}
