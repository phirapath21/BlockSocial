package com.example.btcwidget.ui.social

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.btcwidget.nostr.RelayPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * Saves an image from the web into the phone's Pictures/Block folder. Android 10+
 * needs no permission (MediaStore); Android 9 and below needs storage write permission,
 * which the viewer asks for first.
 */
object ImageSaver {
    private const val FOLDER = "Block"

    /** True when saving needs the storage permission (Android 9 and below). */
    val needsPermission: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    suspend fun save(context: Context, url: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val (bytes, mime) = RelayPool.defaultClient.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code}")
                val type = r.header("Content-Type")?.substringBefore(';')?.trim()?.takeIf { it.startsWith("image/") } ?: guessMime(url)
                r.body.bytes() to type
            }
            require(bytes.isNotEmpty()) { "Empty image" }
            val ext = when (mime) { "image/png" -> "png"; "image/gif" -> "gif"; "image/webp" -> "webp"; else -> "jpg" }
            val name = "block_${System.currentTimeMillis()}.$ext"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, mime)
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + FOLDER)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Couldn't create the file")
                try {
                    resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Couldn't write the file")
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FOLDER).apply { mkdirs() }
                val file = File(dir, name)
                file.writeBytes(bytes)
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime), null)
            }
            Unit
        }
    }

    private fun guessMime(url: String): String = when {
        url.contains(".png", ignoreCase = true) -> "image/png"
        url.contains(".gif", ignoreCase = true) -> "image/gif"
        url.contains(".webp", ignoreCase = true) -> "image/webp"
        else -> "image/jpeg"
    }
}
