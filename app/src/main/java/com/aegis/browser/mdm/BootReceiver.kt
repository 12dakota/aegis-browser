package com.aegis.browser.mdm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.aegis.browser.AegisApplication
import kotlinx.coroutines.launch

/**
 * Re-applies policies and PAC after device reboot so lockdown state survives restarts.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            Log.i("AegisBoot", "Boot completed – refreshing policies")
            val app = context.applicationContext as? AegisApplication ?: return
            app.applicationScope.launch {
                app.policyManager.refreshPolicies()
                app.pacProxyManager.applyDefaultPac()
            }
        }
    }
}
