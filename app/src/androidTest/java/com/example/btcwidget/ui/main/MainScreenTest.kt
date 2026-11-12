package com.example.btcwidget.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import com.example.btcwidget.BlockclockMetrics
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
                modeStates = List(BlockclockMetrics.MODE_COUNT) { true },
                currencySetting = currency,
                onCurrencySelected = {},
                refreshInterval = 30,
                onRefreshIntervalSelected = {},
                onHeroMetricSelected = { hero = it },
                onTabSelected = { tab = it },
                onModeToggle = { _, _ -> },
                onRefresh = {},
                onItemClick = {},
                settingsContent = { com.example.btcwidget.ui.social.SettingsHome(accountName = null, onOpen = { openedSettings = it }) }
            )
        }
    }

    private var openedSettings: com.example.btcwidget.ui.social.SettingsPage? = null

    /** The hero chip; the dashboard's title is also "BLOCK", but isn't clickable. */
    private val blockChip = hasText("BLOCK") and hasClickAction()

    @Test
    fun dashboard_showsHeroPanelForSelectedMetric() {
        setContent()
        composeTestRule.onNode(blockChip).assertExists()
        composeTestRule.onNodeWithContentDescription("BTC USD \$74455. Market price of bitcoin").assertExists()

        composeTestRule.onNode(blockChip).performClick()
        composeTestRule.onNodeWithContentDescription("BLOCK HGHT 925000. Bitcoin block height").assertExists()
    }

    @Test
    fun bottomBar_hasFiveTabsAndTheMenuIsTopLeft() {
        setContent()
        listOf("Home", "Search", "Bitcoin", "Notifications", "Messages", "Open menu").forEach {
            composeTestRule.onNodeWithContentDescription(it).assertExists()
        }
        // Profile and Settings are in the side panel.
        composeTestRule.onNodeWithContentDescription("Settings").assertDoesNotExist()
    }

    @Test
    fun landscape_tabsMoveToARailOnTheLeft() {
        composeTestRule.setContent {
            // A phone on its side: wider than tall.
            val wide = android.content.res.Configuration(androidx.compose.ui.platform.LocalConfiguration.current).apply {
                screenWidthDp = 840; screenHeightDp = 380; orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE
            }
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalConfiguration provides wide) {
                MainScreenContent(
                    state = MainScreenUiState.Success(sample), selectedTab = MainTab.HOME, heroMetric = "PRICE",
                    modeStates = List(BlockclockMetrics.MODE_COUNT) { true }, currencySetting = "USD", onCurrencySelected = {},
                    refreshInterval = 30, onRefreshIntervalSelected = {}, onHeroMetricSelected = {}, onTabSelected = {},
                    onModeToggle = { _, _ -> }, onRefresh = {}, onItemClick = {}
                )
            }
        }
        composeTestRule.onNodeWithTag("side_rail").assertExists()
        listOf("Home", "Search", "Bitcoin", "Notifications", "Messages", "Open menu").forEach {
            composeTestRule.onNodeWithContentDescription(it).assertExists()
        }
    }

    @Test
    fun portrait_hasNoRail() {
        setContent()
        composeTestRule.onNodeWithTag("side_rail").assertDoesNotExist()
    }

    @Test
    fun notifications_openFromTheBottomBar() {
        setContent()
        composeTestRule.onNodeWithContentDescription("Notifications").performClick()
        // The page's title in the top bar.
        composeTestRule.onNode(hasText("Notifications") and androidx.compose.ui.test.hasClickAction().not()).assertExists()
    }

    private val sats = "SATS 1USD 1343. Sats per 1 dollar"

    /**
     * Scrolls the Widgets page (its one lazy grid) until a node matching [target] is composed:
     * panels below the how-to and stack cards aren't until scrolled to.
     */
    private fun scrollWidgetsTo(target: androidx.compose.ui.test.SemanticsMatcher) {
        composeTestRule.onNode(hasScrollToIndexAction()).performScrollToNode(target)
    }

    /** Opens the side bar with your avatar at the top left and picks [page]. */
    private fun openFromSideBar(page: String) {
        composeTestRule.onNodeWithContentDescription("Open menu").performClick()
        composeTestRule.onNodeWithText(page).performClick()
    }

    @Test
    fun settings_openFromSideBar() {
        setContent()
        openFromSideBar("Settings")
        // The categories, each opening its own page.
        composeTestRule.onNodeWithText("Your account").assertExists()
        // The last categories are below the fold on a small screen.
        composeTestRule.onNodeWithText("Bitcoin and widgets").performScrollTo().performClick()
        org.junit.Assert.assertEquals(com.example.btcwidget.ui.social.SettingsPage.BITCOIN, openedSettings)
    }

    @Test
    fun settings_searchFindsASettingAndItsPage() {
        setContent()
        openFromSideBar("Settings")
        composeTestRule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("currency")
        composeTestRule.onNodeWithText("Currency").performClick()
        org.junit.Assert.assertEquals(com.example.btcwidget.ui.social.SettingsPage.BITCOIN, openedSettings)
    }

    @Test
    fun widgets_offMetricsStayVisibleWithTheirSwitch() {
        composeTestRule.setContent {
            val states = remember { androidx.compose.runtime.mutableStateListOf(*Array(14) { it != 1 }) } // SATS off
            MainScreenContent(
                state = MainScreenUiState.Success(sample),
                selectedTab = MainTab.WIDGETS,
                heroMetric = "PRICE",
                modeStates = states,
                currencySetting = "USD",
                onCurrencySelected = {},
                refreshInterval = 30,
                onRefreshIntervalSelected = {},
                onHeroMetricSelected = {},
                onTabSelected = {},
                onModeToggle = { index, on -> states[index] = on },
                onRefresh = {},
                onItemClick = {}
            )
        }
        // The switched-off SATS panel is still shown, with its switch off.
        scrollWidgetsTo(hasContentDescription(sats))
        composeTestRule.onNodeWithContentDescription(sats).assertExists()
        scrollWidgetsTo(hasText("OFF WIDGET"))
        composeTestRule.onNodeWithText("OFF WIDGET").assertExists().performClick()
        composeTestRule.onNodeWithText("OFF WIDGET").assertDoesNotExist()
    }

    @Test
    fun widgets_tabCountsMetricsOnTheWidget() {
        setContent()
        openFromSideBar("Widgets")
        // STACK needs holdings, so 13 of the 14 modes are available.
        composeTestRule.onNodeWithText("13 OF 13", substring = true).assertExists()
    }

    @Test
    fun widgets_sectionsCollapse() {
        composeTestRule.setContent {
            var collapsed by remember { mutableStateOf(emptySet<com.example.btcwidget.MetricCategory>()) }
            MainScreenContent(
                state = MainScreenUiState.Success(sample),
                selectedTab = MainTab.WIDGETS,
                heroMetric = "PRICE",
                modeStates = List(BlockclockMetrics.MODE_COUNT) { true },
                currencySetting = "USD",
                onCurrencySelected = {},
                refreshInterval = 30,
                onRefreshIntervalSelected = {},
                onHeroMetricSelected = {},
                onTabSelected = {},
                onModeToggle = { _, _ -> },
                onRefresh = {},
                onItemClick = {},
                collapsedCategories = collapsed,
                onToggleCategory = { c -> collapsed = if (c in collapsed) collapsed - c else collapsed + c }
            )
        }
        scrollWidgetsTo(hasContentDescription(sats))
        composeTestRule.onNodeWithContentDescription(sats).assertExists()
        scrollWidgetsTo(hasText("MARKET"))
        composeTestRule.onNodeWithText("MARKET").performClick()
        composeTestRule.onNodeWithContentDescription(sats).assertDoesNotExist()
    }

    @Test
    fun alerts_tabShowsCurrentPrice() {
        setContent()
        openFromSideBar("Price alerts")
        composeTestRule.onNodeWithText("\$74,455").assertExists()
    }

    @Test
    fun dashboard_hasNoMetricPanels() {
        setContent()
        // Only the hero shows on the dashboard; metric panels live in the Widgets tab.
        composeTestRule.onNodeWithContentDescription(sats).assertDoesNotExist()
        openFromSideBar("Widgets")
        scrollWidgetsTo(hasContentDescription(sats))
        composeTestRule.onNodeWithContentDescription(sats).assertExists()
    }
}
