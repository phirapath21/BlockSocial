package com.example.btcwidget.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.example.btcwidget.ChatKey
import com.example.btcwidget.LoginKey
import com.example.btcwidget.ProfileKey
import com.example.btcwidget.nostr.DirectMessages
import com.example.btcwidget.nostr.NostrRepository
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk
import kotlinx.coroutines.launch

/** Conversations list (the Messages tab). */
@Composable
fun MessagesScreen(vm: SocialViewModel, navigate: (NavKey) -> Unit) {
    val account by vm.account.collectAsState()
    val conversations by vm.dmConversations.collectAsState()
    val loading by vm.dmLoading.collectAsState()
    val locked by vm.dmLocked.collectAsState()
    val inbox by vm.dmInbox.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val unread by vm.unreadConversations.collectAsState()
    var newMessage by remember { mutableStateOf(false) }
    LaunchedEffect(account) { vm.startDms() }

    val me = account?.pubkey
    if (me == null) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Private messages", color = EInk.Value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "End-to-end encrypted (NIP-17): relays can't see who you talk to or what you say.",
                color = EInk.Label, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )
            PillButton("Log in") { navigate(LoginKey) }
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            if (inbox?.isEmpty() == true) item(key = "inbox") {
                Banner(
                    "Set up your inbox so people can message you. This publishes which relays receive your private messages.",
                    "Set up", vm::setUpDmInbox
                )
            }
            if (locked > 0) item(key = "locked") {
                Banner("$locked messages need your signer app's permission to open.", "Allow", vm::unlockDms)
            }
            if (conversations.isEmpty()) item(key = "empty") {
                if (loading) Spinner(Modifier.padding(top = 40.dp))
                else EmptyNote("No messages yet. Start one with ✉, or from someone's profile.")
            }
            items(conversations, key = { it.key }) { c ->
                ConversationRow(c, me, profiles, unread = c.key in unread, onClick = { navigate(ChatKey(c.key)) })
            }
        }
        FloatingActionButton(
            onClick = { newMessage = true },
            containerColor = EInk.Value,
            contentColor = EInk.Background,
            shape = CircleShape,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Text("✉", fontSize = 22.sp) }
    }

    if (newMessage) NewMessageDialog(vm, onDismiss = { newMessage = false }, onOpen = { pubkey ->
        newMessage = false
        navigate(ChatKey(pubkey))
    })
}

@Composable
private fun Banner(text: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(EInk.Body).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = EInk.Label, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(end = 10.dp))
        PillButton(action, onClick = onClick)
    }
}

@Composable
private fun ConversationRow(c: DirectMessages.Conversation, me: String, profiles: Map<String, Profile>, unread: Boolean, onClick: () -> Unit) {
    val names = c.peers.joinToString(", ") { profiles[it]?.bestName ?: Profile.shortNpub(it) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Avatar(profiles[c.peers.first()], c.peers.first(), size = 46)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(names, color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(relativeTime(c.last.createdAt), color = if (unread) EInk.Value else EInk.Caption, fontSize = 13.sp)
                if (unread) {
                    Box(Modifier.padding(start = 6.dp).size(9.dp).background(EInk.Alert, CircleShape))
                }
            }
            Text(
                (if (c.last.sender == me) "You: " else "") + c.last.content.replace('\n', ' '),
                color = if (unread) EInk.Value else EInk.Label, fontSize = 14.sp,
                fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
}

/** Start a conversation: an npub, nprofile or name@domain. */
@Composable
private fun NewMessageDialog(vm: SocialViewModel, onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New message") },
        text = {
            Column {
                OutlinedTextField(
                    value = input, onValueChange = { input = it; error = null },
                    label = { Text("npub or name@domain") }, singleLine = true, isError = error != null
                )
                Text(
                    error ?: "Or open someone's profile and tap ✉.",
                    color = if (error != null) EInk.Alert else EInk.Caption, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && input.isNotBlank(), onClick = {
                busy = true
                scope.launch {
                    val target = vm.resolve(input)
                    busy = false
                    if (target is NostrRepository.Direct.ProfileTarget) onOpen(target.pubkey)
                    else error = "Couldn't find that person. Try their npub."
                }
            }) { Text(if (busy) "…" else "Open") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** A conversation with [peers]: bubbles, newest at the bottom, and a composer. */
@Composable
fun ChatScreen(vm: SocialViewModel, peers: List<String>, navigate: (NavKey) -> Unit, onBack: () -> Unit) {
    val account by vm.account.collectAsState()
    val all by vm.dmMessages.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val me = account?.pubkey
    val key = remember(peers) { peers.sorted() }
    val messages = remember(all, me, key) { if (me == null) emptyList() else all.filter { it.peers(me) == key } }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        vm.startDms()
        vm.loadProfiles(peers)
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size)
            // Open on screen: everything here is read.
            vm.markConversationRead(key.joinToString(","), messages.maxOf { it.createdAt })
        }
    }

    val title = peers.joinToString(", ") { profiles[it]?.bestName ?: Profile.shortNpub(it) }
    Column(Modifier.fillMaxSize().background(EInk.Background).imePadding()) {
        SocialTopBar(title, onBack)
        SocialScaffold(vm, navigate) { _ ->
            Column(Modifier.fillMaxSize()) {
                LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
                    item(key = "head") {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 20.dp).clickable { navigate(ProfileKey(peers.first())) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Avatar(profiles[peers.first()], peers.first(), size = 56)
                            Text(title, color = EInk.Value, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                            Text("🔒 End-to-end encrypted (NIP-17)", color = EInk.Caption, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    items(messages, key = { it.id }) { m ->
                        val mine = m.sender == me
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                            Column(
                                Modifier
                                    .widthIn(max = 290.dp)
                                    .background(if (mine) EInk.Value else EInk.Body, RoundedCornerShape(18.dp))
                                    .padding(horizontal = 14.dp, vertical = 9.dp)
                            ) {
                                if (!mine && peers.size > 1) {
                                    Text(profiles[m.sender]?.bestName ?: Profile.shortNpub(m.sender), color = EInk.Caption, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(m.content, color = if (mine) EInk.Background else EInk.Value, fontSize = 15.sp)
                                Text(
                                    relativeTime(m.createdAt), fontSize = 11.sp,
                                    color = if (mine) EInk.Background.copy(alpha = 0.7f) else EInk.Caption,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text, onValueChange = { text = it },
                        placeholder = { Text("Message") },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EInk.Value, unfocusedBorderColor = EInk.BodyStroke,
                            focusedTextColor = EInk.Value, unfocusedTextColor = EInk.Value, cursorColor = EInk.Value,
                            focusedPlaceholderColor = EInk.Caption, unfocusedPlaceholderColor = EInk.Caption
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    PillButton(if (sending) "…" else "Send", filled = text.isNotBlank() && !sending) {
                        if (text.isBlank() || sending || me == null) return@PillButton
                        sending = true
                        val outgoing = text
                        text = ""
                        vm.sendDm(peers, outgoing) { ok ->
                            sending = false
                            if (!ok) text = outgoing
                        }
                    }
                }
            }
        }
    }
}
