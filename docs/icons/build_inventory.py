#!/usr/bin/env python3
"""Icon-Inventar: alt (Emoji/Unicode-Glyph bzw. gemalte ic_*.webp) -> neu (ico_* Linien-Icon).

Erzeugt docs/icons/icon-inventar.html (+ PNG via Chromium) und icon-inventar.md.
"""
import re, html, subprocess, os
from pathlib import Path
from build_icons import ICONS, SRC

HERE = Path(__file__).parent

# (Bereich, alt, neu-Key, Bedeutung, wo)
ROWS = [
 ("Navigation", "💬", "chats", "Tab Chats", "Navigation.kt"),
 ("Navigation", "⚙", "more", "Tab Mehr", "Navigation.kt"),
 ("Navigation", "🏝", "island", "Tab Inseln / Meine Insel", "Navigation.kt, Inselleiste"),
 ("Navigation", "🗺 (Bullauge-Bild)", "map", "Inselleiste Karte", "IslandWorld.kt"),
 ("Navigation", "⚓ (Brett-Bild)", "harbour", "Inselleiste Hafen", "IslandWorld.kt"),
 ("Navigation", "💬 (ic_chat.webp)", "chats", "Inselleiste Chats", "IslandWorld.kt"),
 ("Navigation", "← ‹", "back", "Zurück", "überall"),
 ("Navigation", "›", "chevron", "Zeile öffnen", "Listen"),
 ("Navigation", "⋮", "menu", "Weitere Aktionen", "Chat, Leute, Funken"),
 ("Navigation", "✕", "close", "Schließen / leeren", "Suche, Antwort, Räume"),
 ("Navigation", "⌕ 🔍", "search", "Suchen", "Chatliste, Chat, Wörterbuch"),
 ("Navigation", "＋", "add", "Hinzufügen", "Neuer Chat, Wörterbuch"),
 ("Navigation", "⟳", "refresh", "Aktualisieren", "Kopfleiste"),
 ("Navigation", "✓", "check", "Ausgewählt / Fertig", "Listen, Bearbeiten"),
 ("Mehr", "✉", "letter", "Briefe", "MoreScreens.kt"),
 ("Mehr", "★ ⭐", "points", "Punkte", "MoreScreens.kt, Karte"),
 ("Mehr", "📖", "glossary", "Wörterbuch", "MoreScreens.kt"),
 ("Mehr", "🎨", "appearance", "Darstellung", "MoreScreens.kt"),
 ("Mehr", "🔐", "security", "Konto & Sicherheit", "MoreScreens.kt"),
 ("Mehr", "ℹ", "about", "Über die App", "MoreScreens.kt"),
 ("Mehr", "🔐 (groß)", "app_lock", "App gesperrt", "MainActivity.kt"),
 ("Chat", "✎", "edit", "Bearbeiten / Namen ändern", "Chat, Freund-Seite"),
 ("Chat", "↩", "reply", "Antworten", "Nachrichten-Menü"),
 ("Chat", "⧉", "copy", "Kopieren", "Nachrichten-Menü"),
 ("Chat", "📌", "pin", "Anpinnen", "Nachrichten-Menü"),
 ("Chat", "🗑", "delete", "Löschen / verwerfen", "Nachrichten-Menü, Aufnahme"),
 ("Chat", "📷", "camera", "Foto", "Eingabe"),
 ("Chat", "🎙", "mic", "Flaschenpost aufnehmen", "Eingabe"),
 ("Chat", "☺ / ⌨", "emoji / keyboard", "Sticker / Tastatur", "Eingabe"),
 ("Chat", "▶ ■", "play / stop", "Sprachnachricht", "ChatMedia.kt"),
 ("Chat", "#", "topics", "Themen", "Chat-Menü, Freund-Seite"),
 ("Chat", "⚖", "rules", "Freundschaftsregeln", "Chat-Menü, Freund-Seite"),
 ("Chat", "ⓘ", "info", "Info & Verschlüsselung", "Chat-Menü, Funken"),
 ("Chat", "🌱", "growth", "Gemeinsame Chat-Tage", "Chat-Menü"),
 ("Chat", "✦ ✨", "spark", "Freundesfunke", "Chatliste, Funken"),
 ("Briefe", "✦ (bereit)", "letter_ready", "Brief bereit", "Chat, Post, Briefe"),
 ("Briefe", "📖 / ✉ (offen)", "letter_open", "Brief geöffnet", "Briefe"),
 ("Briefe", "✉ (ausgehend)", "send", "Brief unterwegs (von mir)", "Briefe"),
 ("Briefe", "🕊", "transit", "Unterwegs", "Chat-Leiste"),
 ("Briefe", "🔒", "lock", "Versiegelt / gesperrt", "überall"),
 ("Briefe", "◷", "timer", "Öffnet nach Dauer", "Brief-Modus"),
 ("Briefe", "▣", "date", "Öffnet zu Datum", "Brief-Modus"),
 ("Briefe", "☝ ✋", "manual", "Ich gebe frei / Zustimmung", "Brief-Modus, Segelboot"),
 ("Briefe", "✓✓", "check_double", "Beide stimmen zu / gelesen", "Brief-Modus"),
 ("Briefe", "●", "presence", "Beide online", "Brief-Modus"),
 ("Briefe", "? 🎲", "random", "Zufällig", "Brief-Modus, Ruderboot"),
 ("Insel", "🏠", "house", "Mein Haus", "Hinweiskarte"),
 ("Insel", "🗼", "lighthouse", "Leuchtturm (Freunde)", "Hinweiskarte"),
 ("Insel", "🔥 👥", "groups", "Lagerfeuer (Gruppen)", "Hinweiskarte, Quests"),
  ("Insel", "⛵", "boat", "Boot auf Route", "Boot-Sheet"),
 ("Insel", "⚓", "harbour", "vor Anker", "Bootszeit"),
 ("Insel", "🔔", "bell", "Land in Sicht", "Bootszeit"),
 ("Insel", "⏳ 🧭 🕰", "timer", "heute noch / Dampfer", "Bootszeit, Dampfer"),
 ("Insel", "🌊 🗺", "date", "in Tagen / Wochen", "Bootszeit"),
 ("Insel", "📬", "letter_ready", "Brief angekommen", "Freundes-Sheet"),
 ("Quest", "🥾", "q_hike", "Wandern", "Quest-Formular + Listen"),
 ("Quest", "🍖 🔥", "q_grill", "Grillen", ""),
 ("Quest", "🚲", "q_bike", "Radfahren", ""),
 ("Quest", "🍝", "q_food", "Essen", ""),
 ("Quest", "🎲", "q_game", "Spielen", ""),
 ("Quest", "🧳", "q_travel", "Reisen", ""),
 ("Quest", "⚽", "q_sport", "Sport", ""),
 ("Quest", "🎵", "q_music", "Musik", ""),
 ("Quest", "🤝", "q_help", "Helfen", ""),
 ("Quest", "⭐", "q_star", "Sonstiges", ""),
]
KEEP = [
 ("Avatare", "🦊 🐻 🦉 🐺 🦌 🐗 🦅 🐿 🦡 🐴 🐱 🐶", "bleibt – ist das Gesicht einer Person"),
 ("Reaktionen", "❤️ 😂 👍 😮 😢 🌴", "bleibt – Inhalt, den man selbst sendet"),
 ("Sticker", "Sticker-Set", "bleibt – Inhalt"),
 ("Insel-Welt", "gemalte Gebäude, Boote, Deko, Orte (webp)", "bleibt – gemalter Nordic-Stil"),
]

def svg(name):
    s = (SRC / f"{ICONS[name]}.svg").read_text()
    s = re.sub(r"<!--.*?-->", "", s, flags=re.S)
    return s.replace('stroke-width="2"', 'stroke-width="1.75"').replace('width="24"', 'width="30"').replace('height="24"', 'height="30"')

def cell(key):
    return "".join(f'<span class=ic>{svg(k.strip())}</span>' for k in key.split("/"))

if __name__ == "__main__":
    groups = {}
    for r in ROWS: groups.setdefault(r[0], []).append(r)
    parts = []
    for g, rows in groups.items():
        parts.append(f"<section><h2>{g}</h2><table>")
        for _, old, key, what, where in rows:
            parts.append(f"<tr><td class=old>{html.escape(old)}</td><td class=arr>→</td><td>{cell(key)}</td>"
                         f"<td class=what>{html.escape(what)}<small>{html.escape(where)}</small></td></tr>")
        parts.append("</table></section>")
    keep = "".join(f"<tr><td class=what>{a}</td><td class=old2>{html.escape(b)}</td><td class=what><small>{c}</small></td></tr>" for a, b, c in KEEP)
    doc = f"""<!doctype html><meta charset=utf-8><style>
body{{font-family:'DejaVu Sans',sans-serif;background:#F7F0E1;color:#1F3B4D;margin:28px;width:1700px}}
h1{{margin:0 0 4px;font-size:34px}} p.sub{{margin:0 0 18px;color:#6B6250;font-size:17px}}
.grid{{columns:3;column-gap:22px}} section{{break-inside:avoid;background:#FBF6EA;border:1px solid #E6D9BC;border-radius:16px;padding:10px 14px;margin:0 0 18px}}
h2{{font-size:20px;margin:4px 0 6px;color:#2F5D5A}} table{{border-collapse:collapse;width:100%}}
td{{padding:4px 6px;border-top:1px solid #EFE3CA;vertical-align:middle}} tr:first-child td{{border-top:none}}
.old{{font-family:'Noto Color Emoji','DejaVu Sans';font-size:22px;width:96px;white-space:nowrap}} .old2{{font-family:'Noto Color Emoji','DejaVu Sans';font-size:20px}}
.arr{{color:#B5A47E;width:16px}} .ic{{color:#2F5D5A;display:inline-block;margin-right:4px}} .ic svg{{display:block}}
.what{{font-size:15px}} small{{display:block;color:#8A806A;font-size:12px}}
.keep{{background:#EEF4F1;border-color:#C9DCD4}}
</style><h1>Icons in GS Layermaxxing – alt → neu</h1>
<p class=sub>{len(ROWS)} UI-Symbole: bunte Emoji/Unicode-Zeichen → ein einheitliches Linien-Set (Lucide, ISC), 1,75 px Strich, in Teal/Creme eingefärbt. Gemalte Inselwelt bleibt.</p>
<div class=grid>{''.join(parts)}<section class=keep><h2>Bleibt bewusst</h2><table>{keep}</table></section></div>"""
    out = HERE / "icon-inventar.html"; out.write_text(doc)
    md = ["# Icon-Inventar (alt → neu)", "", "| Bereich | alt | neu (ico_*) | Bedeutung | wo |", "|---|---|---|---|---|"]
    md += [f"| {a} | {b} | {' / '.join('ico_' + k.strip() for k in c.split('/'))} | {d} | {e} |" for a, b, c, d, e in ROWS]
    md += ["", "Bleibt: " + "; ".join(f"{a} ({c})" for a, _, c in KEEP)]
    (HERE / "icon-inventar.md").write_text("\n".join(md) + "\n")
    png = HERE / "icon-inventar.png"
    subprocess.run(["chromium", "--headless", "--disable-gpu", "--hide-scrollbars", f"--screenshot={png}",
                    "--window-size=1760,2050", f"file://{out}"], check=True, capture_output=True)
    print(png, len(ROWS))
