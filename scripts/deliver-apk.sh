#!/usr/bin/env bash
set -euo pipefail

# Copies the freshly built Gerfried debug APK to the home directory, named after
# the current versionName. Run ./scripts/verify-agent-change.sh first; this
# script only delivers, it does not build.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

APK="android/app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$APK" ]]; then
  echo "Missing $APK — run ./scripts/verify-agent-change.sh first" >&2
  exit 1
fi

VERSION="$(grep -oP 'versionName\s*=\s*"\K[^"]+' android/app/build.gradle.kts)"
# "5.0-beta5" -> "5-beta5" to match the established delivery filename.
TAG="${VERSION/.0-/-}"
DEST="$HOME/GS-Layermaxxing-Gerfried-v${TAG}-lehen-fluss.apk"

cp "$APK" "$DEST"
echo "Delivered $DEST"
