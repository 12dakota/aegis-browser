package com.aegis.browser.policy

import android.content.Context
import android.content.RestrictionsManager
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aegis.browser.AegisApplication
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Central policy engine.
 * Sources (in priority order):
 * 1. MDM Managed App Configurations (App Restrictions) – highest priority for enterprise lockdown
 * 2. Firebase Firestore (per-user / per-org policies synced via login)
 * 3. Local encrypted storage (last known good + remote FCM lockdown flag)
 *
 * Supports remote lockdown via FCM command that sets lockdown_enabled = true.
 */
class PolicyManager(private val context: Context) {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "aegis_policy_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val firestore = FirebaseFirestore.getInstance()
    private var policyListener: ListenerRegistration? = null

    data class BrowserPolicy(
        val lockdownEnabled: Boolean = false,
        val pacUrl: String = "",
        val pacContent: String = "",          // Will be filled by user later
        val forceSystemProxy: Boolean = true,
        val allowedDomains: Set<String> = emptySet(),
        val blockedCategories: Set<String> = setOf("adult", "gambling"),
        val timeStart: String? = null,        // "08:00"
        val timeEnd: String? = null,          // "22:00"
        val disableDownloads: Boolean = true,
        val disableUploads: Boolean = true,
        val forceSafeSearch: Boolean = true,
        val disableIncognito: Boolean = true,
        val organizationId: String = "",
        val enrollmentToken: String = ""
    )

    @Volatile
    var currentPolicy: BrowserPolicy = BrowserPolicy()
        private set

    /**
     * Refresh from MDM RestrictionsManager + Firebase + local FCM lockdown flag.
     */
    suspend fun refreshPolicies() {
        val mdmPolicy = readMdmRestrictions()
        val firebasePolicy = readFirebasePolicy()
        val localLockdown = prefs.getBoolean(KEY_REMOTE_LOCKDOWN, false)

        // Merge with priority: remote lockdown flag OR mdm lockdown > firebase > defaults
        currentPolicy = BrowserPolicy(
            lockdownEnabled = localLockdown || mdmPolicy.lockdownEnabled || firebasePolicy.lockdownEnabled,
            pacUrl = mdmPolicy.pacUrl.ifBlank { firebasePolicy.pacUrl },
            pacContent = mdmPolicy.pacContent.ifBlank { firebasePolicy.pacContent },
            forceSystemProxy = mdmPolicy.forceSystemProxy,
            allowedDomains = mdmPolicy.allowedDomains.ifEmpty { firebasePolicy.allowedDomains },
            blockedCategories = mdmPolicy.blockedCategories.ifEmpty { firebasePolicy.blockedCategories },
            timeStart = mdmPolicy.timeStart ?: firebasePolicy.timeStart,
            timeEnd = mdmPolicy.timeEnd ?: firebasePolicy.timeEnd,
            disableDownloads = mdmPolicy.disableDownloads,
            disableUploads = mdmPolicy.disableUploads,
            forceSafeSearch = mdmPolicy.forceSafeSearch,
            disableIncognito = mdmPolicy.disableIncognito,
            organizationId = mdmPolicy.organizationId.ifBlank { firebasePolicy.organizationId },
            enrollmentToken = mdmPolicy.enrollmentToken.ifBlank { firebasePolicy.enrollmentToken }
        )

        Log.i(TAG, "Policies refreshed. Lockdown=${currentPolicy.lockdownEnabled}")
        // Persist last known for offline
        prefs.edit()
            .putBoolean(KEY_LAST_LOCKDOWN, currentPolicy.lockdownEnabled)
            .apply()
    }

    private fun readMdmRestrictions(): BrowserPolicy {
        val rm = context.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager
        val bundle = rm.applicationRestrictions ?: return BrowserPolicy()

        return BrowserPolicy(
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
    }

    private suspend fun readFirebasePolicy(): BrowserPolicy {
        val user = FirebaseAuth.getInstance().currentUser ?: return BrowserPolicy()
        return try {
            val orgId = prefs.getString(KEY_ORG_ID, "") ?: ""
            val docPath = if (orgId.isNotBlank()) {
                "organizations/$orgId/users/${user.uid}/policy"
            } else {
                "users/${user.uid}/policy"
            }
            val snap = firestore.document(docPath).get().await()
            if (!snap.exists()) return BrowserPolicy()

            BrowserPolicy(
                lockdownEnabled = snap.getBoolean("lockdownEnabled") ?: false,
                pacUrl = snap.getString("pacUrl") ?: "",
                pacContent = snap.getString("pacContent") ?: "",
                forceSystemProxy = snap.getBoolean("forceSystemProxy") ?: true,
                allowedDomains = (snap.get("allowedDomains") as? List<*>)
                    ?.mapNotNull { it as? String }?.map { it.lowercase() }?.toSet() ?: emptySet(),
                blockedCategories = (snap.get("blockedCategories") as? List<*>)
                    ?.mapNotNull { it as? String }?.map { it.lowercase() }?.toSet()
                    ?: setOf("adult", "gambling"),
                timeStart = snap.getString("timeStart"),
                timeEnd = snap.getString("timeEnd"),
                disableDownloads = snap.getBoolean("disableDownloads") ?: true,
                disableUploads = snap.getBoolean("disableUploads") ?: true,
                forceSafeSearch = snap.getBoolean("forceSafeSearch") ?: true,
                disableIncognito = snap.getBoolean("disableIncognito") ?: true,
                organizationId = orgId,
                enrollmentToken = snap.getString("enrollmentToken") ?: ""
            )
        } catch (e: Exception) {
            Log.w(TAG, "Firebase policy read failed", e)
            BrowserPolicy()
        }
    }

    /**
     * Called by FCM service when a remote lockdown command arrives from MDM / admin console.
     */
    fun applyRemoteLockdown(enable: Boolean, reason: String = "Remote MDM command") {
        prefs.edit().putBoolean(KEY_REMOTE_LOCKDOWN, enable).apply()
        currentPolicy = currentPolicy.copy(lockdownEnabled = enable)
        Log.w(TAG, "Remote lockdown ${if (enable) "ENABLED" else "DISABLED"}: $reason")
        // Optionally notify UI via LiveData / EventBus / broadcast
        context.sendBroadcast(android.content.Intent(ACTION_POLICY_CHANGED))
    }

    fun isUrlAllowed(url: String): Boolean {
        if (!currentPolicy.lockdownEnabled && currentPolicy.allowedDomains.isEmpty()) return true

        val host = try {
            java.net.URI(url).host?.lowercase() ?: return false
        } catch (e: Exception) {
            return false
        }

        // Whitelist always wins
        if (currentPolicy.allowedDomains.any { host == it || host.endsWith(".$it") }) {
            return true
        }

        if (currentPolicy.lockdownEnabled) {
            // In full lockdown only whitelist is allowed
            return false
        }

        // Category blocking would be expanded with real category lists later
        // For now simple keyword / domain block examples
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
                // overnight window
                !now.isBefore(s) || !now.isAfter(e)
            }
        } catch (e: Exception) {
            true
        }
    }

    fun startFirebaseListener() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        policyListener?.remove()
        val orgId = currentPolicy.organizationId
        val path = if (orgId.isNotBlank()) {
            "organizations/$orgId/users/${user.uid}/policy"
        } else {
            "users/${user.uid}/policy"
        }
        policyListener = firestore.document(path).addSnapshotListener { snap, error ->
            if (error != null || snap == null) return@addSnapshotListener
            AegisApplication.instance.applicationScope.launch {
                refreshPolicies()
            }
        }
    }

    fun stopFirebaseListener() {
        policyListener?.remove()
        policyListener = null
    }

    companion object {
        private const val TAG = "AegisPolicy"
        private const val KEY_REMOTE_LOCKDOWN = "remote_lockdown"
        private const val KEY_LAST_LOCKDOWN = "last_lockdown"
        private const val KEY_ORG_ID = "org_id"
        const val ACTION_POLICY_CHANGED = "com.aegis.browser.POLICY_CHANGED"
    }
}
