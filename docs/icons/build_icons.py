#!/usr/bin/env python3
"""Baut das App-Icon-Set (Linien-Icons, Lucide ISC) als Android-VectorDrawables.

Quelle: lucide-static (ISC-Lizenz), entpackt nach $LUCIDE (Ordner mit icons/*.svg).
Ausgabe: android/app/src/main/res/drawable/ico_<name>.xml  (Strich 1.75, runde Enden, schwarz → per tint gefärbt)

Aufruf:  LUCIDE=/pfad/zu/package python3 docs/icons/build_icons.py
"""
import os, re, sys, xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "android/app/src/main/res/drawable"
SRC = Path(os.environ.get("LUCIDE", "")) / "icons"
STROKE = "1.75"

# App-Name -> Lucide-Name. Eine Bedeutung = ein Icon, überall in der App.
ICONS = {
    # Navigation / Hauptmenü
    "chats": "messages-square", "map": "map", "island": "tree-palm", "harbour": "anchor", "more": "menu",
    "home": "house", "back": "arrow-left", "close": "x", "menu": "ellipsis-vertical", "chevron": "chevron-right",
    "expand": "chevron-down", "collapse": "chevron-up", "search": "search", "add": "plus", "check": "check",
    "check_double": "check-check", "edit": "pencil", "delete": "trash-2", "reply": "reply", "pin": "pin",
    # Kommunikation
    "chat": "message-circle", "letter": "mail", "letter_open": "mail-open", "letter_ready": "mailbox",
    "seal": "stamp", "spark": "sparkles", "topics": "hash", "glossary": "book-open", "groups": "users-round",
    "people": "user-round-plus", "friend": "user-round", "voice": "audio-lines", "mic": "mic", "camera": "camera",
    "attach": "paperclip", "emoji": "smile", "keyboard": "keyboard", "play": "play", "stop": "square",
    "send": "send-horizontal",
    # Freundschaft / Regeln / Sicherheit
    "points": "star", "rules": "scale", "info": "info", "lock": "lock", "unlock": "lock-open", "security": "shield-check",
    "key": "key-round", "warning": "triangle-alert", "appearance": "palette", "about": "circle-help",
    "presence": "radio-tower", "manual": "hand", "timer": "hourglass", "schedule": "calendar-clock", "random": "dices",
    "growth": "calendar-heart", "streak": "flame",
    # Insel / See
    "boat": "sailboat", "compass": "compass", "waves": "waves", "bell": "bell-ring", "lighthouse": "lighthouse",
    "campfire": "flame", "library": "library-big", "house": "house", "build": "house-plus", "decor": "flower-2",
    "transit": "bird", "fast_forward": "fast-forward", "refresh": "refresh-cw", "copy": "copy",
    "group": "users-round", "app_lock": "lock-keyhole", "date": "calendar-days", "pin_place": "map-pin",
    # Quest-Arten
    "q_hike": "footprints", "q_grill": "beef", "q_bike": "bike", "q_food": "utensils", "q_game": "dices",
    "q_travel": "luggage", "q_sport": "volleyball", "q_music": "music", "q_help": "handshake", "q_star": "star",
}

def num(s): return float(s)

def shape_to_path(el):
    t = el.tag.split("}")[-1]; a = el.attrib
    if t == "path": return a["d"]
    if t == "circle":
        cx, cy, r = num(a["cx"]), num(a["cy"]), num(a["r"])
        return f"M{cx - r},{cy}a{r},{r} 0 1,0 {2 * r},0a{r},{r} 0 1,0 {-2 * r},0"
    if t == "ellipse":
        cx, cy, rx, ry = num(a["cx"]), num(a["cy"]), num(a["rx"]), num(a["ry"])
        return f"M{cx - rx},{cy}a{rx},{ry} 0 1,0 {2 * rx},0a{rx},{ry} 0 1,0 {-2 * rx},0"
    if t == "line":
        return f"M{a['x1']},{a['y1']}L{a['x2']},{a['y2']}"
    if t in ("polyline", "polygon"):
        pts = re.findall(r"-?[\d.]+", a["points"]); pairs = [f"{pts[i]},{pts[i + 1]}" for i in range(0, len(pts), 2)]
        return "M" + "L".join(pairs) + ("Z" if t == "polygon" else "")
    if t == "rect":
        x, y, w, h = num(a.get("x", 0)), num(a.get("y", 0)), num(a["width"]), num(a["height"])
        rx = num(a.get("rx", a.get("ry", 0))); ry = num(a.get("ry", rx))
        if rx == 0: return f"M{x},{y}h{w}v{h}h{-w}z"
        return (f"M{x + rx},{y}h{w - 2 * rx}a{rx},{ry} 0 0 1 {rx},{ry}v{h - 2 * ry}a{rx},{ry} 0 0 1 {-rx},{ry}"
                f"h{-(w - 2 * rx)}a{rx},{ry} 0 0 1 {-rx},{-ry}v{-(h - 2 * ry)}a{rx},{ry} 0 0 1 {rx},{-ry}z")
    raise ValueError(t)

def convert(lucide):
    root = ET.parse(SRC / f"{lucide}.svg").getroot()
    paths = []
    for el in root.iter():
        if el is root: continue
        t = el.tag.split("}")[-1]
        if t in ("path", "circle", "ellipse", "line", "polyline", "polygon", "rect"): paths.append(shape_to_path(el))
    body = "\n".join(
        f'    <path android:pathData="{p}" android:fillColor="#00000000" android:strokeColor="#FF000000"\n'
        f'        android:strokeWidth="{STROKE}" android:strokeLineCap="round" android:strokeLineJoin="round" />' for p in paths)
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            f'<!-- Lucide "{lucide}" (ISC) – generiert von docs/icons/build_icons.py, nicht von Hand ändern -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">\n'
            f'{body}\n</vector>\n')

if __name__ == "__main__":
    if not SRC.is_dir(): sys.exit("LUCIDE nicht gesetzt / icons fehlt")
    for old in OUT.glob("ico_*.xml"): old.unlink()
    for name, lucide in ICONS.items():
        (OUT / f"ico_{name}.xml").write_text(convert(lucide))
    print(len(ICONS), "Icons ->", OUT)
