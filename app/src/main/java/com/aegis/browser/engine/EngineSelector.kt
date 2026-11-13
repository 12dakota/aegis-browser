package com.aegis.browser.engine

import android.content.Context
import android.net.Uri
import android.util.Log

/**
 * Selection between SERVO, GECKO, WEBKIT, and CUSTOM (user-imported).
 * No Blink/Chromium.
 */
object EngineSelector {

    private const val PREFS = "aegis_engine"
    private const val KEY_PREF = "preference"
    private const val KEY_DOMAIN_OVERRIDES = "domain_overrides"
    private const val KEY_CUSTOM_PATH = "custom_engine_path"
    private const val KEY_CUSTOM_CLASS = "custom_engine_class"
    private const val TAG = "AegisEngineSelector"

    private val geckoPreferred = setOf(
        "mozilla.org", "addons.mozilla.org", "firefox.com", "developer.mozilla.org"
    )

    private val webkitPreferred = setOf(
        "webkit.org", "wpewebkit.org", "apple.com"
    )

    enum class Preference {
        AUTO,
        FORCE_SERVO,
        FORCE_GECKO,
        FORCE_WEBKIT,
        FORCE_CUSTOM
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
        set(value) { currentPreference = value }

    fun setPreference(context: Context, pref: Preference) {
        currentPreference = pref
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PREF, pref.name).apply()
        Log.i(TAG, "Engine preference saved: $pref")
    }

    fun setCustomEngine(context: Context, path: String, className: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_CUSTOM_PATH, path)
            .putString(KEY_CUSTOM_CLASS, className)
            .apply()
        Log.i(TAG, "Custom engine set: $className @ $path")
    }

    fun rememberHostEngine(context: Context, url: String, type: BrowserEngine.Type) {
        val host = hostOf(url) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val map = prefs.getString(KEY_DOMAIN_OVERRIDES, "") ?: ""
        val entries = map.split(';').filter { it.isNotBlank() && !it.startsWith("$host=") }.toMutableList()
        val flag = when (type) {
            BrowserEngine.Type.GECKO -> "G"
            BrowserEngine.Type.WEBKIT -> "W"
            BrowserEngine.Type.CUSTOM -> "X"
            else -> "S"
        }
        entries.add("$host=$flag")
        prefs.edit().putString(KEY_DOMAIN_OVERRIDES, entries.takeLast(100).joinToString(";")).apply()
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
                    "X" -> BrowserEngine.Type.CUSTOM
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
            Preference.FORCE_CUSTOM -> return BrowserEngine.Type.CUSTOM
            Preference.AUTO -> {}
        }

        learnedEngine(context, url)?.let { return it }

        val host = hostOf(url) ?: return BrowserEngine.Type.SERVO

        if (geckoPreferred.any { host == it || host.endsWith(".$it") }) {
            return BrowserEngine.Type.GECKO
        }
        if (webkitPreferred.any { host == it || host.endsWith(".$it") }) {
            return BrowserEngine.Type.WEBKIT
        }

        return BrowserEngine.Type.SERVO // default
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
            BrowserEngine.Type.CUSTOM -> {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val path = prefs.getString(KEY_CUSTOM_PATH, null)
                val cls = prefs.getString(KEY_CUSTOM_CLASS, null)
                if (path != null && cls != null) CustomEngine(context, path, cls)
                else ServoEngine(context)
            }
        }
    }

    fun opposite(type: BrowserEngine.Type): BrowserEngine.Type {
        return when (type) {
            BrowserEngine.Type.SERVO -> BrowserEngine.Type.GECKO
            BrowserEngine.Type.GECKO -> BrowserEngine.Type.WEBKIT
            BrowserEngine.Type.WEBKIT -> BrowserEngine.Type.CUSTOM
            BrowserEngine.Type.CUSTOM -> BrowserEngine.Type.SERVO
        }
    }

    fun label(type: BrowserEngine.Type): String {
        return when (type) {
            BrowserEngine.Type.SERVO -> "S (Servo)"
            BrowserEngine.Type.GECKO -> "G (Gecko)"
            BrowserEngine.Type.WEBKIT -> "W (WebKit)"
            BrowserEngine.Type.CUSTOM -> "X (Custom)"
        }
    }
}