package com.example.btcwidget.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.btcwidget.WidgetUpdater
import com.example.btcwidget.PriceData
import com.example.btcwidget.PriceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainScreenViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<MainScreenUiState>(MainScreenUiState.Loading)
    val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()

    /** Shows cached data immediately and refreshes in the background if it is old. */
    fun loadInitialData(context: Context) {
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
        viewModelScope.launch(Dispatchers.IO) {
            if (_uiState.value !is MainScreenUiState.Success) {
                _uiState.value = MainScreenUiState.Loading
            }
            val data = PriceRepository.fetchPriceData(appContext)
            if (data != null) {
                PriceRepository.saveToPrefs(appContext, data)
                _uiState.value = MainScreenUiState.Success(data)
                WidgetUpdater.updateAllWidgets(appContext)
            } else if (_uiState.value !is MainScreenUiState.Success) {
                _uiState.value = MainScreenUiState.Error(
                    Exception("Failed to fetch price data. Please verify your internet connection.")
                )
            }
        }
    }

    private companion object {
        const val REFRESH_ON_OPEN_AFTER_MS = 60_000L
    }
}

sealed interface MainScreenUiState {
    object Loading : MainScreenUiState
    data class Error(val throwable: Throwable) : MainScreenUiState
    data class Success(val data: PriceData) : MainScreenUiState
}
