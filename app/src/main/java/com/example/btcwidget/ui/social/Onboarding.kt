package com.example.btcwidget.ui.social

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.btcwidget.nostr.AmberSigner
import com.example.btcwidget.nostr.core.HomeFeed
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk
import com.example.btcwidget.ui.main.EInkChip

private val Orange = Color(0xFFF7931A)

/**
 * The first screen for someone not logged in: what Block is, and the ways in: create an
 * account, a signer app (Amber), an nsec, or a remote signer; or just look around first.
 */
@Composable
fun LandingScreen(
    vm: SocialViewModel,
    onCreate: () -> Unit,
    onNsec: () -> Unit,
    onBunker: () -> Unit,
    onLoggedIn: () -> Unit,
    onBrowse: () -> Unit,
    onOpenRules: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val hasSigner = remember { AmberSigner.isInstalled(context) }
    Column(
        Modifier.fillMaxSize().background(EInk.Background).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Spacer(Modifier.height(48.dp))
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(Orange), contentAlignment = Alignment.Center) {
            Text("₿", color = Color.Black, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        }
        Text("Block", color = EInk.Value, fontSize = 40.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
        Text("Bitcoin people, on Nostr.", color = EInk.Value, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Point("An open network: no company owns your account or your followers.")
            Point("Your account is a key you hold. Take it to any Nostr app.")
            Point("Zap sats to posts you love, and keep the Bitcoin price at a glance.")
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(32.dp))

        BigButton("Create account", filled = true, onClick = onCreate)
        Spacer(Modifier.height(10.dp))
        BigButton(if (hasSigner) "Log in with signer app (Amber)" else "Get a signer app (Amber)", filled = false) {
            if (hasSigner) vm.loginWithAmber(onLoggedIn) else uriHandler.openUri("https://github.com/greenart7c3/Amber")
        }
        Spacer(Modifier.height(10.dp))
        BigButton("Log in with nsec", filled = false, onClick = onNsec)
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Remote signer (bunker)", color = EInk.Label, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onBunker).padding(vertical = 8.dp))
            Text("Look around first ›", color = EInk.Value, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBrowse).padding(vertical = 8.dp))
        }
        Text(
            "By creating an account or posting you agree to the Community rules ›",
            color = EInk.Caption, fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp).clickable(onClick = onOpenRules).padding(vertical = 6.dp)
        )
    }
}

@Composable
private fun Point(text: String) {
    Row {
        Text("•", color = Orange, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 10.dp))
        Text(text, color = EInk.Label, fontSize = 15.sp)
    }
}

@Composable
private fun BigButton(text: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(50))
            .background(if (filled) Orange else Color.Transparent)
            .then(if (filled) Modifier else Modifier.background(EInk.Body))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (filled) Color.Black else EInk.Value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Right after creating an account: a name and picture, topics, people to follow, and the
 * message inbox, so the first Home feed isn't empty. Every step can be skipped.
 */
@Composable
fun OnboardingScreen(vm: SocialViewModel, onFinish: () -> Unit) {
    var step by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    var topics by remember { mutableStateOf(setOf("bitcoin", "nostr")) }
    // Back (button or gesture) goes to the previous step; from the first, it leaves as usual.
    androidx.activity.compose.BackHandler(enabled = step > 0) { step-- }
    Column(Modifier.fillMaxSize().background(EInk.Background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) Text(
                "← Back", color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClickLabel = "Previous step") { step-- }.padding(8.dp)
            )
            Text(
                "Step ${step + 1} of 4", color = EInk.Caption, fontSize = 13.sp,
                modifier = Modifier.weight(1f).padding(start = if (step > 0) 4.dp else 8.dp)
            )
            Text("Skip", color = EInk.Label, fontSize = 15.sp, modifier = Modifier.clickable { if (step < 3) step++ else onFinish() }.padding(8.dp))
        }
        // Where you are in the four steps, in the accent colour.
        androidx.compose.material3.LinearProgressIndicator(
            progress = { (step + 1) / 4f }, color = OnboardOrange, trackColor = EInk.Track,
            drawStopIndicator = {}, gapSize = 0.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(3.dp).clip(RoundedCornerShape(50))
        )
        Spacer(Modifier.height(14.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (step) {
                0 -> ProfileStep(vm) { step = 1 }
                1 -> TopicsStep(topics, onChange = { topics = it }) {
                    // Each topic becomes a feed on Home's menu.
                    topics.forEach { vm.addFeed(HomeFeed.Hashtag(it), select = false) }
                    step = 2
                }
                2 -> PeopleStep(vm, topics.toList()) { step = 3 }
                else -> InboxStep(vm, onFinish)
            }
        }
    }
}

@Composable
private fun StepTitle(title: String, body: String) {
    Text(title, color = EInk.Value, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    Text(body, color = EInk.Label, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
}

@Composable
private fun ProfileStep(vm: SocialViewModel, onNext: () -> Unit) {
    val account by vm.account.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val me = account?.pubkey
    val current = me?.let { profiles[it] }
    var name by remember { mutableStateOf(current?.name.orEmpty()) }
    var about by remember { mutableStateOf(current?.about.orEmpty()) }
    var picture by remember { mutableStateOf(current?.picture) }
    val activity by vm.activity.collectAsState()
    var uploading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { uploading = true; vm.uploadImage(uri) { a -> uploading = false; if (a != null) picture = a.url } }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        StepTitle("Make it yours", "A name and a picture help people you know find and trust you. Both are public.")
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(EInk.Body).clickable {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            contentAlignment = Alignment.Center
        ) {
            when {
                uploading -> CircularProgressIndicator(color = EInk.Value, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                picture != null -> AsyncImage(picture, "Profile picture", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else -> Text("+ Photo", color = EInk.Value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(50) }, label = { Text("Name") }, singleLine = true,
            colors = fieldColors(), modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        )
        OutlinedTextField(
            value = about, onValueChange = { about = it.take(280) }, label = { Text("About you (optional)") }, maxLines = 4,
            colors = fieldColors(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        Spacer(Modifier.height(24.dp))
        BigButton(if (saving) "Saving…" else if (uploading) "Uploading photo…" else "Next", filled = true) {
            if (saving || uploading) return@BigButton
            if (name.isBlank() && about.isBlank() && picture == null) { onNext(); return@BigButton }
            saving = true
            vm.saveProfile(name, about, picture) { ok -> saving = false; if (ok) onNext() }
        }
        // While saving: what it's waiting for (signing, then relays answering), with an orange bar.
        if (saving) {
            val a = activity
            Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                val progress = a?.progress
                if (progress == null) androidx.compose.material3.LinearProgressIndicator(
                    color = OnboardOrange, trackColor = EInk.Track, modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50))
                ) else androidx.compose.material3.LinearProgressIndicator(
                    progress = { progress }, color = OnboardOrange, trackColor = EInk.Track, drawStopIndicator = {}, gapSize = 0.dp,
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50))
                )
                Text(a?.status ?: "Preparing…", color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Topics to start with; each becomes a Home feed and seeds the people to follow. */
private val TOPICS = listOf(
    "bitcoin", "nostr", "siamstr", "thailand", "lightning", "zaps", "art", "photography",
    "music", "memes", "food", "travel", "tech", "dev", "privacy", "freedom", "books", "news"
)

@Composable
private fun TopicsStep(selected: Set<String>, onChange: (Set<String>) -> Unit, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        StepTitle("What are you into?", "Pick a few. Each becomes a feed in Home's menu, and we'll find people posting about them.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TOPICS.forEach { t ->
                val on = t in selected
                EInkChip(text = (if (on) "✓ " else "") + "#$t", selected = on, onClick = { onChange(if (on) selected - t else selected + t) })
            }
        }
        Spacer(Modifier.height(24.dp))
        BigButton("Next", filled = true, onClick = onNext)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PeopleStep(vm: SocialViewModel, topics: List<String>, onNext: () -> Unit) {
    val following by vm.following.collectAsState()
    val tags = topics.ifEmpty { listOf("bitcoin", "nostr") }
    // Well-known people first (quick: profiles from the indexers), then who posts about your topics.
    val starters by produceState<List<Profile>?>(null) { value = vm.starterPeople() }
    val topical by produceState<List<Profile>?>(null, tags) { value = vm.topicPeople(tags) }
    val shownTopical = topical.orEmpty().filter { p -> starters.orEmpty().none { it.pubkey == p.pubkey } }
    val everyone = starters.orEmpty() + shownTopical
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        StepTitle("Follow a few people", "Your Home fills with what they post. You can change this any time.")
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PeopleSection("Well known in Bitcoin and Nostr", starters, "Looking them up…", following, vm)
                PeopleSection(
                    "Posting about " + tags.take(3).joinToString(", ") { "#$it" }, if (topical == null) null else shownTopical,
                    "Finding people posting about " + tags.take(3).joinToString(", ") { "#$it" } + "…", following, vm
                )
                if (starters?.isEmpty() == true && topical?.isEmpty() == true) {
                    Text("Couldn't find anyone right now. You can follow people later from Search.", color = EInk.Caption, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val notYet = everyone.filter { it.pubkey !in following }
            if (everyone.isNotEmpty()) Box(Modifier.weight(1f)) {
                // One update of your follow list, not one per person.
                BigButton(if (notYet.isEmpty()) "✓ All followed" else "Follow all (${notYet.size})", filled = false) {
                    if (notYet.isNotEmpty()) vm.followAll(notYet.map { it.pubkey })
                }
            }
            Box(Modifier.weight(1f)) { BigButton("Next", filled = true, onClick = onNext) }
        }
    }
}

/** A titled group of people to follow; while [people] is null, an orange bar and what it's doing. */
@Composable
private fun PeopleSection(title: String, people: List<Profile>?, loading: String, following: Set<String>, vm: SocialViewModel) {
    Text(title, color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
    when {
        people == null -> Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            androidx.compose.material3.LinearProgressIndicator(
                color = OnboardOrange, trackColor = EInk.Track, modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50))
            )
            Text(loading, color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
        people.isEmpty() -> Text("No one found here.", color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
        else -> people.forEach { p ->
            val on = p.pubkey in following
            PersonRow(p, isFollowing = on, onClick = { vm.follow(p.pubkey, !on) }) {
                EInkChip(text = if (on) "FOLLOWING" else "FOLLOW", selected = !on, onClick = { vm.follow(p.pubkey, !on) })
            }
        }
    }
}

private val OnboardOrange = androidx.compose.ui.graphics.Color(0xFFF7931A)

@Composable
private fun InboxStep(vm: SocialViewModel, onFinish: () -> Unit) {
    var done by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        StepTitle(
            "Let people message you",
            "Private messages are end-to-end encrypted. This publishes which relays receive your messages (public), so other apps know where to send them."
        )
        BigButton(if (done) "✓ Inbox ready" else "Set up my inbox", filled = !done) {
            if (!done) { vm.setUpDmInbox(); done = true }
        }
        Spacer(Modifier.height(10.dp))
        BigButton("Start using Block", filled = done, onClick = onFinish)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = EInk.Value, unfocusedBorderColor = EInk.BodyStroke,
    focusedTextColor = EInk.Value, unfocusedTextColor = EInk.Value, cursorColor = EInk.Value,
    focusedLabelColor = EInk.Label, unfocusedLabelColor = EInk.Caption
)
