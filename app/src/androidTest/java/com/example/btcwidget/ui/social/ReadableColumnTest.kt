package com.example.btcwidget.ui.social

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

/** Turning the phone (wide ↔ narrow) keeps what's open inside a page, such as a full-screen video. */
class ReadableColumnTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun stateInsideSurvivesSwitchingBetweenWideAndNarrow() {
        var wide by mutableStateOf(false)
        composeTestRule.setContent {
            ReadableColumn(enabled = wide) {
                // Stands in for a page's state, like the video it has open full screen.
                var taps by remember { mutableStateOf(0) }
                Text("taps $taps", Modifier.clickable { taps++ })
            }
        }
        composeTestRule.onNodeWithText("taps 0").performClick()
        composeTestRule.onNodeWithText("taps 1").assertExists()
        wide = true
        composeTestRule.onNodeWithText("taps 1").assertExists()
        wide = false
        composeTestRule.onNodeWithText("taps 1").assertExists()
    }
}
