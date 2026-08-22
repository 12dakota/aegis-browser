package com.aegis.browser.ui

import android.content.Context
import com.aegis.browser.engine.BrowserEngine
import com.aegis.browser.engine.EngineSelector
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Our own tab manager – Chrome-like multi-tab behavior without using Chrome's tab system.
 * Max tabs can later be capped by parental policy.
 */
class TabManager(private val context: Context) {

    private val tabs = CopyOnWriteArrayList<Tab>()
    private var activeTabId: String? = null

    var onTabsChanged: (() -> Unit)? = null

    val allTabs: List<Tab> get() = tabs.toList()
    val activeTab: Tab? get() = tabs.find { it.id == activeTabId }
    val count: Int get() = tabs.size

    fun createTab(url: String = "https://www.google.com"): Tab {
        val type = EngineSelector.select(url, context)
        val engine = EngineSelector.createEngine(context, type)
        val tab = Tab(
            title = "New Tab",
            url = url,
            engineType = type,
            engine = engine
        )
        tabs.add(tab)
        activeTabId = tab.id
        onTabsChanged?.invoke()
        return tab
    }

    fun switchTo(tabId: String) {
        if (tabs.any { it.id == tabId }) {
            activeTabId = tabId
            onTabsChanged?.invoke()
        }
    }

    fun closeTab(tabId: String) {
        val idx = tabs.indexOfFirst { it.id == tabId }
        if (idx < 0) return
        val tab = tabs.removeAt(idx)
        tab.engine?.setNavigationListener(null)
        tab.engine?.setProgressListener(null)
        tab.engine?.destroy()
        tab.engine = null

        if (tabs.isEmpty()) {
            createTab("about:blank")
        } else if (activeTabId == tabId) {
            val newIdx = idx.coerceAtMost(tabs.lastIndex)
            activeTabId = tabs[newIdx].id
        }
        onTabsChanged?.invoke()
    }

    fun closeAllExceptActive() {
        val keep = activeTabId
        tabs.filter { it.id != keep }.forEach { closeTab(it.id) }
    }
}
