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


def enable(client: TestClient, a: dict, b: dict, **changes) -> None:
    payload = {"friend_id": b["user_id"], "letters_enabled": True, "chats_enabled": True,
               "ep_enabled": False, "min_letter_delay_seconds": 0}
    payload.update(changes)
    proposal = client.post("/api/friendship-settings/proposals", headers=auth(a), json=payload).json()["id"]
    client.post(f"/api/friendship-settings/proposals/{proposal}/respond", headers=auth(b), json={"accept": True})


def test_chats_preview_carries_last_instant_message_for_the_conversation_list(tmp_path, monkeypatch):
    # The conversation overview needs a preview of the newest instant message.
    # Instant messages are released on creation and their key is already handed
    # out by /api/chats/{id}/messages, so this adds no disclosure.
    main = load_app(tmp_path)
    clock = {"now": 1_900_000_000}
    monkeypatch.setattr(main, "now_ts", lambda: clock["now"])
    with TestClient(main.app) as client:
        a, b = register(client, "A"), register(client, "B")
        befriend(client, a, b)
        client.post(f"/api/chats/{b['user_id']}/messages", headers=auth(a),
                    json={"ciphertext": "old", "nonce": "n1", "encryption_key": "k1"})
        clock["now"] += 10
        client.post(f"/api/chats/{b['user_id']}/messages", headers=auth(a),
                    json={"ciphertext": "new", "nonce": "n2", "encryption_key": "k2"})
        thread = client.get("/api/chats", headers=auth(b)).json()[0]
        # Newest message wins the preview, and it exposes only the already-released key.
        assert thread["last_ciphertext"] == "new"
        assert thread["last_nonce"] == "n2"
        assert thread["last_encryption_key"] == "k2"
        assert thread["last_sender_id"] == a["user_id"]


def test_sealed_letters_never_appear_in_the_chats_preview(tmp_path, monkeypatch):
    main = load_app(tmp_path)
    clock = {"now": 1_900_000_000}
    monkeypatch.setattr(main, "now_ts", lambda: clock["now"])
    with TestClient(main.app) as client:
        a, b = register(client, "A"), register(client, "B")
        befriend(client, a, b)
        # A locked letter must not create or fill a conversation preview.
        client.post("/api/messages", headers=auth(a), json={
            "recipient_id": b["user_id"], "ciphertext": "secret", "nonce": "MTIzNDU2Nzg5MDEy",
            "encryption_key": "Yw==", "release_at": clock["now"] + 3600, "title": "Geheim",
        })
        assert client.get("/api/chats", headers=auth(b)).json() == []
        client.post(f"/api/chats/{b['user_id']}/messages", headers=auth(a),
                    json={"ciphertext": "hi", "nonce": "n", "encryption_key": "k"})
        thread = client.get("/api/chats", headers=auth(b)).json()[0]
        assert thread["last_ciphertext"] == "hi"  # the chat message, not the sealed letter


def test_incoming_and_outgoing_settings_proposals_are_exposed(tmp_path):
    # The delivery of a bilateral settings proposal to both sides is the contract
    # the visible request cards and dialog build on.
    main = load_app(tmp_path)
    with TestClient(main.app) as client:
        a, b = register(client, "A"), register(client, "B")
        befriend(client, a, b)
        proposal = client.post("/api/friendship-settings/proposals", headers=auth(a), json={
            "friend_id": b["user_id"], "letters_enabled": True, "chats_enabled": True,
            "ep_enabled": True, "min_letter_delay_seconds": 0,
        }).json()["id"]
        for_b = client.get("/api/friendship-settings", headers=auth(b)).json()[0]
        assert for_b["incoming_proposal"]["id"] == proposal
        assert for_b["incoming_proposal"]["ep_enabled"] is True
        assert for_b["outgoing_proposal"] is None
        for_a = client.get("/api/friendship-settings", headers=auth(a)).json()[0]
        assert for_a["outgoing_proposal"]["id"] == proposal
        assert for_a["incoming_proposal"] is None


def test_reviving_a_friendship_resets_settings_to_defaults(tmp_path):
    # Block/unblock/re-befriend must not silently re-enable EP or a relaxed delay
    # that nobody confirmed a second time. The bilateral-only rule requires defaults.
    main = load_app(tmp_path)
    with TestClient(main.app) as client:
        a, b = register(client, "A"), register(client, "B")
        befriend(client, a, b)
        enable(client, a, b, ep_enabled=True, min_letter_delay_seconds=600)
        settings = client.get("/api/friendship-settings", headers=auth(a)).json()[0]
        assert settings["ep_enabled"] is True and settings["min_letter_delay_seconds"] == 600

        client.post(f"/api/blocks/{b['user_id']}", headers=auth(a))
        client.delete(f"/api/blocks/{b['user_id']}", headers=auth(a))
        befriend(client, a, b)

        revived = client.get("/api/friendship-settings", headers=auth(a)).json()[0]
        assert revived["ep_enabled"] is False
        assert revived["min_letter_delay_seconds"] == 0
        assert revived["letters_enabled"] is True and revived["chats_enabled"] is True


def test_letter_lists_are_ordered_stably_for_multi_recipient_letters(tmp_path, monkeypatch):
    # A letter to several recipients stores rows with an identical created_at; the
    # merged thread needs a deterministic (created_at, id) order.
    main = load_app(tmp_path)
    clock = {"now": 1_900_000_000}
    monkeypatch.setattr(main, "now_ts", lambda: clock["now"])
    with TestClient(main.app) as client:
        a, b, c = register(client, "A"), register(client, "B"), register(client, "C")
        befriend(client, a, b)
        befriend(client, a, c)
        client.post("/api/messages", headers=auth(a), json={
            "recipient_ids": [b["user_id"], c["user_id"]], "ciphertext": "YQ==",
            "nonce": "MTIzNDU2Nzg5MDEy", "encryption_key": "Yw==", "release_at": clock["now"] + 3600, "title": "Rundbrief",
        })
        outbox = client.get("/api/outbox", headers=auth(a)).json()
        ids = [row["id"] for row in outbox]
        assert ids == sorted(ids, reverse=True)  # created_at DESC, id DESC → strictly descending ids
