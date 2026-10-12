"""Mein Leben als Insel (bis 7.9.6): life places on building plots. Since 7.9.7 the plots are gone and
the places are special decoration (test_v12_ausbau.py); old rows stay and the private figure still works."""

from test_v9_quests import auth, befriend, load_app, register
from fastapi.testclient import TestClient


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
        assert [d for d in isl["construction"]["decor"] if d["item"].startswith("place_")] == []


def test_plot_writes_are_gone_and_never_delete_old_places(tmp_path):
    main = load_app(tmp_path)
    with TestClient(main.app) as c:
        a = register(c, "A")
        with main.db() as conn:
            conn.execute("INSERT INTO island_places(user_id,plot,kind,name) VALUES (?,2,'desk','Coden am Berg')", (a["user_id"],))
        for body in ({"places": {}}, {"places": {"0": {"kind": "home", "name": "x"}}}):
            assert c.put("/api/island/places", headers=auth(a), json=body).status_code == 410
        assert own(c, a)["places"] == {"2": {"kind": "desk", "name": "Coden am Berg"}}
        assert [d["name"] for d in own(c, a)["construction"]["decor"] if d["item"] == "place_desk"] == ["Coden am Berg"]


def test_figure_is_private_and_follows_old_places(tmp_path):
    main = load_app(tmp_path)
    with TestClient(main.app) as c:
        a, b = register(c, "A"), register(c, "B")
        befriend(c, a, b)
        assert c.put("/api/island/here", headers=auth(a), json={"plot": 2, "status": "x"}).status_code == 422
        with main.db() as conn:
            conn.execute("INSERT INTO island_places(user_id,plot,kind,name) VALUES (?,0,'home','Zuhause')", (a["user_id"],))
        assert c.put("/api/island/here", headers=auth(a), json={"plot": 0, "status": "  im Zug  "}).status_code == 200
        assert own(c, a)["here"]["plot"] == 0 and own(c, a)["here"]["status"] == "im Zug"
        seen = c.get(f"/api/island/{a['user_id']}", headers=auth(b)).json()
        assert "here" not in seen
        assert seen["places"]["0"]["kind"] == "home"
