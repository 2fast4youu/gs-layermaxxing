"""Mein Leben als Insel: real places on building plots, a private figure."""

from test_v9_quests import auth, befriend, load_app, register
from fastapi.testclient import TestClient
from test_island_evolution import earn, state, action


def own(c, a):
    return c.get(f"/api/island/{a['user_id']}", headers=auth(a)).json()


def test_new_island_has_no_home_or_building_plots(tmp_path):
    main = load_app(tmp_path)
    with TestClient(main.app) as c:
        a = register(c, "A")
        isl = own(c, a)
        assert isl["places"] == {}
        assert isl["plots"] == 0
        assert isl["plot_unlocks"] == [0, 0, 0, 40, 80, 120, 200, 300, 450]
        assert {"hut", "uni", "desk", "station", "club", "bude", "city"} <= set(isl["place_kinds"])
        assert isl["here"] is None


def test_build_rename_and_land_grows_with_score(tmp_path):
    main = load_app(tmp_path)
    with TestClient(main.app) as c:
        a, b = register(c, "A"), register(c, "B")
        befriend(c, a, b)
        earn(c, a, b, 40)
        with main.db() as conn:
            conn.execute("UPDATE users SET created_at=? WHERE id=?", (main.now_ts()-3*86400, a["user_id"]))
        assert action(c,a,state(c,a),action="expand",building=None).status_code == 200
        r = c.put("/api/island/places", headers=auth(a), json={"places": {
            "0": {"kind": "bude", "name": "  Meine   Bude "}, "1": {"kind": "hut", "name": "Brennerhaus"},
            "2": {"kind": "desk", "name": "Coden am Berg"}}})
        assert r.status_code == 200, r.text
        assert own(c, a)["places"]["0"] == {"kind": "bude", "name": "Meine Bude"}
        # Plot 3 needs a second purchased land expansion.
        r = c.put("/api/island/places", headers=auth(a), json={"places": {"3": {"kind": "uni", "name": "JKU"}}})
        assert r.status_code == 403
        q = c.post("/api/quests", headers=auth(a), json={"peer_user_id": b["user_id"], "title": "Gipfel", "points": 80}).json()
        assert c.patch(f"/api/quests/{q['id']}", headers=auth(b), json={"completed": True}).status_code == 200
        assert own(c, a)["plots"] == 3
        assert action(c,a,state(c,a),action="expand",building=None).status_code == 200
        assert own(c, a)["plots"] == 5
        r = c.put("/api/island/places", headers=auth(a), json={"places": {"3": {"kind": "uni", "name": "JKU"}}})
        assert r.status_code == 200
        assert c.put("/api/island/places", headers=auth(a), json={"places": {"1": {"kind": "castle"}}}).status_code == 422
        assert c.put("/api/island/places", headers=auth(a), json={"places": {"9": {"kind": "uni"}}}).status_code == 422


def test_emptied_island_stays_empty(tmp_path):
    main = load_app(tmp_path)
    with TestClient(main.app) as c:
        a = register(c, "A")
        assert c.put("/api/island/places", headers=auth(a), json={"places": {}}).status_code == 200
        assert own(c, a)["places"] == {}


def test_figure_is_private_and_follows_places(tmp_path):
    main = load_app(tmp_path)
    with TestClient(main.app) as c:
        a, b = register(c, "A"), register(c, "B")
        befriend(c, a, b)
        assert c.put("/api/island/here", headers=auth(a), json={"plot": 2, "status": "x"}).status_code == 422
        earn(c,a,b,40)
        with main.db() as conn:
            conn.execute("UPDATE users SET created_at=? WHERE id=?", (main.now_ts()-86400, a["user_id"]))
        assert action(c,a,state(c,a),action="expand",building=None).status_code == 200
        assert c.put("/api/island/places", headers=auth(a), json={"places": {"0": {"kind": "home", "name": "Zuhause"}}}).status_code == 200
        assert c.put("/api/island/here", headers=auth(a), json={"plot": 0, "status": "  im Zug  "}).status_code == 200
        assert own(c, a)["here"]["plot"] == 0 and own(c, a)["here"]["status"] == "im Zug"
        seen = c.get(f"/api/island/{a['user_id']}", headers=auth(b)).json()
        assert "here" not in seen
        assert seen["places"]["0"]["kind"] == "home"
        # Tearing down the place sends the figure home (nowhere).
        c.put("/api/island/places", headers=auth(a), json={"places": {"1": {"kind": "hut", "name": "Brennerhaus"}}})
        assert own(c, a)["here"]["plot"] is None
