package com.aegis.browser.engine

/**
 * Abstraction over the Blink/Chromium implementation.
 *
 * Current production backend: [SystemWebViewChromiumBackend] (Google System WebView =
 * Chromium/Blink on device).
 *
 * Future: a [BundledContentShellBackend] could load a self-built Chromium Content Shell
 * binary for pinned Blink + a path toward Chrome-style extensions. That requires building
 * Chromium from source — not available as a Google Maven AAR.
 *
 * Automatic mode ([EngineSelector]) chooses CHROMIUM vs GECKO per URL; this interface
 * only concerns how the CHROMIUM choice is implemented.
 */
interface ChromiumBackend {
    val displayName: String
    val supportsChromeExtensions: Boolean
}

/**
 * Google Chromium as delivered on Android (WebView / Trichrome).
 * Rendering and site compatibility match Chrome closely; Chrome Web Store extensions
 * are still not available inside WebView (platform limitation).
 */
object SystemWebViewChromiumBackend : ChromiumBackend {
    override val displayName = "Google Chromium (System WebView / Blink)"
    override val supportsChromeExtensions = false
}
