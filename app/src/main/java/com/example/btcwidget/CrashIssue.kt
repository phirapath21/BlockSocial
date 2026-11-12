package com.example.btcwidget

import java.net.URLEncoder

/**
 * A crash report as a new GitHub issue on Block's repository, opened in the browser with the
 * title and text filled in. Nothing is sent by the app: the person sees the issue, can edit or
 * remove anything, and submits it themselves with their GitHub account (issues are public).
 */
object CrashIssue {
    const val REPO = "https://github.com/phirapath21/btcwidget"
    /** Browsers and GitHub turn away very long links; the report is cut to fit. */
    private const val MAX_URL = 7_500

    /** The "new issue" link for [report], with [note] (what the person was doing) above it. */
    fun url(report: String, note: String = ""): String {
        val firstLine = report.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        // "java.lang.IllegalArgumentException: Cannot …" → "IllegalArgumentException: Cannot …"
        val error = report.lineSequence().drop(1).firstOrNull { it.isNotBlank() && !it.startsWith("Thread:") }?.let { line ->
            val cls = line.substringBefore(':').trim().substringAfterLast('.')
            val msg = line.substringAfter(':', "").trim()
            (if (msg.isEmpty()) cls else "$cls: $msg").take(90)
        }.orEmpty()
        val title = "Crash: " + error.ifBlank { "Block closed unexpectedly" }
        fun body(trace: String) = buildString {
            append("**What I was doing**\n")
            append(note.trim().ifBlank { "_(describe what you did just before the crash)_" })
            append("\n\n**Device**\n").append(firstLine).append("\n\n**Crash report**\n```\n").append(trace).append("\n```\n")
        }
        fun link(trace: String) = "$REPO/issues/new?labels=crash&title=${enc(title)}&body=${enc(body(trace))}"
        val trace = report.lines().drop(1).joinToString("\n").trim()
        var cut = trace
        var url = link(cut)
        // Keep the top of the trace (where the cause is), dropping lines from the end until it fits.
        while (url.length > MAX_URL && cut.length > 200) {
            cut = cut.take((cut.length * 0.85).toInt()).substringBeforeLast('\n') + "\n… (cut to fit; copy the full report from the app)"
            url = link(cut)
        }
        return url
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
