# Bundled Chromium (Blink) for Aegis

Goal: ship **GeckoView + a private Chromium/Blink build** in one APK so AUTO mode
can switch between two real engines without relying only on System WebView.

## Why this is a "huge effort"

Google does not publish Chromium as a Maven AAR for third-party apps.
You must:

1. Checkout Chromium (`depot_tools` + `fetch android`) — **~50–100+ GB**
2. Build `content_shell_apk` or a WebView instrumentation target — **2–8+ hours**
3. Package native libs + assets into Aegis (`jniLibs`, `assets`, `noCompress`)
4. Bridge Java/Kotlin UI to the content API (or ship Content Shell as a library)

## Hardware requirements (builder machine)

| Resource | Minimum | Recommended |
|----------|---------|-------------|
| OS | Linux x86_64 | Ubuntu 22.04+ |
| RAM | 16 GB | 32–64 GB |
| Disk | 100 GB free | 200+ GB SSD |
| CPU | 8 cores | 16+ cores |
| Network | Stable | Fast (first sync is huge) |

**GitHub-hosted runners cannot do this reliably.** Use a **self-hosted** runner
or a cloud VM (e.g. 16 vCPU / 64 GB / 300 GB disk).

## Official build targets

From Chromium docs:

```bash
# Content Shell (simplest embedder sample)
autoninja -C out/AegisArm64 content_shell_apk

# Full browser (larger)
autoninja -C out/AegisArm64 chrome_public_apk
```

Reference: https://chromium.googlesource.com/chromium/src/+/HEAD/docs/android_build_instructions.md

## Aegis integration path

```
scripts/chromium/01-fetch-chromium.sh   # depot_tools + gclient sync
scripts/chromium/02-build-content-shell.sh
scripts/chromium/03-package-into-aegis.sh  # → app/src/main/jniLibs + assets
```

Then build Aegis with:

```bash
./gradlew assembleAdultDebug -Paegis.bundledChromium=true
```

`ChromiumEngine` uses System WebView when bundled libs are absent, and logs
`BundledContentShellBackend` when `USE_BUNDLED_CHROMIUM` is true and natives exist.

## Extension reality

- **Gecko + AMO** = works today (in APK)
- **Chrome Web Store extensions** = need Chrome-level extension system, not only Blink
- Bundled Content Shell improves **engine independence / version pin**, not automatic MV3 store support

## Status in this repo

| Piece | Status |
|-------|--------|
| GeckoView in APK | Done |
| AUTO C ↔ G | Done |
| Chromium fetch/build/package scripts | Added |
| Self-hosted CI workflow | Scaffold |
| Full content JNI bridge | Phase 2 (after first successful shell build) |
