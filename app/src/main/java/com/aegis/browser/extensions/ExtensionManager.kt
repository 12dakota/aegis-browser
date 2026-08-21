package com.aegis.browser.extensions

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.aegis.browser.engine.GeckoRuntimeHolder

/**
 * Allowlist-only extension system.
 * AMO + Chrome Web Store browse allowed; install only for allowlisted IDs.
 * uBlock Origin is on by default.
 */
object ExtensionManager {

    private const val TAG = "AegisExt"
    private const val PREFS = "aegis_extensions"

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
        )
    )

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isAllowlisted(id: String): Boolean =
        ALLOWLIST.any { it.id == id || it.amoId == id || it.chromeStoreId == id }

    fun isEnabled(ctx: Context, id: String): Boolean {
        val entry = ALLOWLIST.find { it.id == id } ?: return false
        return prefs(ctx).getBoolean("enabled_$id", entry.defaultEnabled)
    }

    fun setEnabled(ctx: Context, id: String, enabled: Boolean) {
        if (!isAllowlisted(id)) return
        prefs(ctx).edit().putBoolean("enabled_$id", enabled).apply()
    }

    fun enabledEntries(ctx: Context): List<CatalogEntry> =
        ALLOWLIST.filter { isEnabled(ctx, it.id) }

    fun ensureGeckoExtensions(ctx: Context) {
        try {
            val runtime = GeckoRuntimeHolder.get(ctx)
            val controller = runtime.webExtensionController
            // Install signed AMO XPIs for enabled allowlisted extensions.
            // GeckoView may show install prompts; only allowlisted IDs are in our catalog.
            for (entry in enabledEntries(ctx)) {
                Log.i(TAG, "Installing allowlisted extension: ${entry.name}")
                controller.install(entry.amoXpiUrl).accept(
                    { ext ->
                        if (ext != null && isAllowlisted(ext.id)) {
                            Log.i(TAG, "Installed ${ext.id}")
                        } else if (ext != null) {
                            Log.w(TAG, "Unexpected extension id ${ext.id} — uninstalling")
                            try { controller.uninstall(ext) } catch (_: Exception) {}
                        }
                    },
                    { e -> Log.w(TAG, "Install failed ${entry.id}: ${e?.message}") }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "ensureGeckoExtensions failed", e)
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
}
