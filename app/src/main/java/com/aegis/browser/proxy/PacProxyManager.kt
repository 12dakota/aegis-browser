package com.aegis.browser.proxy

import android.content.Context
import android.util.Log
import android.webkit.WebView
import com.aegis.browser.AegisApplication
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages the application-level proxy firewall using PAC (Proxy Auto-Config).
 *
 * Default PAC is the Cloudflare Gateway PAC provided for this project:
 *   https://pac.cloudflare-gateway.com/e2950b77bc288e9366bebbcccf1c25a8/WIyYdBdISzKaPnuqVMMyqg
 *
 * Key behaviour of the PAC:
 * - Local / private networks → DIRECT
 * - Non-HTTP(S) → DIRECT
 * - Auth & Cloudflare infrastructure (Firebase login, Workers, Access, Gateway) → DIRECT
 * - Everything else → HTTPS dna6n8n2u0.proxy.cloudflare-gateway.com:443
 *
 * Full PAC evaluation (isInNet, dnsDomainIs, etc.) is best performed by the system
 * when the app is Device Owner and MDM sets the global PAC URL. The in-app evaluator
 * is a best-effort fallback that understands the main proxy endpoint.
 */
class PacProxyManager(private val context: Context) {

    private val currentPacContent = AtomicReference<String>(DEFAULT_PAC)
    private val currentPacUrl = AtomicReference<String>(DEFAULT_PAC_URL)
    private val currentProxyHost = AtomicReference<String?>(null)
    private val currentProxyPort = AtomicReference<Int?>(null)
    private val currentProxyScheme = AtomicReference<String>("HTTPS")

    /**
     * Apply the default / configured PAC.
     * Call this after PolicyManager.refreshPolicies().
     */
    fun applyDefaultPac() {
        val policy = AegisApplication.instance.policyManager.currentPolicy

        val pac = when {
            policy.pacContent.isNotBlank() -> {
                currentPacUrl.set("")
                policy.pacContent
            }
            policy.pacUrl.isNotBlank() -> {
                currentPacUrl.set(policy.pacUrl)
                // Prefer the known Cloudflare Gateway PAC content when the URL matches
                if (policy.pacUrl.contains("pac.cloudflare-gateway.com")) {
                    DEFAULT_PAC
                } else {
                    // Production: download + cache the remote PAC
                    Log.i(TAG, "Remote PAC URL set (${policy.pacUrl}) – using embedded Cloudflare Gateway PAC as baseline")
                    DEFAULT_PAC
                }
            }
            else -> {
                currentPacUrl.set(DEFAULT_PAC_URL)
                DEFAULT_PAC
            }
        }

        currentPacContent.set(pac)
        // Warm-up with a non-auth URL so the Gateway proxy is selected
        evaluateAndSetProxy("https://www.example.com")

        Log.i(TAG, "PAC applied. Active = ${getActiveProxyDescription()}")
    }

    /**
     * Best-effort evaluator.
     * Understands the Cloudflare Gateway PAC shape:
     *   return "HTTPS host:port";
     *   return "DIRECT";
     * Full isInNet / dnsDomainIs logic requires a real JS PAC runtime or system-level PAC.
     */
    fun findProxyForUrl(url: String, host: String = ""): String {
        val pac = currentPacContent.get()
        val lowerHost = host.ifBlank {
            try {
                java.net.URI(url).host?.lowercase() ?: ""
            } catch (_: Exception) {
                ""
            }
        }

        // Quick bypass for hosts that the real PAC keeps DIRECT (auth path)
        val directHosts = listOf(
            "login-cloudflare-6127a.web.app",
            "login-cloudflare-6127a.firebaseapp.com",
            "firebase-oidc-token.dakdra03.workers.dev",
            "voidlight.cloudflareaccess.com"
        )
        if (directHosts.any { lowerHost == it || lowerHost.endsWith(".$it") } ||
            lowerHost.endsWith(".firebaseapp.com") ||
            lowerHost.endsWith(".googleapis.com") ||
            lowerHost.endsWith(".gstatic.com") ||
            lowerHost.endsWith(".cloudflareaccess.com") ||
            lowerHost.endsWith(".cloudflare-gateway.com") ||
            lowerHost.endsWith(".cloudflare.com")
        ) {
            return "DIRECT"
        }

        // Local / private – treat as DIRECT
        if (lowerHost.isEmpty() ||
            lowerHost == "localhost" ||
            lowerHost.endsWith(".local") ||
            lowerHost.startsWith("10.") ||
            lowerHost.startsWith("192.168.") ||
            lowerHost.startsWith("127.") ||
            lowerHost.startsWith("172.16.") || lowerHost.startsWith("172.17.") ||
            lowerHost.startsWith("172.18.") || lowerHost.startsWith("172.19.") ||
            lowerHost.startsWith("172.2") || lowerHost.startsWith("172.3")
        ) {
            return "DIRECT"
        }

        // Default path from the Cloudflare Gateway PAC
        val httpsMatch = Regex(
            """return\s+"HTTPS\s+([^"]+)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
        ).find(pac)
        if (httpsMatch != null) {
            return "HTTPS ${httpsMatch.groupValues[1]}"
        }

        val proxyMatch = Regex(
            """return\s+"PROXY\s+([^"]+)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
        ).find(pac)
        if (proxyMatch != null) {
            return "PROXY ${proxyMatch.groupValues[1]}"
        }

        return "DIRECT"
    }

    /**
     * Evaluate PAC for a URL and update the active proxy settings.
     */
    fun evaluateAndSetProxy(url: String) {
        val result = findProxyForUrl(url)
        when {
            result.startsWith("HTTPS ", ignoreCase = true) -> {
                val target = result.removePrefix("HTTPS ").removePrefix("https ").trim()
                val parts = target.split(":")
                if (parts.size >= 2) {
                    currentProxyHost.set(parts[0])
                    currentProxyPort.set(parts[1].toIntOrNull() ?: 443)
                    currentProxyScheme.set("HTTPS")
                    return
                }
            }
            result.startsWith("PROXY ", ignoreCase = true) -> {
                val target = result.removePrefix("PROXY ").removePrefix("proxy ").trim()
                val parts = target.split(":")
                if (parts.size >= 2) {
                    currentProxyHost.set(parts[0])
                    currentProxyPort.set(parts[1].toIntOrNull() ?: 8080)
                    currentProxyScheme.set("PROXY")
                    return
                }
            }
        }
        currentProxyHost.set(null)
        currentProxyPort.set(null)
        currentProxyScheme.set("DIRECT")
    }

    /**
     * Best-effort proxy configuration for WebView.
     * Full enforcement is achieved when MDM sets the global PAC URL
     * (DevicePolicyManager) or when a local VpnService / proxy is used.
     */
    fun configureWebViewProxy(webView: WebView) {
        val host = currentProxyHost.get()
        val port = currentProxyPort.get()
        if (host != null && port != null) {
            try {
                System.setProperty("http.proxyHost", host)
                System.setProperty("http.proxyPort", port.toString())
                System.setProperty("https.proxyHost", host)
                System.setProperty("https.proxyPort", port.toString())
                Log.i(TAG, "System proxy properties set to $host:$port")
            } catch (e: Exception) {
                Log.w(TAG, "Could not set system proxy properties", e)
            }
        } else {
            System.clearProperty("http.proxyHost")
            System.clearProperty("http.proxyPort")
            System.clearProperty("https.proxyHost")
            System.clearProperty("https.proxyPort")
        }
    }

    fun getActiveProxyDescription(): String {
        val host = currentProxyHost.get()
        val port = currentProxyPort.get()
        val scheme = currentProxyScheme.get()
        return if (host != null && port != null) {
            "$scheme $host:$port (Cloudflare Gateway PAC)"
        } else {
            "DIRECT (Cloudflare Gateway PAC)"
        }
    }

    fun getPacUrl(): String = currentPacUrl.get()

    companion object {
        private const val TAG = "AegisPAC"

        /** Official Cloudflare Gateway PAC URL for this deployment */
        const val DEFAULT_PAC_URL =
            "https://pac.cloudflare-gateway.com/e2950b77bc288e9366bebbcccf1c25a8/WIyYdBdISzKaPnuqVMMyqg"

        /**
         * Exact PAC content from the Cloudflare Gateway endpoint above.
         * Auth / Cloudflare / Firebase hosts stay DIRECT so login never loops.
         * All other HTTP(S) traffic is sent to the Authorization Proxy Endpoint.
         */
        const val DEFAULT_PAC = """
function FindProxyForURL(url, host) {
    host = host.toLowerCase();

    // 1. Local / private traffic → DIRECT
    if (isPlainHostName(host) ||
        dnsDomainIs(host, ".local") ||
        isInNet(dnsResolve(host), "10.0.0.0", "255.0.0.0") ||
        isInNet(dnsResolve(host), "172.16.0.0", "255.240.0.0") ||
        isInNet(dnsResolve(host), "192.168.0.0", "255.255.0.0") ||
        isInNet(dnsResolve(host), "127.0.0.0", "255.0.0.0") ||
        isInNet(dnsResolve(host), "169.254.0.0", "255.255.0.0") ||
        isInNet(dnsResolve(host), "100.64.0.0", "255.192.0.0")) {
        return "DIRECT";
    }

    // 2. Non-HTTP/HTTPS → DIRECT
    if (url.substring(0, 5) !== "http:" && url.substring(0, 6) !== "https:") {
        return "DIRECT";
    }

    // 3. Authentication & Cloudflare infrastructure → ALWAYS DIRECT
    //    (critical so the login page and token exchange can complete)
    if (
        // Your Firebase login page
        host === "login-cloudflare-6127a.web.app" ||
        host === "login-cloudflare-6127a.firebaseapp.com" ||

        // Token endpoint Worker
        host === "firebase-oidc-token.dakdra03.workers.dev" ||

        // Firebase / Google
        dnsDomainIs(host, ".firebaseapp.com") ||
        dnsDomainIs(host, ".googleapis.com") ||
        dnsDomainIs(host, ".gstatic.com") ||

        // Cloudflare Access + Gateway
        dnsDomainIs(host, ".cloudflareaccess.com") ||
        dnsDomainIs(host, ".cloudflare-gateway.com") ||
        dnsDomainIs(host, ".cloudflare.com") ||
        dnsDomainIs(host, ".cloudflarestatus.com") ||

        // Your specific Access team domain
        host === "voidlight.cloudflareaccess.com"
    ) {
        return "DIRECT";
    }

    // 4. Everything else goes through the Authorization Proxy Endpoint
    return "HTTPS dna6n8n2u0.proxy.cloudflare-gateway.com:443";
}
"""
    }
}
