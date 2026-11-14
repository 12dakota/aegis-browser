# Provonlong Web Browser (Android)

**Secure multi-engine browser (Servo + Gecko + WebKit + optional custom) for family and adult builds.**  
Chrome-like shell is our own code. Policy, PAC, auth, downloads, and lockdown are Provonlong-owned.

## Builds

| Flavor | Package id | Login |
|--------|------------|-------|
| Family | `com.aegis.browser` | Required (Cloudflare Worker) |
| Adult | `com.aegis.browser.adult` | Optional |

Launcher name on both: **Provonlong Web Browser**.

## Engines

- **S** — Servo (bundled AAR from servo-nightly-builds, experimental)
- **G** — GeckoView (Mozilla, WebExtensions via AMO)
- **W** — WPE WebKit (bundled AAR)
- **X** — Custom (user-imported local APK/DEX engine via Settings)

**No Blink / Chromium.**

## Features

- Material 3 Expressive (dynamic color)
- Chrome-like omnibox, bottom bar, tab switcher
- In-app downloads with background transfer
- Settings for engine preference + custom engine import
- MDM / Device Owner PAC lockdown (family)
- Open browsing (login removed); policy/PAC optional

## Build

Place `servo-aarch64-android.aar` in `app/libs/` and uncomment the dependency in `app/build.gradle.kts`.

```bash
./gradlew assembleFamilyDebug
./gradlew assembleAdultDebug
```

CI: `.github/workflows/android-family.yml` and `android-adult.yml`.