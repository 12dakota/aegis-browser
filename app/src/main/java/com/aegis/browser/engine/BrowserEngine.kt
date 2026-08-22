package com.aegis.browser.engine

import android.view.View
import android.view.ViewGroup

/**
 * Common interface for the two rendering engines that live side-by-side in Aegis Browser.
 *
 * - ChromiumEngine  → AndroidX WebView (Blink)
 * - GeckoEngine     → Mozilla GeckoView (Gecko)
 * - WpeEngine       → WPE WebKit (Igalia), bundled AAR
 *
 * The browser picks one engine per navigation (or per tab) using EngineSelector.
 * They are never merged; they simply coexist in the same APK so the app can
 * serve sites that work better on one engine or the other.
 */
interface BrowserEngine {

    enum class Type {
        CHROMIUM,   // Blink / WebView
        GECKO,      // Firefox engine
        WEBKIT      // WPE WebKit (bundled)
    }

    val type: Type

    /** The Android View that must be added to the layout */
    fun getView(): View

    fun loadUrl(url: String)

    fun canGoBack(): Boolean
    fun goBack()

    fun canGoForward(): Boolean
    fun goForward()

    fun reload()

    fun stopLoading()

    fun getCurrentUrl(): String?

    fun getTitle(): String?

    fun setProgressListener(listener: ((Int) -> Unit)?)

    fun setNavigationListener(listener: NavigationListener?)

    fun destroy()

    fun onResume()
    fun onPause()

    interface NavigationListener {
        fun onPageStarted(url: String?)
        fun onPageFinished(url: String?)
        fun onReceivedError(url: String?, errorDescription: String?)
        /** Return true to cancel the navigation (used for parental URL blocking) */
        fun shouldOverrideUrlLoading(url: String): Boolean
        fun onSslError(url: String, detail: String?, proceed: () -> Unit, cancel: () -> Unit) {}
        fun onPageContextMenu(linkUrl: String?, imageUrl: String?, text: String?) {}
        fun onSelectedText(text: String) {}
    }
}
