package com.example.btcwidget.ui.social

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import com.example.btcwidget.nostr.core.Blurhash

/**
 * The blurred preview for [hash] (a note's imeta "blurhash"), to show while the image loads:
 * a 32 × 32 picture, stretched smoothly over the image's box. Null when there's no valid hash.
 */
@Composable
fun rememberBlurhashPainter(hash: String?): Painter? = remember(hash) {
    val pixels = hash?.let { Blurhash.decode(it, SIZE, SIZE) } ?: return@remember null
    BitmapPainter(Bitmap.createBitmap(pixels, SIZE, SIZE, Bitmap.Config.ARGB_8888).asImageBitmap())
}

private const val SIZE = 32
