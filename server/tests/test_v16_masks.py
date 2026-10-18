"""7.9.12-szenen: grid_version 5, the occupancy of every piece is its graphic mask.

- geometry/island_masks.json (generated from the painted WebP alpha by server/tools/island_masks.py) holds, for every
  building x stage x land stage and every decoration item, the cells its rendered sprite covers (mask) and its ground
  part (base). The app reads the very same file (IslandMasks.kt) and draws the sprites at the sizes pinned there.
- A piece may stand where its whole mask is free and its base lies on the land; the harbour on a coast cell with open
  water in front and its pier ground on the land. The moved piece never blocks itself.
- Stricter than version 4 on purpose: neighbours that 7.9.11 put one cell apart overlap on screen and are refused now.
  Saved layouts are never moved or deleted for it; an upgrade that no longer fits is refused before paying.
The reference below is computed independently from the fixtures (never from main.py's helpers).
"""
import json
from pathlib import Path

from fastapi.testclient import TestClient
from test_v9_quests import auth, befriend, load_app, register
from test_island_evolution import earn, state

APP = Path(__file__).resolve().parents[1] / "app"
GRID = json.loads((APP / "island_grid.json").read_text())
MASKS = json.loads((APP / "geometry" / "island_masks.json").read_text())
BUILDINGS = ("HOUSE", "POST", "LIGHTHOUSE", "LIBRARY", "CAMPFIRE", "HARBOUR")
ITEMS = ("flowers", "bench", "lantern", "flag", "palm", "campfire", "hammock", "fountain", "windmill", "maibaum")
SPECIAL = ("place_home", "place_bude", "place_uni", "place_hut", "place_club", "place_station", "place_city", "place_desk")


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

def cells(land):
    return {tuple(c) for c in GRID["cells"][str(land)]}


def coast(land):
    return {tuple(c) for c in GRID["coast"][str(land)]}


def key_building(st, b):
    return "building:%s:%d" % (b, st.get("buildings", {}).get(b, 0))


def key_decor(item):
    if "decor:" + item in MASKS["pieces"]:
        return "decor:" + item
    return "decor:?special" if item.startswith("place_") else "decor:?"


def mask(key, land, spot, part="mask"):
    return {(spot[0] + dx, spot[1] + dy) for dx, dy in MASKS["pieces"][key]["lands"][land][part]}


def fits_land(key, land, spot, harbour=False):
    if tuple(spot) not in cells(land):
        return False
    base = mask(key, land, spot, "base")
    if harbour:
        x, y = spot
        return tuple(spot) in coast(land) and (x, y + 1) not in cells(land) and all(c in cells(land) for c in base if c[1] < y)
    return base <= cells(land)


def others(st, held):
    out = []
    for b in BUILDINGS:
        if ("b", b) != held and (st.get("buildings", {}).get(b) or b in st.get("starters", [])):
            out.append(mask(key_building(st, b), st["land"], st["positions"][b]))
    for d in st.get("decor", []):
        if ("d", d["id"]) != held:
            out.append(mask(key_decor(d["item"]), st["land"], d["position"]))
    return out


def reference_targets(st, held, key):
    land = st["land"]
    harbour = held == ("b", "HARBOUR")
    rest = others(st, held)
    return {c for c in (coast(land) if harbour else cells(land))
            if fits_land(key, land, c, harbour) and not any(mask(key, land, c) & o for o in rest)}


def populated(land):
    """Every kind of piece (big buildings, stage-0 pieces, harbour, decoration, special and unknown legacy decoration),
    packed by a greedy scan from the fixtures alone so that no two masks share a cell."""
    st = {"buildings": {"HOUSE": 2, "LIBRARY": 1, "HARBOUR": 1}, "starters": ["POST", "CAMPFIRE"], "land": land, "spent": 0,
          "revision": 1, "places_migrated": True, "positions": {}, "decor": []}
    order = [("b", "HARBOUR"), ("b", "HOUSE"), ("b", "LIBRARY"), ("d", (101, "place_hut")), ("b", "POST"), ("b", "CAMPFIRE"),
             ("d", (100, "bench")), ("d", (102, "gnome_legacy"))]
    taken = set()
    for kind, what in order:
        key = key_building(st, what) if kind == "b" else key_decor(what[1])
        harbour = what == "HARBOUR"
        spot = next(c for c in sorted(coast(land) if harbour else cells(land), key=lambda c: (c[1], c[0]))
                    if fits_land(key, land, c, harbour) and not mask(key, land, c) & taken)
        taken |= mask(key, land, spot)
        if kind == "b":
            st["positions"][what] = list(spot)
        else:
            st["decor"].append({"id": what[0], "item": what[1], "position": list(spot), **({"name": "Hütte"} if what[0] == 101 else {})})
    return st


# ------------------------------------------------------------------ the fixture

def test_the_server_publishes_version_5_and_both_fixtures_agree(tmp_path):
    m = load_app(tmp_path)
    assert m.GRID_VERSION == GRID["version"] == MASKS["version"] == 5
    assert GRID["masks"] == "geometry/island_masks.json"
    with TestClient(m.app) as c:
        info = c.get("/api/server-info").json()
        a = register(c, "A")
        assert info["grid_version"] == state(c, a)["construction"]["grid_version"] == 5
        assert "build_grid_v5" in info["features"]
    # the server reads exactly the committed file
    assert m.MASKS == MASKS


def test_every_building_stage_land_and_item_has_a_mask_from_its_real_sprite(tmp_path):
    m = load_app(tmp_path)
    keys = {"building:%s:%d" % (b, s) for b in BUILDINGS for s in range(4)} | {"decor:" + i for i in ITEMS + SPECIAL} | \
        {"decor:?", "decor:?special"}
    assert keys <= set(MASKS["pieces"])
    for key, piece in MASKS["pieces"].items():
        assert len(piece["lands"]) == 6, key
        for land, entry in enumerate(piece["lands"]):
            msk = {tuple(c) for c in entry["mask"]}
            base = {tuple(c) for c in entry["base"]}
            # the spot (the sprite's foot) belongs to the piece; the ground part is part of the mask; nothing below the foot
            assert (0, 0) in base <= msk, (key, land)
            assert max(dy for _, dy in msk) <= 1 and 0 < entry["size"] < .2, (key, land)
    # the sizes the app renders (and the generator measured): IslandPlans base size x stage growth x land shrink
    for b, base_size in {"HOUSE": .11, "LIGHTHOUSE": .105, "POST": .105, "CAMPFIRE": .10, "LIBRARY": .10, "HARBOUR": .115}.items():
        for s in range(4):
            for land in range(6):
                want = round(min(base_size * (.9 + s * .16), .145) * (.6 + .4 * m.land_factor(land)), 5)
                assert MASKS["pieces"]["building:%s:%d" % (b, s)]["lands"][land]["size"] == want
    # small starters are really smaller than the finished buildings (not one cell for everything, 7.9.11's mistake)
    for b in BUILDINGS:
        for land in range(6):
            small = len(MASKS["pieces"]["building:%s:0" % b]["lands"][land]["mask"])
            big = len(MASKS["pieces"]["building:%s:3" % b]["lands"][land]["mask"])
            assert small < big or b == "HOUSE" and land in (0, 1), (b, land, small, big)
        assert all(len(MASKS["pieces"]["building:%s:%d" % (b, s)]["lands"][5]["mask"]) >= 6 for s in range(4)), b


def test_masks_are_never_one_cell_for_a_wide_picture(tmp_path):
    """7.9.11's one-cell footprints: every building covers several columns and rows on every land stage."""
    for b in BUILDINGS:
        for s in range(4):
            for land in range(6):
                msk = MASKS["pieces"]["building:%s:%d" % (b, s)]["lands"][land]["mask"]
                assert len({dx for dx, _ in msk}) >= 2 and len({dy for _, dy in msk}) >= 2, (b, s, land)


# ------------------------------------------------------------------ the rule = the reference

def test_target_cells_are_exactly_what_the_rule_accepts(tmp_path):
    m = load_app(tmp_path)
    # (land 0 holds all six stage-0 pieces, test_v13_start; this crowded mix needs land 1+)
    for land in (1, 3, 5):
        st = populated(land)
        assert not m.overlapping_pairs(st), (land, m.overlapping_pairs(st))
        for held in [("b", "POST"), ("b", "HOUSE"), ("b", "HARBOUR"), ("b", "CAMPFIRE"), ("d", 100), ("d", 101), ("d", 102)]:
            if held[0] == "b":
                key = key_building(st, held[1])
            else:
                key = key_decor(next(d for d in st["decor"] if d["id"] == held[1])["item"])
            want = reference_targets(st, held, key)
            for i in range(-1, 22):
                for j in range(-1, 22):
                    if held[0] == "b":
                        ok = m.grid_buildable(i, j, land) and m.building_spot_error(st, [i, j], held[1]) is None
                    else:
                        d = next(x for x in st["decor"] if x["id"] == held[1])
                        ok = m.grid_buildable(i, j, land) and m.decor_blocked(None, None, st, [i, j], ignore_id=held[1],
                                                                            special=m.is_special(d)) is None
                    assert ok == ((i, j) in want), (land, held, i, j)
            # the own old spot is always a target (it never blocks itself) and some cells are really blocked
            spot = st["positions"][held[1]] if held[0] == "b" else next(d for d in st["decor"] if d["id"] == held[1])["position"]
            assert tuple(spot) in want, (land, held)
            assert want != cells(land)


def test_the_api_accepts_every_target_and_refuses_every_other_cell(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        for land in (1, 5):
            a = register(c, "L%d" % land)
            _ledger(m, a, populated(land))
            st = state(c, a, "creative")
            for held, key, body in [(("b", "POST"), "building:POST:0", dict(action="move", building="POST")),
                                    (("b", "HARBOUR"), "building:HARBOUR:1", dict(action="move", building="HARBOUR")),
                                    (("d", 101), "decor:place_hut", dict(action="decor_move", decor_id=101))]:
                before = st["construction"]
                want = reference_targets(before, held, key)
                for cell in m.grid_cells(land) + [[0, 0], [10, 20]]:
                    r = post(c, a, st, mode="creative", position=cell, **body)
                    assert (r.status_code == 200) == (tuple(cell) in want), (land, held, cell, r.text)
                    if r.status_code == 200:
                        back = before["positions"][held[1]] if held[0] == "b" else next(d for d in before["decor"] if d["id"] == held[1])["position"]
                        st = post(c, a, r.json(), mode="creative", position=back, **body).json()
                    else:
                        assert r.status_code == 422
            gnome = next(d for d in populated(land)["decor"] if d["id"] == 102)["position"]
            assert any(d["item"] == "gnome_legacy" and d["position"] == gnome for d in _stored(m, a)["decor"])


def test_densest_allowed_neighbours_never_share_a_cell_and_one_step_closer_is_refused(tmp_path):
    """For every pair of building stages: the closest accepted neighbour has disjoint masks; any offset whose masks
    share a cell is refused. (The pixel check of the same neighbours on the decoded sprites: IslandMasksPixelTest.)"""
    m = load_app(tmp_path)
    for land in (0, 3, 5):
        for b1 in ("HOUSE", "POST"):
            for s1 in range(4):
                for b2 in ("LIBRARY", "CAMPFIRE", "LIGHTHOUSE"):
                    for s2 in range(4):
                        st = {"buildings": {b1: s1, b2: s2}, "starters": [x for x, s in ((b1, s1), (b2, s2)) if s == 0],
                              "land": land, "positions": {b1: [10, 10]}, "decor": []}
                        st["buildings"] = {k: v for k, v in st["buildings"].items() if v}
                        a = mask("building:%s:%d" % (b1, s1), land, [10, 10])
                        for dx in range(-6, 7):
                            for dy in range(-6, 7):
                                p = [10 + dx, 10 + dy]
                                if not m.grid_buildable(p[0], p[1], land):
                                    continue
                                b = mask("building:%s:%d" % (b2, s2), land, p)
                                blocked = m.placement_blocked(st, p, "building", ignore_building=b2) is not None
                                assert blocked == bool(a & b), (land, b1, s1, b2, s2, p)


def test_a_close_neighbour_version_4_accepted_is_refused_now(tmp_path):
    """Gegenprobe: 7.9.11 (version 4) put the finished house and the post office one cell apart on the big land; their
    pictures stand inside each other, version 5 refuses exactly that. The same cells stay fine under version 4."""
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a = register(c, "A")
        st = {"buildings": {"HOUSE": 3, "POST": 2}, "starters": [], "land": 5, "spent": 0, "revision": 1, "places_migrated": True,
              "positions": {"HOUSE": [10, 10], "POST": [10, 13]}, "decor": []}
        _ledger(m, a, st)
        cur = state(c, a, "creative")
        for p in ([11, 10], [9, 10], [10, 9], [10, 11]):
            assert mask("building:HOUSE:3", 5, [10, 10]) & mask("building:POST:2", 5, p)
            r = post(c, a, cur, mode="creative", action="move", building="POST", position=p)
            assert r.status_code == 422 and "belegt" in r.json()["detail"], (p, r.text)
            # version 4 (the historic rule, one cell each) accepted it
            m.GRID_VERSION = 4
            assert m.building_spot_error(cur["construction"], p, "POST") is None
            m.GRID_VERSION = 5
        assert _stored(m, a)["positions"]["POST"] == [10, 13]


# ------------------------------------------------------------------ the harbour

def test_the_harbour_needs_its_whole_pier_on_a_fitting_coast(tmp_path):
    m = load_app(tmp_path)
    for land in range(6):
        for s in range(4):
            key = "building:HARBOUR:%d" % s
            st = {"buildings": {"HARBOUR": s} if s else {}, "starters": [] if s else ["HARBOUR"], "land": land, "positions": {}, "decor": []}
            ok = {tuple(c) for c in m.coast_cells(land) if m.building_spot_error(st, c, "HARBOUR") is None}
            want = {c for c in coast(land) if fits_land(key, land, c, harbour=True)}
            assert ok == want and ok, (land, s)
            # never a coast cell with land in front of it (the pier would point inland), never inland or water
            assert all((x, y + 1) not in cells(land) for x, y in ok)
            assert not ok - coast(land)
            # a free anchor alone is not enough: some free coast cells are refused because the pier does not fit there
            assert coast(land) - ok, (land, s)


def test_a_three_by_three_harbour_does_not_fit_where_only_its_anchor_is_free(tmp_path):
    m = load_app(tmp_path)
    land = 5
    key = "building:HARBOUR:3"
    msk = mask(key, land, [10, 15])
    assert len({x for x, _ in msk}) >= 3 and len({y for _, y in msk}) >= 3  # (at least) the 3x3 pier of the request
    # a lantern right behind the anchor: the anchor cell itself is free, the harbour's mask is not
    behind = next(c for c in sorted(msk) if c != (10, 15) and c[1] < 15)
    st = {"buildings": {"HARBOUR": 3}, "starters": [], "land": land, "positions": {"HARBOUR": [8, 15]},
          "decor": [{"id": 100, "item": "lantern", "position": list(behind)}]}
    assert (10, 15) not in mask("decor:lantern", land, behind)
    assert m.building_spot_error(st, [10, 15], "HARBOUR") == "Bauplatz ist durch einen Ort oder Deko belegt"
    # the own old spot never blocks: one coast cell further along overlaps its old mask and is fine
    st = {"buildings": {"HARBOUR": 3}, "starters": [], "land": land, "positions": {"HARBOUR": [10, 15]}, "decor": []}
    assert mask(key, land, [10, 15]) & mask(key, land, [11, 15])
    assert m.building_spot_error(st, [11, 15], "HARBOUR") is None


# ------------------------------------------------------------------ upgrade and legacy layouts

def test_an_upgrade_whose_bigger_picture_does_not_fit_is_refused_before_paying(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a, b = register(c, "A"), register(c, "B")
        befriend(c, a, b)
        earn(c, a, b, 100)
        # stage-0 letter tree on the land-0 rim: the tree fits, the stage-1 letter box would stick out of the land
        rim = next(p for p in m.grid_cells(0) if fits_land("building:POST:0", 0, p) and not fits_land("building:POST:1", 0, p))
        st = post(c, a, state(c, a), action="build", building="POST", position=rim)
        assert st.status_code == 200, st.text
        st = st.json()
        before = _stored(m, a)
        r = post(c, a, st, action="upgrade", building="POST")
        assert r.status_code == 422 and "zuerst verschieben" in r.json()["detail"], r.text
        assert _stored(m, a) == before  # nothing paid, nothing moved, same revision
        assert state(c, a)["construction"]["spent"] == 0
        # a neighbour that leaves room for the stage-0 tree but not for the stage-1 box: same, no secret relocation
        st = post(c, a, st, action="move", building="POST", position=[10, 11]).json()
        lantern_spot = next(p for p in m.grid_cells(0)
                            if not mask("decor:lantern", 0, p) & mask("building:POST:0", 0, [10, 11])
                            and mask("decor:lantern", 0, p) & mask("building:POST:1", 0, [10, 11])
                            and fits_land("decor:lantern", 0, p))
        with m.db() as conn:
            conn.execute("UPDATE users SET creative_entitled=1 WHERE id=?", (a["user_id"],))
        st = post(c, a, state(c, a, "creative"), mode="creative", action="decor_place", item="lantern", position=lantern_spot)
        assert st.status_code == 200, st.text
        before = _stored(m, a)
        r = post(c, a, st.json(), action="upgrade", building="POST")
        assert r.status_code == 422 and _stored(m, a) == before
        # moved first by the owner, the upgrade goes through and costs exactly the first step
        st = post(c, a, state(c, a), action="move", building="POST", position=[12, 14])
        assert st.status_code == 200, st.text
        up = post(c, a, st.json(), action="upgrade", building="POST")
        assert up.status_code == 200, up.text
        con = up.json()["construction"]
        assert con["buildings"]["POST"] == 1 and con["positions"]["POST"] == [12, 14] and con["spent"] == 25


def test_a_version_4_island_with_overlaps_is_kept_exactly_and_can_be_repaired(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a = register(c, "A")
        # a layout version 4 accepted (one cell each): on screen the house, the post office and the bench overlap
        old = {"buildings": {"HOUSE": 3, "POST": 2, "LIBRARY": 1}, "starters": [], "land": 5, "spent": 0, "revision": 3,
               "places_migrated": True,
               "positions": {"HOUSE": [10, 10], "POST": [11, 10], "LIBRARY": [6, 8]},
               "decor": [{"id": 100, "item": "bench", "position": [9, 10]}]}
        _ledger(m, a, old)
        seen = state(c, a, "creative")["construction"]
        # read: nothing moved, nothing deleted, no error
        assert seen["positions"] == old["positions"] and seen["decor"] == old["decor"] and seen["revision"] == 3
        assert {("b:HOUSE", "b:POST")} <= m.overlapping_pairs(old) or {("b:POST", "b:HOUSE")} <= m.overlapping_pairs(old)
        assert _stored(m, a)["positions"] == old["positions"]
        cur = state(c, a, "creative")
        # an unrelated piece can be moved although the old overlap stays (it is not made worse)
        r = post(c, a, cur, mode="creative", action="move", building="LIBRARY", position=[6, 7])
        assert r.status_code == 200, r.text
        cur = r.json()
        assert cur["construction"]["positions"]["HOUSE"] == [10, 10] and cur["construction"]["positions"]["POST"] == [11, 10]
        # the overlapping post office may not go to another overlapping spot, but to a really free one
        assert post(c, a, cur, mode="creative", action="move", building="POST", position=[10, 11]).status_code == 422
        free = reference_targets(cur["construction"], ("b", "POST"), "building:POST:2")
        spot = sorted(free, key=lambda p: (abs(p[0] - 14) + abs(p[1] - 13)))[0]
        r = post(c, a, cur, mode="creative", action="move", building="POST", position=list(spot))
        assert r.status_code == 200, r.text
        pairs = m.overlapping_pairs(r.json()["construction"])
        assert not any("b:POST" in p for p in pairs)


def test_shrinking_never_lets_two_pictures_overlap(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a = register(c, "A")
        st = {"buildings": {"HOUSE": 1, "POST": 1}, "starters": [], "land": 5, "spent": 0, "revision": 1, "places_migrated": True,
              "positions": {"HOUSE": [7, 9], "POST": [12, 9]}, "decor": []}
        _ledger(m, a, st)
        cur = state(c, a, "creative")
        while cur["construction"]["land"] > 0:
            r = post(c, a, cur, mode="creative", action="shrink")
            if r.status_code != 200:
                assert r.status_code == 422
                land = cur["construction"]["land"] - 1
                assert mask("building:HOUSE:1", land, [7, 9]) & mask("building:POST:1", land, [12, 9]) or \
                    not fits_land("building:HOUSE:1", land, [7, 9]) or not fits_land("building:POST:1", land, [12, 9])
                break
            cur = r.json()
            assert not m.overlapping_pairs(cur["construction"])
        assert _stored(m, a)["positions"] == st["positions"]


def test_unknown_decoration_is_counted_with_the_signpost_it_is_drawn_as(tmp_path):
    m = load_app(tmp_path)
    st = populated(5)
    assert m.decor_mask_key("gnome_legacy") == "decor:?" and m.decor_mask_key("place_future") == "decor:?special"
    spot = next(d for d in st["decor"] if d["id"] == 102)["position"]
    gnome = mask("decor:?", 5, spot)
    assert len(gnome) > 1
    hit = next(c for c in sorted(gnome) if c != tuple(spot))
    assert m.placement_blocked(st, list(hit), "decor", item="flowers") is not None or \
        not mask("decor:flowers", 5, hit) & gnome
