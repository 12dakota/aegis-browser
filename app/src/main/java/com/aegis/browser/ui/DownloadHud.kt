package com.aegis.browser.ui

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.aegis.browser.downloads.DownloadCoordinator

/** Live download sheet: spinner while connecting, bar while transferring, done when finished. */
object DownloadHud {

    private var dialog: Dialog? = null
    private var titleView: TextView? = null
    private var detailView: TextView? = null
    private var percentView: TextView? = null
    private var spinner: ProgressBar? = null
    private var bar: ProgressBar? = null
    private var openBtn: Button? = null
    private var lastUri: String? = null

    fun show(
        ctx: Context,
        host: String,
        name: String,
        state: String,
        progress: Int,
        message: String,
        fileUri: String? = null
    ) {
        if (ctx is Activity && ctx.isFinishing) return
        ensure(ctx)
        bind(host, name, state, progress, message, fileUri)
        if (dialog?.isShowing != true) {
            runCatching { dialog?.show() }
        }
    }

    private fun ensure(ctx: Context) {
        if (dialog != null && titleView != null) return
        val surface = MaterialPalette.surface(ctx)
        val on = MaterialPalette.onSurface(ctx)
        val outline = MaterialPalette.outline(ctx)
        val blue = Color.parseColor("#1A73E8")

        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surface)
            setPadding(ChromeUi.dp(ctx, 24), ChromeUi.dp(ctx, 20), ChromeUi.dp(ctx, 24), ChromeUi.dp(ctx, 16))
        }
        titleView = TextView(ctx).apply {
            textSize = 18f
            setTextColor(on)
        }
        detailView = TextView(ctx).apply {
            textSize = 14f
            setTextColor(outline)
            setPadding(0, ChromeUi.dp(ctx, 8), 0, ChromeUi.dp(ctx, 12))
        }
        spinner = ProgressBar(ctx).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(blue)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = Gravity.CENTER_HORIZONTAL }
        }
        percentView = TextView(ctx).apply {
            textSize = 13f
            setTextColor(blue)
            gravity = Gravity.END
        }
        bar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(blue)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ChromeUi.dp(ctx, 8)
            )
        }
        val actions = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, ChromeUi.dp(ctx, 16), 0, 0)
        }
        openBtn = Button(ctx).apply {
            text = "Open"
            visibility = View.GONE
            setOnClickListener {
                val uri = lastUri
                if (!uri.isNullOrBlank()) {
                    try {
                        ctx.startActivity(
                            Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(uri), "*/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(ctx, "Saved to Downloads", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        val hide = Button(ctx).apply {
            text = "Hide"
            setOnClickListener { dialog?.dismiss() }
        }
        actions.addView(hide)
        actions.addView(openBtn)
        col.addView(titleView)
        col.addView(detailView)
        col.addView(spinner)
        col.addView(percentView)
        col.addView(bar)
        col.addView(actions)

        dialog = Dialog(ctx).apply {
            setContentView(col)
            setCanceledOnTouchOutside(true)
        }
    }

    private fun bind(
        host: String,
        name: String,
        state: String,
        progress: Int,
        message: String,
        fileUri: String?
    ) {
        if (!fileUri.isNullOrBlank()) lastUri = fileUri
        val title = when (state) {
            DownloadCoordinator.STATE_CONNECTING -> "Connecting to host"
            DownloadCoordinator.STATE_TRANSFERRING -> "Transferring the file to your device"
            DownloadCoordinator.STATE_FINISHED -> "Download finished"
            DownloadCoordinator.STATE_BLOCKED -> "Download blocked"
            DownloadCoordinator.STATE_FAILED -> "Download failed"
            else -> "Download"
        }
        titleView?.text = title
        detailView?.text = "$message\n$name\n$host"
        when (state) {
            DownloadCoordinator.STATE_CONNECTING -> {
                spinner?.visibility = View.VISIBLE
                bar?.visibility = View.GONE
                percentView?.visibility = View.GONE
                openBtn?.visibility = View.GONE
            }
            DownloadCoordinator.STATE_TRANSFERRING -> {
                spinner?.visibility = View.VISIBLE
                bar?.visibility = View.VISIBLE
                bar?.isIndeterminate = progress <= 0
                bar?.progress = progress.coerceIn(0, 99)
                percentView?.visibility = View.VISIBLE
                percentView?.text = if (progress > 0) "$progress%" else "Starting…"
                openBtn?.visibility = View.GONE
            }
            DownloadCoordinator.STATE_FINISHED -> {
                spinner?.visibility = View.GONE
                bar?.visibility = View.VISIBLE
                bar?.isIndeterminate = false
                bar?.progress = 100
                percentView?.visibility = View.VISIBLE
                percentView?.text = "100%  Saved to Downloads"
                openBtn?.visibility = if (!lastUri.isNullOrBlank()) View.VISIBLE else View.GONE
            }
            else -> {
                spinner?.visibility = View.GONE
                bar?.visibility = View.GONE
                percentView?.visibility = View.GONE
                openBtn?.visibility = View.GONE
            }
        }
    }
}
