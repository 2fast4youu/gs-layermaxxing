# Gerfried-Testserver: Auto-Deploy

## Erstinstallation tatsächlich geprüft – 2026-10-19

- 25 Controller-Unit-Tests bestanden; zusätzlich echter Deploy des bereits CI-geprüften Commits
  `cd710861676a04b0a38e743c26c66ba4f181184b` (CI `39174124875`), nicht aus dem laufenden Workspace.
- SQLite-Sicherung mit Modus `0600`, Migrationsprobe auf Kopie und echter Kandidatenserver erfolgreich.
- Nach dem echten Dienstneustart: `/health` gesund, Rolle `test`, Backendversion `4.0.0`, `grid_version: 5`;
  tatsächliches Prozess-Arbeitsverzeichnis liegt im neuen unveränderlichen Release.
- Timer aktiviert und `active/waiting`, nächster Lauf nach etwa einer Minute; `Linger=yes` für den User-Manager.
  Ein anschließender Lauf mit unverändertem Branch war ein echter Noop.
- Originalunit unverändert; vorhandener 50er-Drop-in und persistenter DB-Pfad bleiben erhalten.
- Öffentliche HTTPS-Erreichbarkeit ist eine getrennte Ingress-Aufgabe. Diese Installation verändert keine
  Pi-/Proxy-/Router-Konfiguration und betrifft nicht Gregors anderen Testserver.


Der Gerfried-Backend-Dienst (`gs-layermaxxing.service`, User-systemd, Port 8794) aktualisiert sich selbst per
**Auto-Pull** – kein Webhook (dafür fehlt `admin:repo_hook`, und ein öffentlich erreichbarer Endpunkt wäre
zusätzliche Angriffsfläche). Gregors Host ist davon nicht betroffen.

## Ablauf eines Laufs (`scripts/gerfried-autoupdate.py`)

1. `flock` auf `ROOT/deploy/autoupdate.lock` – ein zweiter Lauf beendet sich sofort.
2. Eigener Bare-Git-Cache `ROOT/deploy/repo.git`: `git fetch` nur von `refs/heads/gerfried-erweiterung` per
   fester URL und Refspec → exakter Remote-HEAD-SHA. Der Workspace wird **nie** benutzt.
3. CI-Gate: `gh api …/actions/workflows/gerfried-apk.yml/runs?head_sha=SHA`. Maßgeblich ist nur der **neueste**
   passende Lauf (SHA, Branch, Workflow; sortiert nach `run_number`, `run_attempt`, `id`) – er muss
   `completed`/`success` sein. Ist der neueste pending/failed, zählt ein älterer grüner Lauf nicht.
   Pending/failed/fehlend ⇒ alter Server bleibt.
   Bereits deployter SHA ⇒ Noop. Zuletzt fehlgeschlagener SHA ⇒ `blocked_failed` (kein Minuten-Neustartloop);
   ein neuer Commit oder `--force` versucht es erneut. `--force` umgeht **nie** das CI-Gate.
4. Release `ROOT/releases/gerfried-auto-<sha8>-<UTC>` aus `git archive SHA server` (nur getrackte Dateien inkl.
   `server/app/geometry/`; Links, Traversal und absolute Pfade werden abgelehnt), eigene `.venv`
   (`uv venv --python /usr/bin/python3.12` + `uv pip install -r server/requirements.txt` mit `UV_LINK_MODE=copy`), danach
   schreibgeschützt. Hardlinks (geteilter uv-Cache) ⇒ Abbruch statt `chmod` fremder Inodes. `DEPLOY-MANIFEST.json` enthält SHA, CI-Run, Git-Tree-Hash von `server/` und SHA-256 der
   requirements. Paket-/Netzfehler ⇒ Status `failed`, Dienst unberührt.
5. Konsistentes SQLite-Backup (Backup-API) nach `ROOT/backups/layermaxxing-<UTC>-pre-<sha8>.db` (0600).
   Kopie in `ROOT/deploy/staging/`, dort `app.main.initialize_database()` mit der Kandidaten-venv; Prüfung von
   `integrity_check`, FK-Verletzungen (Anzahl darf nicht steigen) und Zeilenzahlen (keine Tabelle/Zeile darf
   verschwinden) – es werden nur Zahlen protokolliert. Danach startet ein kurzlebiger Kandidaten-uvicorn auf
   freiem Port gegen diese Kopie; `/health` und `/api/server-info` (role `test`, `grid_version` als Zahl, wird
   übernommen, nicht hartkodiert) müssen echt antworten. Nur dieser eigene Prozess wird beendet; die
   Staging-Kopie wird gelöscht.
6. Umschalten: atomarer Symlink `ROOT/current` → Release und Drop-in
   `~/.config/systemd/user/gs-layermaxxing.service.d/60-gerfried-autodeploy.conf` mit
   `WorkingDirectory=ROOT/current/server` und `ExecStart=ROOT/current/.venv/bin/uvicorn <unveränderte Argumente>`
   (Argumente werden aus Unit + übrigen Drop-ins gelesen; anderes als `uvicorn app.main:app` ⇒ Abbruch).
   Unit-Environment (`DB_PATH`, `SERVER_ROLE=test`, …) und der Drop-in `50-hermes-grid-version.conf` bleiben
   unverändert. `daemon-reload`, Restart **nur** von `gs-layermaxxing.service`, Readback auf
   `127.0.0.1:<port>` muss Version/`grid_version` des Kandidaten liefern und die MainPID muss im neuen Release
   laufen. Lässt sich das MainPID-cwd nicht ermitteln ⇒ Fehler und Rollback (fail closed).
7. Fehler beim Umschalten ⇒ vorheriger `current`-Zeiger und vorheriger 60er-Drop-in werden wiederhergestellt
   (bei der Erstübernahme: beide entfernt, also gilt wieder Originalunit + 50er-Drop-in), Restart, Readback.
   Die echte DB wird **nie** aus einem Backup zurückgesetzt. Backups und alte Releases werden nie gelöscht.

Status: `ROOT/deploy/state.json` (0600; SHA, CI-Run, Release, Status, Fehler, Kandidaten-Grid – keine
Credentials/DB-Inhalte). Journal: `journalctl --user -u gs-layermaxxing-autoupdate`.

## Installation (Hermes)

```bash
ROOT=$HOME/.local/share/gs-layermaxxing
REPO=<Checkout des geprüften Stands>
install -d -m 700 "$ROOT/deploy"
install -m 755 "$REPO/scripts/gerfried-autoupdate.py" "$ROOT/deploy/gerfried-autoupdate.py"
install -m 644 "$REPO/deploy/systemd/gs-layermaxxing-autoupdate.service" \
               "$REPO/deploy/systemd/gs-layermaxxing-autoupdate.timer" ~/.config/systemd/user/
systemctl --user daemon-reload

/usr/bin/python3.12 "$ROOT/deploy/gerfried-autoupdate.py" --check     # nur Plan, ändert nichts außer Git-Cache
systemctl --user start gs-layermaxxing-autoupdate.service             # erster echter Deploy
/usr/bin/python3.12 "$ROOT/deploy/gerfried-autoupdate.py" --status
curl -s http://127.0.0.1:8794/health; curl -s http://127.0.0.1:8794/api/server-info
systemctl --user enable --now gs-layermaxxing-autoupdate.timer
```

Weitere Optionen: `--force` (CI-geprüften aktuellen SHA neu installieren), `--config FILE`.

## Konfiguration (optional) `ROOT/deploy/config.json`

Alle Felder von `Config` sind überschreibbar (`root`, `repo_url`, `repo_slug`, `branch`, `workflow`, `service`,
`unit_dir`, `dropin_name`, `db_name`, `python`, `uv`, `git`, `gh`, `systemctl`, `health_timeout`,
`deps_timeout`, `notify_command`). Unbekannte Schlüssel ⇒ Abbruch. Benachrichtigung nur bei Deploy-Erfolg/-Fehler
(nie bei Noop/Warten): `notify_command` ist eine argv-Liste, die Nachricht wird als letztes Argument angehängt,
z. B. `["/pfad/zu/hermes", "-p", "PROFIL", "send", "--to", "telegram:CHAT_ID", "--json"]`
als Wert von `notify_command`. Platzhalter durch den echten CLI-Pfad, das gewünschte Profil und die Ziel-ID ersetzen;
keine Tokens oder privaten Chatkonfigurationen ins Repository übernehmen. Im Repo ist kein Notifier voreingestellt.

## Grenzen

- Rollback betrifft nur Code/Unit; eine bereits gelaufene Live-Migration bleibt (gewollt, keine Datenverluste).
  Backup manuell nutzbar.
- Releases sind schreibgeschützt; zum manuellen Aufräumen erst `chmod -R u+w`.
- `daemon-reload` wirkt auf den ganzen User-Manager (lädt nur Unitdateien neu, startet nichts anderes).
- Tests (`scripts/tests/test_gerfried_autoupdate.py`) sind Unit-Fixtures mit gemocktem CI/systemd; echter
  Nachweis nur durch den realen Lauf.
