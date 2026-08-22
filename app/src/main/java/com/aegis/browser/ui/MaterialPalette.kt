package com.aegis.browser.ui

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import androidx.annotation.AttrRes

/** Resolves Material 3 / Expressive color roles from the activity theme. */
object MaterialPalette {

    private fun resolve(ctx: Context, @AttrRes attr: Int, fallback: Int): Int {
        val tv = TypedValue()
        return if (ctx.theme.resolveAttribute(attr, tv, true)) {
            if (tv.resourceId != 0) {
                try {
                    ctx.getColor(tv.resourceId)
                } catch (_: Exception) {
                    tv.data
                }
            } else {
                tv.data
            }
        } else {
            fallback
        }
    }

    fun surface(ctx: Context) =
        resolve(ctx, com.google.android.material.R.attr.colorSurface, Color.parseColor("#F9F9FF"))

    fun onSurface(ctx: Context) =
        resolve(ctx, com.google.android.material.R.attr.colorOnSurface, Color.parseColor("#1A1C1E"))

    fun surfaceContainer(ctx: Context) =
        resolve(ctx, com.google.android.material.R.attr.colorSurfaceContainer, Color.parseColor("#EEEFF7"))

    fun primary(ctx: Context) =
        resolve(ctx, com.google.android.material.R.attr.colorPrimary, Color.parseColor("#445E91"))

    fun onPrimary(ctx: Context) =
        resolve(ctx, com.google.android.material.R.attr.colorOnPrimary, Color.WHITE)

    fun primaryContainer(ctx: Context) =
        resolve(ctx, com.google.android.material.R.attr.colorPrimaryContainer, Color.parseColor("#D8E2FF"))

    fun onPrimaryContainer(ctx: Context) =
        resolve(ctx, com.google.android.material.R.attr.colorOnPrimaryContainer, Color.parseColor("#001A41"))

    fun outline(ctx: Context) =
        resolve(ctx, com.google.android.material.R.attr.colorOutline, Color.parseColor("#74777F"))
}
