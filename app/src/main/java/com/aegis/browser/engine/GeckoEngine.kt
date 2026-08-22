package com.aegis.browser.engine

import android.content.Context
import android.view.View
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class GeckoEngine(context: Context) : BrowserEngine {

    override val type = BrowserEngine.Type.GECKO

    private val runtime = GeckoRuntimeHolder.get(context)
    private val session = GeckoSession()
    private val geckoView = GeckoView(context)

    private var progressListener: ((Int) -> Unit)? = null
    private var navigationListener: BrowserEngine.NavigationListener? = null
    private var currentUrl: String? = null
    private var currentTitle: String? = null
    private var canGoBackFlag = false
    private var canGoForwardFlag = false

    init {
        session.open(runtime)
        geckoView.setSession(session)

        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                currentUrl = url
                navigationListener?.onPageStarted(url)
                progressListener?.invoke(10)
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                navigationListener?.onPageFinished(currentUrl)
                progressListener?.invoke(100)
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                progressListener?.invoke(progress)
            }
        }

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                currentUrl = url
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                canGoBackFlag = canGoBack
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                canGoForwardFlag = canGoForward
            }

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny>? {
                val uri = request.uri ?: return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                // Intercept XPI / AMO download installs before Gecko tries to download the file
                if (uri.contains(".xpi", ignoreCase = true) ||
                    (uri.contains("addons.mozilla.org", ignoreCase = true) &&
                        (uri.contains("/downloads/", ignoreCase = true) ||
                            uri.contains("type=extension", ignoreCase = true)))
                ) {
                    val block = navigationListener?.shouldOverrideUrlLoading(uri) == true
                    if (block) {
                        return GeckoResult.fromValue(AllowOrDeny.DENY)
                    }
                }
                val blocked = navigationListener?.shouldOverrideUrlLoading(uri) == true
                return GeckoResult.fromValue(if (blocked) AllowOrDeny.DENY else AllowOrDeny.ALLOW)
            }
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                currentTitle = title
            }
        }
    }

    override fun getView(): View = geckoView
    override fun loadUrl(url: String) {
        currentUrl = url
        session.loadUri(url)
    }
    override fun canGoBack(): Boolean = canGoBackFlag
    override fun goBack() { if (canGoBackFlag) session.goBack() }
    override fun canGoForward(): Boolean = canGoForwardFlag
    override fun goForward() { if (canGoForwardFlag) session.goForward() }
    override fun reload() = session.reload()
    override fun stopLoading() = session.stop()
    override fun getCurrentUrl(): String? = currentUrl
    override fun getTitle(): String? = currentTitle
    override fun setProgressListener(listener: ((Int) -> Unit)?) { progressListener = listener }
    override fun setNavigationListener(listener: BrowserEngine.NavigationListener?) {
        navigationListener = listener
    }
    override fun destroy() {
        session.close()
    }
    override fun onResume() {}
    override fun onPause() {}
}
