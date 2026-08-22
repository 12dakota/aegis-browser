# Dual engine (size-aware)

## Engines in one app

1. **Gecko** — GeckoView AAR in the APK (Firefox-class, AMO extensions).
2. **Chromium/Blink** — System WebView on device (Chrome-class rendering, ~0 MB added).

AUTO mode switches which engine paints a tab. They are not mixed in one view.

## Extensions

| Store / type | Engine | Status |
|--------------|--------|--------|
| AMO / Firefox WebExtensions | Gecko | Supported in Provonlong |
| Chrome Web Store | Would need full Chrome embedder | Not available in a ~100 MB add |

## Size budget

- GeckoView dominates APK size (expected).
- Chromium path does **not** add another full engine binary by default.
- No Chromium source checkout in this repo or CI account.
