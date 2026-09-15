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
    response = client.post("/api/register", json={"name": name, "password": "password-123", "device_name": "Testgerät"})
    assert response.status_code == 200, response.text
    result = response.json()
    assert len(result["recovery_code"]) >= 16
    return result


def befriend(client: TestClient, sender: dict, recipient: dict) -> None:
    made = client.post("/api/friend-requests", headers=auth(sender), json={"recipient_id": recipient["user_id"]})
    assert made.status_code == 201, made.text
    incoming = client.get("/api/friend-requests/incoming", headers=auth(recipient)).json()
    request_id = next(row["id"] for row in incoming if row["sender_id"] == sender["user_id"])
    assert client.post(f"/api/friend-requests/{request_id}/respond", headers=auth(recipient), json={"accept": True}).status_code == 200


def test_security_profile_sessions_recovery_and_rate_limit(tmp_path, monkeypatch):
    main = load_app(tmp_path)
    clock = {"now": 1_800_000_000}
    monkeypatch.setattr(main, "now_ts", lambda: clock["now"])
    with TestClient(main.app) as client:
        account = register(client, "Anna")
        recovery = account["recovery_code"]
        profile = client.patch("/api/profile", headers=auth(account), json={
            "name": "Anna Neu", "avatar_emoji": "🦊", "display_color": "#7C4DFF", "discoverable": False,
        })
        assert profile.status_code == 200 and profile.json()["name"] == "Anna Neu"
        assert client.get("/api/users", headers=auth(account)).json() == []

        changed = client.post("/api/password/change", headers=auth(account), json={
            "old_password": "password-123", "new_password": "password-456",
        })
        assert changed.status_code == 200
        login = client.post("/api/login", json={"name": "Anna Neu", "password": "password-456", "device_name": "Zweithandy"})
        assert login.status_code == 200
        second = login.json()
        sessions = client.get("/api/sessions", headers=auth(second)).json()
        assert len(sessions) == 2 and any(row["current"] for row in sessions)
        other_session = next(row for row in sessions if not row["current"])
        assert client.delete(f"/api/sessions/{other_session['id']}", headers=auth(second)).status_code == 200
        assert client.get("/api/status", headers=auth(account)).status_code == 401

        for _ in range(5):
            failed = client.post("/api/login", json={"name": "Anna Neu", "password": "wrong-password", "device_name": "X"})
        assert failed.status_code == 429
        assert client.post("/api/login", json={"name": "Anna Neu", "password": "password-456"}).status_code == 429
        reset = client.post("/api/recovery/reset", json={
            "name": "Anna Neu", "recovery_code": recovery, "new_password": "password-789", "device_name": "Recovery",
        })
        assert reset.status_code == 200
        assert client.post("/api/login", json={"name": "Anna Neu", "password": "password-789"}).status_code == 200


def test_friend_request_withdraw_remove_search_and_block(tmp_path):
    main = load_app(tmp_path)
    with TestClient(main.app) as client:
        a, b = register(client, "A"), register(client, "B")
        request = client.post("/api/friend-requests", headers=auth(a), json={"recipient_id": b["user_id"]}).json()
        assert client.delete(f"/api/friend-requests/{request['id']}", headers=auth(a)).status_code == 200
        assert client.get("/api/friend-requests/incoming", headers=auth(b)).json() == []
        befriend(client, a, b)
        assert client.delete(f"/api/friends/{b['user_id']}", headers=auth(a)).status_code == 200
        assert client.get("/api/friends", headers=auth(a)).json() == []
        assert client.post(f"/api/blocks/{b['user_id']}", headers=auth(a)).status_code == 200
        assert client.get("/api/users", headers=auth(a)).json() == []
        assert client.get("/api/users/search?q=B", headers=auth(a)).json() == []
        assert client.post("/api/friend-requests", headers=auth(b), json={"recipient_id": a["user_id"]}).status_code == 403
        assert client.delete(f"/api/blocks/{b['user_id']}", headers=auth(a)).status_code == 200
        assert client.get("/api/users/search?q=B", headers=auth(a)).json()[0]["name"] == "B"


def test_message_lifecycle_one_time_reactions_and_all_release_modes(tmp_path, monkeypatch):
    main = load_app(tmp_path)
    clock = {"now": 1_800_000_000}
    monkeypatch.setattr(main, "now_ts", lambda: clock["now"])
    monkeypatch.setattr(main.secrets, "randbelow", lambda _: 30)
    with TestClient(main.app) as client:
        a, b = register(client, "A"), register(client, "B")
        befriend(client, a, b)
        base = {"recipient_ids": [b["user_id"]], "ciphertext": "Ym9keQ==", "nonce": "bm9uY2U=", "encryption_key": "a2V5", "title": "Titel"}

        timed = client.post("/api/messages", headers=auth(a), json={**base, "mode": "timed", "release_at": clock["now"] + 60, "one_time": True}).json()["ids"][0]
        assert client.delete(f"/api/messages/{timed}", headers=auth(a)).status_code == 200
        timed = client.post("/api/messages", headers=auth(a), json={**base, "mode": "timed", "release_at": clock["now"] + 60, "one_time": True}).json()["ids"][0]
        assert client.get(f"/api/messages/{timed}/content", headers=auth(b)).status_code == 423
        clock["now"] += 61
        first = client.get(f"/api/messages/{timed}/content", headers=auth(b))
        assert first.status_code == 200 and first.json()["ciphertext"] == "Ym9keQ=="
        assert client.get(f"/api/messages/{timed}/content", headers=auth(b)).status_code == 410
        assert client.delete(f"/api/messages/{timed}", headers=auth(a)).status_code == 409
        assert client.put(f"/api/messages/{timed}/reaction", headers=auth(b), json={"emoji": "❤️"}).status_code == 200
        assert client.get("/api/outbox", headers=auth(a)).json()[0]["reactions"][0]["emoji"] == "❤️"

        manual = client.post("/api/messages", headers=auth(a), json={**base, "mode": "manual"}).json()["ids"][0]
        assert client.post(f"/api/messages/{manual}/release", headers=auth(a)).status_code == 200
        assert client.get(f"/api/messages/{manual}/content", headers=auth(b)).status_code == 200

        mutual = client.post("/api/messages", headers=auth(a), json={**base, "mode": "mutual"}).json()["ids"][0]
        assert client.post(f"/api/messages/{mutual}/approve", headers=auth(a)).status_code == 200
        assert client.get(f"/api/messages/{mutual}/content", headers=auth(b)).status_code == 423
        assert client.post(f"/api/messages/{mutual}/approve", headers=auth(b)).status_code == 200
        assert client.get(f"/api/messages/{mutual}/content", headers=auth(b)).status_code == 200

        random_id = client.post("/api/messages", headers=auth(a), json={
            **base, "mode": "random", "random_from": clock["now"] + 10, "random_to": clock["now"] + 100,
        }).json()["ids"][0]
        clock["now"] += 39
        assert client.get(f"/api/messages/{random_id}/content", headers=auth(b)).status_code == 423
        clock["now"] += 1
        assert client.get(f"/api/messages/{random_id}/content", headers=auth(b)).status_code == 200

        clock["now"] += 200
        presence = client.post("/api/messages", headers=auth(a), json={**base, "mode": "presence"}).json()["ids"][0]
        assert client.get(f"/api/messages/{presence}/content", headers=auth(b)).status_code == 200
        clock["now"] += 200
        assert client.get(f"/api/messages/{presence}/content", headers=auth(b)).status_code == 200


def test_groups_multi_recipient_and_encrypted_attachment(tmp_path, monkeypatch):
    main = load_app(tmp_path)
    monkeypatch.setattr(main, "now_ts", lambda: 1_800_000_000)
    with TestClient(main.app) as client:
        a, b, c = register(client, "A"), register(client, "B"), register(client, "C")
        befriend(client, a, b)
        befriend(client, a, c)
        group = client.post("/api/groups", headers=auth(a), json={
            "name": "Testgruppe", "member_ids": [b["user_id"], c["user_id"]],
        })
        assert group.status_code == 201
        groups = client.get("/api/groups", headers=auth(b)).json()
        assert groups[0]["name"] == "Testgruppe" and len(groups[0]["members"]) == 3
        sent = client.post("/api/messages", headers=auth(a), json={
            "group_id": group.json()["id"], "ciphertext": "Ym9keQ==", "nonce": "bm9uY2U=", "encryption_key": "a2V5",
            "mode": "timed", "release_at": 1_800_000_060, "attachment_name": "bild.jpg",
            "attachment_mime": "image/jpeg", "attachment_ciphertext": "YXR0YWNobWVudA==", "attachment_nonce": "bm9uY2Uy",
        })
        assert sent.status_code == 201 and len(sent.json()["ids"]) == 2
        monkeypatch.setattr(main, "now_ts", lambda: 1_800_000_061)
        for account in (b, c):
            message = client.get("/api/messages", headers=auth(account)).json()[0]
            content = client.get(f"/api/messages/{message['id']}/content", headers=auth(account)).json()
            assert content["attachment_name"] == "bild.jpg"
            assert content["attachment_ciphertext"] == "YXR0YWNobWVudA=="


def test_encrypted_shared_topics_lifecycle_and_permissions(tmp_path, monkeypatch):
    main = load_app(tmp_path)
    clock = {"now": 1_800_000_000}
    monkeypatch.setattr(main, "now_ts", lambda: clock["now"])
    encrypted = {"ciphertext": "ZW5jcnlwdGVkLXRvcGlj", "nonce": "bm9uY2U=", "encryption_key": "a2V5"}
    with TestClient(main.app) as client:
        a, b, c = register(client, "A"), register(client, "B"), register(client, "C")

        personal = client.post("/api/topics", headers=auth(a), json=encrypted)
        assert personal.status_code == 201, personal.text
        personal_id = personal.json()["id"]
        listed = client.get("/api/topics", headers=auth(a)).json()
        assert listed[0]["id"] == personal_id and listed[0]["target_type"] == "personal"
        assert listed[0]["ciphertext"] == encrypted["ciphertext"]
        assert client.get("/api/topics", headers=auth(b)).json() == []

        denied = client.post("/api/topics", headers=auth(a), json={**encrypted, "peer_user_id": b["user_id"]})
        assert denied.status_code == 403
        befriend(client, a, b)
        shared = client.post("/api/topics", headers=auth(a), json={**encrypted, "peer_user_id": b["user_id"]})
        assert shared.status_code == 201, shared.text
        shared_id = shared.json()["id"]
        topic_for_b = next(row for row in client.get("/api/topics", headers=auth(b)).json() if row["id"] == shared_id)
        assert topic_for_b["target_type"] == "friend" and topic_for_b["target_name"] == "A"
        assert topic_for_b["can_delete"] is False

        completed = client.patch(f"/api/topics/{shared_id}", headers=auth(b), json={"completed": True})
        assert completed.status_code == 200
        updated = next(row for row in client.get("/api/topics", headers=auth(a)).json() if row["id"] == shared_id)
        assert updated["completed_at"] == clock["now"] and updated["completed_by_name"] == "B"
        assert client.patch(f"/api/topics/{shared_id}", headers=auth(a), json={"completed": False}).status_code == 200
        assert client.delete(f"/api/topics/{shared_id}", headers=auth(b)).status_code == 403
        assert client.delete(f"/api/topics/{shared_id}", headers=auth(a)).status_code == 200

        befriend(client, a, c)
        group = client.post("/api/groups", headers=auth(a), json={
            "name": "Treffen", "member_ids": [b["user_id"], c["user_id"]],
        }).json()
        group_topic = client.post("/api/topics", headers=auth(a), json={**encrypted, "group_id": group["id"]})
        assert group_topic.status_code == 201
        listed_for_c = client.get("/api/topics", headers=auth(c)).json()
        assert listed_for_c[0]["target_type"] == "group" and listed_for_c[0]["target_name"] == "Treffen"
        assert client.patch(f"/api/topics/{group_topic.json()['id']}", headers=auth(c), json={"completed": True}).status_code == 200

        both_targets = client.post("/api/topics", headers=auth(a), json={
            **encrypted, "peer_user_id": b["user_id"], "group_id": group["id"],
        })
        assert both_targets.status_code == 422


def test_v2_manual_message_mode_is_preserved(tmp_path):
    import sqlite3
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
            created_at INTEGER NOT NULL, manual_release INTEGER NOT NULL DEFAULT 0, released_at INTEGER);
        INSERT INTO users VALUES (1,'A',NULL,NULL,1700000000);
        INSERT INTO users VALUES (2,'B',NULL,NULL,1700000000);
        INSERT INTO messages VALUES (7,1,2,'YQ==','Yg==','Yw==',2147483647,1700000000,1,1700000100);
    """)
    conn.commit(); conn.close()
    main = load_app(tmp_path)
    with TestClient(main.app):
        check = sqlite3.connect(db_path)
        assert check.execute("SELECT mode FROM messages WHERE id=7").fetchone()[0] == "manual"
        check.close()
