# Insel-Szenen und Hafenrouten 7.9.12 (versionCode 72, `7.9.12-szenen`)

## Abschließende unabhängige Gates durch Hermes

- Nach sämtlichen Szenen-, Routing- und Clippingkorrekturen: Backend **133 bestanden**, Android **351 bestanden**, 174 opt-in im Standardlauf übersprungen, keine Fehler. `testDebugUnitTest lintDebug assembleDebug`: **BUILD SUCCESSFUL**.
- 24 aktuelle Stufenbilder und 7 aktuelle Geometrie-/Kartenbilder unter `workspace/layermaxxing-7.9.12-final-review/`; sechs Stufenübersichten sowie Geometrieübersicht. Die betroffenen Karten wurden nach der Clippingkorrektur neu gerendert.
- Die folgenden Test-/Zwischenstandsabschnitte dokumentieren die Claude-Läufe. Dort als offen bezeichnete vollständige Gates wurden anschließend von Hermes wie oben ausgeführt.
- Kein echter Telefon-Ende-zu-Ende-Test. Begrenzte Wasserwegssuche und gemeinsam genutzte gemalte Räume werden nicht als pixelgenaue Küstensimulation oder neue Einzelgemälde ausgegeben.


Stand: 2026-10-19. Beschreibt die tatsächliche Implementierung. Nicht freigegeben, solange Hermes die vollständigen Gates nicht erneut ausgeführt hat.

## Vertrag

- 24 Funktionsbühnen (6 Gebäude × 4 Stufen) in `StageScenes.kt`; die Daten liegen an Ankern im gemalten Bild (`PlaceScenes.kt`). Namen, Funktionen und Aktionen der Stufen bleiben unverändert.
- Innenräume sind ausschließlich gemalte Raumbilder. Der frühere zusammengesetzte Planken-/Polygonraum (`ComposedRoom`, `Composed.LOG_HUT/BOOK_ROOM`) ist entfernt.
  - CAMPFIRE 1 Treffhütte: gemalter Hütteninnenraum `fief_build_closeup` mit Aushängen an der Bretterwand; die Mitglieder der echten Gruppe sitzen auf Hockern rund um den gemalten Kerzentisch `p_table`. Tisch antippen = Gruppen-Quest; Gruppen werden geblättert.
  - HOUSE 2 Häuschen: Wohnwinkel innen (`bg_house`): Bild über der Fensterbank, Schätze auf der Bank und am Tischchen beim Sessel, Kaktus und Blumen auf der Bank. Profil/Einstellungen, „Insel bearbeiten“ und die vier Zählwerte unverändert.
  - LIBRARY 2 Bücherstube: gemalte Stube `fief_treasury_closeup` mit Büchern am Wandbord; das Wörterbuch liegt auf einem dunklen Lesepult.
- Bibliothek: Die Aktion „Wort hinzufügen“ hat eine eigene Zeile unter der Liste und schwebt nicht mehr über den Einträgen (`GregorScreens.kt`, Column statt Box). Die Feder sitzt links unten. Jede Stufe hat eine eigene Einfassung: Treibholzbrett (0), offenes Buch unter dem Lesedach (1), Lesepult (2), Ledereinband (3). Suche, Kategorien, Lesen und Hinzufügen bleiben unverändert.
- Hafenrouten (`HarbourRoutes.kt`): Die Route verläuft vom Liegeplatz zu einem Ausfahrpunkt vor der eigenen Insel. Danach folgt der kürzeste Weg durch einen Sichtbarkeitsgraphen: Start, Ziel sowie 16 Ringpunkte um jede aufgeblähte Insel. Jede Kante wird gegen alle Inseln geprüft, Knoten müssen innerhalb der Kartengrenze liegen (Dijkstra). Die Suche hat keine Tiefenbegrenzung.
  - Dockstücke dürfen nur die eigene Insel berühren und nicht tiefer hinein als bis zum Liegeplatz. Die Insel selbst ist nicht pauschal von der Kollision ausgenommen.
  - Gibt es keinen Wasserweg, liefert die Funktion `null`. Für diesen Freund wird dann weder eine Route noch ein Boot gezeichnet. Eine Gerade durch Land gibt es nicht als Rückfallebene.
  - Lanes: `safeLane` prüft jede seitlich verschobene Lane erneut mit `laneClear` (Mitte frei, Grenzen, Dockregel). Ist die Lane nicht frei, wird der Versatz auf 75/50/25 % verkleinert, zuletzt wird die validierte Route selbst verwendet.
  - Bootsausdehnung (Nachtrag 2026-10-19): Ein Boot wird als Box von 84 × 42 dp gezeichnet (Rumpf, Abzeichen, „+N“). Routen und Lanes verlaufen nur in der um eine halbe Box eingerückten See. Zeigt ein Hafen zum Kartenrand, dreht die Ausfahrt um bis zu ±60° zur offenen See; die Dockregel wird dabei weiter geprüft. Die Semantik liefert jede Box (`MapRoute.hull`) und die Zeichenfläche (`MapAreaKey`).
  - Liegeplatz, echte Ankunft 0..1, Wartestatus auf der Reede, Spuren, Bootdetails, Zeiten und Anti-Stapeln sind unverändert.

## Ausgeführte Tests (2026-10-19, Robolectric/JVM, seriell, offline)

- `*HarbourRouteTest*` mit den neuen Regressionen „viele Inseln, jede Route und jede Lane (±46/92/140 px) ist Wasser“, „enger Korridor wird genutzt, geschlossener liefert keine Route“ und „Ausfahrt in fremder Insel → keine Route; Lane quer durch die eigene Insel wird abgelehnt“.
- Dazu `*StageScene*` (Szenen, Anker, Kleindisplay, Render), `*IslandMapRouteUiTest*` und die Bildtests. Letzter Lauf: 54 Tests, 0 übersprungen, 0 Fehler.
- `StageScenesTest` verschärft: Innenräume brauchen ein gemaltes Raumbild, die Treffhütte ist `fief_build_closeup` mit `p_table`, HOUSE 2 ist ein Innenraum ohne Außen-Set-Piece.
- Frische Renders: `workspace/layermaxxing-7.9.12-final-review/` (24 Stufen + 7 Raster-/Routenbilder, `uebersichten/` mit 6 Kontaktbögen). Alle wurden angesehen.
- Nachtrag 2026-10-19: Neuer Test `IslandMapRouteUiTest.withLenaEveryBoatHullLiesInsideTheMap` mit dem echten Review-Fall. Er schlug vor der Korrektur nachweislich fehl: Boot 16 mit Box links bei −59 px. Ebenfalls neu: `HarbourRouteTest.aPierFacingTheMapEdgeTurnsTowardsTheOpenSeaInsteadOfLeavingTheMap`. Ein seriell ausgeführter Lauf von HarbourRouteTest (9 Tests) und IslandMapRouteUiTest (3 Tests) war grün.
- Danach `IslandReview712VisualReview` neu ausgeführt (6 Tests grün). Nur die 7 Raster-/Kartenbilder und `uebersichten/geometrie-routen.jpg` wurden ersetzt; die 24 Stufenbilder sind per md5 unverändert.
- NICHT ausgeführt: volle Backend-/Android-Gates (Lint/assembleDebug) und ein echter Telefontest.

## Offene Grenzen

- Die Hindernisse sind Kreise (Radius 0,45 × Inselbreite). Auf der Karte verläuft eine Route sichtbar dicht an der Küste. Das Clipping der Lena-Boote am Kartenrand ist behoben. Im Bild `karte-routen-hafen-sued` liegen die beiden Lena-Boote jedoch nahe ihrem Liegeplatz dicht beieinander und teils über dem Inselrand (Dockstück); das ist nicht behoben. Die Kopf- und Menü-Overlays werden bei der Kartenfläche nicht berücksichtigt. Pixelgenaue Küstenmasken für das Routing gibt es nicht.
- Begrenzte Asset-Auswahl: HOUSE 2 und HOUSE 3 teilen dasselbe Raumbild (`bg_house`), unterschieden nur durch Anordnung und Objekte. LIBRARY 2 teilt das Bild mit HOUSE 1, CAMPFIRE 1 mit POST 2. Auf der Werkbank der Treffhütte sind teilweise noch Werkzeuge zu sehen. Neue Malerei war nicht Teil des Auftrags.
- Keine Garantie für frei erfundene Extremlayouts: Ohne Wasserweg wird bewusst nichts gezeichnet, die Briefe stehen weiter in den Listen.
