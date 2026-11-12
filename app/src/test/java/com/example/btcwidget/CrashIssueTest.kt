package com.example.btcwidget

import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

/** The GitHub "new issue" link made from a crash report. */
class CrashIssueTest {
    private val report = "Block 1.0.0 · Android 17 (API 37) · samsung SM-A576B\nThread: main\n\n" +
        "java.lang.IllegalArgumentException: Cannot coerce value to an empty range\n" +
        (1..400).joinToString("\n") { "\tat com.example.Foo.bar$it(Foo.kt:$it)" }

    @Test fun fillsTitleBodyAndLabelAndFitsInALink() {
        val url = CrashIssue.url(report, "Scrolling Home on my side")
        assertTrue(url.startsWith(CrashIssue.REPO + "/issues/new?labels=crash&title="))
        assertTrue(url.length <= 7_500)
        val decoded = URLDecoder.decode(url, "UTF-8")
        assertTrue(decoded.contains("title=Crash: IllegalArgumentException: Cannot coerce value to an empty range"))
        assertTrue(decoded.contains("Scrolling Home on my side"))
        assertTrue(decoded.contains("samsung SM-A576B"))
        // The top of the trace (the cause) is kept; the long tail is cut.
        assertTrue(decoded.contains("Foo.bar1(Foo.kt:1)"))
        assertTrue(decoded.contains("cut to fit"))
    }

    @Test fun shortReportIsKeptWhole() {
        val url = CrashIssue.url("Block 1.0 · Android 15\nThread: main\n\njava.lang.NullPointerException\n\tat a.B(B.kt:1)")
        val decoded = URLDecoder.decode(url, "UTF-8")
        assertTrue(decoded.contains("at a.B(B.kt:1)"))
        assertTrue(!decoded.contains("cut to fit"))
    }
}
