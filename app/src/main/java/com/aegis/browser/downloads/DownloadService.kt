package com.aegis.browser.downloads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.aegis.browser.ui.BrowserActivity
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class DownloadService : Service() {

    private val pool = Executors.newFixedThreadPool(2)
    private val nextId = AtomicLong(1)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL).orEmpty()
        if (url.isBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val id = nextId.getAndIncrement()
        val name = DownloadCoordinator.guessName(url, intent?.getStringExtra(EXTRA_CD))
        val host = DownloadCoordinator.host(url)
        val ua = intent?.getStringExtra(EXTRA_UA)
        val bg = intent?.getBooleanExtra(EXTRA_BG, true) ?: true

        startForeground(NOTIF_BASE + id.toInt(), note(name, "Connecting to host", 0, indeterminate = true))

        pool.execute {
            runDownload(id, url, name, host, ua)
            if (!bg) stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf(startId)
        }
        return START_STICKY
    }

    private fun runDownload(id: Long, url: String, name: String, host: String, ua: String?) {
        val ctx = applicationContext
        fun emit(state: String, pct: Int, msg: String) {
            DownloadCoordinator.broadcast(ctx, id, name, host, state, pct, msg)
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(
                NOTIF_BASE + id.toInt(),
                note(name, msg, pct, indeterminate = state == DownloadCoordinator.STATE_CONNECTING)
            )
        }
        emit(DownloadCoordinator.STATE_CONNECTING, 0, "Connecting to host")
        var conn: HttpURLConnection? = null
        var pendingUri: Uri? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 20_000
                readTimeout = 60_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", ua ?: "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
                connect()
            }
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            val total = conn.contentLengthLong
            val mime = conn.contentType?.substringBefore(";")?.trim().orEmpty().ifBlank {
                val ext = name.substringAfterLast('.', "")
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
            }
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            pendingUri = contentResolver.insert(collection, values)
                ?: throw IllegalStateException("Could not create Downloads entry")
            emit(DownloadCoordinator.STATE_TRANSFERRING, 0, "Transferring the file to your device")
            conn.inputStream.use { input ->
                contentResolver.openOutputStream(pendingUri!!)!!.use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    var lastPct = -1
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        done += read
                        val pct = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 99) else 0
                        if (pct != lastPct) {
                            lastPct = pct
                            emit(
                                DownloadCoordinator.STATE_TRANSFERRING,
                                pct,
                                "Transferring the file to your device"
                            )
                        }
                    }
                    output.flush()
                }
            }
            val doneVals = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            contentResolver.update(pendingUri!!, doneVals, null, null)
            val uriStr = pendingUri.toString()
            DownloadCoordinator.broadcast(
                ctx, id, name, host,
                DownloadCoordinator.STATE_FINISHED, 100,
                "Saved to Downloads",
                uriStr
            )
            getSystemService(NotificationManager::class.java).notify(
                NOTIF_BASE + id.toInt(),
                note(name, "Saved to Downloads — tap to open", 100, false, uriStr)
            )
            Log.i(TAG, "Saved to MediaStore $uriStr")
        } catch (e: Exception) {
            Log.w(TAG, "download failed", e)
            pendingUri?.let { runCatching { contentResolver.delete(it, null, null) } }
            emit(DownloadCoordinator.STATE_FAILED, 0, e.message ?: "Download failed")
        } finally {
            conn?.disconnect()
        }
    }

    private fun uniqueFile(dir: File, name: String): File {
        val safe = name.replace(Regex("[\\\\/]+"), "_")
        var f = File(dir, safe)
        var i = 1
        while (f.exists()) {
            val dot = safe.lastIndexOf('.')
            val base = if (dot > 0) safe.substring(0, dot) else safe
            val ext = if (dot > 0) safe.substring(dot) else ""
            f = File(dir, "$base ($i)$ext")
            i++
        }
        return f
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun note(title: String, text: String, pct: Int, indeterminate: Boolean, fileUri: String? = null): android.app.Notification {
        val open = if (!fileUri.isNullOrBlank()) {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(fileUri), "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(this, BrowserActivity::class.java)
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOnlyAlertOnce(true)
            .setOngoing(pct < 100)
            .setProgress(100, pct, indeterminate)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, title.hashCode(), open,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_UA = "ua"
        const val EXTRA_CD = "cd"
        const val EXTRA_MIME = "mime"
        const val EXTRA_BG = "bg"
        private const val CHANNEL = "provonlong_downloads"
        private const val NOTIF_BASE = 7100
        private const val TAG = "ProvonlongDl"
    }
}
