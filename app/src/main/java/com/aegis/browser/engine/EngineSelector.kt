package com.aegis.browser.engine

import android.content.Context
import android.net.Uri
import android.util.Log

/**
 * Automatic and forced selection between:
 * - SERVO — Rust-based Servo engine (replaces Blink/WebView)
 * - GECKO — Mozilla GeckoView (WebExtensions / AMO)
 *
 * AUTO mode picks per URL when the user navigates (see BrowserActivity.loadInActiveTab).
 */
object EngineSelector {

    private const val PREFS = "aegis_engine"
    private const val KEY_PREF = "preference"
    private const val KEY_DOMAIN_OVERRIDES = "domain_overrides" // host=S|G|W pairs
    private const val TAG = "AegisEngineSelector"

    /** Sites that work better / need Gecko (extensions store, Mozilla, etc.) */
    private val geckoPreferred = setOf(
        "mozilla.org",
        "addons.mozilla.org",
        "addons.mozilla.net",
        "firefox.com",
        "bugzilla.mozilla.org",
        "developer.mozilla.org",
        "mdn.io",
        "mdn.dev",
        "thunderbird.net",
        "getpocket.com",
        "support.mozilla.org"
    )

    /** Sites that typically prefer Servo (or general web) */
    private val servoPreferred = setOf(
        "google.com",
        "youtube.com",
        "youtu.be",
        "gmail.com",
        "docs.google.com",
        "drive.google.com",
        "sheets.google.com",
        "slides.google.com",
        "meet.google.com",
        "chrome.google.com",
        "chromewebstore.google.com",
        "android.com",
        "blogger.com",
        "blogspot.com",
        "googleusercontent.com",
        "gstatic.com",
        "withgoogle.com",
        "netflix.com",
        "office.com",
        "microsoft.com",
        "live.com",
        "outlook.live.com",
        "amazon.com",
        "aws.amazon.com"
    )

    private val webkitPreferred = setOf(
        "webkit.org",
        "wpewebkit.org",
        "apple.com",
        "developer.apple.com",
        "icloud.com"
    )

    enum class Preference {
        AUTO,
        FORCE_SERVO,
        FORCE_GECKO,
        FORCE_WEBKIT
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

    /** Remember that this host should use a given engine (learned from manual toggle). */
    fun rememberHostEngine(context: Context, url: String, type: BrowserEngine.Type) {
        val host = hostOf(url) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val map = prefs.getString(KEY_DOMAIN_OVERRIDES, "") ?: ""
        val entries = map.split(';').filter { it.isNotBlank() && !it.startsWith("$host=") }.toMutableList()
        val flag = when (type) {
            BrowserEngine.Type.GECKO -> "G"
            BrowserEngine.Type.WEBKIT -> "W"
            else -> "S"
        }
        entries.add("$host=$flag")
        // Cap size
        val trimmed = entries.takeLast(100)
        prefs.edit().putString(KEY_DOMAIN_OVERRIDES, trimmed.joinToString(";")).apply()
    }

    private fun learnedEngine(context: Context?, url: String): BrowserEngine.Type? {
        if (context == null) return null
        val host = hostOf(url) ?: return null
        val map = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DOMAIN_OVERRIDES, "") ?: return null
        for (part in map.split(';')) {
            val kv = part.split('=')
            if (kv.size == 2 && (host == kv[0] || host.endsWith("." + kv[0]))) {
                return when (kv[1]) {
                    "G" -> BrowserEngine.Type.GECKO
                    "W" -> BrowserEngine.Type.WEBKIT
                    else -> BrowserEngine.Type.SERVO
                }
            }
        }
        return null
    }

    fun select(url: String, context: Context? = null): BrowserEngine.Type {
        when (currentPreference) {
            Preference.FORCE_SERVO -> return BrowserEngine.Type.SERVO
            Preference.FORCE_GECKO -> return BrowserEngine.Type.GECKO
            Preference.FORCE_WEBKIT -> return BrowserEngine.Type.WEBKIT
            Preference.AUTO -> { /* continue */ }
        }

        // Extension and privileged schemes
        val lower = url.lowercase()
        if (lower.startsWith("moz-extension://")) return BrowserEngine.Type.GECKO
        if (lower.startsWith("chrome-extension://")) {
            // No real Chrome extension host yet — still route to Servo path for clarity
            return BrowserEngine.Type.SERVO
        }
        if (lower.contains("addons.mozilla.org") || lower.contains("/downloads/") && lower.contains(".xpi")) {
            return BrowserEngine.Type.GECKO
        }
        if (lower.contains("chromewebstore.google.com") || lower.contains("chrome.google.com/webstore")) {
            return BrowserEngine.Type.SERVO
        }

        learnedEngine(context, url)?.let { return it }

        val host = hostOf(url) ?: return BrowserEngine.Type.SERVO

        if (geckoPreferred.any { host == it || host.endsWith(".$it") }) {
            return BrowserEngine.Type.GECKO
        }
        if (webkitPreferred.any { host == it || host.endsWith(".$it") }) {
            return BrowserEngine.Type.WEBKIT
        }
        if (servoPreferred.any { host == it || host.endsWith(".$it") }) {
            return BrowserEngine.Type.SERVO
        }

        // Default AUTO → Servo path (best general web + Google sites)
        return BrowserEngine.Type.SERVO
    }

    private fun hostOf(url: String): String? {
        return try {
            Uri.parse(url).host?.lowercase()?.removePrefix("www.")
        } catch (_: Exception) {
            null
        }
    }

    fun createEngine(context: Context, type: BrowserEngine.Type): BrowserEngine {
        return when (type) {
            BrowserEngine.Type.SERVO -> ServoEngine(context)
            BrowserEngine.Type.GECKO -> GeckoEngine(context)
            BrowserEngine.Type.WEBKIT -> WpeEngine(context)
        }
    }

    fun opposite(type: BrowserEngine.Type): BrowserEngine.Type {
        return when (type) {
            BrowserEngine.Type.SERVO -> BrowserEngine.Type.GECKO
            BrowserEngine.Type.GECKO -> BrowserEngine.Type.WEBKIT
            BrowserEngine.Type.WEBKIT -> BrowserEngine.Type.SERVO
        }
    }

    fun label(type: BrowserEngine.Type): String {
        return when (type) {
            BrowserEngine.Type.SERVO -> "S (Servo)"
            BrowserEngine.Type.GECKO -> "G (Gecko)"
            BrowserEngine.Type.WEBKIT -> "W (WebKit/WPE)"
        }
    }
}
