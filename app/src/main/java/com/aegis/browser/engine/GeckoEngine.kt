package com.aegis.browser.engine

import android.content.Context
import android.view.View
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/**
 * Firefox / Gecko engine via Mozilla GeckoView.
 * Both engines live in the same APK; only one is active per page load.
 */
class GeckoEngine(context: Context) : BrowserEngine {

    override val type = BrowserEngine.Type.GECKO

    private val runtime: GeckoRuntime = GeckoRuntime.create(context)
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

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<GeckoSession.NavigationDelegate.AllowOrDeny>? {
                val url = request.uri
                val shouldBlock = navigationListener?.shouldOverrideUrlLoading(url) == true
                return if (shouldBlock) {
                    GeckoResult.fromValue(GeckoSession.NavigationDelegate.AllowOrDeny.DENY)
                } else {
                    GeckoResult.fromValue(GeckoSession.NavigationDelegate.AllowOrDeny.ALLOW)
                }
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                canGoBackFlag = canGoBack
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                canGoForwardFlag = canGoForward
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
        runtime.shutdown()
    }
    override fun onResume() {}
    override fun onPause() {}
}
