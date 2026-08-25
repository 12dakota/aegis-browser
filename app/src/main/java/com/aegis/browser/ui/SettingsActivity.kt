package com.aegis.browser.ui

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aegis.browser.AegisApplication
import com.aegis.browser.BuildConfig
import com.aegis.browser.R
import com.aegis.browser.downloads.UserSettings
import com.aegis.browser.engine.EngineSelector
import com.aegis.browser.extensions.ExtensionManager

/** Chrome-style Settings: section headers + preference rows. */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val surface = MaterialPalette.surface(this)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surface)
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(ChromeUi.dp(this@SettingsActivity, 4), ChromeUi.dp(this@SettingsActivity, 8), ChromeUi.dp(this@SettingsActivity, 12), ChromeUi.dp(this@SettingsActivity, 8))
            setBackgroundColor(surface)
            elevation = 2f * resources.displayMetrics.density
        }
        val back = android.widget.ImageButton(this).apply {
            setImageResource(R.drawable.ic_nav_back)
            contentDescription = "Back"
            background = null
            androidx.core.widget.ImageViewCompat.setImageTintList(
                this,
                android.content.res.ColorStateList.valueOf(MaterialPalette.onSurface(this@SettingsActivity))
            )
            setOnClickListener { finish() }
        }
        val title = TextView(this).apply {
            text = "Settings"
            textSize = 20f
            setTextColor(MaterialPalette.onSurface(this@SettingsActivity))
            setPadding(ChromeUi.dp(this@SettingsActivity, 8), 0, 0, 0)
        }
        bar.addView(back)
        bar.addView(title)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(ChromeUi.section(this, "Basics"))
        root.addView(ChromeUi.switchRow(this, "Search suggestions", "Show suggestions from Google as you type", true) { })
        root.addView(ChromeUi.switchRow(this, "Autocomplete searches and URLs", null, true) { })

        root.addView(ChromeUi.section(this, "Privacy and security"))
        root.addView(ChromeUi.switchRow(this, "JavaScript", "Allow sites to run JavaScript", UserSettings.javascriptEnabled(this)) {
            UserSettings.set(this, UserSettings.KEY_JAVASCRIPT, it)
        })
        root.addView(ChromeUi.switchRow(this, "Cookies", "Allow sites to save and read cookie data", UserSettings.cookiesEnabled(this)) {
            UserSettings.set(this, UserSettings.KEY_COOKIES, it)
        })
        root.addView(ChromeUi.switchRow(this, "Pop-ups and redirects", "Block pop-ups", UserSettings.blockPopups(this)) {
            UserSettings.set(this, UserSettings.KEY_POPUPS, it)
        })
        root.addView(ChromeUi.switchRow(this, "SafeSearch", "Filter explicit results when searching", UserSettings.forceSafeSearch(this)) {
            UserSettings.set(this, UserSettings.KEY_SAFE_SEARCH, it)
        })

        root.addView(ChromeUi.section(this, "Downloads"))
        root.addView(ChromeUi.switchRow(this, "Ask where to save files", "Use the in-app downloader, not the system manager", UserSettings.downloadsEnabled(this)) {
            UserSettings.set(this, UserSettings.KEY_DOWNLOADS, it)
        })
        root.addView(ChromeUi.switchRow(this, "Download later / background", "Keep transferring when you leave the page", UserSettings.backgroundDownloads(this)) {
            UserSettings.set(this, UserSettings.KEY_BACKGROUND, it)
        })

        root.addView(ChromeUi.section(this, "Site settings"))
        root.addView(ChromeUi.switchRow(this, "Desktop site", "Request the desktop version of websites", UserSettings.desktopUa(this)) {
            UserSettings.set(this, UserSettings.KEY_DESKTOP_UA, it)
        })
        root.addView(ChromeUi.switchRow(this, "Allow any extension", "Install from Chrome Web Store or addons.mozilla.org", ExtensionManager.isAllowAny(this)) {
            ExtensionManager.setAllowAny(this, it)
        })

        root.addView(ChromeUi.section(this, "Accessibility / engine"))
        root.addView(ChromeUi.navRow(this, "Engine", enginePrefLabel(), R.drawable.ic_nav_engine) {
            cycleEngine()
        })
        root.addView(ChromeUi.navRow(this, "Use Auto", "Pick Blink, Gecko, or WebKit per site", R.drawable.ic_nav_engine) {
            EngineSelector.setPreference(this, EngineSelector.Preference.AUTO); toast("Auto")
        })
        root.addView(ChromeUi.navRow(this, "Use Chromium", "System WebView / Blink", R.drawable.ic_nav_engine) {
            EngineSelector.setPreference(this, EngineSelector.Preference.FORCE_CHROMIUM); toast("Chromium")
        })
        root.addView(ChromeUi.navRow(this, "Use Gecko", "Firefox engine + extensions", R.drawable.ic_nav_extension) {
            EngineSelector.setPreference(this, EngineSelector.Preference.FORCE_GECKO); toast("Gecko")
        })
        root.addView(ChromeUi.navRow(this, "Use WebKit", "WPE WebKit", R.drawable.ic_nav_engine) {
            EngineSelector.setPreference(this, EngineSelector.Preference.FORCE_WEBKIT); toast("WebKit")
        })

        if (BuildConfig.OS_BUILD) {
            root.addView(ChromeUi.section(this, "System updates"))
            root.addView(ChromeUi.navRow(this, "Check for OS updates", "Current: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", R.drawable.ic_nav_download) {
                toast("Checking for updates…")
                com.aegis.browser.os.OsUpdateManager.maybeCheck(this, force = true)
            })
            root.addView(ChromeUi.navRow(this, "Enrollment guide", "Open provisioning page in browser", R.drawable.ic_nav_help) {
                com.aegis.browser.os.OsUpdateManager.openEnrollmentPage(this)
            })
            root.addView(ChromeUi.navRow(this, "Device settings", "Wi‑Fi, security, lockdown", R.drawable.ic_nav_settings) {
                startActivity(android.content.Intent(this, com.aegis.browser.os.OsDeviceSettingsActivity::class.java))
            })
            root.addView(ChromeUi.navRow(this, "Register this device", "Notify enrollment worker", R.drawable.ic_nav_info) {
                com.aegis.browser.os.OsUpdateManager.registerDevice(this)
                toast("Registration sent")
            })
        }

        val pol = try { AegisApplication.instance.policyManager.currentPolicy } catch (_: Exception) { null }
        root.addView(ChromeUi.section(this, "About Provonlong"))
        root.addView(ChromeUi.navRow(
            this,
            when { BuildConfig.OS_BUILD -> "OS build"; BuildConfig.ADULT_BUILD -> "Adult build"; else -> "Family build" },
            "Login required: ${BuildConfig.REQUIRE_LOGIN} · Lockdown: ${pol?.lockdownEnabled}",
            R.drawable.ic_nav_info
        ) { })

        val scroll = ScrollView(this).apply { addView(root) }
        page.addView(bar)
        page.addView(scroll)
        setContentView(page)
    }

    private fun enginePrefLabel() = when (EngineSelector.preference) {
        EngineSelector.Preference.AUTO -> "Auto"
        EngineSelector.Preference.FORCE_CHROMIUM -> "Chromium"
        EngineSelector.Preference.FORCE_GECKO -> "Gecko"
        EngineSelector.Preference.FORCE_WEBKIT -> "WebKit"
    }

    private fun cycleEngine() {
        val next = when (EngineSelector.preference) {
            EngineSelector.Preference.AUTO -> EngineSelector.Preference.FORCE_CHROMIUM
            EngineSelector.Preference.FORCE_CHROMIUM -> EngineSelector.Preference.FORCE_GECKO
            EngineSelector.Preference.FORCE_GECKO -> EngineSelector.Preference.FORCE_WEBKIT
            EngineSelector.Preference.FORCE_WEBKIT -> EngineSelector.Preference.AUTO
        }
        EngineSelector.setPreference(this, next)
        toast(enginePrefLabel())
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
