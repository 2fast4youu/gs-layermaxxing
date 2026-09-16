import base64
import hashlib
import importlib
import json
import os
import unicodedata
from pathlib import Path

import pytest
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


def befriend(client: TestClient, a: dict, b: dict) -> None:
    request_id = client.post("/api/friend-requests", headers=auth(a), json={"recipient_id": b["user_id"]}).json()["id"]
    client.post(f"/api/friend-requests/{request_id}/respond", headers=auth(b), json={"accept": True})


def b64(value: bytes) -> str:
    return base64.b64encode(value).decode()


def sealed_payload(recipient_id: int, release_at: int, plaintext: str = "Gru\u0308ße 🔐") -> tuple[dict, str]:
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import ec, utils
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM

    canonical = json.dumps({
        "cover_note": "Offen lesbar", "group_id": None, "message_class": "letter", "mode": "timed",
        "one_time": False, "random_from": None, "random_to": None,
        "recipient_ids": [recipient_id], "release_at": release_at, "title": "Versiegelt",
    }, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    clear = unicodedata.normalize("NFC", plaintext).encode()
    salt = bytes(range(32))
    plaintext_sha = hashlib.sha256(clear).digest()
    commitment = hashlib.sha256(b"GS-LM-COMMIT-V1" + salt + canonical.encode() + clear).digest()
    key = bytes(range(32, 64))
    nonce = bytes(range(12))
    ciphertext = AESGCM(key).encrypt(nonce, clear, canonical.encode())
    seal_digest = hashlib.sha256(
        b"GS-LM-SEAL-V1" + canonical.encode() + commitment + ciphertext + nonce
    ).digest()
    private_key = ec.generate_private_key(ec.SECP256R1())
    signature = private_key.sign(seal_digest, ec.ECDSA(utils.Prehashed(hashes.SHA256())))
    public_key = private_key.public_key().public_bytes(
        serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo
    )
    return ({
        "recipient_id": recipient_id, "ciphertext": b64(ciphertext), "nonce": b64(nonce),
        "encryption_key": b64(key), "title": "Versiegelt", "cover_note": "Offen lesbar",
        "mode": "timed", "release_at": release_at, "one_time": False,
        "commitment_salt": b64(salt), "plaintext_sha256": b64(plaintext_sha),
        "commitment": b64(commitment), "public_signing_key": b64(public_key),
        "signature": b64(signature), "protocol_version": 1, "canonical_metadata": canonical,
    }, unicodedata.normalize("NFC", plaintext))


def test_proof_fields_are_all_or_nothing_and_validated(tmp_path, monkeypatch):
    main = load_app(tmp_path)
    monkeypatch.setattr(main, "now_ts", lambda: 1_900_000_000)
    with TestClient(main.app) as client:
        a, b = register(client, "A"), register(client, "B")
        befriend(client, a, b)
        payload, _ = sealed_payload(b["user_id"], 1_900_000_060)

        partial = {k: v for k, v in payload.items() if k != "signature"}
        assert client.post("/api/messages", headers=auth(a), json=partial).status_code == 422
        bad_nonce = {**payload, "nonce": b64(b"too-short")}
        assert client.post("/api/messages", headers=auth(a), json=bad_nonce).status_code == 422
        noncanonical = {**payload, "canonical_metadata": json.dumps(json.loads(payload["canonical_metadata"]))}
        assert client.post("/api/messages", headers=auth(a), json=noncanonical).status_code == 422
        bad_signature = {**payload, "signature": b64(base64.b64decode(payload["signature"])[:-1] + b"x")}
        assert client.post("/api/messages", headers=auth(a), json=bad_signature).status_code == 422
        metadata_mismatch = {**payload, "title": "Manipulierter Titel"}
        assert client.post("/api/messages", headers=auth(a), json=metadata_mismatch).status_code == 422
        assert client.post("/api/messages", headers=auth(a), json=payload).status_code == 201


def test_proof_exports_and_offline_verification_before_and_after_release(tmp_path, monkeypatch):
    from tools.verify_export import verify_export

    main = load_app(tmp_path)
    clock = {"now": 1_900_000_000}
    monkeypatch.setattr(main, "now_ts", lambda: clock["now"])
    with TestClient(main.app) as client:
        a, b = register(client, "A"), register(client, "B")
        befriend(client, a, b)
        payload, normalized = sealed_payload(b["user_id"], clock["now"] + 60)
        message_id = client.post("/api/messages", headers=auth(a), json=payload).json()["id"]

        locked = client.get(f"/api/messages/{message_id}/proof", headers=auth(b))
        assert locked.status_code == 200
        export = locked.json()
        assert export["format"] == "gs-layermaxxing-verification"
        assert export["version"] == 1
        assert export["messages"][0]["cover_note"] == "Offen lesbar"
        assert export["messages"][0]["evidence"]["release_key"] is None
        assert verify_export(export)[0].status == "locked"
        pending = client.get("/api/proofs/pending", headers=auth(a)).json()
        assert [row["id"] for row in pending["messages"]] == [message_id]

        clock["now"] += 61
        released = client.get(f"/api/proofs/{message_id}", headers=auth(a)).json()
        evidence = released["messages"][0]["evidence"]
        assert evidence["release_key"] == payload["encryption_key"]
        assert evidence["release_key_sha256"] == hashlib.sha256(base64.b64decode(payload["encryption_key"])).hexdigest()
        verified = verify_export(released)[0]
        assert verified.status == "verified"
        assert verified.plaintext == normalized
        assert client.get("/api/proofs/pending", headers=auth(b)).json()["messages"] == []


def test_legacy_letters_remain_exportable_and_labelled(tmp_path, monkeypatch):
    main = load_app(tmp_path)
    monkeypatch.setattr(main, "now_ts", lambda: 1_900_000_000)
    with TestClient(main.app) as client:
        a, b = register(client, "A"), register(client, "B")
        befriend(client, a, b)
        message_id = client.post("/api/messages", headers=auth(a), json={
            "recipient_id": b["user_id"], "ciphertext": "YQ==", "nonce": "Yg==",
            "encryption_key": "Yw==", "release_at": 1_900_000_060,
        }).json()["id"]
        listed = client.get("/api/messages", headers=auth(b)).json()[0]
        assert listed["proof_status"] == "legacy"
        exported = client.get(f"/api/messages/{message_id}/proof", headers=auth(b)).json()["messages"][0]
        assert exported["legacy"] is True
        assert exported["evidence"] is None


def test_offline_verifier_rejects_tampering(tmp_path, monkeypatch):
    from tools.verify_export import VerificationError, verify_export

    main = load_app(tmp_path)
    clock = {"now": 1_900_000_000}
    monkeypatch.setattr(main, "now_ts", lambda: clock["now"])
    with TestClient(main.app) as client:
        a, b = register(client, "A"), register(client, "B")
        befriend(client, a, b)
        payload, _ = sealed_payload(b["user_id"], clock["now"] + 60)
        message_id = client.post("/api/messages", headers=auth(a), json=payload).json()["id"]
        clock["now"] += 61
        export = client.get(f"/api/messages/{message_id}/proof", headers=auth(b)).json()

    tampered = json.loads(json.dumps(export))
    tampered["messages"][0]["evidence"]["commitment"] = b64(bytes(32))
    with pytest.raises(VerificationError, match="signature"):
        verify_export(tampered)
