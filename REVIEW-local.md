# Lokaler Review – Gerfried-Testbuild

Stand: f0039004d109cd96b6dd2548c0193e11e8bec8c6, Branch gerfried. Keine Änderung an main oder am laufenden Backend. Nur ignorierte lokale URL-/SDK-Konfiguration gesetzt.

## Verifikation
- Clean Android-Build: clean testDebugUnitTest lintDebug assembleDebug erfolgreich mit lokalem Temurin JDK 17. System-Java 21 hat keinen Java-Compiler und ist deshalb für diesen Build ungeeignet.
- 97 Android-Tests, 0 Fehler. Lint: 36 Warnungen, keine Fehler.
- Backend: 57 Tests bestanden, 1 DeprecationWarning.
- APK 5.0-beta6, versionCode 20, at.gregor.layermaxxing.gerfried, 19.162.554 Bytes. Parallel zur Original-App installierbar.
- APK-Signatur geprüft; lokal signiert. Nicht dieselbe Signatur wie Gerfrieds GitHub-Keystore garantiert; bestehende Gerfried-CI-Installation kann nicht ohne Deinstallation aktualisiert werden.
- Beide vorgesehenen HTTPS-URLs im DEX bestätigt.
- Kein ADB-Gerät verbunden. Keine visuelle oder on-device Runtime-Abnahme möglich.

## Mergeblocker
1. Gerfrieds konfigurierte Testserver-URL https://layermaxxing.derkellner.duckdns.org ist beim Abruf auf Port 443 nicht erreichbar. Authentifizierte externe Client-/Server-E2E-Abnahme nicht möglich.
2. Gregors laufender Server liefert /health 200, /api/server-info aber 404. LayerHome.kt:202–218 ruft neue Endpunkte (friendship-settings, ep, chats, sparks) ohne Feature-Gating gemeinsam in coroutineScope ab. Ein Fehler cancelt die übrigen Jobs. Der Testclient ist kein voll kompatibles Upgrade für das unveränderte Backend.
3. Migration gegen eine konsistente Kopie der echten Produktionsdatenbank scheitert bei server/app/main.py:317: sqlite3.IntegrityError FOREIGN KEY constraint failed. Ein akzeptierter friend_requests-Datensatz verweist auf fehlenden Benutzer. Die unveränderte Live-Datenbank hat bereits 4 FK-Verletzungen, integrity_check ist ok. Der neue Migrationscode berücksichtigt diese Altlasten nicht. Kein direkter Backend-Deploy vor bereinigter, getesteter Migration. Private Kopie nach Diagnose gelöscht.
4. Backend SERVER_ROLE fällt ohne Konfiguration auf test zurück (main.py:27). Bestehende Produktion hat SERVER_ROLE nicht explizit gesetzt; vor Deployment production erzwingen. Kreativ-/Vorspul-Funktionen sind zwar serverseitig gated, aber benötigen die korrekte Rolle.
5. Ein unveränderter Merge würde die Haupt-App-ID auf at.gregor.layermaxxing.gerfried und das Standardprofil auf Gerfrieds Server umstellen. Produktions-/Test-Flavors oder saubere Produktkonfiguration vor Übernahme erforderlich.

## Kleine Übersicht
Messenger-Umbau, sofortige Chats, bilaterale Einstellungen, Ebenen-Punkte, Prüfexporte und Signatur-/Commitment-Nachweise. Servergebundene Mehrkontenverwaltung. Optionales lokales Burgen-/Talexperiment und serverrollenabhängiger Kreativmodus. Bestehende Themenfunktion bleibt vorhanden.

## Empfehlung
Noch nicht direkt in main mergen oder produktiv deployen. Funktionen sind testgedeckt und grundsätzlich interessant. Zuerst Migration/Altlasten und Produktionskonfiguration beheben; Testserver erreichbar machen und echte Zwei-Konten-/Handy-E2E-Abnahme durchführen. Kein vollständiger Security-Audit; Fokus waren Build, Migration, API-Kompatibilität und Test-/Produktionsabgrenzung.
