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
import android.util.Log

/**
 * Servo engine for the adult mobile APK.
 *
 * - Native libservoshell.so is loaded only when a Servo tab is actually created.
 * - All Java-side failures are isolated; the process does not die.
 * - If Servo cannot start, the tab shows a clear fallback instead of crashing.
 * - No other engine AARs are pulled in by this class.
 */
class ServoEngine(private val context: Context) : BrowserEngine {

    override val type = BrowserEngine.Type.SERVO

    companion object {
        private const val TAG = "ServoEngine"
        @Volatile private var nativeLoaded = false

        fun ensureNativeLoaded(): Boolean {
            if (nativeLoaded) return true
            return try {
                System.loadLibrary("servoshell")
                nativeLoaded = true
                Log.i(TAG, "libservoshell.so loaded (on demand)")
                true
            } catch (t: Throwable) {
                Log.e(TAG, "libservoshell.so load failed", t)
                false
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val container = FrameLayout(context)
    private var composeView: ComposeView? = null

    private var progressListener: ((Int) -> Unit)? = null
    private var navigationListener: BrowserEngine.NavigationListener? = null
    private var currentUrl: String? = null
    private var currentTitle: String? = null
    private var initialized = false
    private var destroyed = false
    private var failed = false
    private var pendingUrl: String? = null

    // Held only after successful init so a missing class can't crash construction
    private var navigator: Any? = null

    init {
        container.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                if (!initialized && !destroyed) {
                    initialized = true
                    mainHandler.postDelayed({ tryInitServo() }, 200)
                }
            }
            override fun onViewDetachedFromWindow(v: View) {}
        })
        Log.i(TAG, "ServoEngine created (lazy)")
    }

    private fun tryInitServo() {
        if (destroyed || failed) return
        if (!ensureNativeLoaded()) {
            fail("Native library could not be loaded")
            return
        }
        try {
            // Reflect so a missing/broken Servo class never crashes the activity
            val navigatorClass = Class.forName("org.servo.servoview.ServoNavigator")
            val nav = navigatorClass.getDeclaredConstructor().newInstance()
            navigator = nav

            val clientClass = Class.forName("org.servo.servoview.Servo\$Client")
            val client = java.lang.reflect.Proxy.newProxyInstance(
                clientClass.classLoader,
                arrayOf(clientClass)
            ) { _, method, args ->
                when (method.name) {
                    "onLoadStarted" -> {
                        navigationListener?.onPageStarted(currentUrl)
                        progressListener?.invoke(30)
                    }
                    "onLoadEnded" -> {
                        progressListener?.invoke(100)
                        navigationListener?.onPageFinished(currentUrl)
                    }
                    "onTitleChanged" -> currentTitle = args?.getOrNull(0) as? String
                    "onUrlChanged" -> currentUrl = args?.getOrNull(0) as? String
                    "onAlert" -> Log.i(TAG, "Alert: ${args?.getOrNull(0)}")
                }
                null
            }

            val cv = ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent { ServoHostReflective(client, nav) }
            }
            composeView = cv
            container.removeAllViews()
            container.addView(cv, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            Log.i(TAG, "Servo view attached")
            pendingUrl?.let { url ->
                pendingUrl = null
                mainHandler.post { safeNavigate(url) }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Servo init failed", t)
            fail(t.message ?: t.javaClass.simpleName)
        }
    }

    private fun fail(reason: String) {
        failed = true
        container.removeAllViews()
        val label = TextView(context).apply {
            text = "Servo could not start\n$reason\n\nSwitch to Gecko in Settings."
            textSize = 16f
            setPadding(48, 48, 48, 48)
        }
        container.addView(label)
    }

    private fun safeNavigate(url: String) {
        val nav = navigator ?: return
        try {
            nav.javaClass.getMethod("navigate", String::class.java).invoke(nav, url)
        } catch (t: Throwable) {
            Log.w(TAG, "navigate failed", t)
        }
    }

    override fun getView(): View = container

    override fun loadUrl(url: String) {
        currentUrl = url
        if (failed || composeView == null) {
            pendingUrl = url
        } else {
            safeNavigate(url)
        }
        navigationListener?.onPageStarted(url)
    }

    override fun canGoBack(): Boolean = callBool("getCanGoBack")
    override fun goBack() { callVoid("back") }
    override fun canGoForward(): Boolean = callBool("getCanGoForward")
    override fun goForward() { callVoid("forward") }
    override fun reload() { callVoid("reload") }
    override fun stopLoading() { callVoid("stop") }
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
        navigator = null
    }
    override fun onResume() {}
    override fun onPause() {}

    private fun callBool(name: String): Boolean {
        val nav = navigator ?: return false
        return try {
            nav.javaClass.getMethod(name).invoke(nav) as? Boolean ?: false
        } catch (_: Throwable) { false }
    }

    private fun callVoid(name: String) {
        val nav = navigator ?: return
        try { nav.javaClass.getMethod(name).invoke(nav) } catch (_: Throwable) {}
    }
}

@Composable
private fun ServoHostReflective(client: Any, navigator: Any) {
    ServoHostDirect(client, navigator)
}

@Composable
private fun ServoHostDirect(client: Any, navigator: Any) {
    // Casts are safe only after successful Class.forName in tryInitServo
    org.servo.servoview.Servo(
        experimentalModeEnabled = false,
        softKeyboardVisible = false,
        args = null,
        initialUrl = null,
        logStr = null,
        client = client as org.servo.servoview.Servo.Client,
        navigator = navigator as org.servo.servoview.ServoNavigator,
        modifier = Modifier
    )
}
