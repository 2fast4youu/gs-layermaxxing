"""7.9.9-start: every function's stage 0 (hammock, letter tree, ...) is built for free, only the
upgrade from stage 0 to 1 and beyond costs; the glossary has four categories."""
import json
import sqlite3

from fastapi.testclient import TestClient
from test_v9_quests import auth, befriend, load_app, register
from test_island_evolution import earn, state


def post(c, a, st, **body):
    return c.post("/api/island/construction", headers=auth(a), json={"expected_revision": st["construction"]["revision"], **body})


SPOTS = {"HOUSE": [10, 6], "POST": [4, 9], "LIGHTHOUSE": [16, 9], "LIBRARY": [13, 14], "CAMPFIRE": [7, 14], "HARBOUR": [10, 15]}


def test_every_stage_zero_building_is_free_without_points_or_day_gate(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a = register(c, "A")
        st = state(c, a)
        assert st["construction"]["available"] == 0 and st["construction"]["age_days"] == 0
        assert st["construction"]["starter_cost"] == 0 and st["construction"]["starters"] == []
        # 7.9.12 (grid_version 5): the harbour first, its coast spot with open water in front is the scarce one.
        for b in ["HARBOUR"] + [x for x in m.ISLAND_BUILDINGS if x != "HARBOUR"]:
            con = st["construction"]
            # 7.9.10: the full spot rule (grid, harbour only on the coast, footprint overlap). 7.9.13 (version 6): the
            # harbour's rowboat lies on the beach of the cove, outside the meadow's build ellipse: every lattice cell is
            # a candidate, the rule alone decides.
            spot = next([i, j] for j in range(21) for i in range(21) if not m.building_spot_error(con, [i, j], b))
            r = post(c, a, st, action="build", building=b, position=spot)
            assert r.status_code == 200, (b, r.text)
            st = r.json()
        con = st["construction"]
        assert sorted(con["starters"]) == sorted(m.ISLAND_BUILDINGS)
        # Stage 0 is not a level: nothing upgraded, nothing spent, every piece has its spot.
        assert con["buildings"] == {} and con["spent"] == 0
        assert set(con["positions"]) == set(m.ISLAND_BUILDINGS)
        # A second free build of the same function is refused.
        assert post(c, a, st, action="build", building="HOUSE", position=[10, 11]).status_code == 422


def test_upgrade_from_stage_zero_costs_the_first_step_and_keeps_the_spot(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a, b = register(c, "A"), register(c, "B")
        # 7.9.12: a spot where the stage-1 picture fits as well ([4, 9] is the land-0 rim, see test_v16_masks)
        st = post(c, a, state(c, a), action="build", building="POST", position=[10, 11]).json()
        assert post(c, a, st, action="upgrade", building="POST").status_code == 403  # 25 P missing
        befriend(c, a, b)
        earn(c, a, b, 30)
        st = state(c, a)
        up = post(c, a, st, action="upgrade", building="POST")
        assert up.status_code == 200, up.text
        con = up.json()["construction"]
        assert con["buildings"]["POST"] == 1 and con["spent"] == 25
        assert con["starters"] == [] and con["positions"]["POST"] == [10, 11]


def test_stage_zero_pieces_block_the_grid_move_and_demolish_without_refund(tmp_path):
    m = load_app(tmp_path)
    m.GRID_VERSION = 4  # historic 7.9.11 rule, kept exactly; version 5: test_v16_masks
    with TestClient(m.app) as c:
        a = register(c, "A")
        st = post(c, a, state(c, a), action="build", building="HOUSE", position=[10, 6]).json()
        # 7.9.11 (version 4): a stage-0 piece holds exactly its own cell: the same cell is refused, the
        # neighbouring cell is free (version 3 refused [10, 7] as well, one ring each).
        assert post(c, a, st, action="build", building="POST", position=[10, 6]).status_code == 422
        assert m.footprint_cells([10, 6], m.FOOTPRINT_RINGS["starter"][0]) == {(10, 6)}
        assert m.building_spot_error({**st["construction"], "starters": ["HOUSE", "POST"]}, [10, 7], "POST") is None
        moved = post(c, a, st, action="move", building="HOUSE", position=[10, 11])
        assert moved.status_code == 200, moved.text
        st = moved.json()
        assert st["construction"]["positions"]["HOUSE"] == [10, 11]
        gone = post(c, a, st, action="demolish", building="HOUSE").json()
        assert gone["construction"]["starters"] == [] and "HOUSE" not in gone["construction"]["positions"]
        assert gone["construction"]["spent"] == 0


def test_old_ledgers_are_untouched_and_old_upgrade_from_nothing_still_costs(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a, b = register(c, "A"), register(c, "B")
        befriend(c, a, b)
        earn(c, a, b, 100)
        earn(c, a, b, 100)
        old = {"buildings": {"POST": 2}, "land": 1, "spent": 85, "revision": 4, "positions": {"POST": [10, 11]}}
        with m.db() as conn:
            conn.execute("INSERT INTO island_construction(user_id,mode,state) VALUES (?,'normal',?)", (a["user_id"], json.dumps(old)))
        st = state(c, a)
        con = st["construction"]
        assert con["buildings"] == {"POST": 2} and con["spent"] == 85 and con["starters"] == []
        # An old client still places a building straight at stage 1 for the first step's price (no refund, no change).
        # 7.9.12: the full spot rule for the stage-1 hut (version 5: its whole picture must fit)
        house = {**con, "buildings": {**con["buildings"], "HOUSE": 1}}
        spot = next(list(cell) for cell in m.grid_cells(con["land"]) if not m.building_spot_error(house, cell, "HOUSE"))
        up = post(c, a, st, action="upgrade", building="HOUSE", position=spot)
        assert up.status_code == 200, up.text
        up = up.json()["construction"]
        assert up["buildings"]["HOUSE"] == 1 and up["spent"] == 85 + 25 and up["buildings"]["POST"] == 2


def test_creative_downgrade_from_stage_one_leaves_the_stage_zero_piece(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a = register(c, "A")
        with m.db() as conn:
            conn.execute("UPDATE users SET creative_entitled=1 WHERE id=?", (a["user_id"],))
        st = state(c, a, "creative")
        st = post(c, a, st, mode="creative", action="upgrade", building="POST", position=[10, 11]).json()
        st = post(c, a, st, mode="creative", action="downgrade", building="POST").json()
        assert st["construction"]["buildings"].get("POST", 0) == 0 and st["construction"]["starters"] == ["POST"]
        st = post(c, a, st, mode="creative", action="downgrade", building="POST").json()
        assert st["construction"]["starters"] == []


def test_a_stage_zero_piece_that_grows_too_close_moves_to_a_free_cell(tmp_path):
    m = load_app(tmp_path)
    # Historic 7.9.10/7.9.11 rule (version-4 code path, kept exactly). Version 5 never relocates on an upgrade:
    # it refuses before paying and asks to move first (test_v16_masks).
    m.GRID_VERSION = 4
    with TestClient(m.app) as c:
        a = register(c, "A")
        with m.db() as conn:
            conn.execute("UPDATE users SET creative_entitled=1 WHERE id=?", (a["user_id"],))
        st = state(c, a, "creative")
        con = st["construction"]
        first = next(list(cell) for cell in m.grid_cells(0) if not m.placement_blocked(con, cell, "building"))
        st = post(c, a, st, mode="creative", action="build", building="HOUSE", position=first).json()
        st = post(c, a, st, mode="creative", action="upgrade", building="HOUSE").json()  # now a big stage-1 hut
        assert st["construction"]["positions"]["HOUSE"] == first
        con = st["construction"]
        # A cell where the small stage-0 footprint fits but the big stage-1 footprint overlaps the house (7.9.10).
        small = {**con, "starters": con["starters"] + ["POST"]}
        near = next(list(cell) for cell in m.grid_cells(0)
                    if m.placement_blocked(small, cell, "building", ignore_building="POST") is None
                    and m.placement_blocked(con, cell, "building", ignore_building="POST") is not None)
        st = post(c, a, st, mode="creative", action="build", building="POST", position=near)
        assert st.status_code == 200, st.text
        up = post(c, a, st.json(), mode="creative", action="upgrade", building="POST")
        assert up.status_code == 200, up.text
        con = up.json()["construction"]
        assert con["buildings"]["POST"] == 1 and con["positions"]["POST"] != near
        assert m.placement_blocked(con, con["positions"]["POST"], "building", ignore_building="POST") is None


# ---------------------------------------------------------------------------- glossary categories

def test_glossary_categories_default_validate_and_filter(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a = register(c, "A")
        assert c.get("/api/glossary/categories", headers=auth(a)).json() == ["Begriffe", "Witze", "Sprüche", "Zitate"]
        # Old clients send no category: "Begriffe".
        assert c.post("/api/glossary", headers=auth(a), json={"term": "Layer", "explanation": "Ebene"}).status_code == 201
        assert c.post("/api/glossary", headers=auth(a), json={"term": "Kalauer", "explanation": "Haha", "category": "Witze"}).status_code == 201
        assert c.post("/api/glossary", headers=auth(a), json={"term": "X", "explanation": "y", "category": "Rezepte"}).status_code == 422
        entries = {e["term"]: e["category"] for e in c.get("/api/glossary", headers=auth(a)).json()}
        assert entries == {"Layer": "Begriffe", "Kalauer": "Witze"}
        only = c.get("/api/glossary?category=Witze", headers=auth(a)).json()
        assert [e["term"] for e in only] == ["Kalauer"]
        assert c.get("/api/glossary?category=Rezepte", headers=auth(a)).status_code == 422
        # A merged explanation keeps the word's category.
        c.post("/api/glossary", headers=auth(a), json={"term": "kalauer", "explanation": "noch einer", "category": "Zitate"})
        assert {e["term"]: e["category"] for e in c.get("/api/glossary", headers=auth(a)).json()}["Kalauer"] == "Witze"


def test_existing_glossary_rows_migrate_to_begriffe_without_loss(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a = register(c, "A")
        c.post("/api/glossary", headers=auth(a), json={"term": "Alt", "explanation": "vorher da"})
    # A database from before 7.9.9: no category column at all.
    conn = sqlite3.connect(tmp_path / "test.db")
    cols = [r[1] for r in conn.execute("PRAGMA table_info(glossary_terms)")]
    if "category" in cols:
        conn.execute("ALTER TABLE glossary_terms DROP COLUMN category")
    conn.commit(); conn.close()
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        entries = c.get("/api/glossary", headers=auth(a)).json()
        assert [(e["term"], e["category"], e["explanations"][0]["text"]) for e in entries] == [("Alt", "Begriffe", "vorher da")]
