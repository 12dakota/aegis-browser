package com.aegis.browser.engine

import android.content.Context
import android.util.Log
import android.view.View

/**
 * Gecko engine wrapper.
 * GeckoView dependency is disabled in low-RAM sandbox builds; falls back to Chromium.
 * Re-enable org.mozilla.geckoview in app/build.gradle.kts on a full machine.
 */
class GeckoEngine(context: Context) : BrowserEngine {

    override val type = BrowserEngine.Type.GECKO

    private val fallback = ChromiumEngine(context)

    init {
        Log.w("GeckoEngine", "GeckoView not linked in this build — using Chromium fallback")
    }

    override fun getView(): View = fallback.getView()
    override fun loadUrl(url: String) = fallback.loadUrl(url)
    override fun canGoBack(): Boolean = fallback.canGoBack()
    override fun goBack() = fallback.goBack()
    override fun canGoForward(): Boolean = fallback.canGoForward()
    override fun goForward() = fallback.goForward()
    override fun reload() = fallback.reload()
    override fun stopLoading() = fallback.stopLoading()
    override fun getCurrentUrl(): String? = fallback.getCurrentUrl()
    override fun getTitle(): String? = fallback.getTitle()
    override fun setProgressListener(listener: ((Int) -> Unit)?) = fallback.setProgressListener(listener)
    override fun setNavigationListener(listener: BrowserEngine.NavigationListener?) =
        fallback.setNavigationListener(listener)
    override fun destroy() = fallback.destroy()
    override fun onResume() = fallback.onResume()
    override fun onPause() = fallback.onPause()
}
