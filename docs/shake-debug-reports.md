# Shake screenshot diagnostics — candidate 83

Implemented source candidate: Android `83` / `7.9.14-shake-debug1`. No builds, tests,
network checks, live server changes or deployment were run for this change.

## Consent and use

Open **Mehr → Fehlerbericht / Log kopieren** in an authenticated, unlocked app.
Both switches default OFF. Enable **Lokal bei Schütteln speichern** to capture the
app's visible activity window after three acceleration peaks (2.4g, separated by a
return below 1.6g, at least 160ms apart within 1.2s). All captures share a 12-second
cooldown and one capture/upload at a time. If a sensor is absent, use **App jetzt
aufnehmen**; it closes the dialog before drawing. Sensor/IO failures do not crash
the app. The saved/uploaded/error state is readable in this dialog and persisted.

**Auto-Upload · Gerfried TESTserver** requires its own confirmation. Screenshots
can contain visible letters, chat text and other private material. Only embedded
technical diagnostics are sanitized; pixels are not redacted. Turning off either
switch cancels pending work; turning off local capture also revokes upload consent.
Cancellation cannot recall bytes already accepted by a server. No automatic retry,
background upload, Telegram send, public gallery, sharing broadcast or new
permission is introduced. Previously stored server reports require authenticated
owner deletion or expiry; local delete does not delete server copies.

Upload is restricted to **ServerProfile.GERFRIED**, its exact compiled HTTPS base
URL, and role `test`. The server role is checked again immediately before POST.
Gregor original and Gregor TEST profiles cannot upload. Existing profile URLs,
authentication and profile migration rules are unchanged. In particular the
existing `/test-gerfried/` Gregor TEST endpoint is NOT silently substituted for
Gerfried's own TEST profile. An unreachable/unconfirmed/production-role endpoint
leaves a private local file and a notice. Root must deploy the new route to the
intended **Gerfried** TEST backend and validate it before phone delivery.

## Android dataflow and storage

The activity owns `ShakeReports`. Only a resumed authenticated/unlocked current
session binds it. Pause, disposal, login/account/profile transitions and preference
revocation invalidate a generation and cancel the coroutine/OkHttp call. The
session preferences listener also invalidates before a delayed Compose update.
Power/keyguard state and account/token/profile equality are checked during work
and before accepting results. FLAG_SECURE is honored; login/biometric gates are
never captured. There is no whole-phone capture or MediaProjection.

On Main, the activity's own `decorView` is drawn into an ARGB bitmap, scaled to at
most 1.5 million pixels and 8192 per dimension (at most 6MB raw pixel allocation).
PNG encoding, diagnosis collection, file operations and hashing run on IO. This
captures regular view/Compose content, not external windows or a separate IME;
SurfaceView/video layers require device validation. PNG compression is lossless
for this bounded bitmap; unusually large screens are downscaled before encoding.

A single PNG contains uncompressed UTF-8 `iTXt` with keyword
`gs-shake-diagnostics`, compression flag/method zero and empty language/translated
keyword. Its text begins `Shake report v1\n`. Standard PNG parsers can extract it;
`ShakePng.extract` is the bounded CRC-checking reader. There is no diagnostics
sidecar. Metadata is at most 64KiB; the entire PNG is at most 8MiB. CRC, chunk sizes,
IHDR dimensions, IDAT presence and terminal IEND are checked before embedding and
on extraction. Encoding has a bounded output stream; failures leave no successful
receipt. Peak transient memory also includes bounded PNG byte buffers/copies.

Diagnostics reuse `CrashDiagnostics`/`CrashFormatter`: version/build/API/model,
memory, safe tab/island phase/count/grid breadcrumbs, current and previous run,
last Java crash (exception classes and allowlisted stack frames, no exception
messages), and bounded OS exit reason codes. No request URLs/query strings,
auth values, tokens, keys, message bodies or free-form UI text are collected as
metadata. The existing local crash-report copy/delete/startup paths remain; their
wording now discloses that explicitly enabled shake reports can transmit this
technical data. Screenshot content is governed by separate informed consent.

Files are UUID-named under app-private `files/shake-reports`, atomically renamed,
with deterministic oldest-first retention of at most 5 PNGs and 30MiB. The existing
`allowBackup=false` remains. Local delete cancels work and removes these files.
No FileProvider path, manifest permission, scene/envelope art or grid rule changed.

## Authenticated TEST backend contract

- `POST /api/debug-reports`: current-user bearer auth, raw `image/png`, streamed
  bound 8MiB (also checked if Content-Length is absent). Returns 201 with
  `{id, sha256, bytes}`. At most two concurrent uploads per API worker; excess
  receives 429. No base64 JSON, request-content logging or schema migration.
- `GET /api/debug-reports/{uuid}`: owner auth only, same receipt fields. Server
  hashes the stored PNG again. Other owners and invalid IDs receive 404. No image
  download route or cross-user listing is provided.
- `DELETE /api/debug-reports/{uuid}`: authenticated owner deletion. It returns
  the deleted report's receipt. Other owners receive 404.
- These routes return 404 on production role. Missing/invalid auth uses the
  existing 401 path. Wrong media type 415, oversized body 413, invalid PNG 422,
  upload storage failure 507, corrupted stored report 409.

Strict server validation checks signature, CRC of every chunk, first/unique IHDR,
noninterlaced 8-bit grayscale/RGB/gray-alpha/RGBA dimensions ≤2 million pixels and
8192 per axis, contiguous IDAT, one uncompressed metadata chunk, terminal IEND and
bounded *actual* zlib scanline output/filter bytes. Compressed metadata/profiles,
APNG, unknown chunks, trailing data, duplicate metadata and malformed pixel streams
are rejected. Diagnostics text is bounded UTF-8 with a version marker and no
control characters except newline/tab; redaction happens in the app formatter.

Private storage is `DB_PATH.parent / shake-reports` (normally
`/data/shake-reports`), outside the checkout with the deployed `/data` database.
Do not point DB_PATH inside the checkout. Directory mode is 0700; PNG and minimal
JSON receipt/owner metadata are 0600. Server UUIDs determine filenames. PNG and
JSON use fsync and atomic rename; JSON is the publication marker and orphan PNGs
are reclaimed under an inter-process flock. Interrupted staging files are cleaned.
Request staging follows TMPDIR and is removed on every exit.

Reports expire after 7 days. Owner reads and writes prune expired files, and a
TEST-only lifespan task sweeps every 60 seconds while the API is running. A stopped
server/storage outage can delay physical reclamation; expired reports cannot be
read once it resumes. Limits are 20 reports per owner, 200 globally and 128MiB PNG
bytes globally; oldest records are evicted first. No live cron or service changes
are needed or made. Original API permissions and existing DB data are unchanged.

Client success requires matching local SHA-256/length against BOTH POST and
owner-only GET receipts, matching IDs and unchanged file digest/session/consent
at the end. This is stronger than accepting POST alone. Redirects and connection
replays are disabled for this path; underlying calls have cancellation and a total
deadline. Any failure retains the local report. Manual retry is not implemented.

## Validation for Root — not executed by this author

Focused files:
`ShakeReportCoreTest`, `ShakeReportPngTest`, `ShakeReportApiTest`,
`ShakeReportLifecycleTest`, `ShakeReportUiTest`, and
`server/tests/test_debug_reports.py`. API tests use terminating in-memory OkHttp
interceptors and fail-before-DNS fixtures; backend tests use only tmp_path DBs.

With existing dependencies/SDK configured, Root can run:

```sh
PYTHONPATH=server .venv/bin/pytest -q server/tests/test_debug_reports.py
cd android
./gradlew --no-daemon testDebugUnitTest --tests '*ShakeReport*Test' --tests '*CrashDiagnostics*Test'
```

Then run the complete existing hard gates in `.github/workflows/postal-ui-test.yml`:
backend regression; all old focused filters plus `*ShakeReport*Test`; lint and APK;
full Android regression without suppressed failures. Workflow APK manifest must
be 83 / `7.9.14-shake-debug1`, artifact `postal-ui-test-apk-83`. All compiled
CURRENT=7, NEWEST=8, CONSTANT=8, shared geometry and signer checks remain.

Device acceptance remains required: three real shakes/cooldown; manual capture on
sensorless device; background/lock/login/FLAG_SECURE refusal; off/consent revoke;
account switch mid-POST/readback; offline/full disk recovery; actual PNG metadata
extraction and visible content; Gerfried TEST role/auth; readback digest; owner
isolation/deletion; retention and successful compatible update install. No device,
compiled artifact, backend deployment or end-to-end pass is claimed here.
