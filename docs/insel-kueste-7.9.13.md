# Insel, Küste und Funktionsszenen – 7.9.13-kueste

VersionCode73, Paket `at.gregor.layermaxxing.gerfried`. Produktcode und Tests mit Claude Opus5.5 entwickelt; Hermes hat die Abschlussgates unabhängig ausgeführt und die Artefakte geprüft.

## Umsetzung

- Insel lädt mit begrenztem Timeout, Retry, eigenständiger Statusabfrage und account-/Server-/URL-getrenntem persistentem letztem Stand. Ein fehlender Stand bleibt ehrlich als solcher erkennbar; keine Demo-Insel als Nutzerdaten. Schreibschutz und verspätete Antworten/Kontowechsel werden geprüft.
- Neuer Regelvertrag **v6**: rechteckige Bodenflächen statt geschützter vollständiger Sprite-Silhouetten. Dach-/Kronenüberdeckung ist gewollt; wirkliche Bodenüberschneidungen sind verboten. Gemeinsame Datenquelle `server/app/geometry/island_ground.json`, in Server und APK identisch. Die v5-Maskendatei bleibt für historische Serverregeln erhalten; v2/3/4/5 werden versionsgenau unterstützt, unbekannte Versionen gesperrt.
- Land-, Strand-, Wasser- und Felszonen stimmen mit dem neu gestalteten Gelände zusammen. Strandtreff gehört auf Strand; normale Gebäude nicht. Stege/Anleger prüfen ihre land- und wasserseitigen Flächen. Verschiebeziele zeigen nur passende, freie Ziele unter der tatsächlich verwendeten Regelversion. Der alte weiße Sternweg ist entfernt.
- Bestehende Positionen bleiben erhalten. Ein ungeeigneter alter Hafen wird als Reparaturfall kenntlich; der sichtbare Wasserlandeplatz wird nicht fälschlich als funktionierender alter Binnenhafen ausgegeben. Upgrades prüfen größere Böden vor Kostenabzug.
- Validierte weichere Wasserwege statt kantiger Kreisumwege; gezeichneter und befahrener Pfad stimmen überein. Rumpffreiraum, Hafenposition, Wartefälle und Kurven werden geprüft; Label-/Touchpadding ist keine physische Schiffshülle. Namens-/Warteschilder werden gemessen und bei Bedarf versetzt, nicht Boote versteckt. Geometrie wird nicht mit jedem Fortschrittstakt neu geplant.
- Alle24Funktionsstufen haben eine explizite Story-/Mechanikbindung (`StageStory.kt`, Matrix in `insel-kueste-storyplan-7.9.13.md`): persönliche Gegenstände am Schlafplatz/im Haus; tatsächliche Briefe an Baum/Kasten/Fächern; Freunde/Anfragen am Ausguck/Leuchtsignal; Wörterbuch über Buch/Lupe/Feder/Kategorien; Gruppen/Vorhaben um Feuer/Tische; Aufträge und Ankünfte am Strandtreff/Steg/Hafen. Bestehende Daten, Gates und Callbacks bleiben maßgeblich, keine erfundenen Nutzer oder Statuswerte.

## Letzte UI-Korrekturen

- Freundschaftsanfrage als gemessene, gestapelte Karte mit lesbarem Namen/Anfragetext und getrennten Aktionen; ggf. weitere Freunde auf sichtbaren Folgeseiten.
- Questnotizen mit getrenntem Titel/Schließen-, Partner- und Abhakenbereich. Größere Karten, angepasste Slots und Seiten statt überdeckter Beschriftungen.
- Wörterbuchkategorien umbrechend statt rechts abgeschnitten.
- Neue Bounds-/Überlappungsregressionen bei360×640dp und Schriftgröße1.5. Alle erwarteten Boote bleiben vorhanden; Schilder werden nicht als Kollisionsausnahme übergangen.

## Tatsächlich ausgeführte Abschlussprüfungen

- Backend inkl.25AutoDeploy-Tests: **174 bestanden**, eine bekannte Starlette/AnyIO-DeprecationWarning.
- Vollständiges Androidgate `testDebugUnitTest lintDebug assembleDebug`: **BUILD SUCCESSFUL**. JUnit579Einträge: **396 bestanden,183Opt-in übersprungen,0Fehler**.
- Separate opt-in Renders: **40frische PNGs** – alle24Gebäudestufen,8Karten-/Geometriefälle,8Kleinbildschirm-/Großschriftfälle. Die vormals identischen Hafenszenen wurden durch eine tatsächlich andere Strandtreff-Geometrie unterschieden.
- Frischer unabhängiger readonlyOpus-Bildreview: **READY**, die vier bekannten Überdeckungs-/Abschneidefehler nicht mehr sichtbar. PNGs sind tatsächliche Robolectric-JVM-Renderings mit synthetischen Fixtures, kein Telefon-/Live-Nutzerdatentest.
- APKversion73/7.9.13-kueste und erwarteter Zertifikat-SHA256 `dc86b5644a707b963ec94cc25d233f249cc8654716a6be666e88758ce9b9a0c7` verifiziert.
- Die tatsächlich eingebetteten `island_ground.json` und `island_masks.json` sind bytegleich den Serverdateien.
- Finale lokale APK SHA256 `3b7c5fb38615aaf76605edba12687a96b9bcdfe7d89d0d10cb38abf8a77c9ec3`.

## Grenzen / Betrieb

- Großschrift kann lange Questtitel/Partnernamen mit Ellipse bzw. Wortbruch kürzen. Einzelne Pager-/Titeltexte bei1.5Schriftgröße bleiben knapp; kleine Sprite-Farbsäume und versetzte Namensschilder sind kosmetische Restpunkte. Die geprüften Aktionen/Kategorien sind zugänglich.
- Rollout erfolgt nach grüner CI über den installierten Gerfried-AutoUpdater, nicht aus dem schmutzigen Arbeitsbaum. Aktuelle Liveversion und Git-/CI-/Versandnachweise stehen im externen Implementierungsstatus.
- Die öffentliche Gerfried-Adresse war bei Abschluss der lokalen Gates weiter nicht erreichbar. Zertifikats-HTTP01 scheiterte am externen Zugang; Router-/Providerfreigaben sind eine getrennte offene Infrastrukturaufgabe. Keine Routerregeln geändert, keine anderen Pi-Webseiten/Dienste umkonfiguriert. Ein lokal gesunder v6-Rollout ist kein Beweis für öffentliche Erreichbarkeit oder einen erfolgreichen Test auf Veits Telefon.
