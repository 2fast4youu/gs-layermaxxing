"""Private test-only shake PNG receipts; no image delivery or cross-owner listing."""
import asyncio
import contextlib
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import tempfile
import time
import uuid
import zlib

from fastapi import Depends, HTTPException, Request
from starlette.concurrency import run_in_threadpool

MAX_BYTES = 8 * 1024 * 1024
MAX_PIXELS = 2_000_000
MAX_META = 64 * 1024
KEY = b"gs-shake-diagnostics\0\0\0\0\0"
SIGNATURE = b"\x89PNG\r\n\x1a\n"
TTL = 7 * 86400
USER_LIMIT = 20
GLOBAL_BYTES = 128 * 1024 * 1024
GLOBAL_COUNT = 200


def validate_png(data: bytes) -> str:
    """Validate CRC, ordering, bounded uncompressed metadata and actual scanline stream."""
    if len(data) > MAX_BYTES or not data.startswith(SIGNATURE):
        raise ValueError("PNG required")
    pos = 8
    chunks = 0
    meta = None
    width = height = channels = 0
    idat = bytearray()
    ended_idat = False
    seen_idat = False
    while pos < len(data):
        if pos + 12 > len(data):
            raise ValueError("Truncated chunk")
        size, kind = struct.unpack_from(">I4s", data, pos)
        if size > MAX_BYTES or pos + size + 12 > len(data):
            raise ValueError("Chunk size")
        payload = data[pos + 8:pos + 8 + size]
        crc = struct.unpack_from(">I", data, pos + 8 + size)[0]
        if zlib.crc32(kind + payload) & 0xffffffff != crc:
            raise ValueError("CRC")
        chunks += 1
        if chunks > 2048:
            raise ValueError("Too many chunks")
        if chunks == 1 and kind != b"IHDR":
            raise ValueError("IHDR first")
        if kind == b"IHDR":
            if chunks != 1 or size != 13:
                raise ValueError("IHDR")
            width, height, depth, color, compression, filtering, interlace = struct.unpack(">IIBBBBB", payload)
            channels = {0: 1, 2: 3, 4: 2, 6: 4}.get(color, 0)
            if not width or not height or width * height > MAX_PIXELS or width > 8192 or height > 8192 or not channels or (depth, compression, filtering, interlace) != (8, 0, 0, 0):
                raise ValueError("Dimensions/format")
        elif kind == b"IDAT":
            if ended_idat:
                raise ValueError("IDAT order")
            seen_idat = True
            idat.extend(payload)
        elif kind == b"iTXt":
            if meta is not None or size > MAX_META + len(KEY) or not payload.startswith(KEY):
                raise ValueError("Metadata")
            meta = payload[len(KEY):].decode("utf-8", errors="strict")
            if not meta.startswith("Shake report v1\n") or any(ord(c) < 32 and c not in "\n\t" for c in meta):
                raise ValueError("Metadata text")
        elif kind == b"IEND":
            if size or pos + 12 != len(data) or meta is None or not seen_idat:
                raise ValueError("IEND")
            raw_size = height * (1 + width * channels)
            decoder = zlib.decompressobj()
            raw = decoder.decompress(bytes(idat), raw_size + 1)
            if len(raw) != raw_size or not decoder.eof or decoder.unused_data or decoder.unconsumed_tail:
                raise ValueError("Pixel stream")
            stride = 1 + width * channels
            if any(raw[i] > 4 for i in range(0, len(raw), stride)):
                raise ValueError("Pixel filter")
            return meta
        elif kind in (b"sRGB", b"gAMA", b"cHRM", b"pHYs", b"sBIT"):
            expected = {b"sRGB": 1, b"gAMA": 4, b"cHRM": 32, b"pHYs": 9, b"sBIT": channels}[kind]
            if size != expected:
                raise ValueError("Ancillary chunk size")
        else:
            # No compressed text, profiles, APNG, arbitrary ancillary or unknown critical data.
            raise ValueError("Unsupported chunk")
        if seen_idat and kind != b"IDAT":
            ended_idat = True
        pos += size + 12
    raise ValueError("Missing IEND")


class ReportStore:
    def __init__(self, directory: Path):
        self.directory = directory

    @contextlib.contextmanager
    def locked(self):
        self.directory.mkdir(mode=0o700, parents=True, exist_ok=True)
        self.directory.chmod(0o700)
        with os.fdopen(os.open(self.directory / ".lock", os.O_CREAT | os.O_RDWR, 0o600), "a") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX)
            yield

    def remove(self, report_id):
        for suffix in (".png", ".json"):
            (self.directory / (report_id + suffix)).unlink(missing_ok=True)

    def prune(self):
        rows = []
        for pending in self.directory.glob(".pending-*"):
            pending.unlink(missing_ok=True)
        for path in self.directory.glob("*.json"):
            try:
                row = json.loads(path.read_text())
                if row["created"] + TTL <= time.time() or not path.with_suffix(".png").is_file():
                    self.remove(path.stem)
                else:
                    rows.append(row)
            except (OSError, ValueError, KeyError, TypeError):
                self.remove(path.stem)
        # Clean orphaned PNG/temp files after interrupted process, never an active upload (outside directory).
        known = {r["id"] for r in rows}
        for path in self.directory.glob("*.png"):
            if path.stem not in known:
                path.unlink(missing_ok=True)
        return sorted(rows, key=lambda r: (r["created"], r["id"]))

    def put(self, source: Path, owner: int):
        with source.open("rb") as input_file:
            data = input_file.read(MAX_BYTES + 1)  # independent cap at the storage boundary
        validate_png(data)
        row = {"id": str(uuid.uuid4()), "owner": owner, "created": time.time(),
               "sha256": hashlib.sha256(data).hexdigest(), "bytes": len(data)}
        with self.locked():
            rows = self.prune()
            own = [r for r in rows if r["owner"] == owner]
            while len(own) >= USER_LIMIT:
                old = own.pop(0); self.remove(old["id"]); rows.remove(old)
            while rows and (len(rows) >= GLOBAL_COUNT or sum(r["bytes"] for r in rows) + len(data) > GLOBAL_BYTES):
                self.remove(rows.pop(0)["id"])
            png = self.directory / (row["id"] + ".png")
            metadata = png.with_suffix(".json")
            try:
                for target, content in ((png, data), (metadata, json.dumps(row).encode())):
                    fd, name = tempfile.mkstemp(prefix=".pending-", dir=self.directory)
                    try:
                        with os.fdopen(fd, "wb") as output:
                            output.write(content); output.flush(); os.fsync(output.fileno())
                        os.replace(name, target)
                    finally:
                        Path(name).unlink(missing_ok=True)
            except BaseException:
                self.remove(row["id"])
                raise
        return self.receipt(row)

    @staticmethod
    def receipt(row):
        return {key: row[key] for key in ("id", "sha256", "bytes")}

    def get(self, report_id, owner, delete=False):
        if not re.fullmatch(r"[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}", report_id):
            raise HTTPException(404, "Report unavailable")
        with self.locked():
            self.prune()
            try:
                row = json.loads((self.directory / (report_id + ".json")).read_text())
            except (OSError, ValueError):
                raise HTTPException(404, "Report unavailable")
            if row["owner"] != owner:
                raise HTTPException(404, "Report unavailable")
            # Status verifies the bytes on disk, not just the POST receipt.
            with (self.directory / (report_id + ".png")).open("rb") as input_file:
                data = input_file.read(MAX_BYTES + 1)
            if len(data) != row["bytes"] or hashlib.sha256(data).hexdigest() != row["sha256"]:
                raise HTTPException(409, "Stored report verification failed")
            if delete:
                self.remove(report_id)
            return self.receipt(row)


def install(app, current_user, role, db_path):
    uploads = asyncio.Semaphore(2)
    def test_only():
        if role() != "test":
            raise HTTPException(404, "Report unavailable")

    def store():
        return ReportStore(db_path().parent / "shake-reports")

    def cleanup():
        reports = store()
        if role() == "test" and reports.directory.is_dir():
            with reports.locked():
                reports.prune()

    async def maintenance():
        while True:
            try:
                await run_in_threadpool(cleanup)
            except OSError:
                pass  # A storage outage must not terminate the API; next sweep tries again.
            await asyncio.sleep(60)

    previous_lifespan = app.router.lifespan_context

    @contextlib.asynccontextmanager
    async def lifetime(application):
        async with previous_lifespan(application):
            task = asyncio.create_task(maintenance()) if role() == "test" else None
            try:
                yield
            finally:
                if task:
                    task.cancel()
                    with contextlib.suppress(asyncio.CancelledError):
                        await task

    app.router.lifespan_context = lifetime

    @app.post("/api/debug-reports", status_code=201, dependencies=[Depends(test_only)])
    async def upload(request: Request, user=Depends(current_user)):
        if request.headers.get("content-type", "").lower() != "image/png":
            raise HTTPException(415, "image/png required")
        declared = request.headers.get("content-length")
        if declared and (len(declared) > 10 or not declared.isdecimal() or int(declared) > MAX_BYTES):
            raise HTTPException(413, "Report too large")
        if uploads.locked():
            raise HTTPException(429, "Report upload busy")
        async with uploads:
            # TemporaryFile follows TMPDIR; never retain unvalidated request bytes.
            with tempfile.TemporaryDirectory(prefix="shake-upload-") as temporary:
                path = Path(temporary) / "upload.png"
                size = 0
                with path.open("wb") as output:
                    path.chmod(0o600)
                    async for chunk in request.stream():
                        size += len(chunk)
                        if size > MAX_BYTES:
                            raise HTTPException(413, "Report too large")
                        output.write(chunk)
                try:
                    return await run_in_threadpool(store().put, path, int(user["id"]))
                except (ValueError, UnicodeError, zlib.error):
                    raise HTTPException(422, "Invalid shake PNG")
                except OSError:
                    raise HTTPException(507, "Report storage unavailable")

    @app.get("/api/debug-reports/{report_id}", dependencies=[Depends(test_only)])
    def status(report_id: str, user=Depends(current_user)):
        try:
            return store().get(report_id, int(user["id"]))
        except OSError:
            raise HTTPException(507, "Report storage unavailable")

    @app.delete("/api/debug-reports/{report_id}", dependencies=[Depends(test_only)])
    def delete(report_id: str, user=Depends(current_user)):
        try:
            return store().get(report_id, int(user["id"]), delete=True)
        except OSError:
            raise HTTPException(507, "Report storage unavailable")
