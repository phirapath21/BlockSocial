package com.example.btcwidget.ui.social

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.example.btcwidget.nostr.AmberSigner
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk

/** Blocks screenshots and the recents thumbnail while a private key may be on screen. */
@Composable
fun SecureScreen() {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(Unit) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = EInk.Value, unfocusedBorderColor = EInk.BodyStroke,
    focusedTextColor = EInk.Value, unfocusedTextColor = EInk.Value, cursorColor = EInk.Value,
    focusedLabelColor = EInk.Label, unfocusedLabelColor = EInk.Caption
)

/** Log in with a signer app (recommended), an nsec, or create a new account. */
@Composable
fun LoginScreen(vm: SocialViewModel, onBack: () -> Unit, onDone: () -> Unit, onOpenRules: () -> Unit = {}) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val hasSigner = remember { AmberSigner.isInstalled(context) }
    var mode by remember { mutableStateOf("choose") }   // choose | bunker | nsec | create | backup
    var input by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var newNsec by remember { mutableStateOf<String?>(null) }

    if (mode == "nsec" || mode == "backup") SecureScreen()

    Column(Modifier.fillMaxSize().background(EInk.Background)) {
        SocialTopBar(if (mode == "backup") "Save your key" else "Nostr account", onBack = { if (mode == "choose" || mode == "backup") { if (mode == "backup") onDone() else onBack() } else mode = "choose" })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp)
        ) {
            when (mode) {
                "choose" -> {
                    Text("Join the conversation", color = EInk.Value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Your Nostr account is a key pair. Keep the private key (nsec) safe: whoever has it can post as you, and it can't be reset.",
                        color = EInk.Label, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)
                    )
                    // Accounts already on this phone (after Add account, or a logout of one of several).
                    val saved by vm.savedAccounts.collectAsState()
                    val profiles by vm.profiles.collectAsState()
                    if (saved.isNotEmpty()) {
                        Text("On this phone", color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        saved.forEach { a ->
                            AccountRow(profiles[a.pubkey], a.pubkey, when (a.type) { com.example.btcwidget.nostr.SignerType.AMBER -> "Signer app"; com.example.btcwidget.nostr.SignerType.BUNKER -> "Remote signer"; else -> "Key on this phone" }) {
                                com.example.btcwidget.ui.main.EInkChip(text = "USE", selected = true, onClick = { vm.switchAccount(a.pubkey); onDone() })
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    Option(
                        title = "Connect a signer app",
                        body = if (hasSigner) "Recommended. Your key stays in the signer (such as Amber); Blockface never sees it."
                               else "Recommended. Install a signer such as Amber, which keeps your key and approves each post.",
                        button = if (hasSigner) "Connect" else "Get Amber",
                        filled = true
                    ) {
                        if (hasSigner) vm.loginWithAmber(onDone) else uriHandler.openUri("https://github.com/greenart7c3/Amber")
                    }
                    Option("Remote signer (bunker)", "Use a NIP-46 signer such as nsec.app: paste its bunker:// link. Your key stays with that signer.", "Use bunker", false) {
                        mode = "bunker"; error = null; input = ""
                    }
                    Option("Log in with nsec", "Paste your private key. It's encrypted with this phone's secure hardware and never backed up or sent anywhere.", "Use nsec", false) {
                        mode = "nsec"; error = null
                    }
                    Option("New to Nostr?", "Create a new account in seconds. You'll be shown your private key once to save.", "Create account", false) {
                        mode = "create"; error = null
                    }
                    Text(
                        "By posting you agree to the Community rules ›",
                        color = EInk.Value, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp).clickable(onClickLabel = "Open community rules", onClick = onOpenRules).padding(vertical = 6.dp)
                    )
                }
                "bunker" -> {
                    var connecting by remember { mutableStateOf(false) }
                    Text("Remote signer", color = EInk.Value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "In your signer (nsec.app, nsecBunker…) create a connection for Blockface and paste its bunker:// link. " +
                            "You may need to approve the connection there.",
                        color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.trim(); error = null },
                        label = { Text("bunker://…") },
                        singleLine = true,
                        isError = error != null,
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let { Text(it, color = EInk.Alert, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
                    Spacer(Modifier.height(14.dp))
                    PillButton(if (connecting) "Waiting for the signer…" else "Connect", filled = input.startsWith("bunker://") && !connecting) {
                        if (!input.startsWith("bunker://") || connecting) return@PillButton
                        connecting = true
                        vm.loginWithBunker(input) { e ->
                            connecting = false
                            if (e == null) onDone() else error = e
                        }
                    }
                }
                "nsec" -> {
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                    Text("Log in with nsec", color = EInk.Value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Starts with nsec1…", color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.trim(); error = null },
                        label = { Text("Private key") },
                        singleLine = true,
                        isError = error != null,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    )
                    error?.let { Text(it, color = EInk.Alert, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
                    Spacer(Modifier.height(16.dp))
                    PillButton("Log in") {
                        val e = vm.loginWithSecret(input)
                        if (e == null) { input = ""; onDone() } else error = e
                    }
                }
                "create" -> {
                    Text("Create account", color = EInk.Value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.take(50) },
                        label = { Text("Name (optional)") }, singleLine = true, colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    PillButton("Create") {
                        vm.createAccount(name)?.let { newNsec = it; mode = "backup" }
                    }
                }
                "backup" -> {
                    val nsec = newNsec.orEmpty()
                    Text("This is your private key", color = EInk.Value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Save it in a password manager now. It's the only way to log in on another app or phone, and nobody can recover it for you. Never share it.",
                        color = EInk.Label, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
                    )
                    Text(
                        nsec, color = EInk.Value, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth().background(EInk.Body).padding(12.dp)
                    )
                    Row(Modifier.padding(top = 14.dp)) {
                        PillButton("Copy", filled = false, modifier = Modifier.padding(end = 8.dp)) { copySecret(context, nsec) }
                        PillButton("I saved it") { newNsec = null; onDone() }
                    }
                }
            }
        }
    }
}

@Composable
private fun Option(title: String, body: String, button: String, filled: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(title, color = EInk.Value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(body, color = EInk.Label, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
        PillButton(button, filled = filled, onClick = onClick)
    }
    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
}

/** Copies a secret, marked sensitive so Android 13+ doesn't preview it. */
private fun copySecret(context: Context, secret: String) {
    val clip = ClipData.newPlainText("Nostr private key", secret)
    // Honoured on Android 13+, ignored before.
    clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
}

/** Writing a note, reply or quote. */
@Composable
fun ComposeScreen(vm: SocialViewModel, replyTo: String?, quote: String?, onBack: () -> Unit) {
    val profiles by vm.profiles.collectAsState()
    val account by vm.account.collectAsState()
    val target by produceState<Event?>(null, replyTo, quote) { value = (replyTo ?: quote)?.let { vm.note(it) } }
    val context = androidx.compose.ui.platform.LocalContext.current
    val draftKey = remember(replyTo, quote, account?.pubkey) { com.example.btcwidget.nostr.Drafts.key(account?.pubkey, replyTo, quote) }
    // Picks up where you left off; every change is saved, and posting removes the draft.
    var text by remember { mutableStateOf(com.example.btcwidget.nostr.Drafts.get(context, draftKey).orEmpty()) }
    LaunchedEffect(text) { com.example.btcwidget.nostr.Drafts.put(context, draftKey, text) }
    var sending by remember { mutableStateOf(false) }
    var images by remember { mutableStateOf<List<SocialViewModel.Attachment>>(emptyList()) }
    var uploading by remember { mutableStateOf(0) }
    var sensitive by remember { mutableStateOf(false) }
    // A poll (NIP-88) instead of a note: only for new posts.
    var poll by remember { mutableStateOf(false) }
    var pollOptions by remember { mutableStateOf(listOf("", "")) }
    var pollMulti by remember { mutableStateOf(false) }
    var pollDays by remember { mutableStateOf(1) }
    // Publish later (new posts only): seconds since the epoch, or null to post now.
    var scheduleAt by remember { mutableStateOf<Long?>(null) }
    var schedulePicker by remember { mutableStateOf(false) }
    // "@name" picked from suggestions → pubkey; turned into nostr:npub links when sending.
    var mentions by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val following by vm.following.collectAsState()
    val mentionQuery = remember(text) { MENTION_AT_END.find(text)?.groupValues?.get(2) }
    val suggestions = remember(mentionQuery, profiles, following) {
        val q = mentionQuery?.lowercase() ?: return@remember emptyList()
        profiles.values
            .filter { p -> listOfNotNull(p.name, p.displayName, p.nip05).any { it.lowercase().contains(q) } }
            .sortedWith(compareBy({ it.pubkey !in following }, { !(it.name ?: it.bestName).lowercase().startsWith(q) }))
            .take(5)
    }
    val hashtagQuery = remember(text) { HASHTAG_AT_END.find(text)?.groupValues?.get(2) }
    val tagSuggestions = remember(hashtagQuery) { hashtagQuery?.let { vm.hashtagSuggestions(it) }.orEmpty() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // System photo picker: no storage permission needed.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            uploading++
            val done: (SocialViewModel.Attachment?) -> Unit = { attachment ->
                uploading--
                if (attachment != null) images = images + attachment
            }
            // Photos are re-encoded (location removed); videos upload as they are.
            if (context.contentResolver.getType(uri)?.startsWith("video/") == true) vm.uploadVideo(uri, done)
            else vm.uploadImage(uri, done)
        }
    }

    if (schedulePicker) ScheduleDialog(
        initial = scheduleAt,
        onDismiss = { schedulePicker = false },
        onClear = { scheduleAt = null; schedulePicker = false },
        onPick = { scheduleAt = it; schedulePicker = false }
    )

    Column(Modifier.fillMaxSize().background(EInk.Background)) {
        SocialTopBar(
            when {
                replyTo != null -> "Reply"
                quote != null -> "Quote"
                else -> "New post"
            },
            onBack
        )
        Column(Modifier.fillMaxSize().imePadding().padding(14.dp)) {
            if (replyTo != null) {
                target?.let { t ->
                    Text(
                        "Replying to @" + (profiles[t.pubkey]?.bestName ?: Profile.shortNpub(t.pubkey)),
                        color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(t.content.take(280), color = EInk.Label, fontSize = 14.sp, maxLines = 4, modifier = Modifier.padding(bottom = 10.dp))
                }
            }
            Row(verticalAlignment = Alignment.Top) {
                account?.let { Avatar(profiles[it.pubkey], it.pubkey, size = 40) }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(if (replyTo != null) "Post your reply" else "What's happening?") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EInk.Background, unfocusedBorderColor = EInk.Background,
                        focusedTextColor = EInk.Value, unfocusedTextColor = EInk.Value, cursorColor = EInk.Value,
                        focusedPlaceholderColor = EInk.Caption, unfocusedPlaceholderColor = EInk.Caption
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 17.sp),
                    modifier = Modifier.weight(1f).height(180.dp).focusRequester(focus)
                )
            }
            suggestions.forEach { p ->
                val handle = mentionHandle(p)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClickLabel = "Mention ${p.bestName}") {
                            text = text.replace(MENTION_AT_END, "\$1@" + Regex.escapeReplacement(handle) + " ")
                            mentions = mentions + (handle to p.pubkey)
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Avatar(p, p.pubkey, size = 28)
                    Text(p.bestName, color = EInk.Value, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                    Text(" @$handle", color = EInk.Caption, fontSize = 13.sp, maxLines = 1)
                }
            }
            if (tagSuggestions.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp)
                ) {
                    tagSuggestions.forEach { tag ->
                        com.example.btcwidget.ui.main.EInkChip(text = "#$tag", selected = false, onClick = {
                            text = text.replace(HASHTAG_AT_END, "\$1#" + Regex.escapeReplacement(tag) + " ")
                        })
                    }
                }
            }
            if (quote != null) {
                target?.let { t ->
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp).background(EInk.Body).padding(10.dp)) {
                        Text(profiles[t.pubkey]?.bestName ?: Profile.shortNpub(t.pubkey), color = EInk.Value, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(t.content.take(280), color = EInk.Label, fontSize = 14.sp, maxLines = 5)
                    }
                }
            }
            if (poll) {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp).border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(12.dp)).padding(10.dp)) {
                    pollOptions.forEachIndexed { i, value ->
                        OutlinedTextField(
                            value = value,
                            onValueChange = { v -> pollOptions = pollOptions.toMutableList().also { it[i] = v.take(25) } },
                            placeholder = { Text("Choice ${i + 1}" + if (i >= 2) " (optional)" else "") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState())) {
                        if (pollOptions.size < 4) com.example.btcwidget.ui.main.EInkChip(text = "+ CHOICE", selected = false, onClick = { pollOptions = pollOptions + "" })
                        com.example.btcwidget.ui.main.EInkChip(text = if (pollMulti) "MULTIPLE" else "ONE CHOICE", selected = pollMulti, onClick = { pollMulti = !pollMulti })
                        listOf(1, 3, 7).forEach { d ->
                            com.example.btcwidget.ui.main.EInkChip(text = "${d}D", selected = pollDays == d, onClick = { pollDays = d })
                        }
                    }
                }
            }
            if (images.isNotEmpty() || uploading > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState())
                ) {
                    images.forEach { img ->
                        Box {
                            if (img.isVideo) {
                                Box(Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).background(EInk.Body), contentAlignment = Alignment.Center) {
                                    Text("▶ Video", color = EInk.Value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            } else AsyncImage(
                                model = img.url, contentDescription = "Attached image", contentScale = ContentScale.Crop,
                                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).background(EInk.Body)
                            )
                            Text(
                                "✕", color = EInk.Value, fontSize = 14.sp,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(EInk.Background.copy(alpha = 0.7f))
                                    .clickable(onClickLabel = "Remove image") { images = images - img }
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    repeat(uploading) {
                        Box(Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).background(EInk.Body), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = EInk.Value, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "🖼",
                    color = EInk.Value, fontSize = 22.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(enabled = !poll && images.size + uploading < 4, onClickLabel = "Add photo or video") {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        }
                        .padding(8.dp)
                )
                if (replyTo == null && quote == null) Text(
                    "📊",
                    color = EInk.Value, fontSize = 20.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (poll) EInk.Body else EInk.Background)
                        .clickable(enabled = images.isEmpty() && uploading == 0, onClickLabel = if (poll) "Remove poll" else "Add poll") { poll = !poll }
                        .padding(8.dp)
                )
                if (replyTo == null && quote == null) Text(
                    "🕒",
                    color = EInk.Value, fontSize = 20.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (scheduleAt != null) EInk.Body else EInk.Background)
                        .clickable(enabled = !poll, onClickLabel = "Schedule") { schedulePicker = true }
                        .padding(8.dp)
                )
                Text(
                    "⚠",
                    color = if (sensitive) EInk.Alert else EInk.Caption, fontSize = 20.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClickLabel = if (sensitive) "Unmark sensitive" else "Mark as sensitive") { sensitive = !sensitive }
                        .padding(8.dp)
                )
                Text(
                    if (scheduleAt != null) "Posts " + java.text.SimpleDateFormat("EEE, MMM d - h:mm a", java.util.Locale.ENGLISH).format(java.util.Date(scheduleAt!! * 1000)) + ", if your phone is on and online."
                    else if (sensitive) "Marked sensitive: shown behind a warning."
                    else if (images.any { it.isVideo }) "Videos are posted as they are: any location saved in the file is not removed."
                    else "Public and permanent: notes can't be fully deleted from every relay. Photo location data is removed.",
                    color = EInk.Caption, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                )
                val canSend = !sending && uploading == 0 && (text.isNotBlank() || images.isNotEmpty() || quote != null) &&
                    (replyTo == null && quote == null || target != null) &&
                    (!poll || text.isNotBlank() && pollOptions.count { it.isNotBlank() } >= 2)
                PillButton(if (sending) "Posting…" else if (replyTo != null) "Reply" else if (scheduleAt != null) "Schedule" else "Post", filled = canSend) {
                    if (!canSend) return@PillButton
                    sending = true
                    // "@handle" → nostr:npub… so it links and notifies them (and gets a "p" tag).
                    val body = mentions.entries.fold(text.trim()) { acc, (handle, pk) ->
                        acc.replace(Regex("@" + Regex.escape(handle) + "(?![\\p{L}\\p{M}\\p{N}_])"), "nostr:" + com.example.btcwidget.nostr.core.Nip19.npub(pk))
                    }
                    scheduleAt?.let { at ->
                        if (vm.schedule(body, images, if (sensitive) "Sensitive content" else null, at)) {
                            com.example.btcwidget.nostr.Drafts.remove(context, draftKey); onBack()
                        } else sending = false
                        return@PillButton
                    }
                    if (poll) {
                        vm.createPoll(body, pollOptions, pollMulti, pollDays * 86_400L) { ok ->
                            if (ok) { com.example.btcwidget.nostr.Drafts.remove(context, draftKey); onBack() } else sending = false
                        }
                        return@PillButton
                    }
                    vm.send(body, replyTo?.let { target }, quote?.let { target }, images, if (sensitive) "Sensitive content" else null) { ok ->
                        // Success closes the screen; a failure re-enables the button.
                        if (ok) { com.example.btcwidget.nostr.Drafts.remove(context, draftKey); onBack() } else sending = false
                    }
                }
            }
        }
    }
}

/** When to publish: quick choices, or a day and time. Returns seconds since the epoch. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleDialog(initial: Long?, onDismiss: () -> Unit, onClear: () -> Unit, onPick: (Long) -> Unit) {
    val now = remember { java.util.Calendar.getInstance() }
    var day by remember { mutableStateOf(0) }
    val start = remember { java.util.Calendar.getInstance().apply { if (initial != null) timeInMillis = initial * 1000 else add(java.util.Calendar.HOUR_OF_DAY, 1) } }
    val time = androidx.compose.material3.rememberTimePickerState(start.get(java.util.Calendar.HOUR_OF_DAY), start.get(java.util.Calendar.MINUTE), is24Hour = false)
    fun chosen(): Long = java.util.Calendar.getInstance().apply {
        add(java.util.Calendar.DAY_OF_YEAR, day)
        set(java.util.Calendar.HOUR_OF_DAY, time.hour); set(java.util.Calendar.MINUTE, time.minute); set(java.util.Calendar.SECOND, 0)
    }.timeInMillis / 1000
    val valid = chosen() > now.timeInMillis / 1000 + 60
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule post") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    (0..6).forEach { d ->
                        val label = when (d) {
                            0 -> "TODAY"; 1 -> "TOMORROW"
                            else -> java.text.SimpleDateFormat("EEE d", java.util.Locale.ENGLISH).format(java.util.Date(System.currentTimeMillis() + d * 86_400_000L)).uppercase()
                        }
                        com.example.btcwidget.ui.main.EInkChip(text = label, selected = day == d, onClick = { day = d })
                    }
                }
                androidx.compose.material3.TimeInput(state = time, modifier = Modifier.padding(top = 12.dp))
                Text(
                    if (valid) "It's signed and sent at that time. Your phone must be on and online; a signer app may ask you to approve then."
                    else "Pick a time in the future.",
                    color = if (valid) EInk.Caption else EInk.Alert, fontSize = 12.sp
                )
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(enabled = valid, onClick = { onPick(chosen()) }) { Text("Set") } },
        dismissButton = {
            Row {
                if (initial != null) androidx.compose.material3.TextButton(onClick = onClear) { Text("Post now instead") }
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

/** A small dark label over an image; a spinner while [text] is null (uploading). */
@Composable
private fun UploadBadge(text: String?) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        if (text == null) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        else Text(text, color = Color.White, fontSize = 12.sp)
    }
}

/** A "#tag" being typed at the end of the text. */
private val HASHTAG_AT_END = Regex("""(^|\s)#([\p{L}\p{M}\p{N}_]{1,40})$""")

/** An "@name" being typed at the end of the text. */
private val MENTION_AT_END = Regex("""(^|\s)@([\p{L}\p{M}\p{N}_.]{1,30})$""")

/** The handle inserted for a mention: their username, else their name without spaces. */
private fun mentionHandle(p: Profile): String =
    (p.name?.takeIf { it.isNotBlank() && ' ' !in it } ?: p.bestName.replace(Regex("\\s+"), "_")).take(30)

/** Editing the user's profile (kind 0). */
@Composable
fun EditProfileScreen(vm: SocialViewModel, onBack: () -> Unit) {
    val profiles by vm.profiles.collectAsState()
    val account by vm.account.collectAsState()
    val me = account?.pubkey ?: return
    val current = profiles[me] ?: Profile(me)
    var name by remember { mutableStateOf(current.name.orEmpty()) }
    var displayName by remember { mutableStateOf(current.displayName.orEmpty()) }
    var about by remember { mutableStateOf(current.about.orEmpty()) }
    var picture by remember { mutableStateOf(current.picture.orEmpty()) }
    var banner by remember { mutableStateOf(current.banner.orEmpty()) }
    var nip05 by remember { mutableStateOf(current.nip05.orEmpty()) }
    var lud16 by remember { mutableStateOf(current.lud16.orEmpty()) }
    var website by remember { mutableStateOf(current.website.orEmpty()) }
    // Which image the photo picker is choosing for, and which one is uploading.
    var pickFor by remember { mutableStateOf<String?>(null) }
    var uploadingFor by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val target = pickFor
        if (uri != null && target != null) {
            uploadingFor = target
            vm.uploadImage(uri) { a ->
                uploadingFor = null
                if (a != null) { if (target == "picture") picture = a.url else banner = a.url }
            }
        }
    }
    fun pick(target: String) {
        pickFor = target
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Column(Modifier.fillMaxSize().background(EInk.Background)) {
        SocialTopBar("Edit profile", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp)) {
            @Composable fun field(label: String, value: String, set: (String) -> Unit, singleLine: Boolean = true) =
                OutlinedTextField(value, set, label = { Text(label) }, singleLine = singleLine, colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
            // Banner with the avatar over it, as on the profile; tap either to change it.
            Box(Modifier.fillMaxWidth().padding(bottom = 44.dp)) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(3f).clip(RoundedCornerShape(12.dp)).background(EInk.Track)
                        .clickable(enabled = uploadingFor == null, onClickLabel = "Change banner") { pick("banner") },
                    contentAlignment = Alignment.Center
                ) {
                    if (banner.isNotBlank()) AsyncImage(banner, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    UploadBadge(if (uploadingFor == "banner") null else "🖼 Banner")
                }
                Box(
                    Modifier.align(Alignment.BottomStart).offset(x = 14.dp, y = 36.dp).size(80.dp).clip(CircleShape)
                        .background(EInk.Body).border(3.dp, EInk.Background, CircleShape)
                        .clickable(enabled = uploadingFor == null, onClickLabel = "Change profile picture") { pick("picture") },
                    contentAlignment = Alignment.Center
                ) {
                    if (picture.isNotBlank()) AsyncImage(picture, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    UploadBadge(if (uploadingFor == "picture") null else "📷")
                }
            }
            Text(
                "Tap the picture or banner to upload from your phone (location data is removed).",
                color = EInk.Caption, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp)
            )
            field("Display name", displayName, { displayName = it.take(60) })
            field("Username", name, { name = it.take(40) })
            field("Bio", about, { about = it.take(500) }, singleLine = false)
            field("Picture URL", picture, { picture = it.trim() })
            field("Banner URL", banner, { banner = it.trim() })
            field("Website", website, { website = it.trim() })
            field("Nostr address (NIP-05)", nip05, { nip05 = it.trim() })
            field("Lightning address (for zaps)", lud16, { lud16 = it.trim() })
            Spacer(Modifier.height(8.dp))
            PillButton(if (uploadingFor != null) "Uploading…" else "Save", filled = uploadingFor == null) {
                if (uploadingFor != null) return@PillButton
                vm.updateProfile(
                    current.copy(name = name, displayName = displayName, about = about, picture = picture,
                        banner = banner, nip05 = nip05, lud16 = lud16, website = website),
                    onBack
                )
            }
        }
    }
}
