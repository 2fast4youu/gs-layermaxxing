# Gregor-Erweiterung – Test-Preview

Branch: `gregor-erweiterung`, basierend auf Gerfrieds `f003900`.
Version: `5.1-gregor-preview1`, versionCode 22.

## Änderungen

- Drei getrennte Serverprofile: Gregor (Original), Gerfried (Testserver), Gregor (Testserver). Aktive und gespeicherte Sessions werden nicht zwischen den Servern weitergereicht.
- Hauptnavigation bleibt kompakt; Themen, Gruppen, Tal und Wörterbuch sind direkt über die Gesprächsübersicht erreichbar.
- Durchsuchbares App-Wörterbuch mit Erklärungen zu EP, Funken, Briefen, Tal und Freigaberegeln.
- Themenübersicht mit strikt getrennten privaten, Freundschafts- und Gruppenthemen.
- Gruppen erstellen, Mitglieder anzeigen, gemeinsame Themen öffnen und zeitgesteuerte Gruppenbriefe senden. Noch kein Sofort-Gruppenchat.
- Tal-Schnellaktionen zu Chat, Themen, eigenem Hof, Freund, Gruppen und Wörterbuch.
- Gesprächsfilter nach Person/letzter Nachricht und Neuigkeiten; im Gespräch Textsuche, Kopieren und Zitatantworten. Versiegelte Inhalte werden nicht durch die Suche offengelegt.

## Lokaler Build

In der ignorierten `android/local.properties` oder über Umgebungsvariablen konfigurieren:

- `LAYERMAXXING_GREGOR_URL`: Produktions-URL.
- `LAYERMAXXING_GERFRIED_URL`: Gerfrieds eigener Testserver.
- `LAYERMAXXING_GREGOR_TEST_URL`: Gregors separater Testserver, derzeit `https://gs-layermaxxing.duckdns.org/test-gerfried/`.

Ohne explizite Konfiguration bleiben Gregors URLs absichtlich `example.invalid`. Keine Zugangsdaten einchecken. `docker-compose.test.yml` verwendet eine eigene Datenbank und `SERVER_ROLE=test` und ist kein Produktionsdeployment.

```sh
cd android
./gradlew clean testDebugUnitTest lintDebug assembleDebug
```

## Verifikation und Grenzen

- Clean Build, Unit-Tests, Lint und APK-Erzeugung erfolgreich.
- 102 Android-Tests: keine Fehler und keine übersprungenen Tests.
- 57 Backend-Tests bestanden.
- Lint: 0 Fehler, 38 Warnungen.
- HTTPS-Testserver: Registrierung zweier temporärer Konten, bestätigte Freundschaft, Gruppe, drei Themen in getrennten Bereichen und Chatnachrichten über die API erfolgreich angelegt. Testdaten danach gezielt entfernt; Abwesenheit und Fremdschlüsselprüfung verifiziert.
- APK-Installation im Emulator erfolgreich. Die vollständige visuelle und interaktive Abnahme konnte nicht abgeschlossen werden: wiederholte native Emulator-Abstürze (Exit 139); Software-Emulation bootete nicht rechtzeitig. Nicht als gerätegeprüften Release oder vollständigen UI-E2E-Erfolg behandeln.
- Kein Merge nach `main`, keine Produktionsmigration und keine Übernahme produktiver Daten.
