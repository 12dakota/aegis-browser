package com.aegis.browser.engine

import android.content.Context
import com.aegis.browser.BuildConfig
import java.io.File

/**
 * Abstraction over how the CHROMIUM/Blink engine is provided.
 *
 * Roadmap:
 * 1. SystemWebViewChromiumBackend — current production (Google Chromium on device)
 * 2. BundledContentShellBackend — private Content Shell libs in APK (scripts/chromium/)
 * 3. Full Chrome embedder — required for true Chrome Web Store / MV3 extension host
 *
 * Gecko WebExtensions already work via GeckoView. Chromium *extensions* need the
 * chrome.extension APIs and process model from the Chrome layer, not Blink alone.
 */
interface ChromiumBackend {
    val displayName: String
    /** True only when a full Chrome-level embedder is present (not Content Shell alone). */
    val supportsChromeExtensions: Boolean
    val isBundled: Boolean
}

object SystemWebViewChromiumBackend : ChromiumBackend {
    override val displayName = "Google Chromium (System WebView / Blink)"
    override val supportsChromeExtensions = false
    override val isBundled = false
}

/**
 * Detects whether scripts/chromium/03-package-into-aegis.sh has placed natives/assets.
 * Full Content Shell JNI bridge is Phase 2 after the first successful builder run.
 */
object BundledContentShellBackend : ChromiumBackend {
    override val displayName = "Bundled Chromium Content Shell (Blink)"
    override val supportsChromeExtensions = false // needs Chrome embedder layer
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
