import hashlib
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


def auth(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


def register(client: TestClient, name: str) -> dict:
    response = client.post("/api/register", json={"name": name, "password": "password-123"})
    assert response.status_code == 200, response.text
    return response.json()


def befriend(client: TestClient, sender: dict, recipient: dict) -> None:
    request = client.post(
        "/api/friend-requests", headers=auth(sender["token"]), json={"recipient_id": recipient["user_id"]}
    )
    assert request.status_code == 201, request.text
    incoming = client.get("/api/friend-requests/incoming", headers=auth(recipient["token"])).json()
    assert len(incoming) == 1
    accepted = client.post(
        f"/api/friend-requests/{incoming[0]['id']}/respond",
        headers=auth(recipient["token"]), json={"accept": True},
    )
    assert accepted.status_code == 200, accepted.text


def test_accounts_user_discovery_and_friendship(tmp_path):
    main = load_app(tmp_path)
    with TestClient(main.app) as client:
        a = register(client, "Anna")
        b = register(client, "Bert")
        assert client.post("/api/register", json={"name": "anna", "password": "password-123"}).status_code == 409
        login = client.post("/api/login", json={"name": "Anna", "password": "password-123"})
        assert login.status_code == 200

        users = client.get("/api/users", headers=auth(a["token"])).json()
        assert [{k: row[k] for k in ("id", "name", "relationship")} for row in users] == [
            {"id": b["user_id"], "name": "Bert", "relationship": "none"}
        ]
        befriend(client, a, b)
        friends = client.get("/api/friends", headers=auth(a["token"])).json()
        assert friends[0]["id"] == b["user_id"] and friends[0]["name"] == "Bert"
        assert client.get("/api/users", headers=auth(b["token"])).json()[0]["relationship"] == "friends"


def test_messages_require_friendship_and_timed_unlock(tmp_path, monkeypatch):
    main = load_app(tmp_path)
    clock = {"now": 1_700_000_000}
    monkeypatch.setattr(main, "now_ts", lambda: clock["now"])
    with TestClient(main.app) as client:
        a = register(client, "A")
        b = register(client, "B")
        c = register(client, "C")
        payload = {
            "recipient_id": b["user_id"], "ciphertext": "Y2lwaGVy", "nonce": "bm9uY2U=",
            "encryption_key": "a2V5", "release_at": clock["now"] + 60,
        }
        assert client.post("/api/messages", headers=auth(a["token"]), json=payload).status_code == 403
        befriend(client, a, b)
        assert client.post("/api/messages", headers=auth(a["token"]), json=payload).status_code == 201
        before = client.get("/api/messages", headers=auth(b["token"])).json()[0]
        assert before["unlocked"] is False and before["encryption_key"] is None
        assert client.get("/api/messages", headers=auth(c["token"])).json() == []
        clock["now"] += 61
        after = client.get("/api/messages", headers=auth(b["token"])).json()[0]
        assert after["unlocked"] is True and after["encryption_key"] == "a2V5"


def test_manual_message_stays_locked_until_sender_releases(tmp_path, monkeypatch):
    main = load_app(tmp_path)
    clock = {"now": 1_700_000_000}
    monkeypatch.setattr(main, "now_ts", lambda: clock["now"])
    with TestClient(main.app) as client:
        a = register(client, "A")
        b = register(client, "B")
        befriend(client, a, b)
        payload = {
            "recipient_id": b["user_id"], "ciphertext": "Y2lwaGVy", "nonce": "bm9uY2U=",
            "encryption_key": "a2V5", "manual_release": True,
        }
        created = client.post("/api/messages", headers=auth(a["token"]), json=payload)
        assert created.status_code == 201
        message_id = created.json()["id"]
        clock["now"] += 400 * 24 * 60 * 60
        before = client.get("/api/messages", headers=auth(b["token"])).json()[0]
        assert before["manual_release"] is True and before["release_at"] is None
        assert before["unlocked"] is False and before["encryption_key"] is None
        outbox = client.get("/api/outbox", headers=auth(a["token"])).json()[0]
        assert outbox["id"] == message_id and outbox["released_at"] is None
        assert client.post(f"/api/messages/{message_id}/release", headers=auth(b["token"])).status_code == 404
        released = client.post(f"/api/messages/{message_id}/release", headers=auth(a["token"])).json()
        assert released["released_at"] == clock["now"]
        after = client.get("/api/messages", headers=auth(b["token"])).json()[0]
        assert after["unlocked"] is True and after["encryption_key"] == "a2V5"


def test_legacy_device_and_token_are_migrated(tmp_path):
    db_path = tmp_path / "test.db"
    token = "legacy-device-token"
    conn = sqlite3.connect(db_path)
    conn.executescript(
        """
        CREATE TABLE devices (id INTEGER PRIMARY KEY, name TEXT NOT NULL, token_hash TEXT NOT NULL UNIQUE, created_at INTEGER NOT NULL);
        CREATE TABLE messages (id INTEGER PRIMARY KEY, sender_id INTEGER NOT NULL, recipient_id INTEGER NOT NULL,
          ciphertext TEXT NOT NULL, nonce TEXT NOT NULL, encryption_key TEXT NOT NULL, release_at INTEGER NOT NULL,
          created_at INTEGER NOT NULL, FOREIGN KEY(sender_id) REFERENCES devices(id),
          FOREIGN KEY(recipient_id) REFERENCES devices(id));
        """
    )
    conn.execute(
        "INSERT INTO devices VALUES (5, 'Altprofil', ?, 1700000000)",
        (hashlib.sha256(token.encode()).hexdigest(),),
    )
    conn.commit()
    conn.close()

    main = load_app(tmp_path)
    with TestClient(main.app) as client:
        status = client.get("/api/status", headers=auth(token)).json()
        assert status["name"] == "Altprofil" and status["needs_password"] is True
        set_password = client.post(
            "/api/set-password", headers=auth(token), json={"password": "new-password-123"}
        )
        assert set_password.status_code == 200
        login = client.post("/api/login", json={"name": "Altprofil", "password": "new-password-123"})
        assert login.status_code == 200 and login.json()["user_id"] == 5
        migrated = {"token": token, "user_id": 5}
        other = register(client, "Neuprofil")
        befriend(client, migrated, other)
        sent = client.post("/api/messages", headers=auth(token), json={
            "recipient_id": other["user_id"], "ciphertext": "YQ==", "nonce": "Yg==",
            "encryption_key": "Yw==", "release_at": main.now_ts() + 60,
        })
        assert sent.status_code == 201, sent.text


def test_v1_devices_fk_upgrade_keeps_v4_message_references_and_endpoints_work(tmp_path, monkeypatch):
    db_path = tmp_path / "test.db"
    clock = 1_900_000_000
    tokens = {"A": "legacy-a-token", "B": "legacy-b-token"}
    conn = sqlite3.connect(db_path)
    conn.executescript(
        """
        CREATE TABLE devices (
          id INTEGER PRIMARY KEY, name TEXT NOT NULL, token_hash TEXT NOT NULL UNIQUE,
          created_at INTEGER NOT NULL
        );
        CREATE TABLE friend_requests (
          id INTEGER PRIMARY KEY, sender_id INTEGER NOT NULL, recipient_id INTEGER NOT NULL,
          status TEXT NOT NULL, created_at INTEGER NOT NULL, responded_at INTEGER
        );
        CREATE TABLE messages (
          id INTEGER PRIMARY KEY, sender_id INTEGER NOT NULL, recipient_id INTEGER NOT NULL,
          ciphertext TEXT NOT NULL, nonce TEXT NOT NULL, encryption_key TEXT NOT NULL,
          release_at INTEGER NOT NULL, created_at INTEGER NOT NULL,
          FOREIGN KEY(sender_id) REFERENCES devices(id),
          FOREIGN KEY(recipient_id) REFERENCES devices(id)
        );
        INSERT INTO friend_requests VALUES (3,1,2,'accepted',1899999900,1899999901);
        INSERT INTO messages VALUES (9,1,2,'YQ==','Yg==','Yw==',1900000060,1899999990);
        """
    )
    for user_id, name in enumerate(("A", "B"), start=1):
        conn.execute(
            "INSERT INTO devices VALUES (?,?,?,?)",
            (user_id, name, hashlib.sha256(tokens[name].encode()).hexdigest(), clock - 100),
        )
    conn.commit()
    conn.close()

    main = load_app(tmp_path)
    monkeypatch.setattr(main, "now_ts", lambda: clock)
    with TestClient(main.app) as client:
        with main.db() as upgraded:
            assert {row[2] for row in upgraded.execute("PRAGMA foreign_key_list(message_reactions)")} == {
                "messages", "users"
            }
            assert {row[2] for row in upgraded.execute("PRAGMA foreign_key_list(ep_proposals)")} == {
                "messages", "users", "friendships"
            }

        accounts = {
            name: {"token": token, "user_id": user_id}
            for user_id, (name, token) in enumerate(tokens.items(), start=1)
        }
        reacted = client.put(
            "/api/messages/9/reaction", headers=auth(tokens["B"]), json={"emoji": "👍"}
        )
        assert reacted.status_code == 200, reacted.text

        proposal = client.post(
            "/api/friendship-settings/proposals",
            headers=auth(tokens["A"]),
            json={
                "friend_id": accounts["B"]["user_id"],
                "letters_enabled": True,
                "chats_enabled": True,
                "ep_enabled": True,
                "min_letter_delay_seconds": 0,
            },
        )
        assert proposal.status_code == 201, proposal.text
        accepted = client.post(
            f"/api/friendship-settings/proposals/{proposal.json()['id']}/respond",
            headers=auth(tokens["B"]), json={"accept": True},
        )
        assert accepted.status_code == 200, accepted.text
        ep = client.post(
            "/api/ep/proposals", headers=auth(tokens["A"]),
            json={"beneficiary_id": 2, "points": 7, "title": "Altbrief", "letter_id": 9},
        )
        assert ep.status_code == 201, ep.text
