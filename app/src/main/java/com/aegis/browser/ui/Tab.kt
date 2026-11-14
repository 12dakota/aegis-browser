package com.aegis.browser.ui

import com.aegis.browser.engine.BrowserEngine
import java.util.UUID

/**
 * Single browser tab – our own model, not Chrome's.
 * Each tab owns one engine instance (Chromium or Gecko) chosen by EngineSelector.
 */
data class Tab(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "New Tab",
    var url: String = "about:blank",
    var engineType: BrowserEngine.Type = BrowserEngine.Type.SERVO,
    var engine: BrowserEngine? = null,
    var isLoading: Boolean = false,
    var progress: Int = 0
)
