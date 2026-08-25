package com.aegis.browser.mdm

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.aegis.browser.proxy.PacProxyManager

/**
 * Device Admin / Device Owner helpers for Provonlong OS and managed builds.
 *
 * Device Owner is set externally (adb / MDM):
 *   adb shell dpm set-device-owner com.aegis.browser.os/.mdm.AegisDeviceAdminReceiver
 */
object DeviceOwnerHelper {

    private const val TAG = "ProvonlongDO"

    fun getDevicePolicyManager(context: Context): DevicePolicyManager? =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager

    fun getAdminComponent(context: Context): ComponentName =
        ComponentName(context, AegisDeviceAdminReceiver::class.java)

    fun isAdminActive(context: Context): Boolean {
        val dpm = getDevicePolicyManager(context) ?: return false
        return dpm.isAdminActive(getAdminComponent(context))
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

    /** Opens the system prompt to activate Device Admin (not full Device Owner). */
    fun requestDeviceAdmin(context: Context): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getAdminComponent(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Provonlong OS needs device admin to lock the home screen, apply network policy, and manage this device like a kiosk."
            )
        }
    }

    fun lockNow(context: Context): Boolean {
        if (!isAdminActive(context)) return false
        return try {
            getDevicePolicyManager(context)?.lockNow()
            true
        } catch (e: Exception) {
            Log.w(TAG, "lockNow failed", e)
            false
        }
    }

    /** Allow this package to pin / lock-task (kiosk) when Device Owner. */
    fun enableLockTask(context: Context): Boolean {
        if (!isDeviceOwner(context)) return false
        val dpm = getDevicePolicyManager(context) ?: return false
        val admin = getAdminComponent(context)
        return try {
            dpm.setLockTaskPackages(admin, arrayOf(context.packageName))
            if (Build.VERSION.SDK_INT >= 28) {
                dpm.setLockTaskFeatures(
                    admin,
                    DevicePolicyManager.LOCK_TASK_FEATURE_NONE
                )
            }
            Log.i(TAG, "Lock task packages set for ${context.packageName}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "enableLockTask failed", e)
            false
        }
    }

    fun setKeyguardDisabled(context: Context, disabled: Boolean): Boolean {
        if (!isDeviceOwner(context)) return false
        return try {
            getDevicePolicyManager(context)?.setKeyguardDisabled(getAdminComponent(context), disabled) == true
        } catch (e: Exception) {
            Log.w(TAG, "setKeyguardDisabled", e)
            false
        }
    }

    fun setStatusBarDisabled(context: Context, disabled: Boolean): Boolean {
        if (!isDeviceOwner(context) || Build.VERSION.SDK_INT < 28) return false
        return try {
            getDevicePolicyManager(context)?.setStatusBarDisabled(getAdminComponent(context), disabled) == true
        } catch (e: Exception) {
            Log.w(TAG, "setStatusBarDisabled", e)
            false
        }
    }

    fun applyGlobalPac(context: Context, pacUrl: String = PacProxyManager.DEFAULT_PAC_URL): Boolean {
        if (!isManaged(context)) {
            Log.i(TAG, "Not Device/Profile Owner – skipping global PAC")
            return false
        }
        val dpm = getDevicePolicyManager(context) ?: return false
        val admin = getAdminComponent(context)
        return try {
            dpm.setRecommendedGlobalProxy(
                admin,
                android.net.ProxyInfo.buildPacProxy(Uri.parse(pacUrl))
            )
            Log.i(TAG, "Global PAC applied: $pacUrl")
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException setting global PAC", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set global PAC", e)
            false
        }
    }

    fun clearGlobalProxy(context: Context): Boolean {
        if (!isManaged(context)) return false
        val dpm = getDevicePolicyManager(context) ?: return false
        val admin = getAdminComponent(context)
        return try {
            dpm.setRecommendedGlobalProxy(admin, null)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear global proxy", e)
            false
        }
    }

    fun getStatusSummary(context: Context): String = when {
        isDeviceOwner(context) -> "Device Owner — full lockdown capable"
        isProfileOwner(context) -> "Profile Owner — work profile controls active"
        isAdminActive(context) -> "Device Admin active — lock/wipe available"
        else -> "Not managed — activate Device Admin or set Device Owner via adb/MDM"
    }

    fun ownerSetupHint(packageName: String): String =
        "adb shell dpm set-device-owner $packageName/.mdm.AegisDeviceAdminReceiver"
}
