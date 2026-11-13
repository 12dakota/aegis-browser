package com.aegis.browser.engine

import android.view.View

/**
 * Common interface for rendering engines.
 * Built-in: SERVO, GECKO, WEBKIT.
 * CUSTOM: user-imported local engine.
 */
interface BrowserEngine {

    enum class Type {
        SERVO,      // Official Servo (servoview AAR)
        GECKO,      // Mozilla GeckoView
        WEBKIT,     // WPE WebKit
        CUSTOM      // User-imported local engine
    }

    val type: Type

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
        fun shouldOverrideUrlLoading(url: String): Boolean
        fun onSslError(url: String, detail: String?, proceed: () -> Unit, cancel: () -> Unit) {}
        fun onPageContextMenu(linkUrl: String?, imageUrl: String?, text: String?) {}
        fun onSelectedText(text: String) {}
    }
}