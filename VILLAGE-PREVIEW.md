# Isometrisches Dorf – Preview 5.2

Branch `gregor-erweiterung`, versionCode 25.

## Grafik und Interaktion

Eigenständige, mit Codex/ImageGen erzeugte isometrische Dorf-Grafik, keine kopierten Clash-of-Clans-Assets. `village_plate.webp` ist 1024 × 1536. Die tatsächlichen Gebäudepositionen wurden nach Sichtprüfung vermessen; sie sind nicht blind aus dem Prompt übernommen.

Die Karte verwendet den bestehenden Kamera-/Gesten-/Hit-Test-Unterbau. Elf erreichbare Gebäude, inklusive eigenem und Freundes-Hof; Berührungsziele mindestens 48 dp. Weltgebundene Schilder skalieren mit der Karte. Kein alter Hütten-Sprite wird zusätzlich über die neue Grafik gezeichnet. Hauptdorf, eigener Hof und Freundesbesuch verwenden dieselbe Grafik. Die Bedienleiste ist verkleinert und die Karte reserviert ihre gemessene Höhe.

## Funktionsorte

- Treffpunkt: alle Gespräche; von dort Briefe, Regeln, EP-Vorschläge, Nachrichten-Suche, Kopieren, Antworten, Themen und Freundschaftsaktionen.
- Schwarzes Brett: private, Freundschafts- und Gruppenthemen.
- Gruppenplatz: Gruppen verwalten, Gruppenthemen und Gruppenbriefe.
- Wegweiser: Freundschaft/Tal wechseln und Freunde finden/verwalten.
- Bibliothek: App-Wörterbuch.
- Funkenplatz: Funken-Inbox, gesendete Funken und neue Funken; bestehende Melde-/Stummschaltaktionen bleiben im Funkenbereich.
- Postarchiv: alle Briefe, Freigabestatus und Prüfdateien; Nachweise werden über die echte API geladen.
- EP-Verwaltung: Vorschläge und Verlauf.
- Gemeindehaus: Standardmodus, Profil, Erscheinungsbild, Server, Test-Kreativmodus, Benachrichtigungen, Geräte, Blockierungen, Passwort und Wiederherstellung; Kontenwechsel und Hinzufügen erreichbar.
- Eigener Hof: Poststelle, Schatzkammer und Ausbau.
- Freundes-Hof: nur geteilte Informationen, kein Fernzugriff.

Auch ohne bestätigte Freundschaft sind die allgemeinen Dorf-Orte verfügbar; Aktionen mit Freundschaftsvoraussetzung behalten ihre echten Gates. Messenger-Modus blendet das Dorf weiterhin aus. Dorf-/Hof-Auswahl wird beim Öffnen von Unteransichten konto- und serverbezogen erhalten.

## Grenzen – ausdrücklich kein vollständiger Clash-of-Clans-Klon

Die Dorf-Grafik ist eine feste Illustration mit interaktiven Gebäudebereichen, keine frei platzierbare 3D-Spielwelt. Ausbau-/EP-Daten bleiben echt und unverändert; Ausbaustufen verändern in dieser Preview noch nicht die Gebäudegrafik. Es gibt keinen Kampf-, Ressourcenproduktions- oder Bauzeit-Simulator. Die früheren Boten auf der alten Landschaftsroute werden hier nicht falsch auf neue Wege gezeichnet; Freigaben und Briefaktionen bleiben in Poststelle und Archiv verfügbar.

Grafik visuell geprüft und Gebäudekamera/Hit-Tests auf schmalem Hochformat sowie Querformat im JVM-Test geprüft. Keine visuelle/interaktive Geräteabnahme: kein verbundenes ADB-Gerät; frühere Emulatorversuche endeten in nativen Abstürzen. Build- und Unit-Erfolg nicht als Geräte-E2E ausgeben.
