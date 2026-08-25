package com.aegis.browser.os

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.GridLayout
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
import com.aegis.browser.ui.MaterialPalette
import com.aegis.browser.ui.SettingsActivity

/**
 * Chrome OS–style home shelf for the OS flavor.
 * Acts as HOME / LAUNCHER when Provonlong OS is the default home app.
 */
class OsLauncherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val surface = MaterialPalette.surface(this)
        val on = MaterialPalette.onSurface(this)
        val outline = MaterialPalette.outline(this)
        val primary = MaterialPalette.primary(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#202124"))
            setPadding(ChromeUi.dp(this@OsLauncherActivity, 24), ChromeUi.dp(this@OsLauncherActivity, 48), ChromeUi.dp(this@OsLauncherActivity, 24), ChromeUi.dp(this@OsLauncherActivity, 24))
        }

        root.addView(TextView(this).apply {
            text = "Provonlong OS"
            textSize = 28f
            setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = DeviceOwnerHelper.getStatusSummary(this@OsLauncherActivity)
            textSize = 13f
            setTextColor(Color.parseColor("#9AA0A6"))
            setPadding(0, ChromeUi.dp(this@OsLauncherActivity, 8), 0, ChromeUi.dp(this@OsLauncherActivity, 24))
        })

        val grid = GridLayout(this).apply {
            columnCount = 2
            alignmentMode = GridLayout.ALIGN_BOUNDS
        }

        fun tile(title: String, subtitle: String, onClick: () -> Unit) {
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(ChromeUi.dp(this@OsLauncherActivity, 16), ChromeUi.dp(this@OsLauncherActivity, 16), ChromeUi.dp(this@OsLauncherActivity, 16), ChromeUi.dp(this@OsLauncherActivity, 16))
                setBackgroundColor(Color.parseColor("#303134"))
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(
                        ChromeUi.dp(this@OsLauncherActivity, 6),
                        ChromeUi.dp(this@OsLauncherActivity, 6),
                        ChromeUi.dp(this@OsLauncherActivity, 6),
                        ChromeUi.dp(this@OsLauncherActivity, 6)
                    )
                }
                setOnClickListener { onClick() }
            }
            cell.addView(TextView(this).apply {
                text = title
                textSize = 16f
                setTextColor(Color.WHITE)
            })
            cell.addView(TextView(this).apply {
                text = subtitle
                textSize = 12f
                setTextColor(Color.parseColor("#9AA0A6"))
            })
            grid.addView(cell)
        }

        tile("Browser", "Open the web") {
            startActivity(Intent(this, BrowserActivity::class.java))
        }
        tile("Device settings", "Wi‑Fi, display, security, lockdown") {
            startActivity(Intent(this, OsDeviceSettingsActivity::class.java))
        }
        tile("Downloads", "Files on this device") {
            startActivity(Intent(this, DownloadsActivity::class.java))
        }
        tile("Browser settings", "Engines, extensions, privacy") {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        tile("Enable Device Admin", "Required before Device Owner") {
            try {
                startActivity(DeviceOwnerHelper.requestDeviceAdmin(this))
            } catch (e: Exception) {
                Toast.makeText(this, e.message ?: "Cannot open admin prompt", Toast.LENGTH_LONG).show()
            }
        }
        tile("Lock screen", "Force lock now") {
            if (DeviceOwnerHelper.lockNow(this)) {
                Toast.makeText(this, "Locked", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Enable Device Admin first", Toast.LENGTH_LONG).show()
            }
        }
        tile("Kiosk mode", "Lock task when Device Owner") {
            if (DeviceOwnerHelper.enableLockTask(this)) {
                try {
                    startLockTask()
                    Toast.makeText(this, "Lock task started", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Lock task: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(
                    this,
                    "Need Device Owner.\n${DeviceOwnerHelper.ownerSetupHint(packageName)}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        tile("About", "Build ${BuildConfig.VERSION_NAME}") {
            Toast.makeText(
                this,
                "Provonlong OS\n${DeviceOwnerHelper.getStatusSummary(this)}\n${packageName}",
                Toast.LENGTH_LONG
            ).show()
        }

        root.addView(grid)
        root.addView(TextView(this).apply {
            text = "Tip: set this app as Home, then:\n${DeviceOwnerHelper.ownerSetupHint(packageName)}"
            textSize = 11f
            setTextColor(Color.parseColor("#5F6368"))
            setPadding(0, ChromeUi.dp(this@OsLauncherActivity, 24), 0, 0)
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#202124"))
            addView(root)
        })
    }

    override fun onResume() {
        super.onResume()
        // Refresh status text if we recreated — full recreate is fine on next open
    }
}
