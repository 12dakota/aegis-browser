package com.aegis.browser.auth

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * All authentication goes through the Cloudflare Worker.
 * Secrets (Firebase API keys, service accounts) stay in the Worker — never in the APK.
 *
 * Default worker host can be overridden via BuildConfig / MDM restriction later.
 */
object CloudflareAuth {

    private const val TAG = "AegisCloudAuth"

    /** Production worker host — point this at your deployed Worker */
    var workerBaseUrl: String = "https://aegis-auth.dakdra03.workers.dev"

    data class LoginResult(
        val success: Boolean,
        val sessionToken: String? = null,
        val userId: String? = null,
        val email: String? = null,
        val expiresIn: Long = 3600,
        val orgId: String = "",
        val error: String? = null
    )

    suspend fun login(email: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("$workerBaseUrl/auth/login")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                connectTimeout = 20000
                readTimeout = 20000
            }

            val body = JSONObject()
                .put("email", email)
                .put("password", password)
                .toString()

            OutputStreamWriter(conn.outputStream).use { it.write(body) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let { BufferedReader(InputStreamReader(it)).readText() } ?: ""

            Log.i(TAG, "login HTTP $code")

            if (code !in 200..299) {
                val err = try {
                    JSONObject(text).optString("error", text.ifBlank { "Login failed ($code)" })
                } catch (_: Exception) {
                    text.ifBlank { "Login failed ($code)" }
                }
                return@withContext LoginResult(success = false, error = err)
            }

            val json = JSONObject(text)
            // Support both new sessionToken shape and legacy idToken from Worker
            val token = json.optString("sessionToken")
                .ifBlank { json.optString("idToken") }
                .ifBlank { json.optString("token") }

            if (token.isBlank()) {
                return@withContext LoginResult(success = false, error = "No session token in response")
            }

            val userId = json.optString("userId")
                .ifBlank { json.optString("localId") }
                .ifBlank { email }
            val expiresIn = json.optString("expiresIn", "3600").toLongOrNull() ?: 3600L
            val orgId = json.optString("orgId", "")

            LoginResult(
                success = true,
                sessionToken = token,
                userId = userId,
                email = email,
                expiresIn = expiresIn,
                orgId = orgId
            )
        } catch (e: Exception) {
            Log.e(TAG, "login error", e)
            LoginResult(success = false, error = e.message ?: "Network error")
        }
    }

    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$workerBaseUrl/health")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }
            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        }
    }
}
