package com.example.btcwidget.ui.social

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Which quality videos start at, when they offer more than one (streams with several
 * resolutions); a single-file video always plays as it is.
 */
enum class VideoQuality(val label: String) {
    AUTO("Auto"),
    /** The lowest quality available, to save mobile data. */
    DATA_SAVER("Data saver");

    companion object {
        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: AUTO
    }
}

/** When videos in the timeline start by themselves (muted), as on X. */
enum class VideoAutoplay(val label: String) {
    ALWAYS("Always"),
    WIFI_ONLY("Wi-Fi only"),
    NEVER("Never");

    /** Whether autoplay is allowed on the connection the phone is using now. */
    fun allowedNow(context: Context): Boolean {
        if (this == NEVER) return false
        if (this == ALWAYS) return true
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        return this == WIFI_ONLY && wifi
    }

    companion object {
        /** Saving data and battery: Wi-Fi only unless chosen otherwise. */
        val DEFAULT = WIFI_ONLY
        /** Unknown names (e.g. the removed "mobile data only") fall back to the default. */
        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
