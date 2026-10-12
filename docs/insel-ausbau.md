# Insel-Ausbau

Die Insel beginnt als gemalte Sanddüne ohne Gebäude/Ruinen oder Platzhalter. Alle Grundfunktionen sind trotzdem über **Menü** erreichbar: Schlafplatz (Einstellungen), Briefbaum (Post), Aussichtspalme (Freunde), Wörter am Treibholz (Wörterbuch), Feuerkreis (Gruppen), Strandtreff (Quests/Hafen). Erst gebaute Gebäude stehen auf der Insel; Antippen zeigt ihre Aktionen.

## Normalmodus

- Jeder Funktionsort hat Stufen 0–3: primitive Startstelle, Holzbau, bemalter Ausbau, größerer Meisterbau mit goldener Fahne.
- Ausbaukosten je Schritt: 25 / 60 / 100 Punkte. Mindest-Kontoalter: 0 / 2 / 7 Tage.
- Land hat Stufen 0–5, Kosten 40 / 80 / 140 / 220 / 320 Punkte, Mindestalter 1 / 3 / 7 / 14 / 21 Tage.
- Punkte stammen weiterhin ausschließlich aus bestehenden bestätigten Quests/EP. Ausbau verbraucht den verfügbaren Anteil, nicht die ursprünglichen Quests/EP. Ein später wieder geöffnetes Quest kann den verfügbaren Rest reduzieren, aber gebaute Inseln werden nicht zerstört.
- Persönliche Orte sind seit 7.9.7 **Besondere Deko**, frei platzierbar, 120–200 Punkte teuer und auf drei neue Stücke pro Insel begrenzt (auch kreativ). Vorhandene zusätzliche Stücke und eigene Namen bleiben erhalten; alte Bauplätze werden verlustfrei migriert. `PUT /api/island/places` liefert 410.
- Die Bauleiste ist ein durchgehender horizontaler Streifen: Gebäude · Deko · Besondere Deko · Land. Abschnittsreiter folgen dem Scrollen und springen animiert zum Abschnitt. Kein Bearbeiten-/Fertig-Modus, keine leeren Bauplätze mehr.
- Gebäude und Deko können abgerissen werden (Bestätigungsdialog, keine Punkte-Rückerstattung). Verschieben erfolgt per Langdruck oder Gebäudeaktion.

## Kreativmodus

Der vorhandene Schalter unter Einstellungen/Konto wird wiederverwendet. Testserverrolle **und** serverseitige Kontoberechtigung sind erforderlich. Seit 7.9.4 wirkt der Kreativmodus auf die **echte Insel** (dieselben Gebäude, Orte, Deko und Positionen): Bauen, Ausbauen, Rückbauen, Abreißen, Deko und Land vergrößern/verkleinern sind kostenlos und ohne Tages-/Punktesperren. Ausgegebene Punkte (`spent`) bleiben unverändert. Beim Ausschalten bleibt alles so, wie es gebaut wurde. Die getrennte Kreativwelt aus 7.9.3 bleibt in der Datenbank (`mode='creative'`) unangetastet, wird aber nicht mehr gelesen; normale Inseln sind unverändert.

Ursache des 7.9.3-Totklicks: Die App verwarf jede Inselantwort, deren `construction.mode` nicht dem lokalen Schalter entsprach. Ein Server ohne `construction`-Block (Gregors Testserver ohne island_construction) liefert immer „normal“, also blieb die Insel ungeladen: keine Bauen/Menü-Leiste, keine antippbaren Orte. Jetzt wird nur noch der Besitzer geprüft (`CreativeIslandRegressionTest`).

Besucher und die Freundeskarte zeigen den tatsächlichen aktiven Ausbau des Besitzers; die Größe richtet sich nach dessen Landstufe, nicht nach dem bilateralen Freundschaftslevel. Standort/Status der Figur bleiben privat. Der Modus wird beim Einstellungswechsel serverseitig aktiviert. Ein verspäteter Ausbau einer anderen Welt darf diesen aktiven Modus nicht überschreiben.

## API und Verifikation

`island_construction` speichert den Ledger der echten Insel (`mode='normal'`); `island_mode` merkt sich den aktiven Modus. `POST /api/island/construction` nimmt `mode` (creative = kostenlos), `action` (upgrade, move, expand, demolish, downgrade/shrink nur kreativ, decor_place/decor_move/decor_remove), `building`, `item`, `decor_id`, `position`, `expected_revision`. Ausbau/Expansion prüfen Revision, Punkte und Alter innerhalb `BEGIN IMMEDIATE`; veraltete Wiederholungen liefern 409 ohne weitere Buchung. `activate` ist idempotent und erhöht keine Baurevision. `GET /api/island/{id}?mode=…` darf nur der Besitzer explizit wählen; Freunde erhalten nur den aktiven Zustand. `/api/islands` enthält dieselbe Besitzeransicht für die Karte.

Wasserströmungen sind animiert und werden in der Nahansicht zusammen mit der Insel durch dieselbe Kamera transformiert. Reduzierte Systemanimationen lassen die Wasserströmung stillstehen. Die eigene Insel lädt Zeitfreischaltungen spätestens alle 60 Sekunden neu; verspätete Antworten werden gegen aktuellen Besitzer/Modus geprüft.

Backend-Regressionsfälle stehen in `test_island_evolution.py`; Produktionsansichten und echte Interaktionen in `VillageVisualReview` (61–67). JVM-Render sind Simulationen, keine Handy-Aufnahmen; sie beweisen keine flüssige Animation auf dem Gerät. Der Server muss mit der neuen Migration/API aktualisiert sein, bevor APK-Ausbauaktionen funktionieren.

## Grafikfamilie

Alle Ausbaustufen verwenden die vorhandenen gemalten WebP-Assets (terrain_sand, terrain_home, Deko, Hütten und Gebäude) — keine flachen Ersatzzeichnungen. Die Insel zeigt am Start reine Sandfläche; die natürlichen Startstellen erscheinen nur im Menü und in der Ausbauvorschau, nicht als Platzhaltergebäude auf der Insel. Insel und Ausbauvorschau teilen dieselbe Produktionskomponente `PaintedIslandPlace`.

## 7.9.7-ausbau: Stufenvertrag

`IslandStages` ist die gemeinsame Tabelle für Menü, Namensschild, Gebäudeaktionen, Bauleiste und Ausbauvorschau. Die Funktion bleibt über alle Stufen gleich. Freunde und Post haben auf jeder Stufe verschiedene Icons.

| Funktion | Start (0) | Holzbau (1) | Ausbau (2) | Meisterbau (3) |
| --- | --- | --- | --- | --- |
| Briefe | Briefbaum | Postkasten | Postfächer | Postamt |
| Freunde & Anfragen | Aussichtspalme | Ausguck | Leuchtfeuer | Leuchtturm |
| Wörterbuch | Wörter am Treibholz | Lesedach | Bücherstube | Bibliothek |
| Gruppen | Feuerkreis | Treffhütte | Gemeindehaus | Festhalle |
| Quests & Themen | Strandtreff | Bootssteg | Anleger | Hafen |
| Profil & Einstellungen | Schlafplatz | Holzhütte | Häuschen | Landhaus |

Die Bilder stammen aus der vorhandenen gemalten Assetfamilie; die Festhalle verwendet das Gemeindehaus mit Fahne. Eigene neue Grafiken sind nicht erforderlich. Die große Vorher-/Nachher-Vorschau zeigt nur Ausbau/Rückbau und Schließen, kein Öffnen oder Verschieben. Am Maximum steht „Vollständig ausgebaut“.

Das Baufeld (`server/app/island_grid.json`) ist pro Landstufe derselbe Server-/Client-Vertrag: nur Sand/Wiese innerhalb der Küste. Ungültige Platzierungen werden serverseitig abgelehnt, alte Küstenpositionen deterministisch auf freie Landzellen verschoben. Das Raster zeichnet nur baubare Zellen; belegte Zellen sind dunkler. Die zusätzlich synthetisch gezeichneten braunen Stege sind entfernt (gemalte Hafen-Gebäude bleiben).

Trefferflächen folgen den sichtbaren Alpha-Grenzen der WebP-Sprites (`SpriteBounds`), mit nur 2 dp Rand in Bildschirmgröße. Die kleinen gemalten Namensschilder sitzen am sichtbaren Fuß, bleiben beim Zoomen gleich groß und haben eigene passende Trefferflächen. „Übersicht“ steht auf der eigenen Insel ausschließlich im Menü; Android Zurück bleibt funktionsfähig.

Regressionsbelege: `IslandAusbauTest`, `IslandBuildGridTest`, `IslandFlowTest` und Backend `test_v12_ausbau.py`. `VillageVisualReview.ausbau*` rendert Produktionsansichten nach ausgeführten Interaktionen; JVM-Simulationen, keine Geräteaufnahmen. Deployment und Handyprüfung bleiben separate Schritte.


## 7.9.9-start (Veits Handy-Feedback zu 7.9.8)

- **Inselmenü**: unten keine Pills „Übersicht“, „Bauen“, „Einstellungen“ mehr. Der Kreativ-Schalter bleibt oben. Einstellungen sind über *Mehr* erreichbar, Bauen über den Bauen-Knopf; nur auf der Übersicht bietet das Menü weiter „Meine Insel“.
- **Zurück-Knopf**: „Meine Insel“ hat oben links ein gemaltes Messingschild (`p_plate`, Pfeil + „Übersicht“, 164×52 dp) zur Inselübersicht (`OverviewBackPlate`). Android-Zurück führt ebenfalls zur Übersicht.
- **Start gratis**: Jede Funktion kann ihre Stufe 0 (Schlafplatz/Hängematte, Briefbaum, Aussichtspalme, Wörter am Treibholz, Feuerkreis, Strandtreff) kostenlos und ohne Tagessperre bauen (`action="build"`). Stufe 0 ist keine Ausbaustufe: der Server führt sie in `construction.starters`; `buildings[b]` bleibt die Stufe 1–3 wie bisher, bestehende Spielstände ändern sich nicht, nichts wird erstattet. Erst der Ausbau 0→1 kostet (25 P, wie bisher der erste Schritt). Stufe-0-Stücke halten zu großen Stücken nur den Deko-Abstand (2,4 Zellen / Landfaktor), damit alle sechs auf die Start-Insel passen; wächst ein Stufe-0-Stück beim Ausbau zu nah an ein großes Gebäude, rückt es deterministisch auf die nächste freie Zelle. Alte Clients können weiter `upgrade` von „nichts“ direkt auf Stufe 1 (25 P).
- **Bauleiste**: nur Karten, keine Abschnittsnamen/Trenner mehr im Streifen (die Reiter oben bleiben und folgen dem Scrollen). Baubares in vollen Farben, alles andere in Graustufen mit kurzem Grund („ab Tag 1“, „fehlen 20 P“, „3/3“, „6/6“, „steht · Stufe 1“).
- **Gebäude antippen**: Öffnen, Ausbauen (mit Preis), Abreißen. „Verschieben“ gibt es nur noch per langem Drücken (auch bei Deko).
- **Untermenüs je Stufe**: Post, Hafen, Leuchtturm/Freunde, Wörterbuch, Gruppen und Haus zeigen Schildname, Icon, Kopfbild (gemaltes Stufen-Asset) und Stufenzeile aus `IslandStages.of(b, level)`; einige Texte folgen der Stufe. Der gemalte Raum-Hintergrund bleibt je Funktion gleich (es gibt keine Stufen-Hintergründe).
- **Wörterbuch-Kategorien**: Begriffe (Standard), Witze, Sprüche, Zitate. Filter-Chips oben, Kategorie-Etikett an jedem Eintrag, Auswahl beim Anlegen. Server: Spalte `glossary_terms.category` (Migration: bestehende Einträge → „Begriffe“), Validierung auf die vier Werte, `GET /api/glossary?category=`, `GET /api/glossary/categories`; alte Clients ohne Feld → „Begriffe“.

Tests: `server/tests/test_v13_start.py`, Android `IslandStartUiTest` (rot auf 419348a belegt), `IslandStartModelTest`, angepasste `IslandAusbauTest`/`IslandFlowTest`/`IslandCocControlsTest`, Renders `VillageVisualReview.start69*` (JVM-Simulationen, keine Geräteaufnahmen). Der Testserver braucht das neue Backend, sonst lehnt er `build` ab.
