# Konzept: Insel-Ausbau mit Level-Stufen (CoC-Gefühl)

Stand: Checkout `gregor-erweiterung` · auf Initiative von Gerfried · Idee, noch nicht implementiert.

## Kernidee

Die Insel wird vom Foto zum Wachstum. Alles auf der eigenen Insel startet **ruinös und primitiv**
(ein paar Baumstämme, Lehm, strohgedeckte Hütte) und wächst über sichtbare Stufen zu dem
handgemalten Gouache-Look von heute. Fortschritt ist damit *an der Insel selbst ablesbar*,
nicht nur an einer Zahl im Menü.

**Eiserne Regel:** Der Zugang zu den Main-Funktionen bleibt zu 100 % ungegate't.
Nur die *Optik und der Ausbaustand* sind ein Level-System. Niemand kann durch ein
fehlendes Level einen Brief nicht öffnen, eine Quest nicht erledigen oder eine Gruppe
nicht verwalten.

## Was gekappt wird (nur Darstellung) vs. was immer frei ist

| Funktion | Immer frei | Sichtbar ausbaubar |
| --- | --- | --- |
| Briefe öffnen/schreiben (Post) | ✔ immer | Postamt-Optik |
| Quests/Hitze Liste (Hafen) | ✔ immer | Steg + Tafel-Optik |
| Freundesanfragen (Leuchtturm) | ✔ immer | Turm-Optik |
| Gruppen/Runder Tisch (Gemeindehaus) | ✔ immer | Hütten-Optik |
| Wörterbuch (Bibliothek) | ✔ immer | Regal-/Haus-Optik |
| Mein Haus / Lebensorte | ✔ immer | Haus + Bauplätze (heute: Land wächst mit QP) |
| Chats verlassen / Messenger | ✔ immer | — |

## Level-Modell (datapart, ohne neue Server-Gates)

Neues Feld pro Insel-Objekt: `level: Int` (1..N). Reicht im bestehenden
`HomeIsland.places`-Blob bzw. als neue Spalte `place_levels` — Migration additiv,
bestehende Nutzer behalten ihren Stand (bestehende Orte landen auf Level 2,
damit niemand einen "Rückschritt" sieht; neue Accounts starten bei 1).

Kosten-Treppe (Vorschlag, QP = Quest-Punkte, dieselbe Währung wie die Land-Expansion):

- Gebäude Level 1→2→3→4: 25 / 80 / 200 QP
- Land-Aufschüttung (neue Bauplätze): bleibt wie heute (`plot_unlocks`: 40/80/120/200/300/450)
- Deko: wie heute (`DecorItem.unlockAt`)

Drei Fortschritts-Achsen, die zusammen die Insel formen:
1. **Land** (Aufschüttungen) — existiert schon
2. **Gebäude-Level** — NEU, die eigentliche Idee
3. **Deko** — existiert schon

## UX im Clash-of-Clans-Stil

1. **Antippen = Baumenü.** Gebäude antippen öffnet statt direkt den Raum einen
   kleinen Bau-Kontext: aktuelles Level, nächstes Level (Name + Kosten + was sich
   optisch ändert), Buttons **Ausbauen** / **Bauen verschieben** / **Öffnen**.
   "Öffnen" führt in den bestehenden Raum → Main-Funktionen bleiben 1 Tap entfernt.
2. **Baustellen-Logik.** Ausbauen startet eine Baustelle: Gerüst + Baufortschritts-Balken
   am Gebäude, fertig entweder sofort (QP sofort abgebucht, ehrlicher Stand) oder
   nach kurzer Bauzeit (CoC-Gefühl). Empfehlung: **sofort fertig**, CoC-Bauzeiten
   wären bei einer Freundschafts-App eher Strafe als Spannung.
3. **Drag-to-move.** Im Bearbeitungsmodus (gibt es für Deko schon) Gebäude auf
   benachbarte freie Parzellen ziehen. Bestehende `onPlot`/`plotPick`-Mechanik
   wird dafür erweitert.
4. **Fortschritts-HUD.** Feste Kachel oben: Level-Stern am Gebäudeschild
   ("Postamt ★2"), im Baumenü ein Ausbaustreifen "3/6 Gebäude ausgebaut".
5. **"Was bekomme ich?"-Vorschau.** Im Baumenü Mini-Sprite des nächsten Levels —
   der eigentliche CoC-Sog: man *sieht*, was man freischaltet.

## Grafikplan (machbar mit dem bestehenden Stil)

Jedes der 6 Gebäude braucht 3–4 Varianten im selben Gouache-Stil:

- Stufe 1 "Baumstämme": primitives Lager/Floß/Steinmann, strohig, schiefe Balken
- Stufe 2 "Fachwerk": behauenes Holz, kleines Dach
- Stufe 3 "Ausgebaut": heute-Look (existiert bereits → 1 Sprite pro Gebäude gespart)
- optional Stufe 4 "Pomp": Turm/Umbau mit goldenen Akzenten

Mehraufwand also ~2 Sprites pro Gebäude (6 × 2 = 12 PNGs), plus 1 Gerüst-Sprite,
1 Baubalken-Overlay, 1 Niveau-Stern. Machbar in einem Render-Durchlauf wie die
Icon-Aktion 7.9.

## Server

- Additive Migration: `place_levels` (JSON, Default = Level 2 für bestehende Orte).
- `POST /api/island/upgrade` mit dem bestehenden QP-Ledger (kreativer Modus auf dem
  Testserver baut weiterhin kostenlos — Entitlement existiert schon).
- Keine neuen bilateralen Gates, keine EP-Anfragen: Insel-Ausbau ist **privater Fortschritt**,
  genau wie heute die eigene Insel. Freunde sehen nur das Ergebnis (ihr Besuch).

## Warum das die jetzige UX schlägt

- Heute: Insel sieht ab Tag 1 fertig aus, Land-Wachstum ist die einzige sichtbare Achse,
  Bauen ist ein Bottom-Sheet ohne Vorfreude.
- Neu: jede Quest zahlt auf etwas *Sichtbares* ein; die Insel erzählt die Geschichte der
  Freundschaft (frühe Briefe = Sturm-Hütte, später = gemauertes Postamt).
- CoC-Elemente die wir übernehmen: Level-Sterne, Baumenü mit Preview, Drag-to-move.
  Die wir weglassen: Bauzeiten-Strafen, Ressourcen-Knappheit jenseits QP, Multiplayer-Druck.

## Umsetzung in vertikalen Scheiben

1. **Scheibe 1 (rein visuell):** Level-Feld + Stufe-1-Sprites; alle Level-1-Optik für
   neue Accounts, Stufen noch ohne Ausbau-Klick. Render-Tests = jetzige VillageVisualReview.
2. **Scheibe 2:** Baumenü + Ausbau-Endpunkt + QP-Abbuchung + Baustelle-Overlay.
3. **Scheibe 3:** Drag-to-move im Editiermodus + Ausbaustreifen-HUD.
4. **Scheibe 4 (optional):** Stufe-4-Pomp, Besucherblick "so wächst die Insel des Freundes".

Jede Scheibe mit bestehenden Gates verifizierbar (pytest + testDebugUnitTest/lintDebug/assembleDebug).
