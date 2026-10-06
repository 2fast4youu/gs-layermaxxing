# Vision: Freundschaftstal (Gregors Ziel, Stand 2026-10-04)

Quelle: Gregors handschriftliche Notizen (Foto, 04.10.2026) + Gespräch.
Kernsatz: **Die App visualisiert echte (IRL-)Freundschaften und macht sie interessanter.**
Das Tal ist kein Selbstzweck, sondern ein Spiegel dessen, was zwischen echten Freunden passiert.

## Stil (entschieden 2026-10-04): Inselwelt
- Vorlage: docs/insel-design/00-vorlage-insel-bearbeiten.png (clean, pastell, türkis, weiße runde Karten).
- Ich = Insel in der Mitte mit Hafen (Schwarzes Brett/Quests, Post). Jeder Freund = eigene Insel.
- Insel wächst mit der echten Freundschaft: 1 Neu → 2 Freunde → 3 Vertraut → 4 Beste Freunde.
- Briefe = Boote auf Seewegen: Dampfer (Zeit), Segelboot (Anzahl/gemeinsam, z. B. 2/3), Ruderboot (Zufall).
- Gruppen = Inseln mit Brücken zu einer gemeinsamen Mitte (Lagerfeuer, Gruppenflagge).
- Jeder gestaltet die eigene Insel (Deko für QP/EP, rein visuell); Freunde sehen sie so.
- Screens: 01-archipel, 02-freundschaft, 03-hafen-quests, 04-gruppe, 05-wachstum.
- (Alpendorf-Entwürfe verworfen, liegen in docs/alpendorf-entwuerfe.)

## Funktionale Ziele (aus den Notizen)
1. **2 Orte pro Freundschaft** (jetzt: meine Insel + Insel des Freundes) – mein Ort und der des Freundes, verbunden durch einen Weg.
   Die Freundschaft ist der Raum zwischen den Dörfern.
2. **Briefe visualisieren von A → B** – Briefe sind sichtbar unterwegs zwischen den Dörfern.
3. **Freigabe nach Anzahl, Zeit, Zufall oder beidem zusammen** – sichtbar machen, wann/wie ein Brief aufgeht.
4. **3 Mechanismen = 3 Boten** – jede Freigabeart hat einen eigenen, erkennbaren Boten
   (z. B. Zeit = Postkutsche mit Uhr, Anzahl/gemeinsam = Brieftaube/Wanderer, Zufall = Bergziege o. ä.).
   Backend kennt heute: timed, random, manual, mutual, presence → auf 3 Botentypen abbilden.
5. **Briefsammlung grafisch getrennt** – empfangen / gesendet (und offen/verschlossen) klar getrennt.
6. **Token als eigene Position** – Token/Spielfigur als eigener Ort bzw. eigene Figur im Tal (Bedeutung mit Gregor final klären).
7. **EP → Anreize** – Freundschafts-/Ebenen-Punkte sind Belohnung und Motivation.
8. **Wörterbuch** – von allen anpassbar, überall erreichbar (bereits umgesetzt, Erreichbarkeit beibehalten).
9. **Topics → Schwarzes Brett** – Themen hängen als Zettel am Schwarzen Brett.
10. **RL-Quests / Sidequests** – für eine Gruppe erstellen oder einer Person zuteilen; echte Aktivitäten
    („gemeinsam wandern“, „Grillen organisieren“). Erledigung bringt **QP = Quest-Punkte**.
11. **QP/EP → Dorf erweitern, nicht funktional, sondern visuell** – Punkte kaufen Deko/Ausbau
    (Brunnen, Maibaum, Blumen, Ausbaustufen), keine neuen Funktionen und keine Rechte.

## Leitplanken
- Nur echte Daten visualisieren, nichts erfinden.
- Funktionen bleiben über Gebäude erreichbar; Ausbau mit Punkten ist rein kosmetisch.
- Quests/QP brauchen neue Backend-Endpunkte → zuerst nur Testserver.
- Produktion unverändert.

## Entscheidungen Gregor (2026-10-04, Inselwelt)
- Neue Freundschaft startet als normale kleine Insel (keine Sandbank).
- Wachstum vor allem durch gemeinsam erledigte Quests (QP); EP sind selten und zählen daher stärker.
- Jeder darf Quests erstellen (für einen Freund oder eine Gruppe).
- „Token als eigene Position“: vorerst ignorieren.
