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
 * Device settings applet — system intents + password-gated lockdown controls.
 * Status bar / notification shade stay enabled on purpose.
 */
class OsDeviceSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DeviceOwnerHelper.ensureStatusBarEnabled(this)
        if (DeviceOwnerHelper.isDeviceOwner(this)) {
            DeviceOwnerHelper.setUninstallBlocked(this, true)
        }

        val surface = MaterialPalette.surface(this)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surface)
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(
                ChromeUi.dp(this@OsDeviceSettingsActivity, 8),
                ChromeUi.dp(this@OsDeviceSettingsActivity, 12),
                ChromeUi.dp(this@OsDeviceSettingsActivity, 8),
                ChromeUi.dp(this@OsDeviceSettingsActivity, 12)
            )
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
        list.addView(
            ChromeUi.navRow(
                this,
                "Management",
                DeviceOwnerHelper.getStatusSummary(this),
                R.drawable.ic_nav_info
            ) {}
        )
        list.addView(
            ChromeUi.navRow(
                this,
                "Notification shade",
                "Always available — pull down for Wi‑Fi & alerts",
                R.drawable.ic_nav_info
            ) {
                DeviceOwnerHelper.ensureStatusBarEnabled(this)
                Toast.makeText(this, "Status bar left enabled", Toast.LENGTH_SHORT).show()
            }
        )

        list.addView(ChromeUi.section(this, "Network"))
        list.addView(ChromeUi.navRow(this, "Wi‑Fi", "System Wi‑Fi (also via shade)", R.drawable.ic_nav_settings) {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        })
        list.addView(ChromeUi.navRow(this, "Internet", "System network settings", R.drawable.ic_nav_settings) {
            startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        })
        list.addView(ChromeUi.navRow(this, "Apply Gateway PAC", "Device Owner global proxy", R.drawable.ic_nav_lock) {
            val ok = DeviceOwnerHelper.applyGlobalPac(this, PacProxyManager.DEFAULT_PAC_URL)
            Toast.makeText(this, if (ok) "PAC applied" else "Need Device/Profile Owner", Toast.LENGTH_LONG).show()
        })

        list.addView(ChromeUi.section(this, "Display & sound"))
        list.addView(ChromeUi.navRow(this, "Display", null, R.drawable.ic_nav_desktop) {
            startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
        })
        list.addView(ChromeUi.navRow(this, "Sound", null, R.drawable.ic_nav_settings) {
            startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
        })

        list.addView(ChromeUi.section(this, "System updates"))
        list.addView(
            ChromeUi.navRow(
                this,
                "Check for OS updates",
                "Current: ${com.aegis.browser.BuildConfig.VERSION_NAME} (${com.aegis.browser.BuildConfig.VERSION_CODE})",
                R.drawable.ic_nav_download
            ) {
                startActivity(android.content.Intent(this, com.aegis.browser.ui.UpdateScreen::class.java).putExtra(
                    com.aegis.browser.ui.UpdateScreen.EXTRA_AUTO_INSTALL, true
                ))
            }
        )
        list.addView(ChromeUi.navRow(this, "Enrollment guide", "Cloudflare Workers setup page", R.drawable.ic_nav_help) {
            OsUpdateManager.openEnrollmentPage(this)
        })
        list.addView(ChromeUi.navRow(this, "Register this device", "Notify enrollment worker", R.drawable.ic_nav_info) {
            OsUpdateManager.registerDevice(this)
            Toast.makeText(this, "Registration sent", Toast.LENGTH_SHORT).show()
        })

        list.addView(ChromeUi.section(this, "Security"))
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
        list.addView(ChromeUi.navRow(this, "Apply OS hardening", "Block uninstall · keep status bar", R.drawable.ic_nav_lock) {
            DeviceOwnerHelper.applyDefaultOsHardening(this)
            Toast.makeText(
                this,
                if (DeviceOwnerHelper.isDeviceOwner(this)) "Hardening applied"
                else DeviceOwnerHelper.ownerSetupHint(packageName),
                Toast.LENGTH_LONG
            ).show()
        })
        list.addView(ChromeUi.navRow(this, "Security settings", "System security UI", R.drawable.ic_nav_settings) {
            startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
        })

        list.addView(ChromeUi.section(this, "Protected (admin password)"))
        list.addView(
            ChromeUi.navRow(
                this,
                "Allow uninstall of Provonlong OS",
                "Requires admin password",
                R.drawable.ic_nav_unlock
            ) {
                OsAdminGate.challenge(this, "Allow uninstall") {
                    val ok = DeviceOwnerHelper.setUninstallBlocked(this, false)
                    Toast.makeText(
                        this,
                        if (ok) "Uninstall allowed"
                        else "Not Device Owner or failed",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
        list.addView(
            ChromeUi.navRow(
                this,
                "Clear global proxy",
                "Requires admin password",
                R.drawable.ic_nav_unlock
            ) {
                OsAdminGate.challenge(this, "Clear proxy") {
                    val ok = DeviceOwnerHelper.clearGlobalProxy(this)
                    Toast.makeText(this, if (ok) "Proxy cleared" else "Not managed", Toast.LENGTH_SHORT).show()
                }
            }
        )
        list.addView(
            ChromeUi.navRow(
                this,
                "Show Device Owner removal steps",
                "Requires admin password",
                R.drawable.ic_nav_help
            ) {
                OsAdminGate.challenge(this, "Removal instructions") {
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Remove Device Owner")
                        .setMessage(
                            "1. Allow uninstall (above) with admin password\n" +
                                "2. adb shell dpm remove-active-admin $packageName/.mdm.AegisDeviceAdminReceiver\n" +
                                "   or factory reset the device\n\n" +
                                "Sample admin password is only for development builds."
                        )
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        )

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
