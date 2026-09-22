"""Creative mode entitlement: test-server-only, one-time migration, honest status.

The mode itself is a client concern (free cosmetic builds from a separate local
ledger). The server only answers two questions truthfully: what role does this
server have, and does this account carry the one-time entitlement. Nothing else
changes server-side — letters, EP and every gate keep their existing rules.
"""

import importlib
import sqlite3
from pathlib import Path

from fastapi.testclient import TestClient


def load_app(tmp_path: Path, monkeypatch, *, role: str = "test"):
    monkeypatch.setenv("DB_PATH", str(tmp_path / "test.db"))
    monkeypatch.setenv("SERVER_ROLE", role)
    import app.main as main

    importlib.reload(main)
    return main


def auth(account: dict) -> dict[str, str]:
    return {"Authorization": f"Bearer {account['token']}"}


def register(client: TestClient, name: str) -> dict:
    response = client.post("/api/register", json={"name": name, "password": "password-123"})
    assert response.status_code == 200
    return response.json()


def status_of(client: TestClient, account: dict) -> dict:
    response = client.get("/api/status", headers=auth(account))
    assert response.status_code == 200
    return response.json()


def drop_migration_marker(tmp_path: Path, main) -> None:
    """Simulates deploying this version onto a test server that already has users."""
    conn = sqlite3.connect(tmp_path / "test.db")
    try:
        conn.execute("DELETE FROM meta WHERE key=?", (main.CREATIVE_MIGRATION_KEY,))
        conn.commit()
    finally:
        conn.close()


def test_fresh_test_server_entitles_nobody_and_reports_both_signals(tmp_path, monkeypatch):
    main = load_app(tmp_path, monkeypatch)
    with TestClient(main.app) as client:
        account = register(client, "Neu")
        status = status_of(client, account)
        # A user registered after the one-time migration gets no entitlement.
        assert status["creative_entitled"] is False
        assert status["server_role"] == "test"


def test_migration_entitles_existing_users_once_and_stays_idempotent(tmp_path, monkeypatch):
    main = load_app(tmp_path, monkeypatch)
    with TestClient(main.app) as client:
        a = register(client, "Alt-A")
        b = register(client, "Alt-B")

    # The real deployment scenario: the marker is absent while users exist.
    drop_migration_marker(tmp_path, main)
    main = load_app(tmp_path, monkeypatch)
    with TestClient(main.app) as client:
        assert status_of(client, a)["creative_entitled"] is True
        assert status_of(client, b)["creative_entitled"] is True
        c = register(client, "Danach")
        assert status_of(client, c)["creative_entitled"] is False

    # Restarting again must change nothing: no new entitlements, none revoked.
    main = load_app(tmp_path, monkeypatch)
    with TestClient(main.app) as client:
        assert status_of(client, a)["creative_entitled"] is True
        assert status_of(client, c)["creative_entitled"] is False


def test_production_role_never_runs_the_migration(tmp_path, monkeypatch):
    main = load_app(tmp_path, monkeypatch, role="production")
    with TestClient(main.app) as client:
        account = register(client, "Prod")

    # Even without the marker a production restart entitles nobody.
    drop_migration_marker(tmp_path, main)
    main = load_app(tmp_path, monkeypatch, role="production")
    with TestClient(main.app) as client:
        status = status_of(client, account)
        assert status["creative_entitled"] is False
        assert status["server_role"] == "production"


def test_profile_update_response_keeps_reporting_creative_signals(tmp_path, monkeypatch):
    # The client replaces its status object with this response; losing the two
    # fields here would silently deactivate creative mode until the next poll.
    main = load_app(tmp_path, monkeypatch)
    with TestClient(main.app) as client:
        account = register(client, "Editor")
        updated = client.patch(
            "/api/profile", headers=auth(account), json={"avatar_emoji": "🦊"}
        )
        assert updated.status_code == 200
        assert updated.json()["server_role"] == "test"
        assert updated.json()["creative_entitled"] is False
