package com.aegis.browser.engine

import android.content.Context
import com.aegis.browser.BuildConfig

/**
 * Chromium/Blink provider.
 *
 * Production path: [SystemWebViewChromiumBackend] — Google's prebuilt Chromium
 * (same "engine only, not source" model as GeckoView). Adds ~0 MB to the APK.
 *
 * Full Chromium source / Content Shell packaging is optional offline tooling only
 * and is not used under normal 15 GB account limits.
 */
interface ChromiumBackend {
    val displayName: String
    val supportsChromeExtensions: Boolean
    val isBundled: Boolean
}

object SystemWebViewChromiumBackend : ChromiumBackend {
    override val displayName = "Google Chromium / Blink (System WebView)"
    override val supportsChromeExtensions = false
    override val isBundled = false
}

object BundledContentShellBackend : ChromiumBackend {
    override val displayName = "Bundled Content Shell (optional offline build)"
    override val supportsChromeExtensions = false
    override val isBundled = true

    fun isAvailable(context: Context): Boolean {
        return try {
            context.assets.open("chromium/BUNDLED_MARKER").use { true }
        } catch (_: Exception) {
            false
        }
    }
}

object ChromiumBackendSelector {
    fun active(context: Context): ChromiumBackend {
        if (BuildConfig.USE_BUNDLED_CHROMIUM && BundledContentShellBackend.isAvailable(context)) {
            return BundledContentShellBackend
        }
        return SystemWebViewChromiumBackend
    }
}
