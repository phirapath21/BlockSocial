package com.example.btcwidget.ui.main

import com.example.btcwidget.ui.social.tr
import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.ripple
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
        listsContent = { com.example.btcwidget.ui.social.ListsScreen(social, onItemClick) },
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
        settingsContent = {
            com.example.btcwidget.ui.social.SettingsHome(
                accountName = account?.let { a -> social.profiles.value[a.pubkey]?.bestName ?: com.example.btcwidget.nostr.core.Profile.shortNpub(a.pubkey) },
                onOpen = { page -> onItemClick(com.example.btcwidget.SettingsKey(page.name)) }
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
    /** The Settings page (its categories; each opens its own screen). */
    settingsContent: @Composable () -> Unit = {},
    searchContent: @Composable () -> Unit = {},
    messagesContent: @Composable () -> Unit = {},
    bookmarksContent: @Composable () -> Unit = {},
    communitiesContent: @Composable () -> Unit = {},
    listsContent: @Composable () -> Unit = {},
    /** Top of the side bar: the account (avatar, name) or a log-in prompt. */
    drawerHeader: @Composable () -> Unit = {},
    /** Unread counts shown on bottom-bar tabs. */
    badges: Map<MainTab, Int> = emptyMap(),
    /** The top-left button that opens the side bar (the user's avatar when logged in). */
    menuButtonIcon: @Composable () -> Unit = { Text("≡", color = EInk.Value, fontSize = 22.sp) }
) {
    val data = (state as? MainScreenUiState.Success)?.data
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openPage: (MainTab) -> Unit = { tab ->
        onTabSelected(tab)
        scope.launch { drawerState.close() }
    }
    // Back closes the side bar first, then returns from a side-bar page to Home.
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    BackHandler(enabled = !drawerState.isOpen && selectedTab !in BAR_TABS) { onTabSelected(MainTab.HOME) }

    // The side panel slides in from the right, next to its button at the right of the bottom
    // bar: the drawer is laid out right-to-left, and everything inside it back left-to-right.
    val ltr = androidx.compose.ui.platform.LocalLayoutDirection.current
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        // Swipe in from the right edge to open, back to the right to close. Pages with sideways
        // drags of their own (the price chart scrub, widget reordering) use the button instead.
        gesturesEnabled = drawerState.isOpen || selectedTab !in DRAG_PAGES,
        drawerContent = {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides ltr) {
                ModalDrawerSheet(
                    drawerContainerColor = EInk.Background,
                    drawerContentColor = EInk.Value,
                    drawerShape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
                    modifier = Modifier.width(310.dp)
                ) {
                    SideBar(selectedTab = selectedTab, header = drawerHeader, onOpen = openPage, onClose = { scope.launch { drawerState.close() } })
                }
            }
        },
        modifier = modifier
    ) {
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides ltr) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EInk.Background)
            .statusBarsPadding()
    ) {
        TopBar(
            title = tr(selectedTab.title),
            notifications = badges[MainTab.NOTIFICATIONS] ?: 0,
            notificationsOpen = selectedTab == MainTab.NOTIFICATIONS,
            onNotifications = { onTabSelected(MainTab.NOTIFICATIONS) }
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
                MainTab.LISTS -> listsContent()
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
                                onSetUpStack = { onItemClick(com.example.btcwidget.SettingsKey("BITCOIN")) },
                                history = history,
                                onShare = { key -> onShare(key, currentState.data) }
                            )
                            else -> Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 12.dp)
                            ) {
                                HeaderSection(title = "BLOCK", subtitle = tr("Bitcoin at a glance"))
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
                MainTab.SETTINGS -> settingsContent()
            }
        }

        BottomBar(
            selectedTab = selectedTab,
            badges = badges,
            menuOpen = drawerState.isOpen || selectedTab in DRAWER_PAGES,
            menuIcon = menuButtonIcon,
            onTab = onTabSelected,
            onMenu = { scope.launch { if (drawerState.isOpen) drawerState.close() else drawerState.open() } }
        )
    }
    }
    }
    }
}

private val Accent = Color(0xFFF7931A)

/**
 * The bottom bar, as on X: icons only, outlined, the open page's filled; on the page's own
 * background with a hairline above. Unread counts sit on the icon in an orange bubble. Last,
 * your avatar opens the side panel (ringed in orange while it or one of its pages is open).
 */
@Composable
private fun BottomBar(
    selectedTab: MainTab,
    badges: Map<MainTab, Int>,
    menuOpen: Boolean,
    menuIcon: @Composable () -> Unit,
    onTab: (MainTab) -> Unit,
    onMenu: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(EInk.Background)) {
        HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BAR_TABS.forEach { tab ->
                val count = badges[tab] ?: 0
                val selected = selectedTab == tab
                BarSlot(
                    label = if (count > 0) tr("{0}, {1} unread", tr(tab.label), count) else tr(tab.label),
                    selected = selected,
                    onClick = { onTab(tab) }
                ) {
                    Box {
                        Icon(tab.icon.of(selected), contentDescription = null, tint = EInk.Value, modifier = Modifier.size(27.dp))
                        if (count > 0) CountBadge(count, Modifier.align(Alignment.TopEnd).offset(x = 9.dp, y = (-6).dp))
                    }
                }
            }
            BarSlot(label = tr("Open menu"), selected = menuOpen, onClick = onMenu) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape)
                        .then(if (menuOpen) Modifier.border(2.dp, Accent, CircleShape) else Modifier)
                        .padding(if (menuOpen) 3.dp else 1.dp),
                    contentAlignment = Alignment.Center
                ) { menuIcon() }
            }
        }
    }
}

@Composable
private fun RowScope.BarSlot(label: String, selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.weight(1f).fillMaxHeight()
            .selectable(
                selected = selected, onClick = onClick, role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 26.dp)
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) { content() }
}

/** An unread count: a small orange bubble, "99+" past 99. */
@Composable
private fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.height(18.dp).widthIn(min = 18.dp).clip(CircleShape).background(Accent).border(1.5.dp, EInk.Background, CircleShape)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) { Text(if (count > 99) "99+" else "$count", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold, lineHeight = 10.sp) }
}

/** Title row above every page, with Notifications (and their unread count) at the right, as on X. */
@Composable
private fun TopBar(title: String, notifications: Int, notificationsOpen: Boolean, onNotifications: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(start = 16.dp, end = 6.dp)
    ) {
        Text(
            text = title,
            color = EInk.Value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        val label = if (notifications > 0) tr("{0}, {1} unread", tr("Notifications"), notifications) else tr("Notifications")
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = tr("Notifications"), onClick = onNotifications)
                .semantics { contentDescription = label }
        ) {
            BadgedBox(badge = {
                if (notifications > 0) CountBadge(notifications)
            }) {
                Icon(
                    com.example.btcwidget.ui.social.NavIcons.Bell.of(notificationsOpen),
                    contentDescription = null,
                    tint = EInk.Value,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
}

/**
 * The side panel, as on X: your account at the top, then your pages in large type, Settings
 * last under a line. Opens from the right, by the button at the right of the bottom bar.
 */
@Composable
private fun SideBar(selectedTab: MainTab, header: @Composable () -> Unit, onOpen: (MainTab) -> Unit, onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        Box(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)) { header() }
        HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke, modifier = Modifier.padding(bottom = 6.dp))
        DRAWER_PAGES.forEach { tab ->
            if (tab == MainTab.SETTINGS) HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke, modifier = Modifier.padding(vertical = 6.dp))
            val selected = selectedTab == tab
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 2.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                    .background(if (selected) EInk.Body else Color.Transparent)
                    .clickable(onClickLabel = tr("Open {0}", tr(tab.title))) { onOpen(tab) }
                    .padding(horizontal = 14.dp, vertical = 13.dp)
            ) {
                Icon(tab.icon.of(selected), contentDescription = null, tint = if (selected) Accent else EInk.Value, modifier = Modifier.size(26.dp))
                Text(
                    text = tr(tab.title),
                    color = EInk.Value,
                    fontSize = 20.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(start = 20.dp)
                )
            }
        }
    }
}

/**
 * Pages. The bottom bar holds Home, Search, Bitcoin and Messages, then the side panel's button;
 * Notifications opens from the top bar; the side panel holds Profile, Lists, Bookmarks,
 * Communities, Widgets, Price alerts and Settings. [label] names the bar
 * icon for TalkBack, [title] is shown in the top bar and side bar.
 */
enum class MainTab(val label: String, val icon: com.example.btcwidget.ui.social.NavIcon, val title: String = label) {
    HOME("Home", com.example.btcwidget.ui.social.NavIcons.Home),
    SEARCH("Search", com.example.btcwidget.ui.social.NavIcons.Search),
    DASHBOARD("Bitcoin", com.example.btcwidget.ui.social.NavIcons.Bitcoin),
    NOTIFICATIONS("Notifications", com.example.btcwidget.ui.social.NavIcons.Bell),
    MESSAGES("Messages", com.example.btcwidget.ui.social.NavIcons.Messages),
    PROFILE("Profile", com.example.btcwidget.ui.social.NavIcons.Profile),
    BOOKMARKS("Bookmarks", com.example.btcwidget.ui.social.NavIcons.Bookmarks),
    COMMUNITIES("Communities", com.example.btcwidget.ui.social.NavIcons.Communities),
    LISTS("Lists", com.example.btcwidget.ui.social.NavIcons.Lists),
    WIDGETS("Widgets", com.example.btcwidget.ui.social.NavIcons.Widgets),
    ALERTS("Price alerts", com.example.btcwidget.ui.social.NavIcons.PriceAlerts),
    SETTINGS("Settings", com.example.btcwidget.ui.social.NavIcons.Settings)
}

// Getters, not stored values: a top-level val that touches MainTab would initialize the
// enum while this file's other top-level vals are still null (that crashed the app once).
private val BAR_TABS get() = listOf(MainTab.HOME, MainTab.SEARCH, MainTab.DASHBOARD, MainTab.MESSAGES)
private val DRAWER_PAGES get() = listOf(MainTab.PROFILE, MainTab.LISTS, MainTab.BOOKMARKS, MainTab.COMMUNITIES, MainTab.WIDGETS, MainTab.ALERTS, MainTab.SETTINGS)

/** Pages with sideways drags of their own, where a swipe must not open the side bar. */
private val DRAG_PAGES get() = setOf(MainTab.DASHBOARD, MainTab.WIDGETS)

