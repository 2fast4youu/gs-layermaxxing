# GS Layermaxxing — Arbeitskontext für Claude Code

Arbeite ausschließlich im Repository `/home/veit/projects/gs-layermaxxing` und auf Branch `gerfried`.

## Ziel

Dies ist Veits parallel installierbare Android-Test-App mit eigenem FastAPI-Testserver. Bewahre die bestehende Produktlogik und liefere kleine, vollständig getestete vertikale Änderungen.

## Projektstruktur

- `server/app/main.py`: FastAPI-Backend, rohe SQLite-Migrationen beim Start
- `server/tests/`: pytest-Tests
- `server/tools/verify_export.py`: Offline-Prüfer für Nachweisexporte
- `android/app/src/main/java/at/gregor/layermaxxing/`: Kotlin-/Compose-App
- `docs/gerfried-v4-spec.md`: bestehender Produktvertrag
- `docs/agent-workflow.md`: Rollen, Review- und Übergaberegeln

## Unverhandelbare Regeln

- Branch bleibt `gerfried`; nicht auf `main` arbeiten.
- Test-App bleibt parallel installierbar: `applicationId at.gregor.layermaxxing.gerfried`, Label `GS Layermaxxing Gerfried`.
- Gerfried-Testserver: `https://layermaxxing.derkellner.duckdns.org`.
- Produktions-URL darf ohne explizite Konfiguration `https://example.invalid/` bleiben; niemals eine echte Produktionsadresse raten.
- Keine Secrets, Tokens, Datenbanken, APKs oder lokale Konfiguration committen.
- Bestehende Tests nicht löschen, abschwächen oder pauschal unterdrücken.
- Keine Behauptung von Ende-zu-Ende-Verschlüsselung: Der Server hält den AES-Schlüssel bis zur Freigabe. Signatur/Commitment sind Manipulationsnachweise.
- Freundschaftseinstellungen und EP bleiben bilateral bestätigt. Alle Letter-Aktionen müssen die aktuellen Freundschafts-/Feature-Gates beachten.
- Account-Wechsel der Test-App darf keinen Logout verlangen; gespeicherte Logins sind serverprofilbezogen.
- Vor Änderungen relevante Tests ergänzen oder aktualisieren; anschließend beide vollständigen Gates ausführen.
- Nicht committen oder pushen, außer der konkrete Auftrag verlangt es ausdrücklich. Hermes übernimmt Integration und Auslieferung.

## Verifikation

Bevor du Erfolg meldest:

```bash
./scripts/verify-agent-change.sh
```

Das Skript führt aus:

```bash
PYTHONPATH=server .venv/bin/pytest -q server/tests
cd android
ANDROID_HOME=/home/veit/Android/Sdk ANDROID_SDK_ROOT=/home/veit/Android/Sdk \
  ./gradlew testDebugUnitTest lintDebug assembleDebug
```

Android immer ohne `LAYERMAXXING_GREGOR_URL`/`LAYERMAXXING_GERFRIED_URL` bauen, damit die eingecheckten Standardadressen geprüft werden.

## Arbeitsweise

1. Ist-Zustand und betroffene Tests lesen.
2. Kleine vertikale Änderung planen.
3. Test zuerst bzw. reproduzierbaren Fehler festhalten.
4. Minimal implementieren.
5. Gezielte Tests, danach `./scripts/verify-agent-change.sh`.
6. `git diff --check` und `git status --short` prüfen.
7. Exakt berichten: geänderte Dateien, echte Testergebnisse, offene Risiken. Keine erfundenen Ausgaben.
