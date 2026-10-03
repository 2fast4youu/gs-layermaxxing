# Separater lokaler Gerfried-Testserver

## Ziel und Zugriff
- Quellstand f003900, gesonderter Docker-Compose-Projektname gs-layermaxxing-gerfried-test.
- Öffentliche Basis-URL https://gs-layermaxxing.duckdns.org/test-gerfried/
- Info https://gs-layermaxxing.duckdns.org/test-gerfried/api/server-info
- Eigener Container gs-layermaxxing-gerfried-test-test-api-1, eigenes Volume gs-layermaxxing-gerfried-test_test_data. Keine Produktionsdaten kopiert.
- Explizite Rolle test; neuer SQL-Trigger local_test_creative_entitlement gibt allen neu registrierten Testkonten Kreativberechtigung. Nur in dieser Testdatenbank eingerichtet, keine Quelländerung. Bei neuem Volume muss der Trigger wieder eingerichtet werden. Keine echten privaten Inhalte oder Produktionspasswörter zum Testen verwenden.
- Lokaler Port nur 127.0.0.1:8791; HTTPS via bestehendem Caddy und handle_path /test-gerfried/*, Präfix wird entfernt. Standardroute bleibt Produktions-API.
- Start: docker compose -p gs-layermaxxing-gerfried-test -f docker-compose.test.yml up -d --no-build
- Stop ohne Datenverlust: docker compose -p gs-layermaxxing-gerfried-test -f docker-compose.test.yml stop
- Restart policy unless-stopped.

## Client
- /home/gregor/GS-Layermaxxing-5.0-beta6-Gregor-Testserver.apk
- Parallel installierbare App-ID at.gregor.layermaxxing.gerfried, Profil Gerfried ist nun auf diesen lokalen Testserver konfiguriert. Profil Gregor bleibt auf dem unveränderten Produktionsserver und ist mit dem neuen Client nicht vollständig kompatibel.
- Gleicher lokaler Signer wie zuvor ausgelieferte Review-Test-APK, daher Update darüber möglich. Nicht notwendigerweise gleiche Signatur wie GitHub-CI-APK.

## Verifikation
- Clean Android-Gates bestanden: 97 Unit-Tests, lintDebug, assembleDebug.
- Neuer Testpfad in APK-DEX vorhanden, ursprüngliche Gerfried-Test-URL nicht vorhanden.
- Beide Health-URLs erfolgreich: Test 4.0.0, Produktion 3.1.0.
- HTTPS-E2E mit zwei temporären Accounts: Registrierung/Status/Kreativberechtigung, Freundschaft, AES-GCM Chat senden/lesen/entschlüsseln, gemeinsame Themen/Abhaken, bilaterale Einstellungen/EP, zeitgesperrter Brief/Vorspulen/Entschlüsseln, anonymer Funke. Alle erfolgreich.
- Temporäre Accounts/abhängige Datensätze gelöscht; danach 0 Benutzer, integrity_check ok, 0 FK-Verletzungen.
- Caddy file-bind erfordert nach atomarem Host-Dateiaustausch Container-Recreate; Reload allein sah den alten Inode. Caddy wurde neu erstellt, Produktion danach health-geprüft. Produktionsbackend nicht neu gestartet.

## Grenzen
- Kein echter Handy-Lauf mangels ADB-Gerät. Browser/Handy sollte die Info-URL und anschließend die App gegenprüfen.
- HTTPS-Testpfad ist öffentlich erreichbar und bietet Accountregistrierung; bewusst nur Testdaten verwenden.
- Produktion wurde weder migriert noch auf gerfried umgestellt. Frühere Mergeblocker zur Datenmigration/Produktkonfiguration gelten weiterhin.
