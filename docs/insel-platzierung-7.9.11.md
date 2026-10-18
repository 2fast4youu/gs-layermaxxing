# Insel-Platzierung 7.9.11-raster (versionCode 71)

## Abschließender Prüfstand durch Hermes

- Vollständiger Android-Gate nach der Nachkorrektur: **BUILD SUCCESSFUL**, `testDebugUnitTest lintDebug assembleDebug`; 481 Tests, davon **313 bestanden**, 168 opt-in übersprungen, keine Fehler. Die 32 opt-in-Bildtests wurden separat erfolgreich ausgeführt.
- Backend: **120 bestanden**. Der Serverquellstand entspricht bytegenau dem geprüften Live-Release.
- APK: versionCode 71, versionName `7.9.11-raster`, Paket `at.gregor.layermaxxing.gerfried`; Signatur geprüft.
- Gerfried-Testserver nach SQLite-Sicherung und Migrationsprobe aktualisiert: bestehende Tabellen/Zeilenzahlen erhalten, Integrität okay, Fremdschlüsselzustand nicht verschlechtert. Persistenter Datenbankpfad unverändert. Öffentlicher `/api/server-info` bestätigt `grid_version: 4`, `/health` ist okay.
- Gregors separater Testserver wurde nicht umgestellt; für dessen alte Regeln verwendet die App die Versionskompatibilität.
- Kein echter Telefon-Ende-zu-Ende-Test. Die Bilder sind echte Robolectric-UI-Renderings mit Testdaten, keine Gerätescreenshots.

Die folgenden Diagnose- und Zwischenstandsabschnitte dokumentieren die Entstehung; Aussagen über den damals alten Live-Server sind historisch.

## Behobene Ursache

- **Versionsblindheit:** Der aktive Gerfried-Testserver (Release `gerfried-5c75f8b-…`) validiert noch die Kreisabstände von 7.9.7–7.9.9 und meldet `grid_version: 2`. Die APK 7.9.10 rechnete aber immer mit den Rauten von 7.9.10 (Version 3), weil `ApiClient.IslandConstruction`/`parseHomeIsland` `grid_version` gar nicht gelesen haben. Folge: grüne Vorschau und ✓ an Orten, die der Server mit 422 ablehnt.
- **Schein-Bebaubarkeit:** Das Raster zeichnete jede Bauzelle hell und färbte nur den gehaltenen Platz rot. Belegte Zellen, Inselinneres beim Hafen und Zellen in Abstandszonen sahen bebaubar aus.
- **Zu große Sperrflächen:** Die 7.9.10-Rauten (bis zu 13 Zellen pro Gebäude) haben die Insel schnell gefüllt.
- **Unsichtbare Altdeko:** `decorPieces` hat Deko ohne bekanntes Bild (`IsleDecor.res(...) == null`) weggefiltert. Server und `IslandLayout` zählen sie trotzdem als Hindernis, also blieb eine Zelle ohne sichtbaren Grund gesperrt.
- **Gleiche Räume auf allen Stufen:** `PlaceStage` hat für alle Stufen denselben fertigen Hintergrund benutzt. Der Briefbaum zeigte zum Beispiel die Holzfächer des Postamts.

## Versionsvertrag

| `grid_version` | Server | Regel (App = Server) |
|---|---|---|
| fehlt / `null` | vor 7.9.7 bzw. unbekannt | `PlacementRule.LEGACY`: nichts wird grün, kein Schreiben, Hinweis „Server nennt keine Bauregel …“ |
| vorhanden, aber keine JSON-Ganzzahl (`"4"`, `4.9`, `4.0`, `4e0`, Überlauf über `Int`, `true`, Array/Objekt) | fehlerhaft | `PlacementRule.INVALID`: nichts wird grün, kein Schreiben, Hinweis „Ungültige Bauregel vom Server …“. Es wird nie auf eine bekannte Version gerundet oder umgewandelt (vorher machte `optInt` aus `4.9` und `"4"` still eine 4). Gilt auch für `/api/server-info` (`PlacementRule.read`). |
| 2 | 7.9.7–7.9.9 (läuft live) | Kreisabstände: Gebäude–Gebäude 3, Deko–Gebäude 2,4, Deko–Deko 1,5 Zellen, jeweils geteilt durch den Landfaktor; Stufe-0-Sonderfall (ein gehaltener Starter hält zu großen Stücken nur 2,4 Abstand) |
| 3 | 7.9.10 | Rauten `FOOTPRINT_RINGS_V3` big (2,2,1,1,1,1), starter (1,…), decor (1,1,1,0,0,0) |
| 4 | 7.9.11 (dieser Stand) | Kompakte Rauten `FOOTPRINT_RINGS` big (1,1,0,0,0,0), starter 0, decor 0 |
| andere Zahl | neuer oder fremder Server | nichts wird grün, kein Schreiben, Hinweis „neuere Bauregeln … bitte App aktualisieren“ |

- Der Server validiert genau `GRID_VERSION = 4`. Er meldet diesen Wert in `construction.grid_version` und öffentlich in `/api/server-info` (`"grid_version": 4`, Feature `build_grid_v4`; `build_grid_v2` bleibt für alte Clients erhalten). Weitere Daten kommen nicht hinzu.
- `server/app/island_grid.json` hat `version` 4 und `footprints` (v4), dazu unverändert `footprints_v3` und `legacy_v2` (Abstände und Landfaktoren). App und Server prüfen sich gegen diese eine Datei.
- In der App ist `PlacementRule` (in `IslandLayout.kt`) die einzige Definition. Daraus kommen `IslandLayout.problem` (eine Entscheidung pro Zelle), `targets` (gezeichnete Zielanker), `valid`/`validDecor` (Grün, ✓) und `reason` (Hinweis). Die UI-Komponenten haben keine eigenen Formeln.
- Lokale Fixtures (Konstruktor-Default) nutzen bewusst `PlacementRule.CURRENT` = 4. Ein Netzwerk-Payload ohne Feld ist dagegen immer `LEGACY`.
- Version 4 ist nie strenger als 3 oder 2. Gespeicherte Inseln werden also nicht schlechter, nichts wird verschoben oder gelöscht (`test_v15_raster`, `IslandRasterTest`).
- Der Hafen steht in jeder Version nur an der Küste. Ein v2-Server würde mehr erlauben, aber nie weniger.

## Raster: nur echte Ziele

- Während ein Stück gehalten wird, zeichnet `IslandGridUnderlay` nur noch `IslandLayout.targets(...)`. Das sind beim Hafen genau die freien Küstenzellen, sonst alle freien geeigneten Zellen. Inselinneres, Wasser und belegte Zellen bekommen kein Netz. Die gezeichnete Menge steht auch in der Semantik (`GridTargetsKey`), damit Tests sie prüfen können.
- Andere Stücke bekommen weiter einen dezenten roten Rand und eine leichte rote Tönung, aber keine helle Fläche.
- Eine Grün-Vorschau und ✓ gibt es genau dort, wo auch gezeichnet wird. Die eigene alte Fläche blockiert nie.
- Eine unbekannte Altdeko erscheint als gemalter Holzwegweiser (`IsleDecor.UNKNOWN_RES`) mit dem Namen „Unbekannte Deko (key)“. Sie ist antippbar und kann nur vom Besitzer entfernt werden. Sie blockiert weiterhin ihre Zelle, nichts wird automatisch gelöscht oder ignoriert.
- **409** (Insel inzwischen geändert, z. B. von einem anderen Gerät): Die App meldet ehrlich „… auf einem anderen Gerät geändert. Stand neu geladen – bitte den Platz prüfen.“, lädt neu und berechnet die Ziele neu. Der Entwurf bleibt erhalten. Es gibt keinen automatischen Retry.
- **Gelöschtes Ziel:** Wurde das verschobene Objekt inzwischen entfernt (Deko-ID fehlt im Stand oder ein angehobenes Gebäude steht nicht mehr), ist der Entwurf nie grün und zeigt keine Ziele (`IslandLayout.gone`, Hinweis `IslandLayout.GONE`). Sobald ein frischer Stand ankommt (Neuladen nach einem abgelehnten Schreiben oder die 60-s-Abfrage), wird der Entwurf beendet und genau diese Meldung gezeigt: „Dieses Objekt wurde inzwischen entfernt – Verschieben beendet, Stand neu geladen“. Aus einem `move`/`decor_move` wird nie ein `build`/`decor_place`, es gibt keine Wiederholung. `confirmDraft` prüft das zusätzlich vor jedem Schreiben. Neue Stücke aus der Bauleiste (nicht angehoben, ohne ID) sind davon nicht betroffen.
- **422** auf einem Platz, den die App für frei hielt: Die Zelle kommt in `IslandDraft.rejected` und wird für diesen Entwurf nicht mehr grün angeboten (Hinweis „Server lehnt diesen Platz ab – Stand neu geladen“). Andere freie Zellen bleiben wählbar.
- Unbekannte Regel: Vor jedem Schreiben mit Position prüft `construct` die Regel und lehnt sonst mit der Update-Meldung ab.

## Kompakte Fußabdrücke (Version 4)

- Große Insel (Landstufe 2–5): Jedes Stück belegt genau eine Bodenzelle.
- Kleine Insel (0–1): Funktionsgebäude ab Stufe 1 und besondere Deko belegen eine Raute mit einem Ring (5 Zellen), weil das Sprite dort mehrere Zellen breit ist. Starter und Deko belegen immer nur eine Zelle.
- Der Fußabdruck ist nie breiter als die Sprite-Box. Transparente PNG-Ränder und Touch-Padding zählen nicht. Die Trefferflächen (`SpriteBounds`) bleiben unverändert.

## 24 Stufenansichten

`StageScenes` legt pro Gebäude und Stufe fest: Kulisse, Boden, Held (genau das Insel-Sprite aus `IslandStages`), Requisiten, Datenhalter und Panel. Die Stufen 0–2 sind Außenbühnen aus vorhandenen gemalten Assets. Nur Stufe 3 behält den bestehenden gemalten Raum. Namen, Funktionen, Aktionen und echte Daten bleiben gleich.

| Gebäude | 0 | 1 | 2 | 3 |
|---|---|---|---|---|
| Post (Briefe) | Briefbaum, Strand am Tag: Umschläge an Nadeln am Stamm | Postkasten, Wiese am Tag: versiegelte Briefe pro Name | Postfächer, Wiese am Abend: Holzfächer im Freien | Postamt: gemalter Raum mit Fächern |
| Leuchtturm (Freunde) | Aussichtspalme, Tag: Freunde als Ruderboote | Ausguck, Abend: Segelboote | Leuchtfeuer, Nacht: Bullaugen, Lichtkegel vom Feuer | Leuchtturm: gemalte Galerie mit Lichtkegel |
| Bibliothek (Wörterbuch) | Treibholz, Tag: Pergament auf Kisten | Lesedach, Wiese am Tag: offenes Buch ohne Einband | Bücherstube, Abend: Lederbuch | Bibliothek: gemalter Raum, Lederbuch |
| Gemeindehaus (Gruppen) | Feuerkreis, Abendstrand: Gruppen ums Lagerfeuer | Treffhütte, Abend: Kisten als Tisch | Gemeindehaus, Tag: runde Tische | Festhalle: gemalter Raum mit runden Tischen |
| Hafen (Quests & Themen) | Strandtreff, Tag: Zettel am Pfahl ohne Nadel | Bootssteg, Tag: Zettel am Brett | Anleger, Abend: Aushangbrett | Hafen: gemalte Korktafel |
| Mein Haus (Profil) | Schlafplatz, Abendstrand: Schätze im Sand | Holzhütte, Strand am Tag: Truhe und Planke | Häuschen, Wiese am Tag: gerahmtes Bild auf der Veranda | Landhaus: gemalte Wohnstube |

- Abends und nachts bekommen Boden und Requisiten passendes Licht (Farbfilter auf denselben Assets). Einen Lichtkegel gibt es erst ab dem Leuchtfeuer.
- Daten liegen auf einem gemalten Panel (Pergament oder Brett), damit sie auf jeder Kulisse lesbar bleiben. Die Kopfzeile (Holzschild, Stufenstreifen, Kopfbild) ist unverändert.

## Tatsächlich ausgeführte Tests (in dieser Sitzung)

- Backend: `PYTHONPATH=server $TMPDIR/layermaxxing-verify-venv/bin/pytest -q server/tests`, Ergebnis **120 passed** (vorher 111; neu `server/tests/test_v15_raster.py` mit 9 Tests). An die höhere Dichte angepasst, mit gleich starken Aussagen für Version 4: `test_v4_identity` (server-info-Vertrag), `test_v14_placement` (Fixture v4, Bank-Kollision über den Fußabdruck, Schrumpf-Kollision mit besonderer Deko), `test_v13_start` (gleiche Zelle abgelehnt, Nachbarzelle frei), `test_island_evolution` (Schrumpfen mit 2 Zellen Abstand).
- `git diff --check`: sauber.

### Nachkorrektur (2026-10-18)

Hermes' Gesamtlauf hatte genau einen Fehler: `StageSceneRenderTest.kt:87` fand „Ort: Schlafplatz“ nicht. Ursache war ein Synchronisationsfehler im Test, kein UI-Fehler. Bei pausierter Uhr (`autoAdvance = false`) wurde `current` außerhalb des UI-Threads gesetzt, ohne den Recomposer zu benachrichtigen. Die Frames liefen also ohne Invalidierung und die Post-Ansicht blieb stehen. Jetzt wird `current` in `rule.runOnIdle` gesetzt, mit `Snapshot.sendApplyNotifications()`, danach `waitForIdle` und erst dann die Frames. Alle Assertions, alle 24 Kombinationen und die Kontrastschwelle 4,5:1 bleiben unverändert.

Gezielt ausgeführt (seriell, `--offline --no-daemon --max-workers=1`, `-Xmx1024m`, in-process Kotlin, ohne `LAYERMAXXING_GREGOR_URL`/`LAYERMAXXING_GERFRIED_URL`):

- `--tests '*StageSceneRenderTest*' --tests '*IslandRaster*'`: **BUILD SUCCESSFUL**. `StageSceneRenderTest` 1/1, `IslandRasterTest` 13/13, `IslandRasterUiTest` 10/10, keine Fehler, nichts übersprungen. Damit sind die Pixelschwellen (≥ 12 % verschiedene Rasterzellen, Meeresband, Kontrast) für alle 24 Ansichten in einem Lauf bestätigt.
- Gegenprobe: Mit vorübergehend abgeschalteter `gone`-Prüfung in `adopt` scheitern genau die vier neuen UI-Tests (gelöschte Deko/gelöschtes Gebäude nach 409 sowie per 60-s-Abfrage). Danach wurde der Code wiederhergestellt und die Tests liefen wieder grün.
- Neue Tests: `IslandRasterTest.onlyAPlainJsonIntegerIsAVersionNothingIsCoercedOntoAKnownRule`, `aMovedPieceThatWasRemovedMeanwhileIsNeverGreenAndOffersNothing`, dazu server-info mit `4.9`/`"4"`/`4.0`. `IslandRasterUiTest.aDecorationRemovedOnAnotherDeviceEndsTheDraftAfterTheConflictAndIsNeverPlacedAgain`, `aBuildingRemovedOnAnotherDeviceIsNeverRebuiltByTheMoveDraft`, `theMinutePollEndsADraftWhosePieceWasRemovedWithoutAnyWrite`, `theMinutePollEndsALiftedBuildingDraftAndNothingIsBuilt`. Geändert wurde nur die bestehende Prüfung von `"v4"`: Sie erwartet jetzt `INVALID` statt `LEGACY` und prüft zusätzlich, dass die Version nicht bekannt ist.
- Renders mit `VILLAGE_SHOTS=/home/veit/hermes-andrea-clean-20261016/workspace/layermaxxing-7.9.11-review` (das Verzeichnis war vorher leer), Filter `*StageSceneVisualReview*` und `*.VillageVisualReview.raster*`: **BUILD SUCCESSFUL**, 24/24 und 8/8. Es wurden 32 PNGs (1233×2673) geschrieben.
- Sechs 2×2-Übersichten in `<review>/contact-sheets/` (Pillow, 1234 px breit). Jedes Original ist vollständig und im richtigen Seitenverhältnis enthalten, die Labels stehen außerhalb der Screens. Visuell gesichtet: Alle 24 Kopfschilder sind lesbar, jede Stufe zeigt ihren eigenen Helden, die Aktion ist überall sichtbar. Beim Leuchtturm (Stufe 0–2) liegen in der Startposition der 5. Freund und „Signal an …“ unter „Freunde finden“. Das ist eine scrollbare `LazyColumn` mit 110 dp Bodenpadding und kein verdecktes Bedienelement, deshalb gibt es keine Produktänderung.

## Historischer Prüfstand vor dem abschließenden Gesamtlauf

**Zum Zeitpunkt der Claude-Nachkorrektur war Folgendes noch offen (aktueller Stand siehe oben):**

- Vollständiges `./gradlew testDebugUnitTest lintDebug assembleDebug` (Hermes: `--no-daemon --max-workers=1`, `-Xmx1024m`, in-process Kotlin), ohne `LAYERMAXXING_*_URL`. Nach dieser Nachkorrektur wurde es nicht erneut ausgeführt, ebenso wenig das Backend.
- Gezielt grün (siehe oben), im Gesamtlauf aber noch zu bestätigen: `IslandRasterTest` (Modellvertrag v2/v3/v4/unbekannt, Ziel ⇔ gültig ⇔ Referenzregel aus der Fixture, alle Landstufen und Stückarten, eigene Position, Altdeko, Ablehnung), `IslandRasterUiTest` (echte `IslandWorld`: gezeichnete Ziele = Modell, Hafen nur freie Küste, v2-Server, unbekannte Version, 409 ohne Retry, 422 nicht wieder grün), `StageScenesTest` (24er-Modellvertrag), `StageSceneRenderTest` (alle 24 Ansichten im echten Fenster: Kopfschild-Kontrast ≥ 4,5:1 auf Pixeln, Meeresfarbe je Tag/Abend/Nacht, Stufen eines Gebäudes sichtbar verschieden, Daten und Aktion vorhanden).
- Die Tests der Version 3 (7.9.10) laufen explizit mit `gridVersion = 3` weiter: `IslandPlacementTest`, `IslandWorldTest`, `IslandPlacementUiTest`.
- Opt-in-Renders (`VILLAGE_SHOTS=<dir>`):
  - Filter `*StageSceneVisualReview*`: 24 PNGs `stufe-<gebäude>-<stufe>-<name>.png`.
  - `*VillageVisualReview.raster*`: `raster-01` … `raster-08` (Hafen nur freie Küste, Post-Ziele, kompakter Nachbar, v2-Server, unbekannte Regel, Altdeko-Platzhalter, Altdeko blockiert, kleine Insel).
- Rollout: Der Live-Server meldet weiterhin `grid_version 2`. Mit dieser APK bekommt er die alten Kreisabstände für Anzeige und ✓. Erst nach Hermes' Server-Rollout gilt Version 4. Keine Live-Accounts, kein Serverneustart durch Claude.

## Grenzen

- Robolectric ist eine JVM-Simulation, kein Gerätetest.
- Version 2 kennt keine Fußabdruck-Flächen. Andere Stücke werden dort nur mit ihrer eigenen Zelle rot markiert, die Abstandszonen erkennt man daran, dass dort keine Ziele gezeichnet sind.
