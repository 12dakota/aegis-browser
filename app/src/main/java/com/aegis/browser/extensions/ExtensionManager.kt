package com.aegis.browser.extensions

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.aegis.browser.engine.GeckoRuntimeHolder
import org.mozilla.geckoview.WebExtensionController

/**
 * Extension system: parental allowlist by default.
 * Parents can enable allowAnyExtension (unrestricted) via MDM/cloud policy.
 */
object ExtensionManager {

    private const val TAG = "AegisExt"
    private const val PREFS = "aegis_extensions"
    private const val KEY_ALLOW_ANY = "allow_any_extension"

    data class CatalogEntry(
        val id: String,
        val name: String,
        val description: String,
        val amoId: String,
        val amoXpiUrl: String,
        val chromeStoreId: String,
        val chromeStoreUrl: String,
        val defaultEnabled: Boolean = false
    )

    val ALLOWLIST: List<CatalogEntry> = listOf(
        CatalogEntry(
            id = "ublock-origin",
            name = "uBlock Origin",
            description = "Efficient wide-spectrum content blocker",
            amoId = "uBlock0@raymondhill.net",
            amoXpiUrl = "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi",
            chromeStoreId = "cjpalhdlnbpafilfilklakntgclifmdh",
            chromeStoreUrl = "https://chromewebstore.google.com/detail/ublock-origin/cjpalhdlnbpafilfilklakntgclifmdh",
            defaultEnabled = true
        ),
        CatalogEntry(
            id = "privacy-badger",
            name = "Privacy Badger",
            description = "Blocks invisible trackers",
            amoId = "jid1-MnnxcxisBPnSXQ@jetpack",
            amoXpiUrl = "https://addons.mozilla.org/firefox/downloads/latest/privacy-badger17/latest.xpi",
            chromeStoreId = "pkehgijcmpdhfbdbbnkijodmdjhbjlgp",
            chromeStoreUrl = "https://chromewebstore.google.com/detail/privacy-badger/pkehgijcmpdhfbdbbnkijodmdjhbjlgp",
            defaultEnabled = false
        )
    )

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isAllowAny(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ALLOW_ANY, false)

    fun setAllowAny(ctx: Context, allow: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ALLOW_ANY, allow).apply()
    }

    fun isAllowlisted(id: String): Boolean =
        ALLOWLIST.any { it.id == id || it.amoId == id || it.chromeStoreId == id }

    fun isEnabled(ctx: Context, id: String): Boolean {
        val entry = ALLOWLIST.find { it.id == id } ?: return false
        return prefs(ctx).getBoolean("enabled_" + id, entry.defaultEnabled)
    }

    fun setEnabled(ctx: Context, id: String, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("enabled_" + id, enabled).apply()
    }

    fun enabledEntries(ctx: Context): List<CatalogEntry> =
        ALLOWLIST.filter { isEnabled(ctx, it.id) }

    /** Match catalog by URL slug / id */
    fun matchByUrl(url: String): CatalogEntry? {
        val lower = url.lowercase()
        return ALLOWLIST.find { e ->
            lower.contains(e.id) ||
                lower.contains(e.amoId.lowercase()) ||
                lower.contains(e.chromeStoreId.lowercase()) ||
                (e.id == "ublock-origin" && lower.contains("ublock"))
        }
    }

    fun ensurePromptDelegate(ctx: Context) {
        try {
            val controller = GeckoRuntimeHolder.get(ctx).webExtensionController
            // Default prompt delegate — Gecko may auto-accept signed AMO XPIs depending on version.
            controller.setPromptDelegate(object : WebExtensionController.PromptDelegate {})
            Log.i(TAG, "PromptDelegate attached (allowAny=" + isAllowAny(ctx) + ")")
        } catch (e: Exception) {
            Log.w(TAG, "PromptDelegate setup: " + e.message)
        }
    }

    fun ensureGeckoExtensions(ctx: Context) {
        ensurePromptDelegate(ctx)
        try {
            val controller = GeckoRuntimeHolder.get(ctx).webExtensionController
            for (entry in enabledEntries(ctx)) {
                if (entry.amoXpiUrl.isBlank()) continue
                Log.i(TAG, "Installing: " + entry.name)
                controller.install(entry.amoXpiUrl).accept(
                    { ext -> Log.i(TAG, "Installed " + (ext?.id ?: "?")) },
                    { e -> Log.w(TAG, "Install failed " + entry.id + ": " + (e?.message ?: "?")) }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "ensureGeckoExtensions failed", e)
        }
    }

    /**
     * Install from a remote XPI URL (AMO "Add to Firefox" path).
     * @param onDone main-thread-safe callback with success message or error
     */
    fun installFromUrl(ctx: Context, xpiUrl: String, onDone: (Boolean, String) -> Unit) {
        ensurePromptDelegate(ctx)
        try {
            val controller = GeckoRuntimeHolder.get(ctx).webExtensionController
            Log.i(TAG, "installFromUrl: " + xpiUrl)
            controller.install(xpiUrl).accept(
                { ext ->
                    val id = ext?.id ?: "?"
                    Log.i(TAG, "Installed " + id)
                    onDone(true, "Installed: " + id)
                },
                { e ->
                    val msg = e?.message ?: "unknown error"
                    Log.w(TAG, "Install failed: " + msg)
                    onDone(false, msg)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "installFromUrl exception", e)
            onDone(false, e.message ?: "exception")
        }
    }

    fun listInstalledGecko(ctx: Context, onResult: (List<String>) -> Unit) {
        try {
            val controller = GeckoRuntimeHolder.get(ctx).webExtensionController
            controller.list().accept(
                { list -> onResult(list?.map { it.id } ?: emptyList()) },
                { onResult(emptyList()) }
            )
        } catch (_: Exception) {
            onResult(emptyList())
        }
    }

    fun isStoreBrowseUrl(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("addons.mozilla.org") ||
            u.contains("chrome.google.com/webstore") ||
            u.contains("chromewebstore.google.com")
    }

    fun isXpiOrAmoDownload(url: String): Boolean {
        val u = url.lowercase()
        return u.contains(".xpi") ||
            (u.contains("addons.mozilla.org") && u.contains("/downloads/"))
    }
}
