package com.example.btcwidget.ui.main

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.btcwidget.AppPrefs
import com.example.btcwidget.BlockclockMetrics
import com.example.btcwidget.ChartRange
import com.example.btcwidget.DataSources
import com.example.btcwidget.MetricCategory
import com.example.btcwidget.PriceData
import com.example.btcwidget.PricePoint
import com.example.btcwidget.PriceRepository
import com.example.btcwidget.StackUnit
import com.example.btcwidget.WidgetRefreshScheduler
import com.example.btcwidget.WidgetUpdater
import com.example.btcwidget.alerts.AlertNotifier
import com.example.btcwidget.alerts.AlertRule
import com.example.btcwidget.alerts.AlertStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the user can change, as shown on screen. Persisted through [AppPrefs] / [AlertStore]. */
data class SettingsState(
    val heroMetric: String,
    val currency: String,
    val refreshInterval: Int,
    /** On/off per mode, indexed by mode. */
    val modeStates: List<Boolean>,
    val modeOrder: List<String>,
    val collapsedCategories: Set<MetricCategory>,
    val stackSats: Long,
    val stackUnit: StackUnit,
    val dataSources: DataSources,
    val alerts: List<AlertRule>,
    val notificationsAllowed: Boolean
) {
    val hasStack: Boolean get() = stackSats > 0
}

/**
 * State and actions for the main screen. Owns market data (fetching, history) and the
 * user's settings: each action persists the change and applies its side effects
 * (redrawing widgets, rescheduling refreshes), so the UI only renders state and
 * forwards events. The UI keeps only what needs an Activity: the selected tab and the
 * notification-permission prompt.
 */
class MainScreenViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext get() = getApplication<Application>()

    private val _uiState = MutableStateFlow<MainScreenUiState>(MainScreenUiState.Loading)
    val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)

    /** True while a fetch is running; drives the pull-to-refresh indicator. */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _history = MutableStateFlow<List<PricePoint>>(emptyList())

    /** Hourly BTC/USD closes for the last week (oldest first), for the price chart. */
    val history: StateFlow<List<PricePoint>> = _history.asStateFlow()
    private var historyFetchedAt = 0L

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<SettingsState> = _settings.asStateFlow()

    /** App version for Settings > About, null if unavailable. */
    val appVersion: String? = runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
    }.getOrNull()

    private var started = false

    init {
        AppPrefs.removeObsoleteKeys(appContext)
    }

    private fun readSettings() = SettingsState(
        heroMetric = AppPrefs.heroMetric(appContext),
        currency = AppPrefs.currency(appContext),
        refreshInterval = AppPrefs.refreshInterval(appContext),
        modeStates = AppPrefs.enabledModes(appContext),
        modeOrder = AppPrefs.modeOrder(appContext),
        collapsedCategories = AppPrefs.collapsedCategories(appContext),
        stackSats = AppPrefs.stackSats(appContext),
        stackUnit = AppPrefs.stackUnit(appContext),
        dataSources = AppPrefs.dataSources(appContext),
        alerts = AlertStore.load(appContext),
        notificationsAllowed = AlertNotifier.canNotify(appContext)
    )

    /** Called when the screen first appears: show data, keep refreshes scheduled, redraw widgets. */
    fun start() {
        if (started) return
        started = true
        loadInitialData()
        WidgetRefreshScheduler.schedulePeriodic(appContext)
        WidgetUpdater.updateAllWidgets(appContext)
    }

    // ---- Settings actions ----

    fun selectHero(key: String) {
        AppPrefs.setHeroMetric(appContext, key)
        _settings.update { it.copy(heroMetric = key) }
    }

    fun setCurrency(currency: String) {
        AppPrefs.setCurrency(appContext, currency)
        _settings.update { it.copy(currency = currency) }
        WidgetUpdater.updateAllWidgets(appContext)
    }

    fun setRefreshInterval(minutes: Int) {
        AppPrefs.setRefreshInterval(appContext, minutes)
        _settings.update { it.copy(refreshInterval = minutes) }
        WidgetRefreshScheduler.schedulePeriodic(appContext, intervalChanged = true)
    }

    fun setModeOrder(order: List<String>) {
        AppPrefs.setModeOrder(appContext, order)
        _settings.update { it.copy(modeOrder = BlockclockMetrics.sanitizeOrder(order)) }
    }

    fun toggleCategory(category: MetricCategory) {
        val current = _settings.value.collapsedCategories
        val next = if (category in current) current - category else current + category
        AppPrefs.setCollapsedCategories(appContext, next)
        _settings.update { it.copy(collapsedCategories = next) }
    }

    /**
     * Switches a mode on or off for the widget. The last active mode can't be switched
     * off, so the widget always has something to show.
     */
    fun setModeEnabled(mode: Int, enabled: Boolean) {
        val s = _settings.value
        val active = s.modeStates.indices.count {
            BlockclockMetrics.isActive(it, s.modeStates[it], s.currency, s.hasStack)
        }
        if (!enabled && active <= 1) return
        AppPrefs.setModeEnabled(appContext, mode, enabled)
        _settings.update { st -> st.copy(modeStates = st.modeStates.toMutableList().also { it[mode] = enabled }) }
        // Only which modes are shown changed: redraw from cached data, no network fetch.
        WidgetUpdater.updateAllWidgets(appContext)
    }

    fun setStack(sats: Long, unit: StackUnit) {
        AppPrefs.setStack(appContext, sats, unit)
        _settings.update { it.copy(stackSats = sats, stackUnit = unit) }
        WidgetUpdater.updateAllWidgets(appContext)
    }

    fun setDataSources(sources: DataSources) {
        AppPrefs.setDataSources(appContext, sources)
        _settings.update { it.copy(dataSources = sources) }
        // Show the new source's price right away.
        refreshData()
    }

    /**
     * Saves an alert. Returns true when the UI should ask for the notification permission
     * (an alert was switched on, Android 13+, not yet granted).
     */
    fun updateAlert(rule: AlertRule): Boolean {
        val current = (_uiState.value as? MainScreenUiState.Success)?.data
        AlertStore.save(appContext, rule, current)
        _settings.update { it.copy(alerts = AlertStore.load(appContext)) }
        return if (rule.enabled) {
            // Alerts need the background refresh even with no widget placed.
            WidgetRefreshScheduler.schedulePeriodic(appContext)
            !AlertNotifier.canNotify(appContext) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        } else {
            WidgetRefreshScheduler.cancelIfUnused(appContext)
            false
        }
    }

    /** Re-reads whether notifications are allowed (after the prompt, or on returning to the app). */
    fun refreshNotificationPermission() {
        _settings.update { it.copy(notificationsAllowed = AlertNotifier.canNotify(appContext)) }
    }

    // ---- Market data ----

    /**
     * Shows the cached history, then fetches a fresh week unless one was fetched in the
     * last [HISTORY_MAX_AGE_MS] (hourly candles change slowly). [force] skips that check.
     */
    fun loadHistory(force: Boolean = false) {
        if (_history.value.isEmpty()) _history.value = AppPrefs.history(appContext)
        val now = System.currentTimeMillis()
        if (!force && now - historyFetchedAt < HISTORY_MAX_AGE_MS) return
        historyFetchedAt = now
        viewModelScope.launch(Dispatchers.IO) {
            val fresh = runCatching { PriceRepository.fetchHistory(ChartRange.WEEK.hours) }.getOrNull()
            if (fresh != null && fresh.size >= 2) {
                _history.value = fresh
                AppPrefs.setHistory(appContext, fresh)
            } else {
                historyFetchedAt = 0L // try again next time
            }
        }
    }

    /** Shows cached data immediately and refreshes in the background if it is old. */
    private fun loadInitialData() {
        loadHistory()
        val cached = PriceRepository.getFromPrefs(appContext)
        if (cached == null) {
            refreshData()
            return
        }
        _uiState.value = MainScreenUiState.Success(cached)
        if (System.currentTimeMillis() - cached.timestamp > REFRESH_ON_OPEN_AFTER_MS) {
            refreshData()
        }
    }

    /**
     * Fetches fresh data. While data is already on screen it stays visible during the
     * fetch (no loading flash), and a failed fetch keeps it instead of showing an error.
     */
    fun refreshData() {
        // One fetch at a time: a pull while one is running just waits for it.
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        loadHistory(force = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (_uiState.value !is MainScreenUiState.Success) {
                    _uiState.value = MainScreenUiState.Loading
                }
                val data = PriceRepository.fetchPriceData(appContext)
                if (data != null) {
                    PriceRepository.saveToPrefs(appContext, data)
                    _uiState.value = MainScreenUiState.Success(data)
                    WidgetUpdater.updateAllWidgets(appContext)
                    AlertNotifier.checkAndNotify(appContext, data)
                } else if (_uiState.value !is MainScreenUiState.Success) {
                    _uiState.value = MainScreenUiState.Error(
                        Exception("Failed to fetch price data. Please verify your internet connection.")
                    )
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private companion object {
        const val REFRESH_ON_OPEN_AFTER_MS = 60_000L
        const val HISTORY_MAX_AGE_MS = 10 * 60_000L
    }
}

sealed interface MainScreenUiState {
    object Loading : MainScreenUiState
    data class Error(val throwable: Throwable) : MainScreenUiState
    data class Success(val data: PriceData) : MainScreenUiState
}
