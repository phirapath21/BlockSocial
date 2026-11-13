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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
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
fun LoginScreen(
    vm: SocialViewModel,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onOpenRules: () -> Unit = {},
    /** Where to start: "choose", or straight to "nsec", "bunker" or "create" (from the landing page). */
    startMode: String = "choose",
    /** After a new account is made and its key saved: onboarding. Defaults to [onDone]. */
    onCreated: () -> Unit = onDone
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val hasSigner = remember { AmberSigner.isInstalled(context) }
    var mode by remember { mutableStateOf(startMode) }   // choose | bunker | nsec | create | backup
    var input by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var newNsec by remember { mutableStateOf<String?>(null) }

    if (mode == "nsec" || mode == "backup") SecureScreen()

    Column(Modifier.fillMaxSize().background(EInk.Background)) {
        SocialTopBar(if (mode == "backup") tr("Save your key") else tr("Nostr account"), onBack = { if (mode == "choose" || mode == "backup") { if (mode == "backup") onCreated() else onBack() } else if (startMode != "choose") onBack() else mode = "choose" })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp)
        ) {
            when (mode) {
                "choose" -> {
                    Text(tr("Join the conversation"), color = EInk.Value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(
                        tr("Your Nostr account is a key pair. Keep the private key (nsec) safe: whoever has it can post as you, and it can't be reset."),
                        color = EInk.Label, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)
                    )
                    // Accounts already on this phone (after Add account, or a logout of one of several).
                    val saved by vm.savedAccounts.collectAsState()
                    val profiles by vm.profiles.collectAsState()
                    if (saved.isNotEmpty()) {
                        Text(tr("On this phone"), color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        saved.forEach { a ->
                            AccountRow(profiles[a.pubkey], a.pubkey, when (a.type) { com.example.btcwidget.nostr.SignerType.AMBER -> "Signer app"; com.example.btcwidget.nostr.SignerType.BUNKER -> "Remote signer"; else -> "Key on this phone" }) {
                                com.example.btcwidget.ui.main.EInkChip(text = "USE", selected = true, onClick = { vm.switchAccount(a.pubkey); onDone() })
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    Option(
                        title = tr("Connect a signer app"),
                        body = if (hasSigner) tr("Recommended. Your key stays in the signer (such as Amber); Block never sees it.")
                               else tr("Recommended. Install a signer such as Amber, which keeps your key and approves each post."),
                        button = if (hasSigner) tr("Connect") else tr("Get Amber"),
                        filled = true
                    ) {
                        if (hasSigner) vm.loginWithAmber(onDone) else uriHandler.openUri("https://github.com/greenart7c3/Amber")
                    }
                    Option(tr("Remote signer (bunker)"), tr("Use a NIP-46 signer such as nsec.app: paste its bunker:// link. Your key stays with that signer."), tr("Use bunker"), false) {
                        mode = "bunker"; error = null; input = ""
                    }
                    Option(tr("Log in with nsec"), tr("Paste your private key. It's encrypted with this phone's secure hardware and never backed up or sent anywhere."), tr("Use nsec"), false) {
                        mode = "nsec"; error = null
                    }
                    Option(tr("New to Nostr?"), tr("Create a new account in seconds. You'll be shown your private key once to save."), tr("Create account"), false) {
                        mode = "create"; error = null
                    }
                    Text(
                        tr("By posting you agree to the Community rules ›"),
                        color = EInk.Value, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp).clickable(onClickLabel = tr("Open community rules"), onClick = onOpenRules).padding(vertical = 6.dp)
                    )
                }
                "bunker" -> {
                    var connecting by remember { mutableStateOf(false) }
                    Text(tr("Remote signer"), color = EInk.Value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        tr("In your signer (nsec.app, nsecBunker…) create a connection for Block and paste its bunker:// link. ") +
                            tr("You may need to approve the connection there."),
                        color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.trim(); error = null },
                        label = { Text(tr("bunker://…")) },
                        singleLine = true,
                        isError = error != null,
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let { Text(it, color = EInk.Alert, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
                    Spacer(Modifier.height(14.dp))
                    PillButton(if (connecting) tr("Waiting for the signer…") else tr("Connect"), filled = input.startsWith("bunker://") && !connecting) {
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
                    Text(tr("Log in with nsec"), color = EInk.Value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(tr("Starts with nsec1…"), color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.trim(); error = null },
                        label = { Text(tr("Private key")) },
                        singleLine = true,
                        isError = error != null,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    )
                    error?.let { Text(it, color = EInk.Alert, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
                    Spacer(Modifier.height(16.dp))
                    PillButton(tr("Log in")) {
                        val e = vm.loginWithSecret(input)
                        if (e == null) { input = ""; onDone() } else error = e
                    }
                }
                "create" -> {
                    Text(tr("Create account"), color = EInk.Value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.take(50) },
                        label = { Text(tr("Name (optional)")) }, singleLine = true, colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    PillButton(tr("Create")) {
                        vm.createAccount(name)?.let { newNsec = it; mode = "backup" }
                    }
                }
                "backup" -> {
                    val nsec = newNsec.orEmpty()
                    Text(tr("This is your private key"), color = EInk.Value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        tr("Save it in a password manager now. It's the only way to log in on another app or phone, and nobody can recover it for you. Never share it."),
                        color = EInk.Label, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
                    )
                    Text(
                        nsec, color = EInk.Value, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth().background(EInk.Body).padding(12.dp)
                    )
                    Row(Modifier.padding(top = 14.dp)) {
                        PillButton(tr("Copy"), filled = false, modifier = Modifier.padding(end = 8.dp)) { copySecret(context, nsec) }
                        PillButton(tr("I saved it")) { newNsec = null; onCreated() }
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
    val sendHaptics = rememberHaptics()
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
    var draftsSheet by remember { mutableStateOf(false) }
    var savedDrafts by remember(account?.pubkey) { mutableStateOf(com.example.btcwidget.nostr.Drafts.savedList(context, account?.pubkey)) }
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
    if (draftsSheet) DraftsSheet(
        drafts = savedDrafts,
        canSaveCurrent = text.isNotBlank(),
        onSaveCurrent = {
            com.example.btcwidget.nostr.Drafts.saveCopy(context, account?.pubkey, text)
            com.example.btcwidget.nostr.Drafts.remove(context, draftKey)
            text = ""
            savedDrafts = com.example.btcwidget.nostr.Drafts.savedList(context, account?.pubkey)
            draftsSheet = false
        },
        onSelect = { d ->
            text = d.text
            com.example.btcwidget.nostr.Drafts.deleteSaved(context, account?.pubkey, d.id)
            savedDrafts = com.example.btcwidget.nostr.Drafts.savedList(context, account?.pubkey)
            draftsSheet = false
        },
        onDelete = { d ->
            com.example.btcwidget.nostr.Drafts.deleteSaved(context, account?.pubkey, d.id)
            savedDrafts = com.example.btcwidget.nostr.Drafts.savedList(context, account?.pubkey)
        },
        onDismiss = { draftsSheet = false }
    )

    val canSend = !sending && uploading == 0 && (text.isNotBlank() || images.isNotEmpty() || quote != null) &&
        (replyTo == null && quote == null || target != null) &&
        (!poll || text.isNotBlank() && pollOptions.count { it.isNotBlank() } >= 2)
    fun send() {
        if (!canSend) return
        sending = true
        sendHaptics.confirm()
        // "@handle" → nostr:npub… so it links and notifies them (and gets a "p" tag).
        val body = mentions.entries.fold(text.trim()) { acc, (handle, pk) ->
            acc.replace(Regex("@" + Regex.escape(handle) + "(?![\\p{L}\\p{M}\\p{N}_])"), "nostr:" + com.example.btcwidget.nostr.core.Nip19.npub(pk))
        }
        scheduleAt?.let { at ->
            if (vm.schedule(body, images, if (sensitive) "Sensitive content" else null, at)) {
                com.example.btcwidget.nostr.Drafts.remove(context, draftKey); onBack()
            } else sending = false
            return
        }
        if (poll) {
            vm.createPoll(body, pollOptions, pollMulti, pollDays * 86_400L) { ok ->
                if (ok) { com.example.btcwidget.nostr.Drafts.remove(context, draftKey); onBack() } else sending = false
            }
            return
        }
        vm.send(body, replyTo?.let { target }, quote?.let { target }, images, if (sensitive) "Sensitive content" else null) { ok ->
            // Success closes the screen; a failure re-enables the button.
            if (ok) { com.example.btcwidget.nostr.Drafts.remove(context, draftKey); onBack() } else sending = false
        }
    }
    val newPost = replyTo == null && quote == null
    // A phone on its side leaves little room above the keyboard: a shorter box.
    val shortScreen = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 520

    Column(Modifier.fillMaxSize().background(EInk.Background).navigationBarsPadding().imePadding()) {
        // ✕ on the left, Post on the right, as on X.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = tr("Close"), onClick = onBack),
                contentAlignment = Alignment.Center
            ) { androidx.compose.material3.Icon(Glyphs.CloseLine, contentDescription = tr("Close"), tint = EInk.Value, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.weight(1f))
            if (savedDrafts.isNotEmpty() || text.isNotBlank()) {
                Text(
                    text = if (savedDrafts.isNotEmpty()) tr("Drafts ({0})", savedDrafts.size) else tr("Save draft"),
                    color = SettingsAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(50))
                        .clickable { draftsSheet = true }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
            Row(
                Modifier.padding(end = 6.dp).clip(RoundedCornerShape(50))
                    .background(SettingsAccent.copy(alpha = if (canSend || sending) 1f else 0.4f))
                    .clickable(enabled = canSend, onClickLabel = tr("Post")) { send() }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (sending) CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.Black, strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp).size(14.dp))
                Text(
                    if (sending) tr("Posting…") else if (replyTo != null) tr("Reply") else if (scheduleAt != null) tr("Schedule") else tr("Post"),
                    color = androidx.compose.ui.graphics.Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold
                )
            }
        }

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (replyTo != null) target?.let { ReplyParent(it, profiles[it.pubkey]) }
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = if (replyTo != null) 0.dp else 8.dp)) {
                account?.let { Avatar(profiles[it.pubkey], it.pubkey, size = 40) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    if (replyTo != null) target?.let { t ->
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = EInk.Caption)) { append(tr("Replying to ")) }
                                withStyle(SpanStyle(color = SettingsAccent)) { append("@" + (profiles[t.pubkey]?.bestName ?: Profile.shortNpub(t.pubkey))) }
                            },
                            fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    androidx.compose.foundation.text.BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = androidx.compose.ui.text.TextStyle(color = EInk.Value, fontSize = 18.sp, lineHeight = 25.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(SettingsAccent),
                        modifier = Modifier.fillMaxWidth().heightIn(min = if (shortScreen) 64.dp else 120.dp).padding(top = 8.dp).focusRequester(focus),
                        decorationBox = { field ->
                            Box {
                                if (text.isEmpty()) Text(
                                    if (replyTo != null) tr("Post your reply") else if (quote != null) tr("Add a comment") else tr("What's happening?"),
                                    color = EInk.Caption, fontSize = 18.sp
                                )
                                field()
                            }
                        }
                    )
                    MentionSuggestions(suggestions, ::mentionHandle) { p ->
                        val handle = mentionHandle(p)
                        text = text.replace(MENTION_AT_END, "\$1@" + Regex.escapeReplacement(handle) + " ")
                        mentions = mentions + (handle to p.pubkey)
                    }
                    HashtagSuggestions(tagSuggestions) { tag ->
                        text = text.replace(HASHTAG_AT_END, "\$1#" + Regex.escapeReplacement(tag) + " ")
                    }
                    if (images.isNotEmpty() || uploading > 0) {
                        val single = images.size + uploading == 1
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = ComposeGap).then(if (single) Modifier else Modifier.horizontalScroll(rememberScrollState()))
                        ) {
                            images.forEachIndexed { idx, img ->
                                AttachmentTile(
                                    attachment = img,
                                    single = single,
                                    onMoveLeft = if (idx > 0) ({
                                        images = images.toMutableList().also { list ->
                                            val item = list.removeAt(idx)
                                            list.add(idx - 1, item)
                                        }
                                    }) else null,
                                    onMoveRight = if (idx < images.lastIndex) ({
                                        images = images.toMutableList().also { list ->
                                            val item = list.removeAt(idx)
                                            list.add(idx + 1, item)
                                        }
                                    }) else null,
                                    onRemove = { images = images - img }
                                )
                            }
                            repeat(uploading) { AttachmentTile(null, single) {} }
                        }
                    }
                    if (poll) PollEditor(
                        options = pollOptions, onOptions = { pollOptions = it },
                        multi = pollMulti, onMulti = { pollMulti = it },
                        days = pollDays, onDays = { pollDays = it },
                        onRemove = { poll = false }
                    )
                    if (quote != null) target?.let { QuotePreview(it, profiles[it.pubkey]) }
                    FirstTimeTip(
                        "compose-public", tr("Public and permanent: notes can't be fully deleted from every relay. Photo location data is removed."),
                        Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                }
            }
        }

        // What this post will do, each with ✕ to undo it.
        val chips = scheduleAt != null || sensitive || images.any { it.isVideo }
        if (chips) Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            scheduleAt?.let { at ->
                ComposeStatusChip(
                    Glyphs.Calendar,
                    java.text.SimpleDateFormat("EEE, MMM d · h:mm a", java.util.Locale.ENGLISH).format(java.util.Date(at * 1000)),
                    onClear = { scheduleAt = null }
                )
            }
            if (sensitive) ComposeStatusChip(Glyphs.Warning, tr("Sensitive: shown behind a warning"), onClear = { sensitive = false }, alert = true)
            if (images.any { it.isVideo }) ComposeStatusChip(Glyphs.Info, tr("Videos keep any location saved in the file"))
        }
        androidx.compose.material3.HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
        // The toolbar stays above the keyboard.
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ComposeToolButton(Glyphs.PhotoLine, tr("Add photo or video"), enabled = !poll && images.size + uploading < 4) {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            }
            if (newPost) ComposeToolButton(Glyphs.Poll, if (poll) tr("Remove poll") else tr("Add poll"), enabled = images.isEmpty() && uploading == 0, on = poll) { poll = !poll }
            if (newPost) ComposeToolButton(Glyphs.Calendar, tr("Schedule"), enabled = !poll, on = scheduleAt != null) { schedulePicker = true }
            ComposeToolButton(Glyphs.Warning, if (sensitive) tr("Unmark sensitive") else tr("Mark as sensitive"), on = sensitive) { sensitive = !sensitive }
            Spacer(Modifier.weight(1f))
            if (text.isNotEmpty()) Text("${text.length}", color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.padding(end = 10.dp))
        }
    }
}

/** Sheet for saving the current post as a draft or loading/deleting saved drafts. */
@Composable
private fun DraftsSheet(
    drafts: List<com.example.btcwidget.nostr.Drafts.SavedDraft>,
    canSaveCurrent: Boolean,
    onSaveCurrent: () -> Unit,
    onSelect: (com.example.btcwidget.nostr.Drafts.SavedDraft) -> Unit,
    onDelete: (com.example.btcwidget.nostr.Drafts.SavedDraft) -> Unit,
    onDismiss: () -> Unit
) {
    BlockSheet(onDismiss = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Drafts"), color = EInk.Value, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (canSaveCurrent) {
                    ChoicePill(tr("+ Save current"), selected = true, onClick = onSaveCurrent)
                }
            }
            if (drafts.isEmpty()) {
                Text(tr("No saved drafts yet."), color = EInk.Caption, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
            } else {
                drafts.forEach { d ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(d) }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                d.text.replace('\n', ' '),
                                color = EInk.Value, fontSize = 15.sp, maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            if (d.savedAt > 0) {
                                Text(relativeTime(d.savedAt / 1000), color = EInk.Caption, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                        Box(
                            Modifier.padding(start = 8.dp).size(34.dp).clip(CircleShape).clickable(onClickLabel = tr("Delete draft")) { onDelete(d) },
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.Icon(Glyphs.Trash, contentDescription = tr("Delete draft"), tint = EInk.Alert, modifier = Modifier.size(18.dp))
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
                }
            }
        }
    }
}

/** A poll under the post (NIP-88): up to four choices, one or several answers, how long it runs. */
@Composable
private fun PollEditor(
    options: List<String>, onOptions: (List<String>) -> Unit,
    multi: Boolean, onMulti: (Boolean) -> Unit,
    days: Int, onDays: (Int) -> Unit,
    onRemove: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(top = ComposeGap).clip(RoundedCornerShape(16.dp))
            .border(1.dp, EInk.BodyStroke, RoundedCornerShape(16.dp)).padding(12.dp)
    ) {
        options.forEachIndexed { i, value ->
            OutlinedTextField(
                value = value,
                onValueChange = { v -> onOptions(options.toMutableList().also { it[i] = v.take(25) }) },
                placeholder = { Text(tr("Choice {0}", i + 1) + if (i >= 2) tr(" (optional)") else "") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SettingsAccent, unfocusedBorderColor = EInk.BodyStroke,
                    focusedTextColor = EInk.Value, unfocusedTextColor = EInk.Value, cursorColor = SettingsAccent,
                    focusedPlaceholderColor = EInk.Caption, unfocusedPlaceholderColor = EInk.Caption
                ),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
        }
        if (options.size < 4) Row(
            Modifier.clip(RoundedCornerShape(50)).clickable { onOptions(options + "") }.padding(vertical = 6.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Icon(Glyphs.Plus, contentDescription = null, tint = SettingsAccent, modifier = Modifier.size(18.dp))
            Text(tr("Add a choice"), color = SettingsAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
        }
        Text(tr("Answers"), color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoicePill(tr("One choice"), selected = !multi) { onMulti(false) }
            ChoicePill(tr("Several"), selected = multi) { onMulti(true) }
        }
        Text(tr("Poll length"), color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 3, 7).forEach { d -> ChoicePill(tr(if (d == 1) "{0} day" else "{0} days", d), selected = days == d) { onDays(d) } }
        }
        Text(
            tr("Remove poll"), color = EInk.Alert, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(50)).clickable(onClick = onRemove).padding(vertical = 6.dp, horizontal = 4.dp)
        )
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
    BlockDialog(
        onDismiss = onDismiss,
        title = tr("Schedule post"),
        confirmLabel = if (initial != null) tr("Update") else tr("Schedule"),
        confirmEnabled = valid,
        onConfirm = { onPick(chosen()) }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            (0..6).forEach { d ->
                val label = when (d) {
                    0 -> "TODAY"; 1 -> "TOMORROW"
                    else -> java.text.SimpleDateFormat("EEE d", java.util.Locale.ENGLISH).format(java.util.Date(System.currentTimeMillis() + d * 86_400_000L)).uppercase()
                }
                com.example.btcwidget.ui.main.EInkChip(text = label, selected = day == d, onClick = { day = d })
            }
        }
        androidx.compose.material3.TimeInput(state = time, modifier = Modifier.padding(top = 14.dp))
        Text(
            if (valid) tr("It's signed and sent at that time. Your phone must be on and online; a signer app may ask you to approve then.")
            else tr("Pick a time in the future."),
            color = if (valid) EInk.Caption else EInk.Alert, fontSize = 13.sp
        )
        if (initial != null) Text(
            tr("Post now instead"), color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp).clickable(onClick = onClear).padding(vertical = 6.dp)
        )
    }
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
        SocialTopBar(tr("Edit profile"), onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp)) {
            @Composable fun field(label: String, value: String, set: (String) -> Unit, singleLine: Boolean = true) =
                OutlinedTextField(value, set, label = { Text(label) }, singleLine = singleLine, colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
            // Banner with the avatar over it, as on the profile; tap either to change it.
            Box(Modifier.fillMaxWidth().padding(bottom = 44.dp)) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(3f).clip(RoundedCornerShape(12.dp)).background(EInk.Track)
                        .clickable(enabled = uploadingFor == null, onClickLabel = tr("Change banner")) { pick("banner") },
                    contentAlignment = Alignment.Center
                ) {
                    if (banner.isNotBlank()) AsyncImage(banner, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    UploadBadge(if (uploadingFor == "banner") null else "🖼 Banner")
                }
                Box(
                    Modifier.align(Alignment.BottomStart).offset(x = 14.dp, y = 36.dp).size(80.dp).clip(CircleShape)
                        .background(EInk.Body).border(3.dp, EInk.Background, CircleShape)
                        .clickable(enabled = uploadingFor == null, onClickLabel = tr("Change profile picture")) { pick("picture") },
                    contentAlignment = Alignment.Center
                ) {
                    if (picture.isNotBlank()) AsyncImage(picture, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    UploadBadge(if (uploadingFor == "picture") null else "📷")
                }
            }
            Text(
                tr("Tap the picture or banner to upload from your phone (location data is removed)."),
                color = EInk.Caption, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp)
            )
            field(tr("Display name"), displayName, { displayName = it.take(60) })
            field(tr("Username"), name, { name = it.take(40) })
            field(tr("Bio"), about, { about = it.take(500) }, singleLine = false)
            field(tr("Picture URL"), picture, { picture = it.trim() })
            field(tr("Banner URL"), banner, { banner = it.trim() })
            field(tr("Website"), website, { website = it.trim() })
            field(tr("Nostr address (NIP-05)"), nip05, { nip05 = it.trim() })
            field(tr("Lightning address (for zaps)"), lud16, { lud16 = it.trim() })
            Spacer(Modifier.height(8.dp))
            PillButton(if (uploadingFor != null) tr("Uploading…") else tr("Save"), filled = uploadingFor == null) {
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
