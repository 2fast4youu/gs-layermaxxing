#!/usr/bin/env python3
"""Ein großes Übersichtsbild: alle Screens als Baum von der Inselkarte aus.
Zeilen = Bereiche; links der Eltern-Screen, rechts alles, was man von dort öffnet.
Rahmenfarbe = Theme-Check aus build_clickpaths.py."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from build_clickpaths import THEME, TCOL, TTXT, R, OUT, font, wrap

TH = 380                 # Höhe eines Phone-Screens
S = TH / 2673            # gleiche Skala für alle -> Dialoge bleiben klein
GAP = 40
MINW = 190
INK, MUTED, RED = (30, 50, 55), (110, 115, 115), (225, 70, 40)

# (Zeilentitel, Eltern-Screen, Tap auf Eltern, [(screen|None, Tap-Label, Notiz)])
ROWS = [
 ("Freund antippen", "43-friend-sheet", "Insel eines Freundes", [
   ("06-thread", "Chat", ""), (None, "Brief schreiben", "Brief-Editor"),
   ("45-label-dialog", "Wie nennst du …?", ""), ("44-new-quest-sheet", "+ Neue Quest", ""),
   ("23-besuch", "Insel besuchen", "")]),
 ("Im Chat", "06-thread", "aus Freund-Sheet / Chatliste", [
   ("13-chat-extras", "Extras", ""), ("14-chat-actions", "lange drücken", ""),
   ("50-friend-page", "Name → Freund-Seite", ""), ("40-thread-info-sheet", "⋮ → Info", ""), ("38-letter-room", "✉ Briefe", ""),
   ("41-quest-dialog", "→ Quest", ""), ("42-glossary-term-dialog", "Begriff", ""),
   ("48-ep-proposal-dialog", "Punkte vorschl.", ""), ("49-request-dialog", "Anfrage", "")]),
 ("Meine Insel", "22-meine-insel", "Leiste: Meine Insel", [
   ("30-post", "Post", ""), ("32-leuchtturm", "Leuchtturm", ""),
   ("34-gemeindehaus", "Gemeindehaus", ""), ("33-bibliothek", "Bibliothek", ""),
   ("35-haus", "Figur / Haus", ""), ("26-ort", "Lebensort", ""),
   ("25-bauen", "+ Bauplatz", ""), ("24-deko-wahl", "Bearbeiten → Platz", ""),
   ("28-insel-doppeltipp", "Doppeltipp", ""), ("27-insel-zoom", "Pinch", "")]),
 ("Tiefer in den Orten", None, "", [
   (None, "Post → Umschlag", "Brief offen"), ("47-proof-dialog", "Post → Nachweis", ""),
   (None, "Leuchtt. → Freunde", "Personen-Suche"), ("09-groups-room", "Gemeindeh.→Gruppen", ""),
   ("07-glossary", "Bibliothek", "Inhalt"), ("14-more", "Haus → Einstellungen", ""),
   ("46-recovery-dialog", "Mehr → Konto", "")]),
 ("Hafen", "31-hafen", "Leiste: Hafen", [
   ("08-topics-room", "Themen", ""), ("36-topics-screen", "→ Freund", "")]),
 ("Direkt auf der Karte", None, "", [
   ("37-ep-screen", "⭐ Punkte", ""), ("25-schiff", "Boot antippen", ""),
   ("21-inseln-leer", "ohne Freunde", ""), ("26-inseln-debug", "Debug-Modus an", "")]),
 ("Messenger-Modus / Hauptmenü (auch Leiste „Chats“ in der Insel)", "05-chats", "Tab / Leiste Chats", [
   ("13-login", "vor dem Login", ""), ("12-empty-chats", "leer", ""),
   ("39-spark-room", "Funken", ""), ("03-room", "Raum", ""),
   ("14-more", "Tab Mehr", "")]),
]

def tile(name):
    if name is None:
        w = round(1233 * S); im = Image.new("RGB", (w, TH), (236, 232, 222)); d = ImageDraw.Draw(im)
        for i in range(0, w + TH, 22): d.line([(i, 0), (i - TH, TH)], fill=(224, 218, 205), width=2)
        d.rectangle([2, 2, w - 3, TH - 3], outline=(170, 160, 140), width=2)
        return im
    im = Image.open(R / f"{name}.png").convert("RGB")
    return im.resize((max(1, round(im.width * S)), max(1, round(im.height * S))))

def framed(d, im, x, y, name):
    st = THEME.get(name, ("NONE", ""))[0] if name else "NONE"
    col = TCOL.get(st, (170, 160, 140)); w = {"OLD": 7, "PARTLY": 5}.get(st, 3)
    d.rectangle([x - w, y - w, x + im.width + w - 1, y + im.height + w - 1], outline=col, width=w)
    return st

def build():
    tiles = [(tile(p) if p else None, [(tile(s), s, lab, note) for s, lab, note in kids]) for _, p, _, kids in ROWS]
    leftW = 360                                  # Spalte für Inselkarte
    rowH = TH + 150
    W = leftW + 120 + max((t[0].width if t[0] else 0) + 110 + sum(max(k[0].width, MINW) + GAP for k in t[1]) for t in tiles) + 40
    H = 260 + rowH * len(ROWS) + 60
    img = Image.new("RGB", (W, H), (250, 247, 240)); d = ImageDraw.Draw(img)
    d.text((40, 30), "GS Layermaxxing 7.8 – alle Screens auf einen Blick", fill=INK, font=font(46))
    d.text((40, 92), "Start = Inselkarte. Jede Zeile: links der Screen, rechts alles, was man von dort öffnet (Tap-Text darüber).",
           fill=MUTED, font=font(24, False))
    x0 = 40
    for st, lab in [("FITS", "passt zum Theme"), ("PARTLY", "teils"), ("OLD", "passt NICHT mehr"), ("DEBUG", "Debug")]:
        d.rounded_rectangle([x0, 140, x0 + 30, 170], 6, fill=TCOL[st]); d.text((x0 + 40, 141), lab, fill=INK, font=font(22)); x0 += 70 + d.textlength(lab, font=font(22))
    d.rectangle([x0, 140, x0 + 30, 170], fill=(236, 232, 222), outline=(170, 160, 140), width=2); d.text((x0 + 40, 141), "kein Screenshot", fill=INK, font=font(22))

    top = 230
    # Wurzel: Inselkarte, groß, links mittig
    root = Image.open(R / "20-inseln.png").convert("RGB"); root = root.resize((300, round(root.height * 300 / root.width)))
    ry = top + (rowH * 6 - root.height) // 2
    img.paste(root, (40, ry)); framed(d, root, 40, ry, "20-inseln")
    d.text((40, ry - 40), "Inselkarte (Start)", fill=INK, font=font(26))
    rx = 40 + root.width + 8; rcy = ry + root.height // 2

    for i, ((title, parent, how, _), (pt, kids)) in enumerate(zip(ROWS, tiles)):
        y = top + i * rowH; x = leftW + 120
        d.text((x, y - 18), title, fill=INK, font=font(28))
        cy = y + 60 + TH // 2
        from_root = i in (0, 2, 4, 5)
        if from_root:   # Ast von der Karte
            d.line([(rx, rcy), (rx + 40, rcy), (rx + 40, cy), (x - 12, cy)], fill=(235, 140, 120), width=3)
            d.polygon([(x - 12, cy - 10), (x, cy), (x - 12, cy + 10)], fill=RED)
        elif i == 6:
            d.text((40, y + 50), "eigener Modus,", fill=MUTED, font=font(20, False)); d.text((40, y + 76), "nur ohne Inseln", fill=MUTED, font=font(20, False))
        yy = y + 60
        if pt is not None:
            img.paste(pt, (x, yy)); framed(d, pt, x, yy, parent)
            d.text((x, yy + TH + 8), how, fill=MUTED, font=font(17, False))
            x += pt.width + 30
            d.line([(x, cy), (x + 50, cy)], fill=INK, width=4); d.polygon([(x + 50, cy - 11), (x + 66, cy), (x + 50, cy + 11)], fill=INK)
            x += 80
        for im, s, lab, note in kids:
            slot = max(im.width, MINW)
            ix = x + (slot - im.width) // 2
            ty = yy + (TH - im.height) // 2 if im.height < TH else yy
            d.text((x, yy - 28), lab.replace("⭐", "★")[:21], fill=RED, font=font(17))
            img.paste(im, (ix, ty)); st = framed(d, im, ix, ty, s)
            cap = (s.split("-", 1)[1] if s else note)[:22]
            d.text((x, yy + TH + 8), cap, fill=INK, font=font(15))
            if st in ("OLD", "PARTLY"): d.text((x, yy + TH + 28), TTXT[st], fill=TCOL[st], font=font(15))
            x += slot + GAP
    p = OUT / "00-uebersicht-alle-screens.png"; img.save(p, optimize=True)
    small = img.copy(); small.thumbnail((4096, 4096)); small.save(OUT / "00-uebersicht-alle-screens.jpg", quality=88)
    print(p, img.size)

if __name__ == "__main__":
    build()
