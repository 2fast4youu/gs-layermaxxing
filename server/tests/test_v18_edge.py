"""7.9.14-atelier: grid_version 7 = version 6 unchanged + the friends' look-out at the island's EDGE.

- The look-out (LIGHTHOUSE: palm, look-out, signal station, lighthouse) is newly placed or moved only where its v6 ground
  touches the edge: a meadow cell next to the coast (non-meadow connected to the open sea; an inland rock is no edge).
- geometry/island_ground.json (version 6) is unchanged; geometry/island_edge.json (tools/island_edge.py) pins the edge
  cells and the look-out's spots on an empty island per land x stage for server and app (IslandEdgeTest).
- Saved layouts are never moved or rewritten; an upgrade where the look-out stands is not checked against the edge.
- Every other building, the harbour's beach / pier contract and decoration keep rule 6 exactly.
The reference is computed here from the committed fixtures (never from main.py's helpers).
"""
import json
from pathlib import Path

from fastapi.testclient import TestClient
from test_v9_quests import load_app, register
from test_island_evolution import state
from test_v17_ground import GROUND, LATTICE, _ledger, _stored, fits, meadow, post, rect, zone, ellipse

APP = Path(__file__).resolve().parents[1] / "app"
EDGE = json.loads((APP / "geometry" / "island_edge.json").read_text())
TOOLS = Path(__file__).resolve().parents[1] / "tools"


def ref_edge(land):
    m = meadow(land)
    outside, todo = set(), [(i, j) for i in range(-1, 22) for j in range(-1, 22) if i in (-1, 21) or j in (-1, 21)]
    while todo:
        c = todo.pop()
        if c in outside or c in m or not (-1 <= c[0] <= 21 and -1 <= c[1] <= 21):
            continue
        outside.add(c)
        todo += [(c[0] + 1, c[1]), (c[0] - 1, c[1]), (c[0], c[1] + 1), (c[0], c[1] - 1)]
    return {c for c in m if any((c[0] + dx, c[1] + dy) in outside for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}


def lookout(stage, land):
    return {"buildings": {"LIGHTHOUSE": stage} if stage else {}, "starters": [] if stage else ["LIGHTHOUSE"], "land": land,
            "spent": 0, "revision": 1, "places_migrated": True, "positions": {}, "decor": []}


def test_the_server_publishes_version_7_and_the_ground_file_stays_version_6(tmp_path):
    m = load_app(tmp_path)
    assert m.GRID_VERSION == EDGE["version"] == 7 and GROUND["version"] == 6 and m.GROUND == GROUND
    with TestClient(m.app) as c:
        info = c.get("/api/server-info").json()
        a = register(c, "A")
        assert info["grid_version"] == state(c, a)["construction"]["grid_version"] == 7
        assert "build_grid_v7" in info["features"]


def test_the_edge_fixture_is_generated_from_the_committed_files():
    import importlib.util
    spec = importlib.util.spec_from_file_location("island_edge", TOOLS / "island_edge.py")
    tool = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(tool)
    assert tool.generate() == EDGE


def test_the_edge_is_the_coast_of_every_land_stage_never_an_inland_rock(tmp_path):
    m = load_app(tmp_path)
    for land in range(6):
        edge = {tuple(c) for c in EDGE["edge"][str(land)]}
        assert edge == ref_edge(land) == {tuple(c) for c in m.edge_cells(land)}, land
        assert edge <= meadow(land)
    # an inland pocket (non-meadow cells enclosed by meadow, not reaching the open sea) never makes an edge: a meadow
    # cell whose only non-meadow neighbours lie in such pockets is not an edge cell
    for land in range(6):
        m6 = meadow(land); edge = ref_edge(land)
        for c in m6:
            nb = [(c[0] + dx, c[1] + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))]
            if all(n in m6 for n in nb):
                assert c not in edge, (land, c)


def test_every_land_and_stage_has_edge_spots_and_none_of_them_is_inland():
    for land in range(6):
        edge = ref_edge(land)
        for stage in range(4):
            spots = {tuple(c) for c in EDGE["lighthouse_spots"][str(land)][str(stage)]}
            assert len(spots) >= 10, (land, stage, len(spots))
            key = "building:LIGHTHOUSE:%d" % stage
            ref = {c for c in LATTICE if fits(key, land, c) and rect(key, land, c) & edge}
            assert spots == ref, (land, stage)
            # the ground lies on meadow (rule 6) and touches the coast; crowns / roofs may reach over it (not checked)
            for s in spots:
                assert rect(key, land, s) <= meadow(land) and rect(key, land, s) & edge


def test_the_server_accepts_exactly_the_edge_spots_on_every_land_and_stage(tmp_path):
    m = load_app(tmp_path)
    for land in range(6):
        for stage in range(4):
            st = lookout(stage, land)
            spots = {tuple(c) for c in EDGE["lighthouse_spots"][str(land)][str(stage)]}
            server = {c for c in LATTICE if not m.building_spot_error(st, list(c), "LIGHTHOUSE") and not m.edge_error(st, list(c), "LIGHTHOUSE")}
            assert server == spots, (land, stage)
            # every other building keeps rule 6: inland meadow spots stay valid for the house
            house = dict(st, buildings={"HOUSE": max(1, stage)}, starters=[])
            assert any(not m.building_spot_error(house, list(c), "HOUSE") and not m.edge_error(house, list(c), "HOUSE")
                       and not rect("building:HOUSE:%d" % max(1, stage), land, c) & ref_edge(land) for c in LATTICE), (land, stage)


def test_build_move_and_a_moving_upgrade_follow_the_edge_rule_through_the_api(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a = register(c, "A")
        land = 5
        _ledger(m, a, {"buildings": {}, "starters": [], "land": land, "spent": 0, "revision": 1, "places_migrated": True, "positions": {}, "decor": []})
        cur = state(c, a, "creative")
        edge0 = {tuple(s) for s in EDGE["lighthouse_spots"][str(land)]["0"]}
        inland = next(s for s in LATTICE if fits("building:LIGHTHOUSE:0", land, s) and s not in edge0)
        r = post(c, a, cur, mode="creative", action="build", building="LIGHTHOUSE", position=list(inland))
        assert r.status_code == 422 and "Inselrand" in r.json()["detail"], r.text
        spot = sorted(edge0)[0]
        r = post(c, a, state(c, a, "creative"), mode="creative", action="build", building="LIGHTHOUSE", position=list(spot))
        assert r.status_code == 200, r.text
        # moving: inland refused, another edge spot accepted
        r2 = post(c, a, r.json(), mode="creative", action="move", building="LIGHTHOUSE", position=list(inland))
        assert r2.status_code == 422 and "Inselrand" in r2.json()["detail"]
        other = sorted(edge0)[-1]
        r3 = post(c, a, state(c, a, "creative"), mode="creative", action="move", building="LIGHTHOUSE", position=list(other))
        assert r3.status_code == 200, r3.text
        # an upgrade that names a new spot is a move: the next stage's edge spots only
        edge1 = {tuple(s) for s in EDGE["lighthouse_spots"][str(land)]["1"]}
        inland1 = next(s for s in LATTICE if fits("building:LIGHTHOUSE:1", land, s) and s not in edge1)
        r4 = post(c, a, r3.json(), mode="creative", action="upgrade", building="LIGHTHOUSE", position=list(inland1))
        assert r4.status_code == 422, r4.text
        to = sorted(edge1 - {other})[0]
        r5 = post(c, a, state(c, a, "creative"), mode="creative", action="upgrade", building="LIGHTHOUSE", position=list(to))
        assert r5.status_code == 200 and r5.json()["construction"]["buildings"]["LIGHTHOUSE"] == 1, r5.text


def test_an_inland_look_out_saved_before_is_kept_and_upgrades_where_it_stands(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a = register(c, "A")
        land = 5
        inland = next(s for s in LATTICE if fits("building:LIGHTHOUSE:2", land, s) and fits("building:LIGHTHOUSE:1", land, s)
                      and not rect("building:LIGHTHOUSE:2", land, s) & ref_edge(land))
        old = {"buildings": {"LIGHTHOUSE": 1, "HOUSE": 2}, "starters": [], "land": land, "spent": 40, "revision": 3, "places_migrated": True,
               "positions": {"LIGHTHOUSE": list(inland), "HOUSE": [6, 6]}, "decor": []}
        _ledger(m, a, old)
        cons = state(c, a, "creative")["construction"]
        assert cons["positions"] == old["positions"] and _stored(m, a)["positions"] == old["positions"]
        # an upgrade where it stands is not an edge move (nothing relocated, nothing refused for the new rule)
        r = post(c, a, state(c, a, "creative"), mode="creative", action="upgrade", building="LIGHTHOUSE")
        assert r.status_code == 200, r.text
        assert r.json()["construction"]["positions"]["LIGHTHOUSE"] == list(inland) and r.json()["construction"]["buildings"]["LIGHTHOUSE"] == 2
        # other pieces move freely inland as before
        r2 = post(c, a, r.json(), mode="creative", action="decor_place", item="bench", position=[10, 9])
        assert r2.status_code == 200, r2.text
        assert r2.json()["construction"]["positions"]["LIGHTHOUSE"] == list(inland)


def test_the_harbour_contract_is_unchanged_under_version_7(tmp_path):
    m = load_app(tmp_path)
    for land in (0, 5):
        for stage in range(4):
            st = {"buildings": {"HARBOUR": stage} if stage else {}, "starters": [] if stage else ["HARBOUR"], "land": land,
                  "spent": 0, "revision": 1, "places_migrated": True, "positions": {}, "decor": []}
            key = "building:HARBOUR:%d" % stage
            server = {c for c in LATTICE if not m.building_spot_error(st, list(c), "HARBOUR") and not m.edge_error(st, list(c), "HARBOUR")}
            assert server == {c for c in LATTICE if fits(key, land, c, harbour=True)}, (land, stage)
            assert server


def test_version_6_is_kept_exactly_when_pinned(tmp_path):
    m = load_app(tmp_path)
    m.GRID_VERSION = 6
    st = lookout(1, 5)
    inland = next(s for s in LATTICE if fits("building:LIGHTHOUSE:1", 5, s) and not rect("building:LIGHTHOUSE:1", 5, s) & ref_edge(5))
    assert m.edge_error(st, list(inland), "LIGHTHOUSE") is None and not m.building_spot_error(st, list(inland), "LIGHTHOUSE")
