#!/usr/bin/env bash
# Fetch Chromium Android source. Run on a large Linux builder.
# OFFLINE BUILDER ONLY — requires 100GB+ disk. Do NOT run on limited GitHub accounts.
set -euo pipefail

CHROMIUM_ROOT="${CHROMIUM_ROOT:-$HOME/chromium}"
mkdir -p "$CHROMIUM_ROOT"
cd "$CHROMIUM_ROOT"

if [[ ! -d depot_tools ]]; then
  echo "==> Cloning depot_tools"
  git clone https://chromium.googlesource.com/chromium/tools/depot_tools.git
fi
export PATH="$CHROMIUM_ROOT/depot_tools:$PATH"

if [[ ! -d src ]]; then
  echo "==> fetch android (this downloads tens of GB — long)"
  fetch --nohooks android
fi

cd src
echo "target_os = [ 'android' ]" >> ../.gclient || true
echo "==> gclient sync"
gclient sync --with_branch_heads --with_tags -D

echo "==> install-build-deps (may need sudo)"
./build/install-build-deps.sh --android || true

echo "==> Chromium tree ready at $CHROMIUM_ROOT/src"
