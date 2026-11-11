package com.example.btcwidget.ui.main

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.btcwidget.AppTheme
import com.example.btcwidget.PriceData
import com.example.btcwidget.ShareCard
import com.example.btcwidget.BlockclockMetrics
import com.example.btcwidget.DataSources
import com.example.btcwidget.MetricCategory
import com.example.btcwidget.PricePoint
import com.example.btcwidget.PriceSource
import com.example.btcwidget.Stack
import com.example.btcwidget.StackUnit
import com.example.btcwidget.ThbSource
import com.example.btcwidget.alerts.AlertRule
import com.example.btcwidget.alerts.PriceAlerts

/**
 * Connects [MainScreenViewModel] to [MainScreenContent]. State lives in the ViewModel;
 * this keeps only what needs the Activity: the selected tab and the notification
 * permission prompt.
 */
@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    val social: com.example.btcwidget.ui.social.SocialViewModel = viewModel()
    val account by social.account.collectAsStateWithLifecycle()
    val unreadNotifications by social.unreadNotifications.collectAsStateWithLifecycle()
    val unreadChats by social.unreadConversations.collectAsStateWithLifecycle()
    val pushEnabled by social.pushEnabled.collectAsStateWithLifecycle()
    val nwcWallet by social.nwc.collectAsStateWithLifecycle()
    val notificationItems by social.notifications.collectAsStateWithLifecycle()

    // Badges: load notifications and messages when the account is known and on every return.
    LaunchedEffect(account) { social.refreshBadges() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { social.refreshBadges() }
    // Opening the Notifications tab marks them seen.
    LaunchedEffect(selectedTab, notificationItems) {
        if (selectedTab == MainTab.NOTIFICATIONS) social.markNotificationsSeen()
    }
    // A tapped phone notification opens its tab.
    val requestedTab by com.example.btcwidget.OpenTabRequest.tab.collectAsStateWithLifecycle()
    LaunchedEffect(requestedTab) {
        requestedTab?.let { name ->
            runCatching { MainTab.valueOf(name) }.getOrNull()?.let { selectedTab = it }
            com.example.btcwidget.OpenTabRequest.tab.value = null
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshNotificationPermission()
    }

    LaunchedEffect(Unit) { viewModel.start() }
    // Notifications may have been allowed or blocked in system settings meanwhile.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshNotificationPermission() }

    val context = LocalContext.current
    MainScreenContent(
        state = state,
        selectedTab = selectedTab,
        heroMetric = settings.heroMetric,
        modeStates = settings.modeStates,
        currencySetting = settings.currency,
        theme = settings.theme,
        onThemeSelected = viewModel::setTheme,
        onCurrencySelected = viewModel::setCurrency,
        refreshInterval = settings.refreshInterval,
        onRefreshIntervalSelected = viewModel::setRefreshInterval,
        onHeroMetricSelected = viewModel::selectHero,
        onTabSelected = { selectedTab = it },
        collapsedCategories = settings.collapsedCategories,
        onToggleCategory = viewModel::toggleCategory,
        appVersion = viewModel.appVersion,
        stackSats = settings.stackSats,
        stackUnit = settings.stackUnit,
        onStackChange = viewModel::setStack,
        dataSources = settings.dataSources,
        onDataSourcesChange = viewModel::setDataSources,
        history = history,
        onShare = { key, data -> ShareCard.share(context, key, data, settings.currency, EInk.theme.palette) },
        onModeToggle = viewModel::setModeEnabled,
        onRefresh = viewModel::refreshData,
        isRefreshing = isRefreshing,
        modeOrder = settings.modeOrder,
        onModeOrderChange = viewModel::setModeOrder,
        onItemClick = onItemClick,
        modifier = modifier,
        alerts = settings.alerts,
        onAlertChange = { rule ->
            if (viewModel.updateAlert(rule)) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        notificationsAllowed = settings.notificationsAllowed,
        homeContent = { com.example.btcwidget.ui.social.HomeScreen(social, onItemClick) },
        searchContent = { com.example.btcwidget.ui.social.SearchScreen(social, onItemClick, onBack = null) },
        messagesContent = { com.example.btcwidget.ui.social.MessagesScreen(social, onItemClick) },
        bookmarksContent = { com.example.btcwidget.ui.social.BookmarksScreen(social, onItemClick) },
        communitiesContent = { com.example.btcwidget.ui.social.SpacesScreen(social, onItemClick) },
        drawerHeader = { com.example.btcwidget.ui.social.DrawerAccountHeader(social, onLogin = { onItemClick(com.example.btcwidget.LoginKey) }, navigate = onItemClick) },
        menuButtonIcon = { com.example.btcwidget.ui.social.MenuAvatar(social) },
        badges = mapOf(MainTab.NOTIFICATIONS to unreadNotifications, MainTab.MESSAGES to unreadChats.size),
        notificationsContent = { com.example.btcwidget.ui.social.NotificationsScreen(social, onItemClick) },
        profileContent = {
            val me = account?.pubkey
            if (me != null) {
                com.example.btcwidget.ui.social.ProfileScreen(social, me, onItemClick, onBack = null)
            } else {
                com.example.btcwidget.ui.social.HomeScreen(social, onItemClick)
            }
        },
        nostrSettings = {
            NostrSettingsPanel(
                account = account,
                relays = social.repo.pool.urls.toList(),
                onLogin = { onItemClick(com.example.btcwidget.LoginKey) },
                onLogout = { social.logout() },
                onOpenNostrSettings = { onItemClick(com.example.btcwidget.NostrSettingsKey) },
                onRelaysChange = social::setRelays,
                onOpenRules = { onItemClick(com.example.btcwidget.RulesKey) },
                pushEnabled = pushEnabled,
                walletConnected = nwcWallet != null,
                onConnectWallet = social::connectNwc,
                onDisconnectWallet = social::disconnectNwc,
                onPushChange = { on ->
                    social.setPushEnabled(on)
                    if (on && !settings.notificationsAllowed) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContent(
    state: MainScreenUiState,
    selectedTab: MainTab,
    heroMetric: String,
    modeStates: List<Boolean>,
    currencySetting: String,
    onCurrencySelected: (String) -> Unit,
    theme: AppTheme = AppTheme.CLASSIC,
    onThemeSelected: (AppTheme) -> Unit = {},
    refreshInterval: Int,
    onRefreshIntervalSelected: (Int) -> Unit,
    onHeroMetricSelected: (String) -> Unit,
    onTabSelected: (MainTab) -> Unit,
    onModeToggle: (Int, Boolean) -> Unit,
    onRefresh: () -> Unit,
    onItemClick: (NavKey) -> Unit,
    isRefreshing: Boolean = false,
    modeOrder: List<String> = BlockclockMetrics.MODE_KEYS,
    onModeOrderChange: (List<String>) -> Unit = {},
    modifier: Modifier = Modifier,
    alerts: List<AlertRule> = emptyList(),
    onAlertChange: (AlertRule) -> Unit = {},
    notificationsAllowed: Boolean = true,
    collapsedCategories: Set<MetricCategory> = emptySet(),
    onToggleCategory: (MetricCategory) -> Unit = {},
    appVersion: String? = null,
    stackSats: Long = 0,
    stackUnit: StackUnit = StackUnit.BTC,
    onStackChange: (Long, StackUnit) -> Unit = { _, _ -> },
    dataSources: DataSources = DataSources(PriceSource.COINBASE, ThbSource.CONVERTED),
    onDataSourcesChange: (DataSources) -> Unit = {},
    history: List<PricePoint> = emptyList(),
    onShare: (String, PriceData) -> Unit = { _, _ -> },
    homeContent: @Composable () -> Unit = {},
    notificationsContent: @Composable () -> Unit = {},
    profileContent: @Composable () -> Unit = {},
    nostrSettings: @Composable () -> Unit = {},
    searchContent: @Composable () -> Unit = {},
    messagesContent: @Composable () -> Unit = {},
    bookmarksContent: @Composable () -> Unit = {},
    communitiesContent: @Composable () -> Unit = {},
    /** Top of the side bar: the account (avatar, name) or a log-in prompt. */
    drawerHeader: @Composable () -> Unit = {},
    /** Unread counts shown on bottom-bar tabs. */
    badges: Map<MainTab, Int> = emptyMap(),
    /** The top-left button that opens the side bar (the user's avatar when logged in). */
    menuButtonIcon: @Composable () -> Unit = { Text("≡", color = EInk.Value, fontSize = 22.sp) }
) {
    val data = (state as? MainScreenUiState.Success)?.data
    val stackValue = data?.takeIf { stackSats > 0 }?.let {
        val btc = stackSats / Stack.SATS_PER_BTC.toDouble()
        PriceAlerts.formatPrice(btc * PriceAlerts.price(it, currencySetting), currencySetting)
    }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openPage: (MainTab) -> Unit = { tab ->
        onTabSelected(tab)
        scope.launch { drawerState.close() }
    }
    // Back closes the side bar first, then returns from a side-bar page to Home.
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    BackHandler(enabled = !drawerState.isOpen && selectedTab !in BAR_TABS) { onTabSelected(MainTab.HOME) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Swipe right to open, left to close. Pages with sideways drags of their own (the
        // price chart scrub, widget reordering) open it with the top-left button instead.
        gesturesEnabled = drawerState.isOpen || selectedTab !in DRAG_PAGES,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = EInk.Background,
                drawerContentColor = EInk.Value,
                modifier = Modifier.width(300.dp)
            ) {
                SideBar(selectedTab = selectedTab, header = drawerHeader, onOpen = openPage)
            }
        },
        modifier = modifier
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EInk.Background)
            .statusBarsPadding()
    ) {
        TopBar(
            title = selectedTab.title,
            menuIcon = menuButtonIcon,
            onMenu = { scope.launch { drawerState.open() } }
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedTab) {
                MainTab.HOME -> homeContent()
                MainTab.SEARCH -> searchContent()
                MainTab.MESSAGES -> messagesContent()
                MainTab.BOOKMARKS -> bookmarksContent()
                MainTab.COMMUNITIES -> communitiesContent()
                MainTab.NOTIFICATIONS -> notificationsContent()
                MainTab.PROFILE -> profileContent()
                MainTab.DASHBOARD -> {
                    // Pull down on the dashboard to fetch fresh data now.
                    val pullState = rememberPullToRefreshState()
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = onRefresh,
                        state = pullState,
                        modifier = Modifier.fillMaxSize(),
                        indicator = {
                            PullToRefreshDefaults.Indicator(
                                state = pullState,
                                isRefreshing = isRefreshing,
                                containerColor = EInk.Body,
                                color = EInk.Value,
                                modifier = Modifier.align(Alignment.TopCenter)
                            )
                        }
                    ) {
                        when (val currentState = state) {
                            // The dashboard is its own scrolling grid (with drag-to-reorder).
                            is MainScreenUiState.Success -> DashboardContent(
                                data = currentState.data,
                                heroMetric = heroMetric,
                                onHeroMetricSelected = onHeroMetricSelected,
                                currencySetting = currencySetting,
                                stackSats = stackSats,
                                onSetUpStack = { onTabSelected(MainTab.SETTINGS) },
                                history = history,
                                onShare = { key -> onShare(key, currentState.data) }
                            )
                            else -> Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 12.dp)
                            ) {
                                HeaderSection(title = "BLOCK", subtitle = "Bitcoin at a glance")
                                if (currentState is MainScreenUiState.Error) {
                                    ErrorScreen(error = currentState.throwable, onRefresh = onRefresh)
                                } else {
                                    LoadingScreen()
                                }
                            }
                        }
                    }
                }
                MainTab.ALERTS -> AlertsScreen(
                    alerts = alerts,
                    onAlertChange = onAlertChange,
                    currencySetting = currencySetting,
                    refreshInterval = refreshInterval,
                    notificationsAllowed = notificationsAllowed,
                    data = data
                )
                MainTab.WIDGETS -> WidgetsScreen(
                    data = data,
                    currencySetting = currencySetting,
                    modeStates = modeStates,
                    onModeToggle = onModeToggle,
                    order = modeOrder,
                    onOrderChange = onModeOrderChange,
                    collapsedCategories = collapsedCategories,
                    onToggleCategory = onToggleCategory,
                    stackSats = stackSats,
                    onSetUpStack = { onTabSelected(MainTab.SETTINGS) }
                )
                MainTab.SETTINGS -> SettingsScreen(
                    currencySetting = currencySetting,
                    onCurrencySelected = onCurrencySelected,
                    theme = theme,
                    onThemeSelected = onThemeSelected,
                    refreshInterval = refreshInterval,
                    onRefreshIntervalSelected = onRefreshIntervalSelected,
                    appVersion = appVersion,
                    stackSats = stackSats,
                    stackUnit = stackUnit,
                    onStackChange = onStackChange,
                    stackValue = stackValue,
                    dataSources = dataSources,
                    onDataSourcesChange = onDataSourcesChange,
                    nostrSettings = nostrSettings
                )
            }
        }

        HorizontalDivider(thickness = 1.dp, color = EInk.BodyStroke)
        NavigationBar(
            containerColor = EInk.Body,
            tonalElevation = 0.dp
        ) {
            val itemColors = NavigationBarItemDefaults.colors(
                selectedIconColor = EInk.Value,
                selectedTextColor = EInk.Value,
                indicatorColor = Color.Transparent,
                unselectedIconColor = EInk.Caption,
                unselectedTextColor = EInk.Caption
            )
            BAR_TABS.forEach { tab ->
                // Icons only, as in X; the names are read out by TalkBack.
                val count = badges[tab] ?: 0
                NavigationBarItem(
                    icon = {
                        BadgedBox(badge = {
                            if (count > 0) Badge(containerColor = EInk.Alert) { Text(if (count > 99) "99+" else "$count") }
                        }) {
                            Icon(tab.icon, contentDescription = if (count > 0) "${tab.label}, $count unread" else tab.label)
                        }
                    },
                    alwaysShowLabel = false,
                    selected = selectedTab == tab,
                    onClick = { onTabSelected(tab) },
                    colors = itemColors
                )
            }
        }
    }
    }
}

/** Title row above every page; the left button opens the side bar. */
@Composable
private fun TopBar(title: String, menuIcon: @Composable () -> Unit, onMenu: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = "Open menu", onClick = onMenu)
                .semantics { contentDescription = "Open menu" }
        ) { menuIcon() }
        Text(
            text = title,
            color = EInk.Value,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
}

/** Side bar: the account, then Profile, Bookmarks, Widgets, Price alerts and Settings. */
@Composable
private fun SideBar(selectedTab: MainTab, header: @Composable () -> Unit, onOpen: (MainTab) -> Unit) {
    Column(
        Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp)
    ) {
        Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) { header() }
        HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke, modifier = Modifier.padding(vertical = 8.dp))
        DRAWER_PAGES.forEach { tab ->
            val selected = selectedTab == tab
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = "Open ${tab.title}") { onOpen(tab) }
                    .background(if (selected) EInk.Body else Color.Transparent)
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Icon(tab.icon, contentDescription = null, tint = if (selected) EInk.Value else EInk.Label, modifier = Modifier.size(24.dp))
                Text(
                    text = tab.title,
                    color = EInk.Value,
                    fontSize = 18.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(start = 18.dp)
                )
            }
        }
    }
}

/**
 * Pages. The bottom bar holds Home, Search, Bitcoin, Notifications and Messages; the side
 * bar holds Profile, Widgets, Price alerts and Settings. [label] names the bar
 * icon for TalkBack, [title] is shown in the top bar and side bar.
 */
enum class MainTab(val label: String, val icon: ImageVector, val title: String = label) {
    HOME("Home", Icons.Default.Home),
    SEARCH("Search", Icons.Default.Search),
    DASHBOARD("Bitcoin", TabIcons.Bitcoin),
    NOTIFICATIONS("Notifications", Icons.Default.Notifications),
    MESSAGES("Messages", Icons.Default.Email),
    PROFILE("Profile", Icons.Default.Person),
    BOOKMARKS("Bookmarks", Icons.Default.Star),
    COMMUNITIES("Communities", Icons.Default.Face),
    WIDGETS("Widgets", TabIcons.Widgets),
    ALERTS("Price alerts", Icons.Default.Warning),
    SETTINGS("Settings", Icons.Default.Settings)
}

// Getters, not stored values: a top-level val that touches MainTab would initialize the
// enum while this file's other top-level vals are still null (that crashed the app once).
private val BAR_TABS get() = listOf(MainTab.HOME, MainTab.SEARCH, MainTab.DASHBOARD, MainTab.NOTIFICATIONS, MainTab.MESSAGES)
private val DRAWER_PAGES get() = listOf(MainTab.PROFILE, MainTab.BOOKMARKS, MainTab.COMMUNITIES, MainTab.WIDGETS, MainTab.ALERTS, MainTab.SETTINGS)

/** Pages with sideways drags of their own, where a swipe must not open the side bar. */
private val DRAG_PAGES get() = setOf(MainTab.DASHBOARD, MainTab.WIDGETS)

/**
 * Custom tab glyphs. In their own object so they initialize on first use, independent of
 * this file's top-level values (MainTab reads them while being created).
 */
private object TabIcons {
/** "₿"-like glyph for the Bitcoin tab: a B with two strokes. */
val Bitcoin: ImageVector = ImageVector.Builder(
    name = "Bitcoin",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).addPath(
    pathData = addPathNodes(
        "M9,3h2v2h1V3h2v2.1c2.3,0.4 3.5,1.8 3.5,3.6c0,1.2 -0.6,2.2 -1.6,2.7c1.4,0.5 2.3,1.6 2.3,3.1" +
            "c0,2.3 -1.8,3.9 -4.2,4.3V21h-2v-2h-1v2H9v-2H6v-2h1.5V7H6V5h3V3z" +
            "M9.5,7v4h4c1.1,0 1.9,-0.9 1.9,-2s-0.8,-2 -1.9,-2H9.5z" +
            "M9.5,13v4h4.6c1.2,0 2.1,-0.9 2.1,-2s-0.9,-2 -2.1,-2H9.5z"
    ),
    fill = SolidColor(Color.Black)
).build()

/** Material "widgets" glyph (not in the core icon set). */
val Widgets: ImageVector = ImageVector.Builder(
    name = "Widgets",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).addPath(
    pathData = addPathNodes("M13,13v8h8v-8h-8zM3,21h8v-8L3,13v8zM3,3v8h8L11,3L3,3zM16.66,1.69L11,7.34 16.66,13l5.66,-5.66 -5.66,-5.65z"),
    fill = SolidColor(Color.Black)
).build()
}
