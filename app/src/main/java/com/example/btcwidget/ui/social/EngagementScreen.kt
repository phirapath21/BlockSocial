package com.example.btcwidget.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.example.btcwidget.ProfileKey
import com.example.btcwidget.nostr.NostrRepository
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk

/** Who reposted, liked and zapped a note, in three tabs. */
@Composable
fun EngagementScreen(vm: SocialViewModel, noteId: String, initialTab: Int, navigate: (NavKey) -> Unit, onBack: () -> Unit) {
    val profiles by vm.profiles.collectAsState()
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    val data by produceState<NostrRepository.Engagement?>(null, noteId) { value = vm.engagement(noteId) }

    Column(Modifier.fillMaxSize().background(EInk.Background)) {
        SocialTopBar("Post engagement", onBack)
        val d = data
        FeedTabs(
            listOf(
                "Reposts" + (d?.reposts?.size?.let { " $it" } ?: ""),
                "Likes" + (d?.likes?.size?.let { " $it" } ?: ""),
                "Zaps" + (d?.zaps?.size?.let { " $it" } ?: "")
            ),
            selected = tab
        ) { tab = it }
        when {
            d == null -> Spinner(Modifier.padding(top = 30.dp))
            else -> {
                val rows: List<Triple<String, String?, String?>> = when (tab) {
                    0 -> d.reposts.map { Triple(it, null, null) }
                    1 -> d.likes.map { (pk, reaction) -> Triple(pk, if (reaction == "+") "♥" else reaction, null) }
                    else -> d.zaps.mapNotNull { z -> z.sender?.let { Triple(it, "⚡ ${compactCount((z.amountMsat / 1000).toInt())}", z.comment.ifBlank { null }) } }
                }
                if (rows.isEmpty()) {
                    EmptyNote(listOf("No reposts yet.", "No likes yet.", "No zaps yet.")[tab])
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(rows, key = { it.first + it.second + it.third }) { (pk, badge, comment) ->
                            EngagementRow(profiles[pk], pk, badge, comment) { navigate(ProfileKey(pk)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EngagementRow(profile: Profile?, pubkey: String, badge: String?, comment: String?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Avatar(profile, pubkey, size = 42)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            DisplayName(profile, pubkey, EInk.Value, 15.sp)
            Text(handleText(profile, pubkey), color = EInk.Caption, fontSize = 13.sp, maxLines = 1)
            if (comment != null) Text(comment, color = EInk.Label, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (badge != null) Text(badge, color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
}
