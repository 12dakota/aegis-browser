package com.aegis.browser.mdm

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import com.aegis.browser.proxy.PacProxyManager

/**
 * Helpers for when Aegis Browser is Device Owner or Profile Owner (set by MDM).
 *
 * The strongest proxy enforcement is applying the Cloudflare Gateway PAC
 * as the system-wide proxy via DevicePolicyManager.
 */
object DeviceOwnerHelper {

    private const val TAG = "AegisDeviceOwner"

    fun getDevicePolicyManager(context: Context): DevicePolicyManager? {
        return context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
    }

    fun getAdminComponent(context: Context): ComponentName {
        return ComponentName(context, AegisDeviceAdminReceiver::class.java)
    }

    fun isDeviceOwner(context: Context): Boolean {
        val dpm = getDevicePolicyManager(context) ?: return false
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    fun isProfileOwner(context: Context): Boolean {
        val dpm = getDevicePolicyManager(context) ?: return false
        return dpm.isProfileOwnerApp(context.packageName)
    }

    fun isManaged(context: Context): Boolean = isDeviceOwner(context) || isProfileOwner(context)

    /**
     * Apply the Cloudflare Gateway PAC (or policy-provided PAC URL) as the
     * global proxy when we have Device/Profile Owner privileges.
     *
     * Returns true if the system proxy was successfully set.
     */
    fun applyGlobalPac(context: Context, pacUrl: String = PacProxyManager.DEFAULT_PAC_URL): Boolean {
        if (!isManaged(context)) {
            Log.i(TAG, "Not Device/Profile Owner – skipping global PAC")
            return false
        }

        val dpm = getDevicePolicyManager(context) ?: return false
        val admin = getAdminComponent(context)

        return try {
            // setGlobalProxy is available when the app is Device Owner.
            // PAC URL form is supported on modern Android.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Host/port empty + exclusionList empty → use PAC
                // Some versions accept the PAC URL via the proxy host field in a specific way;
                // the clean modern approach is often through Wifi or a dedicated API.
                // For broadest compatibility we set the PAC via the recommended path.
                dpm.setRecommendedGlobalProxy(
                    admin,
                    android.net.ProxyInfo.buildPacProxy(pacUrl)
                )
                Log.i(TAG, "Global PAC applied via DevicePolicyManager: $pacUrl")
                true
            } else {
                false
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException setting global PAC – check Device Owner status", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set global PAC", e)
            false
        }
    }

    /**
     * Clear any global proxy we previously set.
     */
    fun clearGlobalProxy(context: Context): Boolean {
        if (!isManaged(context)) return false
        val dpm = getDevicePolicyManager(context) ?: return false
        val admin = getAdminComponent(context)
        return try {
            dpm.setRecommendedGlobalProxy(admin, null)
            Log.i(TAG, "Global proxy cleared")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear global proxy", e)
            false
        }
    }

    fun getStatusSummary(context: Context): String {
        return when {
            isDeviceOwner(context) -> "Device Owner – full lockdown capable"
            isProfileOwner(context) -> "Profile Owner – work profile controls active"
            else -> "Standard app – use MDM App Config + FCM for policy"
        }
    }
}
