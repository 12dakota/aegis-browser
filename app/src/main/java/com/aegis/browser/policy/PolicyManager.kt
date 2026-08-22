package com.aegis.browser.policy

import android.content.Context
import android.content.SharedPreferences
import android.content.RestrictionsManager
import android.util.Log
import com.aegis.browser.auth.SessionStore
import java.time.LocalTime
import com.aegis.browser.extensions.ExtensionManager
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.json.JSONArray
import java.time.format.DateTimeFormatter

/**
 * Central policy engine — no Firebase client dependency.
 * Sources: MDM App Restrictions + local session prefs + remote commands.
 * Cloud policy can later be pulled from the Cloudflare Worker with the session token.
 */
class PolicyManager(
    private val context: Context,
    forcePlainPrefs: Boolean = false
) {

    private val prefs: SharedPreferences = createPrefs(forcePlainPrefs)

    private fun createPrefs(forcePlain: Boolean): SharedPreferences {
        if (!forcePlain) {
            try {
                val masterKey = androidx.security.crypto.MasterKey.Builder(context)
                    .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                    .build()
                return androidx.security.crypto.EncryptedSharedPreferences.create(
                    context,
                    "aegis_policy_secure",
                    masterKey,
                    androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                Log.w(TAG, "Encrypted prefs unavailable – using plain SharedPreferences", e)
            }
        }
        return context.getSharedPreferences("aegis_policy_plain", Context.MODE_PRIVATE)
    }

    data class BrowserPolicy(
        val lockdownEnabled: Boolean = false,
        val pacUrl: String = "",
        val pacContent: String = "",
        val forceSystemProxy: Boolean = true,
        val allowedDomains: Set<String> = emptySet(),
        val blockedCategories: Set<String> = setOf("adult", "gambling"),
        val timeStart: String? = null,
        val timeEnd: String? = null,
        val disableDownloads: Boolean = true,
        val disableUploads: Boolean = true,
        val forceSafeSearch: Boolean = true,
        val disableIncognito: Boolean = true,
        val allowAnyExtension: Boolean = false,
        val organizationId: String = "",
        val enrollmentToken: String = ""
    )

    @Volatile
    var currentPolicy: BrowserPolicy = BrowserPolicy()
        private set

    suspend fun refreshPolicies() {
        val mdmPolicy = readMdmRestrictions()
        val cloudPolicy = readCloudPolicy()
        val localLockdown = prefs.getBoolean(KEY_REMOTE_LOCKDOWN, false)
        val orgId = SessionStore.getOrgId(context).orEmpty()
            .ifBlank { mdmPolicy.organizationId }

        currentPolicy = BrowserPolicy(
            lockdownEnabled = localLockdown || mdmPolicy.lockdownEnabled || cloudPolicy.lockdownEnabled,
            pacUrl = mdmPolicy.pacUrl.ifBlank { cloudPolicy.pacUrl },
            pacContent = mdmPolicy.pacContent.ifBlank { cloudPolicy.pacContent },
            forceSystemProxy = mdmPolicy.forceSystemProxy,
            allowedDomains = mdmPolicy.allowedDomains.ifEmpty { cloudPolicy.allowedDomains },
            blockedCategories = mdmPolicy.blockedCategories.ifEmpty { cloudPolicy.blockedCategories },
            timeStart = mdmPolicy.timeStart ?: cloudPolicy.timeStart,
            timeEnd = mdmPolicy.timeEnd ?: cloudPolicy.timeEnd,
            disableDownloads = mdmPolicy.disableDownloads,
            disableUploads = mdmPolicy.disableUploads,
            forceSafeSearch = mdmPolicy.forceSafeSearch || cloudPolicy.forceSafeSearch,
            disableIncognito = mdmPolicy.disableIncognito,
            allowAnyExtension = cloudPolicy.allowAnyExtension,
            organizationId = orgId,
            enrollmentToken = mdmPolicy.enrollmentToken
        )
        ExtensionManager.setAllowAny(context, cloudPolicy.allowAnyExtension)
        Log.i(TAG, "Applied allowAnyExtension=" + cloudPolicy.allowAnyExtension)

        Log.i(TAG, "Policies refreshed. Lockdown=${currentPolicy.lockdownEnabled}")
        prefs.edit().putBoolean(KEY_LAST_LOCKDOWN, currentPolicy.lockdownEnabled).apply()
    }



    private fun readCloudPolicy(): BrowserPolicy {
        return try {
            val userId = SessionStore.getUserId(context).orEmpty()
            val token = SessionStore.getToken(context).orEmpty()
            val base = "https://aegis-auth.dakdra03.workers.dev/policy/get"
            val qs = if (userId.isNotBlank()) "?userId=" + java.net.URLEncoder.encode(userId, "UTF-8") else ""
            val url = URL(base + qs)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000
                readTimeout = 12000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                if (token.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
            }
            if (conn.responseCode !in 200..299) {
                Log.w(TAG, "Cloud policy HTTP " + conn.responseCode)
                return BrowserPolicy()
            }
            val text = conn.inputStream.bufferedReader().readText()
            Log.i(TAG, "Cloud policy response length=" + text.length)
            val root = JSONObject(text)
            val pol = root.optJSONObject("policy") ?: return BrowserPolicy()
            val allowed = mutableSetOf<String>()
            val arr = pol.optJSONArray("allowedDomains")
            if (arr != null) for (i in 0 until arr.length()) allowed.add(arr.getString(i).lowercase())
            val blockedCat = mutableSetOf<String>()
            val bc = pol.optJSONArray("blockedCategories")
            if (bc != null) for (i in 0 until bc.length()) blockedCat.add(bc.getString(i).lowercase())
            val allowAny = pol.optBoolean("allowAnyExtension", false)
            // Always sync local unrestricted flag from cloud (true or false)
            ExtensionManager.setAllowAny(context, allowAny)
            Log.i(TAG, "Cloud policy allowAnyExtension=" + allowAny + " scope=" + root.optString("scope", "?"))
            val ext = root.optJSONObject("extensions")
            if (ext != null) {
                prefs.edit().putString("cloud_extensions_json", ext.toString()).apply()
            }
            BrowserPolicy(
                lockdownEnabled = pol.optBoolean("lockdownEnabled", false),
                pacUrl = pol.optString("pacUrl", ""),
                forceSafeSearch = pol.optBoolean("forceSafeSearch", true),
                allowedDomains = allowed,
                blockedCategories = if (blockedCat.isEmpty()) setOf("adult", "gambling") else blockedCat,
                timeStart = pol.optString("timeStart", null).takeIf { !it.isNullOrBlank() && it != "null" },
                timeEnd = pol.optString("timeEnd", null).takeIf { !it.isNullOrBlank() && it != "null" },
                disableDownloads = pol.optBoolean("disableDownloads", true),
                disableUploads = pol.optBoolean("disableUploads", true),
                disableIncognito = pol.optBoolean("disableIncognito", true),
                allowAnyExtension = allowAny
            )
        } catch (e: Exception) {
            Log.w(TAG, "Cloud policy fetch failed", e)
            BrowserPolicy()
        }
    }

    private fun readMdmRestrictions(): BrowserPolicy {
        return try {
            val rm = context.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager
            val bundle = rm.applicationRestrictions ?: return BrowserPolicy()
            BrowserPolicy(
                lockdownEnabled = bundle.getBoolean("lockdown_enabled", false),
                pacUrl = bundle.getString("pac_url") ?: "",
                pacContent = bundle.getString("pac_content") ?: "",
                forceSystemProxy = bundle.getBoolean("force_system_proxy", true),
                allowedDomains = bundle.getString("allowed_domains")
                    ?.split(",")?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }?.toSet()
                    ?: emptySet(),
                blockedCategories = bundle.getString("blocked_categories")
                    ?.split(",")?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }?.toSet()
                    ?: setOf("adult", "gambling"),
                timeStart = bundle.getString("time_restriction_start")?.takeIf { it.isNotBlank() },
                timeEnd = bundle.getString("time_restriction_end")?.takeIf { it.isNotBlank() },
                disableDownloads = bundle.getBoolean("disable_downloads", true),
                disableUploads = bundle.getBoolean("disable_file_uploads", true),
                forceSafeSearch = bundle.getBoolean("force_safe_search", true),
                disableIncognito = bundle.getBoolean("disable_incognito", true),
                organizationId = bundle.getString("organization_id") ?: "",
                enrollmentToken = bundle.getString("mdm_enrollment_token") ?: ""
            )
        } catch (e: Exception) {
            Log.w(TAG, "MDM restrictions read failed", e)
            BrowserPolicy()
        }
    }

    fun applyRemoteLockdown(enable: Boolean, reason: String = "Remote MDM command") {
        prefs.edit().putBoolean(KEY_REMOTE_LOCKDOWN, enable).apply()
        currentPolicy = currentPolicy.copy(lockdownEnabled = enable)
        Log.w(TAG, "Remote lockdown ${if (enable) "ENABLED" else "DISABLED"}: $reason")
        context.sendBroadcast(android.content.Intent(ACTION_POLICY_CHANGED))
    }

    fun isUrlAllowed(url: String): Boolean {
        if (!currentPolicy.lockdownEnabled && currentPolicy.allowedDomains.isEmpty()) return true

        val host = try {
            java.net.URI(url).host?.lowercase() ?: return false
        } catch (_: Exception) {
            return false
        }

        if (currentPolicy.allowedDomains.any { host == it || host.endsWith(".$it") }) {
            return true
        }

        if (currentPolicy.lockdownEnabled) return false

        val blockedKeywords = listOf("porn", "xxx", "adult", "gamble", "casino")
        if (blockedKeywords.any { host.contains(it) || url.lowercase().contains(it) }) {
            return false
        }
        return true
    }

    fun isWithinAllowedTime(): Boolean {
        val start = currentPolicy.timeStart ?: return true
        val end = currentPolicy.timeEnd ?: return true
        return try {
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            val now = LocalTime.now()
            val s = LocalTime.parse(start, formatter)
            val e = LocalTime.parse(end, formatter)
            if (s.isBefore(e)) {
                !now.isBefore(s) && !now.isAfter(e)
            } else {
                !now.isBefore(s) || !now.isAfter(e)
            }
        } catch (_: Exception) {
            true
        }
    }

    /** Cloud policy listener placeholder — Wire to Worker WebSocket / polling later */
    fun startCloudPolicyListener() {
        Log.i(TAG, "Cloud policy listener: session=${SessionStore.isLoggedIn(context)}")
    }

    fun stopCloudPolicyListener() {
        // no-op for now
    }

    companion object {
        private const val TAG = "AegisPolicy"
        private const val KEY_REMOTE_LOCKDOWN = "remote_lockdown"
        private const val KEY_LAST_LOCKDOWN = "last_lockdown"
        const val ACTION_POLICY_CHANGED = "com.aegis.browser.POLICY_CHANGED"
    }
}
