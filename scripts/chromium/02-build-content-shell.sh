#!/usr/bin/env bash
# Build content_shell_apk for arm64. Run after 01-fetch-chromium.sh
set -euo pipefail

CHROMIUM_ROOT="${CHROMIUM_ROOT:-$HOME/chromium}"
export PATH="$CHROMIUM_ROOT/depot_tools:$PATH"
cd "$CHROMIUM_ROOT/src"

OUT="${OUT:-out/AegisArm64}"
echo "==> gn gen $OUT"
gn gen "$OUT" --args='
  target_os="android"
  target_cpu="arm64"
  is_debug=false
  is_official_build=true
  is_component_build=false
  symbol_level=0
  blink_symbol_level=0
  enable_nacl=false
  proprietary_codecs=true
  ffmpeg_branding="Chrome"
'

echo "==> autoninja content_shell_apk (hours)"
autoninja -C "$OUT" content_shell_apk

echo "==> Built:"
ls -lh "$OUT/apks/" || ls -lh "$OUT/bin/"
echo "APK under $OUT/apks/ContentShell.apk (path may vary by version)"
