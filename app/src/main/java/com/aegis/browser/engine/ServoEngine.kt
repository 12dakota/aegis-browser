package com.aegis.browser.engine

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import org.servo.servoview.Servo
import org.servo.servoview.ServoNavigator
import android.util.Log

/**
 * Real Servo engine using the official servo-aarch64-android.aar (Compose-based).
 */
class ServoEngine(context: Context) : BrowserEngine {

    override val type = BrowserEngine.Type.SERVO

    private val navigator = ServoNavigator()
    private val container = FrameLayout(context)
    private val composeView = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    private var progressListener: ((Int) -> Unit)? = null
    private var navigationListener: BrowserEngine.NavigationListener? = null
    private var currentUrl: String? = null
    private var currentTitle: String? = null

    private val client = object : Servo.Client {
        override fun onAlert(message: String) {
            Log.i("ServoEngine", "Alert: $message")
        }

        override fun onLoadStarted() {
            navigationListener?.onPageStarted(currentUrl)
            progressListener?.invoke(30)
        }

        override fun onLoadEnded() {
            progressListener?.invoke(100)
            navigationListener?.onPageFinished(currentUrl)
        }

        override fun onTitleChanged(title: String) {
            currentTitle = title
        }

        override fun onUrlChanged(url: String) {
            currentUrl = url
        }
    }

    init {
        composeView.setContent {
            ServoHost(client = client, navigator = navigator)
        }
        container.addView(
            composeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        Log.i("ServoEngine", "Real Servo engine (Compose) initialized")
    }

    override fun getView(): View = container

    override fun loadUrl(url: String) {
        currentUrl = url
        navigator.navigate(url)
        navigationListener?.onPageStarted(url)
    }

    override fun canGoBack() = navigator.canGoBack
    override fun goBack() { navigator.back() }
    override fun canGoForward() = navigator.canGoForward
    override fun goForward() { navigator.forward() }
    override fun reload() { navigator.reload() }
    override fun stopLoading() { navigator.stop() }
    override fun getCurrentUrl() = currentUrl
    override fun getTitle() = currentTitle ?: "Servo"
    override fun setProgressListener(listener: ((Int) -> Unit)?) { progressListener = listener }
    override fun setNavigationListener(listener: BrowserEngine.NavigationListener?) {
        navigationListener = listener
    }
    override fun destroy() {
        composeView.disposeComposition()
    }
    override fun onResume() {}
    override fun onPause() {}
}

@Composable
private fun ServoHost(client: Servo.Client, navigator: ServoNavigator) {
    Servo(
        experimentalModeEnabled = false,
        args = null,
        initialUrl = null,
        logStr = null,
        client = client,
        navigator = navigator,
        modifier = Modifier
    )
}
