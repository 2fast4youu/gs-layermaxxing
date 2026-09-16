# GS Layermaxxing Gerfried 4.0

Private Android-App für verschlüsselte Nachrichten mit regelbasierter späterer Freigabe.

## V4-Funktionen

- separat installierbarer Gerfried-Testbuild mit umschaltbaren Serverprofilen und dauerhaftem Testserver-Hinweis
- bilaterale Freundschaftseinstellungen: Briefe, Chat, EP und Mindest-Briefverzögerung ändern sich nur nach Vorschlag und Zustimmung
- Ebenen-Punkte (EP) mit Vorschlag, Annahme, Briefverknüpfung, Verlauf, Summen und Levels
- separater, sofort freigegebener und AES-GCM-verschlüsselter Freundschafts-Chat
- öffentlicher Umschlagtext und klar abgegrenzter versiegelter Briefinhalt
- kryptografische Nachweise mit NFC, SHA-256, gesalzenem Commitment, P-256-Signatur, expliziten Nonces und AAD
- exportierbare `.gsverify.json`-Prüfdateien und Offline-Prüfer `server/tools/verify_export.py`

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
4. Optional in `android/local.properties` `layermaxxing.gregorApiBaseUrl=https://deine-produktions-domain.example/`
   und/oder `layermaxxing.gerfriedApiBaseUrl=https://dein-testserver.example/` eintragen.
5. Android-App unter `android/` mit `./gradlew testDebugUnitTest lintDebug assembleDebug` bauen.

Alternativ werden die Build-URLs über `LAYERMAXXING_GREGOR_URL` und `LAYERMAXXING_GERFRIED_URL` gesetzt.
Ohne Produktionskonfiguration bleibt Gregors Profil bewusst auf `https://example.invalid/` und wird in der App als
„nicht konfiguriert“ angezeigt.

Die lokale `.env`, SQLite-Datenbanken, Sicherungen, Gradle-Caches und APK-Dateien werden bewusst nicht versioniert.

## Sicherheitshinweis

Nachrichten werden clientseitig mit AES-256-GCM verschlüsselt. Zur Durchsetzung der Freigaberegeln liegt der
AES-Freigabeschlüssel bis zur Freigabe auf dem Server; der Serverbetreiber kann deshalb technisch auf gesperrte
Inhalte zugreifen. Das ist eine servererzwungene Zeitsperre, kein kryptografisches Time-Lock-Puzzle. Der pro Brief
erzeugte private P-256-Signierschlüssel wird dagegen nie hochgeladen oder offengelegt.

Prüfdateien können offline ausgeführt werden:

```bash
PYTHONPATH=server .venv/bin/python server/tools/verify_export.py brief.gsverify.json
```
