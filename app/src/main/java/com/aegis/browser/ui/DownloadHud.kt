package com.aegis.browser.ui

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.aegis.browser.downloads.DownloadCoordinator

object DownloadHud {
    fun show(ctx: Context, host: String, name: String, state: String, progress: Int, message: String) {
        val title = when (state) {
            DownloadCoordinator.STATE_CONNECTING -> "Connecting to host"
            DownloadCoordinator.STATE_TRANSFERRING -> "Transferring the file to your device"
            DownloadCoordinator.STATE_FINISHED -> "Download finished"
            DownloadCoordinator.STATE_BLOCKED -> "Download blocked"
            else -> "Download"
        }
        val body = buildString {
            append(message)
            append("\n\n")
            append(name)
            append("\n")
            append(host)
            if (state == DownloadCoordinator.STATE_TRANSFERRING) append("\n$progress%")
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setMessage(body)
            .setPositiveButton(if (state == DownloadCoordinator.STATE_FINISHED) "OK" else "Hide") { d, _ -> d.dismiss() }
            .show()
    }
}
