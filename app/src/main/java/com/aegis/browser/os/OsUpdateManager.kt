package com.aegis.browser.os

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.aegis.browser.BuildConfig
import com.aegis.browser.auth.CloudflareAuth
import com.aegis.browser.mdm.DeviceOwnerHelper
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Chrome OS–style update channel for Provonlong OS.
 * Polls Cloudflare Worker /os/latest and installs newer APKs.
 */
object OsUpdateManager {

    private const val TAG = "OsUpdate"
    private const val PREFS = "provonlong_os_update"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_LAST_TAG = "last_tag"
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    data class Latest(
        val versionCode: Int,
        val versionName: String,
        val tag: String,
        val apkUrl: String,
        val downloadPath: String
    )

    fun maybeCheck(context: Context, force: Boolean = false) {
        if (!BuildConfig.OS_BUILD) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        if (!force && System.currentTimeMillis() - last < 6 * 60 * 60 * 1000L) return
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        io.execute {
            try {
                val latest = fetchLatest() ?: return@execute
                prefs.edit().putString(KEY_LAST_TAG, latest.tag).apply()
                if (latest.versionCode <= BuildConfig.VERSION_CODE) {
                    Log.i(TAG, "Up to date local=${BuildConfig.VERSION_CODE} remote=${latest.versionCode}")
                    return@execute
                }
                Log.i(TAG, "Update available ${latest.tag} code=${latest.versionCode}")
                main.post {
                    Toast.makeText(
                        context,
                        "Updating Provonlong OS to ${latest.versionName}…",
                        Toast.LENGTH_LONG
                    ).show()
                }
                val file = downloadApk(context, latest)
                if (file != null) {
                    main.post { installApk(context, file) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "check failed", e)
            }
        }
    }

    fun fetchLatest(): Latest? {
        val base = CloudflareAuth.workerBaseUrl.trimEnd('/')
        val conn = (URL("$base/os/latest").openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 20000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        conn.inputStream.use { ins ->
            val text = ins.bufferedReader().readText()
            val o = JSONObject(text)
            return Latest(
                versionCode = o.optInt("versionCode", 0),
                versionName = o.optString("versionName", ""),
                tag = o.optString("tag", ""),
                apkUrl = o.optString("apkUrl", ""),
                downloadPath = o.optString("downloadPath", "/os/download")
            )
        }
    }

    private fun downloadApk(context: Context, latest: Latest): File? {
        val base = CloudflareAuth.workerBaseUrl.trimEnd('/')
        val url = "$base${latest.downloadPath}"
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "provonlong-os-update.apk")
        if (out.exists()) out.delete()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30000
            readTimeout = 300000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        conn.inputStream.use { ins ->
            out.outputStream().use { outs -> ins.copyTo(outs) }
        }
        return if (out.length() > 1_000_000) out else null
    }

    private fun installApk(context: Context, file: File) {
        try {
            if (DeviceOwnerHelper.isDeviceOwner(context)) {
                silentInstall(context, file)
                return
            }
            if (Build.VERSION.SDK_INT >= 26) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(i)
                    Toast.makeText(context, "Allow installs, then updates will apply", Toast.LENGTH_LONG).show()
                    return
                }
            }
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "install failed", e)
            Toast.makeText(context, "Update install failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /** Device Owner can commit PackageInstaller sessions without a user prompt. */
    private fun silentInstall(context: Context, file: File) {
        try {
            val pm = context.packageManager
            val installer = pm.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = installer.createSession(params)
            val session = installer.openSession(sessionId)
            file.inputStream().use { ins ->
                session.openWrite("os.apk", 0, file.length()).use { outs ->
                    ins.copyTo(outs)
                    session.fsync(outs)
                }
            }
            val callback = Intent(context, OsUpdateReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context,
                sessionId,
                callback,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pi.intentSender)
            session.close()
            Toast.makeText(context, "OS update installing…", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "silentInstall", e)
            // Fall back to interactive
            try {
                val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (_: Exception) {
            }
        }
    }

    fun openEnrollmentPage(activity: Activity) {
        val url = CloudflareAuth.workerBaseUrl.trimEnd('/') + "/os/enroll"
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun registerDevice(context: Context) {
        io.execute {
            try {
                val base = CloudflareAuth.workerBaseUrl.trimEnd('/')
                val body = JSONObject()
                    .put("deviceId", android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID))
                    .put("model", Build.MODEL)
                    .put("androidVersion", Build.VERSION.RELEASE)
                    .put("packageName", context.packageName)
                    .put("versionCode", BuildConfig.VERSION_CODE)
                    .toString()
                val conn = (URL("$base/os/enroll/register").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 15000
                    readTimeout = 15000
                }
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                Log.i(TAG, "enroll register HTTP $code")
            } catch (e: Exception) {
                Log.w(TAG, "enroll register", e)
            }
        }
    }
}
