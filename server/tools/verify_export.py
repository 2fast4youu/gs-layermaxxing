#!/usr/bin/env python3
"""Verify a GS Layermaxxing proof export without contacting the server."""

from __future__ import annotations

import argparse
import base64
import binascii
import hashlib
import json
import sys
import unicodedata
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from cryptography.exceptions import InvalidSignature, InvalidTag
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, utils
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

FORMAT = "gs-layermaxxing-verification"
COMMIT_PREFIX = b"GS-LM-COMMIT-V1"
SEAL_PREFIX = b"GS-LM-SEAL-V1"
ATTACHMENT_MARKER = b"\nGS-LM-ATTACHMENT-V1\n"


class VerificationError(ValueError):
    """Raised when an export is malformed or its cryptographic evidence is invalid."""


@dataclass(frozen=True)
class VerificationResult:
    message_id: int
    status: str
    plaintext: str | None = None
    attachment: bytes | None = None


def decode(name: str, value: Any, length: int | None = None) -> bytes:
    if not isinstance(value, str):
        raise VerificationError(f"{name} fehlt")
    try:
        result = base64.b64decode(value, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise VerificationError(f"{name} ist kein gültiges Base64") from exc
    if length is not None and len(result) != length:
        raise VerificationError(f"{name} hat eine ungültige Länge")
    return result


def canonical_bytes(value: Any) -> bytes:
    if not isinstance(value, str):
        raise VerificationError("canonical_metadata fehlt")
    try:
        decoded = json.loads(value)
    except json.JSONDecodeError as exc:
        raise VerificationError("canonical_metadata ist kein JSON") from exc
    canonical = json.dumps(decoded, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    if not isinstance(decoded, dict) or canonical != value:
        raise VerificationError("canonical_metadata ist nicht kanonisch")
    return value.encode("utf-8")


def attachment_aad(metadata: bytes, name: str, mime: str) -> bytes:
    return metadata + ATTACHMENT_MARKER + name.encode("utf-8") + b"\n" + mime.encode("utf-8")


def verify_message(message: dict[str, Any]) -> VerificationResult:
    message_id = message.get("id")
    if not isinstance(message_id, int):
        raise VerificationError("Nachrichten-ID fehlt")
    if message.get("legacy") is True and message.get("evidence") is None:
        return VerificationResult(message_id, "legacy")
    evidence = message.get("evidence")
    if not isinstance(evidence, dict) or evidence.get("protocol_version") != 1:
        raise VerificationError("Nachweisprotokoll fehlt oder wird nicht unterstützt")

    metadata = canonical_bytes(evidence.get("canonical_metadata"))
    salt = decode("commitment_salt", evidence.get("commitment_salt"), 32)
    expected_plaintext_hash = decode("plaintext_sha256", evidence.get("plaintext_sha256"), 32)
    commitment = decode("commitment", evidence.get("commitment"), 32)
    ciphertext = decode("ciphertext", evidence.get("ciphertext"))
    nonce = decode("nonce", evidence.get("nonce"), 12)
    public_der = decode("public_signing_key", evidence.get("public_signing_key"))
    signature = decode("signature", evidence.get("signature"))

    if evidence.get("public_key_fingerprint") != hashlib.sha256(public_der).hexdigest():
        raise VerificationError("Fingerabdruck des Prüfschlüssels stimmt nicht")
    try:
        public_key = serialization.load_der_public_key(public_der)
        if not isinstance(public_key, ec.EllipticCurvePublicKey) or not isinstance(public_key.curve, ec.SECP256R1):
            raise ValueError("not P-256")
        seal_digest = hashlib.sha256(SEAL_PREFIX + metadata + commitment + ciphertext + nonce).digest()
        public_key.verify(signature, seal_digest, ec.ECDSA(utils.Prehashed(hashes.SHA256())))
    except InvalidSignature as exc:
        raise VerificationError("ECDSA signature/Signatur stimmt nicht") from exc
    except (TypeError, ValueError) as exc:
        raise VerificationError("Öffentlicher P-256-Schlüssel ist ungültig") from exc

    release_key_value = evidence.get("release_key")
    if release_key_value is None:
        return VerificationResult(message_id, "locked")
    release_key = decode("release_key", release_key_value, 32)
    if evidence.get("release_key_sha256") != hashlib.sha256(release_key).hexdigest():
        raise VerificationError("Fingerabdruck des AES-Freigabeschlüssels stimmt nicht")
    try:
        plaintext_bytes = AESGCM(release_key).decrypt(nonce, ciphertext, metadata)
    except InvalidTag as exc:
        raise VerificationError("AES-GCM-Prüfung ist fehlgeschlagen") from exc
    if hashlib.sha256(plaintext_bytes).digest() != expected_plaintext_hash:
        raise VerificationError("Klartext-Hash stimmt nicht")
    expected_commitment = hashlib.sha256(COMMIT_PREFIX + salt + metadata + plaintext_bytes).digest()
    if expected_commitment != commitment:
        raise VerificationError("Versiegelungs-Hash stimmt nicht")
    try:
        plaintext = plaintext_bytes.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise VerificationError("Klartext ist nicht UTF-8") from exc
    if unicodedata.normalize("NFC", plaintext) != plaintext:
        raise VerificationError("Klartext ist nicht NFC-normalisiert")

    attachment_bytes = None
    attachment = evidence.get("attachment")
    if attachment is not None:
        if not isinstance(attachment, dict):
            raise VerificationError("Anhangsnachweis ist ungültig")
        name, mime = attachment.get("name"), attachment.get("mime")
        if not isinstance(name, str) or not isinstance(mime, str):
            raise VerificationError("Anhangsmetadaten fehlen")
        attachment_nonce = decode("attachment.nonce", attachment.get("nonce"), 12)
        if attachment_nonce == nonce:
            raise VerificationError("Anhang und Brief verwenden dieselbe Nonce")
        try:
            attachment_bytes = AESGCM(release_key).decrypt(
                attachment_nonce, decode("attachment.ciphertext", attachment.get("ciphertext")),
                attachment_aad(metadata, name, mime),
            )
        except InvalidTag as exc:
            raise VerificationError("AES-GCM-Prüfung des Anhangs ist fehlgeschlagen") from exc
    return VerificationResult(message_id, "verified", plaintext, attachment_bytes)


def verify_export(document: dict[str, Any]) -> list[VerificationResult]:
    if document.get("format") != FORMAT or document.get("version") != 1:
        raise VerificationError("Unbekanntes Exportformat")
    messages = document.get("messages")
    if not isinstance(messages, list):
        raise VerificationError("Nachrichtenliste fehlt")
    return [verify_message(message) for message in messages]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("export", type=Path, help="Pfad zu einer .gsverify.json-Datei")
    args = parser.parse_args()
    try:
        document = json.loads(args.export.read_text(encoding="utf-8"))
        results = verify_export(document)
    except (OSError, json.JSONDecodeError, VerificationError) as exc:
        print(f"UNGÜLTIG: {exc}", file=sys.stderr)
        return 1
    for result in results:
        print(f"Nachricht {result.message_id}: {result.status}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
