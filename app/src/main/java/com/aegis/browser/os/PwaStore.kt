package com.aegis.browser.os

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class PwaApp(
    val id: String,
    val name: String,
    val url: String,
    val color: Int = 0xFF1A73E8.toInt()
)

/** Installed PWAs / web apps pinned to the Provonlong OS shelf. */
object PwaStore {
    private const val PREFS = "provonlong_os_pwas"
    private const val KEY = "apps"

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(ctx: Context): List<PwaApp> {
        val raw = p(ctx).getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                PwaApp(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    url = o.optString("url"),
                    color = o.optInt("color", 0xFF1A73E8.toInt())
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(ctx: Context, name: String, url: String): Boolean {
        val clean = url.trim()
        if (!clean.startsWith("http")) return false
        val apps = list(ctx).toMutableList()
        if (apps.any { it.url == clean }) return true
        val id = "pwa_" + System.currentTimeMillis()
        apps.add(PwaApp(id, name.ifBlank { hostOf(clean) }, clean))
        save(ctx, apps)
        return true
    }

    fun remove(ctx: Context, id: String) {
        save(ctx, list(ctx).filter { it.id != id })
    }

    private fun save(ctx: Context, apps: List<PwaApp>) {
        val arr = JSONArray()
        apps.forEach { a ->
            arr.put(
                JSONObject()
                    .put("id", a.id)
                    .put("name", a.name)
                    .put("url", a.url)
                    .put("color", a.color)
            )
        }
        p(ctx).edit().putString(KEY, arr.toString()).apply()
    }

    private fun hostOf(url: String): String =
        try {
            android.net.Uri.parse(url).host?.removePrefix("www.") ?: "Web app"
        } catch (_: Exception) {
            "Web app"
        }
}
