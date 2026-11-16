package com.aegis.browser.engine

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Servo is not bundled in the adult mobile APK.
 * Selecting Servo shows a clear message and does not crash or load native code.
 */
class ServoEngine(context: Context) : BrowserEngine {

    override val type = BrowserEngine.Type.SERVO

    private val container = FrameLayout(context).apply {
        addView(TextView(context).apply {
            text = "Servo is not included in this mobile build.\n\nUse Gecko."
            textSize = 16f
            setPadding(48, 48, 48, 48)
        })
    }

    override fun getView(): View = container
    override fun loadUrl(url: String) {}
    override fun canGoBack() = false
    override fun goBack() {}
    override fun canGoForward() = false
    override fun goForward() {}
    override fun reload() {}
    override fun stopLoading() {}
    override fun getCurrentUrl(): String? = null
    override fun getTitle() = "Servo"
    override fun setProgressListener(listener: ((Int) -> Unit)?) {}
    override fun setNavigationListener(listener: BrowserEngine.NavigationListener?) {}
    override fun destroy() {}
    override fun onResume() {}
    override fun onPause() {}
}
