package com.aegis.browser.extensions

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.aegis.browser.engine.GeckoRuntimeHolder
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/**
 * Full GeckoView WebExtensions manager.
 * - PromptDelegate grants install (required or install never completes)
 * - Lists real installed extensions from GeckoRuntime
 * - Opens options / popup HTML pages in a Gecko tab
 * - Enable / disable / uninstall with parental allowlist or unrestricted mode
 */
object ExtensionManager {

    private const val TAG = "AegisExt"
    private const val PREFS = "aegis_extensions"
    private const val KEY_ALLOW_ANY = "allow_any_extension"
    private const val KEY_GRANTED_PERMS = "granted_perms_" // + extension id

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

    /** Snapshot of a live Gecko-installed extension */
    data class InstalledExtension(
        val id: String,
        val name: String,
        val description: String,
        val version: String,
        val enabled: Boolean,
        val optionsPageUrl: String?,
        val baseUrl: String?,
        val permissions: List<String>,
        val origins: List<String>,
        val isBuiltIn: Boolean
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

    fun matchByUrl(url: String): CatalogEntry? {
        val lower = url.lowercase()
        return ALLOWLIST.find { e ->
            lower.contains(e.id) ||
                lower.contains(e.amoId.lowercase()) ||
                lower.contains(e.chromeStoreId.lowercase()) ||
                (e.id == "ublock-origin" && lower.contains("ublock"))
        }
    }

    /** Must be set before install — Gecko denies install if PromptDelegate is null or returns null. */
    fun ensurePromptDelegate(ctx: Context) {
        try {
            val controller = GeckoRuntimeHolder.get(ctx).webExtensionController
            controller.promptDelegate = object : WebExtensionController.PromptDelegate {
                override fun onInstallPromptRequest(
                    extension: WebExtension,
                    permissions: Array<String>,
                    origins: Array<String>,
                    dataCollectionPermissions: Array<String>
                ): GeckoResult<WebExtension.PermissionPromptResponse>? {
                    val id = extension.id
                    val allow = isAllowAny(ctx) || isAllowlisted(id) ||
                        ALLOWLIST.any { it.amoId == id || id.contains(it.id) }
                    Log.i(TAG, "onInstallPromptRequest id=$id allow=$allow perms=${permissions.joinToString()}")
                    prefs(ctx).edit()
                        .putString(KEY_GRANTED_PERMS + id, permissions.joinToString(","))
                        .apply()
                    // Grant permissions + private browsing; deny technical data collection by default
                    return GeckoResult.fromValue(
                        WebExtension.PermissionPromptResponse(allow, allow, false)
                    )
                }

                override fun onOptionalPrompt(
                    extension: WebExtension,
                    permissions: Array<String>,
                    origins: Array<String>,
                    dataCollectionPermissions: Array<String>
                ): GeckoResult<AllowOrDeny>? {
                    val allow = isAllowAny(ctx) || isAllowlisted(extension.id)
                    Log.i(TAG, "onOptionalPrompt id=${extension.id} allow=$allow")
                    return GeckoResult.fromValue(if (allow) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
                }

                override fun onUpdatePrompt(
                    extension: WebExtension,
                    newPermissions: Array<String>,
                    newOrigins: Array<String>,
                    newDataCollectionPermissions: Array<String>
                ): GeckoResult<AllowOrDeny>? {
                    val allow = isAllowAny(ctx) || isAllowlisted(extension.id)
                    return GeckoResult.fromValue(if (allow) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
                }
            }
            Log.i(TAG, "PromptDelegate attached (allowAny=" + isAllowAny(ctx) + ")")
        } catch (e: Exception) {
            Log.e(TAG, "PromptDelegate setup failed — trying 3-arg only", e)
            try {
                val controller = GeckoRuntimeHolder.get(ctx).webExtensionController
                controller.promptDelegate = object : WebExtensionController.PromptDelegate {
                    override fun onInstallPromptRequest(
                        extension: WebExtension,
                        permissions: Array<String>,
                        origins: Array<String>
                    ): GeckoResult<WebExtension.PermissionPromptResponse>? {
                        val allow = isAllowAny(ctx) || isAllowlisted(extension.id) ||
                            ALLOWLIST.any { it.amoId == extension.id }
                        return GeckoResult.fromValue(
                            WebExtension.PermissionPromptResponse(allow, allow, false)
                        )
                    }
                }
            } catch (e2: Exception) {
                Log.e(TAG, "PromptDelegate fallback failed", e2)
            }
        }
    }

    fun ensureGeckoExtensions(ctx: Context) {
        ensurePromptDelegate(ctx)
        try {
            val controller = GeckoRuntimeHolder.get(ctx).webExtensionController
            for (entry in enabledEntries(ctx)) {
                if (entry.amoXpiUrl.isBlank()) continue
                Log.i(TAG, "Installing catalog: " + entry.name)
                controller.install(entry.amoXpiUrl).accept(
                    { ext -> Log.i(TAG, "Installed " + (ext?.id ?: "?")) },
                    { e -> Log.w(TAG, "Install failed " + entry.id + ": " + (e?.message ?: "?")) }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "ensureGeckoExtensions failed", e)
        }
    }

    fun installFromUrl(ctx: Context, xpiUrl: String, onDone: (Boolean, String) -> Unit) {
        ensurePromptDelegate(ctx)
        try {
            val controller = GeckoRuntimeHolder.get(ctx).webExtensionController
            Log.i(TAG, "installFromUrl: $xpiUrl")
            controller.install(xpiUrl).accept(
                { ext ->
                    val id = ext?.id ?: "?"
                    val name = try { ext?.metaData?.name } catch (_: Exception) { null } ?: id
                    Log.i(TAG, "Installed $id name=$name")
                    // Mark matching catalog entry enabled
                    ALLOWLIST.find { it.amoId == id || id.contains(it.id) }?.let {
                        setEnabled(ctx, it.id, true)
                    }
                    onDone(true, name)
                },
                { e ->
                    val msg = e?.message ?: "unknown error"
                    Log.w(TAG, "Install failed: $msg", e)
                    onDone(false, msg)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "installFromUrl exception", e)
            onDone(false, e.message ?: "exception")
        }
    }

    fun listInstalled(ctx: Context, onResult: (List<InstalledExtension>) -> Unit) {
        try {
            ensurePromptDelegate(ctx)
            val controller = GeckoRuntimeHolder.get(ctx).webExtensionController
            controller.list().accept(
                { list ->
                    val out = list?.mapNotNull { ext ->
                        try {
                            val md = ext.metaData
                            InstalledExtension(
                                id = ext.id,
                                name = md?.name ?: ext.id,
                                description = md?.description ?: "",
                                version = md?.version ?: "",
                                enabled = md?.enabled ?: true,
                                optionsPageUrl = md?.optionsPageUrl,
                                baseUrl = md?.baseUrl,
                                permissions = md?.requiredPermissions?.toList() ?: emptyList(),
                                origins = md?.requiredOrigins?.toList() ?: emptyList(),
                                isBuiltIn = ext.isBuiltIn
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "map extension failed " + ext.id, e)
                            InstalledExtension(
                                id = ext.id,
                                name = ext.id,
                                description = "",
                                version = "",
                                enabled = true,
                                optionsPageUrl = null,
                                baseUrl = null,
                                permissions = emptyList(),
                                origins = emptyList(),
                                isBuiltIn = false
                            )
                        }
                    } ?: emptyList()
                    onResult(out)
                },
                { e ->
                    Log.w(TAG, "list failed", e)
                    onResult(emptyList())
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "listInstalled", e)
            onResult(emptyList())
        }
    }

    fun listInstalledGecko(ctx: Context, onResult: (List<String>) -> Unit) {
        listInstalled(ctx) { onResult(it.map { e -> e.id }) }
    }

    fun setExtensionEnabled(ctx: Context, extensionId: String, enabled: Boolean, onDone: (Boolean, String) -> Unit) {
        try {
            val controller = GeckoRuntimeHolder.get(ctx).webExtensionController
            controller.list().accept(
                { list ->
                    val ext = list?.find { it.id == extensionId }
                    if (ext == null) {
                        onDone(false, "Not found")
                        return@accept
                    }
                    val result = if (enabled) {
                        controller.enable(ext, WebExtensionController.EnableSource.USER)
                    } else {
                        controller.disable(ext, WebExtensionController.EnableSource.USER)
                    }
                    result.accept(
                        { onDone(true, if (enabled) "Enabled" else "Disabled") },
                        { e -> onDone(false, e?.message ?: "failed") }
                    )
                },
                { e -> onDone(false, e?.message ?: "list failed") }
            )
        } catch (e: Exception) {
            onDone(false, e.message ?: "error")
        }
    }

    fun uninstall(ctx: Context, extensionId: String, onDone: (Boolean, String) -> Unit) {
        try {
            val controller = GeckoRuntimeHolder.get(ctx).webExtensionController
            controller.list().accept(
                { list ->
                    val ext = list?.find { it.id == extensionId }
                    if (ext == null) {
                        onDone(false, "Not found")
                        return@accept
                    }
                    controller.uninstall(ext).accept(
                        { onDone(true, "Uninstalled") },
                        { e -> onDone(false, e?.message ?: "failed") }
                    )
                },
                { e -> onDone(false, e?.message ?: "list failed") }
            )
        } catch (e: Exception) {
            onDone(false, e.message ?: "error")
        }
    }

    /**
     * Best URL to open for configuration UI of an installed extension.
     * Prefer options page; fall back to common popup paths under moz-extension://
     */
    fun optionsOrPopupUrl(installed: InstalledExtension): String? {
        val opt = installed.optionsPageUrl
        if (!opt.isNullOrBlank()) return opt
        val base = installed.baseUrl?.trimEnd('/') ?: return null
        // Common AMO extension popup/options paths
        val candidates = listOf(
            "$base/options.html",
            "$base/options_ui.html",
            "$base/settings.html",
            "$base/popup.html",
            "$base/popup/index.html",
            "$base/dashboard.html"
        )
        return candidates.firstOrNull()
    }

    fun grantedPermsSummary(ctx: Context, extensionId: String): String {
        return prefs(ctx).getString(KEY_GRANTED_PERMS + extensionId, "") ?: ""
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
