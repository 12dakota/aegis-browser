package com.aegis.browser.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.aegis.browser.AegisApplication

/**
 * Chromium / Blink engine implemented via AndroidX WebView.
 * This is the default engine for most sites (Google properties, general web, etc.).
 */
class ChromiumEngine(context: Context) : BrowserEngine {

    override val type = BrowserEngine.Type.CHROMIUM

    @SuppressLint("SetJavaScriptEnabled")
    private val webView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
    }

    private var progressListener: ((Int) -> Unit)? = null
    private var navigationListener: BrowserEngine.NavigationListener? = null

    init {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return true
                return navigationListener?.shouldOverrideUrlLoading(url) == true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                navigationListener?.onPageStarted(url)
                // Keep PAC in sync
                AegisApplication.instance.pacProxyManager.evaluateAndSetProxy(url ?: "")
                AegisApplication.instance.pacProxyManager.configureWebViewProxy(webView)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                navigationListener?.onPageFinished(url)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    navigationListener?.onReceivedError(
                        request.url?.toString(),
                        error?.description?.toString()
                    )
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressListener?.invoke(newProgress)
            }
        }
    }

    override fun getView(): View = webView

    override fun loadUrl(url: String) {
        webView.loadUrl(url)
    }

    override fun canGoBack(): Boolean = webView.canGoBack()
    override fun goBack() { if (webView.canGoBack()) webView.goBack() }

    override fun canGoForward(): Boolean = webView.canGoForward()
    override fun goForward() { if (webView.canGoForward()) webView.goForward() }

    override fun reload() = webView.reload()
    override fun stopLoading() = webView.stopLoading()

    override fun getCurrentUrl(): String? = webView.url
    override fun getTitle(): String? = webView.title

    override fun setProgressListener(listener: ((Int) -> Unit)?) {
        progressListener = listener
    }

    override fun setNavigationListener(listener: BrowserEngine.NavigationListener?) {
        navigationListener = listener
    }

    override fun destroy() {
        webView.stopLoading()
        webView.destroy()
    }

    override fun onResume() {
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
    }
}
