#!/usr/bin/env python3
"""Navigations-Graph ("Linien-Spaghetti") + Erreichbarkeits-Check, aktueller Stand 7.7.1.

Jede Kante ist im Code belegt (src). Modus:
  B = in beiden Modi, M = nur Messenger-Modus (ohne Inseln), I = nur Modus "Messenger + Inseln".
Ausgabe: clickpaths/navigation-graph.png, clickpaths/erreichbarkeit.png, clickpaths/navigation.json
"""
from collections import deque
import json, math
from pathlib import Path
from PIL import Image, ImageDraw
from build_clickpaths import THEME, TCOL, TTXT, R, OUT, font, wrap

# ---------- Knoten: id -> (screenshot | None, Name) ----------
N = {
 "login": ("13-login", "Login"),
 # Hauptmenü
 "chats": ("05-chats", "Chats (Tab)"),
 "more": ("14-more", "Mehr (Tab)"),
 "map": ("20-inseln", "Inselkarte (Tab Inseln / Leiste Karte)"),
 "home": ("22-meine-insel", "Meine Insel (Leiste)"),
 "harbour": ("31-hafen", "Hafen (Leiste)"),
 # Messenger
 "thread": ("06-thread", "Chat"),
 "extras": ("13-chat-extras", "Chat-Extras"),
 "actions": ("14-chat-actions", "Reaktionen"),
 "search": (None, "Im Chat suchen"),
 "confirm": (None, "Entfernen / Blockieren"),
 "info": ("40-thread-info-sheet", "Regeln & Info"),
 "rules": (None, "Freundschaftsregeln"),
 "letterroom": ("38-letter-room", "Briefe mit Freund"),
 "topics": ("36-topics-screen", "Themen (Liste)"),
 "questdlg": ("41-quest-dialog", "Chat → Quest"),
 "glossdlg": ("42-glossary-term-dialog", "Begriff"),
 "epprop": ("48-ep-proposal-dialog", "Punkte vorschlagen"),
 "request": ("49-request-dialog", "Anfrage (Pop-up)"),
 "compose": (None, "Brief schreiben"),
 "letteropen": (None, "Brief lesen"),
 "proof": ("47-proof-dialog", "Nachweis"),
 "people": (None, "Leute / Freunde finden"),
 "groupnew": (None, "Gruppe erstellen (Leute)"),
 "sparks": ("39-spark-room", "Funken"),
 "sparknew": (None, "Funke senden"),
 "groups": ("09-groups-room", "Gruppen"),
 "topicshub": ("08-topics-room", "Themen-Übersicht"),
 "glossary": ("07-glossary", "Wörterbuch"),
 "letters": (None, "Briefe-Archiv"),
 "ep": ("37-ep-screen", "Punkte"),
 "profile": (None, "Profil"),
 "appearance": (None, "Darstellung / Modus / Debug"),
 "account": (None, "Konto & Sicherheit"),
 "about": (None, "Über die App"),
 "recovery": ("46-recovery-dialog", "Sicherheitscode"),
 "accounts": (None, "Konten wechseln"),
 "settings": ("14-more", "Einstellungen (aus Haus)"),
 # Inseln
 "friend": ("43-friend-sheet", "Freundes-Sheet"),
 "label": ("45-label-dialog", "Spitzname"),
 "newquest": (None, "Neue Quest"),
 "visit": ("23-besuch", "Insel besuchen"),
 "boat": ("25-schiff", "Boot"),
 "post": ("30-post", "Post"),
 "lighthouse": ("32-leuchtturm", "Leuchtturm"),
 "hall": ("34-gemeindehaus", "Gemeindehaus"),
 "library": ("33-bibliothek", "Bibliothek"),
 "house": ("35-haus", "Mein Haus"),
 "place": ("26-ort", "Lebensort"),
 "build": ("25-bauen", "Bauen"),
 "decor": ("24-deko-wahl", "Deko"),
}
ROOTS = {"M": ["chats", "more"], "I": ["chats", "more", "map"]}   # Hauptmenü pro Modus (Tab-Leiste)
ISLAND_BAR = ["map", "home", "harbour"]                           # Leiste innerhalb der Inselwelt

# ---------- Kanten (von, nach, Tap, Modus, Quelle) ----------
E = [
 ("chats", "thread", "Zeile", "B", "ChatsList.kt:143 onOpen"),
 ("chats", "people", "Neuer Chat", "B", "ChatsList.kt:233/259 onGoPeople"),
 ("chats", "sparks", "Funken", "B", "ChatsList.kt:220"),
 ("chats", "sparknew", "Funke-Knopf", "B", "ChatsList.kt:253"),
 ("chats", "groups", "Chip Gruppen (nur >0)", "B", "ChatsList.kt:212"),
 ("chats", "topicshub", "Chip Themen (nur >0)", "B", "ChatsList.kt:213"),
 ("chats", "request", "automatisch", "B", "LayerHome.kt:571"),
 ("chats", "map", "Tab Inseln", "I", "AppMode.kt:17"),
 ("chats", "more", "Tab Mehr", "B", "AppMode.kt:16"),
 ("more", "chats", "Tab Chats", "B", "AppMode.kt:16"),
 ("chats", "accounts", "Avatar oben", "B", "LayerHome.kt:362 AppChrome / 675"),
 ("more", "accounts", "Avatar oben", "B", "LayerHome.kt:362 AppChrome / 675"),
 ("thread", "letterroom", "Umschlag-Knopf", "B", "ThreadScreen.kt:683"),
 ("thread", "topics", "Menü → Themen", "B", "ThreadScreen.kt:697"),
 ("thread", "search", "Menü → Suchen", "B", "ThreadScreen.kt:698"),
 ("thread", "info", "Name / Menü → Info", "B", "ThreadScreen.kt:655/703"),
 ("thread", "confirm", "Menü → Entfernen", "B", "ThreadScreen.kt:704"),
 ("thread", "extras", "Extras", "B", "ThreadScreen.kt:454"),
 ("thread", "actions", "lange drücken", "B", "ThreadScreen.kt:842"),
 ("thread", "questdlg", "Nachricht → Quest", "B", "ThreadScreen.kt:539"),
 ("thread", "glossdlg", "Begriff", "B", "ChatMedia.kt:321"),
 ("thread", "compose", "Brief-Knopf", "B", "Conversations.kt:114"),
 ("info", "rules", "Freundschaftsregeln", "B", "ThreadScreen.kt:581"),
 ("letterroom", "compose", "Brief schreiben", "B", "ThreadScreen.kt:572"),
 ("letterroom", "letteropen", "Umschlag", "B", "ThreadScreen.kt:575"),
 ("letterroom", "proof", "Nachweis", "B", "ThreadScreen.kt:577"),
 ("letterroom", "epprop", "Punkte vorschlagen", "B", "ThreadScreen.kt:578"),
 ("letterroom", "rules", "Regeln", "B", "ThreadScreen.kt:573"),
 ("people", "thread", "Freund", "B", "LayerHome.kt:536"),
 ("people", "groupnew", "Gruppe erstellen", "B", "LayerHome.kt:1287"),
 ("sparks", "sparknew", "Funke senden", "B", "SparkScreens.kt:139"),
 ("groups", "topics", "Gemeinsame Themen", "B", "GregorScreens.kt:256"),
 ("topicshub", "topics", "Freund / Gruppe", "B", "GregorScreens.kt:226"),
 ("more", "letters", "Briefe", "M", "MoreScreens.kt:210 (im Inselmodus ausgeblendet)"),
 ("more", "ep", "Punkte", "M", "MoreScreens.kt:211 (im Inselmodus ausgeblendet)"),
 ("more", "glossary", "Wörterbuch", "M", "MoreScreens.kt:212 (im Inselmodus ausgeblendet)"),
 ("more", "profile", "Profil", "B", "MoreScreens.kt:198"),
 ("more", "appearance", "Darstellung", "B", "MoreScreens.kt:214"),
 ("more", "account", "Konto & Sicherheit", "B", "MoreScreens.kt:215"),
 ("more", "about", "Über die App", "B", "MoreScreens.kt:216"),
 ("settings", "accounts", "Konten wechseln", "I", "MoreScreens.kt:169 / LayerHome.kt:493"),
 ("settings", "map", "← Inseln", "I", "MoreScreens.kt:168"),
 ("settings", "profile", "Profil", "I", "MoreScreens.kt:198"),
 ("settings", "appearance", "Darstellung", "I", "MoreScreens.kt:214"),
 ("settings", "account", "Konto & Sicherheit", "I", "MoreScreens.kt:215"),
 ("settings", "about", "Über die App", "I", "MoreScreens.kt:216"),
 ("profile", "recovery", "Code", "B", "MoreScreens.kt:161"),
 ("account", "recovery", "Code", "B", "MoreScreens.kt:163"),
 ("letters", "letteropen", "Umschlag", "M", "MoreScreens.kt:158"),
 ("letters", "proof", "Nachweis", "M", "MoreScreens.kt:158"),
 # Inselwelt
 ("map", "friend", "Insel antippen", "I", "IslandWorld.kt:297"),
 ("map", "home", "Meine Insel", "I", "IslandWorld.kt:340"),
 ("map", "harbour", "Hafen", "I", "IslandWorld.kt:341"),
 ("map", "ep", "Punkte-Pille (nur >0)", "I", "IslandWorld.kt:296"),
 ("map", "post", "Brief-Pille (nur >0)", "I", "IslandWorld.kt:300"),
 ("map", "boat", "Boot", "I", "IslandWorld.kt:1187"),
 ("map", "people", "Freunde finden (leer)", "I", "IslandWorld.kt:313"),
 ("map", "chats", "zurück", "I", "IslandWorld.kt:291 onBack"),
 ("home", "map", "Karte", "I", "IslandWorld.kt:339"),
 ("home", "harbour", "Hafen", "I", "IslandWorld.kt:341"),
 ("harbour", "map", "Karte", "I", "IslandWorld.kt:339"),
 ("harbour", "home", "Meine Insel", "I", "IslandWorld.kt:340"),
 ("friend", "thread", "Chat", "I", "IslandWorld.kt:919"),
 ("friend", "compose", "Brief schreiben", "I", "IslandWorld.kt:923"),
 ("friend", "label", "Wie nennst du …?", "I", "IslandWorld.kt:898"),
 ("friend", "newquest", "+ Neue", "I", "IslandWorld.kt:399"),
 ("friend", "visit", "Insel besuchen", "I", "IslandWorld.kt:917"),
 ("friend", "topics", "Themen mit …", "I", "IslandWorld.kt:937"),
 ("visit", "thread", "Schreiben → Chat", "I", "IslandWorld.kt:327"),
 ("visit", "compose", "Schreiben → Brief", "I", "IslandWorld.kt:328"),
 ("home", "post", "Post", "I", "IslandWorld.kt:265"),
 ("home", "lighthouse", "Leuchtturm", "I", "IslandWorld.kt:265"),
 ("home", "hall", "Gemeindehaus", "I", "IslandWorld.kt:265"),
 ("home", "library", "Bibliothek", "I", "IslandWorld.kt:265"),
 ("home", "house", "Figur", "I", "IslandWorld.kt:270"),
 ("home", "place", "Lebensort", "I", "IslandWorld.kt:268"),
 ("home", "build", "+ Bauplatz", "I", "IslandWorld.kt:268"),
 ("home", "decor", "Bearbeiten → Platz", "I", "IslandWorld.kt:332 / IslandHome.kt:153"),
 ("post", "compose", "Brief schreiben", "I", "PlaceScenes.kt:239/276"),
 ("post", "letteropen", "Umschlag", "I", "IslandWorld.kt:355"),
 ("harbour", "newquest", "Quest anpinnen", "I", "PlaceScenes.kt:357"),
 ("harbour", "topicshub", "Themen", "I", "PlaceScenes.kt:380"),
 ("lighthouse", "people", "Freunde finden", "I", "PlaceScenes.kt:455"),
 ("lighthouse", "friend", "Freund", "I", "IslandWorld.kt:363"),
 ("hall", "groups", "Gruppen verwalten", "I", "PlaceScenes.kt:587"),
 ("hall", "newquest", "Gruppen-Quest", "I", "IslandWorld.kt:367"),
 ("library", "glossary", "(Wörterbuch drin)", "I", "IslandWorld.kt:365"),
 ("house", "settings", "Profil & Einstellungen", "I", "PlaceScenes.kt:656 → LayerHome.kt:434/551"),
 ("house", "decor", "Insel bearbeiten", "I", "IslandWorld.kt:375"),
]

# ---------- Erreichbarkeit ----------
def bfs(mode, roots):
    adj = {}
    for a, b, *_ , m, _src in [(e[0], e[1], e[2], e[3], e[4]) for e in E]:
        if m == "B" or m == mode: adj.setdefault(a, []).append(b)
    dist = {r: 0 for r in roots}; q = deque(roots)
    while q:
        u = q.popleft()
        for v in adj.get(u, []):
            if v not in dist: dist[v] = dist[u] + 1; q.append(v)
    return dist

dM = bfs("M", ROOTS["M"])                 # Messenger-Modus, Start Chats/Mehr
dI = bfs("I", ROOTS["I"])                 # Inselmodus, Tab-Leiste
dW = bfs("I", ISLAND_BAR)                 # in der Inselwelt (nur deren Leiste)

FEATURES = [  # wichtige Funktionen -> Ziel-Knoten
 ("Chat öffnen", "thread"), ("Brief schreiben", "compose"), ("Brief lesen", "letteropen"),
 ("Alle Briefe (Archiv / Post)", ["letters", "post"]), ("Freunde finden / hinzufügen", "people"),
 ("Anfrage beantworten", "request"), ("Funke senden", "sparknew"), ("Thema notieren", "topics"),
 ("Gruppen", "groups"), ("Gruppe erstellen", "groupnew"), ("Quest anlegen", ["newquest", "questdlg"]), ("Quest aus Chat", "questdlg"),
 ("Punkte", "ep"), ("Wörterbuch", ["glossary", "library"]), ("Freundschaftsregeln", "rules"),
 ("Darstellung / Modus / Debug", "appearance"), ("Konto & Sicherheit", "account"), ("Konto wechseln", "accounts"),
 ("Profil", "profile"), ("Spitzname", "label"), ("Status „Ich bin hier“", "place"), ("Deko", "decor"),
]

def t(d, k):
    vs = [d[x] for x in ([k] if isinstance(k, str) else k) if x in d]
    return min(vs) if vs else None

# ---------- Graph zeichnen ----------
TH = 230; S = TH / 2673
COLS_GAP, ROW_GAP = 300, 80
SUB = {}
BACK = set()
MCOL = {"B": (70, 80, 85), "M": (50, 110, 210), "I": (225, 120, 25)}

def node_img(k):
    s, _ = N[k]
    if s:
        im = Image.open(R / f"{s}.png").convert("RGB")
        return im.resize((max(1, round(im.width * S)), max(1, round(im.height * S))))
    w = round(1233 * S); im = Image.new("RGB", (w, TH), (236, 232, 222)); d = ImageDraw.Draw(im)
    for i in range(0, w + TH, 18): d.line([(i, 0), (i - TH, TH)], fill=(222, 216, 203), width=2)
    for j, line in enumerate(["kein", "Screenshot"]):
        f = font(13); d.text(((w - d.textlength(line, font=f)) / 2, TH / 2 - 16 + j * 17), line, fill=(140, 130, 110), font=f)
    return im

def layout():
    # Spalte = kürzeste Tap-Tiefe über alle Modi (Hauptmenü = 0); Login ganz links
    depth = {}
    for k in N:
        ds = [x[k] for x in (dM, dI) if k in x]
        depth[k] = min(ds) if ds else 4
    depth["login"] = -1
    for k in ["home", "harbour"]: depth[k] = 1
    raw = {}
    for k, dd in depth.items(): raw.setdefault(dd, []).append(k)
    cols = {}
    for dd in sorted(raw):
        ks = raw[dd]; n = (len(ks) + 6) // 7
        for j in range(n): cols[dd + j / 10] = ks[j::n]
    SUB.update({c: (round(c) if c == int(c) else int(c), c != int(c)) for c in cols})
    order = {}
    for c in sorted(cols):
        def bary(k):
            ps = [order[a] for a, b, *_ in E if b == k and a in order]
            return sum(ps) / len(ps) if ps else list(N).index(k)
        cols[c].sort(key=bary)
        for i, k in enumerate(cols[c]): order[k] = i + 0.5 * (c % 2)
    return depth, cols

def draw_graph():
    depth, cols = layout()
    imgs = {k: node_img(k) for k in N}
    colw = 110 + COLS_GAP
    maxrows = max(len(v) for v in cols.values())
    H = 260 + maxrows * (TH + ROW_GAP) + 140
    W = 80 + len(cols) * colw + 60
    img = Image.new("RGB", (W, H), (250, 247, 240)); d = ImageDraw.Draw(img, "RGBA")
    pos = {}
    for ci, c in enumerate(sorted(cols)):
        ks = cols[c]; colH = len(ks) * (TH + ROW_GAP)
        y0 = 240 + (maxrows * (TH + ROW_GAP) - colH) // 2
        for i, k in enumerate(ks):
            x = 60 + ci * colw; y = y0 + i * (TH + ROW_GAP)
            pos[k] = (x, y, imgs[k].width, imgs[k].height)
    # Ports über die Kartenkante verteilen, damit Bündel unterscheidbar bleiben
    fwd = [e for e in E if not (depth[e[1]] < depth[e[0]] or (depth[e[1]] == depth[e[0]] and pos[e[1]][0] <= pos[e[0]][0]))]
    backs = [e for e in E if e not in fwd]
    outs, ins = {}, {}
    for e in fwd: outs.setdefault(e[0], []).append(e); ins.setdefault(e[1], []).append(e)
    for dct, key in ((outs, 1), (ins, 0)):
        for k, es in dct.items(): es.sort(key=lambda e: pos[e[key]][1])
    labels = []
    for e in fwd:
        a_, b_, lab, m, src = e
        xa, ya, wa, ha = pos[a_]; xb, yb, wb, hb = pos[b_]
        oa = outs[a_].index(e); na = len(outs[a_]); ib = ins[b_].index(e); nb = len(ins[b_])
        p0 = (xa + wa + 2, ya + ha * (oa + 1) / (na + 1)); p3 = (xb - 4, yb + hb * (ib + 1) / (nb + 1))
        dx = max(60, (p3[0] - p0[0]) * .5); c1, c2 = (p0[0] + dx, p0[1]), (p3[0] - dx, p3[1])
        pts = [tuple((1 - t) ** 3 * p0[i] + 3 * (1 - t) ** 2 * t * c1[i] + 3 * (1 - t) * t ** 2 * c2[i] + t ** 3 * p3[i] for i in (0, 1)) for t in [j / 40 for j in range(41)]]
        d.line(pts, fill=MCOL[m] + (150,), width=3)
        ex, ey = pts[-1]; px, py = pts[-3]; ang = math.atan2(ey - py, ex - px)
        d.polygon([(ex, ey), (ex - 14 * math.cos(ang - .45), ey - 14 * math.sin(ang - .45)), (ex - 14 * math.cos(ang + .45), ey - 14 * math.sin(ang + .45))], fill=MCOL[m])
        labels.append((p0[0] + 8, p0[1] - 8, lab, m))
    BACK.update({(e[0], e[1]) for e in backs})
    for k, (x, y, w, h) in pos.items():
        img.paste(imgs[k], (x, y))
        st = THEME.get(N[k][0], ("NONE",))[0] if N[k][0] else "NONE"
        bw = {"OLD": 6, "PARTLY": 4}.get(st, 3)
        main = k in ROOTS["I"] or k in ISLAND_BAR
        d.rectangle([x - bw, y - bw, x + w + bw - 1, y + h + bw - 1], outline=TCOL.get(st, (160, 150, 130)), width=bw)
        if main: d.rectangle([x - bw - 6, y - bw - 6, x + w + bw + 5, y + h + bw + 5], outline=(30, 50, 55), width=3)
        name = N[k][1]
        for j, line in enumerate(wrap(name, 22)[:2]):
            d.text((x, y + h + 8 + j * 18), line, fill=(30, 50, 55), font=font(15))
        tag = []
        if k in dM: tag.append(f"M {dM[k]}")
        if k in dI: tag.append(f"I {dI[k]}")
        d.text((x + w + 6, y), "\n".join(tag), fill=(120, 120, 120), font=font(13))
    for lx, ly, lab, m in labels:
        f = font(12); tw = d.textlength(lab, font=f)
        d.rounded_rectangle([lx - 3, ly - 1, lx + tw + 3, ly + 15], 4, fill=(250, 247, 240, 235))
        d.text((lx, ly), lab, fill=MCOL[m], font=f)
    d.text((60, 28), "Navigation GS Layermaxxing 7.7.1 – alle Wege (aus dem Code)", fill=(30, 50, 55), font=font(44))
    d.text((60, 86), "Spalte = wie viele Taps vom Hauptmenü entfernt (0 = Tab/Leiste, dick umrandet). Rückwege (zurück/Leiste) weggelassen. Zahl neben Karte: Taps im Modus M / I. Tap-Text steht am Start der Linie.",
           fill=(100, 105, 105), font=font(22, False))
    x0 = 60
    for m, lab in [("B", "beide Modi"), ("M", "nur Messenger-Modus"), ("I", "nur Modus Messenger + Inseln")]:
        d.line([(x0, 150), (x0 + 50, 150)], fill=MCOL[m], width=6); d.text((x0 + 60, 138), lab, fill=(30, 50, 55), font=font(20)); x0 += 90 + d.textlength(lab, font=font(20))
    for st, lab in [("FITS", "Theme passt"), ("PARTLY", "teils"), ("OLD", "passt nicht")]:
        d.rectangle([x0, 136, x0 + 28, 164], outline=TCOL[st], width=5); d.text((x0 + 38, 138), lab, fill=(30, 50, 55), font=font(20)); x0 += 70 + d.textlength(lab, font=font(20))
    groups = {}
    for ci, c in enumerate(sorted(cols)): groups.setdefault(int(c // 1), []).append(ci)
    for c, cis in groups.items():
        x1, x2 = 60 + cis[0] * colw, 60 + cis[-1] * colw + 110
        txt = "Login" if c < 0 else ("Hauptmenü" if c == 0 else f"{c} Tap{'s' if c > 1 else ''} entfernt")
        d.line([(x1, 228), (x2, 228)], fill=(190, 180, 160), width=3)
        d.text(((x1 + x2) / 2 - d.textlength(txt, font=font(24)) / 2, 192), txt, fill=(150, 140, 120), font=font(24))
    p = OUT / "navigation-graph.png"; img.save(p, optimize=True)
    j = img.copy(); j.thumbnail((5000, 5000)); j.save(OUT / "navigation-graph.jpg", quality=86)
    return p, img.size

def draw_table():
    rows = []
    for name, k in FEATURES:
        a, b, c = t(dM, k), t(dI, k), t(dW, k)
        rows.append((name, a, b, c))
    W, rh = 1500, 48
    img = Image.new("RGB", (W, 230 + rh * len(rows) + 260), (250, 247, 240)); d = ImageDraw.Draw(img)
    d.text((40, 30), "Erreichbarkeit: Taps vom Hauptmenü bis zur Funktion", fill=(30, 50, 55), font=font(36))
    d.text((40, 82), "0 = direkt in der Tab-Leiste · ✗ = in diesem Modus gar nicht erreichbar · grün ≤1 · gelb 2 · rot ≥3 oder ✗", fill=(100, 105, 105), font=font(20, False))
    hx = [40, 560, 860, 1160]
    for x, h in zip(hx, ["Funktion", "Messenger-Modus", "Messenger + Inseln", "nur in der Inselwelt"]):
        d.text((x, 150), h, fill=(30, 50, 55), font=font(22))
    def cell(v):
        if v is None: return "✗", (200, 40, 40)
        return str(v), (46, 140, 90) if v <= 1 else (222, 150, 30) if v == 2 else (200, 40, 40)
    for i, (name, a, b, c) in enumerate(rows):
        y = 200 + i * rh
        if i % 2 == 0: d.rectangle([30, y - 6, W - 30, y + rh - 10], fill=(240, 235, 224))
        d.text((hx[0], y), name, fill=(30, 50, 55), font=font(21, False))
        for x, v in zip(hx[1:], (a, b, c)):
            s, col = cell(v); d.rounded_rectangle([x, y - 2, x + 64, y + 30], 8, fill=col); d.text((x + 32 - d.textlength(s, font=font(20)) / 2, y + 2), s, fill="white", font=font(20))
    y = 220 + rh * len(rows)
    notes = [
     "Im Inselmodus blendet „Mehr“ Briefe, Punkte und Wörterbuch aus – sie leben dann nur noch auf der Insel (Post, Punkte-Pille, Bibliothek).",
     "Chatliste bekommt onGlossary/onValley, nutzt sie aber nicht (ChatsList.kt:148) → aus den Chats kein Weg zu Wörterbuch/Inseln.",
     "Freundschaftsregeln sind am tiefsten (3–4 Taps: Chat → ⋮ → Regeln & Info → Freundschaftsregeln).",
     "Punkte & Brief-Abkürzung auf der Karte erscheinen nur, wenn es etwas zu zeigen gibt (Punkte > 0, neue Briefe > 0).",
     "Gruppen/Themen-Chips in den Chats nur, wenn schon welche existieren.",
     "In der Inselwelt gibt es keinen direkten Weg zu Chats/Mehr außer ‹ zurück bzw. über das Haus.",
    ]
    for n_ in notes:
        d.text((40, y), "• " + n_, fill=(60, 70, 70), font=font(19, False)); y += 32
    p = OUT / "erreichbarkeit.png"; img.save(p, optimize=True); return p, rows

if __name__ == "__main__":
    for a, b, *_ in E: assert a in N and b in N, (a, b)
    g = draw_graph(); tb, rows = draw_table()
    json.dump({"nodes": {k: {"screenshot": v[0], "name": v[1]} for k, v in N.items()},
               "edges": [dict(zip(["from", "to", "tap", "mode", "src"], e)) for e in E],
               "taps_messenger": dM, "taps_islands": dI, "taps_inside_world": dW,
               "features": [dict(feature=r[0], messenger=r[1], islands=r[2], world=r[3]) for r in rows]},
              open(OUT / "navigation.json", "w"), ensure_ascii=False, indent=1)
    print(g, tb, len(N), "Knoten", len(E), "Kanten")
    unreachM = [k for k in N if k not in dM and k != "login"]; unreachI = [k for k in N if k not in dI and k != "login"]
    print("nicht erreichbar M:", unreachM); print("nicht erreichbar I:", unreachI)
    for r in rows: print(r)
