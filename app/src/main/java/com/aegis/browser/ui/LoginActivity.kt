package com.aegis.browser.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aegis.browser.AegisApplication
import com.aegis.browser.BuildConfig
import com.aegis.browser.auth.CloudflareAuth
import com.aegis.browser.auth.SessionStore
import kotlinx.coroutines.launch

/**
 * Launcher activity. Mandatory login via Cloudflare Worker only.
 * No Firebase client SDK on this path — secrets stay in the cloud.
 */
class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Adult build: no parental login gate
        if (!BuildConfig.REQUIRE_LOGIN) {
            startActivity(Intent(this, BrowserActivity::class.java))
            finish()
            return
        }
        if (SessionStore.isLoggedIn(this)) {
            proceedToBrowser()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 120, 48, 48)
            setBackgroundColor(MaterialPalette.surface(this@LoginActivity))
        }

        val title = TextView(this).apply {
            text = getString(com.aegis.browser.R.string.app_name)
            textSize = 28f
            setTextColor(MaterialPalette.onSurface(this@LoginActivity))
            setPadding(0, 0, 0, 32)
        }

        val subtitle = TextView(this).apply {
            text = "Secure parental & enterprise browser\nLogin is required (Cloudflare)"
            textSize = 14f
            setTextColor(MaterialPalette.outline(this@LoginActivity))
            setPadding(0, 0, 0, 48)
        }

        val email = EditText(this).apply {
            hint = "Email"
            setTextColor(0xFF000000.toInt())
            setHintTextColor(0xFF666666.toInt())
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding(24, 24, 24, 24)
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }

        val password = EditText(this).apply {
            hint = "Password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(0xFF000000.toInt())
            setHintTextColor(0xFF666666.toInt())
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding(24, 24, 24, 24)
        }

        val progress = ProgressBar(this).apply {
            visibility = View.GONE
        }

        val status = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFFFFCDD2.toInt())
            setPadding(0, 16, 0, 0)
        }

        val loginBtn = Button(this).apply {
            text = "Sign In"
            setOnClickListener {
                val e = email.text.toString().trim()
                val p = password.text.toString()
                if (e.isEmpty() || p.isEmpty()) {
                    Toast.makeText(this@LoginActivity, "Email and password required", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                signIn(e, p, progress, status, this)
            }
        }

        val note = TextView(this).apply {
            text = "\nAuth is handled by Cloudflare Workers.\nNo secrets are stored in the APK."
            textSize = 12f
            setTextColor(MaterialPalette.outline(this@LoginActivity))
            setPadding(0, 48, 0, 0)
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(email)
        root.addView(password)
        root.addView(loginBtn)
        root.addView(progress)
        root.addView(status)
        root.addView(note)
        setContentView(root)
    }

    private fun signIn(
        email: String,
        password: String,
        progress: ProgressBar,
        status: TextView,
        btn: Button
    ) {
        progress.visibility = View.VISIBLE
        btn.isEnabled = false
        status.text = ""

        lifecycleScope.launch {
            val result = CloudflareAuth.login(email, password)
            progress.visibility = View.GONE
            btn.isEnabled = true

            if (result.success && !result.sessionToken.isNullOrBlank()) {
                SessionStore.saveSession(
                    context = this@LoginActivity,
                    sessionToken = result.sessionToken,
                    userId = result.userId ?: email,
                    email = result.email ?: email,
                    expiresInSeconds = result.expiresIn,
                    orgId = result.orgId
                )
                proceedToBrowser()
            } else {
                status.text = result.error ?: "Login failed"
                Toast.makeText(
                    this@LoginActivity,
                    result.error ?: "Login failed",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun proceedToBrowser() {
        try {
            AegisApplication.instance.applicationScope.launch {
                try {
                    AegisApplication.instance.policyManager.refreshPolicies()
                    AegisApplication.instance.policyManager.startCloudPolicyListener()
                    AegisApplication.instance.pacProxyManager.applyDefaultPac()
                } catch (e: Exception) {
                    // never block navigation on policy refresh
                }
            }
        } catch (_: Exception) {
        }
        startActivity(Intent(this, BrowserActivity::class.java))
        finish()
    }
}
