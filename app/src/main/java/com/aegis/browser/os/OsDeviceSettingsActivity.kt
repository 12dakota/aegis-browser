package com.aegis.browser.os

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aegis.browser.R
import com.aegis.browser.mdm.DeviceOwnerHelper
import com.aegis.browser.proxy.PacProxyManager
import com.aegis.browser.ui.ChromeUi
import com.aegis.browser.ui.MaterialPalette

/**
 * Device settings applet — system intents + Provonlong lockdown controls.
 */
class OsDeviceSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val surface = MaterialPalette.surface(this)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surface)
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(ChromeUi.dp(this@OsDeviceSettingsActivity, 8), ChromeUi.dp(this@OsDeviceSettingsActivity, 12), ChromeUi.dp(this@OsDeviceSettingsActivity, 8), ChromeUi.dp(this@OsDeviceSettingsActivity, 12))
        }
        bar.addView(TextView(this).apply {
            text = "←  Device settings"
            textSize = 20f
            setTextColor(MaterialPalette.onSurface(this@OsDeviceSettingsActivity))
            setOnClickListener { finish() }
        })
        page.addView(bar)

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        list.addView(ChromeUi.section(this, "Status"))
        list.addView(ChromeUi.navRow(this, "Management", DeviceOwnerHelper.getStatusSummary(this), R.drawable.ic_nav_info) {})

        list.addView(ChromeUi.section(this, "Network"))
        list.addView(ChromeUi.navRow(this, "Wi‑Fi", "Open system Wi‑Fi settings", R.drawable.ic_nav_settings) {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        })
        list.addView(ChromeUi.navRow(this, "Internet", "Open system network settings", R.drawable.ic_nav_settings) {
            startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        })
        list.addView(ChromeUi.navRow(this, "Apply Gateway PAC", "Device Owner global proxy", R.drawable.ic_nav_lock) {
            val ok = DeviceOwnerHelper.applyGlobalPac(this, PacProxyManager.DEFAULT_PAC_URL)
            Toast.makeText(this, if (ok) "PAC applied" else "Need Device/Profile Owner", Toast.LENGTH_LONG).show()
        })
        list.addView(ChromeUi.navRow(this, "Clear global proxy", null, R.drawable.ic_nav_unlock) {
            val ok = DeviceOwnerHelper.clearGlobalProxy(this)
            Toast.makeText(this, if (ok) "Proxy cleared" else "Not managed", Toast.LENGTH_SHORT).show()
        })

        list.addView(ChromeUi.section(this, "Display & sound"))
        list.addView(ChromeUi.navRow(this, "Display", null, R.drawable.ic_nav_desktop) {
            startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
        })
        list.addView(ChromeUi.navRow(this, "Sound", null, R.drawable.ic_nav_settings) {
            startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
        })

        list.addView(ChromeUi.section(this, "Security & lockdown"))
        list.addView(ChromeUi.navRow(this, "Enable Device Admin", "System admin prompt", R.drawable.ic_nav_lock) {
            startActivity(DeviceOwnerHelper.requestDeviceAdmin(this))
        })
        list.addView(ChromeUi.navRow(this, "Lock screen now", null, R.drawable.ic_nav_lock) {
            Toast.makeText(
                this,
                if (DeviceOwnerHelper.lockNow(this)) "Locked" else "Enable Device Admin first",
                Toast.LENGTH_SHORT
            ).show()
        })
        list.addView(ChromeUi.navRow(this, "Enable kiosk packages", "Device Owner lock-task", R.drawable.ic_nav_gecko) {
            val ok = DeviceOwnerHelper.enableLockTask(this)
            Toast.makeText(
                this,
                if (ok) "Lock-task packages set" else DeviceOwnerHelper.ownerSetupHint(packageName),
                Toast.LENGTH_LONG
            ).show()
        })
        list.addView(ChromeUi.navRow(this, "Disable status bar", "Device Owner only", R.drawable.ic_nav_info) {
            val ok = DeviceOwnerHelper.setStatusBarDisabled(this, true)
            Toast.makeText(this, if (ok) "Status bar disabled" else "Need Device Owner", Toast.LENGTH_SHORT).show()
        })
        list.addView(ChromeUi.navRow(this, "Enable status bar", null, R.drawable.ic_nav_info) {
            DeviceOwnerHelper.setStatusBarDisabled(this, false)
            Toast.makeText(this, "Requested status bar on", Toast.LENGTH_SHORT).show()
        })
        list.addView(ChromeUi.navRow(this, "Security settings", "System security UI", R.drawable.ic_nav_settings) {
            startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
        })

        list.addView(ChromeUi.section(this, "Apps & date"))
        list.addView(ChromeUi.navRow(this, "Apps", null, R.drawable.ic_nav_extension) {
            startActivity(Intent(Settings.ACTION_APPLICATION_SETTINGS))
        })
        list.addView(ChromeUi.navRow(this, "Date & time", null, R.drawable.ic_nav_info) {
            startActivity(Intent(Settings.ACTION_DATE_SETTINGS))
        })
        list.addView(ChromeUi.navRow(this, "All system settings", null, R.drawable.ic_nav_settings) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        })

        page.addView(ScrollView(this).apply { addView(list) })
        setContentView(page)
    }
}
