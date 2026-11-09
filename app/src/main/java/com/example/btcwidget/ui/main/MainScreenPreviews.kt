package com.example.btcwidget.ui.main

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import com.example.btcwidget.theme.BTCWidgetTheme
import com.example.btcwidget.PriceData
import java.util.*

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    val sampleData = PriceData(
        btcUsd = 65000.0,
        btcThb = 2200000.0,
        moscowTime = 1538,
        mstrUsd = 1600.0,
        mstrBtc = 0.00012,
        blockHeight = 840000,
        mstrBtcHeld = 214400.0,
        btcCirculation = 19680000.0,
        timestamp = System.currentTimeMillis(),
        feeFastest = 25,
        feeHalfHour = 20,
        feeHour = 10
    )

    BTCWidgetTheme {
        MainScreenContent(
            state = MainScreenUiState.Success(sampleData),
            selectedTab = MainTab.DASHBOARD,
            heroMetric = "BLOCK",
            modeStates = List(13) { true },
            currencySetting = "USD",
            onCurrencySelected = {},
            refreshInterval = 30,
            onRefreshIntervalSelected = {},
            hapticEnabled = true,
            onHapticToggle = {},
            onHeroMetricSelected = {},
            onTabSelected = {},
            onModeToggle = { _, _ -> },
            onRefresh = {},
            onItemClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenSettingsPreview() {
    val sampleData = PriceData(
        btcUsd = 65000.0,
        btcThb = 2200000.0,
        moscowTime = 1538,
        mstrUsd = 1600.0,
        mstrBtc = 0.00012,
        blockHeight = 840000,
        mstrBtcHeld = 214400.0,
        btcCirculation = 19680000.0,
        timestamp = System.currentTimeMillis(),
        feeFastest = 25,
        feeHalfHour = 20,
        feeHour = 10
    )

    BTCWidgetTheme {
        MainScreenContent(
            state = MainScreenUiState.Success(sampleData),
            selectedTab = MainTab.SETTINGS,
            heroMetric = "BLOCK",
            modeStates = List(13) { true },
            currencySetting = "USD",
            onCurrencySelected = {},
            refreshInterval = 30,
            onRefreshIntervalSelected = {},
            hapticEnabled = true,
            onHapticToggle = {},
            onHeroMetricSelected = {},
            onTabSelected = {},
            onModeToggle = { _, _ -> },
            onRefresh = {},
            onItemClick = {}
        )
    }
}
