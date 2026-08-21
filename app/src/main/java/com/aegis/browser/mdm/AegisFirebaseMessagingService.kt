package com.aegis.browser.mdm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.aegis.browser.AegisApplication
import com.aegis.browser.R
import com.aegis.browser.ui.BrowserActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch

/**
 * Receives remote commands from the MDM / enterprise management console (or Cloudflare Worker)
 * via Firebase Cloud Messaging.
 *
 * Expected data payload for lockdown:
 * {
 *   "command": "lockdown",
 *   "enable": "true",
 *   "reason": "Security incident – temporary restriction",
 *   "token": "<mdm_enrollment_token or short-lived Cloudflare signed token>"
 * }
 *
 * Token is validated against the policy enrollmentToken (or later against Cloudflare-signed JWT).
 * Secrets never live in the APK – only short-lived tokens issued by the Cloudflare Worker.
 */
class AegisFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "FCM token refreshed")
        // Production: send token to your Cloudflare Worker / backend so the parent/MDM
        // console can target this specific child device.
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        if (data.isEmpty()) return

        val command = data["command"]?.lowercase() ?: return
        val token = data["token"] ?: ""
        val policy = AegisApplication.instance.policyManager.currentPolicy

        // Basic token gate – replace with Cloudflare JWT verification in production
        if (policy.enrollmentToken.isNotBlank() && token != policy.enrollmentToken) {
            Log.w(TAG, "Rejected command – invalid token")
            return
        }

        when (command) {
            "lockdown" -> {
                val enable = data["enable"]?.toBooleanStrictOrNull() ?: true
                val reason = data["reason"] ?: "Remote MDM / parent command"
                AegisApplication.instance.policyManager.applyRemoteLockdown(enable, reason)
                showLockdownNotification(enable, reason)
            }
            "refresh_policy" -> {
                AegisApplication.instance.applicationScope.launch {
                    AegisApplication.instance.policyManager.refreshPolicies()
                    AegisApplication.instance.pacProxyManager.applyDefaultPac()
                }
            }
            "install_extension" -> {
                // Parent remotely pushes an approved Aegis Module / extension
                val extId = data["extension_id"] ?: return
                val extUrl = data["extension_url"] ?: return
                Log.i(TAG, "Remote install_extension requested: $extId from $extUrl")
                // ExtensionManager will handle download + enable under parental control
            }
            "clear_data" -> {
                Log.i(TAG, "Remote clear_data command received")
            }
            else -> Log.w(TAG, "Unknown command: $command")
        }
    }

    private fun showLockdownNotification(enabled: Boolean, reason: String) {
        val intent = Intent(this, BrowserActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, AegisApplication.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(
                if (enabled) getString(R.string.lockdown_notification_title)
                else "Aegis Lockdown Released"
            )
            .setContentText(if (enabled) reason else "Restrictions have been lifted by parent/admin.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(LOCKDOWN_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "AegisFCM"
        private const val LOCKDOWN_NOTIFICATION_ID = 1001
    }
}
