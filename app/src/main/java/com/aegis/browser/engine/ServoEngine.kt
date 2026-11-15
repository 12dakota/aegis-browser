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
 * Real Servo engine (official servo-aarch64-android.aar).
 * - Explicit native library load
 * - Deferred init until window attachment
 * - Crash-safe fallback
 * - Proper lifecycle hooks
 */
class ServoEngine(private val context: Context) : BrowserEngine {

    override val type = BrowserEngine.Type.SERVO

    companion object {
        private const val TAG = "ServoEngine"
        private var nativeLoaded = false

        fun ensureNativeLoaded() {
            if (nativeLoaded) return
            try {
                System.loadLibrary("servoshell")
                nativeLoaded = true
                Log.i(TAG, "libservoshell.so loaded")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to load libservoshell.so", t)
            }
        }
    }

    private val navigator = ServoNavigator()
    private val container = FrameLayout(context)
    private var composeView: ComposeView? = null

    private var progressListener: ((Int) -> Unit)? = null
    private var navigationListener: BrowserEngine.NavigationListener? = null
    private var currentUrl: String? = null
    private var currentTitle: String? = null
    private var initialized = false
    private var destroyed = false

    private val client = object : Servo.Client {
        override fun onAlert(message: String) { Log.i(TAG, "Alert: $message") }
        override fun onLoadStarted() {
            navigationListener?.onPageStarted(currentUrl)
            progressListener?.invoke(30)
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
        ensureNativeLoaded()
        container.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                if (!initialized && !destroyed) {
                    initialized = true
                    // Small post to let the window fully settle
                    v.post { tryInitServo() }
                }
            }
            override fun onViewDetachedFromWindow(v: View) {}
        })
        Log.i(TAG, "ServoEngine created (init deferred)")
    }

    private fun tryInitServo() {
        if (destroyed) return
        try {
            val cv = ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent { ServoHost(client = client, navigator = navigator) }
            }
            composeView = cv
            container.removeAllViews()
            container.addView(cv, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            Log.i(TAG, "Servo Compose view attached successfully")
        } catch (t: Throwable) {
            Log.e(TAG, "Servo init failed", t)
            showFallback(t.message ?: t.javaClass.simpleName)
        }
    }

    private fun showFallback(reason: String) {
        container.removeAllViews()
        val label = TextView(context).apply {
            text = "Servo unavailable\n$reason\n\nSwitch engine in Settings."
            textSize = 16f
            setPadding(48, 48, 48, 48)
        }
        container.addView(label)
    }

    override fun getView(): View = container

    override fun loadUrl(url: String) {
        currentUrl = url
        try { navigator.navigate(url) } catch (t: Throwable) {
            Log.w(TAG, "navigate failed", t)
        }
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
        destroyed = true
        try { composeView?.disposeComposition() } catch (_: Throwable) {}
        composeView = null
    }
    override fun onResume() { /* Servo handles via Compose lifecycle */ }
    override fun onPause() { /* Servo handles via Compose lifecycle */ }
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
