"""7.9.10 placement: ONE occupancy rule for app and server.

Every piece occupies the lattice cells of its footprint (a diamond of |di| + |dj| <= rings cells whose front
tip is its spot, where the sprite's foot stands; rings per land stage, pinned in island_grid.json). Two pieces
may not share a cell; there is no other clearance. The harbour additionally stands only on a coast cell (a buildable cell with a
non-buildable 4-neighbour)."""
import json
from pathlib import Path

from fastapi.testclient import TestClient
from test_v9_quests import auth, load_app, register
from test_island_evolution import state

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


def test_fixture_pins_coast_cells_and_footprints_of_every_land(tmp_path):
    m = load_app(tmp_path)
    assert GRID["version"] == 3
    for land in range(6):
        coast = GRID["coast"][str(land)]
        assert coast == m.coast_cells(land)
        cells = m.grid_cells(land)
        # Coast = the rim of the buildable grid: on land, next to a non-buildable cell; never empty, never everything.
        assert set(map(tuple, coast)) < set(map(tuple, cells)) and len(coast) > 20
        for x, y in coast:
            assert any(not m.grid_buildable(x + dx, y + dy, land) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    assert GRID["footprints"] == {k: list(v) for k, v in m.FOOTPRINT_RINGS.items()}
    # The footprint is a real set of grid cells: rings 0 = one cell, 1 = five, 2 = thirteen; the spot is its front tip.
    assert [len(m.footprint_cells([10, 10], r)) for r in (0, 1, 2)] == [1, 5, 13]
    assert m.footprint_cells([10, 10], 1) == {(10, 10), (9, 9), (10, 9), (11, 9), (10, 8)}
    assert max(y for _, y in m.footprint_cells([10, 10], 2)) == 10


def test_harbour_only_on_coast_cells_never_inland_or_outside(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a = register(c, "A")
        st = creative(m, c, a, land=5)
        coast = m.coast_cells(5)
        inland = [p for p in m.grid_cells(5) if p not in coast]
        # Building the harbour inland is refused with a clear reason; on the coast it is fine.
        r = post(c, a, st, mode="creative", action="build", building="HARBOUR", position=[10, 10])
        assert r.status_code == 422 and "Küste" in r.json()["detail"]
        st = post(c, a, st, mode="creative", action="build", building="HARBOUR", position=[10, 15]).json()
        assert st["construction"]["positions"]["HARBOUR"] == [10, 15]
        # Every inland cell is refused, outside the land too; every coast cell of the empty island is accepted.
        for p in inland[::7] + [[1, 10], [10, 19], [0, 0]]:
            r = post(c, a, st, mode="creative", action="move", building="HARBOUR", position=p)
            assert r.status_code == 422, p
        for p in coast:
            r = post(c, a, st, mode="creative", action="move", building="HARBOUR", position=p)
            assert r.status_code == 200, (p, r.text)
            st = r.json()
            assert st["construction"]["positions"]["HARBOUR"] == p
        # The coast rule is only for the harbour: the post office moves inland and to the coast.
        st = post(c, a, st, mode="creative", action="build", building="POST", position=[10, 9]).json()
        assert post(c, a, st, mode="creative", action="move", building="POST", position=[9, 9]).status_code == 200


def test_old_client_harbour_without_position_lands_on_the_coast(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a = register(c, "A")
        st = creative(m, c, a, land=3)
        up = post(c, a, st, mode="creative", action="upgrade", building="HARBOUR")
        assert up.status_code == 200, up.text
        assert m.grid_coast(*up.json()["construction"]["positions"]["HARBOUR"], 3)


def test_a_legacy_inland_harbour_stays_until_it_is_moved(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a = register(c, "A")
        old = {"buildings": {"HARBOUR": 2}, "land": 5, "spent": 60, "revision": 3, "positions": {"HARBOUR": [10, 11]}}
        with m.db() as conn:
            conn.execute("INSERT INTO island_construction(user_id,mode,state) VALUES (?,'normal',?)", (a["user_id"], json.dumps(old)))
            conn.execute("UPDATE users SET creative_entitled=1 WHERE id=?", (a["user_id"],))
        st = state(c, a, "creative")
        assert st["construction"]["positions"]["HARBOUR"] == [10, 11]  # never moved silently on read
        st = post(c, a, st, mode="creative", action="upgrade", building="HARBOUR").json()
        assert st["construction"]["positions"]["HARBOUR"] == [10, 11] and st["construction"]["buildings"]["HARBOUR"] == 3
        assert post(c, a, st, mode="creative", action="move", building="HARBOUR", position=[10, 12]).status_code == 422
        assert post(c, a, st, mode="creative", action="move", building="HARBOUR", position=[10, 15]).status_code == 200


def test_land_change_keeps_the_harbour_on_the_new_coast(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a = register(c, "A")
        st = creative(m, c, a, land=1)
        # A coast cell of land 1 that is inland on land 2 (the grid grows by one column there).
        spot = next(p for p in m.coast_cells(1) if p not in m.coast_cells(2))
        st = post(c, a, st, mode="creative", action="build", building="HARBOUR", position=spot).json()
        st = post(c, a, st, mode="creative", action="build", building="HOUSE", position=[10, 6]).json()
        st = post(c, a, st, mode="creative", action="expand").json()
        con = st["construction"]
        assert m.grid_coast(*con["positions"]["HARBOUR"], 2) and con["positions"]["HOUSE"] == [10, 6]
        assert m.placement_blocked(con, con["positions"]["HARBOUR"], "building", ignore_building="HARBOUR") is None


def test_free_neighbour_cells_have_no_hidden_clearance(tmp_path):
    """Right next to a footprint is free (touching), one cell further in is taken; on every land and for both kinds."""
    m = load_app(tmp_path)
    for land in range(6):
        big, decor = m.FOOTPRINT_RINGS["big"][land], m.FOOTPRINT_RINGS["decor"][land]
        st = {"land": land, "buildings": {"HOUSE": 1}, "positions": {"HOUSE": [10, 10]}, "decor": [], "starters": []}
        taken = m.footprint_cells([10, 10], big)
        for cell in m.grid_cells(land):
            # A decoration may stand on every cell whose own footprint shares no cell with the house.
            overlap = bool(m.footprint_cells(cell, decor) & taken)
            assert (m.placement_blocked(st, cell, "decor") is not None) == overlap, (land, cell)
            overlap = bool(m.footprint_cells(cell, big) & taken)
            assert (m.placement_blocked(st, cell, "building", ignore_building="POST") is not None) == overlap, (land, cell)
        # The closest free spots touch the house footprint (a cell of theirs is a 4-neighbour of it): no extra gap.
        def touches(cells):
            return any((x + dx, y + dy) in taken for x, y in cells for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        touching = [c for c in m.grid_cells(land) if not m.footprint_cells(c, decor) & taken and touches(m.footprint_cells(c, decor))]
        assert touching and all(m.placement_blocked(st, c, "decor") is None for c in touching), land


def test_new_rule_is_never_stricter_than_the_old_clearances(tmp_path):
    """Every layout the 7.9.9 distance rule accepted stays valid, so no saved island turns red."""
    m = load_app(tmp_path)

    def old_blocked(land, kind, small, other):
        f = m.land_factor(land)
        big, gap_b, gap_d = (3 / f) ** 2, (2.4 / f) ** 2, (1.5 / f) ** 2
        okind, ostarter = other
        if okind in ("building", "starter"):
            return gap_b if kind != "building" or small or ostarter else big
        if okind == "special":
            return (gap_b if small else big if kind == "building" else gap_b)
        return gap_b if kind == "building" else gap_d

    pieces = {"big": ("building", False), "starter": ("building", True), "special": ("special", False), "decor": ("decor", False)}
    for land in range(6):
        for held in ("big", "starter", "special", "decor"):
            for other in ("big", "starter", "special", "decor"):
                for dx in range(-8, 9):
                    for dy in range(-8, 9):
                        if (dx, dy) == (0, 0):
                            continue
                        kind = "decor" if held == "decor" else "building"
                        gap = old_blocked(land, kind, held == "starter", pieces[other])
                        if dx * dx + dy * dy >= gap:  # the old rule allowed it
                            mine = m.footprint_cells([10, 10], m.rings_of(held, land))
                            theirs = m.footprint_cells([10 + dx, 10 + dy], m.rings_of(other, land))
                            assert not mine & theirs, (land, held, other, dx, dy)


def test_foreign_overlap_is_refused_but_the_own_old_spot_never_blocks(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a = register(c, "A")
        st = creative(m, c, a, land=5)
        st = post(c, a, st, mode="creative", action="build", building="HOUSE", position=[10, 8]).json()
        st = post(c, a, st, mode="creative", action="upgrade", building="HOUSE").json()
        st = post(c, a, st, mode="creative", action="decor_place", item="bench", position=[13, 8]).json()
        bench = next(d for d in st["construction"]["decor"] if d["item"] == "bench")
        house = sorted(m.footprint_cells([10, 8], m.FOOTPRINT_RINGS["big"][5]))
        beside = (max(x for x, _ in house) + 1, 7)  # right of the widest row of the house footprint
        # A decoration on the house footprint is refused, right beside it is fine.
        for cell in house:
            assert post(c, a, st, mode="creative", action="decor_move", decor_id=bench["id"], position=list(cell)).status_code == 422, cell
        r = post(c, a, st, mode="creative", action="decor_move", decor_id=bench["id"], position=list(beside))
        assert r.status_code == 200, r.text
        st = r.json()
        # One step: the new footprint overlaps the house's own old one, which never counts as taken.
        r = post(c, a, st, mode="creative", action="move", building="HOUSE", position=[10, 9])
        assert r.status_code == 200, r.text
        st = r.json()
        # A building onto the bench is refused.
        r = post(c, a, st, mode="creative", action="move", building="HOUSE", position=[beside[0], beside[1] + 1])
        assert r.status_code == 422 and "belegt" in r.json()["detail"]


def _ledger(m, a, st):
    with m.db() as conn:
        conn.execute("INSERT INTO island_construction(user_id,mode,state) VALUES (?,'normal',?) "
                     "ON CONFLICT(user_id,mode) DO UPDATE SET state=excluded.state", (a["user_id"], json.dumps(st)))
        conn.execute("UPDATE users SET creative_entitled=1 WHERE id=?", (a["user_id"],))


def _stored(m, a):
    with m.db() as conn:
        return conn.execute("SELECT state FROM island_construction WHERE user_id=? AND mode='normal'", (a["user_id"],)).fetchone()[0]


def test_growing_stage_zero_piece_without_any_free_cell_is_refused_atomically(tmp_path):
    """nearest_free_cell never hands out an occupied cell: no free spot = 422, nothing stored."""
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a = register(c, "A")
        post_cells = m.footprint_cells([10, 10], m.FOOTPRINT_RINGS["starter"][0])
        # Decoration on every other grid cell: the small stage-0 post fits, its big stage 1 fits nowhere.
        decor = [{"id": 100 + i, "item": "flowers", "position": list(p)} for i, p in enumerate(m.grid_cells(0))
                 if not m.footprint_cells(p, m.FOOTPRINT_RINGS["decor"][0]) & post_cells]
        _ledger(m, a, {"buildings": {}, "starters": ["POST"], "land": 0, "spent": 0, "revision": 5,
                       "positions": {"POST": [10, 10]}, "decor": decor, "places_migrated": True})
        before = _stored(m, a)
        st = state(c, a, "creative")
        r = post(c, a, st, mode="creative", action="upgrade", building="POST")
        assert r.status_code == 422 and "frei" in r.json()["detail"], r.text
        assert _stored(m, a) == before
        assert state(c, a, "creative")["construction"]["revision"] == 5


def test_resize_with_a_fully_blocked_coast_is_refused_atomically(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a = register(c, "A")
        # Land 2, harbour on a coast cell that is not land on stage 1; every coast cell of stage 1 holds decoration.
        spot = next(p for p in m.coast_cells(2) if not m.grid_buildable(p[0], p[1], 1))
        decor = [{"id": 100 + i, "item": "flowers", "position": p} for i, p in enumerate(m.coast_cells(1))]
        _ledger(m, a, {"buildings": {"HARBOUR": 1}, "land": 2, "spent": 0, "revision": 3,
                       "positions": {"HARBOUR": spot}, "decor": decor, "places_migrated": True})
        before = _stored(m, a)
        st = state(c, a, "creative")
        r = post(c, a, st, mode="creative", action="shrink")
        assert r.status_code == 422 and "frei" in r.json()["detail"], r.text
        assert _stored(m, a) == before


def test_shrink_never_creates_a_decoration_collision(tmp_path):
    """Footprints grow when the land shrinks: decoration that would then overlap blocks the shrink."""
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a = register(c, "A")
        # land 3: decoration has 0 rings, on land 2 it has 1 ring -> two neighbours collide there
        decor = [{"id": 100, "item": "bench", "position": [10, 10]}, {"id": 101, "item": "lantern", "position": [11, 10]}]
        assert m.FOOTPRINT_RINGS["decor"][3] == 0 and m.FOOTPRINT_RINGS["decor"][2] == 1
        _ledger(m, a, {"buildings": {}, "land": 3, "spent": 0, "revision": 2, "decor": decor, "places_migrated": True})
        before = _stored(m, a)
        st = state(c, a, "creative")
        r = post(c, a, st, mode="creative", action="shrink")
        assert r.status_code == 422, r.text
        assert _stored(m, a) == before
        # once one of them stands apart, the shrink is fine and the result is collision-free
        st = post(c, a, st, mode="creative", action="decor_move", decor_id=101, position=[13, 10]).json()
        r = post(c, a, st, mode="creative", action="shrink")
        assert r.status_code == 200, r.text
        con = r.json()["construction"]
        prints = [m.decor_footprint(con, d) for d in con["decor"]]
        assert not any(p & q for i, p in enumerate(prints) for q in prints[i + 1:])


def test_reading_a_legacy_island_without_free_cells_keeps_it_unchanged(tmp_path):
    """Normalisation on read keeps its legacy job (move off-grid pieces to free cells) but never onto taken ones."""
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a = register(c, "A")
        decor = [{"id": 100 + i, "item": "flowers", "position": p} for i, p in enumerate(m.grid_cells(0))]
        _ledger(m, a, {"buildings": {"POST": 1}, "land": 0, "spent": 0, "revision": 1,
                       "positions": {"POST": [2, 9]}, "decor": decor, "places_migrated": True})
        con = state(c, a)["construction"]
        assert con["positions"]["POST"] == [2, 9]  # nowhere free: stays where it was, no new overlap
