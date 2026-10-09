# Insel-Ausbau

Die Insel beginnt als neu gezeichnete Sanddüne ohne Gebäude/Ruinen. Alle Grundfunktionen sind trotzdem erreichbar: Schlafplatz (Einstellungen), Briefbaum (Post), Aussichtspalme (Freunde), Treibholz (Wörterbuch), Feuerkreis (Gruppen), Strandtreff (Quests/Hafen). Antippen zeigt Öffnen und Ausbau samt Vorschau.

## Normalmodus

- Jeder Funktionsort hat Stufen 0–3: primitive Startstelle, Holzbau, bemalter Ausbau, größerer Meisterbau mit goldener Fahne.
- Ausbaukosten je Schritt: 25 / 60 / 100 Punkte. Mindest-Kontoalter: 0 / 2 / 7 Tage.
- Land hat Stufen 0–5, Kosten 40 / 80 / 140 / 220 / 320 Punkte, Mindestalter 1 / 3 / 7 / 14 / 21 Tage.
- Punkte stammen weiterhin ausschließlich aus bestehenden bestätigten Quests/EP. Ausbau verbraucht den verfügbaren Anteil, nicht die ursprünglichen Quests/EP. Ein später wieder geöffnetes Quest kann den verfügbaren Rest reduzieren, aber gebaute Inseln werden nicht zerstört.
- Neue persönliche Bauplätze werden durch erworbene Landstufen freigegeben. Die persönlichen Orte sind kosmetische Gestaltung dieser gekauften Flächen; sie umgehen keine funktionalen Gebäudeausbaukosten. Existierende gespeicherte Orte bleiben erhalten.
- Deko bleibt über Bearbeiten → Deko editierbar. Leere Bauplätze erscheinen nur im Bearbeitungsmodus.

## Kreativmodus

Der vorhandene Schalter unter Einstellungen/Konto wird wiederverwendet. Testserverrolle **und** serverseitige Kontoberechtigung sind erforderlich. Eine eigene persistente Kreativwelt erlaubt kostenloses Ausbauen/Rückbauen und Landvergrößern/Verkleinern ohne normale Punkte oder Zeitgates zu verändern. Normale persönliche Orte/Deko werden dort nicht eingeblendet; die sechs Funktionsgebäude bleiben frei gestaltbar.

Besucher und die Freundeskarte zeigen den tatsächlichen aktiven Ausbau des Besitzers; die Größe richtet sich nach dessen Landstufe, nicht nach dem bilateralen Freundschaftslevel. Standort/Status der Figur bleiben privat. Der Modus wird beim Einstellungswechsel serverseitig aktiviert. Ein verspäteter Ausbau einer anderen Welt darf diesen aktiven Modus nicht überschreiben.

## API und Verifikation

`island_construction` speichert zwei unabhängige JSON-Ledger je Benutzer; `island_mode` veröffentlicht die aktive Welt. `POST /api/island/construction` nimmt `mode`, `action`, `building`, `expected_revision`. Ausbau/Expansion prüfen Revision, Punkte und Alter innerhalb `BEGIN IMMEDIATE`; veraltete Wiederholungen liefern 409 ohne weitere Buchung. `activate` ist idempotent und erhöht keine Baurevision. `GET /api/island/{id}?mode=…` darf nur der Besitzer explizit wählen; Freunde erhalten nur den aktiven Zustand. `/api/islands` enthält dieselbe Besitzeransicht für die Karte.

Wasserströmungen sind animiert und werden in der Nahansicht zusammen mit der Insel durch dieselbe Kamera transformiert. Reduzierte Systemanimationen lassen die Wasserströmung stillstehen. Die eigene Insel lädt Zeitfreischaltungen spätestens alle 60 Sekunden neu; verspätete Antworten werden gegen aktuellen Besitzer/Modus geprüft.

Backend-Regressionsfälle stehen in `test_island_evolution.py`; Produktionsansichten und echte Interaktionen in `VillageVisualReview` (61–67). JVM-Render sind Simulationen, keine Handy-Aufnahmen; sie beweisen keine flüssige Animation auf dem Gerät. Der Server muss mit der neuen Migration/API aktualisiert sein, bevor APK-Ausbauaktionen funktionieren.

## Grafikfamilie

Alle Ausbaustufen verwenden die vorhandenen gemalten WebP-Assets (terrain_sand, terrain_home, Deko, Hütten und Gebäude) — keine flachen Ersatzzeichnungen. Der Start zeigt reine Sandfläche, Hängematte, Palmen/Brief, Bücher am Treibgut, Feuerkreis und Ruderboot; keine Startgebäude oder Ruinen. Insel und Ausbauvorschau teilen dieselbe Produktionskomponente `PaintedIslandPlace`.
