# GS Layermaxxing 3.7

Private Android-App für verschlüsselte Nachrichten mit regelbasierter späterer Freigabe.

## V3-Funktionen

- Konten mit `scrypt`, Login-Limit, Passwortwechsel, Recovery-Code und Sitzungsverwaltung
- Profile mit Avatar-Emoji, Farbe, Sichtbarkeit und exakter Suche
- Freundschaftsanfragen, Rückzug, Entfernen und Blockieren
- Einzel-, Mehrfach- und Gruppennachrichten
- Titel, verschlüsselte Anhänge bis 2 MB, Reaktionen und Lesestatus
- Einmal-Lesen und Rückzug vor Freigabe
- Freigabe per Dauer, Zeitpunkt, manuell, gegenseitiger Zustimmung, gleichzeitiger Online-Präsenz oder Zufallsfenster
- Android-Hintergrundbenachrichtigungen mit WorkManager und lokalen Termin-Workern
- vorbereitete Push-Token-API für Firebase
- System-, Hell-, Dunkel- und altertümliches Theme
- optionale biometrische Startsperre
- eigenes adaptives Android-App-Icon und vereinfachte Material-You-Oberfläche
- robuster Netzwerkzugriff mit HTTP/1.1-Wiederholung und DNS-over-HTTPS-Fallback
- fünf rotierende Peitschensounds beim Antippen gesperrter Nachrichten
- verschlüsselte persönliche, gemeinsame und gruppenweite Gesprächsthemen mit Offen-/Erledigt-Status

## Architektur

- `android/`: Kotlin, Jetpack Compose, OkHttp, WorkManager, AndroidX Biometric
- `server/`: FastAPI, SQLite, migrationsfähiges V1/V2/V3-Schema
- `docker-compose.yml`: API, Caddy und DuckDNS

## Lokal starten

1. `.env.example` als `.env` kopieren und DuckDNS-Token, Subdomain und öffentliche App-Domain lokal eintragen.
2. Backend und HTTPS-Proxy mit `docker compose up -d --build` starten.
3. Backend-Tests mit `PYTHONPATH=server .venv/bin/pytest -q server/tests` ausführen.
4. In `android/local.properties` zusätzlich `layermaxxing.apiBaseUrl=https://deine-domain.example/` eintragen.
5. Android-App unter `android/` mit `./gradlew testDebugUnitTest lintDebug assembleDebug` bauen.

Alternativ kann die Build-URL über die Umgebungsvariable `LAYERMAXXING_API_BASE_URL` gesetzt werden. Ohne lokale Konfiguration verwendet der öffentliche Quellcode absichtlich nur `https://example.invalid/`.

Die lokale `.env`, SQLite-Datenbanken, Sicherungen, Gradle-Caches und APK-Dateien werden bewusst nicht versioniert.

## Sicherheitshinweis

Nachrichten werden clientseitig mit AES-256-GCM verschlüsselt. Zur Durchsetzung der Freigaberegeln liegt der Schlüssel bis zur Freigabe auf dem privaten Server. Das ist eine servererzwungene Zeitsperre, kein kryptografisches Time-Lock-Puzzle.
