"""Fresh runtime imports: real API selects 7/8, invalid config cannot start."""
import importlib.util
import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from test_v9_quests import register
from test_island_evolution import state
from test_v17_ground import _ledger, _stored, post


def fresh_app(monkeypatch, tmp_path, version):
    if version is None:
        monkeypatch.delenv("GS_ISLAND_GRID_VERSION", raising=False)
    else:
        monkeypatch.setenv("GS_ISLAND_GRID_VERSION", version)
    monkeypatch.setenv("DB_PATH", str(tmp_path / "runtime.db"))
    monkeypatch.setenv("SERVER_ROLE", "test")
    # Isolate from app.main and historic tests that explicitly pin old rules.
    spec = importlib.util.spec_from_file_location(
        "island_runtime_config", Path(__file__).resolve().parents[1] / "app/main.py")
    module = importlib.util.module_from_spec(spec)
    monkeypatch.setitem(sys.modules, spec.name, module)
    spec.loader.exec_module(module)
    return module


@pytest.mark.parametrize("configured, expected", [(None, 7), ("7", 7), ("8", 8)])
def test_fresh_config_controls_advertisement_and_actual_upgrade_api(monkeypatch, tmp_path, configured, expected):
    m = fresh_app(monkeypatch, tmp_path, configured)
    assert m.GRID_VERSION == expected
    assert not m.DB_PATH.exists()  # importing valid configuration does not initialize storage
    with TestClient(m.app) as client:
        account = register(client, "RuntimeGround")
        info = client.get("/api/server-info").json()
        assert info["grid_version"] == expected
        assert ("build_grid_v8" in info["features"]) == (expected == 8)
        # Tree fits beside the lantern; historic stage-1 ground expands into it.
        original = dict(buildings={}, starters=["POST"], land=0, spent=37, revision=9,
                        places_migrated=True, positions={"POST": [10, 10]},
                        decor=[dict(id=100, item="lantern", position=[11, 10])])
        _ledger(m, account, original)
        view = state(client, account)
        assert view["construction"]["grid_version"] == expected
        assert _stored(m, account) == original  # reading never rewrites the saved layout
        for stage in range(1, 4):
            before = _stored(m, account)
            response = post(client, account, view, action="upgrade", building="POST", mode="creative")
            if expected == 7:
                assert response.status_code == 422, response.text
                assert _stored(m, account) == before
                break
            assert response.status_code == 200, response.text
            saved = _stored(m, account)
            assert saved["buildings"]["POST"] == stage
            assert saved["positions"] == original["positions"]
            assert saved["decor"] == original["decor"]
            assert saved["spent"] == 37
            assert saved["revision"] == 9 + stage
            view = state(client, account)
            assert view["construction"]["grid_version"] == 8
        before = _stored(m, account)
        blocked = post(client, account, view, action="move", building="POST", position=[0, 0], mode="creative")
        assert blocked.status_code == 422, blocked.text
        assert _stored(m, account) == before


@pytest.mark.parametrize("configured", ["", "6", "9", "99", "unknown", " 8", "8 ", "08", "8.0", "true"])
def test_invalid_configuration_fails_before_startup_or_database_creation(monkeypatch, tmp_path, configured):
    with pytest.raises(ValueError, match="GS_ISLAND_GRID_VERSION must be exactly 7 or 8"):
        fresh_app(monkeypatch, tmp_path, configured)
    assert not (tmp_path / "runtime.db").exists()


def test_config_is_captured_at_import_and_changes_require_restart(monkeypatch, tmp_path):
    m = fresh_app(monkeypatch, tmp_path, "7")
    monkeypatch.setenv("GS_ISLAND_GRID_VERSION", "8")
    with TestClient(m.app) as client:
        assert client.get("/api/server-info").json()["grid_version"] == 7


def test_restart_from_7_to_8_preserves_saved_ledger_on_api_read(monkeypatch, tmp_path):
    old = fresh_app(monkeypatch, tmp_path, "7")
    with TestClient(old.app) as client:
        account = register(client, "SavedGround")
        # Deliberately invalid and overlapping historic pieces must remain saved.
        original = dict(buildings={"POST": 2}, starters=[], land=0, spent=137, revision=19,
                        places_migrated=True, positions={"POST": [0, 0]},
                        decor=[dict(id=100, item="lantern", position=[0, 0])])
        _ledger(old, account, original)
        assert _stored(old, account) == original
    new = fresh_app(monkeypatch, tmp_path, "8")
    with TestClient(new.app) as client:
        assert client.get("/api/server-info").json()["grid_version"] == 8
        view = state(client, account)["construction"]
        assert view["grid_version"] == 8
        assert view["positions"] == original["positions"]
        assert view["decor"] == original["decor"]
        assert view["spent"] == 137
        assert view["revision"] == 19
        assert _stored(new, account) == original
