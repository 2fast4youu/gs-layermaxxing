"""Freundesfunke: anonymous, one-way, positive — and provably so.

The recipient-facing responses are the anonymity boundary: they must never
carry a sender field or a timestamp, and they must not shrink when the
recipient blocks or unfriends someone (a vanishing spark would name its
sender). The sender learns exactly one bit and no time.
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
    assert response.status_code == 200
    return response.json()


def befriend(client: TestClient, a: dict, b: dict) -> None:
    request_id = client.post(
        "/api/friend-requests", headers=auth(a), json={"recipient_id": b["user_id"]}
    ).json()["id"]
    client.post(f"/api/friend-requests/{request_id}/respond", headers=auth(b), json={"accept": True})


def spark(client: TestClient, sender: dict, recipient: dict, text: str = "c1") -> dict:
    return client.post("/api/sparks", headers=auth(sender), json={
        "recipient_id": recipient["user_id"], "ciphertext": text, "nonce": "n", "encryption_key": "k",
    })


def test_spark_requires_unblocked_friendship_and_never_self(tmp_path):
    main = load_app(tmp_path)
    with TestClient(main.app) as client:
        a, b, stranger = register(client, "A"), register(client, "B"), register(client, "S")
        assert spark(client, a, a).status_code == 422
        assert spark(client, a, stranger).status_code == 403
        befriend(client, a, b)
        assert spark(client, a, b).status_code == 201
        client.post(f"/api/blocks/{b['user_id']}", headers=auth(a))
        assert spark(client, a, b).status_code == 403


def test_recipient_response_carries_no_sender_and_no_time(tmp_path):
    main = load_app(tmp_path)
    with TestClient(main.app) as client:
        a, b = register(client, "A"), register(client, "B")
        befriend(client, a, b)
        assert spark(client, a, b, "geheim-zart").status_code == 201
        inbox = client.get("/api/sparks", headers=auth(b)).json()
        assert len(inbox) == 1
        # The whole anonymity contract in one assertion: exactly these keys.
        assert set(inbox[0].keys()) == {"id", "ciphertext", "nonce", "encryption_key", "opened"}
        assert inbox[0]["opened"] is False


def test_open_is_recipient_only_idempotent_and_flips_sender_status(tmp_path):
    main = load_app(tmp_path)
    with TestClient(main.app) as client:
        a, b = register(client, "A"), register(client, "B")
        befriend(client, a, b)
        spark_id = spark(client, a, b).json()["id"]
        outbox = client.get("/api/sparks/outbox", headers=auth(a)).json()
        assert outbox[0]["status"] == "unterwegs"
        # No timestamps toward the sender either.
        assert set(outbox[0].keys()) == {"id", "recipient_id", "recipient_name", "status"}
        # Only the recipient may open; the sender cannot fake an open.
        assert client.post(f"/api/sparks/{spark_id}/open", headers=auth(a)).status_code == 404
        assert client.post(f"/api/sparks/{spark_id}/open", headers=auth(b)).status_code == 200
        assert client.post(f"/api/sparks/{spark_id}/open", headers=auth(b)).status_code == 200
        assert client.get("/api/sparks", headers=auth(b)).json()[0]["opened"] is True
        assert client.get("/api/sparks/outbox", headers=auth(a)).json()[0]["status"] == "geöffnet"


def test_blocking_after_receipt_does_not_unmask_by_removal(tmp_path):
    # If blocking A made A's spark vanish, the recipient could identify the
    # sender by blocking friends one by one. The spark stays.
    main = load_app(tmp_path)
    with TestClient(main.app) as client:
        a, b = register(client, "A"), register(client, "B")
        befriend(client, a, b)
        assert spark(client, a, b).status_code == 201
        client.post(f"/api/blocks/{a['user_id']}", headers=auth(b))
        assert len(client.get("/api/sparks", headers=auth(b)).json()) == 1


def test_mute_hides_sender_silently_and_sender_keeps_seeing_unterwegs(tmp_path):
    main = load_app(tmp_path)
    with TestClient(main.app) as client:
        a, b = register(client, "A"), register(client, "B")
        befriend(client, a, b)
        first = spark(client, a, b).json()["id"]
        assert client.post(f"/api/sparks/{first}/mute", headers=auth(b)).status_code == 200
        # Existing and future sparks of that anonymous sender disappear for B …
        assert client.get("/api/sparks", headers=auth(b)).json() == []
        assert spark(client, a, b).status_code == 201  # … but sending still "works".
        assert client.get("/api/sparks", headers=auth(b)).json() == []
        # A learns nothing: both sparks read "unterwegs" forever.
        statuses = [row["status"] for row in client.get("/api/sparks/outbox", headers=auth(a)).json()]
        assert statuses == ["unterwegs", "unterwegs"]
        # Muting is recipient-only.
        assert client.post(f"/api/sparks/{first}/mute", headers=auth(a)).status_code == 404


def test_report_is_recorded_for_recipient_only(tmp_path):
    main = load_app(tmp_path)
    with TestClient(main.app) as client:
        a, b = register(client, "A"), register(client, "B")
        befriend(client, a, b)
        spark_id = spark(client, a, b).json()["id"]
        assert client.post(f"/api/sparks/{spark_id}/report", headers=auth(a)).status_code == 404
        assert client.post(f"/api/sparks/{spark_id}/report", headers=auth(b)).status_code == 200
        import sqlite3
        conn = sqlite3.connect(tmp_path / "test.db")
        try:
            count = conn.execute("SELECT COUNT(*) FROM spark_reports WHERE spark_id=?", (spark_id,)).fetchone()[0]
        finally:
            conn.close()
        assert count == 1


def test_rate_limit_per_pair_and_day(tmp_path, monkeypatch):
    main = load_app(tmp_path)
    clock = {"now": 1_900_000_000}
    monkeypatch.setattr(main, "now_ts", lambda: clock["now"])
    with TestClient(main.app) as client:
        a, b = register(client, "A"), register(client, "B")
        befriend(client, a, b)
        for _ in range(main.SPARK_PAIR_DAILY_LIMIT):
            assert spark(client, a, b).status_code == 201
        assert spark(client, a, b).status_code == 429
        clock["now"] += 86_401
        assert spark(client, a, b).status_code == 201


def test_sender_daily_budget_caps_a_spray_across_many_friends(tmp_path, monkeypatch):
    """The per-pair limit alone would let one account spray every friend at once.

    The second, wider budget is what makes that a bounded amount of noise, so it
    has to hold across recipients and reset on its own day boundary.
    """
    main = load_app(tmp_path)
    clock = {"now": 1_900_000_000}
    monkeypatch.setattr(main, "now_ts", lambda: clock["now"])
    with TestClient(main.app) as client:
        sender = register(client, "Sender")
        # Enough friends that the per-pair limit can never be the binding one.
        needed = main.SPARK_SENDER_DAILY_LIMIT // main.SPARK_PAIR_DAILY_LIMIT + 2
        friends = [register(client, f"F{index}") for index in range(needed)]
        for friend in friends:
            befriend(client, sender, friend)

        sent = 0
        for friend in friends:
            for _ in range(main.SPARK_PAIR_DAILY_LIMIT):
                if spark(client, sender, friend).status_code != 201:
                    break
                sent += 1
        assert sent == main.SPARK_SENDER_DAILY_LIMIT

        # Every remaining friend is refused too: the budget is per sender, not per pair.
        for friend in friends:
            assert spark(client, sender, friend).status_code == 429
        # Being refused must not tell the sender anything about a recipient, so
        # nothing was written either.
        for friend in friends:
            inbox = client.get("/api/sparks", headers=auth(friend)).json()
            assert len(inbox) <= main.SPARK_PAIR_DAILY_LIMIT

        clock["now"] += 86_401
        assert spark(client, sender, friends[0]).status_code == 201


def test_overlong_spark_is_rejected(tmp_path):
    main = load_app(tmp_path)
    with TestClient(main.app) as client:
        a, b = register(client, "A"), register(client, "B")
        befriend(client, a, b)
        response = spark(client, a, b, "x" * (main.SPARK_MAX_CIPHERTEXT + 1))
        assert response.status_code == 422
