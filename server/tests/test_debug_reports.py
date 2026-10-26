"""All storage/DB fixtures are isolated; never connect to a deployed service."""
import hashlib
import json
import struct
import zlib

import pytest
from fastapi.testclient import TestClient
from app.debug_reports import KEY, MAX_BYTES, ReportStore, validate_png
from test_api import load_app, register, auth


def chunk(kind, body):
    return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xffffffff)


def png(text=b"Shake report v1\nApp fixture code=83\n"):
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(b"\0\xff\0\0\xff"))
            + chunk(b"iTXt", KEY + text) + chunk(b"IEND", b""))


def test_real_pixels_metadata_crc_limits():
    body = png()
    assert validate_png(body).startswith("Shake report v1\n")
    for bad in (body[:-1], body + b"extra", b"not png", body[:40] + bytes([body[40] ^ 1]) + body[41:],
                png(b"bad schema"), png(b"Shake report v1\n" + b"x" * 65536)):
        with pytest.raises(ValueError):
            validate_png(bad)


def test_pixel_bomb_and_missing_idat_rejected():
    header = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 6, 0, 0, 0))
    tail = chunk(b"iTXt", KEY + b"Shake report v1\n") + chunk(b"IEND", b"")
    for raw in (b"", b"\x05\0\0\0\0", b"\0" * 100000):
        with pytest.raises(ValueError):
            validate_png(header + chunk(b"IDAT", zlib.compress(raw)) + tail)
    with pytest.raises(ValueError):
        validate_png(header + tail)


def test_authenticated_receipt_status_owner_delete_and_role(tmp_path, monkeypatch):
    monkeypatch.setenv("SERVER_ROLE", "test")
    main = load_app(tmp_path)
    with TestClient(main.app) as client:
        a = register(client, "ShakeA"); b = register(client, "ShakeB")
        assert client.post("/api/debug-reports", content=png(), headers={"Content-Type": "image/png"}).status_code == 401
        headers = {**auth(a["token"]), "Content-Type": "image/png"}
        result = client.post("/api/debug-reports", content=png(), headers=headers)
        assert result.status_code == 201, result.text
        receipt = result.json(); path = "/api/debug-reports/" + receipt["id"]
        assert receipt["sha256"] == hashlib.sha256(png()).hexdigest()
        assert receipt["bytes"] == len(png())
        assert set(receipt) == {"id", "sha256", "bytes"}
        assert client.get(path, headers=auth(a["token"])).json() == receipt
        assert client.get(path).status_code == 401
        assert client.get(path, headers=auth(b["token"])).status_code == 404
        assert client.delete(path, headers=auth(b["token"])).status_code == 404
        assert client.get("/api/debug-reports/not-a-uuid", headers=headers).status_code == 404
        assert client.post("/api/debug-reports", content=png()[:-1], headers=headers).status_code == 422
        assert client.post("/api/debug-reports", content=b"bad", headers=auth(a["token"])).status_code == 415
        assert client.post("/api/debug-reports", content=b"x" * (MAX_BYTES + 1), headers=headers).status_code == 413
        assert client.delete(path, headers=auth(a["token"])).status_code == 200
        assert client.get(path, headers=auth(a["token"])).status_code == 404
        monkeypatch.setattr(main, "SERVER_ROLE", "production")
        assert client.post("/api/debug-reports", content=png(), headers=headers).status_code == 404
        assert client.get(path, headers=auth(a["token"])).status_code == 404


def test_retention_private_files_and_stored_byte_verification(tmp_path, monkeypatch):
    import app.debug_reports as reports
    source = tmp_path / "source.png"; source.write_bytes(png())
    store = ReportStore(tmp_path / "private")
    receipts = [store.put(source, 1) for _ in range(21)]
    assert len(list(store.directory.glob("*.png"))) == 20
    assert not (store.directory / (receipts[0]["id"] + ".png")).exists()
    assert store.directory.stat().st_mode & 0o777 == 0o700
    last = receipts[-1]
    file = store.directory / (last["id"] + ".png")
    assert file.stat().st_mode & 0o777 == 0o600
    file.write_bytes(b"corrupted")
    from fastapi import HTTPException
    with pytest.raises(HTTPException) as failure:
        store.get(last["id"], 1)
    assert failure.value.status_code == 409
    monkeypatch.setattr(reports, "GLOBAL_COUNT", 2)
    monkeypatch.setattr(reports, "GLOBAL_BYTES", len(png()) * 2)
    store.put(source, 2); store.put(source, 3)
    assert len(list(store.directory.glob("*.json"))) == 2
    monkeypatch.setattr(reports.time, "time", lambda: 99999999999)
    with store.locked():
        assert store.prune() == []
    assert not list(store.directory.glob("*.png"))


def test_chunked_oversize_and_strict_structural_rejection(tmp_path, monkeypatch):
    monkeypatch.setenv("SERVER_ROLE", "test")
    main = load_app(tmp_path)
    with TestClient(main.app) as client:
        a = register(client, "Chunked")
        headers = {**auth(a["token"]), "Content-Type": "image/png"}
        chunks = (b"x" * 65536 for _ in range(MAX_BYTES // 65536 + 1))
        assert client.post("/api/debug-reports", content=chunks, headers=headers).status_code == 413
        assert not list((tmp_path / "shake-reports").glob("*.png"))
    header = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 6, 0, 0, 0))
    pixels = chunk(b"IDAT", zlib.compress(b"\0\xff\0\0\xff"))
    tail = chunk(b"iTXt", KEY + b"Shake report v1\n") + chunk(b"IEND", b"")
    for invalid in (header + pixels + chunk(b"zTXt", b"bomb\0\0" + zlib.compress(b"x" * 100000)) + tail,
                    header + pixels + chunk(b"iTXt", KEY + b"Shake report v1\n") + pixels + tail,
                    header + pixels + chunk(b"iTXt", KEY + b"Shake report v1\n") + tail):
        with pytest.raises(ValueError):
            validate_png(invalid)
