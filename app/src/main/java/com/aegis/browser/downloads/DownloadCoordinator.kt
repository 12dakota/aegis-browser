package com.aegis.browser.downloads

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import com.aegis.browser.AegisApplication
import com.aegis.browser.BuildConfig

object DownloadCoordinator {

    const val ACTION_PROGRESS = "com.aegis.browser.DOWNLOAD_PROGRESS"
    const val EXTRA_ID = "id"
    const val EXTRA_NAME = "name"
    const val EXTRA_HOST = "host"
    const val EXTRA_STATE = "state"
    const val EXTRA_PROGRESS = "progress"
    const val EXTRA_MESSAGE = "message"
    const val EXTRA_URI = "uri"

    const val STATE_CONNECTING = "connecting"
    const val STATE_TRANSFERRING = "transferring"
    const val STATE_FINISHED = "finished"
    const val STATE_FAILED = "failed"
    const val STATE_BLOCKED = "blocked"

    fun looksLikeFile(url: String): Boolean {
        val path = Uri.parse(url).path?.lowercase().orEmpty()
        val exts = listOf(
            ".zip", ".apk", ".xapk", ".apks", ".pdf", ".mp4", ".mp3", ".m4a",
            ".7z", ".rar", ".exe", ".dmg", ".iso", ".tar", ".gz", ".tgz",
            ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".csv",
            ".png", ".jpg", ".jpeg", ".webp", ".gif", ".svg",
            ".bin", ".img", ".deb", ".rpm"
        )
        return exts.any { path.endsWith(it) }
    }

    fun allowed(ctx: Context): Boolean {
        if (!UserSettings.downloadsEnabled(ctx)) return false
        if (BuildConfig.ADULT_BUILD) return true
        return !AegisApplication.instance.policyManager.currentPolicy.disableDownloads
    }

    fun start(
        ctx: Context,
        url: String,
        userAgent: String? = null,
        contentDisposition: String? = null,
        mime: String? = null
    ) {
        if (!allowed(ctx)) {
            broadcast(ctx, 0L, guessName(url, contentDisposition), host(url), STATE_BLOCKED, 0, "Downloads are disabled by policy or settings")
            return
        }
        val i = Intent(ctx, DownloadService::class.java).apply {
            putExtra(DownloadService.EXTRA_URL, url)
            putExtra(DownloadService.EXTRA_UA, userAgent)
            putExtra(DownloadService.EXTRA_CD, contentDisposition)
            putExtra(DownloadService.EXTRA_MIME, mime)
            putExtra(DownloadService.EXTRA_BG, UserSettings.backgroundDownloads(ctx))
        }
        if (Build.VERSION.SDK_INT >= 26) {
            ContextCompat.startForegroundService(ctx, i)
        } else {
            ctx.startService(i)
        }
    }

    fun guessName(url: String, contentDisposition: String?): String {
        contentDisposition?.let { cd ->
            val m = Regex("filename\\*?=UTF-8''([^;]+)|filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(cd)
            val n = m?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }
            if (!n.isNullOrBlank()) return Uri.decode(n)
        }
        return Uri.parse(url).lastPathSegment?.substringBefore("?")?.ifBlank { null } ?: "download.bin"
    }

    fun host(url: String): String = Uri.parse(url).host ?: "host"

    fun broadcast(ctx: Context, id: Long, name: String, host: String, state: String, progress: Int, message: String, uri: String? = null) {
        DownloadStore.upsert(id, name, host, state, progress, message, uri)
        ctx.sendBroadcast(
            Intent(ACTION_PROGRESS)
                .setPackage(ctx.packageName)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_NAME, name)
                .putExtra(EXTRA_HOST, host)
                .putExtra(EXTRA_STATE, state)
                .putExtra(EXTRA_PROGRESS, progress)
                .putExtra(EXTRA_MESSAGE, message)
                .putExtra(EXTRA_URI, uri)
        )
    }
}
