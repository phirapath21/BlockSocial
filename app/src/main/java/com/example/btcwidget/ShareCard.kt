package com.example.btcwidget

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Shares a dashboard metric through the system share sheet: the panel as a PNG (in the
 * current theme) plus [ShareText]. Nostr clients such as Amethyst or Primal open a new
 * note with both, which others can then quote or repost. The app itself sends nothing
 * over the network and holds no keys.
 */
object ShareCard {
    private const val WIDTH = 1200
    private const val HEIGHT = 600

    fun share(context: Context, key: String, data: PriceData, currency: String, palette: Palette) {
        val text = ShareText.forKey(key, data, currency) ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, text)
            val image = runCatching { writeImage(context, key, data, currency, palette) }
                .onFailure { Log.w("ShareCard", "Sharing text only", it) }
                .getOrNull()
            if (image != null) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", image)
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(null, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
        }
        val chooser = Intent.createChooser(intent, "Share")
        if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /** Paints the metric's panel, as on the dashboard, into cache/shared/block.png. */
    private fun writeImage(context: Context, key: String, data: PriceData, currency: String, palette: Palette): File {
        val scene = BlockclockScene(
            display = BlockclockMetrics.forKey(key, data, currency),
            stale = !data.lastFetchSuccessful,
            progress = if (key == "HALVING") BlockclockMetrics.halvingProgress(data.blockHeight).toFloat() else null,
            palette = palette
        )
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        BlockclockPainter.paint(AndroidDrawSurface.create(Canvas(bitmap), context), WIDTH.toFloat(), HEIGHT.toFloat(), scene)
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        // One file, overwritten each time, so shares never pile up in the cache.
        val file = File(dir, "block.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }
}
