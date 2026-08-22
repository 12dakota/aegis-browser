# Provonlong Web Browser (Android)

**Secure dual-engine (Chromium/Blink + Gecko + optional WPE WebKit) browser for family and adult builds.**  
Chrome-like shell is our own code. Policy, PAC, auth, downloads, and lockdown are Provonlong-owned.

## Builds

| Flavor | Package id | Login |
|--------|------------|-------|
| Family | `com.aegis.browser` | Required (Cloudflare Worker) |
| Adult | `com.aegis.browser.adult` | Optional |

Launcher name on both: **Provonlong Web Browser**.

## Engines

- **C** — System WebView (Blink / Chromium)
- **G** — GeckoView (extensions via AMO)
- **W** — WPE WebKit (bundled AAR)

## Features

- Material 3 Expressive (dynamic color)
- In-app downloads (not system DownloadManager) with background transfer
- Settings for unmanaged features
- MDM / Device Owner PAC lockdown (family)
- Cloudflare Worker auth + policy

## Build

```bash
./gradlew assembleFamilyDebug
./gradlew assembleAdultDebug
```

CI: `.github/workflows/android-family.yml` and `android-adult.yml`.
