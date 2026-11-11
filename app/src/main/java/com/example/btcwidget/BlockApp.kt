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

    /** Coil with animated GIF/WebP support (the network fetcher is added automatically). */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) add(AnimatedImageDecoder.Factory()) else add(GifDecoder.Factory())
            }
            .build()
}
