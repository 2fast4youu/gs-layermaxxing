"""Real-life quests and friendship islands (Inselwelt)."""

import importlib
import os
from pathlib import Path

from fastapi.testclient import TestClient


def load_app(tmp_path: Path):
    os.environ["DB_PATH"] = str(tmp_path / "test.db")
    import app.main as main
    importlib.reload(main)
    return main


def auth(a: dict) -> dict[str, str]:
    return {"Authorization": f"Bearer {a['token']}"}


def register(client, name):
    r = client.post("/api/register", json={"name": name, "password": "password-123"})
    assert r.status_code == 200
    return r.json()


def befriend(client, a, b):
    client.post("/api/friend-requests", headers=auth(a), json={"recipient_id": b["user_id"]})
    rid = client.get("/api/friend-requests/incoming", headers=auth(b)).json()[0]["id"]
    assert client.post(f"/api/friend-requests/{rid}/respond", headers=auth(b), json={"accept": True}).status_code == 200


def test_quest_only_between_friends(tmp_path):
    main = load_app(tmp_path)
    with TestClient(main.app) as c:
        a, b = register(c, "A"), register(c, "B")
        r = c.post("/api/quests", headers=auth(a), json={"peer_user_id": b["user_id"], "title": "Wandern"})
        assert r.status_code == 403
        befriend(c, a, b)
        r = c.post("/api/quests", headers=auth(a), json={"peer_user_id": b["user_id"], "title": "Wandern", "icon": "hike", "points": 30})
        assert r.status_code == 201
        q = c.get("/api/quests", headers=auth(b)).json()
        assert q[0]["title"] == "Wandern" and q[0]["target_name"] == "A" and q[0]["can_delete"] is False


def test_needs_exactly_one_target_and_valid_icon(tmp_path):
    main = load_app(tmp_path)
    with TestClient(main.app) as c:
        a = register(c, "A")
        assert c.post("/api/quests", headers=auth(a), json={"title": "x"}).status_code == 422
        b = register(c, "B"); befriend(c, a, b)
        assert c.post("/api/quests", headers=auth(a), json={"peer_user_id": b["user_id"], "title": "x", "icon": "nope"}).status_code == 422
        assert c.post("/api/quests", headers=auth(a), json={"peer_user_id": b["user_id"], "title": "x", "points": 500}).status_code == 422


def test_completing_grows_the_island(tmp_path):
    main = load_app(tmp_path)
    with TestClient(main.app) as c:
        a, b = register(c, "A"), register(c, "B")
        befriend(c, a, b)
        isl = c.get("/api/islands", headers=auth(a)).json()
        assert isl["friends"][0]["level"] == 1 and isl["friends"][0]["score"] == 0
        qid = c.post("/api/quests", headers=auth(a), json={"peer_user_id": b["user_id"], "title": "Gipfel", "points": 50}).json()["id"]
        assert c.patch(f"/api/quests/{qid}", headers=auth(b), json={"completed": True}).status_code == 200
        for who in (a, b):
            f = c.get("/api/islands", headers=auth(who)).json()["friends"][0]
            assert f["qp"] == 50 and f["level"] == 2 and f["next_at"] == 120
        assert c.get("/api/quests", headers=auth(a)).json()[0]["completed_by_name"] == "B"


def test_group_quest_grows_every_pair_and_only_creator_deletes(tmp_path):
    main = load_app(tmp_path)
    with TestClient(main.app) as c:
        a, b, d = register(c, "A"), register(c, "B"), register(c, "D")
        befriend(c, a, b); befriend(c, a, d); befriend(c, b, d)
        g = c.post("/api/groups", headers=auth(a), json={"name": "Wandertruppe", "member_ids": [b["user_id"], d["user_id"]]})
        assert g.status_code in (200, 201)
        gid = c.get("/api/groups", headers=auth(a)).json()[0]["id"]
        qid = c.post("/api/quests", headers=auth(b), json={"group_id": gid, "title": "Grillen", "points": 40}).json()["id"]
        assert c.delete(f"/api/quests/{qid}", headers=auth(a)).status_code == 403
        c.patch(f"/api/quests/{qid}", headers=auth(d), json={"completed": True})
        isl = {f["friend_id"]: f for f in c.get("/api/islands", headers=auth(a)).json()["friends"]}
        assert isl[b["user_id"]]["qp"] == 40 and isl[d["user_id"]]["qp"] == 40
        assert c.get("/api/islands", headers=auth(a)).json()["qp"] == 40
        assert c.delete(f"/api/quests/{qid}", headers=auth(b)).status_code == 200


def test_outsider_cannot_see_or_touch(tmp_path):
    main = load_app(tmp_path)
    with TestClient(main.app) as c:
        a, b, x = register(c, "A"), register(c, "B"), register(c, "X")
        befriend(c, a, b)
        qid = c.post("/api/quests", headers=auth(a), json={"peer_user_id": b["user_id"], "title": "Geheim"}).json()["id"]
        assert c.get("/api/quests", headers=auth(x)).json() == []
        assert c.patch(f"/api/quests/{qid}", headers=auth(x), json={"completed": True}).status_code == 404


def test_island_levels():
    import app.main as main
    assert [main.island_level(s) for s in (0, 39, 40, 119, 120, 299, 300, 9999)] == [1, 1, 2, 2, 3, 3, 4, 4]
