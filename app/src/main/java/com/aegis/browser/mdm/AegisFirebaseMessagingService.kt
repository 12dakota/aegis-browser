package com.aegis.browser.mdm

import android.util.Log

/**
 * Placeholder for remote push commands.
 * Firebase Messaging is intentionally not used in the cloud-auth build.
 * Future: Worker-triggered WebPush / custom channel with session token validation.
 */
object AegisRemoteCommands {
    private const val TAG = "AegisRemote"

    fun logStub() {
        Log.i(TAG, "Remote command channel: use Cloudflare Worker + session auth")
    }
}
