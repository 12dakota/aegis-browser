package com.aegis.browser.downloads

import android.content.Context
import com.aegis.browser.BuildConfig
import com.aegis.browser.engine.EngineSelector
import com.aegis.browser.extensions.ExtensionManager

/** Local switches for features not owned by MDM / cloud policy. */
object UserSettings {
    private const val PREFS = "provonlong_user"

    const val KEY_DOWNLOADS = "downloads_enabled"
    const val KEY_BACKGROUND = "downloads_background"
    const val KEY_JAVASCRIPT = "javascript_enabled"
    const val KEY_COOKIES = "cookies_enabled"
    const val KEY_POPUPS = "block_popups"
    const val KEY_DESKTOP_UA = "desktop_ua"
    const val KEY_SAFE_SEARCH = "force_safe_search_local"

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun downloadsEnabled(ctx: Context) = p(ctx).getBoolean(KEY_DOWNLOADS, BuildConfig.ADULT_BUILD)
    fun backgroundDownloads(ctx: Context) = p(ctx).getBoolean(KEY_BACKGROUND, true)
    fun javascriptEnabled(ctx: Context) = p(ctx).getBoolean(KEY_JAVASCRIPT, true)
    fun cookiesEnabled(ctx: Context) = p(ctx).getBoolean(KEY_COOKIES, true)
    fun blockPopups(ctx: Context) = p(ctx).getBoolean(KEY_POPUPS, true)
    fun desktopUa(ctx: Context) = p(ctx).getBoolean(KEY_DESKTOP_UA, false)
    fun forceSafeSearch(ctx: Context) = p(ctx).getBoolean(KEY_SAFE_SEARCH, !BuildConfig.ADULT_BUILD)

    fun set(ctx: Context, key: String, value: Boolean) {
        p(ctx).edit().putBoolean(key, value).apply()
        if (key == "allow_any_extension") {
            ExtensionManager.setAllowAny(ctx, value)
        }
    }

    fun engineLabel(ctx: Context): String = EngineSelector.label(
        when (EngineSelector.preference) {
            EngineSelector.Preference.FORCE_SERVO -> com.aegis.browser.engine.BrowserEngine.Type.CHROMIUM
            EngineSelector.Preference.FORCE_GECKO -> com.aegis.browser.engine.BrowserEngine.Type.GECKO
            EngineSelector.Preference.FORCE_WEBKIT -> com.aegis.browser.engine.BrowserEngine.Type.WEBKIT
            else -> com.aegis.browser.engine.BrowserEngine.Type.CHROMIUM
        }
    ) + if (EngineSelector.preference == EngineSelector.Preference.AUTO) " (Auto)" else ""
}
