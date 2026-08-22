package com.aegis.browser.ui

import android.content.Context

/** Hosts the user chose to open despite HTTP / certificate warnings. */
object SiteWarnings {
    private const val PREFS = "provonlong_site_warnings"
    private const val KEY_HTTP = "allow_http"
    private const val KEY_SSL = "allow_ssl"

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun setOf(raw: String) =
        raw.split('|').map { it.trim().lowercase() }.filter { it.isNotBlank() }.toMutableSet()

    fun allowHttp(ctx: Context, host: String) =
        host.lowercase() in setOf(p(ctx).getString(KEY_HTTP, "") ?: "")

    fun allowSsl(ctx: Context, host: String) =
        host.lowercase() in setOf(p(ctx).getString(KEY_SSL, "") ?: "")

    fun rememberHttp(ctx: Context, host: String) {
        val s = setOf(p(ctx).getString(KEY_HTTP, "") ?: "")
        s.add(host.lowercase())
        p(ctx).edit().putString(KEY_HTTP, s.joinToString("|")).apply()
    }

    fun rememberSsl(ctx: Context, host: String) {
        val s = setOf(p(ctx).getString(KEY_SSL, "") ?: "")
        s.add(host.lowercase())
        p(ctx).edit().putString(KEY_SSL, s.joinToString("|")).apply()
    }

    fun hostOf(url: String): String =
        try { android.net.Uri.parse(url).host.orEmpty() } catch (_: Exception) { "" }
}
