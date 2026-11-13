package com.aegis.browser.engine

import android.content.Context
import android.view.View
import androidx.compose.ui.platform.ComposeView
import org.servo.servoview.Servo
import org.servo.servoview.ServoNavigator

/**
 * Built-in Servo engine. Requires servo-aarch64-android.aar (or nightly) in libs/.
 * Experimental: arm64 preferred; history/keyboard support is incomplete.
 */
class ServoEngine(context: Context) : BrowserEngine {

    override val type = BrowserEngine.Type.SERVO

    private val composeView = ComposeView(context)
    private val navigator = ServoNavigator()
    private var progressListener: ((Int) -> Unit)? = null
    private var navigationListener: BrowserEngine.NavigationListener? = null
    private var currentUrl: String? = null

    private val client = object : Servo.Client {
        override fun onLoadStarted(url: String) {
            currentUrl = url
            navigationListener?.onPageStarted(url)
            progressListener?.invoke(10)
        }
        override fun onLoadFinished(url: String, success: Boolean) {
            navigationListener?.onPageFinished(url)
            progressListener?.invoke(100)
        }
        // Add more Client overrides as the AAR exposes them
    }

    init {
        composeView.setContent {
            Servo(
                experimentalModeEnabled = true,
                args = null,
                initialUrl = null,
                logStr = null,
                client = client,
                navigator = navigator
            )
        }
    }

    override fun getView(): View = composeView
    override fun loadUrl(url: String) {
        currentUrl = url
        navigator.loadUrl(url)
        navigationListener?.onPageStarted(url)
    }
    override fun canGoBack() = navigator.canGoBack()
    override fun goBack() = navigator.goBack()
    override fun canGoForward() = navigator.canGoForward()
    override fun goForward() = navigator.goForward()
    override fun reload() = navigator.reload()
    override fun stopLoading() = navigator.stop()
    override fun getCurrentUrl() = currentUrl
    override fun getTitle() = null
    override fun setProgressListener(listener: ((Int) -> Unit)?) { progressListener = listener }
    override fun setNavigationListener(listener: BrowserEngine.NavigationListener?) {
        navigationListener = listener
    }
    override fun destroy() {}
    override fun onResume() {}
    override fun onPause() {}
}