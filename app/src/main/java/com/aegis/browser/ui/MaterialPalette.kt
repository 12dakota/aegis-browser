package com.aegis.browser.ui

import android.content.Context
import android.graphics.Color
import android.util.TypedValue

/** Theme color roles without depending on Material R.attr symbols that fail analysis. */
object MaterialPalette {

    private fun resolve(ctx: Context, attr: Int, fallback: Int): Int {
        val tv = TypedValue()
        return if (ctx.theme.resolveAttribute(attr, tv, true)) {
            if (tv.resourceId != 0) {
                try {
                    ctx.getColor(tv.resourceId)
                } catch (_: Exception) {
                    tv.data
                }
            } else tv.data
        } else fallback
    }

    // Use framework attrs + known Material theme attr resource names via android.R where possible
    fun surface(ctx: Context) = resolve(ctx, android.R.attr.colorBackground, Color.parseColor("#F9F9FF"))
    fun onSurface(ctx: Context) = resolve(ctx, android.R.attr.textColorPrimary, Color.parseColor("#1A1C1E"))
    fun surfaceContainer(ctx: Context) = resolve(ctx, android.R.attr.colorBackground, Color.parseColor("#EEEFF7"))
    fun primary(ctx: Context) = resolve(ctx, android.R.attr.colorPrimary, Color.parseColor("#445E91"))
    fun onPrimary(ctx: Context) = Color.WHITE
    fun primaryContainer(ctx: Context) = resolve(ctx, android.R.attr.colorPrimary, Color.parseColor("#D8E2FF"))
    fun onPrimaryContainer(ctx: Context) = resolve(ctx, android.R.attr.textColorPrimary, Color.parseColor("#001A41"))
    fun outline(ctx: Context) = resolve(ctx, android.R.attr.textColorSecondary, Color.parseColor("#74777F"))
}
