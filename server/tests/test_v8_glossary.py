"""Shared glossary: everyone may add words and explanations; only authors change their own text."""

import importlib
import os
from pathlib import Path

from fastapi.testclient import TestClient


def load_app(tmp_path: Path):
    os.environ["DB_PATH"] = str(tmp_path / "test.db")
    import app.main as main
    importlib.reload(main)
    return main


def auth(account: dict) -> dict[str, str]:
    return {"Authorization": f"Bearer {account['token']}"}


def register(client: TestClient, name: str) -> dict:
    response = client.post("/api/register", json={"name": name, "password": "password-123"})
    assert response.status_code == 200
    return response.json()


def test_requires_login(tmp_path):
    main = load_app(tmp_path)
    with TestClient(main.app) as client:
        assert client.get("/api/glossary").status_code == 401


def test_anyone_adds_and_everyone_sees(tmp_path):
    main = load_app(tmp_path)
    with TestClient(main.app) as client:
        a, b = register(client, "A"), register(client, "B")
        created = client.post("/api/glossary", headers=auth(a), json={"term": "  Layermaxxing ", "explanation": "Ebenen stapeln"})
        assert created.status_code == 201 and created.json()["merged"] is False
        entries = client.get("/api/glossary", headers=auth(b)).json()
        assert [e["term"] for e in entries] == ["Layermaxxing"]
        assert entries[0]["explanations"][0]["author_name"] == "A"
        assert entries[0]["explanations"][0]["mine"] is False


def test_same_word_merges_into_second_explanation(tmp_path):
    main = load_app(tmp_path)
    with TestClient(main.app) as client:
        a, b = register(client, "A"), register(client, "B")
        first = client.post("/api/glossary", headers=auth(a), json={"term": "Funke", "explanation": "eins"}).json()
        second = client.post("/api/glossary", headers=auth(b), json={"term": "funke", "explanation": "zwei"}).json()
        assert second == {"id": first["id"], "merged": True}
        add = client.post(f"/api/glossary/{first['id']}/explanations", headers=auth(a), json={"text": "drei"})
        assert add.status_code == 201
        entries = client.get("/api/glossary", headers=auth(a)).json()
        assert len(entries) == 1
        assert [x["text"] for x in entries[0]["explanations"]] == ["eins", "zwei", "drei"]


def test_only_author_edits_or_deletes_and_last_delete_removes_word(tmp_path):
    main = load_app(tmp_path)
    with TestClient(main.app) as client:
        a, b = register(client, "A"), register(client, "B")
        client.post("/api/glossary", headers=auth(a), json={"term": "Tal", "explanation": "alt"})
        expl = client.get("/api/glossary", headers=auth(a)).json()[0]["explanations"][0]["id"]
        assert client.patch(f"/api/glossary/explanations/{expl}", headers=auth(b), json={"text": "x"}).status_code == 403
        assert client.delete(f"/api/glossary/explanations/{expl}", headers=auth(b)).status_code == 403
        assert client.patch(f"/api/glossary/explanations/{expl}", headers=auth(a), json={"text": "neu"}).status_code == 200
        assert client.get("/api/glossary", headers=auth(b)).json()[0]["explanations"][0]["text"] == "neu"
        assert client.delete(f"/api/glossary/explanations/{expl}", headers=auth(a)).status_code == 200
        assert client.get("/api/glossary", headers=auth(a)).json() == []


def test_blank_input_rejected(tmp_path):
    main = load_app(tmp_path)
    with TestClient(main.app) as client:
        a = register(client, "A")
        assert client.post("/api/glossary", headers=auth(a), json={"term": "   ", "explanation": "x"}).status_code == 422
        assert client.post("/api/glossary", headers=auth(a), json={"term": "x", "explanation": ""}).status_code == 422
        assert client.post("/api/glossary/999/explanations", headers=auth(a), json={"text": "x"}).status_code == 404
