package com.aegis.browser.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.aegis.browser.downloads.DownloadCoordinator
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object DownloadHud {
    fun show(
        ctx: Context,
        host: String,
        name: String,
        state: String,
        progress: Int,
        message: String,
        fileUri: String? = null
    ) {
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
            if (state == DownloadCoordinator.STATE_FINISHED) {
                append("\n\nSaved to your device Downloads folder.")
            }
        }
        val b = MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setMessage(body)
            .setPositiveButton(
                if (state == DownloadCoordinator.STATE_FINISHED && !fileUri.isNullOrBlank()) "Open"
                else if (state == DownloadCoordinator.STATE_FINISHED) "OK"
                else "Hide"
            ) { d, _ ->
                if (state == DownloadCoordinator.STATE_FINISHED && !fileUri.isNullOrBlank()) {
                    try {
                        ctx.startActivity(
                            Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(fileUri), "*/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(ctx, "Saved to Downloads", Toast.LENGTH_SHORT).show()
                    }
                }
                d.dismiss()
            }
        if (state == DownloadCoordinator.STATE_FINISHED && !fileUri.isNullOrBlank()) {
            b.setNegativeButton("OK") { d, _ -> d.dismiss() }
        }
        b.show()
    }
}
