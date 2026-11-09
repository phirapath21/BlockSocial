package com.example.btcwidget.ui.main

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.btcwidget.BlockclockMetrics
import com.example.btcwidget.BtcWidgetProvider
import com.example.btcwidget.WidgetRefreshScheduler
import com.example.btcwidget.WidgetUpdater
import com.example.btcwidget.alerts.AlertNotifier
import com.example.btcwidget.alerts.AlertRule
import com.example.btcwidget.alerts.AlertStore

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val prefs = remember { context.getSharedPreferences("blockclock_prefs", Context.MODE_PRIVATE) }
    var showSettings by remember { mutableStateOf(false) }
    var heroMetric by remember { mutableStateOf(prefs.getString("hero_metric", "BLOCK") ?: "BLOCK") }
    var currencySetting by remember { mutableStateOf(prefs.getString("currency", "USD") ?: "USD") }
    var hapticEnabled by remember { mutableStateOf(prefs.getBoolean("haptic_enabled", true)) }
    var refreshInterval by remember { mutableStateOf(prefs.getInt("refresh_interval", 30)) }

    // Mode states for Settings
    val modeStates = remember {
        mutableStateListOf<Boolean>().apply {
            addAll((0 until BlockclockMetrics.MODE_COUNT).map { prefs.getBoolean("mode_enabled_$it", true) })
        }
    }

    val onHeroMetricSelected: (String) -> Unit = { metric ->
        prefs.edit().putString("hero_metric", metric).apply()
        heroMetric = metric
    }

    LaunchedEffect(Unit) {
        viewModel.loadInitialData(context)
        WidgetRefreshScheduler.schedulePeriodic(context)
        // Redraw placed widgets with the current layout and cached data.
        WidgetUpdater.updateAllWidgets(context)
    }

    val onCurrencySelected: (String) -> Unit = { newCurrency ->
        prefs.edit().putString("currency", newCurrency).apply()
        currencySetting = newCurrency
        WidgetUpdater.updateAllWidgets(context)
    }

    val onRefreshIntervalSelected: (Int) -> Unit = { newInterval ->
        prefs.edit().putInt("refresh_interval", newInterval).apply()
        refreshInterval = newInterval
        WidgetRefreshScheduler.schedulePeriodic(context, intervalChanged = true)
    }

    val onHapticToggle: (Boolean) -> Unit = { enabled ->
        prefs.edit().putBoolean("haptic_enabled", enabled).apply()
        hapticEnabled = enabled
    }

    var modeOrder by remember {
        mutableStateOf(BlockclockMetrics.parseOrder(prefs.getString(BtcWidgetProvider.KEY_MODE_ORDER, null)))
    }
    val onModeOrderChange: (List<String>) -> Unit = { newOrder ->
        prefs.edit().putString(BtcWidgetProvider.KEY_MODE_ORDER, BlockclockMetrics.serializeOrder(newOrder)).apply()
        modeOrder = BlockclockMetrics.sanitizeOrder(newOrder)
    }

    var alerts by remember { mutableStateOf(AlertStore.load(context)) }
    var notificationsAllowed by remember { mutableStateOf(AlertNotifier.canNotify(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = AlertNotifier.canNotify(context)
    }
    val onAlertChange: (AlertRule) -> Unit = { rule ->
        val current = (state as? MainScreenUiState.Success)?.data
        AlertStore.save(context, rule, current)
        alerts = AlertStore.load(context)
        if (rule.enabled) {
            // Alerts need the background refresh even with no widget placed.
            WidgetRefreshScheduler.schedulePeriodic(context)
            if (!AlertNotifier.canNotify(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            WidgetRefreshScheduler.cancelIfUnused(context)
        }
    }

    val onModeToggle: (Int, Boolean) -> Unit = { index, checked ->
        val activeCount = modeStates.indices.count {
            BlockclockMetrics.isActive(it, modeStates[it], currencySetting)
        }
        if (checked || activeCount > 1) {
            prefs.edit().putBoolean("mode_enabled_$index", checked).apply()
            modeStates[index] = checked
            // Only which modes are shown changed: redraw from cached data, no network fetch.
            WidgetUpdater.updateAllWidgets(context)
        }
    }

    MainScreenContent(
        state = state,
        showSettings = showSettings,
        heroMetric = heroMetric,
        modeStates = modeStates,
        currencySetting = currencySetting,
        onCurrencySelected = onCurrencySelected,
        refreshInterval = refreshInterval,
        onRefreshIntervalSelected = onRefreshIntervalSelected,
        hapticEnabled = hapticEnabled,
        onHapticToggle = onHapticToggle,
        onHeroMetricSelected = onHeroMetricSelected,
        onShowSettingsChange = { newValue: Boolean -> showSettings = newValue },
        onModeToggle = onModeToggle,
        onRefresh = { viewModel.refreshData(context) },
        isRefreshing = isRefreshing,
        modeOrder = modeOrder,
        onModeOrderChange = onModeOrderChange,
        onItemClick = onItemClick,
        modifier = modifier,
        alerts = alerts,
        onAlertChange = onAlertChange,
        notificationsAllowed = notificationsAllowed
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContent(
    state: MainScreenUiState,
    showSettings: Boolean,
    heroMetric: String,
    modeStates: List<Boolean>,
    currencySetting: String,
    onCurrencySelected: (String) -> Unit,
    refreshInterval: Int,
    onRefreshIntervalSelected: (Int) -> Unit,
    hapticEnabled: Boolean,
    onHapticToggle: (Boolean) -> Unit,
    onHeroMetricSelected: (String) -> Unit,
    onShowSettingsChange: (Boolean) -> Unit,
    onModeToggle: (Int, Boolean) -> Unit,
    onRefresh: () -> Unit,
    onItemClick: (NavKey) -> Unit,
    isRefreshing: Boolean = false,
    modeOrder: List<String> = BlockclockMetrics.MODE_KEYS,
    onModeOrderChange: (List<String>) -> Unit = {},
    modifier: Modifier = Modifier,
    alerts: List<AlertRule> = emptyList(),
    onAlertChange: (AlertRule) -> Unit = {},
    notificationsAllowed: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EInk.Background)
            .statusBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (showSettings) {
                SettingsScreen(
                    modeStates = modeStates,
                    onModeToggle = onModeToggle,
                    currencySetting = currencySetting,
                    onCurrencySelected = onCurrencySelected,
                    refreshInterval = refreshInterval,
                    onRefreshIntervalSelected = onRefreshIntervalSelected,
                    hapticEnabled = hapticEnabled,
                    onHapticToggle = onHapticToggle,
                    alerts = alerts,
                    onAlertChange = onAlertChange,
                    notificationsAllowed = notificationsAllowed,
                    modeOrder = modeOrder
                )
            } else {
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
                            enabledModes = modeStates,
                            currencySetting = currencySetting,
                            order = modeOrder,
                            onOrderChange = onModeOrderChange
                        )
                        else -> Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp)
                        ) {
                            HeaderSection(title = "BLOCKFACE", subtitle = "Bitcoin at a glance")
                            if (currentState is MainScreenUiState.Error) {
                                ErrorScreen(error = currentState.throwable, onRefresh = onRefresh)
                            } else {
                                LoadingScreen()
                            }
                        }
                    }
                }
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
            NavigationBarItem(
                icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                label = { Text("DASHBOARD", fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 1.sp) },
                selected = !showSettings,
                onClick = { onShowSettingsChange(false) },
                colors = itemColors
            )
            NavigationBarItem(
                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                label = { Text("SETTINGS", fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 1.sp) },
                selected = showSettings,
                onClick = { onShowSettingsChange(true) },
                colors = itemColors
            )
        }
    }
}
