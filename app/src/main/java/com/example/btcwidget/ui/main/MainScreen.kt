package com.example.btcwidget.ui.main

import android.content.Context
import com.example.btcwidget.WidgetUpdater
import com.example.btcwidget.WidgetRefreshScheduler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import java.util.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings



internal fun formatLabelForTheme(label: String, currentTheme: Int): String {
    if (currentTheme == 11) {
        if (label.isEmpty()) return ""
        val words = label.split(" ")
        return words.joinToString(" ") { word ->
            if (word.contains("/")) {
                word.split("/").joinToString("/") { sub ->
                    sub.lowercase(Locale.US).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
                }
            } else if (word.lowercase(Locale.US) == "usd" || word.lowercase(Locale.US) == "btc" || word.lowercase(Locale.US) == "thb") {
                word.uppercase(Locale.US)
            } else {
                word.lowercase(Locale.US).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            }
        }
    }
    return label
}

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val prefs = remember { context.getSharedPreferences("blockclock_prefs", Context.MODE_PRIVATE) }
    val initialTheme = prefs.getInt("blockclock_theme", 0)
    val sanitizedTheme = if (initialTheme == 0 || initialTheme == 1 || initialTheme == 9) initialTheme else 0
    var currentTheme by remember { mutableStateOf(sanitizedTheme) }
    if (initialTheme != sanitizedTheme) {
        prefs.edit().putInt("blockclock_theme", sanitizedTheme).apply()
    }
    var showSettings by remember { mutableStateOf(false) }
    var heroMetric by remember { mutableStateOf(prefs.getString("hero_metric", "BLOCK") ?: "BLOCK") }
    var currencySetting by remember { mutableStateOf(prefs.getString("currency", "USD") ?: "USD") }
    var hapticEnabled by remember { mutableStateOf(prefs.getBoolean("haptic_enabled", true)) }
    var refreshInterval by remember { mutableStateOf(prefs.getInt("refresh_interval", 30)) }

    // Mode states for Settings
    val modeStates = remember {
        mutableStateListOf<Boolean>().apply {
            addAll((0..12).map { prefs.getBoolean("mode_enabled_$it", true) })
        }
    }

    val onHeroMetricSelected: (String) -> Unit = { metric ->
        prefs.edit().putString("hero_metric", metric).apply()
        heroMetric = metric
    }

    LaunchedEffect(Unit) {
        viewModel.loadInitialData(context)
        WidgetRefreshScheduler.schedulePeriodic(context)
    }

    val onThemeSelected: (Int) -> Unit = { newTheme ->
        prefs.edit().putInt("blockclock_theme", newTheme).apply()
        currentTheme = newTheme
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
        val enabledCount = modeStates.count { it }
        if (checked || enabledCount > 1) {
            prefs.edit().putBoolean("mode_enabled_$index", checked).apply()
            modeStates[index] = checked
            WidgetUpdater.updateAllWidgets(context)
            viewModel.refreshData(context)
        }
    }

    MainScreenContent(
        state = state,
        currentTheme = currentTheme,
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
        onThemeSelected = onThemeSelected,
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
    currentTheme: Int,
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
    onThemeSelected: (Int) -> Unit,
    onShowSettingsChange: (Boolean) -> Unit,
    onModeToggle: (Int, Boolean) -> Unit,
    onRefresh: () -> Unit,
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeBgColor: Color
    val themeCardBgColor: Color
    val themeValueColor: Color
    val themeLabelColor: Color
    val themeSubTextColor: Color
    val themeBorderColor: Color

    when (currentTheme) {
        1 -> { // White / Light Theme
            themeBgColor = Color(0xFFF2F2F7)
            themeCardBgColor = Color(0x73FFFFFF)
            themeValueColor = Color(0xFF000000)
            themeLabelColor = Color(0xFF8E8E93)
            themeSubTextColor = Color(0xFF8E8E93)
            themeBorderColor = Color(0xFF007AFF)
        }
        9 -> { // Orange Pill Theme
            themeBgColor = Color(0xFF050200)
            themeCardBgColor = Color(0x731E0E05)
            themeValueColor = Color(0xFFF7931A)
            themeLabelColor = Color(0xFFA66210)
            themeSubTextColor = Color(0xFFA66210)
            themeBorderColor = Color(0xFFF7931A)
        }
        else -> { // Dark Theme (Default / Theme 0)
            themeBgColor = Color(0xFF000000)
            themeCardBgColor = Color(0x661C1C1E)
            themeValueColor = Color(0xFFFFFFFF)
            themeLabelColor = Color(0xFF8E8E93)
            themeSubTextColor = Color(0xFF8E8E93)
            themeBorderColor = Color(0xFF007AFF)
        }
    }

    val backgroundModifier = when (currentTheme) {
        1 -> { // White / Light Theme
            Modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(
                listOf(Color(0xFFF5E6E8), Color(0xFFD4E6F1), Color(0xFFEAF2F8))
            ))
        }
        9 -> { // Orange Pill Theme
            Modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(
                listOf(Color(0xFF2C0F00), Color(0xFF140700), Color(0xFF050200))
            ))
        }
        else -> { // Dark Theme (Default / Theme 0)
            Modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(
                listOf(Color(0xFF141527), Color(0xFF2A1B4E), Color(0xFF0B0C16))
            ))
        }
    }

    Box(
        modifier = backgroundModifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (showSettings) {
                    SettingsScreen(
                        currentTheme = currentTheme,
                        onThemeSelected = onThemeSelected,
                        onModeToggle = onModeToggle,
                        modeStates = modeStates,
                        currencySetting = currencySetting,
                        onCurrencySelected = onCurrencySelected,
                        refreshInterval = refreshInterval,
                        onRefreshIntervalSelected = onRefreshIntervalSelected,
                        hapticEnabled = hapticEnabled,
                        onHapticToggle = onHapticToggle,
                        cardBgColor = themeCardBgColor,
                        borderColor = themeBorderColor,
                        valueColor = themeValueColor,
                        labelColor = themeLabelColor,
                        subTextColor = themeSubTextColor
                    )
                } else {
                    Column(
                        modifier = modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        HeaderSection(
                            titleColor = themeBorderColor,
                            subtitleColor = themeLabelColor,
                            currentTheme = currentTheme
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        when (val currentState = state) {
                            is MainScreenUiState.Loading -> {
                                LoadingScreen()
                            }
                            is MainScreenUiState.Success -> {
                                DashboardContent(
                                    data = currentState.data,
                                    heroMetric = heroMetric,
                                    onHeroMetricSelected = onHeroMetricSelected,
                                    cardBgColor = themeCardBgColor,
                                    borderColor = themeBorderColor,
                                    valueColor = themeValueColor,
                                    labelColor = themeLabelColor,
                                    subTextColor = themeSubTextColor,
                                    enabledModes = modeStates,
                                    currencySetting = currencySetting,
                                    currentTheme = currentTheme
                                )
                            }
                            is MainScreenUiState.Error -> {
                                ErrorScreen(
                                    error = currentState.throwable,
                                    onRefresh = onRefresh
                                )
                            }
                        }
                    }
                }
            }

            val selectedAccent = if (currentTheme == 9) Color(0xFFF7931A) else Color(0xFF007AFF)
            val navBg = when (currentTheme) {
                1 -> Color(0x99F2F2F7)
                9 -> Color(0x99140700)
                else -> Color(0x991C1C1E)
            }
            NavigationBar(
                containerColor = navBg,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home", fontFamily = FontFamily.Default) },
                    selected = !showSettings,
                    onClick = { onShowSettingsChange(false) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = selectedAccent,
                        selectedTextColor = selectedAccent,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = Color(0xFF8E8E93),
                        unselectedTextColor = Color(0xFF8E8E93)
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontFamily = FontFamily.Default) },
                    selected = showSettings,
                    onClick = { onShowSettingsChange(true) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = selectedAccent,
                        selectedTextColor = selectedAccent,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = Color(0xFF8E8E93),
                        unselectedTextColor = Color(0xFF8E8E93)
                    )
                )
            }
        }
    }
}
