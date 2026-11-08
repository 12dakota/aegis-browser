package com.aegis.browser.engine

import android.content.Context
import android.util.Log
import android.view.View
import org.servo.servoview.ServoView

/**
 * Servo engine embedding via official ServoView (org.servo.servoview).
 * Replaces the previous Blink / System WebView path.
 *
 * Requires the Servo AAR (servo-aarch64-android.aar from servo-nightly-builds)
 * placed in app/libs/ or resolved via Gradle.
 */
class ServoEngine(context: Context) : BrowserEngine {

    override val type = BrowserEngine.Type.SERVO

    private val servoView = ServoView(context)

    private var progressListener: ((Int) -> Unit)? = null
    private var navigationListener: BrowserEngine.NavigationListener? = null

    private var currentUrl: String? = null
    private var currentTitle: String? = null

    init {
        Log.i(TAG, "Servo engine created")
        // Basic client wiring for navigation events if available
        try {
            servoView.setClient(object : org.servo.servoview.Servo.Client {
                // Minimal callbacks — expand as Servo API stabilizes
            })
        } catch (_: Exception) {
            // Older/newer AAR may differ; navigation still works via loadUrl
        }
    }

    override fun getView(): View = servoView

    override fun loadUrl(url: String) {
        currentUrl = url
        navigationListener?.onPageStarted(url)
        try {
            servoView.loadUrl(url)
            progressListener?.invoke(100)
            navigationListener?.onPageFinished(url)
        } catch (e: Exception) {
            Log.e(TAG, "Servo loadUrl failed", e)
            navigationListener?.onReceivedError(url, e.message)
        }
    }

    override fun canGoBack(): Boolean = try { servoView.canGoBack() } catch (_: Exception) { false }
    override fun goBack() { try { if (servoView.canGoBack()) servoView.goBack() } catch (_: Exception) {} }
    override fun canGoForward(): Boolean = try { servoView.canGoForward() } catch (_: Exception) { false }
    override fun goForward() { try { if (servoView.canGoForward()) servoView.goForward() } catch (_: Exception) {} }
    override fun reload() { try { servoView.reload() } catch (_: Exception) {} }
    override fun stopLoading() { try { servoView.stop() } catch (_: Exception) {} }
    override fun getCurrentUrl(): String? = currentUrl
    override fun getTitle(): String? = currentTitle

    override fun setProgressListener(listener: ((Int) -> Unit)?) { progressListener = listener }
    override fun setNavigationListener(listener: BrowserEngine.NavigationListener?) {
        navigationListener = listener
    }

    override fun destroy() {
        try {
            servoView.stop()
        } catch (_: Exception) {}
    }

    override fun onResume() {}
    override fun onPause() {}

    companion object {
        private const val TAG = "ProvonlongServo"
    }
}
