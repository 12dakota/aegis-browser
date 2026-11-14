package com.aegis.browser.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.content.res.ColorStateList
import androidx.core.widget.ImageViewCompat
import android.util.TypedValue
import android.widget.ImageView
import android.widget.ImageButton
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
import com.aegis.browser.BuildConfig
import com.aegis.browser.R
import com.aegis.browser.engine.BrowserEngine
import com.aegis.browser.engine.EngineSelector
import com.aegis.browser.ui.MaterialPalette
import com.aegis.browser.mdm.DeviceOwnerHelper
import com.aegis.browser.policy.PolicyManager
import com.aegis.browser.auth.SessionStore
import com.aegis.browser.extensions.ExtensionManager
import com.aegis.browser.downloads.DownloadCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Chrome-like browser shell - entirely our UI and control code.
 *
 * Familiar Chrome patterns (omnibox, tabs, toolbar, progress) implemented by us.
 * Engines, policy, PAC, auth, and lockdown remain Provonlong-owned.
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
    private lateinit var securityIcon: ImageView
    private lateinit var engineBadge: TextView
    private lateinit var tabCountBtn: TextView
    private lateinit var reloadBtn: ImageButton
    private lateinit var bottomTabsBtn: FrameLayout

    private val policyManager get() = AegisApplication.instance.policyManager
    private val pacManager get() = AegisApplication.instance.pacProxyManager

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadCoordinator.ACTION_PROGRESS) return
            val state = intent.getStringExtra(DownloadCoordinator.EXTRA_STATE) ?: return
            val name = intent.getStringExtra(DownloadCoordinator.EXTRA_NAME).orEmpty()
            val host = intent.getStringExtra(DownloadCoordinator.EXTRA_HOST).orEmpty()
            val msg = intent.getStringExtra(DownloadCoordinator.EXTRA_MESSAGE).orEmpty()
            val prog = intent.getIntExtra(DownloadCoordinator.EXTRA_PROGRESS, 0)
            val uri = intent.getStringExtra(DownloadCoordinator.EXTRA_URI)
            runOnUiThread {
                DownloadHud.show(this@BrowserActivity, host, name, state, prog, msg, uri)
            }
        }
    }

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

        // Family build requires cloud login; adult (Aegis Open) does not
        if (BuildConfig.REQUIRE_LOGIN && !SessionStore.isLoggedIn(this)) {
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
            val start = intent.getStringExtra(EXTRA_START_URL)?.takeIf { it.startsWith("http") }
                ?: "https://www.google.com"
            tabManager.createTab(start)
            showActiveTab()
            loadInActiveTab(start)
        }

        ContextCompat.registerReceiver(
            this,
            policyReceiver,
            IntentFilter(PolicyManager.ACTION_POLICY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ContextCompat.registerReceiver(
            this,
            downloadReceiver,
            IntentFilter(DownloadCoordinator.ACTION_PROGRESS),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }


    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun roundedBg(fill: Int, cornerDp: Float, stroke: Int? = null, strokeW: Int = 0): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerDp * resources.displayMetrics.density
            setColor(fill)
            if (stroke != null && strokeW > 0) {
                setStroke(strokeW, stroke)
            }
        }
    }


    private fun buildChromeLikeUi() {
        val surface = MaterialPalette.surface(this)
        val onSurface = MaterialPalette.onSurface(this)
        val container = MaterialPalette.surfaceContainer(this)
        val outline = MaterialPalette.outline(this)
        val primary = MaterialPalette.primary(this)
        val iconTint = ColorStateList.valueOf(onSurface)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surface)
        }

        // —— Tab strip ——
        tabScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(container)
        }
        tabStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(dp(8), dp(8), dp(8), 0)
            minimumHeight = dp(44)
        }
        val newTabBtn = chromeIconButton(R.drawable.ic_nav_add, "New tab") {
            if (policyManager.currentPolicy.lockdownEnabled) return@chromeIconButton
            tabManager.createTab("https://www.google.com")
            loadInActiveTab("https://www.google.com")
        }
        tabStrip.addView(newTabBtn)
        tabScroll.addView(tabStrip)
        tabScroll.visibility = View.GONE // Chrome Android: tabs live in overview, not a strip

        // —— Top toolbar ——
        toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(6), dp(2), dp(6))
            setBackgroundColor(surface)
            elevation = 1f * resources.displayMetrics.density
        }

        val backBtn = chromeIconButton(R.drawable.ic_nav_back, "Back") {
            tabManager.activeTab?.engine?.goBack()
        }
        val forwardBtn = chromeIconButton(R.drawable.ic_nav_forward, "Forward") {
            tabManager.activeTab?.engine?.goForward()
        }
        reloadBtn = chromeIconButton(R.drawable.ic_nav_refresh, "Reload") {
            val tab = tabManager.activeTab
            val eng = tab?.engine
            if (eng != null && tab.isLoading) eng.stopLoading() else eng?.reload()
        }

        // Pill omnibox (Chrome)
        val omniboxContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(container, 24f)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                it.marginStart = dp(2)
                it.marginEnd = dp(2)
            }
            minimumHeight = dp(42)
        }

        securityIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_nav_lock)
            ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(outline))
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).also { it.marginEnd = dp(8) }
            contentDescription = "Site security"
        }

        omnibox = EditText(this).apply {
            hint = "Search Google or type URL"
            setHintTextColor(outline)
            setTextColor(onSurface)
            setBackgroundColor(Color.TRANSPARENT)
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_GO
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            textSize = 15f
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
            setTextColor(primary)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(6), 0, 0, 0)
            contentDescription = "Switch engine"
            setOnClickListener { switchEngineOnActiveTab() }
        }

        omniboxContainer.addView(securityIcon)
        omniboxContainer.addView(omnibox)
        omniboxContainer.addView(engineBadge)

        // Tab counter (Chrome square)
        tabCountBtn = TextView(this).apply {
            text = "1"
            setTextColor(onSurface)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = roundedBg(Color.TRANSPARENT, 6f, onSurface, dp(2))
            minWidth = dp(28)
            minHeight = dp(28)
            setPadding(dp(6), dp(4), dp(6), dp(4))
            contentDescription = "Open tabs"
            setOnClickListener { showTabSwitcher() }
        }

        val menuBtn = chromeIconButton(R.drawable.ic_nav_menu, "Menu") { showChromeMenu() }

        // Chrome Android top chrome is the omnibox only; navigation is the bottom bar.
        toolbar.addView(omniboxContainer)
        toolbar.addView(reloadBtn)
        toolbar.addView(chromeIconButton(R.drawable.ic_nav_gecko, "Firefox menu") { showFirefoxMenu() })

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(2)
            )
            progressTintList = ColorStateList.valueOf(Color.parseColor("#1A73E8"))
        }

        statusBar = TextView(this).apply {
            setTextColor(outline)
            textSize = 11f
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setBackgroundColor(surface)
            visibility = View.GONE
        }

        engineContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            setBackgroundColor(Color.WHITE)
        }

        // Bottom bar — Chrome Android style
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setBackgroundColor(surface)
            elevation = 8f * resources.displayMetrics.density
        }
        bottomBar.addView(chromeIconButton(R.drawable.ic_nav_back, "Back") {
            tabManager.activeTab?.engine?.goBack()
        }.apply { layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f) })
        bottomBar.addView(chromeIconButton(R.drawable.ic_nav_forward, "Forward") {
            tabManager.activeTab?.engine?.goForward()
        }.apply { layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f) })
        bottomBar.addView(chromeIconButton(R.drawable.ic_nav_home, "Home") {
            loadInActiveTab("https://www.google.com")
        }.apply { layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f) })

        // Tabs with number badge
        bottomTabsBtn = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
            contentDescription = "Tabs"
            setOnClickListener { showTabSwitcher() }
            val ic = ImageView(this@BrowserActivity).apply {
                setImageResource(R.drawable.ic_nav_tabs)
                ImageViewCompat.setImageTintList(this, iconTint)
                layoutParams = FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER)
            }
            addView(ic)
            // small count text overlay bottom-right of icon area
            val badge = TextView(this@BrowserActivity).apply {
                text = "1"
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(onSurface)
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER).also {
                    it.topMargin = dp(10)
                }
                tag = "tab_badge"
            }
            addView(badge)
        }
        bottomBar.addView(bottomTabsBtn)
        bottomBar.addView(chromeIconButton(R.drawable.ic_nav_menu, "Menu") {
            showChromeMenu()
        }.apply { layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f) })

        root.addView(tabScroll)
        root.addView(toolbar)
        root.addView(progressBar)
        root.addView(statusBar)
        root.addView(engineContainer)
        root.addView(bottomBar)
        root.fitsSystemWindows = true
        setContentView(root)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = surface
        window.navigationBarColor = surface
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        root.requestApplyInsets()
    }

    private fun chromeIconButton(iconRes: Int, desc: String, onClick: () -> Unit): ImageButton {
        return ImageButton(this).apply {
            setImageResource(iconRes)
            contentDescription = desc
            background = null
            ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(MaterialPalette.onSurface(this@BrowserActivity)))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            minimumWidth = dp(44)
            minimumHeight = dp(44)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { onClick() }
        }
    }

    private fun refreshTabStrip() {
        val surface = MaterialPalette.surface(this)
        val container = MaterialPalette.surfaceContainer(this)
        val onSurface = MaterialPalette.onSurface(this)
        val outline = MaterialPalette.outline(this)

        while (tabStrip.childCount > 1) {
            tabStrip.removeViewAt(0)
        }
        tabManager.allTabs.forEach { tab ->
            val active = tab.id == tabManager.activeTab?.id
            val chip = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(8), dp(4), dp(8))
                background = if (active) roundedBg(surface, 12f) else roundedBg(container, 12f)
                elevation = if (active) 2f * resources.displayMetrics.density else 0f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = dp(4) }
            }
            val fav = ImageView(this).apply {
                setImageResource(R.drawable.ic_nav_info)
                ImageViewCompat.setImageTintList(
                    this,
                    ColorStateList.valueOf(
                        if (active) Color.parseColor("#1A73E8") else outline
                    )
                )
                layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).also { it.marginEnd = dp(6) }
            }
            val host = try {
                android.net.Uri.parse(tab.url).host?.removePrefix("www.") ?: "New Tab"
            } catch (_: Exception) {
                "New Tab"
            }
            val title = TextView(this).apply {
                text = tab.title.take(18).ifBlank { host }
                setTextColor(if (active) onSurface else outline)
                textSize = 13f
                maxLines = 1
                setPadding(0, 0, dp(4), 0)
                setOnClickListener {
                    tabManager.switchTo(tab.id)
                    showActiveTab()
                }
            }
            val close = ImageButton(this).apply {
                setImageResource(R.drawable.ic_nav_close)
                contentDescription = "Close tab"
                background = null
                ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(outline))
                setPadding(dp(6), dp(6), dp(6), dp(6))
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
                setOnClickListener {
                    if (!policyManager.currentPolicy.lockdownEnabled) {
                        tabManager.closeTab(tab.id)
                        showActiveTab()
                    }
                }
            }
            chip.addView(fav)
            chip.addView(title)
            chip.addView(close)
            tabStrip.addView(chip, tabStrip.childCount - 1)
        }
        val count = tabManager.count.toString()
        tabCountBtn.text = count
        tabCountBtn.background = roundedBg(Color.TRANSPARENT, 6f, MaterialPalette.onSurface(this), dp(2))
        // update bottom badge
        if (::bottomTabsBtn.isInitialized) {
            (bottomTabsBtn.findViewWithTag<TextView>("tab_badge"))?.text = count
        }
        updateStatusBar()
    }


    private fun updateSecurityIcon(url: String?) {
        if (!::securityIcon.isInitialized) return
        val u = url.orEmpty()
        val secure = u.startsWith("https://", ignoreCase = true)
        val insecure = u.startsWith("http://", ignoreCase = true)
        securityIcon.setImageResource(
            when {
                secure -> R.drawable.ic_nav_lock
                insecure -> R.drawable.ic_nav_unlock
                else -> R.drawable.ic_nav_info
            }
        )
        securityIcon.contentDescription = when {
            secure -> "Connection is secure"
            insecure -> "Connection is not secure"
            else -> "Site info"
        }
        val tint = android.content.res.ColorStateList.valueOf(
            if (insecure) android.graphics.Color.parseColor("#C5221F")
            else MaterialPalette.outline(this)
        )
        androidx.core.widget.ImageViewCompat.setImageTintList(securityIcon, tint)
    }


    private fun hideWarning() {
        if (!::engineContainer.isInitialized) return
        val tagged = engineContainer.findViewWithTag<View>("error_overlay")
        if (tagged != null) engineContainer.removeView(tagged)
    }

    private fun showWarning(
        kind: ErrorOverlay.Kind,
        url: String,
        detail: String?,
        onRetry: (() -> Unit)? = null,
        onContinue: (() -> Unit)? = null,
        onCancelSsl: (() -> Unit)? = null
    ) {
        if (!::engineContainer.isInitialized) return
        hideWarning()
        val overlay = ErrorOverlay.build(
            this,
            kind,
            url,
            detail,
            onBack = {
                hideWarning()
                onCancelSsl?.invoke()
                tabManager.activeTab?.engine?.goBack()
            },
            onRetry = {
                hideWarning()
                onRetry?.invoke()
            },
            onContinue = onContinue
        )
        overlay.tag = "error_overlay"
        engineContainer.addView(
            overlay,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        overlay.bringToFront()
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
            BrowserEngine.Type.SERVO -> "C"
            BrowserEngine.Type.GECKO -> "G"
            BrowserEngine.Type.WEBKIT -> "W"
            BrowserEngine.Type.CUSTOM -> "X"
        }
        progressBar.visibility = if (tab.isLoading) View.VISIBLE else View.GONE
        progressBar.progress = tab.progress
        if (::reloadBtn.isInitialized) {
            reloadBtn.setImageResource(
                if (tab.isLoading) R.drawable.ic_nav_stop else R.drawable.ic_nav_refresh
            )
            reloadBtn.contentDescription = if (tab.isLoading) "Stop" else "Reload"
        }
        updateSecurityIcon(tab.url)
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
                if (::reloadBtn.isInitialized) {
                    reloadBtn.setImageResource(
                        if (tab.isLoading) R.drawable.ic_nav_stop else R.drawable.ic_nav_refresh
                    )
                }
            }
        }
        engine.setNavigationListener(object : BrowserEngine.NavigationListener {
            override fun onPageStarted(url: String?) {
                tab.url = url ?: tab.url
                tab.isLoading = true
                if (tab.id == tabManager.activeTab?.id) {
                    omnibox.setText(url ?: "")
                    updateSecurityIcon(url)
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
                val desc = errorDescription.orEmpty()
                val kind = if (desc.startsWith("NO_INTERNET")) ErrorOverlay.Kind.NO_INTERNET else ErrorOverlay.Kind.GENERIC
                val target = url ?: tab.url
                showWarning(
                    kind,
                    target,
                    desc.removePrefix("NO_INTERNET:"),
                    onRetry = { loadInActiveTab(target) }
                )
            }

            override fun onSslError(url: String, detail: String?, proceed: () -> Unit, cancel: () -> Unit) {
                val host = SiteWarnings.hostOf(url)
                if (host.isNotBlank() && SiteWarnings.allowSsl(this@BrowserActivity, host)) {
                    proceed()
                    return
                }
                showWarning(
                    ErrorOverlay.Kind.CERT,
                    url,
                    detail,
                    onContinue = {
                        if (host.isNotBlank()) SiteWarnings.rememberSsl(this@BrowserActivity, host)
                        hideWarning()
                        proceed()
                    },
                    onCancelSsl = { cancel() }
                )
            }

            override fun onPageContextMenu(linkUrl: String?, imageUrl: String?, text: String?) {
                val page = tab.url
                com.aegis.browser.extensions.ExtensionContext.remember(page, linkUrl, imageUrl, text)
                ExtensionManager.listInstalled(this@BrowserActivity) { installed ->
                    runOnUiThread {
                        val addons = installed.filter { it.enabled }
                        val extActs = addons.map { ext ->
                            ExtensionMenuAction("Use ${ext.name}") {
                                runExtensionOnContext(ext)
                            }
                        } + addons.map { ext ->
                            ExtensionMenuAction("${ext.name} settings") {
                                openExtensionUi(ext)
                            }
                        }
                        val title = if (tab.engineType == BrowserEngine.Type.GECKO)
                            "Firefox menu" else "Menu"
                        PageContextMenu.show(
                            this@BrowserActivity,
                            PageHit(linkUrl, imageUrl, text),
                            openInTab = { u ->
                                tabManager.createTab(u)
                                loadInActiveTab(u)
                            },
                            loadHere = { u -> loadInActiveTab(u) },
                            extensionActions = extActs,
                            title = title
                        )
                    }
                }
            }

            override fun onSelectedText(text: String) { /* menu handles actions */ }

            override fun shouldOverrideUrlLoading(url: String): Boolean {
                if (handlePossibleExtensionInstall(url)) return true
                if (DownloadCoordinator.looksLikeFile(url) && DownloadCoordinator.allowed(this@BrowserActivity)) {
                    DownloadCoordinator.start(this@BrowserActivity, url)
                    return true
                }
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
        if (DownloadCoordinator.looksLikeFile(url) && DownloadCoordinator.allowed(this)) {
            DownloadCoordinator.start(this, url)
            return
        }
        if (!isNavigationAllowed(url)) return

        var tab = tabManager.activeTab
        if (tab == null) {
            tab = tabManager.createTab(url)
        }

        val desired = EngineSelector.select(url, this)
        if (tab.engine == null || tab.engineType != desired) {
            tab.engine?.destroy()
            tab.engine = EngineSelector.createEngine(this, desired)
            tab.engineType = desired
            showActiveTab()
        }

        if (url.startsWith("http://", ignoreCase = true)) {
            val host = SiteWarnings.hostOf(url)
            if (host.isNotBlank() && !SiteWarnings.allowHttp(this, host)) {
                showWarning(
                    ErrorOverlay.Kind.NOT_SECURE,
                    url,
                    null,
                    onContinue = {
                        SiteWarnings.rememberHttp(this, host)
                        hideWarning()
                        tabManager.activeTab?.engine?.loadUrl(url)
                        updateSecurityIcon(url)
                    }
                )
                omnibox.setText(url)
                updateSecurityIcon(url)
                return
            }
        }

        hideWarning()
        tab.url = url
        tab.engine?.loadUrl(url)
        omnibox.setText(url)
        engineBadge.text = when (desired) {
            BrowserEngine.Type.SERVO -> "C"
            BrowserEngine.Type.GECKO -> "G"
            BrowserEngine.Type.WEBKIT -> "W"
            BrowserEngine.Type.CUSTOM -> "X"
        }
        refreshTabStrip()
    }

    private fun switchEngineOnActiveTab() {
        val tab = tabManager.activeTab ?: return
        val url = tab.url
        val opposite = EngineSelector.opposite(tab.engineType)
        tab.engine?.destroy()
        tab.engine = EngineSelector.createEngine(this, opposite)
        tab.engineType = opposite
        EngineSelector.rememberHostEngine(this, url, opposite)
        showActiveTab()
        if (url.isNotBlank() && url != "about:blank") {
            tab.engine?.loadUrl(url)
        }
        Toast.makeText(this, "Engine: ${opposite.name}", Toast.LENGTH_SHORT).show()
    }


    private fun handlePossibleExtensionInstall(url: String): Boolean {
        if (!ExtensionManager.isXpiOrAmoDownload(url)) return false

        val allowAny = ExtensionManager.isAllowAny(this) ||
            BuildConfig.ADULT_BUILD ||
            BuildConfig.DEFAULT_ALLOW_ANY_EXTENSION ||
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
        val anyExt = ExtensionManager.isAllowAny(this) || BuildConfig.ADULT_BUILD ||
            policyManager.currentPolicy.allowAnyExtension
        val extCount = ExtensionManager.enabledEntries(this).size
        statusBar.text = buildString {
            append(if (lockdown) "LOCKDOWN • " else "Aegis • ")
            append(pacManager.getActiveProxyDescription())
            append(if (anyExt) " • Ext:ANY" else " • Ext:$extCount")
            append(" • Tabs:${tabManager.count}")
            append(" • ")
            append(owner.take(20))
        }
        statusBar.visibility = if (statusBar.text.isNullOrBlank()) View.GONE else View.VISIBLE
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
        val list = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val scroll = android.widget.ScrollView(this)
        tabManager.allTabs.forEach { tab ->
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(14), dp(8), dp(14))
                background = roundedBg(MaterialPalette.surfaceContainer(this@BrowserActivity), 16f)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dp(8) }
            }
            val host = try {
                android.net.Uri.parse(tab.url).host?.removePrefix("www.") ?: "New tab"
            } catch (_: Exception) { "New tab" }
            val title = android.widget.TextView(this).apply {
                text = (tab.title.ifBlank { host }) + "\n" + host
                setTextColor(MaterialPalette.onSurface(this@BrowserActivity))
                textSize = 14f
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    tabManager.switchTo(tab.id)
                    showActiveTab()
                }
            }
            val close = chromeIconButton(R.drawable.ic_nav_close, "Close tab") {
                if (!policyManager.currentPolicy.lockdownEnabled) {
                    tabManager.closeTab(tab.id)
                    showActiveTab()
                }
            }
            row.addView(title)
            row.addView(close)
            // click row
            row.setOnClickListener {
                tabManager.switchTo(tab.id)
                showActiveTab()
            }
            list.addView(row)
        }
        scroll.addView(list)
        AlertDialog.Builder(this)
            .setTitle("Tabs")
            .setView(scroll)
            .setPositiveButton("New tab") { _, _ ->
                tabManager.createTab("https://www.google.com")
                loadInActiveTab("https://www.google.com")
            }
            .setNegativeButton("Done") { d, _ -> d.dismiss() }
            .show()
    }

    private fun showChromeMenu() {
        val panel = ChromeUi.popupPanel(this)
        fun add(title: String, icon: Int, click: () -> Unit) {
            panel.addView(ChromeUi.navRow(this, title, null, icon) {
                click()
            })
        }
        val dlg = AlertDialog.Builder(this).setView(panel).create()
        add("New tab", R.drawable.ic_nav_add) {
            dlg.dismiss()
            tabManager.createTab("https://www.google.com")
            loadInActiveTab("https://www.google.com")
        }
        add("Extensions", R.drawable.ic_nav_extension) {
            dlg.dismiss()
            showExtensionsMenu()
        }
        add("Downloads", R.drawable.ic_nav_download) {
            dlg.dismiss()
            startActivity(Intent(this, DownloadsActivity::class.java))
        }
        add("Desktop site", R.drawable.ic_nav_desktop) {
            val next = !com.aegis.browser.downloads.UserSettings.desktopUa(this)
            com.aegis.browser.downloads.UserSettings.set(this, com.aegis.browser.downloads.UserSettings.KEY_DESKTOP_UA, next)
            Toast.makeText(this, if (next) "Desktop site on" else "Desktop site off", Toast.LENGTH_SHORT).show()
            dlg.dismiss()
        }
        add("Install app", R.drawable.ic_nav_add) {
            dlg.dismiss()
            installCurrentAsPwa()
        }
        add("Settings", R.drawable.ic_nav_settings) {
            dlg.dismiss()
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        add("Help & feedback", R.drawable.ic_nav_help) {
            dlg.dismiss()
            showAbout()
        }
        dlg.show()
    }

    private fun showFirefoxMenu() {
        val panel = ChromeUi.popupPanel(this)
        fun add(title: String, sub: String?, icon: Int, click: () -> Unit) {
            panel.addView(ChromeUi.navRow(this, title, sub, icon) { click() })
        }
        val dlg = androidx.appcompat.app.AlertDialog.Builder(this).setView(panel).create()
        add("New tab", null, R.drawable.ic_nav_add) {
            dlg.dismiss()
            tabManager.createTab("https://www.google.com")
            loadInActiveTab("https://www.google.com")
        }
        add("Add-ons", "Firefox extensions (AMO)", R.drawable.ic_nav_extension) {
            dlg.dismiss()
            EngineSelector.setPreference(this, EngineSelector.Preference.FORCE_GECKO)
            showExtensionsMenu()
        }
        add("Use Gecko engine", "Switch this tab to Firefox", R.drawable.ic_nav_gecko) {
            dlg.dismiss()
            EngineSelector.setPreference(this, EngineSelector.Preference.FORCE_GECKO)
            val tab = tabManager.activeTab
            val url = tab?.url ?: "https://www.google.com"
            if (tab != null) {
                tab.engine?.destroy()
                tab.engine = EngineSelector.createEngine(this, BrowserEngine.Type.GECKO)
                tab.engineType = BrowserEngine.Type.GECKO
                showActiveTab()
                tab.engine?.loadUrl(url)
            }
        }
        add("Downloads", null, R.drawable.ic_nav_download) {
            dlg.dismiss()
            startActivity(android.content.Intent(this, DownloadsActivity::class.java))
        }
        add("Desktop site", null, R.drawable.ic_nav_desktop) {
            val next = !com.aegis.browser.downloads.UserSettings.desktopUa(this)
            com.aegis.browser.downloads.UserSettings.set(this, com.aegis.browser.downloads.UserSettings.KEY_DESKTOP_UA, next)
            android.widget.Toast.makeText(this, if (next) "Desktop site on" else "Desktop site off", android.widget.Toast.LENGTH_SHORT).show()
            dlg.dismiss()
        }
        add("Settings", null, R.drawable.ic_nav_settings) {
            dlg.dismiss()
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
        }
        add("Help", null, R.drawable.ic_nav_help) {
            dlg.dismiss()
            showAbout()
        }
        dlg.show()
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
                val panel = ChromeUi.popupPanel(this)
                val scroll = android.widget.ScrollView(this)
                val col = android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                }
                items.forEachIndexed { i, label ->
                    val icon = when {
                        label.startsWith("Open Mozilla") -> R.drawable.ic_nav_extension
                        label.startsWith("Open Chrome") -> R.drawable.ic_nav_extension
                        label.startsWith("＋") || label.startsWith("+") -> R.drawable.ic_nav_add
                        label.startsWith("──") -> R.drawable.ic_nav_info
                        else -> R.drawable.ic_nav_extension
                    }
                    col.addView(ChromeUi.navRow(this, label, null, icon) {
                        if (i in actions.indices) actions[i].invoke()
                    })
                }
                scroll.addView(col)
                panel.addView(scroll)
                AlertDialog.Builder(this)
                    .setTitle(title)
                    .setView(panel)
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

    private fun runExtensionOnContext(ext: ExtensionManager.InstalledExtension) {
        val target = com.aegis.browser.extensions.ExtensionContext.primaryUrl()
        if (target.isNotBlank()) {
            val cm = getSystemService(android.content.ClipboardManager::class.java)
            cm.setPrimaryClip(android.content.ClipData.newPlainText("Provonlong", target))
        }
        // Open the add-on popup/options so flows like "Save to Google Drive" can run.
        EngineSelector.setPreference(this, EngineSelector.Preference.FORCE_GECKO)
        ExtensionManager.resolveOptionsPage(this, ext.id) { resolved ->
            runOnUiThread {
                val popup = resolved
                    ?: ExtensionManager.optionsOrPopupUrl(ext)
                    ?: ext.baseUrl
                if (popup.isNullOrBlank()) {
                    android.widget.Toast.makeText(
                        this,
                        "Open ${ext.name} from Add-ons to finish this action",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    showExtensionsMenu()
                    return@runOnUiThread
                }
                android.widget.Toast.makeText(
                    this,
                    if (target.isBlank()) "Opening ${ext.name}"
                    else "Opening ${ext.name} with copied link",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                openUrlInGeckoTab(popup)
            }
        }
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
        val chromium = try {
            ChromiumEngine.chromiumVersionSummary(this)
        } catch (_: Exception) {
            "System WebView"
        }
        val msg = """
            Provonlong Web Browser
            Chrome-like shell - our own code.

            Engines: Chromium (Google System WebView) + Gecko (GeckoView)
            Chromium provider: $chromium
            Auth: Cloudflare Worker (family) / open (adult)
            Proxy: Cloudflare Gateway PAC (family)
            Extensions: Gecko / AMO WebExtensions

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

    private fun installCurrentAsPwa() {
        val tab = tabManager.activeTab
        val url = tab?.url?.takeIf { it.startsWith("http") }
        if (url.isNullOrBlank()) {
            Toast.makeText(this, "Open a website first", Toast.LENGTH_SHORT).show()
            return
        }
        val name = tab.title?.takeIf { it.isNotBlank() && !it.startsWith("http") }
            ?: try {
                android.net.Uri.parse(url).host?.removePrefix("www.") ?: "Web app"
            } catch (_: Exception) {
                "Web app"
            }
        val ok = com.aegis.browser.os.PwaStore.add(this, name, url)
        Toast.makeText(
            this,
            if (ok) "Installed “$name” — open the OS shelf to launch it"
            else "Could not install",
            Toast.LENGTH_LONG
        ).show()
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                val sm = getSystemService(android.content.pm.ShortcutManager::class.java)
                if (sm != null && sm.isRequestPinShortcutSupported) {
                    val shortcut = android.content.pm.ShortcutInfo.Builder(this, "pwa_" + url.hashCode())
                        .setShortLabel(name.take(20))
                        .setLongLabel(name)
                        .setIntent(
                            Intent(this, BrowserActivity::class.java).apply {
                                action = Intent.ACTION_VIEW
                                putExtra(EXTRA_START_URL, url)
                            }
                        )
                        .build()
                    sm.requestPinShortcut(shortcut, null)
                }
            }
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(policyReceiver)
        } catch (_: Exception) {
        }
        try {
            unregisterReceiver(downloadReceiver)
        } catch (_: Exception) {
        }
        tabManager.allTabs.forEach {
            it.engine?.destroy()
            it.engine = null
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_START_URL = "start_url"
    }
}
