package com.example.btcwidget.ui.social

import org.junit.Assert.assertEquals
import org.junit.Test

/** How photos and videos in posts are shaped on different screens. */
class MediaRatioTest {
    @Test fun phoneOnItsSideNeverCrashesAndStaysWithinMax() {
        // 900 × 380 dp: the height cap wants wider than 2:1; the max (2) wins instead of crashing.
        assertEquals(2f, mediaRatioFor(0.56f, 0.8f, 2f, 900, 380), 0.001f)
        assertEquals(2f, mediaRatioFor(16f / 9f, 0.8f, 2f, 900, 380), 0.001f)
    }

    @Test fun portraitKeepsOrdinaryShapesAndTrimsVeryTallOnes() {
        assertEquals(16f / 9f, mediaRatioFor(16f / 9f, 0.8f, 2f, 400, 860), 0.001f)
        // A tall 9:16 video is trimmed to the 4:5 limit on a tall phone.
        assertEquals(0.8f, mediaRatioFor(9f / 16f, 0.8f, 2f, 400, 860), 0.001f)
        // On a wide but short screen (a tablet on its side), to fit about half its height.
        assertEquals((560f - 70f) / (700f * 0.55f), mediaRatioFor(9f / 16f, 0.8f, 2f, 1200, 700), 0.001f)
    }
}
