package com.aegis.browser.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aegis.browser.AegisApplication
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * Mandatory login. No anonymous or skip mode.
 * In production this will call the Cloudflare Worker to exchange credentials
 * for short-lived tokens so that long-lived secrets never live in the APK.
 */
class LoginActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (auth.currentUser != null) {
            proceedToBrowser()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 120, 48, 48)
            setBackgroundColor(0xFF0D47A1.toInt())
        }

        val title = TextView(this).apply {
            text = "Aegis Browser"
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 32)
        }

        val subtitle = TextView(this).apply {
            text = "Secure parental & enterprise browser\nLogin is required"
            textSize = 14f
            setTextColor(0xFFBBDEFB.toInt())
            setPadding(0, 0, 0, 48)
        }

        val email = EditText(this).apply {
            hint = "Email"
            setTextColor(0xFF000000.toInt())
            setHintTextColor(0xFF666666.toInt())
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding(24, 24, 24, 24)
        }

        val password = EditText(this).apply {
            hint = "Password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(0xFF000000.toInt())
            setHintTextColor(0xFF666666.toInt())
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding(24, 24, 24, 24)
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
                signIn(e, p)
            }
        }

        val note = TextView(this).apply {
            text = "\nAll authentication secrets are held in the cloud\n(Cloudflare Worker + Firebase).\nNothing sensitive is stored in the app."
            textSize = 12f
            setTextColor(0xFF90CAF9.toInt())
            setPadding(0, 48, 0, 0)
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(email)
        root.addView(password)
        root.addView(loginBtn)
        root.addView(note)
        setContentView(root)
    }

    private fun signIn(email: String, password: String) {
        // Production path:
        // 1. Call Cloudflare Worker /auth/login with email+password (or OAuth code)
        // 2. Worker validates, talks to Firebase Admin, returns short-lived custom token
        // 3. Client does signInWithCustomToken(token)
        // For scaffold we use direct Firebase Auth (still protected by Firebase security rules)
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                proceedToBrowser()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Login failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun proceedToBrowser() {
        AegisApplication.instance.applicationScope.launch {
            AegisApplication.instance.policyManager.refreshPolicies()
            AegisApplication.instance.policyManager.startFirebaseListener()
            AegisApplication.instance.pacProxyManager.applyDefaultPac()
        }
        startActivity(Intent(this, BrowserActivity::class.java))
        finish()
    }
}
