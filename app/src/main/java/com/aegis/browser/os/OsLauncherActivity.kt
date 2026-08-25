package com.aegis.browser.os

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
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
 * Chrome OS–style tablet home: desk + bottom shelf + scrollable All Apps drawer.
 */
class OsLauncherActivity : AppCompatActivity() {

    private lateinit var root: FrameLayout
    private lateinit var shelfApps: LinearLayout
    private lateinit var statusLine: TextView
    private var drawer: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#1B1B1F"))
        }

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
                intArrayOf(
                    Color.parseColor("#202124"),
                    Color.parseColor("#303134"),
                    Color.parseColor("#1A73E8")
                )
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
            text = "Tap ⊞ All apps on the shelf to open installed apps"
            textSize = 13f
            setTextColor(Color.parseColor("#9AA0A6"))
            gravity = Gravity.CENTER
            setPadding(
                ChromeUi.dp(this@OsLauncherActivity, 24),
                ChromeUi.dp(this@OsLauncherActivity, 12),
                ChromeUi.dp(this@OsLauncherActivity, 24),
                0
            )
        })
        root.addView(desk)

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
        shelf.addView(shelfIcon("⊞", "All apps") { openAppDrawer() })
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

    override fun onBackPressed() {
        if (drawer != null) {
            closeAppDrawer()
        } else {
            super.onBackPressed()
        }
    }

    private fun rebuildShelf() {
        shelfApps.removeAllViews()
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
        PwaStore.list(this).forEach { app ->
            val chip = shelfIcon(app.name.take(1).uppercase(), app.name) {
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

    private fun openAppDrawer() {
        closeAppDrawer()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F8F9FA"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            elevation = 24f * resources.displayMetrics.density
            setPadding(0, ChromeUi.dp(this@OsLauncherActivity, 24), 0, ChromeUi.dp(this@OsLauncherActivity, 80))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                ChromeUi.dp(this@OsLauncherActivity, 16),
                ChromeUi.dp(this@OsLauncherActivity, 8),
                ChromeUi.dp(this@OsLauncherActivity, 16),
                ChromeUi.dp(this@OsLauncherActivity, 8)
            )
        }
        header.addView(TextView(this).apply {
            text = "All apps"
            textSize = 22f
            setTextColor(Color.parseColor("#202124"))
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(TextView(this).apply {
            text = "Close"
            textSize = 15f
            setTextColor(Color.parseColor("#1A73E8"))
            setOnClickListener { closeAppDrawer() }
        })
        panel.addView(header)

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ChromeUi.dp(this@OsLauncherActivity, 8), 0, ChromeUi.dp(this@OsLauncherActivity, 8), 0)
        }
        val apps = InstalledAppsHelper.listLaunchable(this)
        if (apps.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "No other launchable apps found on this device."
                setTextColor(Color.parseColor("#5F6368"))
                setPadding(32, 48, 32, 32)
            })
        } else {
            apps.forEach { app ->
                list.addView(appRow(app))
            }
        }
        panel.addView(ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            addView(list)
        })

        drawer = panel
        root.addView(panel)
    }

    private fun closeAppDrawer() {
        drawer?.let { root.removeView(it) }
        drawer = null
    }

    private fun appRow(app: LaunchableApp): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                ChromeUi.dp(this@OsLauncherActivity, 12),
                ChromeUi.dp(this@OsLauncherActivity, 10),
                ChromeUi.dp(this@OsLauncherActivity, 12),
                ChromeUi.dp(this@OsLauncherActivity, 10)
            )
            setOnClickListener {
                if (InstalledAppsHelper.launch(this@OsLauncherActivity, app)) {
                    closeAppDrawer()
                } else {
                    Toast.makeText(this@OsLauncherActivity, "Could not open ${app.label}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        val iconSize = ChromeUi.dp(this, 40)
        if (app.icon != null) {
            row.addView(ImageView(this).apply {
                setImageDrawable(app.icon)
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).also {
                    it.marginEnd = ChromeUi.dp(this@OsLauncherActivity, 14)
                }
            })
        } else {
            row.addView(TextView(this).apply {
                text = app.label.take(1)
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#1A73E8"))
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).also {
                    it.marginEnd = ChromeUi.dp(this@OsLauncherActivity, 14)
                }
            })
        }
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(this@OsLauncherActivity).apply {
                text = app.label
                textSize = 16f
                setTextColor(Color.parseColor("#202124"))
            })
            addView(TextView(this@OsLauncherActivity).apply {
                text = app.packageName
                textSize = 11f
                setTextColor(Color.parseColor("#5F6368"))
            })
        })
        return row
    }

    private fun shelfIcon(glyph: String, desc: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = glyph
            contentDescription = desc
            textSize = if (glyph.length <= 2) 20f else 14f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#202124"))
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
            "All apps",
            "Enable Device Admin",
            "Lock screen",
            "Kiosk mode",
            "About Provonlong OS"
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("System")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> openAppDrawer()
                    1 -> startActivity(DeviceOwnerHelper.requestDeviceAdmin(this))
                    2 -> {
                        if (!DeviceOwnerHelper.lockNow(this)) {
                            Toast.makeText(this, "Enable Device Admin first", Toast.LENGTH_LONG).show()
                        }
                    }
                    3 -> {
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
                    4 -> Toast.makeText(
                        this,
                        "Provonlong OS ${BuildConfig.VERSION_NAME}\n${packageName}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .show()
    }
}
