# Insel-Platzierung 7.9.10-insel (versionCode 70)

## Regeln (App = Server)

- Jede Position ist eine Zelle des Bauraster der aktuellen Landstufe (`IslandGrid.buildable` = `grid_buildable`, festgehalten in `server/app/island_grid.json`).
- Belegung: Raute `|di| + |dj| <= rings` aus ganzen Zellen. Die Spitze vorne ist der Fuß des Sprites, die Mitte der Raute liegt `rings` Zellen dahinter (`IslandFootprint.middle` = `footprint_cells`). Zwei Teile teilen sich nie eine Zelle, sonst gibt es keinen Abstand.
- Hafen: nur auf Küstenzellen (Rand des Bauraster, `IslandGrid.coast` = `grid_coast`).
- Während ein Teil gehalten wird, sind alle anderen Belegungsflächen rot markiert, im Ruhezustand nicht.

## Hafen schnappt an die Küste

- Beim Ziehen, Tippen und beim Mitführen durch die Kamera schnappt der gehaltene Hafen auf die nächstgelegene Küstenzelle (`IslandGrid.snapCell(..., coastOnly = true)`). Alle anderen Teile schnappen weiter auf jede Landzelle.
- Der Modus wird pro Geste festgelegt. Ein Hafen, den derselbe Finger gerade erst angehoben hat, schnappt also schon an die Küste, bevor der Entwurf neu gezeichnet ist.
- Ist die nächste Küstenzelle belegt, bleibt der Hafen dort: rote Vorschau, „Belegt – rot markierte Fläche“, ✓ gesperrt. Er springt nicht von selbst auf eine andere Zelle.
- Ein alter Hafen im Inselinneren (Altdaten) startet beim ersten Anheben auf der nächstgelegenen Küstenzelle (`IslandLayout.startCell`). Verschoben wird nur der lokale Entwurf, der Server behält die alte Position bis zum ✓.
- Ein Tipp ins Wasser wird wie bisher ignoriert.
- Modell und Server lehnen manipulierte Innenpositionen weiter ab (`IslandLayout.valid`/`reason`, `building_spot_error`).

## Behobene Befunde

1. **Hafen-Snap:** `IslandTouchSurface` benutzte für alle Teile `nearestLandCell`. Jetzt nutzt sie `snapCell` mit Küstenmodus für den Hafen.
2. **Ausbauvorschau zu klein:** Die Bildfläche war fest 150 dp hoch, das Bild damit 138 dp. Jetzt ist die Höhe responsiv: `UpgradePreview.height` = 1,25 × Rahmenbreite, begrenzt auf 180…240 dp. Das Bild bleibt damit auch auf schmalen Geräten ≥ 160 dp.
3. **`IslandLayout.freeCell` suchte nur 10 Ringe:** Beispiel Land 5 mit einziger freier Küstenzelle [17,9], Start [3,9]: 14 Zellen entfernt, nicht gefunden, der neue Hafen startete rot. Jetzt wird das ganze 0..20-Raster durchsucht. Ist alles belegt, bleibt der Start ungültig und ✓ gesperrt. Aufrufer sind nur `startBuilding` (über `startCell`, für nicht stehende Gebäude) und `startDecor` (für neue Deko).
4. **Top-Hinweis abgeschnitten:** `PlacementHint` war einzeilig ohne Umbruch. Mit großer Systemschrift auf schmalem Gerät wurde der Text rechts ohne Ellipse abgeschnitten („Rot = belegt“ fehlte). Jetzt bricht er auf bis zu 3 zentrierte Zeilen um; die Ecken haben einen festen Radius statt 50 %.
5. **Ausbau-Titelschild:** Im Render `menu-04` verdeckten die gemalten Nieten das „P“ und das „n“ von „Postkasten ausbauen“ (28 dp Innenabstand bei mitwachsenden Nieten). Jetzt nutzt der Titel nur die nietenfreie Mitte der Platte (`UpgradeTitle.INSET` = 19 % je Seite, die Nieten reichen im Asset 16 % weit). Die Platte wächst mit dem gemessenen Titel bis zur Sheet-Breite. Passt er dann noch nicht, wird er gemessen verkleinert (bis 15 sp) und erst danach zweizeilig gesetzt, ohne Wörter zu trennen.

## Tests

- `IslandPlacementTest` (JVM): Snap für jeden Finger auf allen Landstufen, Start eines alten Innenhafens, belegte Küstenzelle wird nicht übersprungen, `freeCell`-Regression [3,9]→[17,9] und „alles belegt“, Höhe der Ausbauvorschau.
- `IslandPlacementUiTest` (Robolectric, echte `IslandWorld`): Tipp ins Innere führt zu belegter Küstenzelle (rot) bzw. zu freier Küstenzelle (✓ speichert); alter Innenhafen wird beim Anheben ohne Speichern an den Rand gesetzt; Hinweis bricht bei Schriftfaktor 1,6 auf 320 dp um.
- `IslandAusbauTest.upgradePreviewHasLargePaintedBeforeAfterWithoutOpenOrMoveAndUpdatesTheStage`: unverändert (≥ 160 dp).
- Visualtest `placement-06-hafen-innen-getippt-rand-snap`: echter Gestenlauf, Tipp auf [7,11] schnappt auf [5,13].
- `IslandUpgradeTitleTest` (Robolectric): Titel innerhalb der Safe-Area für „Postkasten ausbauen“ (einzeilig) und für alle Stufennamen, einmal mit Schriftfaktor 1,6 auf 280 dp, einmal mit normaler Schrift auf 371 dp (kurze Titel bis zur Länge von „Postkasten ausbauen“ einzeilig, lange höchstens zweizeilig).
- Backend: `server/tests/test_v14_placement.py` unverändert (Innenhafen wird abgelehnt, NoSpace-Atomik, Schrumpfen ohne Kollision).

## Grenzen

- Robolectric ist eine JVM-Simulation, kein Gerätetest.
- Die ✓/✗-Blasen werden über ihre Größe am Fuß positioniert; geprüft ist nur, dass Tipps darüber und daneben das Raster treffen.
