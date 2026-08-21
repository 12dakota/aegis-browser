package com.aegis.browser.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.aegis.browser.AegisApplication
import com.aegis.browser.R
import com.aegis.browser.engine.BrowserEngine
import com.aegis.browser.engine.EngineSelector
import com.aegis.browser.mdm.DeviceOwnerHelper
import com.aegis.browser.policy.PolicyManager
import com.aegis.browser.auth.SessionStore

/**
 * Chrome-like browser shell — entirely our UI and control code.
 *
 * Familiar Chrome patterns (omnibox, tabs, toolbar, progress) implemented by us.
 * Engines, policy, PAC, auth, and lockdown remain Aegis-owned.
 */
class BrowserActivity : AppCompatActivity() {

    private lateinit var tabManager: TabManager
    private lateinit var engineContainer: FrameLayout
    private lateinit var omnibox: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var tabStrip: LinearLayout
    private lateinit var tabScroll: HorizontalScrollView
    private lateinit var statusBar: TextView
    private lateinit var toolbar: LinearLayout
    private lateinit var securityIcon: TextView
    private lateinit var engineBadge: TextView
    private lateinit var tabCountBtn: TextView

    private val policyManager get() = AegisApplication.instance.policyManager
    private val pacManager get() = AegisApplication.instance.pacProxyManager

    private val policyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            applyPolicyToChrome()
            if (policyManager.currentPolicy.lockdownEnabled) {
                tabManager.activeTab?.engine?.loadUrl("about:blank")
                showLockdownOverlay()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!SessionStore.isLoggedIn(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        tabManager = TabManager(this)
        tabManager.onTabsChanged = {
            refreshTabStrip()
            showActiveTab()
        }

        buildChromeLikeUi()
        applyPolicyToChrome()

        if (policyManager.currentPolicy.lockdownEnabled) {
            showLockdownOverlay()
        } else {
            tabManager.createTab("https://www.google.com")
            showActiveTab()
            loadInActiveTab("https://www.google.com")
        }

        ContextCompat.registerReceiver(
            this,
            policyReceiver,
            IntentFilter(PolicyManager.ACTION_POLICY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun buildChromeLikeUi() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#202124"))
        }

        tabScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(Color.parseColor("#303134"))
        }
        tabStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), 0)
        }
        val newTabBtn = TextView(this).apply {
            text = "＋"
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setOnClickListener {
                if (policyManager.currentPolicy.lockdownEnabled) return@setOnClickListener
                tabManager.createTab("https://www.google.com")
                loadInActiveTab("https://www.google.com")
            }
        }
        tabStrip.addView(newTabBtn)
        tabScroll.addView(tabStrip)

        toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setBackgroundColor(Color.parseColor("#202124"))
        }

        val backBtn = chromeIconButton("←") { tabManager.activeTab?.engine?.goBack() }
        val forwardBtn = chromeIconButton("→") { tabManager.activeTab?.engine?.goForward() }
        val reloadBtn = chromeIconButton("↻") {
            val tab = tabManager.activeTab
            val eng = tab?.engine
            if (eng != null && tab.isLoading) eng.stopLoading() else eng?.reload()
        }

        val omniboxContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#303134"))
            setPadding(dp(10), dp(6), dp(10), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                it.marginStart = dp(4)
                it.marginEnd = dp(4)
            }
        }

        securityIcon = TextView(this).apply {
            text = "🔒"
            textSize = 12f
            setPadding(0, 0, dp(6), 0)
        }

        omnibox = EditText(this).apply {
            hint = "Search Google or type URL"
            setHintTextColor(Color.parseColor("#9AA0A6"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_GO
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_GO ||
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
                ) {
                    submitOmnibox()
                    true
                } else false
            }
        }

        engineBadge = TextView(this).apply {
            text = "C"
            setTextColor(Color.parseColor("#8AB4F8"))
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(6), 0, 0, 0)
            setOnClickListener { switchEngineOnActiveTab() }
        }

        omniboxContainer.addView(securityIcon)
        omniboxContainer.addView(omnibox)
        omniboxContainer.addView(engineBadge)

        tabCountBtn = TextView(this).apply {
            text = "1"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setBackgroundColor(Color.parseColor("#3C4043"))
            setOnClickListener { showTabSwitcher() }
        }

        val menuBtn = chromeIconButton("⋮") { showChromeMenu() }

        toolbar.addView(backBtn)
        toolbar.addView(forwardBtn)
        toolbar.addView(reloadBtn)
        toolbar.addView(omniboxContainer)
        toolbar.addView(tabCountBtn)
        toolbar.addView(menuBtn)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(2)
            )
        }

        statusBar = TextView(this).apply {
            setTextColor(Color.parseColor("#9AA0A6"))
            textSize = 10f
            setPadding(dp(10), dp(2), dp(10), dp(4))
            setBackgroundColor(Color.parseColor("#202124"))
        }

        engineContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            setBackgroundColor(Color.WHITE)
        }

        root.addView(tabScroll)
        root.addView(toolbar)
        root.addView(progressBar)
        root.addView(statusBar)
        root.addView(engineContainer)
        setContentView(root)
    }

    private fun chromeIconButton(label: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(Color.parseColor("#E8EAED"))
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(24, 12, 24, 12)
            setOnClickListener { onClick() }
        }
    }

    private fun refreshTabStrip() {
        while (tabStrip.childCount > 1) {
            tabStrip.removeViewAt(0)
        }
        tabManager.allTabs.forEach { tab ->
            val chip = TextView(this).apply {
                text = tab.title.take(18).ifBlank { "New Tab" }
                setTextColor(
                    if (tab.id == tabManager.activeTab?.id) Color.WHITE
                    else Color.parseColor("#9AA0A6")
                )
                textSize = 12f
                setPadding(28, 16, 28, 16)
                setBackgroundColor(
                    if (tab.id == tabManager.activeTab?.id) Color.parseColor("#202124")
                    else Color.parseColor("#3C4043")
                )
                setOnClickListener {
                    tabManager.switchTo(tab.id)
                    showActiveTab()
                }
                setOnLongClickListener {
                    if (!policyManager.currentPolicy.lockdownEnabled) {
                        tabManager.closeTab(tab.id)
                        showActiveTab()
                    }
                    true
                }
            }
            tabStrip.addView(chip, tabStrip.childCount - 1)
        }
        tabCountBtn.text = tabManager.count.toString()
    }

    private fun showActiveTab() {
        engineContainer.removeAllViews()
        val tab = tabManager.activeTab ?: return
        val engine = tab.engine ?: return

        bindEngineListeners(tab, engine)

        val view = engine.getView()
        (view.parent as? ViewGroup)?.removeView(view)
        engineContainer.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        omnibox.setText(tab.url.takeIf { it != "about:blank" } ?: "")
        engineBadge.text = when (tab.engineType) {
            BrowserEngine.Type.CHROMIUM -> "C"
            BrowserEngine.Type.GECKO -> "G"
        }
        progressBar.visibility = if (tab.isLoading) View.VISIBLE else View.GONE
        progressBar.progress = tab.progress
        refreshTabStrip()
        applyPolicyToChrome()
    }

    private fun bindEngineListeners(tab: Tab, engine: BrowserEngine) {
        engine.setProgressListener { p ->
            tab.progress = p
            tab.isLoading = p in 1..99
            if (tab.id == tabManager.activeTab?.id) {
                progressBar.visibility = if (tab.isLoading) View.VISIBLE else View.GONE
                progressBar.progress = p
            }
        }
        engine.setNavigationListener(object : BrowserEngine.NavigationListener {
            override fun onPageStarted(url: String?) {
                tab.url = url ?: tab.url
                tab.isLoading = true
                if (tab.id == tabManager.activeTab?.id) {
                    omnibox.setText(url ?: "")
                    url?.let { pacManager.evaluateAndSetProxy(it) }
                }
            }

            override fun onPageFinished(url: String?) {
                tab.isLoading = false
                tab.url = url ?: tab.url
                tab.title = engine.getTitle()?.takeIf { it.isNotBlank() } ?: tab.url
                if (tab.id == tabManager.activeTab?.id) {
                    progressBar.visibility = View.GONE
                    refreshTabStrip()
                }
            }

            override fun onReceivedError(url: String?, errorDescription: String?) {
                Toast.makeText(
                    this@BrowserActivity,
                    "Page error on ${tab.engineType.name}. Tap engine badge to switch.",
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun shouldOverrideUrlLoading(url: String): Boolean {
                return !isNavigationAllowed(url)
            }
        })
    }

    private fun submitOmnibox() {
        val raw = omnibox.text.toString().trim()
        if (raw.isEmpty()) return
        hideKeyboard()
        loadInActiveTab(raw)
    }

    private fun loadInActiveTab(raw: String) {
        var url = raw.trim()
        if (url.isEmpty()) return

        if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("about:")) {
            url = if (url.contains(".") && !url.contains(" ")) {
                "https://$url"
            } else {
                "https://www.google.com/search?q=${java.net.URLEncoder.encode(url, "UTF-8")}&safe=active"
            }
        }

        if (!isNavigationAllowed(url)) return

        var tab = tabManager.activeTab
        if (tab == null) {
            tab = tabManager.createTab(url)
        }

        val desired = EngineSelector.select(url)
        if (tab.engine == null || tab.engineType != desired) {
            tab.engine?.destroy()
            tab.engine = EngineSelector.createEngine(this, desired)
            tab.engineType = desired
            showActiveTab()
        }

        tab.url = url
        tab.engine?.loadUrl(url)
        omnibox.setText(url)
        engineBadge.text = if (desired == BrowserEngine.Type.CHROMIUM) "C" else "G"
        refreshTabStrip()
    }

    private fun switchEngineOnActiveTab() {
        val tab = tabManager.activeTab ?: return
        val url = tab.url
        val opposite = EngineSelector.opposite(tab.engineType)
        tab.engine?.destroy()
        tab.engine = EngineSelector.createEngine(this, opposite)
        tab.engineType = opposite
        showActiveTab()
        if (url.isNotBlank() && url != "about:blank") {
            tab.engine?.loadUrl(url)
        }
        Toast.makeText(this, "Engine: ${opposite.name}", Toast.LENGTH_SHORT).show()
    }

    private fun isNavigationAllowed(url: String): Boolean {
        if (!policyManager.isWithinAllowedTime()) {
            Toast.makeText(this, R.string.time_restricted, Toast.LENGTH_LONG).show()
            return false
        }
        if (!policyManager.isUrlAllowed(url)) {
            Toast.makeText(this, R.string.url_blocked, Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    private fun applyPolicyToChrome() {
        val lockdown = policyManager.currentPolicy.lockdownEnabled
        toolbar.visibility = if (lockdown) View.GONE else View.VISIBLE
        tabScroll.visibility = if (lockdown) View.GONE else View.VISIBLE
        val owner = DeviceOwnerHelper.getStatusSummary(this)
        statusBar.text = buildString {
            append(if (lockdown) "LOCKDOWN • " else "Aegis • ")
            append(pacManager.getActiveProxyDescription())
            append(" • ")
            append(owner.take(28))
        }
    }

    private fun showLockdownOverlay() {
        AlertDialog.Builder(this)
            .setTitle("Lockdown Active")
            .setMessage(getString(R.string.lockdown_active))
            .setCancelable(false)
            .setPositiveButton("OK") { d, _ -> d.dismiss() }
            .show()
        tabManager.activeTab?.engine?.loadUrl("about:blank")
    }

    private fun showTabSwitcher() {
        val titles = tabManager.allTabs.map {
            "${it.title.take(30)} (${it.engineType.name.take(1)})"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Tabs (${tabManager.count})")
            .setItems(titles) { _, which ->
                val tab = tabManager.allTabs.getOrNull(which) ?: return@setItems
                tabManager.switchTo(tab.id)
                showActiveTab()
            }
            .setNegativeButton("Close") { d, _ -> d.dismiss() }
            .show()
    }

    private fun showChromeMenu() {
        val items = arrayOf(
            "New tab",
            "Switch engine (C ↔ G)",
            "Close other tabs",
            "Engine preference: Auto",
            "Engine preference: Chromium",
            "Engine preference: Gecko",
            "Status / About"
        )
        AlertDialog.Builder(this)
            .setTitle("Aegis")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        tabManager.createTab("https://www.google.com")
                        loadInActiveTab("https://www.google.com")
                    }
                    1 -> switchEngineOnActiveTab()
                    2 -> tabManager.closeAllExceptActive()
                    3 -> {
                        EngineSelector.setPreference(this, EngineSelector.Preference.AUTO)
                        Toast.makeText(this, "Engine: Auto", Toast.LENGTH_SHORT).show()
                    }
                    4 -> {
                        EngineSelector.setPreference(this, EngineSelector.Preference.FORCE_CHROMIUM)
                        Toast.makeText(this, "Engine: Chromium only", Toast.LENGTH_SHORT).show()
                    }
                    5 -> {
                        EngineSelector.setPreference(this, EngineSelector.Preference.FORCE_GECKO)
                        Toast.makeText(this, "Engine: Gecko only", Toast.LENGTH_SHORT).show()
                    }
                    6 -> showAbout()
                }
            }
            .show()
    }

    private fun showAbout() {
        val msg = """
            Aegis Browser
            Chrome-like shell — our own code.

            Engines: Chromium (WebView) + Gecko (GeckoView)
            Auth: Cloudflare Worker (required)
            Proxy: Cloudflare Gateway PAC
            Controls: MDM + parental policy + remote lockdown

            ${DeviceOwnerHelper.getStatusSummary(this)}
            Active engine: ${tabManager.activeTab?.engineType}
            Tabs: ${tabManager.count}
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("About Aegis")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(omnibox.windowToken, 0)
        omnibox.clearFocus()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val eng = tabManager.activeTab?.engine
        if (eng?.canGoBack() == true && !policyManager.currentPolicy.lockdownEnabled) {
            eng.goBack()
        } else {
            Toast.makeText(this, "Navigation restricted by policy", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        tabManager.activeTab?.engine?.onResume()
    }

    override fun onPause() {
        tabManager.activeTab?.engine?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(policyReceiver)
        } catch (_: Exception) {
        }
        tabManager.allTabs.forEach {
            it.engine?.destroy()
            it.engine = null
        }
        super.onDestroy()
    }
}
