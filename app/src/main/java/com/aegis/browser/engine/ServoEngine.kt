package com.aegis.browser.engine

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import org.servo.servoview.Servo
import org.servo.servoview.ServoNavigator
import android.util.Log

/**
 * Real Servo engine using official servo-aarch64-android.aar.
 * Initialization is defensive — native/Compose failures fall back to a safe placeholder
 * instead of crashing the process.
 */
class ServoEngine(context: Context) : BrowserEngine {

    override val type = BrowserEngine.Type.SERVO

    private val navigator = ServoNavigator()
    private val container = FrameLayout(context)
    private var composeView: ComposeView? = null

    private var progressListener: ((Int) -> Unit)? = null
    private var navigationListener: BrowserEngine.NavigationListener? = null
    private var currentUrl: String? = null
    private var currentTitle: String? = null
    private var initialized = false

    private val client = object : Servo.Client {
        override fun onAlert(message: String) { Log.i("ServoEngine", "Alert: $message") }
        override fun onLoadStarted() {
            navigationListener?.onPageStarted(currentUrl)
            progressListener?.invoke(40)
        }
        override fun onLoadEnded() {
            progressListener?.invoke(100)
            navigationListener?.onPageFinished(currentUrl)
        }
        override fun onTitleChanged(title: String) { currentTitle = title }
        override fun onUrlChanged(url: String) { currentUrl = url }
        override fun onImeShow() {}
        override fun onImeHide() {}
    }

    init {
        // Defer heavy init until the view is attached to a window.
        container.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                if (!initialized) {
                    initialized = true
                    tryInitServo(context)
                }
            }
            override fun onViewDetachedFromWindow(v: View) {}
        })
        Log.i("ServoEngine", "ServoEngine created (init deferred)")
    }

    private fun tryInitServo(context: Context) {
        try {
            val cv = ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent { ServoHost(client = client, navigator = navigator) }
            }
            composeView = cv
            container.addView(cv, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            Log.i("ServoEngine", "Servo Compose view attached")
        } catch (t: Throwable) {
            Log.e("ServoEngine", "Servo init failed — showing fallback", t)
            showFallback(context, t.message ?: "unknown error")
        }
    }

    private fun showFallback(context: Context, reason: String) {
        container.removeAllViews()
        val label = TextView(context).apply {
            text = "Servo unavailable\n$reason\n\nSwitch engine in Settings."
            textSize = 16f
            setPadding(32, 32, 32, 32)
        }
        container.addView(label)
    }

    override fun getView(): View = container

    override fun loadUrl(url: String) {
        currentUrl = url
        try { navigator.navigate(url) } catch (_: Throwable) {}
        navigationListener?.onPageStarted(url)
    }

    override fun canGoBack() = try { navigator.canGoBack } catch (_: Throwable) { false }
    override fun goBack() { try { navigator.back() } catch (_: Throwable) {} }
    override fun canGoForward() = try { navigator.canGoForward } catch (_: Throwable) { false }
    override fun goForward() { try { navigator.forward() } catch (_: Throwable) {} }
    override fun reload() { try { navigator.reload() } catch (_: Throwable) {} }
    override fun stopLoading() { try { navigator.stop() } catch (_: Throwable) {} }
    override fun getCurrentUrl() = currentUrl
    override fun getTitle() = currentTitle ?: "Servo"
    override fun setProgressListener(listener: ((Int) -> Unit)?) { progressListener = listener }
    override fun setNavigationListener(listener: BrowserEngine.NavigationListener?) {
        navigationListener = listener
    }
    override fun destroy() {
        try { composeView?.disposeComposition() } catch (_: Throwable) {}
    }
    override fun onResume() {}
    override fun onPause() {}
}

@Composable
private fun ServoHost(client: Servo.Client, navigator: ServoNavigator) {
    Servo(
        experimentalModeEnabled = false,
        softKeyboardVisible = false,
        args = null,
        initialUrl = null,
        logStr = null,
        client = client,
        navigator = navigator,
        modifier = Modifier
    )
}
