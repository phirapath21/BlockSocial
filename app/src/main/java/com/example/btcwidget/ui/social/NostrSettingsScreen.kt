package com.example.btcwidget.ui.social

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.example.btcwidget.nostr.SignerType
import com.example.btcwidget.ui.main.EInk
import com.example.btcwidget.ui.main.EInkChip

/** Account, notification, media server and inbox settings for the logged-in Nostr account. */
@Composable
fun NostrSettingsScreen(vm: SocialViewModel, navigate: (NavKey) -> Unit, onBack: () -> Unit) {
    val account by vm.account.collectAsState()
    val off by vm.notificationsOff.collectAsState()
    val inbox by vm.dmInbox.collectAsState()
    Column(Modifier.fillMaxSize().background(EInk.Background)) {
        SocialTopBar("Nostr settings", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            val acct = account
            if (acct == null) {
                Text("Log in to change these settings.", color = EInk.Caption, fontSize = 14.sp)
                return@Column
            }

            AccountsSection(vm, navigate)

            Section("Phone notifications", "Choose what Blockface notifies you about while it's closed.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                NOTIFICATION_TYPES.forEach { (type, label) ->
                    EInkChip(text = label, selected = type !in off, onClick = { vm.setNotificationType(type, on = type in off) })
                }
            }

            ListEditor(
                title = "Media servers",
                note = "Blossom servers your photos and videos upload to, in order (your kind 10063 list). Empty uses blossom.primal.net.",
                initial = remember(acct.pubkey) { vm.mediaServers() },
                placeholder = "https://blossom.example.com",
                valid = { it.startsWith("https://") }
            ) { urls, done -> vm.setMediaServers(urls, done) }

            ListEditor(
                title = "Message inbox relays",
                note = "Where people send you private messages (your kind 10050 list, public).",
                initial = inbox.orEmpty(),
                placeholder = "wss://relay.example.com",
                valid = { it.startsWith("wss://") }
            ) { urls, done -> if (urls.isNotEmpty()) vm.setDmInbox(urls, done) }

            VideoSection(vm)

            ScheduledSection(vm)

            SafetySection(vm)

            if (acct.type == SignerType.LOCAL) ExportKeySection(vm)
        }
    }
}

/** The active account and the others saved on this phone, with switch, add and remove. */
@Composable
private fun AccountsSection(vm: SocialViewModel, navigate: (NavKey) -> Unit) {
    val account by vm.account.collectAsState()
    val saved by vm.savedAccounts.collectAsState()
    val profiles by vm.profiles.collectAsState()
    var removing by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(saved) { saved.forEach { vm.refreshProfile(it.pubkey) } }
    Section("Accounts", "Switch between Nostr accounts on this phone.")
    account?.let { AccountRow(profiles[it.pubkey], it.pubkey, "Active") {} }
    saved.forEach { a ->
        AccountRow(profiles[a.pubkey], a.pubkey, when (a.type) { SignerType.AMBER -> "Signer app"; SignerType.BUNKER -> "Remote signer"; else -> "Key on this phone" }) {
            EInkChip(text = "SWITCH", selected = true, onClick = { vm.switchAccount(a.pubkey) })
            EInkChip(text = "REMOVE", selected = false, onClick = { removing = a.pubkey })
        }
    }
    Row(Modifier.padding(top = 8.dp)) {
        EInkChip(text = "+ ADD ACCOUNT", selected = false, onClick = { vm.prepareAddAccount(); navigate(com.example.btcwidget.LoginKey) })
    }
    removing?.let { pk ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove this account from the phone?") },
            text = { Text("Its key (if stored here), drafts and settings are deleted from this phone. The account itself stays on Nostr. Make sure you have its nsec if you want to use it again.") },
            confirmButton = { TextButton(onClick = { vm.removeSavedAccount(pk); removing = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } }
        )
    }
}

@Composable
internal fun AccountRow(profile: com.example.btcwidget.nostr.core.Profile?, pubkey: String, note: String, actions: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Avatar(profile, pubkey, size = 36)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(profile?.bestName ?: com.example.btcwidget.nostr.core.Profile.shortNpub(pubkey), color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(note, color = EInk.Caption, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { actions() }
    }
}

/** When timeline videos start by themselves (muted). */
@Composable
private fun VideoSection(vm: SocialViewModel) {
    val mode by vm.videoAutoplay.collectAsState()
    Section("Video autoplay", "Videos in the timeline can start by themselves, muted. Autoplay uses data and battery.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        VideoAutoplay.entries.forEach { m ->
            EInkChip(text = (if (m == mode) "✓ " else "") + m.label.uppercase(), selected = m == mode, onClick = { vm.setVideoAutoplay(m) })
        }
    }
    val quality by vm.videoQuality.collectAsState()
    Text(
        "Quality: videos that come in several resolutions start at this. Most Nostr videos are a single file and always play as they are.",
        color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        VideoQuality.entries.forEach { q ->
            EInkChip(text = (if (q == quality) "✓ " else "") + q.label.uppercase(), selected = q == quality, onClick = { vm.setVideoQuality(q) })
        }
    }
}

/** Posts waiting to be published, with Post now and Cancel. */
@Composable
private fun ScheduledSection(vm: SocialViewModel) {
    val posts by vm.scheduled.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refreshScheduled() }
    Section("Scheduled posts", "Sent at their time if your phone is on and online. Schedule one with 🕒 in the composer.")
    if (posts.isEmpty()) Text("None", color = EInk.Caption, fontSize = 13.sp)
    posts.forEach { p ->
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(java.text.SimpleDateFormat("EEE, MMM d - h:mm a", java.util.Locale.ENGLISH).format(java.util.Date(p.at * 1000)) +
                if (p.at * 1000 < System.currentTimeMillis()) " · waiting to send" else "",
                color = EInk.Caption, fontSize = 12.sp)
            Text(p.content, color = EInk.Value, fontSize = 14.sp, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EInkChip(text = "POST NOW", selected = true, onClick = { vm.postScheduledNow(p) })
                EInkChip(text = "CANCEL", selected = false, onClick = { vm.cancelScheduled(p.id) })
            }
        }
    }
}

/** Muted words and hashtags (public, on the NIP-51 mute list) and the web-of-trust filter. */
@Composable
private fun SafetySection(vm: SocialViewModel) {
    val mutes by vm.mutes.collectAsState()
    val wot by vm.wotFilter.collectAsState()
    var input by remember { mutableStateOf("") }
    Section("Muted words and hashtags", "Posts containing these are hidden everywhere. Your mute list is public, like your follows.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        mutes.words.sorted().forEach { w -> EInkChip(text = "$w ✕", selected = false, onClick = { vm.muteEntry("word", w, false) }) }
        mutes.hashtags.sorted().forEach { t -> EInkChip(text = "#$t ✕", selected = false, onClick = { vm.muteEntry("t", t, false) }) }
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        OutlinedTextField(
            value = input, onValueChange = { input = it.take(60) },
            placeholder = { Text("word or #hashtag") }, singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EInk.Value, unfocusedBorderColor = EInk.BodyStroke, cursorColor = EInk.Value, focusedTextColor = EInk.Value, unfocusedTextColor = EInk.Value),
            modifier = Modifier.weight(1f)
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 8.dp))
        EInkChip(text = "MUTE", selected = input.isNotBlank(), onClick = {
            val v = input.trim()
            if (v.isNotEmpty()) {
                if (v.startsWith("#")) vm.muteEntry("t", v.removePrefix("#"), true) else vm.muteEntry("word", v, true)
                input = ""
            }
        })
    }
    if (mutes.threads.isNotEmpty()) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("${mutes.threads.size} muted conversations", color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.weight(1f))
            EInkChip(text = "UNMUTE ALL", selected = false, onClick = { vm.unmuteAllThreads() })
        }
    }

    Section("Web of trust", "Hide replies, mentions and search results from accounts that nobody you follow follows. Cuts most spam; people new to Nostr may be hidden too.")
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(if (wot) "On: your network only" else "Off: everyone", color = EInk.Value, fontSize = 14.sp, modifier = Modifier.weight(1f))
        EInkChip(text = if (wot) "TURN OFF" else "TURN ON", selected = !wot, onClick = { vm.setWotFilter(!wot) })
    }
}

private val NOTIFICATION_TYPES = listOf(
    "reply" to "REPLIES", "mention" to "MENTIONS", "repost" to "REPOSTS",
    "like" to "LIKES", "zap" to "ZAPS", "dm" to "MESSAGES"
)

@Composable
private fun Section(title: String, note: String? = null) {
    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke, modifier = Modifier.padding(top = 16.dp, bottom = 10.dp))
    Text(title, color = EInk.Value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    note?.let { Text(it, color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)) }
}

/** A list of addresses, one per line, saved as a whole. */
@Composable
private fun ListEditor(
    title: String,
    note: String,
    initial: List<String>,
    placeholder: String,
    valid: (String) -> Boolean,
    onSave: (List<String>, () -> Unit) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var text by remember(initial) { mutableStateOf(initial.joinToString("\n")) }
    var saving by remember { mutableStateOf(false) }
    Section(title, note)
    if (!editing) {
        if (initial.isEmpty()) Text("Not set", color = EInk.Caption, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        initial.forEach { Text(it, color = EInk.Value, fontSize = 13.sp, fontFamily = FontFamily.Monospace) }
        Row(Modifier.padding(top = 8.dp)) { EInkChip(text = "EDIT", selected = false, onClick = { editing = true }) }
        return
    }
    val lines = text.split('\n', ' ', ',').map { it.trim().trimEnd('/') }.filter { it.isNotEmpty() }
    val bad = lines.filterNot(valid)
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text(placeholder) },
        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = EInk.Value),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EInk.Value, unfocusedBorderColor = EInk.BodyStroke, cursorColor = EInk.Value),
        modifier = Modifier.fillMaxWidth()
    )
    if (bad.isNotEmpty()) Text("Not valid: " + bad.joinToString(), color = EInk.Alert, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        EInkChip(text = if (saving) "SAVING…" else "SAVE", selected = bad.isEmpty(), onClick = {
            if (bad.isEmpty() && !saving) {
                saving = true
                onSave(lines.distinct()) { saving = false; editing = false }
            }
        })
        EInkChip(text = "CANCEL", selected = false, onClick = { editing = false; text = initial.joinToString("\n") })
    }
}

/** Shows the nsec after a warning, with screenshots blocked while it is on screen. */
@Composable
private fun ExportKeySection(vm: SocialViewModel) {
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    var nsec by remember { mutableStateOf<String?>(null) }
    Section("Your private key (nsec)", "Back it up somewhere safe. Anyone with it can post as you, forever; it can't be changed.")
    val shown = nsec
    if (shown == null) {
        Row { EInkChip(text = "SHOW NSEC", selected = false, onClick = { confirm = true }) }
    } else {
        SecureScreen()
        Text(shown, color = EInk.Value, fontSize = 13.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EInkChip(text = "COPY", selected = false, onClick = { copySecret(context, shown) })
            EInkChip(text = "HIDE", selected = true, onClick = { nsec = null })
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Show your private key?") },
            text = {
                Text(
                    "Never share it or paste it into a website. Anyone who sees it controls your account, " +
                        "and Nostr can't reset it. Make sure no one is looking at your screen."
                )
            },
            confirmButton = { TextButton(onClick = { confirm = false; nsec = vm.exportNsec() }) { Text("Show") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}

/** Copies a secret marked sensitive, so Android 13+ hides it from the clipboard preview. */
private fun copySecret(context: Context, secret: String) {
    val clip = ClipData.newPlainText("nsec", secret)
    if (Build.VERSION.SDK_INT >= 33) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
    android.widget.Toast.makeText(context, "Copied. Paste it somewhere safe, then clear your clipboard.", android.widget.Toast.LENGTH_LONG).show()
}
