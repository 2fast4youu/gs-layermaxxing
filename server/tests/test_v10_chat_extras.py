from fastapi.testclient import TestClient

from test_v4_chat import auth, befriend, change_settings, load_app, register

MSG = {"ciphertext": "c", "nonce": "n", "encryption_key": "k"}


def pair(client):
    a, b = register(client, "anna"), register(client, "ben")
    befriend(client, a, b)
    change_settings(client, a, b)
    return a, b


def test_reactions_edit_delete_and_pin(tmp_path):
    main = load_app(tmp_path)
    with TestClient(main.app) as client:
        a, b = pair(client)
        mid = client.post(f"/api/chats/{b['user_id']}/messages", headers=auth(a), json=MSG).json()["id"]
        base_b = f"/api/chats/{a['user_id']}/messages/{mid}"
        base_a = f"/api/chats/{b['user_id']}/messages/{mid}"
        assert client.put(base_b + "/reaction", headers=auth(b), json={"emoji": "❤️"}).status_code == 200
        msg = client.get(f"/api/chats/{b['user_id']}/messages", headers=auth(a)).json()[0]
        assert msg["reactions"][0]["emoji"] == "❤️" and msg["kind"] == "text" and not msg["deleted"]
        assert client.put(base_b + "/reaction", headers=auth(b), json={"emoji": ""}).status_code == 200
        assert client.get(f"/api/chats/{b['user_id']}/messages", headers=auth(a)).json()[0]["reactions"] == []
        # Only the author edits or deletes.
        edit = {"ciphertext": "c2", "nonce": "n2", "encryption_key": "k2"}
        assert client.patch(base_b, headers=auth(b), json=edit).status_code == 403
        assert client.patch(base_a, headers=auth(a), json=edit).status_code == 200
        msg = client.get(f"/api/chats/{a['user_id']}/messages", headers=auth(b)).json()[0]
        assert msg["ciphertext"] == "c2" and msg["edited_at"]
        # Either side may pin.
        assert client.put(base_b + "/pin", headers=auth(b), json={"pinned": True}).status_code == 200
        assert client.get(f"/api/chats/{b['user_id']}/messages", headers=auth(a)).json()[0]["pinned_at"]
        assert client.delete(base_b, headers=auth(b)).status_code == 403
        assert client.delete(base_a, headers=auth(a)).status_code == 200
        msg = client.get(f"/api/chats/{a['user_id']}/messages", headers=auth(b)).json()[0]
        assert msg["deleted"] and msg["ciphertext"] is None and msg["pinned_at"] is None
        assert client.patch(base_a, headers=auth(a), json=edit).status_code == 409


def test_edit_window_closes_after_a_day(tmp_path, monkeypatch):
    main = load_app(tmp_path)
    clock = {"now": 1_900_000_000}
    monkeypatch.setattr(main, "now_ts", lambda: clock["now"])
    with TestClient(main.app) as client:
        a, b = pair(client)
        mid = client.post(f"/api/chats/{b['user_id']}/messages", headers=auth(a), json=MSG).json()["id"]
        clock["now"] += 25 * 3600
        r = client.patch(f"/api/chats/{b['user_id']}/messages/{mid}", headers=auth(a), json=MSG)
        assert r.status_code == 409


def test_media_messages_need_matching_attachment(tmp_path):
    main = load_app(tmp_path)
    with TestClient(main.app) as client:
        a, b = pair(client)
        url = f"/api/chats/{b['user_id']}/messages"
        assert client.post(url, headers=auth(a), json={**MSG, "kind": "image"}).status_code == 422
        assert client.post(url, headers=auth(a), json={**MSG, "kind": "image", "attachment_mime": "audio/mp4",
                                                       "attachment_ciphertext": "x", "attachment_nonce": "y"}).status_code == 422
        assert client.post(url, headers=auth(a), json={**MSG, "kind": "weird"}).status_code == 422
        mid = client.post(url, headers=auth(a), json={**MSG, "kind": "image", "attachment_mime": "image/jpeg",
                                                      "attachment_ciphertext": "x", "attachment_nonce": "y"}).json()["id"]
        listed = client.get(f"/api/chats/{a['user_id']}/messages", headers=auth(b)).json()[0]
        assert listed["kind"] == "image" and listed["has_attachment"] and "attachment_ciphertext" not in listed
        att = client.get(f"/api/chats/{a['user_id']}/messages/{mid}/attachment", headers=auth(b)).json()
        assert att["ciphertext"] == "x" and att["mime"] == "image/jpeg" and att["encryption_key"] == "k"
        stranger = register(client, "eve")
        assert client.get(f"/api/chats/{a['user_id']}/messages/{mid}/attachment", headers=auth(stranger)).status_code == 403
        sid = client.post(url, headers=auth(a), json={**MSG, "kind": "sticker"}).json()["id"]
        assert sid > mid


def test_typing_presence_and_chat_days_grow_island(tmp_path, monkeypatch):
    main = load_app(tmp_path)
    clock = {"now": 1_900_000_000 - (1_900_000_000 % 86400) + 3600}
    monkeypatch.setattr(main, "now_ts", lambda: clock["now"])
    with TestClient(main.app) as client:
        a, b = pair(client)
        assert client.post(f"/api/chats/{b['user_id']}/typing", headers=auth(a)).status_code == 200
        pres = client.get(f"/api/chats/{a['user_id']}/presence", headers=auth(b)).json()
        assert pres["typing"] and pres["online"] and pres["chat_days"] == 0
        clock["now"] += 20
        assert not client.get(f"/api/chats/{a['user_id']}/presence", headers=auth(b)).json()["typing"]
        for day in range(2):
            client.post(f"/api/chats/{b['user_id']}/messages", headers=auth(a), json=MSG)
            # One-sided days do not count.
            assert client.get(f"/api/chats/{a['user_id']}/presence", headers=auth(b)).json()["chat_day_today"] is False
            client.post(f"/api/chats/{a['user_id']}/messages", headers=auth(b), json=MSG)
            assert client.get(f"/api/chats/{a['user_id']}/presence", headers=auth(b)).json()["chat_day_today"] is True
            clock["now"] += 86400
        pres = client.get(f"/api/chats/{a['user_id']}/presence", headers=auth(b)).json()
        assert pres["chat_days"] == 2 and pres["chat_points"] == 2
        isl = client.get("/api/islands", headers=auth(a)).json()["friends"][0]
        assert isl["chat"] == 2 and isl["score"] >= 2


def test_thread_preview_hides_deleted_and_names_media(tmp_path):
    main = load_app(tmp_path)
    with TestClient(main.app) as client:
        a, b = pair(client)
        url = f"/api/chats/{b['user_id']}/messages"
        mid = client.post(url, headers=auth(a), json=MSG).json()["id"]
        client.delete(f"{url}/{mid}", headers=auth(a))
        t = client.get("/api/chats", headers=auth(b)).json()[0]
        assert t["last_deleted"] is True and t["last_ciphertext"] is None
        client.post(url, headers=auth(a), json={**MSG, "kind": "sticker"})
        t = client.get("/api/chats", headers=auth(b)).json()[0]
        assert t["last_kind"] == "sticker" and t["last_deleted"] is False
