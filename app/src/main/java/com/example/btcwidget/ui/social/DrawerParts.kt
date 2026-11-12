package com.example.btcwidget.ui.social

import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk

/** Top of the side bar: avatar, name, handle and follow count, or a log-in prompt. */
@Composable
fun DrawerAccountHeader(vm: SocialViewModel, onLogin: () -> Unit, navigate: (androidx.navigation3.runtime.NavKey) -> Unit = {}) {
    val account by vm.account.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val following by vm.following.collectAsState()
    val me = account?.pubkey
    if (me == null) {
        Column {
            Text(tr("Block"), color = EInk.Value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(tr("Log in to post, follow and zap."), color = EInk.Label, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            PillButton(tr("Log in"), onClick = onLogin)
        }
        return
    }
    val profile = profiles[me]
    var showQr by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (showQr) ProfileQrDialog(vm, me, navigate, onDismiss = { showQr = false })
    Column {
        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Avatar(profile, me, size = 52, onClick = { navigate(com.example.btcwidget.ProfileKey(me)) })
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            // Your profile as a QR code (and a scanner), to follow each other in person.
            QrButton("My QR code") { showQr = true }
        }
        DisplayName(profile, me, EInk.Value, 19.sp, Modifier.padding(top = 12.dp))
        Text(
            handleText(profile, me),
            color = EInk.Caption, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        // "120 Following", as on X: the number bold; opens the list.
        Text(
            androidx.compose.ui.text.buildAnnotatedString {
                pushStyle(androidx.compose.ui.text.SpanStyle(color = EInk.Value, fontWeight = FontWeight.Bold))
                append(compactCount(following.size))
                pop()
                append(" " + tr("Following"))
            },
            color = EInk.Caption, fontSize = 15.sp,
            modifier = Modifier.padding(top = 10.dp)
                .clickable(onClickLabel = tr("Following")) { navigate(com.example.btcwidget.FollowsKey(me, followers = false)) }
        )
    }
}

/** The side panel button's face (right of the bottom bar): the user's avatar, or ≡ when logged out. */
@Composable
fun MenuAvatar(vm: SocialViewModel) {
    val account by vm.account.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val me = account?.pubkey
    if (me == null) Text("≡", color = EInk.Value, fontSize = 24.sp) else Avatar(profiles[me], me, size = 28)
}
