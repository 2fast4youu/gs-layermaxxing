"""Opt-in v8 only: independent v6 stage-0 oracle, no cleanup/migration/refund."""
import copy
import importlib.util
import json
from pathlib import Path

from fastapi.testclient import TestClient
from test_v9_quests import load_app, register
from test_island_evolution import state
from test_v17_ground import BUILDINGS, GROUND, LATTICE, _ledger, _stored, post, rect, fits
from test_v18_edge import ref_edge

GEO = Path(__file__).resolve().parents[1] / 'app' / 'geometry'
FIXTURE = json.loads((GEO / 'island_constant_ground.json').read_text())


def ledger(b, land, spot):
    return dict(buildings={}, starters=[b], land=land, spent=37, revision=9,
                places_migrated=True, positions={b: list(spot)}, decor=[])


def oracle(b, land, spot):
    return rect('building:%s:0' % b, land, spot) | rect('building:%s:0' % b, land, spot, 'sea')


def test_fixture_reproducible_and_art_sizes_decoration_terrain_unchanged():
    spec = importlib.util.spec_from_file_location('constant_tool', GEO.parents[1] / 'tools' / 'island_constant_ground.py')
    tool = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(tool)
    assert FIXTURE['version'] == 8 and GROUND['version'] == 6
    assert tool.generate() == FIXTURE
    for key, piece in GROUND['pieces'].items():
        if key.startswith('decor:'):
            assert FIXTURE['pieces'][key] == piece
        for land, shape in enumerate(piece['lands']):
            assert FIXTURE['pieces'][key]['lands'][land]['size'] == shape['size']
    for field in ('zones', 'coast', 'berth', 'projection'):
        assert FIXTURE[field] == GROUND[field]


def test_every_building_land_stage_and_candidate_has_constant_cells_and_independent_fit(tmp_path):
    m = load_app(tmp_path)
    assert m.GRID_VERSION == 7  # implementation is deliberately not enabled
    m.GRID_VERSION = 8
    with TestClient(m.app) as c:
        a = register(c, 'DeclaredGround8')
        assert c.get('/api/server-info').json()['grid_version'] == 8
        assert state(c, a)['construction']['grid_version'] == 8
    edges = {land: ref_edge(land) for land in range(6)}
    for b in BUILDINGS:
        for land in range(6):
            st = ledger(b, land, (10, 10))
            for stage in range(4):
                st['buildings'] = {b: stage}
                for spot in LATTICE:
                    expected = oracle(b, land, spot)
                    assert m.building_footprint(st, b) == oracle(b, land, (10, 10))
                    assert m.ground_cells('building:%s:%d' % (b, stage), land, spot) == expected
                    valid = fits('building:%s:0' % b, land, spot, b == 'HARBOUR')
                    assert (m.building_spot_error(st, list(spot), b) is None) == valid
                    if b == 'LIGHTHOUSE':
                        assert (m.edge_error(st, list(spot), b) is None) == bool(expected & edges[land])


def test_all_upgrades_keep_tight_neighbours_and_exact_spent_without_cleanup(tmp_path):
    m = load_app(tmp_path)
    m.GRID_VERSION = 8
    with TestClient(m.app) as c:
        a = register(c, 'ConstantGround')
        for b in BUILDINGS:
            for land in range(6):
                spot = next(p for p in LATTICE if fits('building:%s:0' % b, land, p, b == 'HARBOUR'))
                st = ledger(b, land, spot)
                occupied = oracle(b, land, spot)
                # Surround all four sides with saved one-cell lantern grounds. No neighbour clearance.
                border = {(x+dx, y+dy) for x, y in occupied for dx, dy in ((1,0),(-1,0),(0,1),(0,-1))} - occupied
                st['decor'] = [dict(id=100+i, item='lantern', position=list(p)) for i, p in enumerate(sorted(border))]
                _ledger(m, a, st)
                before = copy.deepcopy(st)
                for stage in range(1, 4):
                    view = state(c, a)
                    response = post(c, a, view, action='upgrade', building=b, mode='creative')
                    assert response.status_code == 200, response.text
                    saved = _stored(m, a)
                    assert saved['buildings'][b] == stage
                    assert saved['positions'] == before['positions']
                    assert saved['decor'] == before['decor']
                    assert saved['spent'] == 37
                    assert m.building_footprint(saved, b) == occupied
                assert _stored(m, a)['revision'] == 12


def test_saved_out_of_zone_and_overlapping_legacy_pieces_never_move_or_disappear(tmp_path):
    m = load_app(tmp_path)
    m.GRID_VERSION = 8
    st = ledger('POST', 0, (0, 0))
    st['decor'] = [dict(id=100, item='lantern', position=[0, 0])]
    original = copy.deepcopy(st)
    assert m.normalize_layout(st, strict=True) == original
    with TestClient(m.app) as c:
        a = register(c, 'LegacyGround')
        _ledger(m, a, st)
        response = post(c, a, state(c, a), action='upgrade', building='POST', mode='creative')
        assert response.status_code == 200, response.text
        saved = _stored(m, a)
        assert saved['positions'] == original['positions'] and saved['decor'] == original['decor']
        assert saved['spent'] == 37


def test_unknown_server_rule_refuses_writes(tmp_path):
    m = load_app(tmp_path)
    with TestClient(m.app) as c:
        a = register(c, 'UnknownGround')
        view = state(c, a)
        m.GRID_VERSION = 9
        assert post(c, a, view, action='build', building='POST', position=[10, 10]).status_code == 503


def test_normal_upgrade_spends_exact_cost_once_and_failures_preserve_ledger(tmp_path):
    from test_v9_quests import befriend
    from test_island_evolution import earn
    m = load_app(tmp_path)
    m.GRID_VERSION = 8
    with TestClient(m.app) as c:
        a, b = register(c, 'Payer'), register(c, 'Peer')
        befriend(c, a, b)
        for _ in range(4):
            earn(c, a, b, 100)
        st = ledger('POST', 0, (10, 10))
        st['decor'] = [dict(id=100, item='lantern', position=[11, 10])]
        _ledger(m, a, st, creative=False)
        with m.db() as conn:
            conn.execute('UPDATE users SET created_at=? WHERE id=?', (m.now_ts()-30*86400, a['user_id']))
        spent = 37
        for stage in range(3):
            view = state(c, a)
            before = _stored(m, a)
            blocked = post(c, a, view, action='upgrade', building='POST', position=[11, 10])
            assert blocked.status_code == 422
            assert _stored(m, a) == before
            response = post(c, a, view, action='upgrade', building='POST')
            assert response.status_code == 200, response.text
            spent += m.BUILD_COSTS[stage]
            saved = _stored(m, a)
            assert saved['spent'] == spent and saved['positions'] == st['positions'] and saved['decor'] == st['decor']
            assert post(c, a, view, action='upgrade', building='POST').status_code == 409
            assert _stored(m, a) == saved
        response = post(c, a, state(c, a), action='demolish', building='POST')
        assert response.status_code == 200
        assert _stored(m, a)['spent'] == spent  # no inferred refund on explicit removal either
