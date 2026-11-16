package com.aegis.browser.engine

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

/**
 * WebKit (WPE) is not bundled in the adult mobile APK to keep RAM under control.
 * Selecting WebKit shows a clear message and does not crash the browser.
 */
class WpeEngine(context: Context) : BrowserEngine {

    override val type = BrowserEngine.Type.WEBKIT

    private val container = FrameLayout(context).apply {
        addView(TextView(context).apply {
            text = "WebKit is not included in this mobile build.\n\nUse Gecko or Servo."
            textSize = 16f
            setPadding(48, 48, 48, 48)
        })
    }

    override fun getView(): View = container
    override fun loadUrl(url: String) {}
    override fun canGoBack() = false
    override fun goBack() {}
    override fun canGoForward() = false
    override fun goForward() {}
    override fun reload() {}
    override fun stopLoading() {}
    override fun getCurrentUrl(): String? = null
    override fun getTitle() = "WebKit"
    override fun setProgressListener(listener: ((Int) -> Unit)?) {}
    override fun setNavigationListener(listener: BrowserEngine.NavigationListener?) {}
    override fun destroy() {}
    override fun onResume() {}
    override fun onPause() {}
}
