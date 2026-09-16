# Gerfried v4 test build — implementation contract

This document defines the requested test build on branch `gerfried`. Implement the complete vertical slice with tests before production changes. Preserve all existing v3 behavior and data.

## Product identity and servers

- The test APK must install beside Gregor's original app. Use application ID `at.gregor.layermaxxing.gerfried` and visible label `GS Layermaxxing Gerfried` (or `GS Layermaxxing (Gerfried-Test)`). Keep the Kotlin namespace/package unchanged unless required.
- Add two runtime-selectable server profiles:
  - `Gregor (Original)` using BuildConfig URL `GREGOR_API_BASE_URL`.
  - `Gerfried (Testserver)` using BuildConfig URL `GERFRIED_API_BASE_URL`.
- Build URLs come from environment variables `LAYERMAXXING_GREGOR_URL` and `LAYERMAXXING_GERFRIED_URL`. The Gerfried build defaults to `https://layermaxxing.derkellner.duckdns.org/`. The production URL may remain `https://example.invalid/` when no production URL is supplied; show it as not configured rather than silently connecting.
- Store the selected profile in SharedPreferences. A profile change clears the bearer token and returns to login.
- When Gerfried/test is selected, show an unmistakable persistent red/orange banner on authentication and app screens: `⚠ TESTSERVER VON GERFRIED – NUR ZUM AUSPROBIEREN`. Show a confirmation dialog once per selection. Do not show this warning for a verified production-role server.
- Backend: add `GET /api/server-info` returning name, role (`production` or `test`), version, warning and supported feature flags. Add `X-Layermaxxing-Role` response header. Environment: `SERVER_NAME`, `SERVER_ROLE`.

## Cryptographic evidence for locked letters

Threat model remains explicit: this is a server-enforced time lock, not a time-lock puzzle. The server currently stores the AES release key. Improve tamper evidence without claiming the server cannot read locked content.

For every new letter:

1. Normalize the secret body to NFC and UTF-8.
2. Generate 32 random bytes `commitment_salt`.
3. Compute `plaintext_sha256 = SHA-256(plaintext)` and `commitment = SHA-256("GS-LM-COMMIT-V1" || commitment_salt || canonical metadata || plaintext bytes)`.
4. Generate a per-letter ECDSA P-256 signing keypair on the client (Android API 26 compatible).
5. Encrypt using AES-256-GCM with an explicit 12-byte random nonce and canonical metadata as AAD. Attachments use the same AES key but a distinct explicit 12-byte nonce and attachment-specific AAD.
6. Sign `SHA-256("GS-LM-SEAL-V1" || canonical metadata || commitment || ciphertext || nonce)` with the per-letter private signing key.
7. Upload/store the commitment, SHA-256, public signing key, signature, salt, protocol version and canonical metadata together with the existing encrypted content. The private signing key is never uploaded or disclosed; disclosing it would allow forged signatures and destroy the proof. The later disclosed secret is the AES release/decryption key, which is already the current release mechanism.

The server validates field formats and stores the evidence. It cannot recompute the plaintext hash before release, but after release the client and offline verifier can decrypt and verify AES-GCM, plaintext SHA-256, salted commitment and ECDSA signature.

- Long-pressing a locked letter opens a verification dialog with:
  - protocol version;
  - salted commitment (`Versiegelungs-Hash`);
  - plaintext hash clearly marked as potentially guessable for short messages;
  - public verification key fingerprint and copyable key;
  - signature;
  - release rule/timestamps;
  - `Öffentliche Prüfdatei exportieren`.
- After release, the same view also shows the disclosed AES release-key fingerprint and local verification result. Never label the AES key as a signing private key.
- Add endpoints for one proof and all pending proofs for a participant. Export format: JSON with `format: gs-layermaxxing-verification`, `version: 1`, message metadata and evidence. One message and all pending letters must be exportable from Android via `ACTION_CREATE_DOCUMENT`/share sheet as `.gsverify.json`.
- Legacy messages remain readable and are labelled `Legacy – ohne kryptografischen Nachweis`.
- Keep an offline Python verifier under `server/tools/verify_export.py` with tests.

## Friendship settings: bilateral only

- Materialize accepted friendships and default settings:
  - letters enabled;
  - chats enabled;
  - EP disabled;
  - optional minimum letter delay 0 seconds.
- Settings cannot be directly edited. A user can only create a proposal (styled as a letter). The other friendship member can accept or reject; only acceptance atomically applies it. The proposer cannot accept their own proposal. One pending proposal per friendship.
- Add list/create/respond/withdraw endpoints and Android screens/cards for current settings, outgoing proposal and incoming proposal.
- Removing/blocking a friend closes pending settings proposals.

## EP — Ebenen-Punkte

- EP is available only after both users accepted a friendship-settings proposal enabling it.
- A user may propose 1–1000 EP for the other person (not themselves) with required title and optional description. The other person must accept; pending/rejected proposals never count.
- An EP proposal may link to a letter between the same two users. Preserve the link in history (nullable if the letter is deleted).
- After sending a letter and after opening/releasing one, offer a non-blocking prompt: `Hat dieser Brief zu EP geführt?` with the likely beneficiary preselected but editable.
- Screens show incoming/outgoing pending EP, accepted history, totals given/received and playful levels. Use labels `EP` and `Ebenen-Punkte (wie Experience Points, nur auf mehreren Ebenen)`.

## Letter and chat UX

- Letter compose must look/feel like a letter with a seal. Clearly split:
  - an open, unencrypted description/cover note (`Öffentlicher Umschlagtext`);
  - a clearly framed secret encrypted body (`Versiegelter Inhalt`).
- Compose starts either from a friend/contact card (recipient preselected) or via a recipient dropdown. Keep group support where practical.
- Add ordinary friendship chat as a separate `instant` message class. It is still AES-GCM encrypted in transit/storage but released immediately, has no title/release controls, and is shown in a thread UI, not the letter inbox/outbox.
- Random release windows use numeric value + units `Minuten`, `Stunden`, `Tage` for both bounds; minimum valid window is 1 minute. Convert to absolute UTC epoch seconds and keep the server as source of truth for the chosen random release instant.
- Existing modes remain: timed, random, manual, mutual approval, simultaneous presence. Explain in UI:
  - AES-256-GCM protects ciphertext integrity and confidentiality in storage/network.
  - the server holds the AES key until the release condition; therefore the server operator can technically access locked content;
  - timed/random/manual/mutual/presence differ only in the server-side key-release gate;
  - presence is server-attested liveness, not explicit consent;
  - one-time is app/server policy and cannot prevent screenshots/caching.

## Backend schema/API expectations

Add idempotent SQLite migrations without losing existing databases. Avoid positional `INSERT INTO messages SELECT *` in migrations; use explicit columns. Add the necessary tables/columns for:

- server identity metadata (environment only);
- friendships and friendship settings;
- friendship-setting proposals;
- EP proposals/history;
- message class (`letter` vs `instant`) or `instant` mode;
- public cover note;
- evidence/proof fields;
- chat listing/thread read state.

Require accepted, unblocked friendship and enabled feature for letters/chats/EP. Preserve all existing endpoints and tests.

## Tests and build gates

Use strict vertical TDD. Required final commands:

```bash
PYTHONPATH=server .venv/bin/pytest -q server/tests
cd android
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Add focused server tests for role identity, migrations, bilateral settings, EP approval/link validation, chat separation, proof/export validation and minute-scale random windows. Add JVM unit tests for canonical metadata, hashes/commitments, explicit nonces/AAD round trips, signatures, server profile persistence/model logic and export serialization.

No fake output, no test deletion, no blanket lint suppression. Keep secrets and real production URLs out of git.
