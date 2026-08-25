package com.aegis.browser.os

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aegis.browser.BuildConfig
import com.aegis.browser.mdm.DeviceOwnerHelper
import com.aegis.browser.ui.BrowserActivity
import com.aegis.browser.ui.ChromeUi
import com.aegis.browser.ui.DownloadsActivity
import com.aegis.browser.ui.SettingsActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Chrome OS–style tablet home: wallpaper desk + bottom shelf with pinned apps / PWAs.
 */
class OsLauncherActivity : AppCompatActivity() {

    private lateinit var shelfApps: LinearLayout
    private lateinit var statusLine: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#1B1B1F"))
        }

        // Desktop / wallpaper area
        val desk = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(0, 0, 0, ChromeUi.dp(this@OsLauncherActivity, 88))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#202124"), Color.parseColor("#303134"), Color.parseColor("#1A73E8"))
            )
        }
        desk.addView(TextView(this).apply {
            text = SimpleDateFormat("h:mm", Locale.getDefault()).format(Date())
            textSize = 64f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        desk.addView(TextView(this).apply {
            text = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
            textSize = 18f
            setTextColor(Color.parseColor("#E8EAED"))
            gravity = Gravity.CENTER
            setPadding(0, ChromeUi.dp(this@OsLauncherActivity, 8), 0, 0)
        })
        statusLine = TextView(this).apply {
            text = DeviceOwnerHelper.getStatusSummary(this@OsLauncherActivity)
            textSize = 12f
            setTextColor(Color.parseColor("#BDC1C6"))
            gravity = Gravity.CENTER
            setPadding(0, ChromeUi.dp(this@OsLauncherActivity, 16), 0, 0)
        }
        desk.addView(statusLine)
        desk.addView(TextView(this).apply {
            text = "Search or type a URL in Browser · Install sites as apps from ⋮"
            textSize = 13f
            setTextColor(Color.parseColor("#9AA0A6"))
            gravity = Gravity.CENTER
            setPadding(ChromeUi.dp(this@OsLauncherActivity, 24), ChromeUi.dp(this@OsLauncherActivity, 12), ChromeUi.dp(this@OsLauncherActivity, 24), 0)
        })
        root.addView(desk)

        // Bottom shelf (Chrome OS tablet style)
        val shelf = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#E8EAED"))
            elevation = 12f * resources.displayMetrics.density
            setPadding(
                ChromeUi.dp(this@OsLauncherActivity, 12),
                ChromeUi.dp(this@OsLauncherActivity, 8),
                ChromeUi.dp(this@OsLauncherActivity, 12),
                ChromeUi.dp(this@OsLauncherActivity, 8)
            )
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                ChromeUi.dp(this@OsLauncherActivity, 72),
                Gravity.BOTTOM
            )
        }

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }
        shelfApps = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        scroll.addView(shelfApps)
        shelf.addView(scroll)

        // System tray end of shelf
        shelf.addView(shelfIcon("⋮", "More") { showMoreMenu() })

        root.addView(shelf)
        setContentView(root)
        rebuildShelf()
    }

    override fun onResume() {
        super.onResume()
        DeviceOwnerHelper.ensureStatusBarEnabled(this)
        if (DeviceOwnerHelper.isDeviceOwner(this)) {
            DeviceOwnerHelper.applyDefaultOsHardening(this)
        }
        statusLine.text = DeviceOwnerHelper.getStatusSummary(this)
        rebuildShelf()
    }

    private fun rebuildShelf() {
        shelfApps.removeAllViews()
        // Built-in system apps
        shelfApps.addView(shelfIcon("🌐", "Browser") {
            startActivity(Intent(this, BrowserActivity::class.java))
        })
        shelfApps.addView(shelfIcon("⚙", "Settings") {
            startActivity(Intent(this, OsDeviceSettingsActivity::class.java))
        })
        shelfApps.addView(shelfIcon("⬇", "Files") {
            startActivity(Intent(this, DownloadsActivity::class.java))
        })
        shelfApps.addView(shelfIcon("🔧", "Browser prefs") {
            startActivity(Intent(this, SettingsActivity::class.java))
        })
        // Installed PWAs
        PwaStore.list(this).forEach { app ->
            val label = app.name.take(10)
            val chip = shelfIcon(label.take(1).uppercase(), app.name) {
                startActivity(
                    Intent(this, BrowserActivity::class.java).apply {
                        putExtra(BrowserActivity.EXTRA_START_URL, app.url)
                    }
                )
            }
            chip.setOnLongClickListener {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(app.name)
                    .setItems(arrayOf("Open", "Remove from shelf")) { _, which ->
                        if (which == 0) {
                            startActivity(
                                Intent(this, BrowserActivity::class.java).apply {
                                    putExtra(BrowserActivity.EXTRA_START_URL, app.url)
                                }
                            )
                        } else {
                            PwaStore.remove(this, app.id)
                            rebuildShelf()
                        }
                    }
                    .show()
                true
            }
            shelfApps.addView(chip)
        }
    }

    private fun shelfIcon(glyph: String, desc: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = glyph
            contentDescription = desc
            textSize = if (glyph.length == 1) 20f else 14f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#202124"))
            setBackgroundColor(Color.parseColor("#FFFFFF"))
            background = GradientDrawable().apply {
                cornerRadius = 16f * resources.displayMetrics.density
                setColor(Color.parseColor("#FFFFFF"))
            }
            val size = ChromeUi.dp(this@OsLauncherActivity, 48)
            layoutParams = LinearLayout.LayoutParams(size, size).also {
                it.marginEnd = ChromeUi.dp(this@OsLauncherActivity, 10)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun showMoreMenu() {
        val items = arrayOf(
            "Enable Device Admin",
            "Lock screen",
            "Kiosk mode",
            "About Provonlong OS"
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("System")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> startActivity(DeviceOwnerHelper.requestDeviceAdmin(this))
                    1 -> {
                        if (!DeviceOwnerHelper.lockNow(this)) {
                            Toast.makeText(this, "Enable Device Admin first", Toast.LENGTH_LONG).show()
                        }
                    }
                    2 -> {
                        if (DeviceOwnerHelper.enableLockTask(this)) {
                            try {
                                startLockTask()
                            } catch (e: Exception) {
                                Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(this, DeviceOwnerHelper.ownerSetupHint(packageName), Toast.LENGTH_LONG).show()
                        }
                    }
                    3 -> Toast.makeText(
                        this,
                        "Provonlong OS ${BuildConfig.VERSION_NAME}\n${packageName}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .show()
    }
}
