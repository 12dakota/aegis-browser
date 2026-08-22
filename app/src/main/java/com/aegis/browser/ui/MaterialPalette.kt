package com.aegis.browser.ui

import android.content.Context
import android.graphics.Color
import com.google.android.material.R as MaterialR
import com.google.android.material.color.MaterialColors

/** Resolves Material 3 Expressive / dynamic-color roles for Views chrome. */
object MaterialPalette {
    fun surface(ctx: Context) =
        MaterialColors.getColor(ctx, MaterialR.attr.colorSurface, Color.parseColor("#F9F9FF"))

    fun onSurface(ctx: Context) =
        MaterialColors.getColor(ctx, MaterialR.attr.colorOnSurface, Color.parseColor("#1A1C1E"))

    fun surfaceContainer(ctx: Context) =
        MaterialColors.getColor(ctx, MaterialR.attr.colorSurfaceContainer, Color.parseColor("#EEEFF7"))

    fun primary(ctx: Context) =
        MaterialColors.getColor(ctx, MaterialR.attr.colorPrimary, Color.parseColor("#445E91"))

    fun onPrimary(ctx: Context) =
        MaterialColors.getColor(ctx, MaterialR.attr.colorOnPrimary, Color.WHITE)

    fun primaryContainer(ctx: Context) =
        MaterialColors.getColor(ctx, MaterialR.attr.colorPrimaryContainer, Color.parseColor("#D8E2FF"))

    fun onPrimaryContainer(ctx: Context) =
        MaterialColors.getColor(ctx, MaterialR.attr.colorOnPrimaryContainer, Color.parseColor("#001A41"))

    fun outline(ctx: Context) =
        MaterialColors.getColor(ctx, MaterialR.attr.colorOutline, Color.parseColor("#74777F"))
}
