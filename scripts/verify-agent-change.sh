#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

branch="$(git branch --show-current)"
if [[ "$branch" != "gerfried-erweiterung" && "$branch" != "gerfried" ]]; then
  echo "Refusing verification outside branch gerfried-erweiterung" >&2
  exit 2
fi

PYTHONPATH=server "${PYTEST:-.venv/bin/pytest}" -q server/tests

cd android
export ANDROID_HOME="${ANDROID_HOME:-/home/veit/Android/Sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
unset LAYERMAXXING_GREGOR_URL LAYERMAXXING_GERFRIED_URL
# Gregors Testserver-Profil hat bewusst keinen eingecheckten Default; der Profiltest braucht den Testpfad.
export LAYERMAXXING_GREGOR_TEST_URL="${LAYERMAXXING_GREGOR_TEST_URL:-https://gs-layermaxxing.duckdns.org/test-gerfried/}"
./gradlew testDebugUnitTest lintDebug assembleDebug

cd "$ROOT"
git diff --check
echo "GS Layermaxxing gates passed."
