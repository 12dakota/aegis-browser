package com.aegis.browser.engine

import android.content.Context
import android.net.Uri
import android.util.Log

/**
 * Decides which engine (Chromium or Gecko) should handle a given URL.
 *
 * Strategy:
 * 1. Explicit preference (AUTO / FORCE_CHROMIUM / FORCE_GECKO) – persisted + policy-overridable
 * 2. Domain preference lists
 * 3. Default → Chromium
 *
 * Both engines live in the same APK. Only one is active per page.
 */
object EngineSelector {

    private const val PREFS = "aegis_engine"
    private const val KEY_PREF = "preference"
    private const val TAG = "AegisEngineSelector"

    private val geckoPreferred = setOf(
        "mozilla.org",
        "addons.mozilla.org",
        "firefox.com",
        "bugzilla.mozilla.org",
        "developer.mozilla.org",
        "mdn.io"
    )

    private val chromiumPreferred = setOf(
        "google.com",
        "youtube.com",
        "gmail.com",
        "docs.google.com",
        "drive.google.com",
        "chrome.google.com",
        "android.com",
        "blogger.com",
        "blogspot.com"
    )

    enum class Preference {
        AUTO,
        FORCE_CHROMIUM,
        FORCE_GECKO
    }

    @Volatile
    private var currentPreference: Preference = Preference.AUTO

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_PREF, Preference.AUTO.name)
        currentPreference = try {
            Preference.valueOf(stored ?: Preference.AUTO.name)
        } catch (_: Exception) {
            Preference.AUTO
        }
        Log.i(TAG, "Engine preference loaded: $currentPreference")
    }

    var preference: Preference
        get() = currentPreference
        set(value) {
            currentPreference = value
        }

    fun setPreference(context: Context, pref: Preference) {
        currentPreference = pref
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PREF, pref.name)
            .apply()
        Log.i(TAG, "Engine preference saved: $pref")
    }

    fun select(url: String): BrowserEngine.Type {
        when (currentPreference) {
            Preference.FORCE_CHROMIUM -> return BrowserEngine.Type.CHROMIUM
            Preference.FORCE_GECKO -> return BrowserEngine.Type.GECKO
            Preference.AUTO -> { /* fall through */ }
        }

        // Extension pages only work in Gecko
        if (url.startsWith("moz-extension://", ignoreCase = true)) {
            return BrowserEngine.Type.GECKO
        }

        val host = try {
            Uri.parse(url).host?.lowercase()?.removePrefix("www.")
                ?: return BrowserEngine.Type.CHROMIUM
        } catch (_: Exception) {
            return BrowserEngine.Type.CHROMIUM
        }

        if (geckoPreferred.any { host == it || host.endsWith(".$it") }) {
            return BrowserEngine.Type.GECKO
        }
        if (chromiumPreferred.any { host == it || host.endsWith(".$it") }) {
            return BrowserEngine.Type.CHROMIUM
        }

        return BrowserEngine.Type.CHROMIUM
    }

    fun createEngine(context: Context, type: BrowserEngine.Type): BrowserEngine {
        return when (type) {
            BrowserEngine.Type.CHROMIUM -> ChromiumEngine(context)
            BrowserEngine.Type.GECKO -> GeckoEngine(context)
        }
    }

    fun opposite(type: BrowserEngine.Type): BrowserEngine.Type {
        return if (type == BrowserEngine.Type.CHROMIUM) BrowserEngine.Type.GECKO
        else BrowserEngine.Type.CHROMIUM
    }
}
