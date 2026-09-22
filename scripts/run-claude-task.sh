#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ "$(git branch --show-current)" != "gerfried" ]]; then
  echo "Refusing Claude run outside branch gerfried" >&2
  exit 2
fi

if [[ -n "$(git status --porcelain)" && "${ALLOW_DIRTY:-0}" != "1" ]]; then
  echo "Working tree is not clean. Commit/stash first or set ALLOW_DIRTY=1 intentionally." >&2
  exit 3
fi

if [[ $# -gt 0 ]]; then
  prompt="$*"
elif [[ ! -t 0 ]]; then
  prompt="$(cat)"
else
  echo "Pass a task as arguments or on stdin." >&2
  exit 4
fi

exec claude -p "$prompt" \
  --model "${CLAUDE_MODEL:-sonnet}" \
  --effort "${CLAUDE_EFFORT:-medium}" \
  --max-turns "${CLAUDE_MAX_TURNS:-30}" \
  --permission-mode acceptEdits \
  --allowedTools Read Edit Write Bash
