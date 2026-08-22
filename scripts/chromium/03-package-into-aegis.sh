#!/usr/bin/env bash
# Copy native libs + assets from a built ContentShell APK into Aegis app/
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
AEGIS_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
CHROMIUM_ROOT="${CHROMIUM_ROOT:-$HOME/chromium}"
OUT="${OUT:-$CHROMIUM_ROOT/src/out/AegisArm64}"

SHELL_APK=$(find "$OUT" -name 'ContentShell*.apk' 2>/dev/null | head -1)
if [[ -z "${SHELL_APK}" ]]; then
  echo "ContentShell APK not found under $OUT"
  echo "Run 02-build-content-shell.sh first."
  exit 1
fi

WORKDIR=$(mktemp -d)
trap 'rm -rf "$WORKDIR"' EXIT
unzip -q "$SHELL_APK" -d "$WORKDIR"

JNILIBS="$AEGIS_ROOT/app/src/main/jniLibs/arm64-v8a"
ASSETS="$AEGIS_ROOT/app/src/main/assets/chromium"
mkdir -p "$JNILIBS" "$ASSETS"

echo "==> Copying .so from ContentShell"
find "$WORKDIR/lib" -name '*.so' -exec cp -v {} "$JNILIBS/" \; 2>/dev/null || true
# Some builds put libs under lib/arm64-v8a
find "$WORKDIR" -path '*/lib/arm64-v8a/*.so' -exec cp -v {} "$JNILIBS/" \; 2>/dev/null || true

echo "==> Copying pak/dat assets"
find "$WORKDIR/assets" -type f \( -name '*.pak' -o -name '*.dat' -o -name '*.bin' \) \
  -exec cp -v {} "$ASSETS/" \; 2>/dev/null || true

# Marker for BuildConfig / runtime detection
echo "bundled=true" > "$AEGIS_ROOT/app/src/main/assets/chromium/BUNDLED_MARKER"
echo "source_apk=$SHELL_APK" >> "$AEGIS_ROOT/app/src/main/assets/chromium/BUNDLED_MARKER"
date -u +"%Y-%m-%dT%H:%M:%SZ" >> "$AEGIS_ROOT/app/src/main/assets/chromium/BUNDLED_MARKER"

echo "==> Packaged into:"
echo "  $JNILIBS"
echo "  $ASSETS"
du -sh "$JNILIBS" "$ASSETS" 2>/dev/null || true
echo "Next: ./gradlew assembleAdultDebug -Paegis.bundledChromium=true"
