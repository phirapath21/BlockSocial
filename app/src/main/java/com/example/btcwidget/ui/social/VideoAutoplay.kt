package com.example.btcwidget.ui.social

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** When videos in the timeline start by themselves (muted), as on X. */
enum class VideoAutoplay(val label: String) {
    ALWAYS("Always"),
    WIFI_ONLY("Wi-Fi only"),
    MOBILE_ONLY("Mobile data only"),
    NEVER("Never");

    /** Whether autoplay is allowed on the connection the phone is using now. */
    fun allowedNow(context: Context): Boolean {
        if (this == NEVER) return false
        if (this == ALWAYS) return true
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        val mobile = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        return when (this) {
            WIFI_ONLY -> wifi
            MOBILE_ONLY -> mobile && !wifi
            else -> false
        }
    }

    companion object {
        /** Saving data and battery: Wi-Fi only unless chosen otherwise. */
        val DEFAULT = WIFI_ONLY
        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
