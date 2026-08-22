# Automatic Blink ↔ Gecko switching

## User preference

| Mode | Behavior |
|------|----------|
| **AUTO** | Each navigation picks engine from URL heuristics + learned overrides |
| **FORCE_CHROMIUM** | Always Blink/Chromium path |
| **FORCE_GECKO** | Always Gecko |

## AUTO rules (order)

1. Forced preference if not AUTO
2. `moz-extension://` → Gecko
3. AMO / `.xpi` → Gecko
4. Chrome Web Store hosts → Chromium path
5. Learned host override (user tapped engine badge)
6. Mozilla-related hosts → Gecko
7. Google / major Chromium-oriented hosts → Chromium
8. Default → Chromium (Blink)

## Why Chromium path is still System WebView

Blink is not published as an embeddable AAR. The CHROMIUM engine uses Google’s Chromium
on the device. Extensions that need a full Chrome extension process model require a
future bundled Content Shell — tracked as `ChromiumBackend` / `supportsChromeExtensions`.

**Today’s extension support is Gecko + AMO**, selected automatically for add-on sites.
