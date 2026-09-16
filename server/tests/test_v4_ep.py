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
    request_id = client.post(
        "/api/friend-requests", headers=auth(a), json={"recipient_id": b["user_id"]}
    ).json()["id"]
    client.post(f"/api/friend-requests/{request_id}/respond", headers=auth(b), json={"accept": True})


def enable_ep(client: TestClient, a: dict, b: dict) -> None:
    proposal = client.post("/api/friendship-settings/proposals", headers=auth(a), json={
        "friend_id": b["user_id"], "letters_enabled": True, "chats_enabled": True,
        "ep_enabled": True, "min_letter_delay_seconds": 0,
    })
    assert proposal.status_code == 201, proposal.text
    accepted = client.post(
        f"/api/friendship-settings/proposals/{proposal.json()['id']}/respond",
        headers=auth(b), json={"accept": True},
    )
    assert accepted.status_code == 200


def letter(client: TestClient, sender: dict, recipient: dict, now: int) -> int:
    response = client.post("/api/messages", headers=auth(sender), json={
        "recipient_id": recipient["user_id"], "ciphertext": "YQ==", "nonce": "MTIzNDU2Nzg5MDEy",
        "encryption_key": "Yw==", "release_at": now + 60, "title": "Brief",
    })
    assert response.status_code == 201, response.text
    return response.json()["id"]


def test_ep_requires_bilateral_enablement_and_beneficiary_approval(tmp_path, monkeypatch):
    main = load_app(tmp_path)
    clock = {"now": 1_900_000_000}
    monkeypatch.setattr(main, "now_ts", lambda: clock["now"])
    with TestClient(main.app) as client:
        anna, bert = register(client, "Anna"), register(client, "Bert")
        befriend(client, anna, bert)
        denied = client.post("/api/ep/proposals", headers=auth(anna), json={
            "beneficiary_id": bert["user_id"], "points": 25, "title": "Mut", "description": "Guter Brief",
        })
        assert denied.status_code == 403

        enable_ep(client, anna, bert)
        made = client.post("/api/ep/proposals", headers=auth(anna), json={
            "beneficiary_id": bert["user_id"], "points": 25, "title": "Mut", "description": "Guter Brief",
        })
        assert made.status_code == 201, made.text
        proposal_id = made.json()["id"]
        assert client.post(
            f"/api/ep/proposals/{proposal_id}/respond", headers=auth(anna), json={"accept": True}
        ).status_code == 403

        before = client.get("/api/ep", headers=auth(bert)).json()
        assert before["totals"] == {"given": 0, "received": 0}
        assert before["incoming_pending"][0]["title"] == "Mut"
        accepted = client.post(
            f"/api/ep/proposals/{proposal_id}/respond", headers=auth(bert), json={"accept": True}
        )
        assert accepted.json()["status"] == "accepted"
        for account, expected in ((anna, {"given": 25, "received": 0}), (bert, {"given": 0, "received": 25})):
            view = client.get("/api/ep", headers=auth(account)).json()
            assert view["totals"] == expected
            assert view["history"][0]["points"] == 25
            assert view["level"]["name"]


def test_ep_validation_and_letter_links_are_same_friendship_only(tmp_path, monkeypatch):
    main = load_app(tmp_path)
    clock = {"now": 1_900_000_000}
    monkeypatch.setattr(main, "now_ts", lambda: clock["now"])
    with TestClient(main.app) as client:
        a, b, c = register(client, "A"), register(client, "B"), register(client, "C")
        befriend(client, a, b)
        befriend(client, a, c)
        enable_ep(client, a, b)
        enable_ep(client, a, c)
        ab_letter = letter(client, a, b, clock["now"])
        instant = client.post(f"/api/chats/{b['user_id']}/messages", headers=auth(a), json={
            "ciphertext": "YQ==", "nonce": "MTIzNDU2Nzg5MDEy", "encryption_key": "Yw==",
        }).json()["id"]

        invalid = [
            {"beneficiary_id": a["user_id"], "points": 5, "title": "Selbst"},
            {"beneficiary_id": b["user_id"], "points": 0, "title": "Null"},
            {"beneficiary_id": b["user_id"], "points": 1001, "title": "Viel"},
            {"beneficiary_id": b["user_id"], "points": 5, "title": "   "},
            {"beneficiary_id": c["user_id"], "points": 5, "title": "Falscher Link", "letter_id": ab_letter},
            {"beneficiary_id": b["user_id"], "points": 5, "title": "Chat ist kein Brief", "letter_id": instant},
        ]
        for payload in invalid:
            assert client.post("/api/ep/proposals", headers=auth(a), json=payload).status_code in (403, 422)

        linked = client.post("/api/ep/proposals", headers=auth(a), json={
            "beneficiary_id": b["user_id"], "points": 10, "title": "Briefwirkung", "letter_id": ab_letter,
        })
        assert linked.status_code == 201, linked.text
        proposal_id = linked.json()["id"]
        assert client.post(
            f"/api/ep/proposals/{proposal_id}/respond", headers=auth(b), json={"accept": False}
        ).json()["status"] == "rejected"
        assert client.get("/api/ep", headers=auth(a)).json()["totals"]["given"] == 0

        linked2 = client.post("/api/ep/proposals", headers=auth(a), json={
            "beneficiary_id": b["user_id"], "points": 10, "title": "Bleibt", "letter_id": ab_letter,
        }).json()["id"]
        client.post(f"/api/ep/proposals/{linked2}/respond", headers=auth(b), json={"accept": True})
        with main.db() as conn:
            conn.execute("DELETE FROM messages WHERE id=?", (ab_letter,))
        assert client.get("/api/ep", headers=auth(a)).json()["history"][0]["letter_id"] is None
