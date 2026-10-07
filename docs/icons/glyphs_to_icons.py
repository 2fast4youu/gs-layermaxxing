#!/usr/bin/env python3
"""Einmal-Umbau: einzelne Symbol-Glyphen in Text(...) -> AppIcon(R.drawable.ico_*).

Ersetzt nur Text-Aufrufe, deren erstes Argument GENAU ein Symbol ist (z.B. Text("✕", fontSize = 16.sp, color = x)).
fontSize -> size (etwas größer, Linien-Icons wirken leichter), color -> tint, Modifier bleibt.
"""
import re, sys
from pathlib import Path

GLYPH = {
    "⌕": "search", "🔍": "search", "✕": "close", "✦": "spark", "✨": "spark", "＋": "add",
    "‹": "back", "←": "back", "›": "chevron", "⋮": "menu", "⟳": "refresh", "✓": "check",
    "🔒": "lock", "🔐": "app_lock", "👥": "group", "⚠": "warning", "★": "points", "✎": "edit",
    "✉": "letter", "🗑": "delete", "📷": "camera", "🎙": "mic", "⛵": "boat",
}

def split_args(s):
    out, depth, cur, q = [], 0, "", False
    for i, c in enumerate(s):
        if c == '"' and (i == 0 or s[i - 1] != "\\"): q = not q
        if not q:
            if c in "([{": depth += 1
            elif c in ")]}": depth -= 1
            elif c == "," and depth == 0: out.append(cur.strip()); cur = ""; continue
        cur += c
    if cur.strip(): out.append(cur.strip())
    return out

def find_close(s, start):
    depth, q = 0, False
    for i in range(start, len(s)):
        c = s[i]
        if c == '"' and s[i - 1] != "\\": q = not q
        if q: continue
        if c == "(": depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0: return i
    return -1

PAT = re.compile(r'\bText\(\s*"([^"]{1,2})"')

def transform(src):
    out, pos, n = [], 0, 0
    while True:
        m = PAT.search(src, pos)
        if not m: out.append(src[pos:]); break
        g = m.group(1)
        if g not in GLYPH:
            out.append(src[pos:m.end()]); pos = m.end(); continue
        open_i = src.index("(", m.start()); close_i = find_close(src, open_i)
        args = split_args(src[open_i + 1:close_i])[1:]
        mod = size = tint = None; ok = True
        for a in args:
            if a.startswith("fontSize"): size = float(re.search(r"([\d.]+)\.sp", a).group(1))
            elif a.startswith("color"): tint = a.split("=", 1)[1].strip()
            elif a.startswith("modifier"): mod = a.split("=", 1)[1].strip()
            elif a.startswith("Modifier"): mod = a
            elif a.startswith(("fontWeight", "fontFamily", "style")): pass
            else: ok = False
        if not ok:
            out.append(src[pos:m.end()]); pos = m.end(); print("  skip", g, args, file=sys.stderr); continue
        px = round((size or 18) * 1.1)
        parts = [f"R.drawable.ico_{GLYPH[g]}", "null"]
        if mod: parts.append(f"modifier = {mod}")
        if tint: parts.append(f"tint = {tint}")
        parts.append(f"size = {px}.dp")
        out.append(src[pos:m.start()]); out.append("AppIcon(" + ", ".join(parts) + ")")
        pos = close_i + 1; n += 1
    return "".join(out), n

if __name__ == "__main__":
    total = 0
    for f in sys.argv[1:]:
        p = Path(f); s, n = transform(p.read_text())
        if n: p.write_text(s); print(f"{p.name}: {n}"); total += n
    print("gesamt", total)
