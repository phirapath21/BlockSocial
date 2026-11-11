package com.example.btcwidget.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.ui.main.EInk

/**
 * Community rules shown in the app (also in docs/community-rules.md). Google Play requires
 * apps with user posts to state what isn't allowed, and to offer reporting and blocking.
 */
object CommunityRules {
    const val TITLE = "Community rules"

    const val INTRO = "Block is a window onto Nostr, an open network that no company runs. " +
        "Block doesn't host posts, but these rules apply to everyone using the app."

    val RULES = listOf(
        "Nothing illegal" to "No content that is illegal where you or your readers are, including child sexual abuse material, which is never tolerated.",
        "No harassment or hate" to "Don't threaten, harass, bully or dox anyone, and don't attack people for who they are.",
        "No spam or scams" to "No repetitive or automated posting, fake giveaways, phishing, or posts that trick people out of money or keys.",
        "No impersonation" to "Don't pretend to be another person or organisation in a way that misleads.",
        "Label sensitive content" to "Mark adult or graphic content as such, and keep it out of replies to people who didn't ask for it.",
        "Respect privacy" to "Don't post other people's private information or intimate images without their consent."
    )

    val TOOLS = listOf(
        "Mute" to "Hides an account everywhere in Block. Use ⋯ on a post, or Unmute on their profile to undo.",
        "Report" to "Use ⋯ › Report on a post. The report is published to your relays (NIP-56) so relays and other apps can act on it.",
        "Contact" to "For anything urgent or illegal, open an issue at github.com/phirapath21/btcwidget, or contact the relay that hosts the post."
    )

    const val OUTRO = "Posts on Nostr are public and permanent: relays may keep copies even after you delete. " +
        "Breaking these rules can get you reported, muted by others, and refused by relays."
}

@Composable
fun CommunityRulesScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(EInk.Background)) {
        SocialTopBar(CommunityRules.TITLE, onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text(CommunityRules.INTRO, color = EInk.Label, fontSize = 15.sp)
            Section("Not allowed", CommunityRules.RULES)
            Section("Your tools", CommunityRules.TOOLS)
            Text(CommunityRules.OUTRO, color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.padding(top = 18.dp))
        }
    }
}

@Composable
private fun Section(title: String, items: List<Pair<String, String>>) {
    Text(title, color = EInk.Value, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp, bottom = 4.dp))
    items.forEach { (head, body) ->
        Text(head, color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        Text(body, color = EInk.Label, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
    }
}
