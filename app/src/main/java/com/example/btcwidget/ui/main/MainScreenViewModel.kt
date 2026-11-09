package com.example.btcwidget.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.btcwidget.WidgetUpdater
import com.example.btcwidget.alerts.AlertNotifier
import com.example.btcwidget.PriceData
import com.example.btcwidget.PricePoint
import com.example.btcwidget.AppPrefs
import com.example.btcwidget.ChartRange
import com.example.btcwidget.PriceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainScreenViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<MainScreenUiState>(MainScreenUiState.Loading)
    val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)

    /** True while a fetch is running; drives the pull-to-refresh indicator. */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _history = MutableStateFlow<List<PricePoint>>(emptyList())

    /** Hourly BTC/USD closes for the last week (oldest first), for the price chart. */
    val history: StateFlow<List<PricePoint>> = _history.asStateFlow()
    private var historyFetchedAt = 0L

    /**
     * Shows the cached history, then fetches a fresh week unless one was fetched in the
     * last [HISTORY_MAX_AGE_MS] (hourly candles change slowly). [force] skips that check.
     */
    fun loadHistory(context: Context, force: Boolean = false) {
        val appContext = context.applicationContext
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
    fun loadInitialData(context: Context) {
        loadHistory(context)
        val cached = PriceRepository.getFromPrefs(context)
        if (cached == null) {
            refreshData(context)
            return
        }
        _uiState.value = MainScreenUiState.Success(cached)
        if (System.currentTimeMillis() - cached.timestamp > REFRESH_ON_OPEN_AFTER_MS) {
            refreshData(context)
        }
    }

    /**
     * Fetches fresh data. While data is already on screen it stays visible during the
     * fetch (no loading flash), and a failed fetch keeps it instead of showing an error.
     */
    fun refreshData(context: Context) {
        val appContext = context.applicationContext
        // One fetch at a time: a pull while one is running just waits for it.
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        loadHistory(context, force = true)
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
