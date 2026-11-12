package com.example.btcwidget.ui.social

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The composer's toolbar buttons and the chips that say what a post will do. */
class ComposePartsTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun toolButtonsWorkUnlessDisabled() {
        var taps = 0
        composeTestRule.setContent {
            androidx.compose.foundation.layout.Row {
                ComposeToolButton(Glyphs.PhotoLine, "Add photo or video") { taps++ }
                ComposeToolButton(Glyphs.Poll, "Add poll", enabled = false) { taps += 100 }
            }
        }
        composeTestRule.onNodeWithContentDescription("Add photo or video").performClick()
        composeTestRule.onNodeWithContentDescription("Add poll").assertIsNotEnabled()
        composeTestRule.runOnIdle { assertEquals(1, taps) }
    }

    @Test
    fun statusChipUndoes() {
        var cleared = false
        composeTestRule.setContent {
            ComposeStatusChip(Glyphs.Warning, "Sensitive", onClear = { cleared = true })
        }
        composeTestRule.onNodeWithContentDescription("Undo").performClick()
        composeTestRule.runOnIdle { assertTrue(cleared) }
    }
}
