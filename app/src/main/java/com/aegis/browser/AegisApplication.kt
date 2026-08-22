package com.aegis.browser

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.aegis.browser.BuildConfig
import com.aegis.browser.engine.EngineSelector
import com.aegis.browser.mdm.DeviceOwnerHelper
import com.aegis.browser.policy.PolicyManager
import com.aegis.browser.proxy.PacProxyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Crash-safe entry point.
 * Auth is Cloudflare Worker based — Firebase is NOT required to start the app.
 */
class AegisApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var policyManager: PolicyManager
        private set

    lateinit var pacProxyManager: PacProxyManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        try {
            createNotificationChannel()
        } catch (e: Exception) {
            Log.w(TAG, "Notification channel failed", e)
        }

        try {
            EngineSelector.init(this)
        } catch (e: Exception) {
            Log.w(TAG, "EngineSelector init failed", e)
        }

        // Policy + PAC must never crash the process
        try {
            policyManager = PolicyManager(this)
        } catch (e: Exception) {
            Log.e(TAG, "PolicyManager init failed – using empty shell", e)
            policyManager = PolicyManager(this, forcePlainPrefs = true)
        }

        try {
            pacProxyManager = PacProxyManager(this)
        } catch (e: Exception) {
            Log.e(TAG, "PacProxyManager init failed", e)
            // rethrow only if we literally cannot continue — construct minimal
            pacProxyManager = PacProxyManager(this)
        }

        applicationScope.launch {
            try {
                if (BuildConfig.DEFAULT_ALLOW_ANY_EXTENSION) {
                    com.aegis.browser.extensions.ExtensionManager.setAllowAny(this@AegisApplication, true)
                }
                // Family: cloud policy + PAC. Adult: skip forced Gateway PAC so browsing works offline/normal net.
                if (BuildConfig.REQUIRE_LOGIN) {
                    policyManager.refreshPolicies()
                    pacProxyManager.applyDefaultPac()
                    val pacUrl = policyManager.currentPolicy.pacUrl
                        .ifBlank { PacProxyManager.DEFAULT_PAC_URL }
                    val applied = DeviceOwnerHelper.applyGlobalPac(this@AegisApplication, pacUrl)
                    Log.i(TAG, "Device Owner: ${DeviceOwnerHelper.getStatusSummary(this@AegisApplication)}")
                    Log.i(TAG, "Global PAC applied: $applied")
                } else {
                    Log.i(TAG, "Adult build: open mode, no forced PAC / login policy")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background policy/PAC refresh failed", e)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "AegisApp"
        const val CHANNEL_ID = "aegis_security"
        lateinit var instance: AegisApplication
            private set
    }
}
