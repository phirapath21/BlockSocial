package com.example.btcwidget.ui.social

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.example.btcwidget.nostr.SignerType
import com.example.btcwidget.nostr.core.Nip19
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk

/**
 * The settings pages, in the order the Settings list shows them. Pages that also appear in the
 * bars or the side panel (your account, notifications, Bitcoin) use the same icon there.
 */
enum class SettingsPage(val title: String, val summary: String, val icon: () -> androidx.compose.ui.graphics.vector.ImageVector) {
    ACCOUNT("Your account", "Accounts on this phone, your key, scheduled posts, log out", { NavIcons.Profile.outline }),
    NOTIFICATIONS("Notifications", "What Block tells you about while it's closed", { NavIcons.Bell.outline }),
    PRIVACY("Privacy and safety", "Muted words, web of trust, private lists", { Glyphs.Shield }),
    DISPLAY("Display and language", "Language, videos, where links open", { Glyphs.Eye }),
    RELAYS("Relays and servers", "Where your notes, messages and media go", { Glyphs.Server }),
    WALLET("Wallet and zaps", "Connect a Lightning wallet to zap in one tap", { Glyphs.Zap }),
    BITCOIN("Bitcoin and widgets", "Your stack, currency, price sources, refresh", { NavIcons.Bitcoin.outline }),
    ABOUT("About Block", "Version, privacy policy, community rules", { Glyphs.Info });

    companion object {
        fun of(name: String) = entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: ACCOUNT
    }
}

/** Settings worth finding by name from the search box, and the page each is on. */
private val SEARCHABLE: List<Pair<String, SettingsPage>> = listOf(
    "Accounts on this phone" to SettingsPage.ACCOUNT, "Add account" to SettingsPage.ACCOUNT, "Log out" to SettingsPage.ACCOUNT,
    "Your private key (nsec)" to SettingsPage.ACCOUNT, "Scheduled posts" to SettingsPage.ACCOUNT, "Edit profile" to SettingsPage.ACCOUNT,
    "Phone notifications" to SettingsPage.NOTIFICATIONS, "Replies" to SettingsPage.NOTIFICATIONS, "Mentions" to SettingsPage.NOTIFICATIONS,
    "Zaps" to SettingsPage.NOTIFICATIONS, "Messages" to SettingsPage.NOTIFICATIONS,
    "Muted words and hashtags" to SettingsPage.PRIVACY, "Muted conversations" to SettingsPage.PRIVACY, "Web of trust" to SettingsPage.PRIVACY,
    "Private bookmarks and mutes" to SettingsPage.PRIVACY, "Community rules" to SettingsPage.PRIVACY,
    "Language" to SettingsPage.DISPLAY, "Open links in" to SettingsPage.DISPLAY, "Video autoplay" to SettingsPage.DISPLAY, "Video quality" to SettingsPage.DISPLAY,
    "Relays" to SettingsPage.RELAYS, "Message inbox relays" to SettingsPage.RELAYS, "Media servers" to SettingsPage.RELAYS,
    "Wallet (NWC)" to SettingsPage.WALLET,
    "Your stack" to SettingsPage.BITCOIN, "Currency" to SettingsPage.BITCOIN, "Widget theme" to SettingsPage.BITCOIN,
    "Refresh every" to SettingsPage.BITCOIN, "BTC price" to SettingsPage.BITCOIN, "THB price" to SettingsPage.BITCOIN,
    "Privacy policy" to SettingsPage.ABOUT, "Version" to SettingsPage.ABOUT,
)

/**
 * The Settings page: a search box and the categories, each opening its own page, as on X.
 * [accountName] shows under "Your account" (null when logged out).
 */
@Composable
fun SettingsHome(accountName: String?, onOpen: (SettingsPage) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(EInk.Background).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            SettingsTextField(
                value = query, onValueChange = { query = it.take(40) }, placeholder = tr("Search settings"),
                modifier = Modifier.fillMaxWidth()
            )
        }
        val q = query.trim()
        if (q.isNotEmpty()) {
            val hits = SEARCHABLE.filter { (title, page) -> tr(title).contains(q, ignoreCase = true) || title.contains(q, ignoreCase = true) || tr(page.title).contains(q, ignoreCase = true) }
            if (hits.isEmpty()) SettingsNote(tr("No settings match \"{0}\".", q))
            hits.forEach { (title, page) -> SettingsLinkRow(tr(title), summary = tr(page.title)) { onOpen(page) } }
            return@Column
        }
        SettingsPage.entries.forEach { page ->
            val summary = if (page == SettingsPage.ACCOUNT && accountName != null) accountName + " · " + tr(page.summary) else tr(page.summary)
            SettingsLinkRow(tr(page.title), summary = summary, icon = page.icon()) { onOpen(page) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * One settings page, full screen with a back arrow. [bitcoin] draws the Bitcoin and widgets
 * page (its settings live with the price data); [appVersion] shows on About.
 */
@Composable
fun SettingsPageScreen(
    vm: SocialViewModel,
    page: SettingsPage,
    navigate: (NavKey) -> Unit,
    onBack: () -> Unit,
    appVersion: String? = null,
    bitcoin: @Composable () -> Unit = {},
) {
    val account by vm.account.collectAsState()
    Column(Modifier.fillMaxSize().background(EInk.Background)) {
        SocialTopBar(tr(page.title), onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            val needsAccount = page in setOf(SettingsPage.NOTIFICATIONS, SettingsPage.PRIVACY, SettingsPage.WALLET)
            if (needsAccount && account == null) {
                SettingsNote(tr("Log in to change these settings."))
                SettingsLinkRow(tr("Log in or create account")) { navigate(com.example.btcwidget.LoginKey) }
                return@Column
            }
            when (page) {
                SettingsPage.ACCOUNT -> AccountPage(vm, navigate)
                SettingsPage.NOTIFICATIONS -> NotificationsPage(vm)
                SettingsPage.PRIVACY -> PrivacyPage(vm, navigate)
                SettingsPage.DISPLAY -> DisplayPage(vm)
                SettingsPage.RELAYS -> RelaysPage(vm, navigate)
                SettingsPage.WALLET -> WalletPage(vm)
                SettingsPage.BITCOIN -> bitcoin()
                SettingsPage.ABOUT -> AboutPage(appVersion, navigate)
            }
        }
    }
}

// ---- Your account ----

@Composable
private fun AccountPage(vm: SocialViewModel, navigate: (NavKey) -> Unit) {
    val context = LocalContext.current
    val account by vm.account.collectAsState()
    val saved by vm.savedAccounts.collectAsState()
    val profiles by vm.profiles.collectAsState()
    var removing by remember { mutableStateOf<String?>(null) }
    var loggingOut by remember { mutableStateOf(false) }
    LaunchedEffect(saved) { saved.forEach { vm.refreshProfile(it.pubkey) } }

    val acct = account
    if (acct == null) {
        SettingsNote(tr("You're not logged in. Log in with a signer app, a remote signer or your nsec, or create a new account."))
        SettingsLinkRow(tr("Log in or create account")) { navigate(com.example.btcwidget.LoginKey) }
        return
    }
    // Who you are, with the npub to copy.
    val npub = Nip19.npub(acct.pubkey)
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(profiles[acct.pubkey], acct.pubkey, size = 52, onClick = { navigate(com.example.btcwidget.ProfileKey(acct.pubkey)) })
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            DisplayName(profiles[acct.pubkey], acct.pubkey, EInk.Value, 18.sp)
            Text(
                npub.take(14) + "…" + npub.takeLast(6), color = EInk.Caption, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable(onClickLabel = tr("Copy npub")) {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("npub", npub))
                    android.widget.Toast.makeText(context, tr("npub copied"), android.widget.Toast.LENGTH_SHORT).show()
                }.padding(vertical = 2.dp)
            )
            Text(signerLabel(acct.type), color = EInk.Caption, fontSize = 13.sp)
        }
    }
    SettingsLinkRow(tr("Edit profile")) { navigate(com.example.btcwidget.EditProfileKey) }
    ScheduledRow(vm)

    SettingsDivider()
    SettingsHeader(tr("Accounts on this phone"))
    SettingsNote(tr("Switch between Nostr accounts without logging out."))
    saved.forEach { a ->
        Row(Modifier.fillMaxWidth().clickable(onClickLabel = tr("Switch")) { vm.switchAccount(a.pubkey) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(profiles[a.pubkey], a.pubkey, size = 40)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(profiles[a.pubkey]?.bestName ?: Profile.shortNpub(a.pubkey), color = EInk.Value, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(signerLabel(a.type), color = EInk.Caption, fontSize = 13.sp)
            }
            SettingsButton(tr("Switch"), primary = true) { vm.switchAccount(a.pubkey) }
            Spacer(Modifier.size(8.dp))
            SettingsButton(tr("Remove"), destructive = true) { removing = a.pubkey }
        }
    }
    SettingsLinkRow(tr("+ Add account"), chevron = false) { navigate(com.example.btcwidget.LoginKey) }

    if (acct.type == SignerType.LOCAL) {
        SettingsDivider()
        SettingsHeader(tr("Your private key (nsec)"))
        PrivateKey(vm)
    }

    SettingsDivider()
    SettingsLinkRow(tr("Log out"), destructive = true) { loggingOut = true }
    Spacer(Modifier.height(8.dp))

    removing?.let { pk ->
        ConfirmDialog(
            title = tr("Remove this account from the phone?"),
            text = tr("Its key (if stored here), drafts and settings are deleted from this phone. The account itself stays on Nostr. Make sure you have its nsec if you want to use it again."),
            confirmLabel = tr("Remove"),
            onConfirm = { vm.removeSavedAccount(pk); removing = null },
            onDismiss = { removing = null }
        )
    }
    if (loggingOut) ConfirmDialog(
        title = tr("Log out?"),
        text = if (acct.type == SignerType.LOCAL) tr("Logging out deletes the key from this phone. Make sure you saved your nsec.")
        else tr("You can log in again with the same signer any time."),
        confirmLabel = tr("Log out"),
        onConfirm = { loggingOut = false; vm.logout() },
        onDismiss = { loggingOut = false }
    )
}

private fun signerLabel(type: SignerType): String = when (type) {
    SignerType.AMBER -> tr("Signed by your signer app")
    SignerType.BUNKER -> tr("Signed by your remote signer (bunker)")
    else -> tr("Key encrypted on this device")
}

/** Scheduled posts, opened in place: Post now or Cancel each. */
@Composable
private fun ScheduledRow(vm: SocialViewModel) {
    val posts by vm.scheduled.collectAsState()
    var open by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refreshScheduled() }
    SettingsLinkRow(
        tr("Scheduled posts"),
        summary = tr("Sent at their time if your phone is on and online. Schedule one with 🕒 in the composer."),
        value = if (posts.isEmpty()) tr("None") else posts.size.toString(),
        chevron = posts.isNotEmpty()
    ) { open = !open }
    if (open) posts.forEach { p ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                java.text.SimpleDateFormat("EEE d MMM · HH:mm", java.util.Locale.getDefault()).format(java.util.Date(p.at * 1000)) +
                    if (p.at * 1000 < System.currentTimeMillis()) tr(" · waiting to send") else "",
                color = EInk.Caption, fontSize = 13.sp
            )
            Text(p.content, color = EInk.Value, fontSize = 15.sp, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsButton(tr("Post now"), primary = true) { vm.postScheduledNow(p) }
                SettingsButton(tr("Cancel"), destructive = true) { vm.cancelScheduled(p.id) }
            }
        }
    }
}

/** Shows the nsec after a warning, with screenshots blocked while it is on screen. */
@Composable
private fun PrivateKey(vm: SocialViewModel) {
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    var nsec by remember { mutableStateOf<String?>(null) }
    SettingsNote(tr("Back it up somewhere safe. Anyone with it can post as you, forever; it can't be changed."))
    val shown = nsec
    if (shown == null) {
        SettingsLinkRow(tr("Show your private key"), chevron = true) { confirm = true }
    } else {
        SecureScreen()
        Text(shown, color = EInk.Value, fontSize = 14.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsButton(tr("Copy")) { copySecret(context, shown) }
            SettingsButton(tr("Hide"), primary = true) { nsec = null }
        }
    }
    if (confirm) ConfirmDialog(
        title = tr("Show your private key?"),
        text = tr("Never share it or paste it into a website. Anyone who sees it controls your account, ") +
            tr("and Nostr can't reset it. Make sure no one is looking at your screen."),
        confirmLabel = tr("Show key"),
        onConfirm = { confirm = false; nsec = vm.exportNsec() },
        onDismiss = { confirm = false }
    )
}

/** Copies a secret marked sensitive, so Android 13+ hides it from the clipboard preview. */
private fun copySecret(context: Context, secret: String) {
    val clip = ClipData.newPlainText("nsec", secret)
    if (Build.VERSION.SDK_INT >= 33) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
    android.widget.Toast.makeText(context, tr("Copied. Paste it somewhere safe, then clear your clipboard."), android.widget.Toast.LENGTH_LONG).show()
}

// ---- Notifications ----

private val NOTIFICATION_TYPES = listOf(
    "reply" to "Replies", "mention" to "Mentions", "repost" to "Reposts",
    "like" to "Likes", "zap" to "Zaps", "dm" to "Messages"
)

@Composable
private fun NotificationsPage(vm: SocialViewModel) {
    val push by vm.pushEnabled.collectAsState()
    val off by vm.notificationsOff.collectAsState()
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) {}
    SettingsSwitchRow(
        tr("Phone notifications"),
        summary = tr("Replies, zaps and messages while Block is closed, checked every refresh interval."),
        checked = push
    ) { on ->
        vm.setPushEnabled(on)
        if (on && Build.VERSION.SDK_INT >= 33) permission.launch("android.permission.POST_NOTIFICATIONS")
    }
    SettingsDivider()
    SettingsHeader(tr("Notify me about"))
    NOTIFICATION_TYPES.forEach { (type, label) ->
        SettingsSwitchRow(tr(label), checked = push && type !in off, enabled = push) { on -> vm.setNotificationType(type, on) }
    }
    SettingsNote(tr("These also apply to the Notifications tab's unread count."))
}

// ---- Privacy and safety ----

@Composable
private fun PrivacyPage(vm: SocialViewModel, navigate: (NavKey) -> Unit) {
    val mutes by vm.mutes.collectAsState()
    val wot by vm.wotFilter.collectAsState()
    val private by vm.privateLists.collectAsState()
    var input by remember { mutableStateOf("") }

    SettingsHeader(tr("Muted words and hashtags"))
    SettingsNote(tr("Posts containing these are hidden everywhere. Your mute list is public, like your follows."))
    val entries = mutes.words.sorted().map { "word" to it } + mutes.hashtags.sorted().map { "t" to it }
    entries.forEach { (type, value) ->
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (type == "t") "#$value" else value, color = EInk.Value, fontSize = 16.sp, modifier = Modifier.weight(1f))
            androidx.compose.foundation.layout.Box(
                Modifier.size(40.dp).clickable(onClickLabel = tr("Unmute {0}", value)) { vm.muteEntry(type, value, false) },
                contentAlignment = Alignment.Center
            ) { Icon(Glyphs.Close, contentDescription = null, tint = EInk.Caption, modifier = Modifier.size(20.dp)) }
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        SettingsTextField(input, { input = it.take(60) }, tr("word or #hashtag"), Modifier.weight(1f))
        Spacer(Modifier.size(8.dp))
        SettingsButton(tr("Mute"), primary = true, enabled = input.isNotBlank()) {
            val v = input.trim()
            if (v.startsWith("#")) vm.muteEntry("t", v.removePrefix("#"), true) else vm.muteEntry("word", v, true)
            input = ""
        }
    }
    if (mutes.threads.isNotEmpty()) {
        SettingsLinkRow(tr("Muted conversations"), value = mutes.threads.size.toString(), chevron = false) {}
        Row(Modifier.padding(horizontal = 16.dp)) { SettingsButton(tr("Unmute all")) { vm.unmuteAllThreads() } }
    }

    SettingsDivider()
    SettingsHeader(tr("Filters"))
    SettingsSwitchRow(
        tr("Web of trust"),
        summary = tr("Hide replies, mentions and search results from accounts that nobody you follow follows. Cuts most spam; people new to Nostr may be hidden too."),
        checked = wot
    ) { vm.setWotFilter(it) }
    SettingsSwitchRow(
        tr("Private bookmarks and mutes"),
        summary = tr("New ones are encrypted so only you can read them (relays see only that the list exists). Off: anyone can see them. Either way they sync to your other Nostr apps."),
        checked = private
    ) { vm.setPrivateLists(it) }

    SettingsDivider()
    SettingsLinkRow(tr("Community rules"), summary = tr("What isn't allowed; mute and report")) { navigate(com.example.btcwidget.RulesKey) }
}

// ---- Display and language ----

@Composable
private fun DisplayPage(vm: SocialViewModel) {
    val context = LocalContext.current
    val inApp by vm.linksInApp.collectAsState()
    val autoplay by vm.videoAutoplay.collectAsState()
    val quality by vm.videoQuality.collectAsState()

    SettingsHeader(tr("Language"))
    SettingsNote(tr("Text in menus, buttons and messages. Posts stay in the language they were written in."))
    Lang.Choice.entries.forEach { c ->
        SettingsRadioRow(if (c == Lang.Choice.SYSTEM) tr(c.label) else c.label, selected = Lang.choice == c) { Lang.choose(context, c) }
    }

    SettingsDivider()
    SettingsHeader(tr("Open links in"))
    SettingsRadioRow(tr("Block's browser"), summary = tr("The post stays under the page, so you can like or reply while reading."), selected = inApp) { vm.setLinksInApp(true) }
    SettingsRadioRow(tr("Phone's browser"), summary = tr("Has your logins and saved passwords."), selected = !inApp) { vm.setLinksInApp(false) }

    SettingsDivider()
    SettingsHeader(tr("Video autoplay"))
    SettingsNote(tr("Videos in the timeline can start by themselves, muted. Autoplay uses data and battery."))
    VideoAutoplay.entries.forEach { m -> SettingsRadioRow(tr(m.label), selected = autoplay == m) { vm.setVideoAutoplay(m) } }

    SettingsDivider()
    SettingsHeader(tr("Video quality"))
    SettingsNote(tr("Quality: videos that come in several resolutions start at this. Most Nostr videos are a single file and always play as they are."))
    VideoQuality.entries.forEach { q -> SettingsRadioRow(tr(q.label), selected = quality == q) { vm.setVideoQuality(q) } }
}

// ---- Relays and servers ----

@Composable
private fun RelaysPage(vm: SocialViewModel, navigate: (NavKey) -> Unit) {
    val account by vm.account.collectAsState()
    val inbox by vm.dmInbox.collectAsState()
    val status = remember { vm.relayStatus().filter { it.primary } }
    SettingsLinkRow(
        tr("Relay status and lists"),
        summary = tr("Your relay lists (read and write, search, blocked) and how each relay is doing."),
        value = tr("{0} of {1} connected", status.count { it.state == com.example.btcwidget.nostr.RelayStatus.State.CONNECTED }, status.size)
    ) { navigate(com.example.btcwidget.RelaysKey) }

    SettingsDivider()
    SettingsHeader(tr("Relays Block connects to"))
    SettingsNote(tr("One wss:// address per line."))
    SettingsListEditor(
        initial = vm.repo.pool.urls.toList(),
        placeholder = "wss://relay.example.com",
        valid = { it.startsWith("wss://") || it.startsWith("ws://") },
        extraAction = { SettingsButton(tr("Use defaults")) { vm.setRelays(null) } }
    ) { urls, done -> vm.setRelays(urls); done() }

    val acct = account ?: return
    SettingsDivider()
    SettingsHeader(tr("Message inbox relays"))
    SettingsNote(tr("Where people send you private messages (your kind 10050 list, public)."))
    SettingsListEditor(
        initial = inbox.orEmpty(),
        placeholder = "wss://relay.example.com",
        valid = { it.startsWith("wss://") }
    ) { urls, done -> if (urls.isNotEmpty()) vm.setDmInbox(urls, done) else done() }

    SettingsDivider()
    SettingsHeader(tr("Media servers"))
    SettingsNote(tr("Blossom servers your photos and videos upload to, in order (your kind 10063 list). Empty uses blossom.primal.net."))
    SettingsListEditor(
        initial = remember(acct.pubkey) { vm.mediaServers() },
        placeholder = "https://blossom.example.com",
        valid = { it.startsWith("https://") }
    ) { urls, done -> vm.setMediaServers(urls, done) }
}

// ---- Wallet and zaps ----

@Composable
private fun WalletPage(vm: SocialViewModel) {
    val wallet by vm.nwc.collectAsState()
    var uri by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var disconnecting by remember { mutableStateOf(false) }
    SettingsHeader(tr("Wallet (NWC)"))
    if (wallet != null) {
        SettingsNote(tr("Connected. Zaps pay from this wallet in one tap, up to the budget you set in the wallet."))
        SettingsLinkRow(tr("Disconnect wallet"), destructive = true) { disconnecting = true }
    } else {
        SettingsNote(
            tr("In your wallet (Alby Hub, Primal, Coinos, Mutiny…) create a Nostr Wallet Connect link and paste it here. ") +
                tr("Set a spending budget there: anyone with this link can spend up to it. It's stored encrypted on this phone.")
        )
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            androidx.compose.material3.OutlinedTextField(
                value = uri, onValueChange = { uri = it.trim(); error = null },
                placeholder = { Text("nostr+walletconnect://…", color = EInk.Caption) },
                singleLine = true, isError = error != null,
                visualTransformation = PasswordVisualTransformation(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SettingsAccent, unfocusedBorderColor = EInk.BodyStroke, cursorColor = SettingsAccent,
                    focusedTextColor = EInk.Value, unfocusedTextColor = EInk.Value
                ),
                modifier = Modifier.fillMaxWidth()
            )
            error?.let { Text(it, color = androidx.compose.ui.graphics.Color(0xFFF4212E), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
            Spacer(Modifier.height(10.dp))
            SettingsButton(tr("Connect"), primary = true, enabled = uri.isNotBlank()) {
                val e = vm.connectNwc(uri)
                if (e == null) uri = "" else error = e
            }
        }
        SettingsNote(tr("Without a wallet here, zapping opens your Lightning wallet app to pay."))
    }
    if (disconnecting) ConfirmDialog(
        title = tr("Disconnect wallet?"),
        text = tr("Zaps will open your Lightning wallet app to pay instead. You can connect again any time."),
        confirmLabel = tr("Disconnect"),
        onConfirm = { disconnecting = false; vm.disconnectNwc() },
        onDismiss = { disconnecting = false }
    )
}

// ---- About ----

@Composable
private fun AboutPage(appVersion: String?, navigate: (NavKey) -> Unit) {
    val uri = LocalUriHandler.current
    SettingsLinkRow(tr("Version"), value = appVersion ?: "–", chevron = false) {}
    SettingsLinkRow(tr("Privacy policy")) { uri.openUri(com.example.btcwidget.ui.main.PRIVACY_POLICY_URL) }
    SettingsLinkRow(tr("Community rules")) { navigate(com.example.btcwidget.RulesKey) }
    SettingsDivider()
    SettingsHeader(tr("Data and credits"))
    SettingsNote(tr("Prices: Coinbase, Kraken, Bitkub · Network: mempool.space · Supply: blockchain.info · MSTR: Yahoo Finance, CoinGecko"))
    SettingsNote(tr("Type: Inter, Noto Sans Thai, Oswald, VT323 (SIL Open Font License)"))
}

/** A saved account with its actions, as listed when adding or switching accounts at login. */
@Composable
internal fun AccountRow(profile: Profile?, pubkey: String, note: String, actions: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(profile, pubkey, size = 36)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(profile?.bestName ?: Profile.shortNpub(pubkey), color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(note, color = EInk.Caption, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { actions() }
    }
}
