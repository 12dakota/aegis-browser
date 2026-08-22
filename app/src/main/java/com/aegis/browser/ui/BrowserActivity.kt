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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.aegis.browser.AegisApplication
import com.aegis.browser.R
import com.aegis.browser.engine.BrowserEngine
import com.aegis.browser.engine.EngineSelector
import com.aegis.browser.mdm.DeviceOwnerHelper
import com.aegis.browser.policy.PolicyManager
import com.aegis.browser.auth.SessionStore
import com.aegis.browser.extensions.ExtensionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Chrome-like browser shell - entirely our UI and control code.
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

        // Allowlisted extensions (uBlock Origin on by default) on Gecko runtime
        try {
            ExtensionManager.ensureGeckoExtensions(this)
        } catch (_: Exception) {
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
        root.fitsSystemWindows = true
        setContentView(root)

        // Keep chrome below status bar and above nav bar
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = Color.parseColor("#202124")
        window.navigationBarColor = Color.parseColor("#202124")
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        root.requestApplyInsets()
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
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        while (tabStrip.childCount > 1) {
            tabStrip.removeViewAt(0)
        }
        tabManager.allTabs.forEach { tab ->
            val active = tab.id == tabManager.activeTab?.id
            val chip = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(6), dp(4), dp(6))
                setBackgroundColor(
                    if (active) Color.parseColor("#202124") else Color.parseColor("#3C4043")
                )
            }
            val title = TextView(this).apply {
                text = tab.title.take(16).ifBlank { "New Tab" }
                setTextColor(if (active) Color.WHITE else Color.parseColor("#9AA0A6"))
                textSize = 12f
                setPadding(0, 0, dp(6), 0)
                setOnClickListener {
                    tabManager.switchTo(tab.id)
                    showActiveTab()
                }
            }
            val close = TextView(this).apply {
                text = "×"
                setTextColor(Color.parseColor("#E8EAED"))
                textSize = 16f
                setPadding(dp(8), dp(2), dp(8), dp(2))
                setOnClickListener {
                    if (!policyManager.currentPolicy.lockdownEnabled) {
                        tabManager.closeTab(tab.id)
                        showActiveTab()
                    }
                }
            }
            chip.addView(title)
            chip.addView(close)
            // Margin between chips
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = dp(4)
            chip.layoutParams = lp
            tabStrip.addView(chip, tabStrip.childCount - 1)
        }
        tabCountBtn.text = tabManager.count.toString()
        updateStatusBar()
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
                if (handlePossibleExtensionInstall(url)) return true
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

        if (handlePossibleExtensionInstall(url)) return
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


    private fun handlePossibleExtensionInstall(url: String): Boolean {
        if (!ExtensionManager.isXpiOrAmoDownload(url)) return false

        val allowAny = ExtensionManager.isAllowAny(this) ||
            policyManager.currentPolicy.allowAnyExtension
        val entry = ExtensionManager.matchByUrl(url)

        if (!allowAny && entry == null) {
            Toast.makeText(
                this,
                "This extension is not on the parental allowlist. Parent can enable Allow any extension.",
                Toast.LENGTH_LONG
            ).show()
            return true
        }

        val title = entry?.name ?: "this extension"
        val desc = entry?.description ?: url

        val modeLine = if (allowAny) "Unrestricted mode is ON." else "Allowlist mode."
        val message = "Install " + title + "?\n\n" + desc + "\n\n" +
            "Installs use the Gecko engine (Firefox-compatible). " + modeLine
        AlertDialog.Builder(this)
            .setTitle("Add to Aegis?")
            .setMessage(message)
            .setPositiveButton("Add") { _, _ ->
                if (entry != null) ExtensionManager.setEnabled(this, entry.id, true)
                EngineSelector.setPreference(this, EngineSelector.Preference.FORCE_GECKO)
                // Prefer the actual XPI URL the user triggered (AMO download link)
                val xpi = if (url.contains(".xpi", ignoreCase = true) ||
                    url.contains("/downloads/", ignoreCase = true)
                ) url else (entry?.amoXpiUrl ?: url)
                Toast.makeText(this, "Installing…", Toast.LENGTH_SHORT).show()
                ExtensionManager.installFromUrl(this, xpi) { ok, msg ->
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            if (ok) "Installed: $msg - use Gecko tabs (badge G)"
                            else "Install failed: $msg",
                            Toast.LENGTH_LONG
                        ).show()
                        updateStatusBar()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
        return true
    }

    private fun isNavigationAllowed(url: String): Boolean {
        if (!policyManager.isWithinAllowedTime()) {
            Toast.makeText(this, R.string.time_restricted, Toast.LENGTH_LONG).show()
            return false
        }
        // Extension store browse is allowed when not in full lockdown
        if (ExtensionManager.isStoreBrowseUrl(url) && !policyManager.currentPolicy.lockdownEnabled) {
            return true
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
        updateStatusBar()
    }

    private fun updateStatusBar() {
        val lockdown = policyManager.currentPolicy.lockdownEnabled
        val owner = DeviceOwnerHelper.getStatusSummary(this)
        val anyExt = ExtensionManager.isAllowAny(this) || policyManager.currentPolicy.allowAnyExtension
        val extCount = ExtensionManager.enabledEntries(this).size
        statusBar.text = buildString {
            append(if (lockdown) "LOCKDOWN • " else "Aegis • ")
            append(pacManager.getActiveProxyDescription())
            append(if (anyExt) " • Ext:ANY" else " • Ext:$extCount")
            append(" • Tabs:${tabManager.count}")
            append(" • ")
            append(owner.take(20))
        }
        // Tap status bar → extensions menu
        statusBar.setOnClickListener { showExtensionsMenu() }
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
            "Extensions…",
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
                    1 -> showExtensionsMenu()
                    2 -> switchEngineOnActiveTab()
                    3 -> tabManager.closeAllExceptActive()
                    4 -> {
                        EngineSelector.setPreference(this, EngineSelector.Preference.AUTO)
                        Toast.makeText(this, "Engine: Auto", Toast.LENGTH_SHORT).show()
                    }
                    5 -> {
                        EngineSelector.setPreference(this, EngineSelector.Preference.FORCE_CHROMIUM)
                        Toast.makeText(this, "Engine: Chromium only", Toast.LENGTH_SHORT).show()
                    }
                    6 -> {
                        EngineSelector.setPreference(this, EngineSelector.Preference.FORCE_GECKO)
                        Toast.makeText(this, "Engine: Gecko only", Toast.LENGTH_SHORT).show()
                    }
                    7 -> showAbout()
                }
            }
            .show()
    }

    private fun showExtensionsMenu() {
        Toast.makeText(this, "Loading installed extensions…", Toast.LENGTH_SHORT).show()
        ExtensionManager.listInstalled(this) { installed ->
            runOnUiThread {
                val allowAny = ExtensionManager.isAllowAny(this) ||
                    policyManager.currentPolicy.allowAnyExtension
                val items = mutableListOf<String>()
                val actions = mutableListOf<() -> Unit>()

                if (installed.isEmpty()) {
                    items.add("(none installed yet — open AMO and Add to Firefox)")
                    actions.add { }
                } else {
                    for (ext in installed) {
                        val mark = if (ext.enabled) "✓" else "○"
                        items.add("$mark ${ext.name}  v${ext.version}")
                        actions.add { showInstalledExtensionDetail(ext) }
                    }
                }

                items.add("── Catalog / install ──")
                actions.add { }

                for (e in ExtensionManager.ALLOWLIST) {
                    items.add("＋ ${e.name}")
                    actions.add { showCatalogExtensionDetail(e) }
                }

                items.add("Open Mozilla Add-ons (AMO)")
                actions.add { loadInActiveTab("https://addons.mozilla.org/firefox/") }
                items.add("Open Chrome Web Store")
                actions.add { loadInActiveTab("https://chromewebstore.google.com/") }

                val title = if (allowAny) "Extensions (unrestricted)" else "Extensions (allowlist)"
                AlertDialog.Builder(this)
                    .setTitle(title)
                    .setItems(items.toTypedArray()) { _, which ->
                        if (which in actions.indices) actions[which].invoke()
                    }
                    .setPositiveButton("Refresh") { _, _ -> showExtensionsMenu() }
                    .setNegativeButton("Close", null)
                    .show()
            }
        }
    }

    private fun showInstalledExtensionDetail(ext: ExtensionManager.InstalledExtension) {
        val perms = if (ext.permissions.isEmpty()) "(none listed)"
            else ext.permissions.joinToString(", ")
        val origins = if (ext.origins.isEmpty()) "(none)" else ext.origins.take(8).joinToString("\n")
        val msg = """
            ${ext.name}
            id: ${ext.id}
            version: ${ext.version}
            status: ${if (ext.enabled) "ENABLED" else "disabled"}

            ${ext.description}

            Permissions:
            $perms

            Host access:
            $origins

            Options page: ${ext.optionsPageUrl ?: "(none — will try popup paths)"}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle(ext.name)
            .setMessage(msg)
            .setPositiveButton("Open settings") { _, _ -> openExtensionUi(ext) }
            .setNeutralButton("Advanced") { _, _ -> showExtensionAdvanced(ext) }
            .setNegativeButton("More…") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle(ext.name)
                    .setItems(arrayOf(
                        if (ext.enabled) "Disable" else "Enable",
                        "Pick settings page…",
                        "Uninstall"
                    )) { _, which ->
                        when (which) {
                            0 -> ExtensionManager.setExtensionEnabled(this, ext.id, !ext.enabled) { ok, m ->
                                runOnUiThread {
                                    Toast.makeText(this, if (ok) m else "Failed: $m", Toast.LENGTH_SHORT).show()
                                    updateStatusBar()
                                }
                            }
                            1 -> showExtensionPagesPicker(ext)
                            2 -> AlertDialog.Builder(this)
                                .setTitle("Uninstall ${ext.name}?")
                                .setPositiveButton("Uninstall") { _, _ ->
                                    ExtensionManager.uninstall(this, ext.id) { ok, m ->
                                        runOnUiThread {
                                            Toast.makeText(this, if (ok) "Uninstalled" else m, Toast.LENGTH_SHORT).show()
                                            updateStatusBar()
                                        }
                                    }
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .show()
    }

    private fun openExtensionUi(ext: ExtensionManager.InstalledExtension) {
        EngineSelector.setPreference(this, EngineSelector.Preference.FORCE_GECKO)
        Toast.makeText(this, "Resolving settings page…", Toast.LENGTH_SHORT).show()
        ExtensionManager.resolveOptionsPage(this, ext.id) { resolved ->
            runOnUiThread {
                val url = resolved ?: ExtensionManager.optionsOrPopupUrl(ext)
                if (url.isNullOrBlank()) {
                    showExtensionPagesPicker(ext)
                    return@runOnUiThread
                }
                openUrlInGeckoTab(url)
            }
        }
    }

    private fun openUrlInGeckoTab(url: String) {
        EngineSelector.setPreference(this, EngineSelector.Preference.FORCE_GECKO)
        Toast.makeText(this, "Opening: " + url.take(48), Toast.LENGTH_SHORT).show()
        val tab = tabManager.createTab(url)
        tabManager.switchTo(tab.id)
        showActiveTab()
        refreshTabStrip()
        // Force load in case createTab URL was rewritten
        tab.engine?.loadUrl(url)
        omnibox.setText(url)
    }

    private fun showExtensionPagesPicker(ext: ExtensionManager.InstalledExtension) {
        val pages = ExtensionManager.listAllSettingsCandidates(ext)
        if (pages.isEmpty()) {
            Toast.makeText(
                this,
                "No settings page URL yet. Wait a few seconds after install, then Refresh in Extensions.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val labels = pages.map { it.first + "\n" + it.second.take(60) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(ext.name + " — pages")
            .setItems(labels) { _, which ->
                openUrlInGeckoTab(pages[which].second)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showExtensionAdvanced(ext: ExtensionManager.InstalledExtension) {
        val perms = if (ext.permissions.isEmpty()) "(none listed in manifest)"
            else ext.permissions.joinToString("\n• ", prefix = "• ")
        val origins = if (ext.origins.isEmpty()) "(all / none listed)"
            else ext.origins.take(12).joinToString("\n• ", prefix = "• ")
        val granted = ExtensionManager.grantedPermsSummary(this, ext.id)
        val msg = """
            Advanced — ${ext.name}

            Extension id:
            ${ext.id}

            Enabled: ${ext.enabled}
            Version: ${ext.version}
            baseUrl: ${ext.baseUrl ?: "(pending — refresh after install)"}
            optionsPageUrl: ${ext.optionsPageUrl ?: "(none in manifest)"}

            Required permissions:
            $perms

            Host permissions:
            $origins

            Granted at install:
            ${granted.ifBlank { "(not recorded)" }}

            Tip: Open settings uses the extension options page inside a Gecko tab.
            Content scripts only run on Gecko engine tabs.
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("Advanced settings")
            .setMessage(msg)
            .setPositiveButton("Open settings page") { _, _ -> openExtensionUi(ext) }
            .setNeutralButton("Pick page…") { _, _ -> showExtensionPagesPicker(ext) }
            .setNegativeButton(
                if (ext.enabled) "Disable extension" else "Enable extension"
            ) { _, _ ->
                ExtensionManager.setExtensionEnabled(this, ext.id, !ext.enabled) { ok, m ->
                    runOnUiThread {
                        Toast.makeText(this, if (ok) m else "Failed: $m", Toast.LENGTH_SHORT).show()
                        updateStatusBar()
                    }
                }
            }
            .show()
    }

    private fun showCatalogExtensionDetail(entry: ExtensionManager.CatalogEntry) {
        val enabled = ExtensionManager.isEnabled(this, entry.id)
        val msg = """
            ${entry.name}
            ${entry.description}

            Catalog id: ${entry.id}
            AMO id: ${entry.amoId}

            Tap Install to download the signed XPI via Gecko.
            After install it appears in the installed list above.
            Use Open settings on the installed entry for its menu / options HTML.
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle(entry.name)
            .setMessage(msg)
            .setPositiveButton("Install") { _, _ ->
                EngineSelector.setPreference(this, EngineSelector.Preference.FORCE_GECKO)
                ExtensionManager.setEnabled(this, entry.id, true)
                Toast.makeText(this, "Installing ${entry.name}…", Toast.LENGTH_SHORT).show()
                ExtensionManager.installFromUrl(this, entry.amoXpiUrl) { ok, m ->
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            if (ok) "Installed: $m — open Extensions list to configure"
                            else "Install failed: $m",
                            Toast.LENGTH_LONG
                        ).show()
                        updateStatusBar()
                    }
                }
            }
            .setNeutralButton("Open AMO page") { _, _ ->
                loadInActiveTab("https://addons.mozilla.org/firefox/addon/${entry.id}/")
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showExtensionDetail(entry: ExtensionManager.CatalogEntry) {
        showCatalogExtensionDetail(entry)
    }

    private fun showAbout() {
        val msg = """
            Aegis Browser
            Chrome-like shell - our own code.

            Engines: Chromium (WebView) + Gecko (GeckoView)
            Auth: Cloudflare Worker (required)
            Proxy: Cloudflare Gateway PAC
            Extensions: allowlist only (uBlock Origin default)
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
        // Pull latest parent policy (including allowAnyExtension unrestricted mode)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                policyManager.refreshPolicies()
                runOnUiThread {
                    applyPolicyToChrome()
                    updateStatusBar()
                }
            } catch (_: Exception) {
            }
        }
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
