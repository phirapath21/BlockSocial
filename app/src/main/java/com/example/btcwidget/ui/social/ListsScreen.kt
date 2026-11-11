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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.example.btcwidget.ListKey
import com.example.btcwidget.LoginKey
import com.example.btcwidget.ProfileKey
import com.example.btcwidget.nostr.FeedItem
import com.example.btcwidget.nostr.core.PeopleList
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk
import kotlinx.coroutines.delay

private val fieldColors @Composable get() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = EInk.Value, unfocusedBorderColor = EInk.BodyStroke,
    focusedTextColor = EInk.Value, unfocusedTextColor = EInk.Value, cursorColor = EInk.Value
)

/**
 * Lists of people, like X's Lists (NIP-51 kind 30000): the user's own (a page in the side
 * bar, [owner] null) or someone else's (from their profile, with [onBack]).
 */
@Composable
fun ListsScreen(vm: SocialViewModel, navigate: (NavKey) -> Unit, owner: String? = null, onBack: (() -> Unit)? = null) {
    val account by vm.account.collectAsState()
    val mine by vm.lists.collectAsState()
    val me = account?.pubkey
    val own = owner == null || owner == me
    val theirs by produceState<List<PeopleList>?>(null, owner) { if (!own && owner != null) value = vm.listsBy(owner) }
    LaunchedEffect(me, own) { if (own && me != null) vm.loadLists() }
    var creating by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(EInk.Background)) {
        if (onBack != null) SocialTopBar("Lists", onBack)
        SocialScaffold(vm, navigate) { _ ->
            val lists = if (own) mine else theirs
            when {
                own && me == null -> {
                    EmptyNote("Log in to make lists of people and read them as feeds.")
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PillButton("Log in") { navigate(LoginKey) } }
                }
                lists == null -> Spinner(Modifier.padding(top = 30.dp))
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    if (own) item(key = "new") {
                        Row(
                            Modifier.fillMaxWidth().clickable(onClickLabel = "New list") { creating = true }.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(EInk.Body), contentAlignment = Alignment.Center) {
                                Text("+", color = EInk.Value, fontSize = 24.sp)
                            }
                            Text("New list", color = EInk.Value, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
                    }
                    if (lists.isEmpty()) item(key = "empty") {
                        EmptyNote(
                            if (own) "Lists keep people together, a topic or a circle of friends, and read as a feed of just them. Lists are public on Nostr."
                            else "No lists."
                        )
                    }
                    items(lists, key = { it.address }) { l -> ListRow(l, vm) { navigate(ListKey(l.owner, l.d)) } }
                }
            }
        }
    }
    if (creating) NameDialog("New list", "", onDismiss = { creating = false }) { name ->
        creating = false
        vm.createList(name) { navigate(ListKey(it.owner, it.d)) }
    }
}

@Composable
private fun ListRow(list: PeopleList, vm: SocialViewModel, onClick: () -> Unit) {
    val profiles by vm.profiles.collectAsState()
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        // The first few faces, overlapping.
        Box(Modifier.width(44.dp + ((list.members.size.coerceIn(1, 3) - 1) * 14).dp)) {
            if (list.members.isEmpty()) Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(EInk.Body), contentAlignment = Alignment.Center) {
                Text("☰", color = EInk.Caption, fontSize = 20.sp)
            }
            list.members.take(3).forEachIndexed { i, pk ->
                Box(Modifier.padding(start = (i * 14).dp)) { Avatar(profiles[pk], pk, size = 44) }
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(list.title, color = EInk.Value, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                peopleCount(list.members.size) + (list.description?.let { " · $it" } ?: ""),
                color = EInk.Caption, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
}

private fun peopleCount(n: Int) = if (n == 1) "1 person" else "$n people"

/** One list: its posts (a feed of just these people) or its people, with editing for your own. */
@Composable
fun ListScreen(vm: SocialViewModel, owner: String, d: String, navigate: (NavKey) -> Unit, onBack: () -> Unit) {
    val account by vm.account.collectAsState()
    val mine by vm.lists.collectAsState()
    val stats by vm.stats.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val following by vm.following.collectAsState()
    val isOwn = owner == account?.pubkey
    // Your own list follows your edits; someone else's is fetched once.
    val fetched by produceState<Any?>(Unit, owner, d) { value = vm.peopleList(owner, d) }
    val list = (if (isOwn) mine?.firstOrNull { it.d == d } else null) ?: fetched as? PeopleList
    val feedKey = list?.members?.sorted()?.hashCode()
    val posts by produceState<List<FeedItem>?>(null, feedKey) {
        value = null
        if (list != null) value = vm.listFeed(list)
    }
    var tab by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(EInk.Background)) {
        SocialTopBar(list?.title ?: "List", onBack)
        SocialScaffold(vm, navigate) { actions ->
            when {
                list == null && fetched == Unit -> Spinner(Modifier.padding(top = 30.dp))
                list == null -> EmptyNote("This list couldn't be found on your relays.")
                else -> {
                    val header: @Composable () -> Unit = {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(list.title, color = EInk.Value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            list.description?.let { Text(it, color = EInk.Label, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp)) }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp).clickable { navigate(ProfileKey(list.owner)) }) {
                                Avatar(profiles[list.owner], list.owner, size = 20)
                                Text(
                                    (profiles[list.owner]?.bestName ?: Profile.shortNpub(list.owner)) + " · " + peopleCount(list.members.size),
                                    color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                                PillButton("Show on Home") { vm.showListOnHome(list); onBack() }
                                if (isOwn) {
                                    PillButton("Edit", filled = false) { editing = true }
                                    PillButton("Delete", filled = false) { confirmDelete = true }
                                }
                            }
                        }
                        FeedTabs(listOf("Posts", "People"), tab) { tab = it }
                    }
                    if (tab == 0) {
                        val items = posts
                        NoteList(items.orEmpty(), stats, actions, header = {
                            header()
                            when {
                                list.members.isEmpty() -> EmptyNote(if (isOwn) "No one here yet. Add people from the People tab or from their profile." else "No one in this list.")
                                items == null -> Spinner()
                                items.isEmpty() -> EmptyNote("No recent posts from these people.")
                            }
                        })
                    } else LazyColumn(Modifier.fillMaxSize()) {
                        item(key = "header") { header() }
                        if (isOwn) item(key = "add") {
                            Text(
                                "+ Add people", color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth().clickable { adding = true }.padding(horizontal = 16.dp, vertical = 14.dp)
                            )
                            HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
                        }
                        items(list.members, key = { it }) { pk ->
                            MemberRow(pk, profiles[pk], pk in following, onOpen = { navigate(ProfileKey(pk)) }, onRemove = if (isOwn) ({ vm.setInList(list, pk, false) }) else null)
                        }
                    }
                }
            }
        }
    }
    if (list != null && editing) EditListDialog(list, onDismiss = { editing = false }) { title, about ->
        editing = false
        vm.renameList(list, title, about)
    }
    if (list != null && confirmDelete) ConfirmDialog(
        title = "Delete “${list.title}”?",
        text = "The list is removed from your relays and your Home feeds. The people in it aren't told.",
        confirmLabel = "Delete",
        onConfirm = { confirmDelete = false; vm.deleteList(list); onBack() },
        onDismiss = { confirmDelete = false }
    )
    if (list != null && adding) AddPeopleSheet(vm, list, onClose = { adding = false })
}

@Composable
private fun MemberRow(pubkey: String, profile: Profile?, isFollowing: Boolean, onOpen: () -> Unit, onRemove: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(profile, pubkey, size = 44)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(profile?.bestName ?: Profile.shortNpub(pubkey), color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(handleText(profile, pubkey) + if (isFollowing) " · Following" else "", color = EInk.Caption, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onRemove != null) Text(
            "Remove", color = EInk.Value, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(EInk.Track).clickable(onClick = onRemove).padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
}

/** Search people (as you type) and add them to [list]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPeopleSheet(vm: SocialViewModel, list: PeopleList, onClose: () -> Unit) {
    val mine by vm.lists.collectAsState()
    val current = mine?.firstOrNull { it.d == list.d } ?: list
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(350)
        searching = true
        results = vm.searchPeople(q)
        searching = false
    }
    BlockSheet(onDismiss = onClose) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text("Add to ${current.title}", color = EInk.Value, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Search people") }, colors = fieldColors,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
            if (searching) Spinner()
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                results.forEach { p ->
                    val inList = p.pubkey in current.members
                    PersonRow(p, isFollowing = false, onClick = { vm.setInList(current, p.pubkey, !inList) }) {
                        Text(
                            if (inList) "Added" else "Add", color = if (inList) EInk.Value else androidx.compose.ui.graphics.Color.Black,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (inList) EInk.Track else EInk.Value)
                                .clickable { vm.setInList(current, p.pubkey, !inList) }.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

/** From a profile: tick the lists this person is in, or start a new one with them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToListSheet(vm: SocialViewModel, pubkey: String, onClose: () -> Unit) {
    val lists by vm.lists.collectAsState()
    LaunchedEffect(Unit) { vm.loadLists() }
    var creating by remember { mutableStateOf(false) }
    val name = vm.profiles.collectAsState().value[pubkey]?.bestName ?: Profile.shortNpub(pubkey)
    BlockSheet(onDismiss = onClose) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text("Lists with $name", color = EInk.Value, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            when (val l = lists) {
                null -> Spinner()
                else -> Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    l.forEach { list ->
                        val inList = pubkey in list.members
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.setInList(list, pubkey, !inList) }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(list.title, color = EInk.Value, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(peopleCount(list.members.size), color = EInk.Caption, fontSize = 13.sp)
                            }
                            Box(
                                Modifier.size(24.dp).clip(CircleShape).background(if (inList) androidx.compose.ui.graphics.Color(0xFFF7931A) else EInk.Track),
                                contentAlignment = Alignment.Center
                            ) { if (inList) Text("✓", color = androidx.compose.ui.graphics.Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            Text(
                "+ New list with $name", color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().clickable { creating = true }.padding(horizontal = 16.dp, vertical = 14.dp)
            )
            Text("Lists are public on Nostr: anyone can see who's in them.", color = EInk.Caption, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
    if (creating) NameDialog("New list", "", onDismiss = { creating = false }) { title ->
        creating = false
        vm.createList(title, firstMember = pubkey)
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    BlockDialog(onDismiss = onDismiss, title = title, confirmLabel = "Create", confirmEnabled = name.isNotBlank(), onConfirm = { onDone(name) }) {
        OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, singleLine = true, placeholder = { Text("Name, e.g. Bitcoin devs") }, colors = fieldColors, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun EditListDialog(list: PeopleList, onDismiss: () -> Unit, onDone: (String, String?) -> Unit) {
    var name by remember { mutableStateOf(list.title) }
    var about by remember { mutableStateOf(list.description.orEmpty()) }
    BlockDialog(onDismiss = onDismiss, title = "Edit list", confirmLabel = "Save", confirmEnabled = name.isNotBlank(), onConfirm = { onDone(name, about) }) {
        OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, singleLine = true, label = { Text("Name") }, colors = fieldColors, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.size(8.dp))
        OutlinedTextField(value = about, onValueChange = { about = it.take(280) }, label = { Text("Description (optional)") }, colors = fieldColors, modifier = Modifier.fillMaxWidth())
    }
}
