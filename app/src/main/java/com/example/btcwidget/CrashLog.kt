package com.example.btcwidget

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Saves the last crash's stack trace on the device (never sent anywhere), so the next
 * launch can show it and the user can copy it into a bug report. Android shows no
 * details itself when an app closes.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val file = File(context.applicationContext.filesDir, FILE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        // Only wrap once (the Activity can be recreated).
        if (previous is Handler) return
        Thread.setDefaultUncaughtExceptionHandler(Handler(file, previous, versionOf(context)))
    }

    private class Handler(
        private val file: File,
        private val previous: Thread.UncaughtExceptionHandler?,
        private val version: String
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, error: Throwable) {
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                file.writeText(
                    "Blockface $version · Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}) · " +
                        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\nThread: ${thread.name}\n\n" +
                        trace.take(20_000)
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The saved crash report, or null. */
    fun read(context: Context): String? =
        File(context.filesDir, FILE).takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }

    private fun versionOf(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"
}
