"""Briefbaum 22.10.: a personal "mark as unread" for a letter the recipient has read.

It is the recipient's own reading marker only: the server's read time (the sender's read receipt), the release, the
proof export and the read-once rule stay exactly as they were. Reading the letter again through /content lifts it.
"""
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
    assert response.status_code == 200, response.text
    return response.json()


def befriend(client: TestClient, first: dict, second: dict) -> None:
    request_id = client.post("/api/friend-requests", headers=auth(first), json={"recipient_id": second["user_id"]}).json()["id"]
    assert client.post(f"/api/friend-requests/{request_id}/respond", headers=auth(second), json={"accept": True}).status_code == 200


def letter(client: TestClient, sender: dict, recipient: dict, release_at: int, **extra) -> int:
    response = client.post("/api/messages", headers=auth(sender), json={
        "recipient_id": recipient["user_id"], "ciphertext": "YQ==", "nonce": "MTIzNDU2Nzg5MDEy",
        "encryption_key": "Yw==", "release_at": release_at, **extra,
    })
    assert response.status_code == 201, response.text
    return response.json()["id"]


def incoming(client: TestClient, account: dict, message_id: int) -> dict:
    return next(m for m in client.get("/api/messages", headers=auth(account)).json() if m["id"] == message_id)


def outgoing(client: TestClient, account: dict, message_id: int) -> dict:
    return next(m for m in client.get("/api/outbox", headers=auth(account)).json() if m["id"] == message_id)


def test_recipient_marks_a_read_letter_unread_and_reading_it_again_lifts_the_mark(tmp_path, monkeypatch):
    main = load_app(tmp_path)
    clock = {"now": 1_900_000_000}
    monkeypatch.setattr(main, "now_ts", lambda: clock["now"])
    with TestClient(main.app) as client:
        a, b = register(client, "A"), register(client, "B")
        befriend(client, a, b)
        message_id = letter(client, a, b, clock["now"] + 60)
        clock["now"] += 61
        assert incoming(client, b, message_id)["marked_unread"] is False
        assert client.get(f"/api/messages/{message_id}/content", headers=auth(b)).status_code == 200
        read_at = incoming(client, b, message_id)["read_at"]
        assert read_at == clock["now"]

        clock["now"] += 500
        marked = client.post(f"/api/messages/{message_id}/unread", headers=auth(b))
        assert marked.status_code == 200, marked.text
        assert marked.json() == {"marked_unread": True}
        mine = incoming(client, b, message_id)
        assert mine["marked_unread"] is True
        # the read time (and with it the sender's receipt) is history and stays
        assert mine["read_at"] == read_at
        theirs = outgoing(client, a, message_id)
        assert theirs["read_at"] == read_at and "marked_unread" not in theirs
        # marking twice is the same mark
        assert client.post(f"/api/messages/{message_id}/unread", headers=auth(b)).status_code == 200

        # reading it again (the real open call) lifts the mark; the first read time stays
        clock["now"] += 500
        assert client.get(f"/api/messages/{message_id}/content", headers=auth(b)).status_code == 200
        again = incoming(client, b, message_id)
        assert again["marked_unread"] is False and again["read_at"] == read_at

        # persistent: the mark survives a restart of the server (same database)
        assert client.post(f"/api/messages/{message_id}/unread", headers=auth(b)).status_code == 200
    main = load_app(tmp_path)
    monkeypatch.setattr(main, "now_ts", lambda: clock["now"])
    with TestClient(main.app) as client:
        assert incoming(client, b, message_id)["marked_unread"] is True


def test_only_the_recipient_of_a_read_letter_inside_an_active_friendship_may_mark_it(tmp_path, monkeypatch):
    main = load_app(tmp_path)
    clock = {"now": 1_900_000_000}
    monkeypatch.setattr(main, "now_ts", lambda: clock["now"])
    with TestClient(main.app) as client:
        a, b, c = register(client, "A"), register(client, "B"), register(client, "C")
        befriend(client, a, b)
        travelling = letter(client, a, b, clock["now"] + 3600)
        arrived = letter(client, a, b, clock["now"] + 60)
        clock["now"] += 61
        # not read yet (nothing to mark) and not openable yet: refused, nothing changes
        assert client.post(f"/api/messages/{arrived}/unread", headers=auth(b)).status_code == 409
        assert client.post(f"/api/messages/{travelling}/unread", headers=auth(b)).status_code == 409
        assert incoming(client, b, travelling)["read_at"] is None
        assert client.get(f"/api/messages/{arrived}/content", headers=auth(b)).status_code == 200
        # the sender and strangers cannot touch the recipient's marker (not found, as everywhere)
        assert client.post(f"/api/messages/{arrived}/unread", headers=auth(a)).status_code == 404
        assert client.post(f"/api/messages/{arrived}/unread", headers=auth(c)).status_code == 404
        assert client.post("/api/messages/999999/unread", headers=auth(b)).status_code == 404
        assert client.post(f"/api/messages/{arrived}/unread").status_code == 401
        assert incoming(client, b, arrived)["marked_unread"] is False

        # letters switched off in the friendship: no letter action, also not this one
        proposal = client.post("/api/friendship-settings/proposals", headers=auth(a), json={
            "friend_id": b["user_id"], "letters_enabled": False, "chats_enabled": True, "ep_enabled": False,
            "min_letter_delay_seconds": 0,
        }).json()["id"]
        assert client.post(f"/api/friendship-settings/proposals/{proposal}/respond", headers=auth(b), json={"accept": True}).status_code == 200
        assert client.post(f"/api/messages/{arrived}/unread", headers=auth(b)).status_code == 403


def test_a_read_once_letter_cannot_be_marked_unread_and_stays_consumed(tmp_path, monkeypatch):
    main = load_app(tmp_path)
    clock = {"now": 1_900_000_000}
    monkeypatch.setattr(main, "now_ts", lambda: clock["now"])
    with TestClient(main.app) as client:
        a, b = register(client, "A"), register(client, "B")
        befriend(client, a, b)
        once = letter(client, a, b, clock["now"] + 60, one_time=True)
        clock["now"] += 61
        assert client.get(f"/api/messages/{once}/content", headers=auth(b)).status_code == 200
        refused = client.post(f"/api/messages/{once}/unread", headers=auth(b))
        assert refused.status_code == 409
        assert "einmal" in refused.json()["detail"].lower()
        assert incoming(client, b, once)["marked_unread"] is False
        # still consumed: no second content
        assert client.get(f"/api/messages/{once}/content", headers=auth(b)).status_code == 410
