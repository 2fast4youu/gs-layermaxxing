"""Test-only fast-forward for letters: powerful, therefore triple-gated.

Only on SERVER_ROLE=test, only between two creative-entitled accounts, only
for time- and random-gated letters. Consent gates (mutual/manual/presence)
stay untouched, foreign letters stay invisible, production knows no route.
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


def befriend(client: TestClient, a: dict, b: dict) -> None:
    request_id = client.post(
        "/api/friend-requests", headers=auth(a), json={"recipient_id": b["user_id"]}
    ).json()["id"]
    client.post(f"/api/friend-requests/{request_id}/respond", headers=auth(b), json={"accept": True})


def entitle(tmp_path: Path, *user_ids: int) -> None:
    conn = sqlite3.connect(tmp_path / "test.db")
    try:
        for user_id in user_ids:
            conn.execute("UPDATE users SET creative_entitled=1 WHERE id=?", (user_id,))
        conn.commit()
    finally:
        conn.close()


def timed_letter(client: TestClient, sender: dict, recipient: dict, now: int, *, mode: str = "timed") -> int:
    payload = {
        "recipient_id": recipient["user_id"], "ciphertext": "YQ==", "nonce": "MTIzNDU2Nzg5MDEy",
        "encryption_key": "Yw==", "title": "Test", "mode": mode,
    }
    if mode == "timed":
        payload["release_at"] = now + 3600
    if mode == "random":
        payload["random_from"] = now + 3600
        payload["random_to"] = now + 7200
    response = client.post("/api/messages", headers=auth(sender), json=payload)
    assert response.status_code == 201, response.text
    return response.json()["id"]


def advance(client: TestClient, account: dict, letter_id: int):
    return client.post(f"/api/test/letters/{letter_id}/advance", headers=auth(account), json={"phase": "release"})


def test_advance_unlocks_timed_and_random_and_is_idempotent_and_audited(tmp_path, monkeypatch):
    main = load_app(tmp_path, monkeypatch)
    clock = {"now": 1_900_000_000}
    monkeypatch.setattr(main, "now_ts", lambda: clock["now"])
    with TestClient(main.app) as client:
        a, b = register(client, "A"), register(client, "B")
        befriend(client, a, b)
        entitle(tmp_path, a["user_id"], b["user_id"])
        for mode in ("timed", "random"):
            letter_id = timed_letter(client, a, b, clock["now"], mode=mode)
            assert client.get("/api/messages", headers=auth(b)).json()[0]["unlocked"] is False
            first = advance(client, b, letter_id)
            assert first.status_code == 200 and first.json() == {"advanced": True, "unlocked": True}
            # Idempotent: a second call changes nothing and stays honest.
            again = advance(client, b, letter_id)
            assert again.json() == {"advanced": False, "unlocked": True}
            # The letter is genuinely released: content is fetchable the real way.
            assert client.get(f"/api/messages/{letter_id}/content", headers=auth(b)).status_code == 200
        conn = sqlite3.connect(tmp_path / "test.db")
        try:
            audits = conn.execute("SELECT COUNT(*) FROM test_advances").fetchone()[0]
        finally:
            conn.close()
        assert audits == 2  # one row per real advance, none for the no-ops


def test_advance_clears_an_agreed_minimum_only_between_entitled_accounts(tmp_path, monkeypatch):
    main = load_app(tmp_path, monkeypatch)
    clock = {"now": 1_900_000_000}
    monkeypatch.setattr(main, "now_ts", lambda: clock["now"])
    with TestClient(main.app) as client:
        a, b = register(client, "A"), register(client, "B")
        befriend(client, a, b)
        proposal = client.post("/api/friendship-settings/proposals", headers=auth(a), json={
            "friend_id": b["user_id"], "letters_enabled": True, "chats_enabled": True,
            "ep_enabled": False, "min_letter_delay_seconds": 600,
        }).json()["id"]
        client.post(f"/api/friendship-settings/proposals/{proposal}/respond", headers=auth(b), json={"accept": True})
        entitle(tmp_path, a["user_id"], b["user_id"])
        letter_id = timed_letter(client, a, b, clock["now"])
        assert advance(client, a, letter_id).json()["unlocked"] is True


def test_advance_never_exists_on_production(tmp_path, monkeypatch):
    main = load_app(tmp_path, monkeypatch, role="production")
    with TestClient(main.app) as client:
        a, b = register(client, "A"), register(client, "B")
        befriend(client, a, b)
        entitle(tmp_path, a["user_id"], b["user_id"])
        letter_id = timed_letter(client, a, b, main.now_ts())
        assert advance(client, a, letter_id).status_code == 404


def test_advance_requires_both_accounts_entitled(tmp_path, monkeypatch):
    main = load_app(tmp_path, monkeypatch)
    with TestClient(main.app) as client:
        a, b = register(client, "A"), register(client, "B")
        befriend(client, a, b)
        letter_id = timed_letter(client, a, b, main.now_ts())
        # Nobody entitled → no. Only the actor entitled → still no: fast-forward
        # must never run against an account that is not a tester's own.
        assert advance(client, a, letter_id).status_code == 403
        entitle(tmp_path, a["user_id"])
        assert advance(client, a, letter_id).status_code == 403
        entitle(tmp_path, b["user_id"])
        assert advance(client, a, letter_id).status_code == 200


def test_advance_rejects_foreigners_consent_modes_and_unknown_phases(tmp_path, monkeypatch):
    main = load_app(tmp_path, monkeypatch)
    with TestClient(main.app) as client:
        a, b, c = register(client, "A"), register(client, "B"), register(client, "C")
        befriend(client, a, b)
        befriend(client, a, c)
        entitle(tmp_path, a["user_id"], b["user_id"], c["user_id"])
        letter_id = timed_letter(client, a, b, main.now_ts())
        # C is entitled but not a participant of this letter: invisible.
        assert advance(client, c, letter_id).status_code == 404
        # Consent-based modes are not advanceable, however entitled everyone is.
        mutual_id = timed_letter(client, a, b, main.now_ts(), mode="mutual")
        assert advance(client, a, mutual_id).status_code == 422
        # Unknown phases are rejected instead of guessing.
        bad = client.post(f"/api/test/letters/{letter_id}/advance", headers=auth(a), json={"phase": "read"})
        assert bad.status_code == 422
