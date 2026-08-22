# Chromium engine in Aegis

## What ships today

| Engine | Implementation | Updates |
|--------|----------------|---------|
| **Chromium (C)** | Android **System WebView** — Google’s Chromium/Blink via AndroidX `webkit` | Play Store / OS WebView updates |
| **Gecko (G)** | **GeckoView** AAR (bundled in APK) | Our `update-engines` workflow |

There is **no official Google Maven artifact** equivalent to GeckoView that embeds a full Chromium browser binary inside a third-party app. Chromium project maintainers document that Content Shell / `//content` integration is a from-source build, not a drop-in library. CEF does not support Android.

## Why System WebView is still “Google Chromium”

On modern Android, WebView **is** Chromium (same lineage as Chrome).  
`WebViewCompat.getCurrentWebViewPackage()` reports the provider (often `com.google.android.webview` or Trichrome).

## Full bundled Chromium (optional, heavy)

To ship a **pinned** Chromium revision inside the APK you must:

1. Check out [Chromium for Android](https://chromium.googlesource.com/chromium/src/+/HEAD/docs/android_build_instructions.md)
2. Build `content_shell_apk` or a custom `//content` embedder (many hours, tens of GB)
3. Rewrite Aegis’s `ChromiumEngine` against that embedder’s API (not `android.webkit.WebView`)

That path is **not** wired into CI today because it cannot run on standard GitHub-hosted runners in a reasonable time/disk budget.

## Practical dual-engine stance

- **C** = latest Google Chromium available on the device (WebView)
- **G** = Firefox Gecko pinned in the APK (extensions, independent engine)

Use **G** for AMO extensions; use **C** for maximum Chrome-site compatibility and size efficiency.
