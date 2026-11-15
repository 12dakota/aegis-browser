package com.aegis.browser.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
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
 * Real Servo engine for regular phone APKs (family / adult).
 * - Explicit libservoshell load
 * - Init only after the view has a window and a short settle delay
 * - All native calls isolated so a Servo failure cannot crash the process
 * - Boot / OS detection logic is left untouched elsewhere
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

    private val mainHandler = Handler(Looper.getMainLooper())
    private val navigator = ServoNavigator()
    private val container = FrameLayout(context)
    private var composeView: ComposeView? = null

    private var progressListener: ((Int) -> Unit)? = null
    private var navigationListener: BrowserEngine.NavigationListener? = null
    private var currentUrl: String? = null
    private var currentTitle: String? = null
    private var initialized = false
    private var destroyed = false
    private var pendingUrl: String? = null

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
                    // Give the window / surface a moment to be fully ready
                    mainHandler.postDelayed({ tryInitServo() }, 150)
                }
            }
            override fun onViewDetachedFromWindow(v: View) {}
        })
        Log.i(TAG, "ServoEngine created (phone path, init deferred)")
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
            Log.i(TAG, "Servo view rendered")
            // Flush any URL that arrived before the view was ready
            pendingUrl?.let { url ->
                pendingUrl = null
                mainHandler.post { safeNavigate(url) }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Servo render failed", t)
            showFallback(t.message ?: t.javaClass.simpleName)
        }
    }

    private fun showFallback(reason: String) {
        container.removeAllViews()
        val label = TextView(context).apply {
            text = "Servo could not start\n$reason\n\nSwitch to Gecko or WebKit in Settings."
            textSize = 16f
            setPadding(48, 48, 48, 48)
        }
        container.addView(label)
    }

    private fun safeNavigate(url: String) {
        try { navigator.navigate(url) } catch (t: Throwable) {
            Log.w(TAG, "navigate failed for $url", t)
        }
    }

    override fun getView(): View = container

    override fun loadUrl(url: String) {
        currentUrl = url
        if (composeView == null) {
            pendingUrl = url
        } else {
            safeNavigate(url)
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
        mainHandler.removeCallbacksAndMessages(null)
        try { composeView?.disposeComposition() } catch (_: Throwable) {}
        composeView = null
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
