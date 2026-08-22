package com.aegis.browser.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.ImageViewCompat
import com.aegis.browser.R
import com.aegis.browser.downloads.DownloadCoordinator
import com.aegis.browser.downloads.DownloadItem
import com.aegis.browser.downloads.DownloadStore

/** Chrome-style Downloads list with live progress. */
class DownloadsActivity : AppCompatActivity() {

    private lateinit var list: LinearLayout
    private val refresh = { runOnUiThread { render() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val surface = MaterialPalette.surface(this)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surface)
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ChromeUi.dp(this@DownloadsActivity, 4), ChromeUi.dp(this@DownloadsActivity, 8), ChromeUi.dp(this@DownloadsActivity, 8), ChromeUi.dp(this@DownloadsActivity, 8))
            elevation = 2f * resources.displayMetrics.density
            setBackgroundColor(surface)
        }
        val back = ImageButton(this).apply {
            setImageResource(R.drawable.ic_nav_back)
            contentDescription = "Back"
            background = null
            ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(MaterialPalette.onSurface(this@DownloadsActivity)))
            setOnClickListener { finish() }
        }
        val title = TextView(this).apply {
            text = "Downloads"
            textSize = 20f
            setTextColor(MaterialPalette.onSurface(this@DownloadsActivity))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(ChromeUi.dp(this@DownloadsActivity, 8), 0, 0, 0)
        }
        val clear = TextView(this).apply {
            text = "Clear"
            setTextColor(MaterialPalette.primary(this@DownloadsActivity))
            setPadding(ChromeUi.dp(this@DownloadsActivity, 12), ChromeUi.dp(this@DownloadsActivity, 8), ChromeUi.dp(this@DownloadsActivity, 12), ChromeUi.dp(this@DownloadsActivity, 8))
            setOnClickListener { DownloadStore.clearFinished() }
        }
        bar.addView(back)
        bar.addView(title)
        bar.addView(clear)

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list) }
        page.addView(bar)
        page.addView(scroll)
        setContentView(page)
        DownloadStore.addListener(refresh)
        render()
    }

    override fun onDestroy() {
        DownloadStore.removeListener(refresh)
        super.onDestroy()
    }

    private fun render() {
        list.removeAllViews()
        val items = DownloadStore.snapshot()
        if (items.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "No downloads yet.\nFiles you save appear here with progress."
                setTextColor(MaterialPalette.outline(this@DownloadsActivity))
                textSize = 15f
                setPadding(ChromeUi.dp(this@DownloadsActivity, 24), ChromeUi.dp(this@DownloadsActivity, 32), ChromeUi.dp(this@DownloadsActivity, 24), 0)
            })
            return
        }
        items.forEach { list.addView(row(it)) }
    }

    private fun row(item: DownloadItem): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ChromeUi.dp(this@DownloadsActivity, 20), ChromeUi.dp(this@DownloadsActivity, 14), ChromeUi.dp(this@DownloadsActivity, 20), ChromeUi.dp(this@DownloadsActivity, 14))
        }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(TextView(this).apply {
            text = item.name
            textSize = 16f
            setTextColor(MaterialPalette.onSurface(this@DownloadsActivity))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val status = when (item.state) {
            DownloadCoordinator.STATE_CONNECTING -> "Connecting to host"
            DownloadCoordinator.STATE_TRANSFERRING -> "Transferring… ${item.progress}%"
            DownloadCoordinator.STATE_FINISHED -> "Download finished"
            DownloadCoordinator.STATE_FAILED -> "Failed"
            DownloadCoordinator.STATE_BLOCKED -> "Blocked"
            else -> item.message
        }
        col.addView(head)
        col.addView(TextView(this).apply {
            text = "${item.host}\n$status"
            textSize = 13f
            setTextColor(MaterialPalette.outline(this@DownloadsActivity))
        })
        if (item.state == DownloadCoordinator.STATE_CONNECTING ||
            item.state == DownloadCoordinator.STATE_TRANSFERRING
        ) {
            col.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                isIndeterminate = item.state == DownloadCoordinator.STATE_CONNECTING
                progress = item.progress
                progressTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#1A73E8"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    ChromeUi.dp(this@DownloadsActivity, 6)
                ).also { it.topMargin = ChromeUi.dp(this@DownloadsActivity, 8) }
            })
        }
        col.setOnClickListener {
            if (item.state == DownloadCoordinator.STATE_FINISHED && !item.uri.isNullOrBlank()) {
                try {
                    startActivity(
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(item.uri), "*/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    )
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(this, "Saved to Downloads", Toast.LENGTH_SHORT).show()
                }
            }
        }
        return col
    }
}
