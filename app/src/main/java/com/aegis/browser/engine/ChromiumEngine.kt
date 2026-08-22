package com.aegis.browser.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.DownloadListener
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.aegis.browser.AegisApplication
import com.aegis.browser.downloads.DownloadCoordinator

/**
 * Chromium / Blink engine.
 *
 * On Android, Google's Chromium is delivered as **Android System WebView**
 * (or Trichrome on modern devices). There is no official GeckoView-style
 * Maven artifact that embeds a full standalone Chromium binary into the APK.
 *
 * This class uses AndroidX WebView + WebKit APIs against that Google Chromium
 * engine so the "C" engine is real Chromium (version reported by the system),
 * kept current via Play Store WebView updates — same family as Chrome.
 *
 * Full offline-bundled Content Shell would require building Chromium from source
 * (multi-GB, hours) — see docs/CHROMIUM_ENGINE.md.
 */
class ChromiumEngine(context: Context) : BrowserEngine {

    override val type = BrowserEngine.Type.CHROMIUM

    @SuppressLint("SetJavaScriptEnabled")
    private val webView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mediaPlaybackRequiresUserGesture = true
        settings.userAgentString = settings.userAgentString // keep system Chromium UA
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = true
        }
        // AndroidX WebKit extras when the installed Chromium WebView supports them
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                @Suppress("DEPRECATION")
                WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_OFF)
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.OFF_SCREEN_PRERASTER)) {
                WebSettingsCompat.setOffscreenPreRaster(settings, true)
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
                WebSettingsCompat.setSafeBrowsingEnabled(settings, true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "WebKit feature setup: ${e.message}")
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
    }

    private var progressListener: ((Int) -> Unit)? = null
    private var navigationListener: BrowserEngine.NavigationListener? = null

    init {
        val backend = ChromiumBackendSelector.active(context)
        Log.i(TAG, "Chromium backend: ${backend.displayName} bundled=${backend.isBundled}")
        Log.i(TAG, "Chromium WebView provider: " + chromiumVersionSummary(context))

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return true
                return navigationListener?.shouldOverrideUrlLoading(url) == true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                navigationListener?.onPageStarted(url)
                try {
                    AegisApplication.instance.pacProxyManager.evaluateAndSetProxy(url ?: "")
                    AegisApplication.instance.pacProxyManager.configureWebViewProxy(webView)
                } catch (_: Exception) {
                }
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
                    val code = error?.errorCode ?: 0
                    val desc = when (code) {
                        android.webkit.WebViewClient.ERROR_HOST_LOOKUP,
                        android.webkit.WebViewClient.ERROR_CONNECT,
                        android.webkit.WebViewClient.ERROR_TIMEOUT,
                        android.webkit.WebViewClient.ERROR_IO,
                        android.webkit.WebViewClient.ERROR_PROXY_AUTHENTICATION,
                        -6, -2 -> "NO_INTERNET:" + (error?.description?.toString() ?: "offline")
                        else -> error?.description?.toString()
                    }
                    navigationListener?.onReceivedError(request.url?.toString(), desc)
                }
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: android.webkit.SslErrorHandler?,
                error: android.net.http.SslError?
            ) {
                val url = error?.url ?: view?.url.orEmpty()
                val detail = error?.toString()
                if (handler == null) return
                navigationListener?.onSslError(
                    url,
                    detail,
                    { handler.proceed() },
                    { handler.cancel() }
                ) ?: handler.cancel()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressListener?.invoke(newProgress)
            }
        }
        webView.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            DownloadCoordinator.start(
                context.applicationContext,
                url,
                userAgent,
                contentDisposition,
                mimetype
            )
        })

        webView.isLongClickable = true
        webView.setOnLongClickListener {
            val ht = webView.hitTestResult
            val extra = ht.extra
            when (ht.type) {
                android.webkit.WebView.HitTestResult.SRC_ANCHOR_TYPE,
                android.webkit.WebView.HitTestResult.PHONE_TYPE,
                android.webkit.WebView.HitTestResult.EMAIL_TYPE,
                android.webkit.WebView.HitTestResult.GEO_TYPE,
                android.webkit.WebView.HitTestResult.ANCHOR_TYPE -> {
                    navigationListener?.onPageContextMenu(extra, null, extra)
                    true
                }
                android.webkit.WebView.HitTestResult.IMAGE_TYPE -> {
                    navigationListener?.onPageContextMenu(null, extra, extra)
                    true
                }
                android.webkit.WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    navigationListener?.onPageContextMenu(extra, extra, extra)
                    true
                }
                else -> {
                    webView.evaluateJavascript(
                        "(function(){try{return window.getSelection().toString();}catch(e){return '';}})()"
                    ) { raw ->
                        val text = raw?.trim()?.trim('"')?.replace("\\n", "\n")
                        if (!text.isNullOrBlank() && text != "null") {
                            navigationListener?.onPageContextMenu(null, null, text)
                        } else if (!extra.isNullOrBlank()) {
                            navigationListener?.onPageContextMenu(extra, extra, extra)
                        }
                    }
                    true
                }
            }
        }
    }

    override fun getView(): View = webView
    override fun loadUrl(url: String) { webView.loadUrl(url) }
    override fun canGoBack(): Boolean = webView.canGoBack()
    override fun goBack() { if (webView.canGoBack()) webView.goBack() }
    override fun canGoForward(): Boolean = webView.canGoForward()
    override fun goForward() { if (webView.canGoForward()) webView.goForward() }
    override fun reload() = webView.reload()
    override fun stopLoading() = webView.stopLoading()
    override fun getCurrentUrl(): String? = webView.url
    override fun getTitle(): String? = webView.title
    override fun setProgressListener(listener: ((Int) -> Unit)?) { progressListener = listener }
    override fun setNavigationListener(listener: BrowserEngine.NavigationListener?) {
        navigationListener = listener
    }
    override fun destroy() {
        webView.stopLoading()
        webView.destroy()
    }
    override fun onResume() { webView.onResume() }
    override fun onPause() { webView.onPause() }

    companion object {
        private const val TAG = "AegisChromium"

        /** Human-readable Google Chromium / WebView package version on this device. */
        fun chromiumVersionSummary(context: Context): String {
            return try {
                val pkg = WebViewCompat.getCurrentWebViewPackage(context)
                if (pkg != null) {
                    "${pkg.packageName} ${pkg.versionName}"
                } else {
                    "System WebView (package unknown)"
                }
            } catch (e: Exception) {
                "WebView ${WebView.getCurrentWebViewPackage()?.versionName ?: "?"}"
            }
        }
    }
}
