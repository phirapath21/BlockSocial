package com.example.btcwidget.ui.social

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.example.btcwidget.nostr.NostrRepository
import com.example.btcwidget.nostr.core.Profile

/**
 * The handle shown under a name: "✓ name@domain" once the domain confirms it (NIP-05),
 * else "@username", else a short npub. An unverified address is never shown, so nobody
 * can borrow a well-known domain just by typing it into their profile.
 */
@Composable
fun handleText(profile: Profile?, pubkey: String): String {
    val repo = NostrRepository.get(LocalContext.current)
    val verified by repo.nip05Verified.collectAsState()
    val id = profile?.nip05?.trim()?.takeIf { it.isNotEmpty() }
    LaunchedEffect(pubkey, id) { if (id != null) repo.verifyNip05(pubkey, id) }
    return when {
        id != null && verified["$pubkey|$id"] == true -> "✓ " + id.removePrefix("_@")
        !profile?.name.isNullOrBlank() && ' ' !in profile!!.name!! -> "@" + profile.name
        else -> Profile.shortNpub(pubkey)
    }
}
