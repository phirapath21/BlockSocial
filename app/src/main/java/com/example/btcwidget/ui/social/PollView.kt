package com.example.btcwidget.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.btcwidget.nostr.core.Poll
import com.example.btcwidget.ui.main.EInk

/**
 * A NIP-88 poll, X-style: tap a choice to vote (or tick several and Vote, for multiple
 * choice); once you've voted or it has ended, each choice shows its share as a bar.
 */
@Composable
fun PollView(poll: Poll, actions: NoteActions) {
    var reload by remember { mutableStateOf(0) }
    val tally by produceState<Poll.Tally?>(null, poll.event.id, reload) { value = actions.pollTally(poll) }
    var picked by remember(poll.event.id) { mutableStateOf(emptySet<String>()) }
    var sending by remember(poll.event.id) { mutableStateOf(false) }
    // Shown as voted right away; the relays catch up on the next load.
    var votedNow by remember(poll.event.id) { mutableStateOf<Set<String>?>(null) }
    val closed = poll.isClosed()
    val mine = votedNow ?: tally?.mine.orEmpty()
    val showResults = closed || mine.isNotEmpty()

    fun send(ids: List<String>) {
        if (sending || ids.isEmpty()) return
        sending = true
        actions.vote(poll, ids) { ok ->
            sending = false
            if (ok) { votedNow = ids.toSet(); reload++ }
        }
    }

    Column(Modifier.padding(top = 8.dp).fillMaxWidth()) {
        poll.options.forEach { option ->
            val shape = RoundedCornerShape(8.dp)
            if (showResults) {
                val t = tally
                val counts = t?.count(option.id) ?: 0
                val ownExtra = if (votedNow != null && option.id in votedNow!! && option.id !in t?.mine.orEmpty()) 1 else 0
                val total = ((t?.voters ?: 0) + if (votedNow != null && t?.mine.isNullOrEmpty()) 1 else 0).coerceAtLeast(1)
                val share = (counts + ownExtra).toFloat() / total
                Box(Modifier.padding(vertical = 3.dp).fillMaxWidth().height(36.dp).clip(shape)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(share.coerceIn(0f, 1f)).background(EInk.BodyStroke))
                    Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            option.label + if (option.id in mine) "  ✓" else "",
                            color = EInk.Value, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontWeight = if (option.id in mine) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        Text("${(share * 100).toInt()}%", color = EInk.Value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                val selected = option.id in picked
                Box(
                    Modifier.padding(vertical = 3.dp).fillMaxWidth().height(36.dp).clip(shape)
                        .border(if (selected) 1.5.dp else 1.dp, if (selected) EInk.Value else EInk.BodyStroke, shape)
                        .clickable(enabled = !sending) {
                            if (poll.multipleChoice) picked = if (selected) picked - option.id else picked + option.id
                            else send(listOf(option.id))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        (if (poll.multipleChoice) (if (selected) "☑ " else "☐ ") else "") + option.label,
                        color = EInk.Value, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }
            }
        }
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val votes = tally?.voters?.let { it + if (votedNow != null && tally?.mine.isNullOrEmpty()) 1 else 0 }
            val parts = listOfNotNull(
                votes?.let { if (it == 1) "1 vote" else "$it votes" } ?: "Loading votes…",
                if (poll.multipleChoice) "Choose any" else null,
                when {
                    closed -> "Final results"
                    poll.endsAt != null -> "Ends " + endsIn(poll.endsAt)
                    else -> null
                }
            )
            Text(parts.joinToString(" · "), color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.weight(1f))
            if (!showResults && poll.multipleChoice && picked.isNotEmpty()) {
                Text(
                    if (sending) "Voting…" else "Vote", color = EInk.Value, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = !sending) { send(poll.options.map { it.id }.filter { it in picked }) }.padding(6.dp)
                )
            }
        }
    }
}

/** "in 3h", "in 2d", for a time in the future. */
private fun endsIn(at: Long, now: Long = System.currentTimeMillis() / 1000): String {
    val s = (at - now).coerceAtLeast(0)
    return when {
        s < 3600 -> "in ${(s / 60).coerceAtLeast(1)}m"
        s < 86_400 -> "in ${s / 3600}h"
        else -> "in ${s / 86_400}d"
    }
}
