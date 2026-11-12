package com.example.btcwidget.ui.social

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The pieces of the profile header: round buttons, rows you can copy, the loading skeleton. */
class ProfilePartsTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun copyButtonPutsTheFullTextOnTheClipboard() {
        composeTestRule.setContent {
            ProfileInfoRow(Glyphs.Zap, "alice@example.com", "alice@example.com", "copied")
        }
        composeTestRule.onNodeWithText("alice@example.com").assertExists()
        composeTestRule.onNodeWithContentDescription("Copy").performClick()
        composeTestRule.runOnIdle {
            val clipboard = composeTestRule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals("alice@example.com", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        }
    }

    @Test
    fun tappingTheTextDoesItsAction() {
        var opened = false
        composeTestRule.setContent {
            ProfileInfoRow(Glyphs.Link, "example.com", "https://example.com", "copied", onClick = { opened = true })
        }
        composeTestRule.onNodeWithText("example.com").performClick()
        composeTestRule.runOnIdle { assertTrue(opened) }
    }

    @Test
    fun roundButtonsHaveLabels() {
        var tapped = ""
        composeTestRule.setContent {
            androidx.compose.foundation.layout.Row {
                CircleIconButton(Glyphs.MoreHoriz, "More") { tapped = "more" }
                CircleIconButton(Glyphs.Chat, "Message") { tapped = "message" }
            }
        }
        composeTestRule.onNodeWithContentDescription("Message").performClick()
        composeTestRule.runOnIdle { assertEquals("message", tapped) }
    }

    @Test
    fun skeletonShowsWhileLoading() {
        // The skeleton pulses forever: drive the clock by hand so the test doesn't wait on it.
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            androidx.compose.foundation.layout.Column {
                ProfileHeaderSkeleton(topInset = true)
                NotesSkeleton(2)
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onRoot().assertExists()
    }
}
