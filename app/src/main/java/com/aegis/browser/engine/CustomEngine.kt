package com.aegis.browser.engine

import android.content.Context
import android.view.View
import dalvik.system.DexClassLoader
import java.io.File

/**
 * Loads a user-imported engine from a local APK/DEX or directory.
 * Expected: a class with a static method `create(Context): BrowserEngine`
 * or a class that implements BrowserEngine with a Context constructor.
 */
class CustomEngine(context: Context, private val enginePath: String, private val className: String) : BrowserEngine {

    override val type = BrowserEngine.Type.CUSTOM

    private val delegate: BrowserEngine

    init {
        val dexOutput = File(context.codeCacheDir, "custom_engine_dex").apply { mkdirs() }
        val loader = DexClassLoader(enginePath, dexOutput.absolutePath, null, context.classLoader)
        val clazz = loader.loadClass(className)
        delegate = try {
            val method = clazz.getMethod("create", Context::class.java)
            method.invoke(null, context) as BrowserEngine
        } catch (_: Exception) {
            clazz.getConstructor(Context::class.java).newInstance(context) as BrowserEngine
        }
    }

    override fun getView() = delegate.getView()
    override fun loadUrl(url: String) = delegate.loadUrl(url)
    override fun canGoBack() = delegate.canGoBack()
    override fun goBack() = delegate.goBack()
    override fun canGoForward() = delegate.canGoForward()
    override fun goForward() = delegate.goForward()
    override fun reload() = delegate.reload()
    override fun stopLoading() = delegate.stopLoading()
    override fun getCurrentUrl() = delegate.getCurrentUrl()
    override fun getTitle() = delegate.getTitle()
    override fun setProgressListener(listener: ((Int) -> Unit)?) = delegate.setProgressListener(listener)
    override fun setNavigationListener(listener: BrowserEngine.NavigationListener?) = delegate.setNavigationListener(listener)
    override fun destroy() = delegate.destroy()
    override fun onResume() = delegate.onResume()
    override fun onPause() = delegate.onPause()
}