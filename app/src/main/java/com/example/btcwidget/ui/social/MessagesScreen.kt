package com.example.btcwidget.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
    val following by vm.following.collectAsState()
    val allMessages by vm.dmMessages.collectAsState()
    var newMessage by remember { mutableStateOf(false) }
    var newGroup by remember { mutableStateOf(false) }
    var showRequests by remember { mutableStateOf(false) }
    LaunchedEffect(account) { vm.startDms() }

    val me = account?.pubkey
    if (me == null) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(tr("Private messages"), color = EInk.Value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                tr("End-to-end encrypted (NIP-17): relays can't see who you talk to or what you say."),
                color = EInk.Label, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )
            PillButton(tr("Log in")) { navigate(LoginKey) }
        }
        return
    }

    // Message requests: chats with people you don't follow, until you reply.
    val answered = remember(allMessages, me) { allMessages.filter { it.sender == me }.map { it.peers(me).joinToString(",") }.toSet() }
    val (requests, chats) = remember(conversations, following, answered) {
        conversations.partition { c -> c.key !in answered && c.peers.none { it in following } }
    }
    val shown = if (showRequests) requests else chats

    // Tap Messages again: back to the top; at the top, check for new messages.
    val listState = vm.listState("messages")
    ScrollToTopOnTabTap("MESSAGES", listState) { vm.startDms() }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            if (requests.isNotEmpty() || showRequests) item(key = "tabs") {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.example.btcwidget.ui.main.EInkChip(text = "CHATS", selected = !showRequests, onClick = { showRequests = false })
                    com.example.btcwidget.ui.main.EInkChip(text = tr("REQUESTS {0}", requests.size), selected = showRequests, onClick = { showRequests = true })
                }
                if (showRequests) Text(
                    tr("From people you don't follow. Replying moves a chat to Chats. Links and photos here can come from strangers."),
                    color = EInk.Caption, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                )
                HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
            }
            if (inbox?.isEmpty() == true) item(key = "inbox") {
                Banner(
                    "Set up your inbox so people can message you. This publishes which relays receive your private messages.",
                    "Set up", vm::setUpDmInbox
                )
            }
            if (locked > 0) item(key = "locked") {
                Banner("$locked messages need your signer app's permission to open.", "Allow", vm::unlockDms)
            }
            if (shown.isEmpty()) item(key = "empty") {
                if (loading) Spinner(Modifier.padding(top = 40.dp))
                else EmptyNote(if (showRequests) tr("No message requests.") else tr("No messages yet. Start one with ✉, or from someone's profile."))
            }
            items(shown, key = { it.key }) { c ->
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
    }, onNewGroup = { newMessage = false; newGroup = true })
    if (newGroup) NewGroupDialog(vm, onDismiss = { newGroup = false }, onStart = { peers ->
        newGroup = false
        navigate(ChatKey(peers.sorted().joinToString(",")))
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
                (if (c.last.sender == me) "You: " else "") + (if (c.last.kind == com.example.btcwidget.nostr.core.Nip17.FILE) "📷 Photo" else c.last.content.replace('\n', ' ')),
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
private fun NewMessageDialog(vm: SocialViewModel, onDismiss: () -> Unit, onOpen: (String) -> Unit, onNewGroup: () -> Unit) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    BlockDialog(
        onDismiss = onDismiss,
        title = tr("New message"),
        confirmLabel = if (busy) tr("Looking…") else tr("Open chat"),
        confirmEnabled = !busy && input.isNotBlank(),
        onConfirm = {
            busy = true
            scope.launch {
                val target = vm.resolve(input)
                busy = false
                if (target is NostrRepository.Direct.ProfileTarget) onOpen(target.pubkey)
                else error = tr("Couldn't find that person. Try their npub.")
            }
        }
    ) {
        OutlinedTextField(
            value = input, onValueChange = { input = it; error = null },
            label = { Text(tr("npub or name@domain")) }, singleLine = true, isError = error != null,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            error ?: tr("Or open someone's profile and tap ✉."),
            color = if (error != null) EInk.Alert else EInk.Caption, fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            tr("New group chat ›"), color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 10.dp).clickable(onClick = onNewGroup).padding(vertical = 6.dp)
        )
    }
}

/** Pick people you follow (or paste npubs) for a group chat. */
@Composable
private fun NewGroupDialog(vm: SocialViewModel, onDismiss: () -> Unit, onStart: (List<String>) -> Unit) {
    val following by vm.following.collectAsState()
    val profiles by vm.profiles.collectAsState()
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(listOf<String>()) }
    LaunchedEffect(following) { vm.loadProfiles(following.take(300)) }
    val q = query.trim().lowercase()
    val pasted = remember(q) { (com.example.btcwidget.nostr.core.Nip19.decode(q) as? com.example.btcwidget.nostr.core.Nip19.Pubkey)?.hex }
    val matches = remember(q, following, profiles) {
        following.filter { pk ->
            q.isEmpty() || profiles[pk]?.let { p -> listOfNotNull(p.name, p.displayName, p.nip05).any { it.lowercase().contains(q) } } == true
        }.sortedBy { profiles[it]?.bestName?.lowercase() ?: "~" }.take(40)
    }
    BlockDialog(
        onDismiss = onDismiss,
        title = tr("New group chat"),
        confirmLabel = if (picked.size >= 2) tr("Start with {0} people", picked.size) else tr("Pick at least 2 people"),
        confirmEnabled = picked.size >= 2,
        onConfirm = { onStart(picked) }
    ) {
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text(tr("Search follows or paste an npub")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (picked.isNotEmpty()) Text(
            picked.joinToString(", ") { profiles[it]?.bestName ?: Profile.shortNpub(it) },
            color = EInk.Value, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)
        )
        // A plain column (at most 40 rows): the dialog itself scrolls.
        Column(Modifier.padding(top = 6.dp)) {
            (listOfNotNull(pasted) + matches).distinct().forEach { pk ->
                val on = pk in picked
                Row(
                    Modifier.fillMaxWidth().clickable { picked = if (on) picked - pk else (picked + pk).take(MAX_GROUP) }.padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(profiles[pk], pk, size = 32)
                    Text(profiles[pk]?.bestName ?: Profile.shortNpub(pk), color = EInk.Value, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 10.dp), maxLines = 1)
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).background(if (on) androidx.compose.ui.graphics.Color(0xFFF7931A) else EInk.Track),
                        contentAlignment = Alignment.Center
                    ) { if (on) Text("✓", color = androidx.compose.ui.graphics.Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        Text(tr("Everyone in the group sees each other and every message. Up to {0} people.", MAX_GROUP), color = EInk.Caption, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

/** Each message is wrapped once per member, so groups stay small. */
private const val MAX_GROUP = 10

/** Emoji offered when reacting to a message. */
private val DM_REACTIONS = listOf("❤️", "😂", "👍", "🔥", "😮", "😢", "🙏")

/** A conversation with [peers]: bubbles, newest at the bottom, and a composer. */
@Composable
fun ChatScreen(vm: SocialViewModel, peers: List<String>, navigate: (NavKey) -> Unit, onBack: () -> Unit) {
    val account by vm.account.collectAsState()
    val all by vm.dmMessages.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val me = account?.pubkey
    val key = remember(peers) { peers.sorted() }
    val inChat = remember(all, me, key) { if (me == null) emptyList() else all.filter { it.peers(me) == key } }
    val messages = remember(inChat) { inChat.filter { it.isBubble } }
    // Reactions grouped under the message they react to (each person's latest counts).
    val reactions = remember(inChat) {
        inChat.filter { it.kind == com.example.btcwidget.nostr.core.Nip17.REACTION && it.replyTo != null }
            .groupBy { it.replyTo!! }
            .mapValues { (_, rs) -> rs.groupBy { it.sender }.map { (_, mine) -> mine.maxBy { it.createdAt }.content } }
    }
    val byId = remember(messages) { messages.associateBy { it.id } }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val sendHaptics = rememberHaptics()
    var replyTo by remember { mutableStateOf<com.example.btcwidget.nostr.core.Nip17.Message?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var photoSending by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            photoSending = true
            vm.sendDmImage(peers, uri) { photoSending = false }
        }
    }
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
                            Text(tr("🔒 End-to-end encrypted (NIP-17)"), color = EInk.Caption, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                            if (messages.any { it.isLegacy }) Text(
                                tr("Older messages marked \"old format\" use NIP-04: relays could see who talked and when."),
                                color = EInk.Caption, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, start = 20.dp, end = 20.dp)
                            )
                        }
                    }
                    items(messages, key = { it.id }) { m ->
                        val mine = m.sender == me
                        val fg = if (mine) EInk.Background else EInk.Value
                        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                            Box {
                                Column(
                                    Modifier
                                        .widthIn(max = 290.dp)
                                        .background(if (mine) EInk.Value else EInk.Body, RoundedCornerShape(18.dp))
                                        .combinedClickable(onClick = {}, onLongClickLabel = "Message options", onLongClick = { menuFor = m.id })
                                        .padding(horizontal = 14.dp, vertical = 9.dp)
                                ) {
                                    if (!mine && peers.size > 1) {
                                        Text(profiles[m.sender]?.bestName ?: Profile.shortNpub(m.sender), color = EInk.Caption, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    m.replyTo?.let { byId[it] }?.let { parent ->
                                        Text(
                                            "↩ " + (profiles[parent.sender]?.bestName ?: Profile.shortNpub(parent.sender)) + ": " +
                                                (if (parent.kind == com.example.btcwidget.nostr.core.Nip17.FILE) "📷 Photo" else parent.content.replace('\n', ' ')),
                                            color = fg.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                    }
                                    val file = m.file
                                    if (file != null) EncryptedPhoto(vm, file)
                                    else Text(m.content, color = fg, fontSize = 15.sp)
                                    Text(
                                        relativeTime(m.createdAt) + if (m.isLegacy) tr(" · old format") else "", fontSize = 11.sp,
                                        color = if (mine) EInk.Background.copy(alpha = 0.7f) else EInk.Caption,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                if (menuFor == m.id) ActionSheet(
                                    onDismiss = { menuFor = null },
                                    actions = buildList {
                                        add(SheetAction(Glyphs.Reply, tr("Reply")) { replyTo = m })
                                        if (m.kind != com.example.btcwidget.nostr.core.Nip17.FILE) add(SheetAction(Glyphs.Copy, tr("Copy text")) {
                                            (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                                                .setPrimaryClip(android.content.ClipData.newPlainText("message", m.content))
                                        })
                                    },
                                    header = {
                                        // Quick reactions, as on X: a row of emoji above the actions.
                                        Row(
                                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly
                                        ) {
                                            DM_REACTIONS.forEach { e ->
                                                Text(e, fontSize = 26.sp, modifier = Modifier.clip(CircleShape).clickable(onClickLabel = tr("React {0}", e)) { menuFor = null; vm.reactDm(peers, m, e) }.padding(6.dp))
                                            }
                                        }
                                        HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke, modifier = Modifier.padding(vertical = 4.dp))
                                    }
                                )
                            }
                            reactions[m.id]?.takeIf { it.isNotEmpty() }?.let { rs ->
                                Text(
                                    rs.groupingBy { it }.eachCount().entries.joinToString(" ") { (e, n) -> if (n > 1) "$e $n" else e },
                                    fontSize = 13.sp, color = EInk.Value,
                                    modifier = Modifier.padding(top = 2.dp).background(EInk.Body, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
                replyTo?.let { r ->
                    Row(Modifier.fillMaxWidth().background(EInk.Body).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tr("Replying to ") + (if (r.sender == me) "yourself" else profiles[r.sender]?.bestName ?: Profile.shortNpub(r.sender)) + ": " +
                                (if (r.kind == com.example.btcwidget.nostr.core.Nip17.FILE) "📷 Photo" else r.content.replace('\n', ' ')),
                            color = EInk.Label, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                        )
                        Text("✕", color = EInk.Value, fontSize = 14.sp, modifier = Modifier.clickable(onClickLabel = tr("Cancel reply")) { replyTo = null }.padding(6.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (photoSending) "…" else "🖼", fontSize = 22.sp, color = EInk.Value,
                        modifier = Modifier.clip(CircleShape).clickable(enabled = !photoSending, onClickLabel = tr("Send a photo")) {
                            photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }.padding(6.dp)
                    )
                    OutlinedTextField(
                        value = text, onValueChange = { text = it },
                        placeholder = { Text(tr("Message")) },
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
                    PillButton(if (sending) "…" else tr("Send"), filled = text.isNotBlank() && !sending) {
                        if (text.isBlank() || sending || me == null) return@PillButton
                        sending = true
                        sendHaptics.confirm()
                        val outgoing = text
                        text = ""
                        val parent = replyTo
                        replyTo = null
                        vm.sendDm(peers, outgoing, parent?.id) { ok ->
                            sending = false
                            if (!ok) { text = outgoing; replyTo = parent }
                        }
                    }
                }
            }
        }
    }
}

/** A photo sent in a message: downloaded, decrypted on the phone, then shown. */
@Composable
private fun EncryptedPhoto(vm: SocialViewModel, info: com.example.btcwidget.nostr.core.DmFile.Info) {
    val bitmap by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, info.url) {
        value = vm.openDmFile(info)?.let { bytes ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                runCatching { android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
            }
        }
    }
    val ratio = if ((info.width ?: 0) > 0 && (info.height ?: 0) > 0) info.width!!.toFloat() / info.height!! else 1f
    Box(
        Modifier.width(240.dp).aspectRatio(ratio.coerceIn(0.5f, 2f)).clip(RoundedCornerShape(12.dp)).background(EInk.Track),
        contentAlignment = Alignment.Center
    ) {
        val b = bitmap
        if (b != null) androidx.compose.foundation.Image(b, contentDescription = tr("Photo"), contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text(tr("🔒 Photo"), color = EInk.Caption, fontSize = 13.sp)
    }
}
