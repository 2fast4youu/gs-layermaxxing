"""7.9.7 Ausbau: one shared build grid per land stage, special decoration (former life places) max. 3."""
import json
from pathlib import Path

from fastapi.testclient import TestClient
from test_v9_quests import auth, befriend, load_app, register
from test_island_evolution import earn, state

GRID = json.loads((Path(__file__).resolve().parents[1] / "app" / "island_grid.json").read_text())


def post(c, a, st, **body):
    return c.post("/api/island/construction", headers=auth(a), json={"expected_revision": st["construction"]["revision"], **body})


def creative(m, c, a, land=0):
    with m.db() as conn:
        conn.execute("UPDATE users SET creative_entitled=1 WHERE id=?", (a["user_id"],))
    st = state(c, a, "creative")
    for _ in range(land):
        st = post(c, a, st, mode="creative", action="expand").json()
    return st


def test_build_grid_fixture_is_the_server_rule_and_grows_with_the_land(tmp_path):
    m = load_app(tmp_path)
    cells = {int(k): v for k, v in GRID["cells"].items()}
    assert sorted(cells) == [0, 1, 2, 3, 4, 5]
    for land in range(6):
        assert cells[land] == m.grid_cells(land)
        # Nothing on the border rows/columns (water), every default spot is buildable on every stage.
        assert all(0 < i < 20 and 0 < j < 20 for i, j in cells[land])
        assert all(m.grid_buildable(x, y, land) for x, y in m.ISLAND_DEFAULT_POSITIONS.values())
    # The edge margin is constant on screen: a small island loses a little more of its lattice.
    assert [len(cells[l]) for l in range(6)] == sorted(len(cells[l]) for l in range(6))
    assert set(map(tuple, cells[0])) < set(map(tuple, cells[5]))
    # 7.9.6 accepted coast/water spots: the old defaults of post and harbour were in the water.
    assert not m.grid_buildable(2, 9, 5) and not m.grid_buildable(10, 17, 5) and not m.grid_buildable(1, 10, 0)


def test_position_in_the_water_or_outside_the_land_stage_is_422_for_every_piece(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a = register(c, "A")
        st = creative(m, c, a, land=0)
        outer = next(p for p in m.grid_cells(5) if p not in m.grid_cells(0))  # land of stage 5, not of stage 0
        for p in ([1, 10], [10, 19], [2, 9], outer):
            assert post(c, a, st, mode="creative", action="upgrade", building="POST", position=p).status_code == 422, p
            assert post(c, a, st, mode="creative", action="decor_place", item="bench", position=p).status_code == 422, p
            assert post(c, a, st, mode="creative", action="decor_place", item="place_hut", position=p).status_code == 422, p
        st = post(c, a, st, mode="creative", action="upgrade", building="POST", position=[10, 10]).json()
        st = post(c, a, st, mode="creative", action="decor_place", item="bench", position=[10, 4]).json()
        bench = next(d for d in st["construction"]["decor"] if d["item"] == "bench")
        for p in ([1, 10], outer):
            assert post(c, a, st, mode="creative", action="move", building="POST", position=p).status_code == 422
            assert post(c, a, st, mode="creative", action="decor_move", decor_id=bench["id"], position=p).status_code == 422
        assert state(c, a)["construction"]["positions"]["POST"] == [10, 10]
        # The same outer cell is fine once the land has grown to stage 5.
        for _ in range(5):
            st = post(c, a, st, mode="creative", action="expand").json()
        assert post(c, a, st, mode="creative", action="move", building="POST", position=outer).status_code == 200


def test_special_decor_is_expensive_free_placed_and_limited_to_three(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a, b = register(c, "A"), register(c, "B")
        befriend(c, a, b)
        earn(c, a, b, 100)
        st = state(c, a)
        assert st["construction"]["special_limit"] == 3 and st["construction"]["special_costs"]["place_desk"] == 200
        # Normal mode spends points: 100 available, a 120-point place is refused.
        assert post(c, a, st, action="decor_place", item="place_home", position=[10, 9]).status_code == 403
        earn(c, a, b, 100)
        st = state(c, a)
        placed = post(c, a, st, action="decor_place", item="place_home", position=[10, 9], name="  Meine   Bude ").json()
        obj = next(d for d in placed["construction"]["decor"] if d["item"] == "place_home")
        assert obj["name"] == "Meine Bude" and obj["position"] == [10, 9]
        assert placed["construction"]["spent"] == 120 and placed["construction"]["available"] == 80
        # A special piece blocks like a building.
        assert post(c, a, placed, action="upgrade", building="POST", position=[11, 9]).status_code == 422
        # Creative: free, but the limit of three is global.
        st = creative(m, c, a, land=5)
        for item, p in (("place_hut", [5, 9]), ("place_uni", [15, 9])):
            r = post(c, a, st, mode="creative", action="decor_place", item=item, position=p)
            assert r.status_code == 200, r.text
            st = r.json()
        assert sum(1 for d in st["construction"]["decor"] if d["item"].startswith("place_")) == 3
        fourth = post(c, a, st, mode="creative", action="decor_place", item="place_desk", position=[10, 15])
        assert fourth.status_code == 422 and "3" in fourth.json()["detail"]
        # Ordinary decoration is not limited by the special rule.
        assert post(c, a, st, mode="creative", action="decor_place", item="bench", position=[10, 15]).status_code == 200
        assert state(c, a)["construction"]["spent"] == 120


def test_old_life_places_migrate_losslessly_and_more_than_three_stay(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a, b = register(c, "A"), register(c, "B")
        befriend(c, a, b)
        rows = [(0, "bude", "Meine Bude"), (1, "hut", "Brennerhaus"), (3, "desk", "Coden am Berg"), (4, "uni", "JKU")]
        with m.db() as conn:
            conn.executemany("INSERT INTO island_places(user_id,plot,kind,name) VALUES (?,?,?,?)", [(a["user_id"], *r) for r in rows])
            conn.execute("UPDATE users SET creative_entitled=1 WHERE id=?", (a["user_id"],))
        st = state(c, a)
        special = [d for d in st["construction"]["decor"] if d["item"].startswith("place_")]
        assert sorted((d["plot"], d["item"], d["name"]) for d in special) == [
            (0, "place_bude", "Meine Bude"), (1, "place_hut", "Brennerhaus"), (3, "place_desk", "Coden am Berg"), (4, "place_uni", "JKU")]
        # Every migrated piece stands on the current land (small land 0: old plots outside move inwards).
        assert all(m.grid_buildable(*d["position"], 0) for d in special)
        # Friends see the same pieces.
        assert c.get(f"/api/island/{a['user_id']}", headers=auth(b)).json()["construction"]["decor"] == st["construction"]["decor"]
        # Four old ones stay, but no fifth/fourth new one.
        st = state(c, a, "creative")
        assert post(c, a, st, mode="creative", action="decor_place", item="place_city", position=[10, 9]).status_code == 422
        # Persisted on the next write without duplicates; rows are never deleted.
        st = post(c, a, st, mode="creative", action="expand").json()
        assert sum(1 for d in st["construction"]["decor"] if d["item"].startswith("place_")) == 4
        with m.db() as conn:
            assert conn.execute("SELECT COUNT(*) FROM island_places WHERE user_id=?", (a["user_id"],)).fetchone()[0] == 4
        st = post(c, a, st, mode="creative", action="decor_remove", decor_id=special[0]["id"]).json()
        st = post(c, a, st, mode="creative", action="decor_remove", decor_id=special[1]["id"]).json()
        assert sum(1 for d in state(c, a)["construction"]["decor"] if d["item"].startswith("place_")) == 2
        assert post(c, a, st, mode="creative", action="decor_place", item="place_city", position=[10, 9]).status_code == 200


def test_old_coast_positions_move_onto_the_grid_without_losing_anything(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a = register(c, "A")
        old = {"buildings": {"POST": 2, "HARBOUR": 1, "HOUSE": 1}, "land": 5, "spent": 85, "revision": 7,
               "positions": {"POST": [2, 9], "HARBOUR": [10, 17]},
               "decor": [{"id": 100, "item": "bench", "position": [0, 0]}, {"id": 101, "item": "flowers", "position": [12, 9]}]}
        with m.db() as conn:
            conn.execute("INSERT INTO island_construction(user_id,mode,state) VALUES (?,'normal',?)", (a["user_id"], json.dumps(old)))
        cons = state(c, a)["construction"]
        assert cons["buildings"] == old["buildings"] and cons["spent"] == 85 and cons["revision"] == 7
        assert cons["positions"].get("HOUSE", [10, 6]) == [10, 6]  # the house stood on land: unchanged
        for p in list(cons["positions"].values()) + [d["position"] for d in cons["decor"]]:
            assert m.grid_buildable(p[0], p[1], 5), p
        assert [d["id"] for d in cons["decor"]] == [100, 101] and cons["decor"][1]["position"] == [12, 9]
        # Deterministic: reading twice gives the same layout.
        assert state(c, a)["construction"] == cons
