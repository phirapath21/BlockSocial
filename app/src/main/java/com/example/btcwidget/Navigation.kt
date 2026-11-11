package com.example.btcwidget

import androidx.compose.foundation.layout.fillMaxSize
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
  // One SocialViewModel for every screen (scoped to the Activity): they share the cache.
  val social: SocialViewModel = viewModel()
  val context = androidx.compose.ui.platform.LocalContext.current
  // Not logged in and new here: the welcome page first (Main stays underneath).
  // Decided once at launch: logging in later must not swap the back stack out from under us.
  val showLanding = androidx.compose.runtime.saveable.rememberSaveable { social.account.value == null && !AppPrefs.landingDone(context) }
  val backStack = rememberNavBackStack(*(if (showLanding) arrayOf<androidx.navigation3.runtime.NavKey>(Main, LandingKey) else arrayOf<androidx.navigation3.runtime.NavKey>(Main)))
  val activity = androidx.activity.compose.LocalActivity.current
  val navigate: (androidx.navigation3.runtime.NavKey) -> Unit = { backStack.add(it) }
  // Never pop the main screen: an empty back stack crashes NavDisplay. That happened when two
  // "go back"s landed together (e.g. a screen's done and back callbacks both firing).
  val back: () -> Unit = { if (backStack.size > 1) backStack.removeLastOrNull() }

  androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
  NavDisplay(
    backStack = backStack,
    // System back on the main screen leaves the app instead.
    onBack = { if (backStack.size > 1) backStack.removeLastOrNull() else activity?.finish() },
    entryProvider =
      entryProvider {
        entry<Main> { MainScreen(onItemClick = navigate) }
        entry<NoteKey> { key -> ThreadScreen(social, key.id, navigate, back) }
        entry<ProfileKey> { key -> ProfileScreen(social, key.pubkey, navigate, back) }
        entry<HashtagKey> { key -> HashtagScreen(social, key.tag, navigate, back) }
        entry<ComposeKey> { key -> ComposeScreen(social, key.replyTo, key.quote, back) }
        entry<LoginKey> { LoginScreen(social, onBack = back, onDone = back, onOpenRules = { navigate(RulesKey) }, onCreated = { back(); navigate(OnboardingKey) }) }
        // Leaving the welcome flow: drop the welcome page and the login screen under it.
        val leaveLanding: () -> Unit = {
          AppPrefs.setLandingDone(context)
          backStack.removeAll { it is LandingKey || it is LoginWithKey }
        }
        entry<LandingKey> {
          com.example.btcwidget.ui.social.LandingScreen(
            social,
            onCreate = { navigate(LoginWithKey("create")) },
            onNsec = { navigate(LoginWithKey("nsec")) },
            onBunker = { navigate(LoginWithKey("bunker")) },
            onLoggedIn = leaveLanding,
            onBrowse = leaveLanding,
            onOpenRules = { navigate(RulesKey) }
          )
        }
        entry<LoginWithKey> { key ->
          LoginScreen(
            social, onBack = back, onDone = leaveLanding, onOpenRules = { navigate(RulesKey) }, startMode = key.mode,
            onCreated = { leaveLanding(); navigate(OnboardingKey) }
          )
        }
        entry<OnboardingKey> { com.example.btcwidget.ui.social.OnboardingScreen(social, onFinish = back) }
        entry<EditProfileKey> { EditProfileScreen(social, back) }
        entry<ChatKey> { key -> com.example.btcwidget.ui.social.ChatScreen(social, key.peers.split(','), navigate, back) }
        entry<FollowsKey> { key -> com.example.btcwidget.ui.social.FollowListScreen(social, key.pubkey, key.followers, navigate, back) }
        entry<EngagementKey> { key -> com.example.btcwidget.ui.social.EngagementScreen(social, key.noteId, key.tab, navigate, back) }
        entry<RulesKey> { com.example.btcwidget.ui.social.CommunityRulesScreen(back) }
        entry<ArticleKey> { key -> com.example.btcwidget.ui.social.ArticleScreen(social, key.naddr, navigate, back) }
        entry<NostrSettingsKey> { com.example.btcwidget.ui.social.NostrSettingsScreen(social, navigate, back) }
        entry<CommunityKey> { key -> com.example.btcwidget.ui.social.CommunityScreen(social, key.coordinate, navigate, back) }
        entry<ChannelKey> { key -> com.example.btcwidget.ui.social.ChannelScreen(social, key.id, navigate, back) }
        entry<GroupKey> { key -> com.example.btcwidget.ui.social.GroupScreen(social, key.address, navigate, back) }
        entry<ListsKey> { key -> com.example.btcwidget.ui.social.ListsScreen(social, navigate, key.owner, back) }
        entry<ListKey> { key -> com.example.btcwidget.ui.social.ListScreen(social, key.owner, key.d, navigate, back) }
        entry<PackKey> { key -> com.example.btcwidget.ui.social.FollowPackScreen(social, key.address, navigate, back) }
        entry<LiveKey> { key -> com.example.btcwidget.ui.social.LiveScreen(social, key.address, navigate, back) }
        entry<RelaysKey> { com.example.btcwidget.ui.social.RelaysScreen(social, back) }
        entry<SearchKey> { com.example.btcwidget.ui.social.SearchScreen(social, navigate, back) }
      },
  )
  // While a repost, reply or post is being sent: what it's waiting for, over every screen.
  com.example.btcwidget.ui.social.PublishStatusBar(social, androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.TopCenter))
  }
}
