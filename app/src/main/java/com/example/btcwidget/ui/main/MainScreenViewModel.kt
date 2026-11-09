package com.example.btcwidget.ui.main

import android.content.Context
import android.content.Intent
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

    fun loadInitialData(context: Context) {
        val cached = PriceRepository.getFromPrefs(context)
        if (cached != null) {
            _uiState.value = MainScreenUiState.Success(cached)
        } else {
            refreshData(context)
        }
    }

    fun refreshData(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = MainScreenUiState.Loading
            val data = PriceRepository.fetchPriceData(context)
            if (data != null) {
                PriceRepository.saveToPrefs(context, data)
                _uiState.value = MainScreenUiState.Success(data)

                // Trigger widget update directly
                WidgetUpdater.updateAllWidgets(context)
            } else {
                _uiState.value = MainScreenUiState.Error(
                    Exception("Failed to fetch price data. Please verify your internet connection.")
                )
            }
        }
    }
}

sealed interface MainScreenUiState {
    object Loading : MainScreenUiState
    data class Error(val throwable: Throwable) : MainScreenUiState
    data class Success(val data: PriceData) : MainScreenUiState
}
