package com.example.btcwidget.ui.social

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Scans a QR code with Google's code scanner: the camera screen runs in Play services, so
 * Block needs no camera permission and never sees the camera. [onResult] gets the code's
 * text, or null if cancelled or unavailable.
 */
object QrScanner {
    fun scan(context: Context, onResult: (String?) -> Unit) {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        runCatching {
            GmsBarcodeScanning.getClient(context, options).startScan()
                .addOnSuccessListener { onResult(it.rawValue) }
                .addOnCanceledListener { onResult(null) }
                .addOnFailureListener { onResult(null) }
        }.onFailure { onResult(null) }
    }
}
