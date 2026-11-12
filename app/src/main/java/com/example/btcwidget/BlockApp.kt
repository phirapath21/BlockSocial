package com.example.btcwidget

import android.app.Application
import android.content.Context
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder

/** Installs the crash log before anything else runs, and sets up image loading. */
class BlockApp : Application(), SingletonImageLoader.Factory {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        CrashLog.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(RelaySleeper)
        com.example.btcwidget.ui.social.Lang.load(this)
    }

    /**
     * Puts relay connections to sleep 30 s after the app leaves the screen (a quick switch to
     * another app and back keeps them), and wakes them when it returns.
     */
    private object RelaySleeper : ActivityLifecycleCallbacks {
        private val main = android.os.Handler(android.os.Looper.getMainLooper())
        private var started = 0
        private val sleep = Runnable { if (started == 0) com.example.btcwidget.nostr.RelayPool.enterBackground() }

        override fun onActivityStarted(activity: android.app.Activity) {
            started++
            main.removeCallbacks(sleep)
            if (started == 1) com.example.btcwidget.nostr.RelayPool.enterForeground()
        }

        override fun onActivityStopped(activity: android.app.Activity) {
            started = (started - 1).coerceAtLeast(0)
            if (started == 0) main.postDelayed(sleep, 30_000)
        }

        override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
        override fun onActivityResumed(activity: android.app.Activity) {}
        override fun onActivityPaused(activity: android.app.Activity) {}
        override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
        override fun onActivityDestroyed(activity: android.app.Activity) {}
    }

    /** Coil with animated GIF/WebP support (the network fetcher is added automatically). */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) add(AnimatedImageDecoder.Factory()) else add(GifDecoder.Factory())
            }
            .build()
}
