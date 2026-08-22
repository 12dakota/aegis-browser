package com.aegis.browser.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.core.widget.ImageViewCompat
import com.aegis.browser.R

/** Shared Chrome-style list rows (settings + overflow menus). */
object ChromeUi {

    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    fun section(ctx: Context, title: String): TextView {
        return TextView(ctx).apply {
            text = title
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(MaterialPalette.primary(ctx))
            setPadding(dp(ctx, 20), dp(ctx, 20), dp(ctx, 20), dp(ctx, 8))
        }
    }

    fun divider(ctx: Context): View {
        return View(ctx).apply {
            setBackgroundColor(MaterialPalette.surfaceContainer(ctx))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 1)
            )
        }
    }

    fun navRow(
        ctx: Context,
        title: String,
        subtitle: String? = null,
        icon: Int? = null,
        onClick: () -> Unit
    ): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14))
            setOnClickListener { onClick() }
            isClickable = true
            isFocusable = true
        }
        if (icon != null) {
            row.addView(ImageView(ctx).apply {
                setImageResource(icon)
                ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(MaterialPalette.onSurface(ctx)))
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 24), dp(ctx, 24)).also {
                    it.marginEnd = dp(ctx, 16)
                }
            })
        }
        val texts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        texts.addView(TextView(ctx).apply {
            text = title
            textSize = 16f
            setTextColor(MaterialPalette.onSurface(ctx))
        })
        if (!subtitle.isNullOrBlank()) {
            texts.addView(TextView(ctx).apply {
                text = subtitle
                textSize = 13f
                setTextColor(MaterialPalette.outline(ctx))
            })
        }
        row.addView(texts)
        return row
    }

    fun switchRow(
        ctx: Context,
        title: String,
        subtitle: String? = null,
        checked: Boolean,
        onChange: (Boolean) -> Unit
    ): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 20), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12))
        }
        val texts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        texts.addView(TextView(ctx).apply {
            text = title
            textSize = 16f
            setTextColor(MaterialPalette.onSurface(ctx))
        })
        if (!subtitle.isNullOrBlank()) {
            texts.addView(TextView(ctx).apply {
                text = subtitle
                textSize = 13f
                setTextColor(MaterialPalette.outline(ctx))
            })
        }
        val sw = Switch(ctx).apply { isChecked = checked }
        sw.setOnCheckedChangeListener { _, v -> onChange(v) }
        row.addView(texts)
        row.addView(sw)
        return row
    }

    fun popupPanel(ctx: Context): LinearLayout {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(MaterialPalette.surface(ctx))
            background = GradientDrawable().apply {
                cornerRadius = 16f * ctx.resources.displayMetrics.density
                setColor(MaterialPalette.surface(ctx))
            }
            setPadding(0, dp(ctx, 8), 0, dp(ctx, 8))
        }
    }
}
