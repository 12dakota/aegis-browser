package com.aegis.browser

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.aegis.browser.engine.EngineSelector
import com.aegis.browser.mdm.DeviceOwnerHelper
import com.aegis.browser.policy.PolicyManager
import com.aegis.browser.proxy.PacProxyManager
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Aegis Browser Application entry point.
 * Dual-engine (Chromium + Gecko), mandatory Firebase auth, Cloudflare Gateway PAC,
 * MDM / Device Owner lockdown support.
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

        FirebaseApp.initializeApp(this)
        createNotificationChannel()

        EngineSelector.init(this)
        policyManager = PolicyManager(this)
        pacProxyManager = PacProxyManager(this)

        applicationScope.launch {
            policyManager.refreshPolicies()
            pacProxyManager.applyDefaultPac()

            // Strongest path: if we are Device/Profile Owner, push the PAC system-wide
            val pacUrl = policyManager.currentPolicy.pacUrl
                .ifBlank { PacProxyManager.DEFAULT_PAC_URL }
            val applied = DeviceOwnerHelper.applyGlobalPac(this@AegisApplication, pacUrl)
            Log.i("AegisApp", "Device Owner status: ${DeviceOwnerHelper.getStatusSummary(this@AegisApplication)}")
            Log.i("AegisApp", "Global PAC applied: $applied")
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
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "aegis_security"
        lateinit var instance: AegisApplication
            private set
    }
}
