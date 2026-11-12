package com.example.btcwidget.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.example.btcwidget.LoginKey
import com.example.btcwidget.ProfileKey
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk

/** Whom [pubkey] follows, or who follows them, with follow buttons. */
@Composable
fun FollowListScreen(vm: SocialViewModel, pubkey: String, followers: Boolean, navigate: (NavKey) -> Unit, onBack: () -> Unit) {
    val profiles by vm.profiles.collectAsState()
    val following by vm.following.collectAsState()
    val account by vm.account.collectAsState()
    val people by produceState<List<String>?>(null, pubkey, followers) {
        value = if (followers) vm.followersOf(pubkey) else vm.followingOf(pubkey).toList()
    }
    // Names and pictures fill in as they load.
    LaunchedEffect(people) { people?.let { vm.loadProfiles(it) } }
    val owner = profiles[pubkey]?.bestName ?: Profile.shortNpub(pubkey)

    Column(Modifier.fillMaxSize().background(EInk.Background)) {
        SocialTopBar(if (followers) tr("Followers of {0}", owner) else tr("{0} follows", owner), onBack)
        SocialScaffold(vm, navigate) { _ ->
            val list = people
            when {
                list == null -> Spinner(Modifier.padding(top = 30.dp))
                list.isEmpty() -> EmptyNote(if (followers) tr("No followers found on these relays.") else tr("Not following anyone yet."))
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item(key = "count") {
                        Text(
                            if (followers && list.size >= 500) tr("At least {0} (relays return up to 500)", list.size) else tr("{0} people", list.size),
                            color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                    // People you follow first, then the rest, each by name.
                    val sorted = list.sortedWith(compareBy({ it !in following }, { profiles[it]?.bestName?.lowercase() ?: "~" }))
                    items(sorted, key = { it }) { pk ->
                        val isFollowing = pk in following
                        PersonRow(
                            profile = profiles[pk] ?: Profile(pk),
                            isFollowing = false,
                            onClick = { navigate(ProfileKey(pk)) },
                            trailing = if (pk == account?.pubkey) null else {
                                {
                                    PillButton(if (isFollowing) tr("Following") else tr("Follow"), filled = !isFollowing) {
                                        if (account == null) navigate(LoginKey) else vm.follow(pk, !isFollowing)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
