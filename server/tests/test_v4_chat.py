import importlib
import os
from pathlib import Path

from fastapi.testclient import TestClient


def load_app(tmp_path: Path):
    os.environ["DB_PATH"] = str(tmp_path / "test.db")
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
    request_id = client.post("/api/friend-requests", headers=auth(a), json={"recipient_id": b["user_id"]}).json()["id"]
    client.post(f"/api/friend-requests/{request_id}/respond", headers=auth(b), json={"accept": True})


def change_settings(client: TestClient, a: dict, b: dict, **changes) -> None:
    payload = {"friend_id": b["user_id"], "letters_enabled": True, "chats_enabled": True,
               "ep_enabled": False, "min_letter_delay_seconds": 0}
    payload.update(changes)
    proposal = client.post("/api/friendship-settings/proposals", headers=auth(a), json=payload).json()["id"]
    client.post(f"/api/friendship-settings/proposals/{proposal}/respond", headers=auth(b), json={"accept": True})


def test_instant_chat_is_separate_encrypted_thread_with_read_state(tmp_path, monkeypatch):
    main = load_app(tmp_path)
    clock = {"now": 1_900_000_000}
    monkeypatch.setattr(main, "now_ts", lambda: clock["now"])
    with TestClient(main.app) as client:
        a, b = register(client, "A"), register(client, "B")
        befriend(client, a, b)
        payload = {"ciphertext": "Y2lwaGVy", "nonce": "MTIzNDU2Nzg5MDEy", "encryption_key": "a2V5"}
        sent = client.post(f"/api/chats/{b['user_id']}/messages", headers=auth(a), json=payload)
        assert sent.status_code == 201, sent.text
        message_id = sent.json()["id"]

        assert client.get("/api/messages", headers=auth(b)).json() == []
        assert client.get("/api/outbox", headers=auth(a)).json() == []
        threads = client.get("/api/chats", headers=auth(b)).json()
        assert threads[0]["friend_id"] == a["user_id"]
        assert threads[0]["unread_count"] == 1
        thread = client.get(f"/api/chats/{a['user_id']}/messages", headers=auth(b)).json()
        assert thread == [{
            "id": message_id, "sender_id": a["user_id"], "recipient_id": b["user_id"],
            "ciphertext": "Y2lwaGVy", "nonce": "MTIzNDU2Nzg5MDEy", "encryption_key": "a2V5",
            "created_at": clock["now"], "read_at": clock["now"],
        }]
        assert client.get("/api/chats", headers=auth(b)).json()[0]["unread_count"] == 0
        change_settings(client, a, b, chats_enabled=False)
        assert client.get("/api/chats", headers=auth(b)).json() == []


def test_chat_feature_gate_and_friendship_are_required(tmp_path):
    main = load_app(tmp_path)
    with TestClient(main.app) as client:
        a, b = register(client, "A"), register(client, "B")
        payload = {"ciphertext": "YQ==", "nonce": "MTIzNDU2Nzg5MDEy", "encryption_key": "Yw=="}
        assert client.post(f"/api/chats/{b['user_id']}/messages", headers=auth(a), json=payload).status_code == 403
        befriend(client, a, b)
        change_settings(client, a, b, chats_enabled=False)
        assert client.post(f"/api/chats/{b['user_id']}/messages", headers=auth(a), json=payload).status_code == 403
        assert client.get(f"/api/chats/{a['user_id']}/messages", headers=auth(b)).status_code == 403


def test_random_release_window_is_at_least_one_minute(tmp_path, monkeypatch):
    main = load_app(tmp_path)
    clock = 1_900_000_000
    monkeypatch.setattr(main, "now_ts", lambda: clock)
    monkeypatch.setattr(main.secrets, "randbelow", lambda size: size - 1)
    with TestClient(main.app) as client:
        a, b = register(client, "A"), register(client, "B")
        befriend(client, a, b)
        base = {"recipient_id": b["user_id"], "ciphertext": "YQ==", "nonce": "MTIzNDU2Nzg5MDEy",
                "encryption_key": "Yw==", "mode": "random", "random_from": clock + 60}
        assert client.post("/api/messages", headers=auth(a), json={**base, "random_to": clock + 119}).status_code == 422
        valid = client.post("/api/messages", headers=auth(a), json={**base, "random_to": clock + 120})
        assert valid.status_code == 201, valid.text
        row = client.get("/api/outbox", headers=auth(a)).json()[0]
        assert row["random_from"] == clock + 60
        assert row["random_to"] == clock + 120
        with main.db() as conn:
            assert conn.execute("SELECT release_at FROM messages WHERE id=?", (row["id"],)).fetchone()[0] == clock + 120
