package com.example.btcwidget.ui.main

import android.content.Context
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.btcwidget.WidgetRefreshScheduler
import com.example.btcwidget.WidgetUpdater

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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

    val onModeToggle: (Int, Boolean) -> Unit = { index, checked ->
        val activeCount = modeStates.indices.count {
            BlockclockMetrics.isActive(it, modeStates[it], currencySetting)
        }
        if (checked || activeCount > 1) {
            prefs.edit().putBoolean("mode_enabled_$index", checked).apply()
            modeStates[index] = checked
            WidgetUpdater.updateAllWidgets(context)
            viewModel.refreshData(context)
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
        onItemClick = onItemClick,
        modifier = modifier
    )
}

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
    modifier: Modifier = Modifier
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
                    onHapticToggle = onHapticToggle
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp)
                ) {
                    HeaderSection(title = "SATOSHI DASHBOARD", subtitle = "Blockclock console")

                    when (val currentState = state) {
                        is MainScreenUiState.Loading -> LoadingScreen()
                        is MainScreenUiState.Success -> DashboardContent(
                            data = currentState.data,
                            heroMetric = heroMetric,
                            onHeroMetricSelected = onHeroMetricSelected,
                            enabledModes = modeStates,
                            currencySetting = currencySetting
                        )
                        is MainScreenUiState.Error -> ErrorScreen(
                            error = currentState.throwable,
                            onRefresh = onRefresh
                        )
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
