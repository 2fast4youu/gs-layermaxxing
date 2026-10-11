# Insel-Ausbau

Die Insel beginnt als neu gezeichnete Sanddüne ohne Gebäude/Ruinen. Alle Grundfunktionen sind trotzdem erreichbar: Schlafplatz (Einstellungen), Briefbaum (Post), Aussichtspalme (Freunde), Treibholz (Wörterbuch), Feuerkreis (Gruppen), Strandtreff (Quests/Hafen). Antippen zeigt Öffnen und Ausbau samt Vorschau.

## Normalmodus

- Jeder Funktionsort hat Stufen 0–3: primitive Startstelle, Holzbau, bemalter Ausbau, größerer Meisterbau mit goldener Fahne.
- Ausbaukosten je Schritt: 25 / 60 / 100 Punkte. Mindest-Kontoalter: 0 / 2 / 7 Tage.
- Land hat Stufen 0–5, Kosten 40 / 80 / 140 / 220 / 320 Punkte, Mindestalter 1 / 3 / 7 / 14 / 21 Tage.
- Punkte stammen weiterhin ausschließlich aus bestehenden bestätigten Quests/EP. Ausbau verbraucht den verfügbaren Anteil, nicht die ursprünglichen Quests/EP. Ein später wieder geöffnetes Quest kann den verfügbaren Rest reduzieren, aber gebaute Inseln werden nicht zerstört.
- Neue persönliche Bauplätze werden durch erworbene Landstufen freigegeben. Die persönlichen Orte sind kosmetische Gestaltung dieser gekauften Flächen; sie umgehen keine funktionalen Gebäudeausbaukosten. Existierende gespeicherte Orte bleiben erhalten.
- Deko wird wie Gebäude in der Bauleiste (Shop-Button „Bauen“, Reiter „Deko“) gebaut und frei platziert; die festen „+“-Plätze gibt es nicht mehr (siehe `island-layout.md`). Leere Lebensort-Bauplätze erscheinen nur im Bearbeitungsmodus.
- Gebäude, Deko und Lebensorte können abgerissen werden (Bestätigungsdialog, keine Punkte-Rückerstattung).

## Kreativmodus

Der vorhandene Schalter unter Einstellungen/Konto wird wiederverwendet. Testserverrolle **und** serverseitige Kontoberechtigung sind erforderlich. Seit 7.9.4 wirkt der Kreativmodus auf die **echte Insel** (dieselben Gebäude, Orte, Deko und Positionen): Bauen, Ausbauen, Rückbauen, Abreißen, Deko und Land vergrößern/verkleinern sind kostenlos und ohne Tages-/Punktesperren. Ausgegebene Punkte (`spent`) bleiben unverändert. Beim Ausschalten bleibt alles so, wie es gebaut wurde. Die getrennte Kreativwelt aus 7.9.3 bleibt in der Datenbank (`mode='creative'`) unangetastet, wird aber nicht mehr gelesen; normale Inseln sind unverändert.

Ursache des 7.9.3-Totklicks: Die App verwarf jede Inselantwort, deren `construction.mode` nicht dem lokalen Schalter entsprach. Ein Server ohne `construction`-Block (Gregors Testserver ohne island_construction) liefert immer „normal“, also blieb die Insel ungeladen: keine Bauen/Menü-Leiste, keine antippbaren Orte. Jetzt wird nur noch der Besitzer geprüft (`CreativeIslandRegressionTest`).

Besucher und die Freundeskarte zeigen den tatsächlichen aktiven Ausbau des Besitzers; die Größe richtet sich nach dessen Landstufe, nicht nach dem bilateralen Freundschaftslevel. Standort/Status der Figur bleiben privat. Der Modus wird beim Einstellungswechsel serverseitig aktiviert. Ein verspäteter Ausbau einer anderen Welt darf diesen aktiven Modus nicht überschreiben.

## API und Verifikation

`island_construction` speichert den Ledger der echten Insel (`mode='normal'`); `island_mode` merkt sich den aktiven Modus. `POST /api/island/construction` nimmt `mode` (creative = kostenlos), `action` (upgrade, move, expand, demolish, downgrade/shrink nur kreativ, decor_place/decor_move/decor_remove), `building`, `item`, `decor_id`, `position`, `expected_revision`. Ausbau/Expansion prüfen Revision, Punkte und Alter innerhalb `BEGIN IMMEDIATE`; veraltete Wiederholungen liefern 409 ohne weitere Buchung. `activate` ist idempotent und erhöht keine Baurevision. `GET /api/island/{id}?mode=…` darf nur der Besitzer explizit wählen; Freunde erhalten nur den aktiven Zustand. `/api/islands` enthält dieselbe Besitzeransicht für die Karte.

Wasserströmungen sind animiert und werden in der Nahansicht zusammen mit der Insel durch dieselbe Kamera transformiert. Reduzierte Systemanimationen lassen die Wasserströmung stillstehen. Die eigene Insel lädt Zeitfreischaltungen spätestens alle 60 Sekunden neu; verspätete Antworten werden gegen aktuellen Besitzer/Modus geprüft.

Backend-Regressionsfälle stehen in `test_island_evolution.py`; Produktionsansichten und echte Interaktionen in `VillageVisualReview` (61–67). JVM-Render sind Simulationen, keine Handy-Aufnahmen; sie beweisen keine flüssige Animation auf dem Gerät. Der Server muss mit der neuen Migration/API aktualisiert sein, bevor APK-Ausbauaktionen funktionieren.

## Grafikfamilie

Alle Ausbaustufen verwenden die vorhandenen gemalten WebP-Assets (terrain_sand, terrain_home, Deko, Hütten und Gebäude) — keine flachen Ersatzzeichnungen. Der Start zeigt reine Sandfläche, Hängematte, Palmen/Brief, Bücher am Treibgut, Feuerkreis und Ruderboot; keine Startgebäude oder Ruinen. Insel und Ausbauvorschau teilen dieselbe Produktionskomponente `PaintedIslandPlace`.