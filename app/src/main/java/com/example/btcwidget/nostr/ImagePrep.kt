package com.example.btcwidget.nostr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream

/** An image ready to upload: re-encoded bytes without metadata. */
class PreparedImage(val bytes: ByteArray, val mime: String, val width: Int, val height: Int)

/**
 * Turns a picked photo into an upload: decoded, rotated upright, scaled to at most
 * [MAX_SIDE] px and re-encoded as JPEG. Re-encoding drops EXIF, so GPS location, camera
 * and time stamps never leave the phone.
 */
object ImagePrep {
    private const val MAX_SIDE = 2048
    private const val QUALITY = 85

    fun prepare(context: Context, uri: Uri): PreparedImage {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "That file isn't an image" }

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Couldn't read the image")

        val rotation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees() } ?: 0
        }.getOrDefault(0)

        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        val upright = if (matrix.isIdentity) decoded
            else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        val out = ByteArrayOutputStream()
        upright.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        val result = PreparedImage(out.toByteArray(), "image/jpeg", upright.width, upright.height)
        if (upright !== decoded) upright.recycle()
        decoded.recycle()
        return result
    }

    private fun ExifInterface.rotationDegrees(): Int = when (getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
}
