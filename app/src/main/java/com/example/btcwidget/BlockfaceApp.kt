package com.example.btcwidget

import android.app.Application
import android.content.Context

/** Installs the crash log before anything else runs (content providers included). */
class BlockfaceApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        CrashLog.install(this)
    }
}
