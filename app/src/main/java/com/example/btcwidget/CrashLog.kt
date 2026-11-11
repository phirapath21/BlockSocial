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
        // The crash log itself must never crash the app.
        runCatching {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            // Only wrap once (the Activity can be recreated).
            if (previous is Handler) return
            // In Application.attachBaseContext the application context is still null; the
            // context itself already resolves files.
            val ctx = context.applicationContext ?: context
            Thread.setDefaultUncaughtExceptionHandler(Handler(File(ctx.filesDir, FILE), previous, versionOf(ctx)))
        }
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
                    "Block $version · Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}) · " +
                        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\nThread: ${thread.name}\n\n" +
                        trace.take(20_000)
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The saved crash report, or null. */
    fun read(context: Context): String? = runCatching {
        File(context.filesDir, FILE).takeIf { it.exists() }?.readText()
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }

    private fun versionOf(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"
}
