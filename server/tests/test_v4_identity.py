import importlib
import os
from pathlib import Path

from fastapi.testclient import TestClient


def load_app(tmp_path: Path, monkeypatch, *, role: str = "test"):
    monkeypatch.setenv("DB_PATH", str(tmp_path / "test.db"))
    monkeypatch.setenv("SERVER_NAME", "Gerfried CI")
    monkeypatch.setenv("SERVER_ROLE", role)
    import app.main as main

    importlib.reload(main)
    return main


def test_server_info_and_role_header(tmp_path, monkeypatch):
    main = load_app(tmp_path, monkeypatch)
    with TestClient(main.app) as client:
        response = client.get("/api/server-info")

        assert response.status_code == 200
        assert response.headers["X-Layermaxxing-Role"] == "test"
        assert response.json() == {
            "name": "Gerfried CI",
            "role": "test",
            "version": "4.0.0",
            "warning": "TESTSERVER VON GERFRIED – NUR ZUM AUSPROBIEREN",
            "features": ["letters", "chats", "friendship_settings", "ep", "verification_exports", "creative_mode", "sparks"],
        }
        assert client.get("/health").headers["X-Layermaxxing-Role"] == "test"


def test_invalid_server_role_is_never_reported_as_production(tmp_path, monkeypatch):
    main = load_app(tmp_path, monkeypatch, role="definitely-not-production")
    with TestClient(main.app) as client:
        info = client.get("/api/server-info").json()
        assert info["role"] == "test"
        assert info["warning"]
