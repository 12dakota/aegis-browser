package com.aegis.browser.engine

import android.content.Context
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

object GeckoRuntimeHolder {
    @Volatile
    private var runtime: GeckoRuntime? = null

    fun get(context: Context): GeckoRuntime {
        runtime?.let { return it }
        synchronized(this) {
            runtime?.let { return it }
            val settings = GeckoRuntimeSettings.Builder()
                .remoteDebuggingEnabled(false)
                .consoleOutput(false)
                .build()
            val created = GeckoRuntime.create(context.applicationContext, settings)
            runtime = created
            return created
        }
    }
}
