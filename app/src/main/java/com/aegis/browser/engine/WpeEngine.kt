package com.aegis.browser.engine

import android.content.Context
import android.util.Log
import android.view.View
import org.wpewebkit.wpeview.WebChromeClient
import org.wpewebkit.wpeview.WebView
import org.wpewebkit.wpeview.WebViewClient

/**
 * Bundled WPE WebKit (~167 MB AAR). Third engine beside Blink WebView and Gecko.
 */
class WpeEngine(context: Context) : BrowserEngine {

    override val type = BrowserEngine.Type.WEBKIT

    private val webView = WebView(context)

    private var progressListener: ((Int) -> Unit)? = null
    private var navigationListener: BrowserEngine.NavigationListener? = null

    init {
        Log.i(TAG, "WPE WebKit engine created")
        try {
            webView.settings.javaScriptEnabled = true
        } catch (e: Exception) {
            Log.w(TAG, "WPE settings: ${e.message}")
        }
        webView.webViewClient = object : WebViewClient() {}
        webView.webChromeClient = object : WebChromeClient() {}
    }

    override fun getView(): View = webView
    override fun loadUrl(url: String) {
        navigationListener?.onPageStarted(url)
        webView.loadUrl(url)
        navigationListener?.onPageFinished(url)
    }
    override fun canGoBack(): Boolean = try { webView.canGoBack() } catch (_: Exception) { false }
    override fun goBack() { try { if (webView.canGoBack()) webView.goBack() } catch (_: Exception) {} }
    override fun canGoForward(): Boolean = try { webView.canGoForward() } catch (_: Exception) { false }
    override fun goForward() { try { if (webView.canGoForward()) webView.goForward() } catch (_: Exception) {} }
    override fun reload() { try { webView.reload() } catch (_: Exception) {} }
    override fun stopLoading() { try { webView.stopLoading() } catch (_: Exception) {} }
    override fun getCurrentUrl(): String? = try { webView.url } catch (_: Exception) { null }
    override fun getTitle(): String? = try { webView.title } catch (_: Exception) { null }
    override fun setProgressListener(listener: ((Int) -> Unit)?) { progressListener = listener }
    override fun setNavigationListener(listener: BrowserEngine.NavigationListener?) {
        navigationListener = listener
    }
    override fun destroy() {
        try {
            webView.stopLoading()
            webView.destroy()
        } catch (_: Exception) {
        }
    }
    override fun onResume() { try { webView.onResume() } catch (_: Exception) {} }
    override fun onPause() { try { webView.onPause() } catch (_: Exception) {} }

    companion object {
        private const val TAG = "AegisWpe"
    }
}
