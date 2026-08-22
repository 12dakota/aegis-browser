package com.aegis.browser.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

object ErrorOverlay {

    enum class Kind { NO_INTERNET, GENERIC, NOT_SECURE, CERT }

    fun build(
        ctx: Context,
        kind: Kind,
        url: String,
        detail: String?,
        onBack: () -> Unit,
        onRetry: (() -> Unit)? = null,
        onContinue: (() -> Unit)? = null
    ): View {
        val title: String
        val body: String
        when (kind) {
            Kind.NO_INTERNET -> {
                title = "No internet"
                body = "Provonlong can’t reach this page.\n\nCheck Wi‑Fi or mobile data, then try again.\n\n$url"
            }
            Kind.NOT_SECURE -> {
                title = "Your connection is not secure"
                body = "This site uses HTTP, so other people on the network could see what you send.\n\n$url\n\nYou can go back, or continue anyway."
            }
            Kind.CERT -> {
                title = "Your connection is not private"
                body = "Attackers might be trying to steal information from this site (certificate error).\n\n$url\n\n${detail.orEmpty()}\n\nOnly continue if you trust this network."
            }
            Kind.GENERIC -> {
                title = "This page isn’t working"
                body = "${detail ?: "The site sent an unexpected response."}\n\n$url"
            }
        }

        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(MaterialPalette.surface(ctx))
            setPadding(ChromeUi.dp(ctx, 24), ChromeUi.dp(ctx, 32), ChromeUi.dp(ctx, 24), ChromeUi.dp(ctx, 24))
        }
        col.addView(TextView(ctx).apply {
            text = title
            textSize = 22f
            setTextColor(if (kind == Kind.NOT_SECURE || kind == Kind.CERT) Color.parseColor("#C5221F") else MaterialPalette.onSurface(ctx))
        })
        col.addView(TextView(ctx).apply {
            text = body
            textSize = 15f
            setTextColor(MaterialPalette.outline(ctx))
            setPadding(0, ChromeUi.dp(ctx, 16), 0, ChromeUi.dp(ctx, 24))
        })
        col.addView(Button(ctx).apply {
            text = "Go back"
            setOnClickListener { onBack() }
        })
        if (onRetry != null) {
            col.addView(Button(ctx).apply {
                text = "Try again"
                setOnClickListener { onRetry() }
            })
        }
        if (onContinue != null) {
            col.addView(Button(ctx).apply {
                text = "Continue anyway"
                setOnClickListener { onContinue() }
            })
        }
        return ScrollView(ctx).apply {
            setBackgroundColor(MaterialPalette.surface(ctx))
            addView(col)
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
    }
}
