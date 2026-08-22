# Chromium / Blink in Aegis (size-constrained)

## Account / size rules

- **Do not** fetch the Chromium source tree (~50–100+ GB).
- **Do not** commit multi-hundred-MB engine blobs unless explicitly approved.
- Target: Chromium path adds **~0 MB** to the APK (same idea as “engine only,” not source).

## How Gecko was brought in (the model we copy)

Mozilla publishes a **prebuilt** GeckoView AAR. Gradle downloads it; natives land in the APK.
We never clone Firefox source into this repo.

## How Chromium is brought in (same idea)

Google publishes Chromium for Android as **System WebView** (and Trichrome).
That **is** the Blink/Chromium engine, prebuilt and updated via Play Store.

| Engine | Prebuilt provider | Extra size in Aegis APK |
|--------|-------------------|-------------------------|
| Gecko | GeckoView AAR (Maven) | ~150–250 MB (natives) |
| Chromium/Blink | System WebView | **~0 MB** (uses OS) |

`ChromiumEngine` already talks to that engine via `android.webkit.WebView` + AndroidX WebKit.

## Why not “extract 100 MB from a Chrome APK”?

- Chrome / WebView APKs are often **~90–250 MB** per ABI; unpacking does not give a clean embed API.
- There is **no official** “Blink.aar” like GeckoView.
- Archived third-party AARs (e.g. old chromium-aw ~**181 MB**, Chromium 93, unmaintained) exceed a 100 MB budget and are years out of date.

## Full Chrome extensions

Still require a Chrome-level host, not a 100 MB slice of an APK.
Gecko + AMO remains the supported extension path in Aegis.

## Scripts under scripts/chromium/

Optional **only** on a dedicated builder with 100+ GB disk. **Not** for the GitHub account quota.
Default product path: System WebView.
