#!/usr/bin/env python3
"""Baut die Klickpfad-Filmstreifen + Theme-Check aus rendered/*.png.

Ausgabe (neben diesem Skript):
  clickpaths/NN-<journey>.png   je Aufgabe ein Filmstreifen
  clickpaths/theme-check.png    alle Screens, markiert nach Theme-Passung
  clickpaths/index.html         alles zusammen zum Durchscrollen
Tap-Positionen sind in % des jeweiligen Screenshots (visuell aus den Renders
bestimmt, Ziel jeweils im Code belegt -> 'src').
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import html, json

HERE = Path(__file__).parent
R = HERE / "rendered"
OUT = HERE / "clickpaths"
OUT.mkdir(exist_ok=True)
FB = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FR = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
font = lambda s, b=True: ImageFont.truetype(FB if b else FR, s)

# ---------- Theme-Bewertung (aktueller Stil: "Nordische Küste", 7.7) ----------
# FITS = passt, PARTLY = teils (Farben ok, aber Standard-Material), OLD = passt nicht mehr
THEME = {
    "03-room": ("PARTLY", "Türkis/Creme ok, aber nackte Material-Liste"),
    "04-card": ("OLD", "altes buntes Alpendorf; BuildingCard im App-Code nirgends mehr benutzt"),
    "05-chats": ("FITS", "Hafenpapier, gedämpftes Teal"),
    "06-thread": ("PARTLY", "Chat ok, Kopfleiste dunkelblau/Standard"),
    "07-glossary": ("FITS", "Creme + Teal, ruhig"),
    "08-topics-room": ("PARTLY", "Farben ok, sonst Standard-Forum"),
    "09-groups-room": ("PARTLY", "großes weißes Panel, schwere schwarze Schrift"),
    "12-empty-chats": ("FITS", "Hafen-Text + Boot-Illustration"),
    "13-chat-extras": ("PARTLY", "ok, aber unruhig: viele Emoji/Werkzeuge"),
    "13-login": ("PARTLY", "Insel im Hintergrund, Formular lavendel/Material"),
    "14-chat-actions": ("PARTLY", "loses Werkzeug-Palette, kein fertiger Look"),
    "14-more": ("OLD", "flache lavendel Material-Liste – im Inselmodus 'Mein Haus'"),
    "20-inseln": ("FITS", "Kernstil"),
    "21-inseln-leer": ("FITS", "Kernstil"),
    "22-meine-insel": ("FITS", "Kernstil"),
    "23-besuch": ("FITS", "Kernstil"),
    "24-deko-wahl": ("PARTLY", "Deko-Bilder gut, Hintergrund reines Weiß"),
    "25-bauen": ("PARTLY", "Bilder gut, Formular weiß/Standard"),
    "25-schiff": ("FITS", "dunkles Glas-Sheet über Meer"),
    "26-inseln-debug": ("DEBUG", "nur Debug-Modus (Hitboxen) – kein Theme-Thema"),
    "26-ort": ("PARTLY", "kleines Inselbild, Rest weißes Material-Formular"),
    "27-insel-zoom": ("FITS", "Kernstil"),
    "28-insel-doppeltipp": ("FITS", "Kernstil"),
    "29-insel-einzeltipp": ("FITS", "Postamt, Holz + Siegel"),
    "30-post": ("FITS", "Holz, Umschläge, Wachssiegel"),
    "31-hafen": ("FITS", "Kork, Zettel, Küste"),
    "32-leuchtturm": ("FITS", "Küste, Holzschild"),
    "33-bibliothek": ("FITS", "Holz + Papier"),
    "34-gemeindehaus": ("FITS", "warmes Holz"),
    "35-haus": ("FITS", "gemaltes Portrait, Holz"),
    "36-topics-screen": ("OLD", "weißes Material-Layout, graue Rahmen"),
    "37-ep-screen": ("OLD", "blau/grau Formular, kein Inselbezug"),
    "38-letter-room": ("OLD", "dunkelbraun/schwarz – bricht mit ruhigem Creme/Teal"),
    "39-spark-room": ("PARTLY", "Sand-Karten ok, Knopf blau, keine Welt"),
    "40-thread-info-sheet": ("OLD", "weiße Einstellungsseite, Standard-Schalter"),
    "41-quest-dialog": ("PARTLY", "Spielschrift ok, Dialog lavendel"),
    "42-glossary-term-dialog": ("PARTLY", "Mini-Dialog, lavendel"),
    "43-friend-sheet": ("FITS", "Inselbild, Teal/Creme"),
    "45-label-dialog": ("PARTLY", "lavendel Formular, blaue Chips"),
    "46-recovery-dialog": ("PARTLY", "generischer Dialog"),
    "47-proof-dialog": ("PARTLY", "technischer Dialog, lavendel"),
    "48-ep-proposal-dialog": ("PARTLY", "Formular lavendel/blau"),
    "49-request-dialog": ("PARTLY", "grau-lavendel Material"),
}
TCOL = {"FITS": (46, 140, 90), "PARTLY": (222, 150, 30), "OLD": (200, 40, 40), "DEBUG": (110, 110, 110)}
TTXT = {"FITS": "passt", "PARTLY": "teils", "OLD": "passt NICHT mehr", "DEBUG": "Debug"}

# ---------- Aufgaben (Journeys) ----------
# step: (screen | None für 'kein Screenshot', caption, tap (x%,y%) | None, tap-label, src)
J = [
 ("Mit einem Freund chatten", "Inselkarte", [
   ("20-inseln", "Inselkarte", (50, 13), "Insel von Gerfried", "IslandWorld.kt:297 onFriend"),
   ("43-friend-sheet", "Freundes-Sheet", (30, 28), "Chat", "IslandWorld.kt:919 onChat"),
   ("06-thread", "Chat offen", None, "", "LayerHome.kt:509 ThreadScreen"),
 ], ["Alternative im Chat-Tab: 1 Tap (Zeile antippen) – aber der Chat-Tab ist im Inselmodus versteckt."]),
 ("Brief an einen Freund schreiben", "Inselkarte", [
   ("20-inseln", "Inselkarte", (50, 13), "Insel von Gerfried", "IslandWorld.kt:297"),
   ("43-friend-sheet", "Freundes-Sheet", (70, 28), "Brief schreiben", "IslandWorld.kt:920-923 onLetter"),
   (None, "Brief-Editor", None, "", "LayerHome.kt composeLetterFriend – kein Screenshot"),
 ], ["Alternativ über Post: Meine Insel → Post → „Brief schreiben“ → Empfänger wählen = 4 Taps.",
     "Im Chat: ✦ neben dem Eingabefeld."]),
 ("Neuen Brief lesen", "Inselkarte", [
   ("20-inseln", "Inselkarte", (50, 95), "Meine Insel", "IslandWorld.kt:340"),
   ("22-meine-insel", "Meine Insel", (12, 47), "Post", "IslandWorld.kt:265 onBuilding"),
   ("30-post", "Postamt", (20, 18), "Umschlag", "PlaceScenes.kt:239 PostOffice onOpen"),
   (None, "Brief offen", None, "", "openLetter – kein Screenshot"),
 ], ["Abkürzung existiert schon: „✉ n“-Pille oben (nur wenn ein Brief bereit ist) → direkt Post = 2 Taps."]),
 ("Quest mit Freund anlegen", "Inselkarte", [
   ("20-inseln", "Inselkarte", (50, 13), "Insel von Gerfried", "IslandWorld.kt:297"),
   ("43-friend-sheet", "Freundes-Sheet", (85, 35), "+ Neue", "IslandWorld.kt:399 newQuestFor"),
   (None, "Neue Quest (Sheet)", None, "", "NewQuestSheet – nicht renderbar"),
 ], ["Zweiter Weg: Hafen → „Quest anpinnen“ (2 Taps).",
     "Dritter Weg im Chat → ChatQuestDialog (41) – ANDERES Formular für dieselbe Sache → Stilbruch."]),
 ("Freund einen Spitznamen geben", "Inselkarte", [
   ("20-inseln", "Inselkarte", (50, 13), "Insel von Gerfried", "IslandWorld.kt:297"),
   ("43-friend-sheet", "Freundes-Sheet", (39, 10), "Wie nennst du …?", "IslandWorld.kt:898"),
   ("45-label-dialog", "Namen wählen", (50, 85), "Speichern", "IslandWorld.kt:1153 LabelDialog"),
 ], []),
 ("„Ich bin hier“ / Status setzen", "Meine Insel", [
   ("22-meine-insel", "Meine Insel", (50, 44), "Lebensort (z.B. Bude)", "IslandWorld.kt:268 onPlot"),
   ("26-ort", "Ort-Sheet", (52, 64), "Status speichern", "LifeIsland.kt:231"),
 ], []),
 ("Lebensort bauen", "Meine Insel", [
   ("22-meine-insel", "Meine Insel", (73, 57), "+ Bauplatz", "IslandWorld.kt:268 onPlot → buildPick"),
   ("25-bauen", "Was steht hier?", (15, 39), "Zuhause", "LifeIsland.kt:159"),
   ("25-bauen", "Name + Bauen", (84, 67), "Bauen", "LifeIsland.kt:201"),
 ], []),
 ("Deko setzen", "Meine Insel", [
   ("22-meine-insel", "Meine Insel", (50, 88), "Insel bearbeiten", "IslandWorld.kt:332"),
   (None, "Bearbeiten-Modus", None, "freien Deko-Platz", "IslandHome.kt:153 – kein Screenshot"),
   ("24-deko-wahl", "Deko wählen", (19, 46), "Blumen", "IslandHome.kt:204 DecorPicker"),
 ], []),
 ("Gruppe erstellen", "Meine Insel", [
   ("22-meine-insel", "Meine Insel", (25, 53), "Gemeindehaus / Lagerfeuer", "IslandWorld.kt:265 onBuilding"),
   ("34-gemeindehaus", "Gemeindehaus", (50, 96), "Gruppen verwalten", "PlaceScenes.kt:587"),
   ("09-groups-room", "Gruppen", (50, 30), "+ Gruppe erstellen", "GregorScreens.kt:279"),
 ], ["Zweiter „Gruppe erstellen“ existiert separat (LayerHome.kt:1287) → doppelt."]),
 ("Thema fürs nächste Gespräch notieren", "Inselkarte", [
   ("20-inseln", "Inselkarte", (70, 95), "Hafen", "IslandWorld.kt:341"),
   ("31-hafen", "Hafen", (81, 73), "Themen", "PlaceScenes.kt:380 onAllTopics"),
   ("08-topics-room", "Themen", (50, 35), "Gerfried", "GregorScreens.kt:226 TopicsHub"),
   ("36-topics-screen", "Themen mit Gerfried", (85, 96), "Merken", "TopicsScreen.kt:59"),
 ], ["4 Taps + Text. Kandidat: „Thema“ direkt im Freundes-Sheet."]),
 ("Freunde finden", "Meine Insel", [
   ("22-meine-insel", "Meine Insel", (87, 47), "Leuchtturm", "IslandWorld.kt:265"),
   ("32-leuchtturm", "Leuchtturm", (50, 95), "Freunde finden", "PlaceScenes.kt:455 onFind"),
   (None, "Personen-Suche", None, "", "LayerHome peopleOpen – nicht im Inventar"),
 ], ["Auf der leeren Karte gibt es zusätzlich direkt „Freunde finden“ (21)."]),
 ("Einstellungen / Debug-Modus", "Meine Insel", [
   ("22-meine-insel", "Meine Insel", (36, 53), "Figur (= Mein Haus)", "IslandWorld.kt:270 onFigure"),
   ("35-haus", "Mein Haus", (50, 96), "Profil & Einstellungen", "PlaceScenes.kt:656"),
   ("14-more", "Mehr (lavendel!)", (50, 39), "Darstellung", "MoreScreens.kt:214"),
   (None, "Darstellung → Debug-Modus", None, "", "MoreScreens.kt:371 – kein Screenshot"),
 ], ["Stilbruch: aus dem gemalten Haus direkt in die alte Material-Liste."]),
 ("Punkte ansehen", "Inselkarte", [
   ("20-inseln", "Inselkarte", (89, 5), "⭐ Punkte", "IslandWorld.kt:296 onPoints"),
   ("37-ep-screen", "Punkte", None, "", "SocialScreens.kt:34 EpScreen"),
 ], ["Schnell (1 Tap), landet aber im alten Formular-Look."]),
]

# ---------- Zeichnen ----------
TH = 640  # Höhe eines Phone-Screens im Streifen
def thumb(name):
    im = Image.open(R / f"{name}.png").convert("RGB")
    s = TH / 2673  # gleiche Skala für Dialoge -> echte Größenverhältnisse
    return im.resize((max(1, round(im.width * s)), max(1, round(im.height * s))))

def placeholder(text):
    w = round(1233 * TH / 2673)
    im = Image.new("RGB", (w, TH), (236, 232, 222)); d = ImageDraw.Draw(im)
    d.rectangle([3, 3, w - 4, TH - 4], outline=(160, 150, 130), width=3)
    for i in range(0, w + TH, 26): d.line([(i, 0), (i - TH, TH)], fill=(225, 220, 208), width=2)
    y = TH // 2 - 40
    for line in ["(kein Screenshot)", *wrap(text, 16)]:
        tw = d.textlength(line, font=font(17)); d.text(((w - tw) / 2, y), line, fill=(90, 80, 60), font=font(17)); y += 26
    return im

def wrap(t, n):
    out, cur = [], ""
    for w in t.split():
        if len(cur) + len(w) + 1 > n and cur: out.append(cur); cur = w
        else: cur = (cur + " " + w).strip()
    return out + ([cur] if cur else [])

def badge(d, x, y, txt, col):
    f = font(15); tw = d.textlength(txt, font=f)
    d.rounded_rectangle([x, y, x + tw + 16, y + 24], 12, fill=col); d.text((x + 8, y + 3), txt, fill="white", font=f)

def strip(idx, title, start, steps, notes):
    tiles = [(thumb(s) if s else placeholder(c), s, c, tap, lab, src) for s, c, tap, lab, src in steps]
    gap, pad, head = 90, 30, 120
    W = pad * 2 + sum(t[0].width for t in tiles) + gap * (len(tiles) - 1)
    foot = 40 + 30 * len(notes)
    H = head + TH + 110 + foot
    im = Image.new("RGB", (max(W, 1100), H), (250, 247, 240)); d = ImageDraw.Draw(im)
    taps = sum(1 for t in tiles if t[4])
    d.text((pad, 22), f"{idx}. {title}", fill=(30, 50, 55), font=font(34))
    d.text((pad, 70), f"Start: {start}   ·   {taps} Taps", fill=(90, 100, 100), font=font(22, False))
    x = pad; n = 0
    for i, (t, s, cap, tap, lab, src) in enumerate(tiles):
        y = head
        im.paste(t, (x, y)); d.rectangle([x - 1, y - 1, x + t.width, y + t.height], outline=(180, 170, 150), width=2)
        if s and s in THEME and THEME[s][0] in ("OLD", "PARTLY"):
            st = THEME[s][0]; d.rectangle([x - 5, y - 5, x + t.width + 4, y + t.height + 4], outline=TCOL[st], width=5)
            badge(d, x + 6, y + 6, "Theme: " + TTXT[st], TCOL[st])
        if lab:
            n += 1
            if tap:
                cx, cy = x + t.width * tap[0] / 100, y + t.height * tap[1] / 100
                # Ring lässt das Ziel sichtbar; Nummer als Plakette daneben
                d.ellipse([cx - 30, cy - 30, cx + 30, cy + 30], outline=(255, 70, 40), width=6)
                bx, by = cx + 22, cy - 52
                d.ellipse([bx - 17, by - 17, bx + 17, by + 17], fill=(255, 70, 40), outline="white", width=3)
                num = str(n); d.text((bx - d.textlength(num, font=font(20)) / 2, by - 12), num, fill="white", font=font(20))
        d.text((x, y + t.height + 10), cap, fill=(30, 50, 55), font=font(19))
        if lab: d.text((x, y + t.height + 36), f"{n} Tap: {lab}", fill=(200, 60, 30), font=font(18))
        d.text((x, y + t.height + 62), src[:46], fill=(140, 140, 140), font=font(13, False))
        if i < len(tiles) - 1:
            ax = x + t.width + 12; ay = y + TH // 2
            d.line([(ax, ay), (ax + gap - 24, ay)], fill=(255, 70, 40), width=6)
            d.polygon([(ax + gap - 24, ay - 14), (ax + gap - 6, ay), (ax + gap - 24, ay + 14)], fill=(255, 70, 40))
        x += t.width + gap
    yy = head + TH + 110
    for nt in notes:
        d.text((pad, yy), "• " + nt, fill=(60, 70, 70), font=font(19, False)); yy += 30
    import re, unicodedata
    slug = re.sub(r"[^a-z0-9]+", "-", unicodedata.normalize("NFKD", title.lower()).encode("ascii", "ignore").decode()).strip("-")[:32]
    p = OUT / f"{idx:02d}-{slug}.png"
    im.save(p, optimize=True)
    return p, taps

def theme_sheet():
    names = sorted(THEME)
    cols, cw = 8, 250
    rows = (len(names) + cols - 1) // cols
    ch = 620
    im = Image.new("RGB", (cols * (cw + 20) + 20, 140 + rows * ch), (250, 247, 240)); d = ImageDraw.Draw(im)
    d.text((20, 20), "Theme-Check: passt der Screen noch zum Stil „Nordische Küste“ (7.7)?", fill=(30, 50, 55), font=font(32))
    x0 = 20
    for k, (st, lab) in enumerate([("FITS", "passt"), ("PARTLY", "teils – Farben ok, Material-Standard"), ("OLD", "passt nicht mehr"), ("DEBUG", "nur Debug")]):
        badge(d, x0, 74, lab, TCOL[st]); x0 += d.textlength(lab, font=font(15)) + 40
    for i, nm in enumerate(names):
        st, why = THEME[nm]
        im_ = Image.open(R / f"{nm}.png").convert("RGB"); im_.thumbnail((cw, 500))
        x = 20 + (i % cols) * (cw + 20); y = 130 + (i // cols) * ch
        im.paste(im_, (x, y + 30))
        d.rectangle([x - 4, y + 26, x + im_.width + 3, y + 30 + im_.height + 3], outline=TCOL[st], width=6 if st != "FITS" else 3)
        d.text((x, y), nm, fill=(30, 50, 55), font=font(16))
        badge(d, x, y + 30 + im_.height + 10, TTXT[st], TCOL[st])
        for j, line in enumerate(wrap(why, 28)[:2]):
            d.text((x, y + 30 + im_.height + 42 + j * 20), line, fill=(80, 80, 80), font=font(14, False))
    p = OUT / "theme-check.png"; im.save(p, optimize=True); return p

if __name__ == "__main__":
    made = [strip(i + 1, *j) for i, j in enumerate(J)]
    tc = theme_sheet()
    counts = {k: sum(1 for v in THEME.values() if v[0] == k) for k in TCOL}
    rows = "".join(f'<h2>{html.escape(J[i][0])} <small>{t} Taps</small></h2><img src="{p.name}">' for i, (p, t) in enumerate(made))
    (OUT / "index.html").write_text(f"""<!doctype html><meta charset=utf-8><title>Klickpfade GS Layermaxxing 7.7.1</title>
<style>body{{font-family:sans-serif;background:#f5f1e8;color:#1e3237;max-width:2200px;margin:auto;padding:20px}}img{{max-width:100%;border-radius:10px;box-shadow:0 2px 10px #0002;margin-bottom:30px}}small{{color:#c43}}</style>
<h1>Klickpfade – GS Layermaxxing 7.7.1</h1><p>Echte Renders (Testdaten). Roter Kreis = hier tippen, Zahl = Reihenfolge. Rot/orange umrandet = passt nicht/teils zum aktuellen Theme.</p>
<p>Theme: {counts['FITS']} passen · {counts['PARTLY']} teils · {counts['OLD']} passen nicht mehr · {counts['DEBUG']} Debug</p>
<h2>Theme-Check</h2><img src="theme-check.png">{rows}""", encoding="utf-8")
    json.dump({"journeys": [{"title": j[0], "taps": t, "file": p.name} for j, (p, t) in zip(J, made)], "theme": THEME, "counts": counts},
              open(OUT / "clickpaths.json", "w"), ensure_ascii=False, indent=1)
    for p, t in made: print(p.name, t, Image.open(p).size)
    print("theme", tc, Image.open(tc).size, counts)
