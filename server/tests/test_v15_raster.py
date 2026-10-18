"""7.9.11-raster: the placement rule is versioned and compact.

- The server validates exactly one rule (GRID_VERSION = 4) and publishes it in construction.grid_version and in the
  public /api/server-info, so the app never guesses (it keeps the exact rules 2 and 3 for servers that still run them).
- Version 4 footprints are compact: on the big land (2-5) every piece holds one real ground cell, on the small land
  (0-1) a function building / special decoration holds a diamond of one ring, stage-0 pieces and decoration one cell.
- Version 4 is never stricter than version 3 or 2: no saved island gets worse, nothing is moved or deleted.
- The set of target cells for a held piece (computed here independently from island_grid.json) is exactly what the
  API accepts, for every land stage and every kind of piece; the harbour only on free coast cells.
"""
import json
from pathlib import Path

from fastapi.testclient import TestClient
from test_v9_quests import auth, register
from test_v9_quests import load_app as _load_app
from test_island_evolution import state

GRID = json.loads((Path(__file__).resolve().parents[1] / "app" / "island_grid.json").read_text())

def load_app(tmp_path):
    """Historic 7.9.11 contract, kept exactly (7.9.12): these tests run the server's unchanged version-4 code path
    (GRID_VERSION = 4, FOOTPRINT_RINGS). The current rule, version 5 (graphic masks), is pinned in test_v16_masks."""
    m = _load_app(tmp_path)
    m.GRID_VERSION = 4
    return m

RINGS = GRID["footprints_v4"]


def post(c, a, st, **body):
    return c.post("/api/island/construction", headers=auth(a), json={"expected_revision": st["construction"]["revision"], **body})


def _ledger(m, a, st):
    with m.db() as conn:
        conn.execute("INSERT INTO island_construction(user_id,mode,state) VALUES (?,'normal',?) "
                     "ON CONFLICT(user_id,mode) DO UPDATE SET state=excluded.state", (a["user_id"], json.dumps(st)))
        conn.execute("UPDATE users SET creative_entitled=1 WHERE id=?", (a["user_id"],))


def _stored(m, a):
    with m.db() as conn:
        return json.loads(conn.execute("SELECT state FROM island_construction WHERE user_id=? AND mode='normal'", (a["user_id"],)).fetchone()[0])


# ------------------------------------------------------------------ an independent reference from the fixture

def diamond(spot, rings):
    x, y = spot
    return {(x + dx, y - rings + dy) for dx in range(-rings, rings + 1) for dy in range(-rings, rings + 1) if abs(dx) + abs(dy) <= rings}


def kind_of_building(st, b):
    return "starter" if b in st.get("starters", []) and not st.get("buildings", {}).get(b) else "big"


def kind_of_decor(d):
    return "big" if str(d["item"]).startswith("place_") else "decor"


def reference_targets(st, held):
    """Every cell where [held] may stand: grid (coast for the harbour) and no shared cell with any OTHER piece."""
    land = st["land"]
    cells = GRID["coast" if held == ("b", "HARBOUR") else "cells"][str(land)]
    others = []
    for b, p in st.get("positions", {}).items():
        if ("b", b) != held and (b in st.get("starters", []) or st.get("buildings", {}).get(b)):
            others.append(diamond(p, RINGS[kind_of_building(st, b)][land]))
    for d in st.get("decor", []):
        if ("d", d["id"]) != held:
            others.append(diamond(d["position"], RINGS[kind_of_decor(d)][land]))
    if held[0] == "b":
        kind = kind_of_building(st, held[1])
    else:
        kind = kind_of_decor(next(d for d in st["decor"] if d["id"] == held[1]))
    return {tuple(c) for c in cells if not any(diamond(c, RINGS[kind][land]) & o for o in others)}


# ------------------------------------------------------------------ contract

def test_the_effective_rule_is_published_identically_everywhere(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        info = c.get("/api/server-info").json()
        # Public and minimal: no accounts, paths or secrets, only the rule's number.
        assert set(info) == {"name", "role", "version", "warning", "features", "grid_version"}
        a = register(c, "A")
        con = state(c, a)["construction"]
        assert info["grid_version"] == con["grid_version"] == m.GRID_VERSION == 4
        assert "build_grid_v4" in info["features"] and "build_grid_v2" in info["features"]
        # The writes answer with the same number (the rule they were validated with).
        st = post(c, a, state(c, a), action="build", building="POST", position=[10, 10])
        assert st.status_code == 200 and st.json()["construction"]["grid_version"] == 4


def test_fixture_pins_version_4_and_keeps_version_3_and_2(tmp_path):
    m = load_app(tmp_path)
    assert GRID["footprints_v4"] == {k: list(v) for k, v in m.FOOTPRINT_RINGS.items()}
    assert GRID["footprints_v3"] == {k: list(v) for k, v in m.FOOTPRINT_RINGS_V3.items()} == \
        {"big": [2, 2, 1, 1, 1, 1], "starter": [1, 1, 1, 1, 1, 1], "decor": [1, 1, 1, 0, 0, 0]}
    assert GRID["legacy_v2"] == {"big": 3, "decor_building": 2.4, "decor_decor": 1.5, "land_factor": [m.land_factor(l) for l in range(6)]}


def test_version_4_footprints_are_compact(tmp_path):
    m = load_app(tmp_path)
    for land in range(6):
        for kind in ("big", "starter", "decor", "special"):
            n = len(m.footprint_cells([10, 10], m.rings_of(kind, land)))
            if land >= 2 or kind in ("starter", "decor"):
                assert n == 1, (land, kind)  # one real ground cell
            else:
                assert n == 5, (land, kind)  # at most one compact diamond on the small land


def test_version_4_is_never_stricter_than_version_3(tmp_path):
    """Every layout version 3 accepted stays valid (version 2 -> 3 is pinned in test_v14_placement)."""
    m = load_app(tmp_path)
    for land in range(6):
        for held in ("big", "starter", "special", "decor"):
            for other in ("big", "starter", "special", "decor"):
                r3 = lambda k: m.FOOTPRINT_RINGS_V3["big" if k == "special" else k][land]
                for dx in range(-6, 7):
                    for dy in range(-6, 7):
                        if diamond([10, 10], r3(held)) & diamond([10 + dx, 10 + dy], r3(other)):
                            continue
                        mine = m.footprint_cells([10, 10], m.rings_of(held, land))
                        theirs = m.footprint_cells([10 + dx, 10 + dy], m.rings_of(other, land))
                        assert not mine & theirs, (land, held, other, dx, dy)


def _populated(land):
    """Every kind of piece on the island: big buildings, stage-0 pieces, decoration, special decoration, legacy decoration."""
    st = {"buildings": {"HOUSE": 2, "LIBRARY": 1, "HARBOUR": 1}, "starters": ["POST", "CAMPFIRE"], "land": land, "spent": 0,
          "revision": 1, "places_migrated": True,
          "positions": {"HOUSE": [10, 6], "LIBRARY": [12, 12], "POST": [7, 9], "CAMPFIRE": [9, 12],
                        "HARBOUR": GRID["coast"][str(land)][len(GRID["coast"][str(land)]) // 2]},
          "decor": [{"id": 100, "item": "bench", "position": [11, 9]},
                    {"id": 101, "item": "place_hut", "position": [13, 8], "name": "Hütte"},
                    # an old item this app version has no sprite for: still a real obstacle (never dropped)
                    {"id": 102, "item": "gnome_legacy", "position": [8, 11]}]}
    for p in list(st["positions"].values()) + [d["position"] for d in st["decor"]]:
        assert m_grid(land, p), (land, p)
    return st


def m_grid(land, p):
    return list(p) in GRID["cells"][str(land)]


def test_target_cells_are_exactly_what_the_rule_accepts_on_every_land(tmp_path):
    m = load_app(tmp_path)
    for land in range(6):
        st = _populated(land)
        assert not m.overlapping_pairs(st), land
        for held in [("b", "POST"), ("b", "HOUSE"), ("b", "HARBOUR"), ("b", "CAMPFIRE"), ("d", 100), ("d", 101), ("d", 102)]:
            want = reference_targets(st, held)
            for i in range(-1, 22):
                for j in range(-1, 22):
                    if held[0] == "b":
                        ok = m.grid_buildable(i, j, land) and m.building_spot_error(st, [i, j], held[1]) is None
                    else:
                        d = next(x for x in st["decor"] if x["id"] == held[1])
                        ok = m.grid_buildable(i, j, land) and m.placement_blocked(
                            st, [i, j], "building" if m.is_special(d) else "decor", ignore_decor=held[1]) is None
                    assert ok == ((i, j) in want), (land, held, i, j)
            # never the empty set on this island, never every cell (obstacles really block)
            assert want and want != {tuple(c) for c in GRID["cells"][str(land)]}, (land, held)
        # the harbour's targets are free coast cells only, never inland, water or a taken coast cell
        harbour = reference_targets(st, ("b", "HARBOUR"))
        assert harbour <= {tuple(c) for c in GRID["coast"][str(land)]}


def test_the_api_accepts_every_target_and_refuses_every_other_cell(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        for land in (0, 5):
            a = register(c, "L%d" % land)
            _ledger(m, a, _populated(land))
            st = state(c, a, "creative")
            for held, body in [(("b", "POST"), dict(action="move", building="POST")),
                               (("b", "LIBRARY"), dict(action="move", building="LIBRARY")),
                               (("b", "HARBOUR"), dict(action="move", building="HARBOUR")),
                               (("d", 100), dict(action="decor_move", decor_id=100)),
                               (("d", 101), dict(action="decor_move", decor_id=101))]:
                before = st["construction"]
                want = reference_targets(before, held)
                for cell in m.grid_cells(land) + [[0, 0], [10, 20], [1, 10]]:
                    r = post(c, a, st, mode="creative", position=cell, **body)
                    assert (r.status_code == 200) == (tuple(cell) in want), (land, held, cell, r.text)
                    if r.status_code == 200:
                        st = r.json()
                        # move back: the obstacles for the next cell are exactly the same
                        back = before["positions"][held[1]] if held[0] == "b" else next(d for d in before["decor"] if d["id"] == held[1])["position"]
                        st = post(c, a, st, mode="creative", position=back, **body).json()
                    else:
                        assert r.status_code == 422
            # the legacy decoration was never touched by any of these writes
            assert any(d["item"] == "gnome_legacy" and d["position"] == [8, 11] for d in _stored(m, a)["decor"])


def test_starters_and_decoration_fit_densely_on_the_starter_island(tmp_path):
    """Version 4: six stage-0 pieces and decoration side by side on the smallest land, no hidden clearance."""
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a = register(c, "A")
        st = state(c, a)
        row = [[6 + i, 9] for i in range(6)]
        for b, p in zip(["HOUSE", "POST", "LIGHTHOUSE", "LIBRARY", "CAMPFIRE"], row):
            r = post(c, a, st, action="build", building=b, position=p)
            assert r.status_code == 200, (b, p, r.text)
            st = r.json()
        harbour = next(p for p in m.coast_cells(0) if not m.building_spot_error(st["construction"], p, "HARBOUR"))
        st = post(c, a, st, action="build", building="HARBOUR", position=harbour).json()
        assert sorted(st["construction"]["starters"]) == sorted(m.ISLAND_BUILDINGS)
        # the very cell of a piece stays refused
        assert post(c, a, st, action="move", building="POST", position=[6, 9]).status_code == 422


def test_the_own_old_spot_never_blocks_and_a_stale_revision_changes_nothing(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a = register(c, "A")
        _ledger(m, a, _populated(0))
        st = state(c, a, "creative")
        # one step: the big land-0 footprint overlaps only its own old one
        r = post(c, a, st, mode="creative", action="move", building="HOUSE", position=[10, 7])
        assert r.status_code == 200, r.text
        # a write against the old revision (another device changed the island) is a 409, nothing is stored
        before = _stored(m, a)
        stale = post(c, a, st, mode="creative", action="move", building="HOUSE", position=[10, 5])
        assert stale.status_code == 409
        assert _stored(m, a) == before


def test_legacy_unknown_decoration_is_kept_and_blocks_its_cell(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a = register(c, "A")
        _ledger(m, a, _populated(5))
        st = state(c, a, "creative")
        assert any(d["item"] == "gnome_legacy" for d in st["construction"]["decor"])
        r = post(c, a, st, mode="creative", action="move", building="POST", position=[8, 11])
        assert r.status_code == 422 and "Deko" in r.json()["detail"]
        st = post(c, a, st, mode="creative", action="move", building="POST", position=[8, 13]).json()
        assert any(d["item"] == "gnome_legacy" and d["position"] == [8, 11] for d in st["construction"]["decor"])
