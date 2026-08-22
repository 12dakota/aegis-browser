# WPE WebKit (third engine)

Aegis can load **W** = [WPE WebKit](https://github.com/Igalia/wpe-android) via Maven:

```
implementation("org.wpewebkit.wpeview:wpeview:0.3.3")
```

- AAR on Maven Central is about **167 MB** (fits a ~100–650 MB on-device budget).
- This is **WebKit** (Safari family), not Chromium/Blink.
- It is the only other *public embeddable* full engine on Android besides GeckoView.

Menu: **Engine preference: WebKit (WPE)** or cycle **C → G → W**.
EOF
