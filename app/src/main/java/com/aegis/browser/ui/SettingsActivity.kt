package com.aegis.browser.ui

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aegis.browser.AegisApplication
import com.aegis.browser.BuildConfig
import com.aegis.browser.R
import com.aegis.browser.downloads.UserSettings
import com.aegis.browser.engine.EngineSelector
import com.aegis.browser.extensions.ExtensionManager

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply {
            setBackgroundColor(MaterialPalette.surface(this@SettingsActivity))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
        }

        root.addView(header("Provonlong settings"))
        root.addView(note("Unmanaged features — local to this device. MDM/cloud policy still wins when it locks a control."))

        root.addView(header("Downloads"))
        root.addView(toggle("In-app downloads (not the system manager)", UserSettings.downloadsEnabled(this)) {
            UserSettings.set(this, UserSettings.KEY_DOWNLOADS, it)
        })
        root.addView(toggle("Continue transfers in the background", UserSettings.backgroundDownloads(this)) {
            UserSettings.set(this, UserSettings.KEY_BACKGROUND, it)
        })

        root.addView(header("Engines"))
        root.addView(note("Current: ${enginePrefLabel()}"))
        root.addView(action("Engine: Auto") { EngineSelector.setPreference(this, EngineSelector.Preference.AUTO); toast("Auto") })
        root.addView(action("Engine: Chromium / Blink") { EngineSelector.setPreference(this, EngineSelector.Preference.FORCE_CHROMIUM); toast("Chromium") })
        root.addView(action("Engine: Gecko") { EngineSelector.setPreference(this, EngineSelector.Preference.FORCE_GECKO); toast("Gecko") })
        root.addView(action("Engine: WebKit (WPE)") { EngineSelector.setPreference(this, EngineSelector.Preference.FORCE_WEBKIT); toast("WebKit") })

        root.addView(header("Extensions"))
        root.addView(toggle("Allow any extension (unrestricted)", ExtensionManager.isAllowAny(this)) {
            ExtensionManager.setAllowAny(this, it)
        })

        root.addView(header("Page behavior"))
        root.addView(toggle("JavaScript", UserSettings.javascriptEnabled(this)) {
            UserSettings.set(this, UserSettings.KEY_JAVASCRIPT, it)
        })
        root.addView(toggle("Cookies", UserSettings.cookiesEnabled(this)) {
            UserSettings.set(this, UserSettings.KEY_COOKIES, it)
        })
        root.addView(toggle("Block pop-ups", UserSettings.blockPopups(this)) {
            UserSettings.set(this, UserSettings.KEY_POPUPS, it)
        })
        root.addView(toggle("Request desktop site", UserSettings.desktopUa(this)) {
            UserSettings.set(this, UserSettings.KEY_DESKTOP_UA, it)
        })
        root.addView(toggle("Force SafeSearch (local)", UserSettings.forceSafeSearch(this)) {
            UserSettings.set(this, UserSettings.KEY_SAFE_SEARCH, it)
        })

        root.addView(header("About this build"))
        val pol = try { AegisApplication.instance.policyManager.currentPolicy } catch (_: Exception) { null }
        root.addView(note(
            "Flavor: ${if (BuildConfig.ADULT_BUILD) "Adult" else "Family"}\n" +
                "Login required: ${BuildConfig.REQUIRE_LOGIN}\n" +
                "Policy lockdown: ${pol?.lockdownEnabled}\n" +
                "Policy disable downloads: ${pol?.disableDownloads}\n" +
                "Files save under app Downloads (not the system Download app)."
        ))

        scroll.addView(root)
        setContentView(scroll)
        title = getString(R.string.app_name)
    }

    private fun enginePrefLabel() = when (EngineSelector.preference) {
        EngineSelector.Preference.AUTO -> "Auto"
        EngineSelector.Preference.FORCE_CHROMIUM -> "Chromium"
        EngineSelector.Preference.FORCE_GECKO -> "Gecko"
        EngineSelector.Preference.FORCE_WEBKIT -> "WebKit"
    }

    private fun header(text: String) = TextView(this).apply {
        this.text = text
        textSize = 18f
        setTextColor(MaterialPalette.onSurface(this@SettingsActivity))
        setPadding(0, dp(20), 0, dp(8))
    }

    private fun note(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(MaterialPalette.outline(this@SettingsActivity))
        setPadding(0, 0, 0, dp(8))
    }

    private fun toggle(label: String, checked: Boolean, on: (Boolean) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        val tv = TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(MaterialPalette.onSurface(this@SettingsActivity))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val sw = Switch(this).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, v -> on(v) }
        }
        row.addView(tv)
        row.addView(sw)
        return row
    }

    private fun action(label: String, click: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 15f
        setTextColor(MaterialPalette.primary(this@SettingsActivity))
        setPadding(0, dp(10), 0, dp(10))
        setOnClickListener { click() }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
