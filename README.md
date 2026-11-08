# Provonlong Web Browser (Android)

**Secure multi-engine (Servo + Gecko + optional WPE WebKit) browser for family and adult builds.**  
Chrome-like shell is our own code. Policy, PAC, auth, downloads, and lockdown are Provonlong-owned.

## Builds

| Flavor | Package id | Login |
|--------|------------|-------|
| Family | `com.aegis.browser` | Required (Cloudflare Worker) |
| Adult | `com.aegis.browser.adult` | Optional |

Launcher name on both: **Provonlong Web Browser**.

## Engines

- **S** — Servo (Rust, replaces previous Blink/WebView path). Requires `app/libs/servo-aarch64-android.aar` from [servo-nightly-builds](https://github.com/servo/servo-nightly-builds/releases/latest).
- **G** — GeckoView (extensions via AMO)
- **W** — WPE WebKit (bundled AAR)

Default AUTO selection now prefers Servo for general web.

## Features

- Material 3 Expressive (dynamic color)
- In-app downloads (not system DownloadManager) with background transfer
- Settings for unmanaged features
- MDM / Device Owner PAC lockdown (family)
- Cloudflare Worker auth + policy

## Build

1. Download the latest `servo-aarch64-android.aar` into `app/libs/`.
2. ```bash
   ./gradlew assembleFamilyDebug
   ./gradlew assembleAdultDebug
   ```

CI: `.github/workflows/android-family.yml` and `android-adult.yml`.

## Notes

Servo is experimental. Site compatibility is lower than Blink today; Gecko remains available as a fallback via engine selector or domain preference.
