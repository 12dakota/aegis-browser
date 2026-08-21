package com.aegis.browser.mdm

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Optional Device Admin component.
 * When the MDM sets this app as Device Owner or Profile Owner, it gains the ability
 * to call DevicePolicyManager APIs (force lock, set global proxy, wipe, etc.).
 */
class AegisDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin enabled – deeper lockdown capabilities available")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "Device Admin disabled")
    }

    companion object {
        private const val TAG = "AegisDeviceAdmin"
    }
}
