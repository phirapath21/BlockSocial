package com.example.btcwidget.ui.social

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.view.WindowManager
import androidx.compose.foundation.background
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
fun LoginScreen(vm: SocialViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val hasSigner = remember { AmberSigner.isInstalled(context) }
    var mode by remember { mutableStateOf("choose") }   // choose | nsec | create | backup
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
                    Option(
                        title = "Connect a signer app",
                        body = if (hasSigner) "Recommended. Your key stays in the signer (such as Amber); Blockface never sees it."
                               else "Recommended. Install a signer such as Amber, which keeps your key and approves each post.",
                        button = if (hasSigner) "Connect" else "Get Amber",
                        filled = true
                    ) {
                        if (hasSigner) vm.loginWithAmber(onDone) else uriHandler.openUri("https://github.com/greenart7c3/Amber")
                    }
                    Option("Log in with nsec", "Paste your private key. It's encrypted with this phone's secure hardware and never backed up or sent anywhere.", "Use nsec", false) {
                        mode = "nsec"; error = null
                    }
                    Option("New to Nostr?", "Create a new account in seconds. You'll be shown your private key once to save.", "Create account", false) {
                        mode = "create"; error = null
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
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

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
            if (quote != null) {
                target?.let { t ->
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp).background(EInk.Body).padding(10.dp)) {
                        Text(profiles[t.pubkey]?.bestName ?: Profile.shortNpub(t.pubkey), color = EInk.Value, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(t.content.take(280), color = EInk.Label, fontSize = 14.sp, maxLines = 5)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Public and permanent: notes can't be fully deleted from every relay.",
                    color = EInk.Caption, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(end = 10.dp)
                )
                val canSend = !sending && (text.isNotBlank() || quote != null) && (replyTo == null && quote == null || target != null)
                PillButton(if (sending) "Posting…" else if (replyTo != null) "Reply" else "Post", filled = canSend) {
                    if (!canSend) return@PillButton
                    sending = true
                    vm.send(text.trim(), replyTo?.let { target }, quote?.let { target }) { ok ->
                        // Success closes the screen; a failure re-enables the button.
                        if (ok) onBack() else sending = false
                    }
                }
            }
        }
    }
}

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

    Column(Modifier.fillMaxSize().background(EInk.Background)) {
        SocialTopBar("Edit profile", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp)) {
            @Composable fun field(label: String, value: String, set: (String) -> Unit, singleLine: Boolean = true) =
                OutlinedTextField(value, set, label = { Text(label) }, singleLine = singleLine, colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
            field("Display name", displayName, { displayName = it.take(60) })
            field("Username", name, { name = it.take(40) })
            field("Bio", about, { about = it.take(500) }, singleLine = false)
            field("Picture URL", picture, { picture = it.trim() })
            field("Banner URL", banner, { banner = it.trim() })
            field("Website", website, { website = it.trim() })
            field("Nostr address (NIP-05)", nip05, { nip05 = it.trim() })
            field("Lightning address (for zaps)", lud16, { lud16 = it.trim() })
            Spacer(Modifier.height(8.dp))
            PillButton("Save") {
                vm.updateProfile(
                    current.copy(name = name, displayName = displayName, about = about, picture = picture,
                        banner = banner, nip05 = nip05, lud16 = lud16, website = website),
                    onBack
                )
            }
        }
    }
}
