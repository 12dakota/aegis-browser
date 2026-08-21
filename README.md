# Aegis Browser (Android)

**Secure, mandatory-login, dual-engine (Chromium + Gecko) parental & enterprise browser. Chrome-like shell is our own code; engines, policy, PAC, auth, and lockdown are Aegis-owned.**

## Key Design Decisions

| Topic                        | Decision |
|-----------------------------|----------|
| Rendering engines           | **Dual-engine in one APK**: Chromium (WebView/Blink) + Gecko (GeckoView). Auto-selects per URL; manual switch available. Engines are side-by-side, not merged. |
| Login                       | **Required**. Firebase + Cloudflare Worker (secrets stay in cloud) |
| Proxy / Firewall            | PAC-based. Default is a placeholder – **you supply the real PAC later** |
| Lockdown                    | MDM App Config + FCM remote command + optional Device Owner |
| Extensions                  | Controlled “Aegis Modules” (parent-gated only) |
| Anti-bypass                 | Defense-in-depth (MDM + kiosk + PAC + required auth + remote kill) |
| Distribution                | GitHub Actions → APK → Managed Google Play / Play Store |

See `docs/ARCHITECTURE.md` for the full rationale and roadmap.

## Project Structure

```
aegis-browser-android/
├── app/                          # Android application
│   └── src/main/java/com/aegis/browser/
│       ├── AegisApplication.kt
│       ├── engine/                   ← Dual engine (Chromium + Gecko)
│       │   ├── BrowserEngine.kt
│       │   ├── ChromiumEngine.kt
│       │   ├── GeckoEngine.kt
│       │   └── EngineSelector.kt
│       ├── policy/PolicyManager.kt
│       ├── proxy/PacProxyManager.kt  ← ready for your PAC
│       ├── mdm/                      ← FCM lockdown + Device Admin
│       └── ui/                       ← Login (mandatory) + Browser
├── cloudflare-worker/            # Secrets & auth live here
│   └── src/index.ts
├── docs/ARCHITECTURE.md
└── README.md
```

## Quick Start (Android Studio)

1. Open the project in Android Studio (Hedgehog or newer recommended).
2. Add your `google-services.json` (from Firebase console) into `app/`.
3. Create a Firebase project with Authentication (Email/Password) and Firestore enabled.
4. (Optional) Deploy the Cloudflare Worker and set the secrets.
5. Run on an arm64 emulator or device (`./gradlew installDebug`).

## Cloudflare Gateway PAC (default)

The browser ships with your Cloudflare Gateway PAC as the default:

**PAC URL:** `https://pac.cloudflare-gateway.com/e2950b77bc288e9366bebbcccf1c25a8/WIyYdBdISzKaPnuqVMMyqg`

Behaviour:
- Local / private networks → DIRECT  
- Auth paths (Firebase login, your Workers, Cloudflare Access, Google/Firebase domains, `voidlight.cloudflareaccess.com`) → DIRECT  
- Everything else → `HTTPS dna6n8n2u0.proxy.cloudflare-gateway.com:443`

You can still override via MDM restriction `pac_content` / `pac_url` or Firebase policy at any time.

## Remote Lockdown

Parents / MDM can send an FCM data message:

```json
{
  "command": "lockdown",
  "enable": "true",
  "reason": "Homework time",
  "token": "<enrollment token>"
}
```

The browser will immediately enter lockdown (UI reduced, navigation blocked, about:blank).

## Next Engineering Steps

1. Replace programmatic layouts with proper XML + ViewBinding.
2. Implement full Google / SSO login that goes through the Cloudflare Worker.
3. Build the parent management console (web).
4. Implement Aegis Module download + injection.
5. Document Device Owner enrollment for maximum lockdown.
6. Long-term: stand up a Chromium Android build pipeline and replace WebView.

## Legal

A Terms of Service and Privacy Policy will be required before any public or Play Store release. They will be written and presented at first login.
