"""7.9.13-kueste: grid_version 6, a piece occupies only its GROUND rectangle (like Clash of Clans).

- geometry/island_ground.json (server/tools/island_ground.py, from the painted sprites and the painted terrain) holds
  for every building x stage x land stage and every decoration item one ground rectangle, for the harbour's pier (stage
  1..3) its head over the water, and the zones of the terrain (L land, B beach, S shore, R rock, W water).
- A normal piece's whole ground lies on meadow inside the land stage's build ellipse; the harbour's rowboat on the
  beach; a pier with its root on the beach, its head and its berth over open water. Only grounds may not share a cell:
  roofs, crowns and towers may cover other pieces on screen.
- Saved layouts are never moved, deleted or reinterpreted; an upgrade is checked with the next stage's ground before
  anything is paid; the piece being moved never blocks itself.
The reference below is computed independently from the fixtures (never from main.py's helpers).
"""
import json
from collections import deque
from pathlib import Path

from fastapi.testclient import TestClient
from test_v9_quests import auth, befriend, register
from test_v9_quests import load_app as _load_app
from test_island_evolution import earn, state


def load_app(tmp_path):
    """7.9.14: the current server runs version 7 (version 6 + the look-out's edge rule, test_v18_edge); this file pins
    version 6 exactly, like the historic files pin 4 and 5, and runs it unchanged."""
    m = _load_app(tmp_path)
    m.GRID_VERSION = 6
    return m

APP = Path(__file__).resolve().parents[1] / "app"
GRID = json.loads((APP / "island_grid.json").read_text())
GROUND = json.loads((APP / "geometry" / "island_ground.json").read_text())
MASKS = json.loads((APP / "geometry" / "island_masks.json").read_text())
BUILDINGS = ("HOUSE", "POST", "LIGHTHOUSE", "LIBRARY", "CAMPFIRE", "HARBOUR")
ITEMS = ("flowers", "bench", "lantern", "flag", "palm", "campfire", "hammock", "fountain", "windmill", "maibaum")
SPECIAL = ("place_home", "place_bude", "place_uni", "place_hut", "place_club", "place_station", "place_city", "place_desk")
LATTICE = [(i, j) for j in range(21) for i in range(21)]


def post(c, a, st, **body):
    return c.post("/api/island/construction", headers=auth(a), json={"expected_revision": st["construction"]["revision"], **body})


def _ledger(m, a, st, creative=True):
    with m.db() as conn:
        conn.execute("INSERT INTO island_construction(user_id,mode,state) VALUES (?,'normal',?) "
                     "ON CONFLICT(user_id,mode) DO UPDATE SET state=excluded.state", (a["user_id"], json.dumps(st)))
        if creative:
            conn.execute("UPDATE users SET creative_entitled=1 WHERE id=?", (a["user_id"],))


def _stored(m, a):
    with m.db() as conn:
        return json.loads(conn.execute("SELECT state FROM island_construction WHERE user_id=? AND mode='normal'", (a["user_id"],)).fetchone()[0])


# ------------------------------------------------------------------ independent reference from the fixtures

def zone(i, j):
    return GROUND["zones"][j][i] if 0 <= i <= 20 and 0 <= j <= 20 else "W"


def ellipse(land):
    return {tuple(c) for c in GRID["cells"][str(land)]}


def meadow(land):
    return {c for c in ellipse(land) if zone(*c) == "L"}


def key_building(st, b):
    return "building:%s:%d" % (b, st.get("buildings", {}).get(b, 0))


def key_decor(item):
    if "decor:" + item in GROUND["pieces"]:
        return "decor:" + item
    return "decor:?special" if item.startswith("place_") else "decor:?"


def rect(key, land, spot, part="ground"):
    r = GROUND["pieces"][key]["lands"][land].get(part)
    if not r:
        return set()
    return {(spot[0] + i, spot[1] + j) for i in range(r[0], r[2] + 1) for j in range(r[1], r[3] + 1)}


def occupied(key, land, spot):
    return rect(key, land, spot) | rect(key, land, spot, "sea")


def fits(key, land, spot, harbour=False):
    if not (0 <= spot[0] <= 20 and 0 <= spot[1] <= 20):
        return False
    ground, sea = rect(key, land, spot), rect(key, land, spot, "sea")
    if harbour:
        berth = (spot[0] + GROUND["berth"][0], spot[1] + GROUND["berth"][1])
        return all(zone(*c) == "B" for c in ground) and (not sea or (all(zone(*c) == "W" for c in sea) and zone(*berth) == "W"))
    return ground <= meadow(land)


def others(st, held):
    out = []
    for b in BUILDINGS:
        if ("b", b) != held and (st.get("buildings", {}).get(b) or b in st.get("starters", [])):
            out.append(occupied(key_building(st, b), st["land"], st["positions"][b]))
    for d in st.get("decor", []):
        if ("d", d["id"]) != held:
            out.append(occupied(key_decor(d["item"]), st["land"], d["position"]))
    return out


def reference_targets(st, held, key):
    land = st["land"]
    rest = others(st, held)
    return {c for c in LATTICE if fits(key, land, c, held == ("b", "HARBOUR")) and not any(occupied(key, land, c) & o for o in rest)}


def populated(land):
    """Every kind of piece (big buildings, stage-0 pieces, the harbour's pier, decoration, special and unknown legacy
    decoration), packed by a greedy scan from the fixtures alone so that no two grounds share a cell."""
    st = {"buildings": {"HOUSE": 2, "LIBRARY": 1, "HARBOUR": 1}, "starters": ["POST", "CAMPFIRE"], "land": land, "spent": 0,
          "revision": 1, "places_migrated": True, "positions": {}, "decor": []}
    order = [("b", "HARBOUR"), ("b", "HOUSE"), ("b", "LIBRARY"), ("d", (101, "place_hut")), ("b", "POST"), ("b", "CAMPFIRE"),
             ("d", (100, "bench")), ("d", (102, "gnome_legacy"))]
    taken = set()
    for kind, what in order:
        key = key_building(st, what) if kind == "b" else key_decor(what[1])
        harbour = what == "HARBOUR"
        spot = next(c for c in LATTICE if fits(key, land, c, harbour) and not occupied(key, land, c) & taken)
        taken |= occupied(key, land, spot)
        if kind == "b":
            st["positions"][what] = list(spot)
        else:
            st["decor"].append({"id": what[0], "item": what[1], "position": list(spot), **({"name": "Hütte"} if what[0] == 101 else {})})
    return st


# ------------------------------------------------------------------ the contract

def test_the_server_publishes_version_6_and_reads_the_one_ground_file(tmp_path):
    m = load_app(tmp_path)
    assert m.GRID_VERSION == GROUND["version"] == 6
    assert MASKS["version"] == 5  # version 5 stays as history, untouched
    assert m.GROUND == GROUND and m.MASKS == MASKS
    with TestClient(m.app) as c:
        info = c.get("/api/server-info").json()
        a = register(c, "A")
        assert info["grid_version"] == state(c, a)["construction"]["grid_version"] == 6
        assert {"build_grid_v5", "build_grid_v6"} <= set(info["features"])


def test_every_building_stage_land_and_item_has_a_ground_rectangle_at_the_drawn_size():
    keys = {"building:%s:%d" % (b, s) for b in BUILDINGS for s in range(4)} | {"decor:" + i for i in ITEMS + SPECIAL} | \
        {"decor:?", "decor:?special"}
    assert keys <= set(GROUND["pieces"])
    assert set(GROUND["pieces"]) == set(MASKS["pieces"])
    for key, piece in GROUND["pieces"].items():
        assert len(piece["lands"]) == 6
        for land, e in enumerate(piece["lands"]):
            # the very sprite size of version 5: nothing is drawn bigger, smaller or elsewhere
            assert e["size"] == MASKS["pieces"][key]["lands"][land]["size"], (key, land)
            x0, y0, x1, y1 = e["ground"]
            assert x0 <= 0 <= x1 and y0 <= y1, (key, land)
            if key.startswith("building:HARBOUR:") and not key.endswith(":0"):
                # pier: root on the beach right behind the head, the head over the water in the spot row
                assert y1 == -1 and e["sea"] == [x0, 0, x1, 0], (key, land)
            else:
                assert y1 == 0 and "sea" not in e, (key, land)


def test_a_ground_is_a_plate_not_the_whole_picture_and_never_one_cell_for_everything():
    for b in ("HOUSE", "POST", "LIGHTHOUSE", "LIBRARY", "CAMPFIRE"):
        for stage in (1, 2, 3):
            for land in range(6):
                key = "building:%s:%d" % (b, stage)
                ground = rect(key, land, (10, 10))
                picture = {(10 + dx, 10 + dy) for dx, dy in MASKS["pieces"][key]["lands"][land]["mask"]}
                # a real building stands on more than one cell, but its roof / tower / crown are not ground; the only
                # exception is measured, not assumed: the wooden look-out (LIGHTHOUSE 1) stands on its legs in one cell
                assert len(ground) >= (1 if key == "building:LIGHTHOUSE:1" else 2), (key, land)
                assert len(ground) < len(picture), (key, land)
                # the plate lies at the foot: nothing of it above the drawn picture's lowest rows
                assert min(y for _, y in ground) >= min(y for _, y in picture), (key, land)
    # a lighthouse tower is tall and narrow: its ground is a small share of its picture
    assert len(rect("building:LIGHTHOUSE:3", 5, (10, 10))) * 2 < len({tuple(c) for c in MASKS["pieces"]["building:LIGHTHOUSE:3"]["lands"][5]["mask"]})


def test_zones_come_from_the_painted_terrain_with_one_real_beach():
    rows = GROUND["zones"]
    assert len(rows) == 21 and all(len(r) == 21 and set(r) <= set("LBSRW") for r in rows)
    beach = {c for c in LATTICE if zone(*c) == "B"}
    assert len(beach) >= 6
    # one connected beach, reaching the open water
    seen, todo = set(), deque([min(beach)])
    while todo:
        c = todo.popleft()
        if c in seen:
            continue
        seen.add(c)
        todo.extend(n for n in ((c[0] + 1, c[1]), (c[0] - 1, c[1]), (c[0], c[1] + 1), (c[0], c[1] - 1)) if n in beach)
    assert seen == beach
    assert any(zone(i, j + 1) == "W" for i, j in beach)
    for land in range(6):
        # the beach is no meadow: nothing but the harbour stands there; rocks are never buildable
        assert not beach & meadow(land)
        assert all(zone(*c) != "R" for c in meadow(land))
        # the meadow is most of the build ellipse on every land stage
        assert len(meadow(land)) > .8 * len(ellipse(land))


def test_every_harbour_stage_has_a_spot_and_the_small_island_holds_all_six_starters():
    for land in range(6):
        for stage in range(4):
            key = "building:HARBOUR:%d" % stage
            spots = [c for c in LATTICE if fits(key, land, c, harbour=True)]
            assert spots, (land, stage)
            for s in spots:
                ground = rect(key, land, s)
                assert all(zone(*c) == "B" for c in ground)
                if stage:
                    assert all(zone(*c) == "W" for c in rect(key, land, s, "sea"))
        # all six free stage-0 pieces fit on the land at once (greedy, grounds disjoint)
        taken = set()
        for b in ("HARBOUR",) + tuple(x for x in BUILDINGS if x != "HARBOUR"):
            key = "building:%s:0" % b
            spot = next((c for c in LATTICE if fits(key, land, c, b == "HARBOUR") and not occupied(key, land, c) & taken), None)
            assert spot is not None, (land, b)
            taken |= occupied(key, land, spot)


def test_target_cells_are_exactly_what_the_rule_accepts(tmp_path):
    m = load_app(tmp_path)
    for land in (0, 3, 5):
        st = populated(land)
        held_all = [("b", b) for b in BUILDINGS] + [("d", d["id"]) for d in st["decor"]] + [("d", None)]
        for held in held_all:
            if held[0] == "b":
                key = key_building(st, held[1])
                server = {c for c in LATTICE if not m.building_spot_error(st, list(c), held[1])}
            elif held[1] is None:
                key = key_decor("lantern")
                server = {c for c in LATTICE if not m.decor_blocked(None, 0, st, list(c), item="lantern")}
            else:
                d = next(d for d in st["decor"] if d["id"] == held[1])
                key = key_decor(d["item"])
                server = {c for c in LATTICE if not m.decor_blocked(None, 0, st, list(c), ignore_id=d["id"], special=d["item"].startswith("place_"))}
            assert server == reference_targets(st, held, key), (land, held)


def test_the_api_accepts_every_target_and_refuses_every_other_cell(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a = register(c, "A")
        st = populated(0)
        _ledger(m, a, st)
        cur = state(c, a, "creative")
        for b in ("HARBOUR", "HOUSE"):
            targets = reference_targets(st, ("b", b), key_building(st, b))
            assert targets
            for cell in LATTICE:
                r = post(c, a, cur, mode="creative", action="move", building=b, position=list(cell))
                assert (r.status_code == 200) == (cell in targets), (b, cell, r.text)
                if r.status_code == 200:
                    _ledger(m, a, st)  # back to the populated layout for the next cell
                    cur = state(c, a, "creative")


def test_overlapping_roofs_with_separate_grounds_may_stand_but_one_shared_ground_cell_may_not(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a = register(c, "A")
        land = 5
        st = {"buildings": {"LIGHTHOUSE": 3, "HOUSE": 3}, "land": land, "spent": 0, "revision": 1, "places_migrated": True,
              "positions": {"LIGHTHOUSE": [10, 8], "HOUSE": [10, 11]}, "decor": []}
        _ledger(m, a, st)
        cur = state(c, a, "creative")
        # the nearest spot where the PICTURES overlap (the version-5 masks of the two sprites share cells, i.e. one is
        # drawn over the other) but the grounds are disjoint
        tower = occupied("building:LIGHTHOUSE:3", land, (10, 8))
        tower_picture = {(10 + dx, 8 + dy) for dx, dy in MASKS["pieces"]["building:LIGHTHOUSE:3"]["lands"][land]["mask"]}
        house_key = "building:HOUSE:3"
        spot = next(cc for cc in sorted(meadow(land), key=lambda p: (abs(p[0] - 10) + abs(p[1] - 8), p))
                    if fits(house_key, land, cc) and not occupied(house_key, land, cc) & tower and
                    {(cc[0] + dx, cc[1] + dy) for dx, dy in MASKS["pieces"][house_key]["lands"][land]["mask"]} & tower_picture)
        r = post(c, a, cur, mode="creative", action="move", building="HOUSE", position=list(spot))
        assert r.status_code == 200, (spot, r.text)
        assert r.json()["construction"]["positions"]["HOUSE"] == list(spot)
        # one step closer, so that a ground cell is shared: refused, nothing stored
        cur = r.json()
        closer = next(cc for cc in sorted(meadow(land), key=lambda p: (abs(p[0] - spot[0]) + abs(p[1] - spot[1]), p))
                      if fits(house_key, land, cc) and occupied(house_key, land, cc) & tower)
        bad = post(c, a, cur, mode="creative", action="move", building="HOUSE", position=list(closer))
        assert bad.status_code == 422 and "belegt" in bad.json()["detail"]
        assert _stored(m, a)["positions"]["HOUSE"] == list(spot)


def test_normal_pieces_never_stand_on_the_beach_and_the_harbour_only_there(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a = register(c, "A")
        st = {"buildings": {}, "starters": ["HARBOUR", "HOUSE"], "land": 3, "spent": 0, "revision": 1, "places_migrated": True,
              "positions": {"HARBOUR": None, "HOUSE": [10, 9]}, "decor": []}
        st["positions"]["HARBOUR"] = list(next(cc for cc in LATTICE if fits("building:HARBOUR:0", 3, cc, True)))
        _ledger(m, a, st)
        cur = state(c, a, "creative")
        beach = sorted(cc for cc in LATTICE if zone(*cc) == "B")
        for cc in beach:
            r = post(c, a, cur, mode="creative", action="move", building="HOUSE", position=list(cc))
            assert r.status_code == 422, cc
            r = post(c, a, cur, mode="creative", action="decor_place", item="bench", position=list(cc))
            assert r.status_code == 422, cc
        assert "Strand" in post(c, a, cur, mode="creative", action="move", building="HOUSE", position=list(beach[0])).json()["detail"]
        # the rowboat never lies on the meadow
        r = post(c, a, cur, mode="creative", action="move", building="HARBOUR", position=[10, 9])
        assert r.status_code == 422 and "Strand" in r.json()["detail"]


def test_a_pier_needs_its_root_on_the_beach_and_open_water_without_rocks_in_front(tmp_path):
    m = load_app(tmp_path)
    for land in range(6):
        key = "building:HARBOUR:2"
        st = {"buildings": {"HARBOUR": 2}, "land": land, "spent": 0, "revision": 1, "places_migrated": True,
              "positions": {"HARBOUR": [0, 0]}, "decor": []}
        good = [cc for cc in LATTICE if fits(key, land, cc, True)]
        assert good
        for cc in LATTICE:
            reason = m.building_spot_error(st, list(cc), "HARBOUR")
            assert (reason is None) == (cc in good), (land, cc, reason)
            sea = rect(key, land, cc, "sea")
            if any(zone(*s) == "R" for s in sea):
                assert reason is not None
        # a pier head on the beach (no water in front) is refused with the pier's reason
        beach_head = next(cc for cc in LATTICE if zone(*cc) == "B")
        assert m.building_spot_error(st, list(beach_head), "HARBOUR")


def test_an_old_layout_is_kept_exactly_and_shown_where_it_may_go(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a = register(c, "A")
        # 7.9.12 data: the harbour on the old grass coast, a house and a bench on build-grid cells that are beach now,
        # the house's and the post office's grounds overlapping
        old = {"buildings": {"HARBOUR": 1, "HOUSE": 2, "POST": 1}, "land": 4, "spent": 85, "revision": 7, "places_migrated": True,
               "positions": {"HARBOUR": [12, 13], "HOUSE": [10, 15], "POST": [10, 14]},
               "decor": [{"id": 100, "item": "bench", "position": [11, 15]}]}
        assert zone(10, 15) == zone(11, 15) == "B" and (10, 15) in ellipse(4) and (11, 15) in ellipse(4)
        assert occupied("building:HOUSE:2", 4, (10, 15)) & occupied("building:POST:1", 4, (10, 14))
        _ledger(m, a, old)
        cons = state(c, a, "creative")["construction"]
        assert cons["positions"] == old["positions"] and cons["decor"] == old["decor"]
        assert cons["buildings"] == old["buildings"] and cons["spent"] == 85
        assert ("b:HOUSE", "b:POST") in m.overlapping_pairs(_stored(m, a))  # shown, never moved on its own
        assert _stored(m, a)["positions"] == old["positions"]
        # moving the old harbour offers exactly the real pier spots; it may leave its old place
        st = dict(old, starters=[])
        targets = reference_targets(st, ("b", "HARBOUR"), key_building(st, "HARBOUR"))
        assert targets and (12, 13) not in targets
        to = sorted(targets)[0]
        r = post(c, a, state(c, a, "creative"), mode="creative", action="move", building="HARBOUR", position=list(to))
        assert r.status_code == 200, r.text
        # an unrelated move keeps the old overlap (never worse), nothing else moves
        r2 = post(c, a, r.json(), mode="creative", action="decor_move", decor_id=100, position=[6, 9])
        assert r2.status_code == 200, r2.text
        assert r2.json()["construction"]["positions"]["HOUSE"] == [10, 15] and r2.json()["construction"]["positions"]["POST"] == [10, 14]
        # expanding the land never moves the harbour (beach and water do not move with the land stage)
        r3 = post(c, a, r2.json(), mode="creative", action="expand")
        assert r3.status_code == 200 and r3.json()["construction"]["positions"]["HARBOUR"] == list(to)


def test_an_upgrade_checks_the_next_ground_before_paying_and_the_rowboat_becomes_a_pier_at_the_water(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a, b = register(c, "A"), register(c, "B")
        befriend(c, a, b)
        earn(c, a, b, 100)
        land = 0
        boat = next(cc for cc in LATTICE if fits("building:HARBOUR:0", land, cc, True))
        st = {"buildings": {}, "starters": ["HARBOUR"], "land": land, "spent": 0, "revision": 1, "places_migrated": True,
              "positions": {"HARBOUR": list(boat)}, "decor": []}
        _ledger(m, a, st, creative=False)
        cur = state(c, a)
        before = _stored(m, a)
        # on its beach spot the pier head would stand on sand: refused before anything is paid
        r = post(c, a, cur, action="upgrade", building="HARBOUR")
        assert r.status_code == 422 and "verschieben" in r.json()["detail"]
        assert _stored(m, a) == before
        # the owner picks the pier spot at the water: one atomic write, paid once, nothing else changes
        pier = sorted(cc for cc in LATTICE if fits("building:HARBOUR:1", land, cc, True))[0]
        r = post(c, a, cur, action="upgrade", building="HARBOUR", position=list(pier))
        assert r.status_code == 200, r.text
        con = r.json()["construction"]
        assert con["buildings"]["HARBOUR"] == 1 and con["positions"]["HARBOUR"] == list(pier) and con["spent"] == 25
        # a building whose next stage's ground would cover a neighbour's ground is refused before paying
        st = {"buildings": {"POST": 1}, "starters": [], "land": 0, "spent": 0, "revision": 3, "places_migrated": True,
              "positions": {"POST": [10, 9]}, "decor": []}
        nxt = rect("building:POST:2", 0, (10, 9)) - rect("building:POST:1", 0, (10, 9))
        assert nxt  # the stage-2 post office reaches one row further back on land 0
        if nxt:
            blocker = next(cc for cc in sorted(meadow(0)) if rect("building:LIBRARY:0", 0, cc) & nxt and
                           not rect("building:LIBRARY:0", 0, cc) & rect("building:POST:1", 0, (10, 9)) and fits("building:LIBRARY:0", 0, cc))
            st["starters"] = ["LIBRARY"]
            st["positions"]["LIBRARY"] = list(blocker)
            _ledger(m, a, st, creative=False)
            earn(c, a, b, 100)
            cur = state(c, a)
            before = _stored(m, a)
            r = post(c, a, cur, action="upgrade", building="POST")
            assert r.status_code == 422 and "Platz" in r.json()["detail"]
            assert _stored(m, a) == before


def test_the_moved_piece_never_blocks_itself_and_a_shrink_never_saves_a_ground_overlap(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a = register(c, "A")
        st = {"buildings": {"HOUSE": 3}, "starters": [], "land": 5, "spent": 0, "revision": 1, "places_migrated": True,
              "positions": {"HOUSE": [10, 9]}, "decor": []}
        _ledger(m, a, st)
        cur = state(c, a, "creative")
        # one cell to the side overlaps its own old ground: fine
        r = post(c, a, cur, mode="creative", action="move", building="HOUSE", position=[11, 9])
        assert r.status_code == 200, r.text
        # shrinking: the stored layout never gets a ground overlap
        cur = r.json()
        cur = post(c, a, cur, mode="creative", action="decor_place", item="fountain", position=[13, 9]).json()
        for _ in range(5):
            r = post(c, a, cur, mode="creative", action="shrink")
            if r.status_code != 200:
                assert r.status_code == 422
                break
            cur = r.json()
        stored = _stored(m, a)
        assert not m.overlapping_pairs(stored)


def test_unknown_decoration_is_counted_with_the_signpost_ground_it_is_drawn_as(tmp_path):
    m = load_app(tmp_path)
    st = {"buildings": {}, "land": 2, "spent": 0, "revision": 1, "places_migrated": True, "positions": {},
          "decor": [{"id": 100, "item": "gnome_from_the_future", "position": [10, 9]}]}
    assert m.decor_footprint(st, st["decor"][0]) == occupied("decor:?", 2, (10, 9))
    held = rect("decor:bench", 2, (10, 9))
    assert m.decor_blocked(None, 0, st, [10, 9], item="bench") is not None and held & occupied("decor:?", 2, (10, 9))
