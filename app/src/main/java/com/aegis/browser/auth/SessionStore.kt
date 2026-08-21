package com.aegis.browser.auth

import android.content.Context
import android.content.SharedPreferences

/**
 * Local session for cloud (Cloudflare Worker) authentication.
 * No Firebase client SDK required for the login gate.
 */
object SessionStore {

    private const val PREFS = "aegis_session"
    private const val KEY_TOKEN = "session_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_EMAIL = "email"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_ORG_ID = "org_id"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isLoggedIn(context: Context): Boolean {
        val p = prefs(context)
        val token = p.getString(KEY_TOKEN, null) ?: return false
        if (token.isBlank()) return false
        val expires = p.getLong(KEY_EXPIRES_AT, 0L)
        // 0 = no expiry tracked; treat token as valid until logout
        if (expires > 0 && System.currentTimeMillis() > expires) {
            clear(context)
            return false
        }
        return true
    }

    fun saveSession(
        context: Context,
        sessionToken: String,
        userId: String,
        email: String,
        expiresInSeconds: Long = 3600,
        orgId: String = ""
    ) {
        val expiresAt = if (expiresInSeconds > 0) {
            System.currentTimeMillis() + expiresInSeconds * 1000
        } else 0L
        prefs(context).edit()
            .putString(KEY_TOKEN, sessionToken)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_EMAIL, email)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .putString(KEY_ORG_ID, orgId)
            .apply()
    }

    fun getToken(context: Context): String? =
        prefs(context).getString(KEY_TOKEN, null)

    fun getUserId(context: Context): String? =
        prefs(context).getString(KEY_USER_ID, null)

    fun getEmail(context: Context): String? =
        prefs(context).getString(KEY_EMAIL, null)

    fun getOrgId(context: Context): String? =
        prefs(context).getString(KEY_ORG_ID, null)

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
