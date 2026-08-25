package com.aegis.browser.ui

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aegis.browser.BuildConfig
import com.aegis.browser.R
import com.aegis.browser.auth.CloudflareAuth
import com.aegis.browser.os.OsUpdateManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Full-screen update UI — checks the Cloudflare / OS update channel,
 * shows current vs available version, and starts download/install.
 */
class UpdateScreen : AppCompatActivity() {

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var currentLine: TextView
    private lateinit var remoteLine: TextView
    private lateinit var spinner: ProgressBar
    private lateinit var actionHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val surface = MaterialPalette.surface(this)
        val on = MaterialPalette.onSurface(this)
        val outline = MaterialPalette.outline(this)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surface)
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(ChromeUi.dp(this@UpdateScreen, 12), ChromeUi.dp(this@UpdateScreen, 14), ChromeUi.dp(this@UpdateScreen, 12), ChromeUi.dp(this@UpdateScreen, 14))
        }
        bar.addView(TextView(this).apply {
            text = "←  System update"
            textSize = 20f
            setTextColor(on)
            setOnClickListener { finish() }
        })
        page.addView(bar)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ChromeUi.dp(this@UpdateScreen, 24), ChromeUi.dp(this@UpdateScreen, 16), ChromeUi.dp(this@UpdateScreen, 24), ChromeUi.dp(this@UpdateScreen, 24))
        }

        statusTitle = TextView(this).apply {
            text = "Checking for updates…"
            textSize = 22f
            setTextColor(on)
        }
        statusDetail = TextView(this).apply {
            text = "Contacting update server"
            textSize = 14f
            setTextColor(outline)
            setPadding(0, ChromeUi.dp(this@UpdateScreen, 8), 0, ChromeUi.dp(this@UpdateScreen, 20))
        }
        spinner = ProgressBar(this).apply {
            isIndeterminate = true
        }
        currentLine = TextView(this).apply {
            text = "Installed: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
            textSize = 14f
            setTextColor(on)
            setPadding(0, ChromeUi.dp(this@UpdateScreen, 16), 0, 0)
        }
        remoteLine = TextView(this).apply {
            text = "Available: —"
            textSize = 14f
            setTextColor(on)
            setPadding(0, ChromeUi.dp(this@UpdateScreen, 8), 0, 0)
        }
        actionHint = TextView(this).apply {
            text = ""
            textSize = 13f
            setTextColor(outline)
            setPadding(0, ChromeUi.dp(this@UpdateScreen, 24), 0, 0)
        }

        body.addView(statusTitle)
        body.addView(statusDetail)
        body.addView(spinner)
        body.addView(currentLine)
        body.addView(remoteLine)
        body.addView(actionHint)

        body.addView(ChromeUi.section(this, "Actions"))
        body.addView(ChromeUi.navRow(this, "Check again", null, R.drawable.ic_nav_refresh) {
            runCheck(installIfNewer = false)
        })
        body.addView(ChromeUi.navRow(this, "Download & install update", "If a newer build is available", R.drawable.ic_nav_download) {
            runCheck(installIfNewer = true)
        })
        if (BuildConfig.OS_BUILD) {
            body.addView(ChromeUi.navRow(this, "Enrollment guide", null, R.drawable.ic_nav_help) {
                OsUpdateManager.openEnrollmentPage(this)
            })
        }

        page.addView(ScrollView(this).apply { addView(body) })
        setContentView(page)

        runCheck(installIfNewer = intent.getBooleanExtra(EXTRA_AUTO_INSTALL, true))
    }

    private fun runCheck(installIfNewer: Boolean) {
        statusTitle.text = "Checking for updates…"
        statusDetail.text = "Contacting ${CloudflareAuth.workerBaseUrl}"
        spinner.visibility = android.view.View.VISIBLE
        actionHint.text = ""

        io.execute {
            try {
                val base = CloudflareAuth.workerBaseUrl.trimEnd('/')
                val path = if (BuildConfig.OS_BUILD) "/os/latest" else "/os/latest"
                val conn = (URL("$base$path").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000
                    readTimeout = 20000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                }
                val text = conn.inputStream.bufferedReader().readText()
                val o = JSONObject(text)
                val remoteCode = o.optInt("versionCode", 0)
                val remoteName = o.optString("versionName", o.optString("tag", "unknown"))
                val tag = o.optString("tag", "")
                val local = BuildConfig.VERSION_CODE

                main.post {
                    spinner.visibility = android.view.View.GONE
                    remoteLine.text = "Available: $remoteName ($remoteCode) · $tag"
                    when {
                        remoteCode <= 0 -> {
                            statusTitle.text = "Could not read update channel"
                            statusDetail.text = text.take(120)
                        }
                        remoteCode <= local -> {
                            statusTitle.text = "You're up to date"
                            statusDetail.text = "No newer build on the update channel"
                            actionHint.text = "Provonlong checks automatically while running."
                        }
                        else -> {
                            statusTitle.text = "Update available"
                            statusDetail.text = "Version $remoteName is ready to install"
                            actionHint.text = if (installIfNewer) {
                                "Starting download…"
                            } else {
                                "Tap Download & install update to apply it."
                            }
                            if (installIfNewer) {
                                if (BuildConfig.OS_BUILD) {
                                    OsUpdateManager.maybeCheck(this, force = true)
                                } else {
                                    // Adult/family: reuse OS download path when worker publishes channel
                                    OsUpdateManager.maybeCheck(this, force = true)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                main.post {
                    spinner.visibility = android.view.View.GONE
                    statusTitle.text = "Update check failed"
                    statusDetail.text = e.message ?: "Network error"
                    remoteLine.text = "Available: —"
                    Toast.makeText(this, "Update check failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        const val EXTRA_AUTO_INSTALL = "auto_install"
    }
}
