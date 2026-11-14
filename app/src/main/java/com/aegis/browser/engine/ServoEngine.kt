package com.aegis.browser.engine

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.util.Log

/**
 * Stub Servo engine.
 *
 * Real integration requires the official servo-aarch64-android.aar
 * placed in app/libs/ and the dependency uncommented in app/build.gradle.kts.
 *
 * This stub keeps the project building until the AAR is available.
 */
class ServoEngine(context: Context) : BrowserEngine {

    override val type = BrowserEngine.Type.SERVO

    private val container = FrameLayout(context).apply {
        val label = TextView(context).apply {
            text = "Servo engine (stub)\nPlace servo-aarch64-android.aar in app/libs/"
            textSize = 16f
            setPadding(32, 32, 32, 32)
        }
        addView(label)
    }

    private var progressListener: ((Int) -> Unit)? = null
    private var navigationListener: BrowserEngine.NavigationListener? = null
    private var currentUrl: String? = null

    init {
        Log.i("ServoEngine", "Using stub ServoEngine — real AAR not present")
    }

    override fun getView(): View = container

    override fun loadUrl(url: String) {
        currentUrl = url
        navigationListener?.onPageStarted(url)
        progressListener?.invoke(100)
        navigationListener?.onPageFinished(url)
    }

    override fun canGoBack() = false
    override fun goBack() {}
    override fun canGoForward() = false
    override fun goForward() {}
    override fun reload() { loadUrl(currentUrl ?: "https://servo.org") }
    override fun stopLoading() {}
    override fun getCurrentUrl() = currentUrl
    override fun getTitle() = "Servo (stub)"
    override fun setProgressListener(listener: ((Int) -> Unit)?) { progressListener = listener }
    override fun setNavigationListener(listener: BrowserEngine.NavigationListener?) {
        navigationListener = listener
    }
    override fun destroy() {}
    override fun onResume() {}
    override fun onPause() {}
}