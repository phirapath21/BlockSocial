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
            var tab by remember { mutableStateOf(MainTab.DASHBOARD) }
            var hero by remember { mutableStateOf("PRICE") }
            MainScreenContent(
                state = MainScreenUiState.Success(sample),
                selectedTab = tab,
                heroMetric = hero,
                modeStates = List(13) { true },
                currencySetting = currency,
                onCurrencySelected = {},
                refreshInterval = 30,
                onRefreshIntervalSelected = {},
                hapticEnabled = true,
                onHapticToggle = {},
                onHeroMetricSelected = { hero = it },
                onTabSelected = { tab = it },
                onModeToggle = { _, _ -> },
                onRefresh = {},
                onItemClick = {}
            )
        }
    }

    @Test
    fun dashboard_showsHeroPanelForSelectedMetric() {
        setContent()
        composeTestRule.onNodeWithText("BLOCKFACE").assertExists()
        composeTestRule.onNodeWithContentDescription("BTC USD \$74455. Market price of bitcoin").assertExists()

        composeTestRule.onNodeWithText("BLOCK").performClick()
        composeTestRule.onNodeWithContentDescription("BLOCK HGHT 925000. Bitcoin block height").assertExists()
    }

    @Test
    fun settings_openFromNavigationBar() {
        setContent()
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        // Header subtitle; "SETTINGS" alone also matches the navigation label.
        composeTestRule.onNodeWithText("APP PREFERENCES").assertExists()
        composeTestRule.onNodeWithText("Currency").assertExists()
    }

    @Test
    fun dashboard_offMetricsStayVisibleWithTheirSwitch() {
        composeTestRule.setContent {
            val states = remember { androidx.compose.runtime.mutableStateListOf(*Array(14) { it != 1 }) } // SATS off
            MainScreenContent(
                state = MainScreenUiState.Success(sample),
                selectedTab = MainTab.DASHBOARD,
                heroMetric = "PRICE",
                modeStates = states,
                currencySetting = "USD",
                onCurrencySelected = {},
                refreshInterval = 30,
                onRefreshIntervalSelected = {},
                hapticEnabled = true,
                onHapticToggle = {},
                onHeroMetricSelected = {},
                onTabSelected = {},
                onModeToggle = { index, on -> states[index] = on },
                onRefresh = {},
                onItemClick = {}
            )
        }
        // The switched-off SATS panel is still shown, with its switch off.
        composeTestRule.onNodeWithContentDescription("SATS 1USD 1343. Sats per 1 dollar").assertExists()
        composeTestRule.onNodeWithText("OFF WIDGET").assertExists().performClick()
        composeTestRule.onNodeWithText("OFF WIDGET").assertDoesNotExist()
    }

    @Test
    fun widgets_tabCountsMetricsOnTheWidget() {
        setContent()
        composeTestRule.onNodeWithContentDescription("Widgets").performClick()
        // STACK needs holdings, so 13 of the 14 modes are available.
        composeTestRule.onNodeWithText("13 of 13 metrics on").assertExists()
    }

    @Test
    fun dashboard_sectionsCollapse() {
        composeTestRule.setContent {
            var collapsed by remember { mutableStateOf(emptySet<com.example.btcwidget.MetricCategory>()) }
            MainScreenContent(
                state = MainScreenUiState.Success(sample),
                selectedTab = MainTab.DASHBOARD,
                heroMetric = "PRICE",
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
                onItemClick = {},
                collapsedCategories = collapsed,
                onToggleCategory = { c -> collapsed = if (c in collapsed) collapsed - c else collapsed + c }
            )
        }
        val sats = "SATS 1USD 1343. Sats per 1 dollar"
        composeTestRule.onNodeWithContentDescription(sats).assertExists()
        composeTestRule.onNodeWithText("MARKET").performClick()
        composeTestRule.onNodeWithContentDescription(sats).assertDoesNotExist()
    }

    @Test
    fun alerts_tabShowsCurrentPrice() {
        setContent()
        composeTestRule.onNodeWithContentDescription("Alerts").performClick()
        composeTestRule.onNodeWithText("\$74,455").assertExists()
    }
}

