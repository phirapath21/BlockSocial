package com.example.btcwidget.nostr

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.security.MessageDigest

/** A picked video copied to the app's cache, ready to upload as-is. */
class PreparedVideo(val file: File, val mime: String, val sha256: String, val size: Long, val width: Int, val height: Int)

/**
 * Prepares a picked video for upload without re-encoding: it is copied to the cache while
 * its SHA-256 is computed, and its size read. Metadata in the file (which can include
 * where it was recorded) is not removed; the composer says so.
 */
object VideoPrep {
    const val MAX_BYTES = 100L * 1024 * 1024

    class TooLarge : Exception("Videos can be up to 100 MB")

    fun prepare(context: Context, uri: Uri): PreparedVideo {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)?.takeIf { it.startsWith("video/") } ?: "video/mp4"
        val dir = File(context.cacheDir, "uploads").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "video-" + System.nanoTime())
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        try {
            (resolver.openInputStream(uri) ?: error("Couldn't open the video")).use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        size += n
                        if (size > MAX_BYTES) throw TooLarge()
                        digest.update(buf, 0, n)
                        out.write(buf, 0, n)
                    }
                }
            }
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        var width = 0
        var height = 0
        runCatching {
            MediaMetadataRetriever().apply {
                setDataSource(file.path)
                val w = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val h = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val rotation = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                if (rotation == 90 || rotation == 270) { width = h; height = w } else { width = w; height = h }
                release()
            }
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        return PreparedVideo(file, mime, sha, size, width, height)
    }
}
