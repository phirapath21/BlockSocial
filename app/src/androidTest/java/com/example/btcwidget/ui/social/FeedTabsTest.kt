package com.example.btcwidget.ui.social

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.btcwidget.nostr.core.HomeFeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The Home feeds as tabs: switching, and the buttons at the end. */
class FeedTabsTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tappingATabSelectsThatFeed() {
        var picked: HomeFeed? = null
        var managed = false
        composeTestRule.setContent {
            FeedTabBar(
                feeds = HomeFeed.DEFAULTS + HomeFeed.Hashtag("siamstr"),
                current = HomeFeed.DEFAULTS.first(),
                onSelect = { picked = it }, onManage = { managed = true }
            )
        }
        composeTestRule.onNodeWithText("Latest").assertExists()
        composeTestRule.onNodeWithText("With replies").performClick()
        composeTestRule.runOnIdle { assertEquals(HomeFeed.Following(true), picked) }
        composeTestRule.onNodeWithContentDescription("Manage feeds").performClick()
        composeTestRule.runOnIdle { assertTrue(managed) }
    }
}
