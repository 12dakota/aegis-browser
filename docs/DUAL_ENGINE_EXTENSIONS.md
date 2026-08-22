# Dual engine + dual extension support (product plan)

## Vision

One browser where a user can live in **both** Chrome-class and Firefox-class
ecosystems:

- Sites that prefer Blink → Chromium engine
- Sites that prefer Gecko → Gecko engine
- **Chrome Web Store extensions** on Chromium tabs
- **AMO / Firefox add-ons** on Gecko tabs
- AUTO mode picks the engine; user can force either

## What works today in Aegis

| Capability | Status |
|------------|--------|
| GeckoView **in the APK** | Yes |
| System WebView Chromium for pages | Yes |
| AUTO switch C ↔ G | Yes |
| AMO WebExtensions on Gecko | Yes (install/list/options) |
| Chrome Web Store extensions on C | **No** — needs full Chrome embedder |
| Bundled private Blink binary | Scripts ready; needs builder machine |

## Why Chrome extensions ≠ “add Blink”

Blink renders pages. Chrome extensions need:

- Extension process / service worker host
- `chrome.*` API surface
- Manifest V2/V3 handling
- Web Store (or sideload) install pipeline
- Permission UI and update checks

That lives in the **Chrome embedder** (`//chrome`), not in Content Shell alone.
Content Shell = good for **pinned Blink rendering**.  
Full extension parity = build/customize something closer to `chrome_public_apk`
or maintain a long-term fork of an embeddable Chrome layer.

## Phased plan

### Phase 1 — Dual rendering (current → near term)
1. Keep GeckoView + System WebView
2. AUTO engine selection (done)
3. Run `scripts/chromium/*` on a **self-hosted** builder
4. Package Content Shell natives into APK (`USE_BUNDLED_CHROMIUM`)
5. Point ChromiumEngine at bundled shell when present (JNI bridge)

### Phase 2 — Gecko extensions polish
1. Reliable AMO install + options pages (in progress)
2. Extension browser actions / popups
3. Parent allowlist + adult unrestricted

### Phase 3 — Chromium extension host
1. Build Chrome Android targets or adopt an embeddable Chrome layer
2. Sideload CRX / Web Store install flow
3. Map extension list UI to both stores
4. Per-tab: extensions only run on matching engine

### Phase 4 — Product polish
1. “Chrome mode” vs “Firefox mode” presets
2. Shared bookmarks/history across engines
3. Single account (Cloudflare/Firebase) above both

## APK size

Expect **500 MB – 1.5 GB+** with GeckoView + bundled Chromium natives.
ABI split (`arm64-v8a` only) is recommended for production.

## Builder one-liner (after disk/RAM ready)

```bash
export CHROMIUM_ROOT=$HOME/chromium
bash scripts/chromium/01-fetch-chromium.sh
bash scripts/chromium/02-build-content-shell.sh
bash scripts/chromium/03-package-into-aegis.sh
./gradlew assembleAdultDebug -Paegis.bundledChromium=true
```
