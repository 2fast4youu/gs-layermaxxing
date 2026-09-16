import importlib
import os
import sqlite3
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
    made = client.post(
        "/api/friend-requests", headers=auth(first), json={"recipient_id": second["user_id"]}
    )
    request_id = made.json()["id"]
    accepted = client.post(
        f"/api/friend-requests/{request_id}/respond",
        headers=auth(second),
        json={"accept": True},
    )
    assert accepted.status_code == 200, accepted.text


def settings_payload(friend_id: int, **changes) -> dict:
    result = {
        "friend_id": friend_id,
        "letters_enabled": True,
        "chats_enabled": True,
        "ep_enabled": False,
        "min_letter_delay_seconds": 0,
    }
    result.update(changes)
    return result


def test_accepted_friendship_has_defaults_and_bilateral_proposals(tmp_path):
    main = load_app(tmp_path)
    with TestClient(main.app) as client:
        anna, bert = register(client, "Anna"), register(client, "Bert")
        befriend(client, anna, bert)

        current = client.get("/api/friendship-settings", headers=auth(anna)).json()
        assert current == [{
            "friend_id": bert["user_id"],
            "friend_name": "Bert",
            "letters_enabled": True,
            "chats_enabled": True,
            "ep_enabled": False,
            "min_letter_delay_seconds": 0,
            "incoming_proposal": None,
            "outgoing_proposal": None,
        }]

        proposed = client.post(
            "/api/friendship-settings/proposals",
            headers=auth(anna),
            json=settings_payload(bert["user_id"], ep_enabled=True, min_letter_delay_seconds=60),
        )
        assert proposed.status_code == 201, proposed.text
        proposal_id = proposed.json()["id"]
        assert client.post(
            f"/api/friendship-settings/proposals/{proposal_id}/respond",
            headers=auth(anna), json={"accept": True},
        ).status_code == 403
        duplicate = client.post(
            "/api/friendship-settings/proposals", headers=auth(bert),
            json=settings_payload(anna["user_id"], letters_enabled=False),
        )
        assert duplicate.status_code == 409

        accepted = client.post(
            f"/api/friendship-settings/proposals/{proposal_id}/respond",
            headers=auth(bert), json={"accept": True},
        )
        assert accepted.status_code == 200
        assert accepted.json()["status"] == "accepted"
        applied = client.get("/api/friendship-settings", headers=auth(bert)).json()[0]
        assert applied["ep_enabled"] is True
        assert applied["min_letter_delay_seconds"] == 60
        assert applied["incoming_proposal"] is None


def test_reject_withdraw_and_removal_close_pending_proposals(tmp_path):
    main = load_app(tmp_path)
    with TestClient(main.app) as client:
        a, b = register(client, "A"), register(client, "B")
        befriend(client, a, b)
        first = client.post(
            "/api/friendship-settings/proposals", headers=auth(a),
            json=settings_payload(b["user_id"], chats_enabled=False),
        ).json()["id"]
        assert client.post(
            f"/api/friendship-settings/proposals/{first}/respond", headers=auth(b), json={"accept": False}
        ).json()["status"] == "rejected"

        second = client.post(
            "/api/friendship-settings/proposals", headers=auth(a),
            json=settings_payload(b["user_id"], letters_enabled=False),
        ).json()["id"]
        assert client.delete(
            f"/api/friendship-settings/proposals/{second}", headers=auth(b)
        ).status_code == 403
        assert client.delete(
            f"/api/friendship-settings/proposals/{second}", headers=auth(a)
        ).status_code == 200

        third = client.post(
            "/api/friendship-settings/proposals", headers=auth(a),
            json=settings_payload(b["user_id"], ep_enabled=True),
        ).json()["id"]
        assert client.delete(f"/api/friends/{b['user_id']}", headers=auth(a)).status_code == 200
        with main.db() as conn:
            assert conn.execute(
                "SELECT status FROM friendship_setting_proposals WHERE id=?", (third,)
            ).fetchone()["status"] == "cancelled"


def test_settings_gate_letters_and_minimum_delay(tmp_path, monkeypatch):
    main = load_app(tmp_path)
    monkeypatch.setattr(main, "now_ts", lambda: 1_900_000_000)
    with TestClient(main.app) as client:
        a, b = register(client, "A"), register(client, "B")
        befriend(client, a, b)
        base = {
            "recipient_id": b["user_id"], "ciphertext": "YQ==", "nonce": "MTIzNDU2Nzg5MDEy",
            "encryption_key": "Yw==", "release_at": 1_900_000_030,
        }
        proposal = client.post(
            "/api/friendship-settings/proposals", headers=auth(a),
            json=settings_payload(b["user_id"], min_letter_delay_seconds=60),
        ).json()["id"]
        client.post(f"/api/friendship-settings/proposals/{proposal}/respond", headers=auth(b), json={"accept": True})
        denied = client.post("/api/messages", headers=auth(a), json=base)
        assert denied.status_code == 422
        base["release_at"] = 1_900_000_060
        assert client.post("/api/messages", headers=auth(a), json=base).status_code == 201

        disable = client.post(
            "/api/friendship-settings/proposals", headers=auth(a),
            json=settings_payload(b["user_id"], letters_enabled=False),
        ).json()["id"]
        client.post(f"/api/friendship-settings/proposals/{disable}/respond", headers=auth(b), json={"accept": True})
        assert client.post("/api/messages", headers=auth(a), json=base).status_code == 403


def test_disabled_letters_hide_existing_letters_and_gate_every_letter_action(tmp_path, monkeypatch):
    main = load_app(tmp_path)
    clock = {"now": 1_900_000_000}
    monkeypatch.setattr(main, "now_ts", lambda: clock["now"])
    with TestClient(main.app) as client:
        a, b, stranger = register(client, "A"), register(client, "B"), register(client, "C")
        befriend(client, a, b)
        base = {
            "recipient_id": b["user_id"], "ciphertext": "YQ==", "nonce": "MTIzNDU2Nzg5MDEy",
            "encryption_key": "Yw==", "title": "Bestehend",
        }
        timed = client.post(
            "/api/messages", headers=auth(a), json={**base, "mode": "timed", "release_at": clock["now"] + 60}
        ).json()["id"]
        manual = client.post(
            "/api/messages", headers=auth(a), json={**base, "mode": "manual"}
        ).json()["id"]
        mutual = client.post(
            "/api/messages", headers=auth(a), json={**base, "mode": "mutual"}
        ).json()["id"]

        disable = client.post(
            "/api/friendship-settings/proposals", headers=auth(a),
            json=settings_payload(b["user_id"], letters_enabled=False),
        ).json()["id"]
        assert client.post(
            f"/api/friendship-settings/proposals/{disable}/respond",
            headers=auth(b), json={"accept": True},
        ).status_code == 200

        assert client.get("/api/messages", headers=auth(b)).json() == []
        assert client.get("/api/outbox", headers=auth(a)).json() == []
        assert client.get(f"/api/messages/{timed}/content", headers=auth(b)).status_code == 403
        assert client.post(f"/api/messages/{manual}/release", headers=auth(a)).status_code == 403
        assert client.post(f"/api/messages/{mutual}/approve", headers=auth(a)).status_code == 403
        assert client.post(f"/api/messages/{mutual}/approve", headers=auth(b)).status_code == 403
        assert client.delete(f"/api/messages/{timed}", headers=auth(a)).status_code == 403
        assert client.put(
            f"/api/messages/{timed}/reaction", headers=auth(b), json={"emoji": "❤️"}
        ).status_code == 403

        # Non-participants must still receive the legacy not-found response rather than
        # learning whether a hidden letter exists.
        assert client.put(
            f"/api/messages/{timed}/reaction", headers=auth(stranger), json={"emoji": "❤️"}
        ).status_code == 404


def test_blocked_and_inactive_friendships_hide_and_gate_existing_letters(tmp_path, monkeypatch):
    main = load_app(tmp_path)
    monkeypatch.setattr(main, "now_ts", lambda: 1_900_000_000)
    with TestClient(main.app) as client:
        a, b, c, d = (register(client, name) for name in ("A", "B", "C", "D"))
        befriend(client, a, b)
        befriend(client, c, d)

        def send(sender, recipient):
            response = client.post("/api/messages", headers=auth(sender), json={
                "recipient_id": recipient["user_id"], "ciphertext": "YQ==",
                "nonce": "MTIzNDU2Nzg5MDEy", "encryption_key": "Yw==",
                "release_at": 1_900_000_060,
            })
            assert response.status_code == 201, response.text
            return response.json()["id"]

        blocked_letter = send(a, b)
        inactive_letter = send(c, d)
        assert client.post(f"/api/blocks/{b['user_id']}", headers=auth(a)).status_code == 200
        assert client.delete(f"/api/friends/{d['user_id']}", headers=auth(c)).status_code == 200

        for account in (a, b, c, d):
            assert client.get("/api/messages", headers=auth(account)).json() == []
            assert client.get("/api/outbox", headers=auth(account)).json() == []
        assert client.get(f"/api/messages/{blocked_letter}/content", headers=auth(b)).status_code == 403
        assert client.get(f"/api/messages/{inactive_letter}/content", headers=auth(d)).status_code == 403
        assert client.put(
            f"/api/messages/{blocked_letter}/reaction", headers=auth(a), json={"emoji": "👍"}
        ).status_code == 403
        assert client.put(
            f"/api/messages/{inactive_letter}/reaction", headers=auth(c), json={"emoji": "👍"}
        ).status_code == 403


def test_minimum_delay_allows_small_clock_race_but_never_unlocks_early(tmp_path, monkeypatch):
    main = load_app(tmp_path)
    clock = {"now": 1_900_000_000}
    monkeypatch.setattr(main, "now_ts", lambda: clock["now"])
    with TestClient(main.app) as client:
        a, b = register(client, "A"), register(client, "B")
        befriend(client, a, b)
        proposal = client.post(
            "/api/friendship-settings/proposals", headers=auth(a),
            json=settings_payload(b["user_id"], min_letter_delay_seconds=60),
        ).json()["id"]
        client.post(
            f"/api/friendship-settings/proposals/{proposal}/respond",
            headers=auth(b), json={"accept": True},
        )
        payload = {
            "recipient_id": b["user_id"], "ciphertext": "YQ==", "nonce": "MTIzNDU2Nzg5MDEy",
            "encryption_key": "Yw==", "release_at": 1_900_000_060,
        }

        # The client calculated the exact boundary immediately before encryption;
        # two seconds elapsed before the server validated the request.
        clock["now"] += 2
        accepted = client.post("/api/messages", headers=auth(a), json=payload)
        assert accepted.status_code == 201, accepted.text
        message_id = accepted.json()["id"]

        clock["now"] = 1_900_000_060
        assert client.get(f"/api/messages/{message_id}/content", headers=auth(b)).status_code == 423
        clock["now"] = 1_900_000_062
        assert client.get(f"/api/messages/{message_id}/content", headers=auth(b)).status_code == 200

        payload["release_at"] = 1_900_000_120
        clock["now"] = 1_900_000_063
        far_beyond_minimum = client.post("/api/messages", headers=auth(a), json=payload)
        assert far_beyond_minimum.status_code == 201, far_beyond_minimum.text

        payload["release_at"] = 1_900_000_120
        clock["now"] = 1_900_000_066
        above_tolerance = client.post("/api/messages", headers=auth(a), json=payload)
        assert above_tolerance.status_code == 422, above_tolerance.text


def test_existing_accepted_friendships_and_messages_migrate_idempotently(tmp_path):
    db_path = tmp_path / "test.db"
    conn = sqlite3.connect(db_path)
    conn.executescript("""
        CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE COLLATE NOCASE,
            password_salt TEXT, password_hash TEXT, created_at INTEGER NOT NULL);
        CREATE TABLE sessions (token_hash TEXT PRIMARY KEY, user_id INTEGER NOT NULL, created_at INTEGER NOT NULL);
        CREATE TABLE friend_requests (id INTEGER PRIMARY KEY, sender_id INTEGER NOT NULL, recipient_id INTEGER NOT NULL,
            status TEXT NOT NULL, created_at INTEGER NOT NULL, responded_at INTEGER);
        CREATE TABLE messages (id INTEGER PRIMARY KEY, sender_id INTEGER NOT NULL, recipient_id INTEGER NOT NULL,
            ciphertext TEXT NOT NULL, nonce TEXT NOT NULL, encryption_key TEXT NOT NULL, release_at INTEGER NOT NULL,
            created_at INTEGER NOT NULL, manual_release INTEGER NOT NULL DEFAULT 0, released_at INTEGER,
            FOREIGN KEY(sender_id) REFERENCES users(id), FOREIGN KEY(recipient_id) REFERENCES users(id));
        INSERT INTO users VALUES (1,'A',NULL,NULL,1700000000);
        INSERT INTO users VALUES (2,'B',NULL,NULL,1700000000);
        INSERT INTO friend_requests VALUES (4,2,1,'accepted',1700000001,1700000002);
        INSERT INTO messages VALUES (9,2,1,'YQ==','Yg==','Yw==',1700001000,1700000003,0,NULL);
    """)
    conn.commit()
    conn.close()

    main = load_app(tmp_path)
    main.initialize_database()
    main.initialize_database()
    check = sqlite3.connect(db_path)
    assert check.execute("SELECT user_low_id,user_high_id FROM friendships").fetchall() == [(1, 2)]
    assert check.execute(
        "SELECT letters_enabled,chats_enabled,ep_enabled,min_letter_delay_seconds FROM friendship_settings"
    ).fetchall() == [(1, 1, 0, 0)]
    assert check.execute("SELECT id,ciphertext FROM messages").fetchall() == [(9, "YQ==")]
    assert {row[2] for row in check.execute("PRAGMA foreign_key_list(message_reactions)")} == {"messages", "users"}
    assert {row[2] for row in check.execute("PRAGMA foreign_key_list(ep_proposals)")} == {
        "messages", "users", "friendships"
    }
    check.close()
