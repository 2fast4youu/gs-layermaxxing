#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ "$(git branch --show-current)" != "gerfried" ]]; then
  echo "Refusing verification outside branch gerfried" >&2
  exit 2
fi

PYTHONPATH=server .venv/bin/pytest -q server/tests

cd android
export ANDROID_HOME="${ANDROID_HOME:-/home/veit/Android/Sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
unset LAYERMAXXING_GREGOR_URL LAYERMAXXING_GERFRIED_URL
./gradlew testDebugUnitTest lintDebug assembleDebug

cd "$ROOT"
git diff --check
echo "GS Layermaxxing gates passed."
