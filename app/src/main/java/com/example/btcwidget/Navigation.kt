package com.example.btcwidget

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.btcwidget.ui.main.MainScreen
import com.example.btcwidget.ui.social.ComposeScreen
import com.example.btcwidget.ui.social.EditProfileScreen
import com.example.btcwidget.ui.social.HashtagScreen
import com.example.btcwidget.ui.social.LoginScreen
import com.example.btcwidget.ui.social.ProfileScreen
import com.example.btcwidget.ui.social.SocialViewModel
import com.example.btcwidget.ui.social.ThreadScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)
  // One SocialViewModel for every screen (scoped to the Activity): they share the cache.
  val social: SocialViewModel = viewModel()
  val navigate: (androidx.navigation3.runtime.NavKey) -> Unit = { backStack.add(it) }
  val back: () -> Unit = { backStack.removeLastOrNull() }

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> { MainScreen(onItemClick = navigate) }
        entry<NoteKey> { key -> ThreadScreen(social, key.id, navigate, back) }
        entry<ProfileKey> { key -> ProfileScreen(social, key.pubkey, navigate, back) }
        entry<HashtagKey> { key -> HashtagScreen(social, key.tag, navigate, back) }
        entry<ComposeKey> { key -> ComposeScreen(social, key.replyTo, key.quote, back) }
        entry<LoginKey> { LoginScreen(social, onBack = back, onDone = back, onOpenRules = { navigate(RulesKey) }) }
        entry<EditProfileKey> { EditProfileScreen(social, back) }
        entry<ChatKey> { key -> com.example.btcwidget.ui.social.ChatScreen(social, key.peers.split(','), navigate, back) }
        entry<RulesKey> { com.example.btcwidget.ui.social.CommunityRulesScreen(back) }
        entry<SearchKey> { com.example.btcwidget.ui.social.SearchScreen(social, navigate, back) }
      },
  )
}
