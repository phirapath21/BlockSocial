package com.example.btcwidget.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.btcwidget.PriceData
import org.junit.Rule
import org.junit.Test

/** UI tests for [MainScreenContent] with fixed data (no network, no prefs). */
class MainScreenTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val sample = PriceData(
        btcUsd = 74_455.0, btcThb = 2_415_000.0, moscowTime = 1343, mstrUsd = 412.6,
        mstrBtc = 0.005574, blockHeight = 925_000, mstrBtcHeld = 640_031.0,
        btcCirculation = 19_912_345.0, timestamp = 0L
    )

    private fun setContent(currency: String = "USD") {
        composeTestRule.setContent {
            var showSettings by remember { mutableStateOf(false) }
            var hero by remember { mutableStateOf("PRICE") }
            MainScreenContent(
                state = MainScreenUiState.Success(sample),
                showSettings = showSettings,
                heroMetric = hero,
                modeStates = List(13) { true },
                currencySetting = currency,
                onCurrencySelected = {},
                refreshInterval = 30,
                onRefreshIntervalSelected = {},
                hapticEnabled = true,
                onHapticToggle = {},
                onHeroMetricSelected = { hero = it },
                onShowSettingsChange = { showSettings = it },
                onModeToggle = { _, _ -> },
                onRefresh = {},
                onItemClick = {}
            )
        }
    }

    @Test
    fun dashboard_showsHeroPanelForSelectedMetric() {
        setContent()
        composeTestRule.onNodeWithText("SATOSHI DASHBOARD").assertExists()
        composeTestRule.onNodeWithContentDescription("BTC USD \$74455. Market price of bitcoin").assertExists()

        composeTestRule.onNodeWithText("BLOCK").performClick()
        composeTestRule.onNodeWithContentDescription("BLOCK HGHT 925000. Bitcoin block height").assertExists()
    }

    @Test
    fun settings_openFromNavigationBar() {
        setContent()
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        // Header subtitle; "SETTINGS" alone also matches the navigation label.
        composeTestRule.onNodeWithText("CONSOLE CONFIGURATION").assertExists()
        composeTestRule.onNodeWithText("Currency").assertExists()
    }

    @Test
    fun settings_moscowTimeLockedOffInThb() {
        setContent(currency = "THB")
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.onNodeWithText("USD only · off while THB is selected").performScrollTo().assertExists()
    }
}
