# Aegis Browser – Architecture & Roadmap

## Vision
A Chromium-based secure browser for Android (with future desktop parity) that:
- Requires login (Firebase + Cloudflare Worker) – never optional
- Is managed by parents / MDM with remote lockdown
- Uses a PAC-based application proxy firewall (PAC supplied by you later)
- Supports controlled extensions / “Aegis Modules” that parents can push remotely
- Is significantly harder for children to bypass than Google Family Link
- Keeps all secrets in the cloud (Cloudflare Worker), not in the APK

## Dual-Engine Design (Implemented)

Both engines live in **one APK**, side-by-side (not merged into a single engine):

- **ChromiumEngine** → AndroidX WebView (Blink)
- **GeckoEngine** → Mozilla GeckoView (Gecko)

`EngineSelector` chooses which engine handles a given URL:

1. Policy override (force Chromium or force Gecko) – ready for remote config later
2. Simple domain preference lists (Mozilla-related → Gecko, Google properties → Chromium, etc.)
3. Default → Chromium (broadest real-world compatibility)

Only one engine is active per page load. A rotate button lets the user (or later a parent policy) switch.  
This gives the “double-edged sword” – maximum website reach in a single installable app – while every parental control, PAC rule, lockdown, and mandatory login applies equally to both engines.

### Critical Reality Checks

### 1. “Completely unjailbreakable by kids”
Absolute claims are false. Determined users (or kids with access to another device, a friend’s phone, or physical access) can always find paths.  

**What we actually do (defense-in-depth):**
- Mandatory Firebase login + short-lived tokens from Cloudflare Worker
- MDM Device Owner / Profile Owner + Lock Task / Kiosk mode
- Forced PAC / system proxy when Device Owner
- Remote lockdown via FCM (parent or MDM can instantly blank the browser)
- No sideloading of other browsers (enforced by MDM)
- Network-level controls (the PAC + optional always-on VPN)
- Encrypted local policy store
- No developer options / USB debugging when fully managed

This makes bypass *extremely high friction* for a normal child.

### 3. Full Chromium Android source build
Cloning and building Chromium for Android is a large-scale engineering project:
- Source tree is tens of GB
- Requires depot_tools, specific Linux environment, many hours of compile time per architecture (arm64)
- Needs significant CI infrastructure (self-hosted runners with high RAM/CPU/disk)

**Current scaffold:** AndroidX WebView (which is Chromium under the hood on modern Android) + full policy/auth/proxy/MDM surface.  
This gives us a working, installable APK today. Later we replace the WebView with a real Chromium Android fork while keeping the same policy, auth, PAC, and extension APIs.

### 4. Extension support + parental remote install
- Short term: “Aegis Modules” – controlled content scripts / JS modules that parents approve and push via the management console / FCM.
- Medium term (full Chromium fork): Native Manifest V3 extension support + a private Aegis Extension Store. Parents can force-install or allowlist extensions for a child’s account. Children cannot install anything themselves.

## High-Level Components

```
┌─────────────────────────────────────────────────────────────┐
│                     Parent / MDM Console                    │
│  (Web app – Firebase + Cloudflare Worker)                   │
│  - Set policies, PAC, time limits, whitelist                │
│  - Remote lockdown / clear data / push extension            │
│  - View activity (if enabled)                               │
└──────────────────────────┬──────────────────────────────────┘
                           │ FCM + HTTPS
┌──────────────────────────▼──────────────────────────────────┐
│              Cloudflare Worker (secrets live here)          │
│  - /auth/login → short-lived tokens                         │
│  - /policy/* → signed policy updates                        │
│  - /fcm/send → lockdown & extension commands                │
│  - Never ships private keys to the device                   │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                 Aegis Browser (Android APK)                 │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────────┐   │
│  │ Firebase    │  │ PolicyManager│  │ PacProxyManager  │   │
│  │ Auth (req.) │  │ MDM + FCM +  │  │ (Cloudflare      │   │
│  │             │  │ Firestore    │  │  Gateway PAC)    │   │
│  └─────────────┘  └──────────────┘  └──────────────────┘   │
│  ┌─────────────┐  ┌──────────────┐                         │
│  │ Dual Engine │  │ Extension    │                         │
│  │ Chromium +  │  │ Manager      │                         │
│  │ Gecko       │  │ (Aegis Mods) │                         │
│  └─────────────┘  └──────────────┘                         │
└─────────────────────────────────────────────────────────────┘
```

## PAC (Proxy Auto-Config)
Default is the Cloudflare Gateway PAC:

`https://pac.cloudflare-gateway.com/e2950b77bc288e9366bebbcccf1c25a8/WIyYdBdISzKaPnuqVMMyqg`

- Local / private → DIRECT  
- Auth & Cloudflare infrastructure (Firebase login, Workers, Access, etc.) → DIRECT  
- All other HTTP(S) → `HTTPS dna6n8n2u0.proxy.cloudflare-gateway.com:443`

Can still be overridden via MDM (`pac_content` / `pac_url`) or Firebase policy.

## Login is Mandatory
There is no guest mode. BrowserActivity immediately redirects to LoginActivity if `FirebaseAuth.currentUser == null`.

## Future GitHub + Actions Path
1. Create a private GitHub repository for this project.
2. Add the Android project + Cloudflare Worker.
3. GitHub Actions workflow:
   - Build the current Android APK (assembleRelease) on every push / tag.
   - Later: add a self-hosted runner capable of building a full Chromium Android target (this is expensive and complex).
4. Signed APK can be uploaded to Google Play (or distributed via Managed Google Play for enterprise/parental MDM).

## Terms of Service / Privacy Policy
Will be written later and must be accepted at first login. Required for Google Play and for legal clarity with parents and children.

## Immediate Next Steps (this scaffold)
- [x] Mandatory Firebase login gate
- [x] Policy engine (MDM restrictions + Firebase + remote FCM lockdown)
- [x] PAC manager with Cloudflare Gateway PAC embedded + URL default
- [x] Cloudflare Worker skeleton (auth + lockdown command)
- [x] Basic WebView browser with URL/time/category gating
- [ ] Real layout XML + ViewBinding
- [ ] Full Google Sign-In / SSO through the Worker
- [ ] Aegis Module (extension) download & injection system
- [ ] Parent management web console
- [ ] Device Owner setup documentation for strong lockdown
- [ ] Replace WebView with a real Chromium Android binary (long-term)

## Why this can beat Family Link
- True Chromium extension surface (future)
- Parent-controlled private extension store
- Arbitrary PAC / secure proxy of the parent’s choice
- Remote instant lockdown
- Multi-tenant / enterprise MDM ready
- Secrets never in the binary
- Open path to add more child products later under the same login & policy system
