from __future__ import annotations

import base64
import hashlib
import os
import secrets
import sqlite3
from contextlib import asynccontextmanager, contextmanager
from datetime import datetime, timezone
from pathlib import Path

from fastapi import Depends, FastAPI, Header, HTTPException, Query, status
from pydantic import BaseModel, Field, field_validator

DB_PATH = Path(os.getenv("DB_PATH", "/data/layermaxxing.db"))
MAX_RELEASE_SECONDS = 365 * 24 * 60 * 60
MANUAL_RELEASE_SENTINEL = 2_147_483_647
ONLINE_SECONDS = 90
MAX_ATTACHMENT_B64 = 3_000_000
VALID_MODES = {"timed", "manual", "mutual", "presence", "random"}


def now_ts() -> int:
    return int(datetime.now(timezone.utc).timestamp())


def token_hash(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


def code_hash(code: str) -> str:
    return hashlib.sha256(code.replace("-", "").upper().encode()).hexdigest()


def create_password_hash(password: str) -> tuple[str, str]:
    salt = secrets.token_bytes(16)
    derived = hashlib.scrypt(password.encode(), salt=salt, n=2**14, r=8, p=1, dklen=32)
    return base64.b64encode(salt).decode(), base64.b64encode(derived).decode()


def verify_password(password: str, salt_b64: str | None, hash_b64: str | None) -> bool:
    if not salt_b64 or not hash_b64:
        return False
    actual = hashlib.scrypt(password.encode(), salt=base64.b64decode(salt_b64), n=2**14, r=8, p=1, dklen=32)
    return secrets.compare_digest(actual, base64.b64decode(hash_b64))


def recovery_code() -> str:
    raw = secrets.token_hex(10).upper()
    return "-".join(raw[i:i + 5] for i in range(0, len(raw), 5))


@contextmanager
def db():
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys=ON")
    try:
        yield conn
        conn.commit()
    finally:
        conn.close()


def add_columns(conn: sqlite3.Connection, table: str, definitions: dict[str, str]) -> None:
    columns = {row["name"] for row in conn.execute(f"PRAGMA table_info({table})")}
    for name, definition in definitions.items():
        if name not in columns:
            conn.execute(f"ALTER TABLE {table} ADD COLUMN {name} {definition}")


def initialize_database() -> None:
    with db() as conn:
        conn.executescript("""
        PRAGMA journal_mode=WAL;
        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE COLLATE NOCASE,
            password_salt TEXT, password_hash TEXT, created_at INTEGER NOT NULL
        );
        CREATE TABLE IF NOT EXISTS sessions (
            token_hash TEXT PRIMARY KEY, user_id INTEGER NOT NULL, created_at INTEGER NOT NULL,
            FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
        );
        CREATE TABLE IF NOT EXISTS friend_requests (
            id INTEGER PRIMARY KEY AUTOINCREMENT, sender_id INTEGER NOT NULL, recipient_id INTEGER NOT NULL,
            status TEXT NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','accepted','rejected')),
            created_at INTEGER NOT NULL, responded_at INTEGER,
            FOREIGN KEY(sender_id) REFERENCES users(id) ON DELETE CASCADE,
            FOREIGN KEY(recipient_id) REFERENCES users(id) ON DELETE CASCADE
        );
        CREATE INDEX IF NOT EXISTS idx_friend_sender ON friend_requests(sender_id,status);
        CREATE INDEX IF NOT EXISTS idx_friend_recipient ON friend_requests(recipient_id,status);
        """)
        legacy = conn.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name='devices'").fetchone()
        if legacy:
            conn.execute("INSERT OR IGNORE INTO users(id,name,password_salt,password_hash,created_at) SELECT id,name,NULL,NULL,created_at FROM devices")
            conn.execute("INSERT OR IGNORE INTO sessions(token_hash,user_id,created_at) SELECT token_hash,id,created_at FROM devices")

        add_columns(conn, "users", {
            "avatar_emoji": "TEXT NOT NULL DEFAULT '🔐'", "display_color": "TEXT NOT NULL DEFAULT '#6750A4'",
            "discoverable": "INTEGER NOT NULL DEFAULT 1", "recovery_hash": "TEXT",
            "failed_logins": "INTEGER NOT NULL DEFAULT 0", "locked_until": "INTEGER",
        })
        add_columns(conn, "sessions", {
            "device_name": "TEXT NOT NULL DEFAULT 'Unbekanntes Gerät'", "last_seen_at": "INTEGER NOT NULL DEFAULT 0",
            "push_token": "TEXT",
        })
        conn.executescript("""
        CREATE TABLE IF NOT EXISTS messages (
            id INTEGER PRIMARY KEY AUTOINCREMENT, sender_id INTEGER NOT NULL, recipient_id INTEGER NOT NULL,
            ciphertext TEXT NOT NULL, nonce TEXT NOT NULL, encryption_key TEXT NOT NULL,
            release_at INTEGER NOT NULL, created_at INTEGER NOT NULL,
            manual_release INTEGER NOT NULL DEFAULT 0, released_at INTEGER,
            FOREIGN KEY(sender_id) REFERENCES users(id) ON DELETE CASCADE,
            FOREIGN KEY(recipient_id) REFERENCES users(id) ON DELETE CASCADE
        );
        CREATE INDEX IF NOT EXISTS idx_messages_recipient ON messages(recipient_id,created_at DESC);
        CREATE INDEX IF NOT EXISTS idx_messages_sender ON messages(sender_id,created_at DESC);
        """)
        add_columns(conn, "messages", {
            "manual_release": "INTEGER NOT NULL DEFAULT 0", "released_at": "INTEGER",
            "title": "TEXT NOT NULL DEFAULT ''", "mode": "TEXT NOT NULL DEFAULT 'timed'",
            "one_time": "INTEGER NOT NULL DEFAULT 0", "read_at": "INTEGER", "sender_approved": "INTEGER NOT NULL DEFAULT 0",
            "recipient_approved": "INTEGER NOT NULL DEFAULT 0", "random_from": "INTEGER", "random_to": "INTEGER",
            "attachment_name": "TEXT", "attachment_mime": "TEXT", "attachment_ciphertext": "TEXT",
            "attachment_nonce": "TEXT", "group_id": "INTEGER",
        })
        conn.execute(
            "UPDATE messages SET mode='manual' WHERE manual_release=1 AND mode='timed' AND release_at=?",
            (MANUAL_RELEASE_SENTINEL,),
        )
        conn.executescript("""
        CREATE TABLE IF NOT EXISTS blocks (
            blocker_id INTEGER NOT NULL, blocked_id INTEGER NOT NULL, created_at INTEGER NOT NULL,
            PRIMARY KEY(blocker_id,blocked_id),
            FOREIGN KEY(blocker_id) REFERENCES users(id) ON DELETE CASCADE,
            FOREIGN KEY(blocked_id) REFERENCES users(id) ON DELETE CASCADE
        );
        CREATE TABLE IF NOT EXISTS message_reactions (
            message_id INTEGER NOT NULL, user_id INTEGER NOT NULL, emoji TEXT NOT NULL, created_at INTEGER NOT NULL,
            PRIMARY KEY(message_id,user_id),
            FOREIGN KEY(message_id) REFERENCES messages(id) ON DELETE CASCADE,
            FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
        );
        CREATE TABLE IF NOT EXISTS groups (
            id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, owner_id INTEGER NOT NULL, created_at INTEGER NOT NULL,
            FOREIGN KEY(owner_id) REFERENCES users(id) ON DELETE CASCADE
        );
        CREATE TABLE IF NOT EXISTS group_members (
            group_id INTEGER NOT NULL, user_id INTEGER NOT NULL, PRIMARY KEY(group_id,user_id),
            FOREIGN KEY(group_id) REFERENCES groups(id) ON DELETE CASCADE,
            FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
        );
        """)

    # V1 message tables referenced devices. Rebuild once without losing rows.
    raw = sqlite3.connect(DB_PATH)
    try:
        foreign_keys = list(raw.execute("PRAGMA foreign_key_list(messages)"))
        if any(row[2] == "devices" for row in foreign_keys):
            raw.execute("PRAGMA foreign_keys=OFF")
            raw.executescript("""
            DROP INDEX IF EXISTS idx_messages_recipient; DROP INDEX IF EXISTS idx_messages_sender;
            ALTER TABLE messages RENAME TO messages_legacy_device_fk;
            CREATE TABLE messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT, sender_id INTEGER NOT NULL, recipient_id INTEGER NOT NULL,
                ciphertext TEXT NOT NULL, nonce TEXT NOT NULL, encryption_key TEXT NOT NULL,
                release_at INTEGER NOT NULL, created_at INTEGER NOT NULL, manual_release INTEGER NOT NULL DEFAULT 0,
                released_at INTEGER, title TEXT NOT NULL DEFAULT '', mode TEXT NOT NULL DEFAULT 'timed',
                one_time INTEGER NOT NULL DEFAULT 0, read_at INTEGER, sender_approved INTEGER NOT NULL DEFAULT 0,
                recipient_approved INTEGER NOT NULL DEFAULT 0, random_from INTEGER, random_to INTEGER,
                attachment_name TEXT, attachment_mime TEXT, attachment_ciphertext TEXT, attachment_nonce TEXT, group_id INTEGER,
                FOREIGN KEY(sender_id) REFERENCES users(id) ON DELETE CASCADE,
                FOREIGN KEY(recipient_id) REFERENCES users(id) ON DELETE CASCADE
            );
            INSERT INTO messages SELECT * FROM messages_legacy_device_fk;
            DROP TABLE messages_legacy_device_fk;
            CREATE INDEX idx_messages_recipient ON messages(recipient_id,created_at DESC);
            CREATE INDEX idx_messages_sender ON messages(sender_id,created_at DESC);
            """)
    finally:
        raw.close()


@asynccontextmanager
async def lifespan(_: FastAPI):
    initialize_database()
    yield


app = FastAPI(title="GS Layermaxxing", version="3.0.0", docs_url=None, redoc_url=None, lifespan=lifespan)


class Credentials(BaseModel):
    name: str = Field(min_length=1, max_length=40)
    password: str = Field(min_length=8, max_length=128)
    device_name: str = Field(default="Android", min_length=1, max_length=80)

    @field_validator("name", "device_name")
    @classmethod
    def clean(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("Darf nicht leer sein")
        return value


class AuthResponse(BaseModel):
    token: str
    user_id: int
    name: str
    recovery_code: str | None = None


class PasswordOnly(BaseModel):
    password: str = Field(min_length=8, max_length=128)


class PasswordChange(BaseModel):
    old_password: str
    new_password: str = Field(min_length=8, max_length=128)


class RecoveryReset(BaseModel):
    name: str
    recovery_code: str
    new_password: str = Field(min_length=8, max_length=128)
    device_name: str = "Android"


class ProfileUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=40)
    avatar_emoji: str | None = Field(default=None, min_length=1, max_length=8)
    display_color: str | None = Field(default=None, pattern=r"^#[0-9A-Fa-f]{6}$")
    discoverable: bool | None = None


class FriendRequestCreate(BaseModel):
    recipient_id: int


class FriendRequestRespond(BaseModel):
    accept: bool


class ReactionCreate(BaseModel):
    emoji: str = Field(min_length=1, max_length=8)


class PushTokenCreate(BaseModel):
    token: str = Field(min_length=8, max_length=4096)


class GroupCreate(BaseModel):
    name: str = Field(min_length=1, max_length=60)
    member_ids: list[int] = Field(min_length=1, max_length=20)


class MessageCreate(BaseModel):
    recipient_id: int | None = None
    recipient_ids: list[int] = Field(default_factory=list, max_length=20)
    group_id: int | None = None
    ciphertext: str = Field(min_length=1, max_length=100_000)
    nonce: str = Field(min_length=1, max_length=256)
    encryption_key: str = Field(min_length=1, max_length=256)
    title: str = Field(default="", max_length=100)
    mode: str = "timed"
    release_at: int | None = None
    manual_release: bool = False
    random_from: int | None = None
    random_to: int | None = None
    one_time: bool = False
    attachment_name: str | None = Field(default=None, max_length=160)
    attachment_mime: str | None = Field(default=None, max_length=120)
    attachment_ciphertext: str | None = Field(default=None, max_length=MAX_ATTACHMENT_B64)
    attachment_nonce: str | None = Field(default=None, max_length=256)


def issue_token(conn: sqlite3.Connection, user_id: int, device_name: str) -> str:
    token = secrets.token_urlsafe(32)
    current = now_ts()
    conn.execute("INSERT INTO sessions(token_hash,user_id,created_at,device_name,last_seen_at) VALUES (?,?,?,?,?)",
                 (token_hash(token), user_id, current, device_name, current))
    return token


def current_user(authorization: str | None = Header(default=None)) -> sqlite3.Row:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(401, "Token fehlt")
    hashed = token_hash(authorization[7:].strip())
    with db() as conn:
        row = conn.execute("""SELECT u.*,s.rowid AS session_id,s.token_hash AS current_token_hash
                              FROM sessions s JOIN users u ON u.id=s.user_id WHERE s.token_hash=?""", (hashed,)).fetchone()
        if row:
            conn.execute("UPDATE sessions SET last_seen_at=? WHERE token_hash=?", (now_ts(), hashed))
    if not row:
        raise HTTPException(401, "Token ungültig")
    return row


def blocked(conn: sqlite3.Connection, first: int, second: int) -> bool:
    return conn.execute("SELECT 1 FROM blocks WHERE (blocker_id=? AND blocked_id=?) OR (blocker_id=? AND blocked_id=?)",
                        (first, second, second, first)).fetchone() is not None


def are_friends(conn: sqlite3.Connection, first: int, second: int) -> bool:
    return conn.execute("""SELECT 1 FROM friend_requests WHERE status='accepted' AND
                        ((sender_id=? AND recipient_id=?) OR (sender_id=? AND recipient_id=?)) LIMIT 1""",
                        (first, second, second, first)).fetchone() is not None


def user_view(row: sqlite3.Row, relationship: str = "none") -> dict:
    return {"id": row["id"], "name": row["name"], "avatar_emoji": row["avatar_emoji"],
            "display_color": row["display_color"], "relationship": relationship}


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "version": "3.0.0"}


@app.post("/api/register", response_model=AuthResponse)
def register(payload: Credentials) -> AuthResponse:
    salt, password_hash = create_password_hash(payload.password)
    recovery = recovery_code()
    with db() as conn:
        try:
            cur = conn.execute("""INSERT INTO users(name,password_salt,password_hash,created_at,recovery_hash)
                                VALUES (?,?,?,?,?)""", (payload.name, salt, password_hash, now_ts(), code_hash(recovery)))
        except sqlite3.IntegrityError:
            raise HTTPException(409, "Benutzername bereits vergeben")
        user_id = int(cur.lastrowid)
        token = issue_token(conn, user_id, payload.device_name)
    return AuthResponse(token=token, user_id=user_id, name=payload.name, recovery_code=recovery)


@app.post("/api/login", response_model=AuthResponse)
def login(payload: Credentials) -> AuthResponse:
    current = now_ts()
    with db() as conn:
        user = conn.execute("SELECT * FROM users WHERE name=? COLLATE NOCASE", (payload.name.strip(),)).fetchone()
        if user and user["locked_until"] and user["locked_until"] > current:
            raise HTTPException(429, "Zu viele Versuche. Bitte später erneut versuchen")
        if not user or not verify_password(payload.password, user["password_salt"], user["password_hash"]):
            if user:
                failures = int(user["failed_logins"] or 0) + 1
                locked_until = current + 300 if failures >= 5 else None
                conn.execute("UPDATE users SET failed_logins=?,locked_until=? WHERE id=?", (failures, locked_until, user["id"]))
                conn.commit()
                if locked_until:
                    raise HTTPException(429, "Zu viele Versuche. Konto 5 Minuten gesperrt")
            raise HTTPException(401, "Name oder Passwort falsch")
        conn.execute("UPDATE users SET failed_logins=0,locked_until=NULL WHERE id=?", (user["id"],))
        token = issue_token(conn, user["id"], payload.device_name)
    return AuthResponse(token=token, user_id=user["id"], name=user["name"])


@app.post("/api/set-password")
def set_password(payload: PasswordOnly, user: sqlite3.Row = Depends(current_user)) -> dict:
    if user["password_hash"] is not None:
        raise HTTPException(409, "Passwort ist bereits gesetzt")
    salt, hashed = create_password_hash(payload.password)
    recovery = recovery_code()
    with db() as conn:
        conn.execute("UPDATE users SET password_salt=?,password_hash=?,recovery_hash=? WHERE id=? AND password_hash IS NULL",
                     (salt, hashed, code_hash(recovery), user["id"]))
    return {"ok": True, "recovery_code": recovery}


@app.post("/api/password/change")
def change_password(payload: PasswordChange, user: sqlite3.Row = Depends(current_user)) -> dict:
    with db() as conn:
        full = conn.execute("SELECT * FROM users WHERE id=?", (user["id"],)).fetchone()
        if not verify_password(payload.old_password, full["password_salt"], full["password_hash"]):
            raise HTTPException(403, "Altes Passwort ist falsch")
        salt, hashed = create_password_hash(payload.new_password)
        conn.execute("UPDATE users SET password_salt=?,password_hash=?,failed_logins=0,locked_until=NULL WHERE id=?",
                     (salt, hashed, user["id"]))
    return {"ok": True}


@app.post("/api/recovery/new")
def new_recovery(user: sqlite3.Row = Depends(current_user)) -> dict:
    code = recovery_code()
    with db() as conn:
        conn.execute("UPDATE users SET recovery_hash=? WHERE id=?", (code_hash(code), user["id"]))
    return {"recovery_code": code}


@app.post("/api/recovery/reset", response_model=AuthResponse)
def reset_password(payload: RecoveryReset) -> AuthResponse:
    salt, hashed = create_password_hash(payload.new_password)
    with db() as conn:
        user = conn.execute("SELECT * FROM users WHERE name=? COLLATE NOCASE", (payload.name.strip(),)).fetchone()
        if not user or not user["recovery_hash"] or not secrets.compare_digest(user["recovery_hash"], code_hash(payload.recovery_code)):
            raise HTTPException(403, "Wiederherstellungscode ungültig")
        new_code = recovery_code()
        conn.execute("""UPDATE users SET password_salt=?,password_hash=?,recovery_hash=?,failed_logins=0,locked_until=NULL
                     WHERE id=?""", (salt, hashed, code_hash(new_code), user["id"]))
        conn.execute("DELETE FROM sessions WHERE user_id=?", (user["id"],))
        token = issue_token(conn, user["id"], payload.device_name)
    return AuthResponse(token=token, user_id=user["id"], name=user["name"], recovery_code=new_code)


@app.get("/api/status")
def api_status(user: sqlite3.Row = Depends(current_user)) -> dict:
    return {"user_id": user["id"], "name": user["name"], "needs_password": user["password_hash"] is None,
            "avatar_emoji": user["avatar_emoji"], "display_color": user["display_color"],
            "discoverable": bool(user["discoverable"])}


@app.patch("/api/profile")
def update_profile(payload: ProfileUpdate, user: sqlite3.Row = Depends(current_user)) -> dict:
    changes = payload.model_dump(exclude_none=True)
    if "name" in changes:
        changes["name"] = changes["name"].strip()
    if not changes:
        return api_status(user)
    assignments = ",".join(f"{key}=?" for key in changes)
    values = [int(v) if isinstance(v, bool) else v for v in changes.values()]
    with db() as conn:
        try:
            conn.execute(f"UPDATE users SET {assignments} WHERE id=?", (*values, user["id"]))
        except sqlite3.IntegrityError:
            raise HTTPException(409, "Benutzername bereits vergeben")
        row = conn.execute("SELECT * FROM users WHERE id=?", (user["id"],)).fetchone()
    return {"user_id": row["id"], "name": row["name"], "needs_password": row["password_hash"] is None,
            "avatar_emoji": row["avatar_emoji"], "display_color": row["display_color"], "discoverable": bool(row["discoverable"])}


@app.get("/api/sessions")
def sessions(user: sqlite3.Row = Depends(current_user)) -> list[dict]:
    with db() as conn:
        rows = conn.execute("SELECT rowid AS id,token_hash,device_name,created_at,last_seen_at FROM sessions WHERE user_id=? ORDER BY created_at DESC",
                            (user["id"],)).fetchall()
    return [{"id": r["id"], "device_name": r["device_name"], "created_at": r["created_at"],
             "last_seen_at": r["last_seen_at"], "current": r["token_hash"] == user["current_token_hash"]} for r in rows]


@app.delete("/api/sessions/{session_id}")
def revoke_session(session_id: int, user: sqlite3.Row = Depends(current_user)) -> dict:
    with db() as conn:
        cur = conn.execute("DELETE FROM sessions WHERE rowid=? AND user_id=?", (session_id, user["id"]))
        if cur.rowcount != 1:
            raise HTTPException(404, "Sitzung nicht gefunden")
    return {"ok": True}


@app.post("/api/logout")
def logout(user: sqlite3.Row = Depends(current_user)) -> dict:
    with db() as conn:
        conn.execute("DELETE FROM sessions WHERE token_hash=?", (user["current_token_hash"],))
    return {"ok": True}


def relationships(conn: sqlite3.Connection, user_id: int) -> dict[int, str]:
    result: dict[int, str] = {}
    for row in conn.execute("SELECT sender_id,recipient_id,status FROM friend_requests WHERE sender_id=? OR recipient_id=?",
                            (user_id, user_id)):
        other = row["recipient_id"] if row["sender_id"] == user_id else row["sender_id"]
        if row["status"] == "accepted": result[other] = "friends"
        elif row["status"] == "pending" and result.get(other) != "friends":
            result[other] = "outgoing" if row["sender_id"] == user_id else "incoming"
    return result


@app.get("/api/users")
def list_users(user: sqlite3.Row = Depends(current_user)) -> list[dict]:
    with db() as conn:
        relation = relationships(conn, user["id"])
        rows = conn.execute("""SELECT * FROM users u WHERE id!=? AND discoverable=1 AND NOT EXISTS(
                            SELECT 1 FROM blocks b WHERE (b.blocker_id=? AND b.blocked_id=u.id) OR
                            (b.blocker_id=u.id AND b.blocked_id=?)) ORDER BY name COLLATE NOCASE""",
                            (user["id"], user["id"], user["id"])).fetchall()
    return [user_view(row, relation.get(row["id"], "none")) for row in rows]


@app.get("/api/users/search")
def search_user(q: str = Query(min_length=1, max_length=40), user: sqlite3.Row = Depends(current_user)) -> list[dict]:
    with db() as conn:
        relation = relationships(conn, user["id"])
        rows = conn.execute("""SELECT * FROM users u WHERE id!=? AND name=? COLLATE NOCASE AND NOT EXISTS(
                            SELECT 1 FROM blocks b WHERE (b.blocker_id=? AND b.blocked_id=u.id) OR
                            (b.blocker_id=u.id AND b.blocked_id=?))""", (user["id"], q.strip(), user["id"], user["id"])).fetchall()
    return [user_view(row, relation.get(row["id"], "none")) for row in rows]


@app.post("/api/friend-requests", status_code=201)
def create_friend_request(payload: FriendRequestCreate, user: sqlite3.Row = Depends(current_user)) -> dict:
    if payload.recipient_id == user["id"]: raise HTTPException(422, "Anfrage an dich selbst ist nicht möglich")
    with db() as conn:
        if not conn.execute("SELECT 1 FROM users WHERE id=?", (payload.recipient_id,)).fetchone(): raise HTTPException(404, "Benutzer nicht gefunden")
        if blocked(conn, user["id"], payload.recipient_id): raise HTTPException(403, "Kontakt ist blockiert")
        existing = conn.execute("""SELECT status FROM friend_requests WHERE
                                (sender_id=? AND recipient_id=?) OR (sender_id=? AND recipient_id=?) ORDER BY id DESC LIMIT 1""",
                                (user["id"], payload.recipient_id, payload.recipient_id, user["id"])).fetchone()
        if existing and existing["status"] in ("pending", "accepted"): raise HTTPException(409, "Anfrage oder Freundschaft besteht bereits")
        cur = conn.execute("INSERT INTO friend_requests(sender_id,recipient_id,status,created_at) VALUES (?,?,'pending',?)",
                           (user["id"], payload.recipient_id, now_ts()))
    return {"id": int(cur.lastrowid)}


@app.get("/api/friend-requests/incoming")
def incoming_requests(user: sqlite3.Row = Depends(current_user)) -> list[dict]:
    with db() as conn:
        rows = conn.execute("""SELECT fr.id,fr.sender_id,u.name sender_name,u.avatar_emoji,u.display_color,fr.created_at
                            FROM friend_requests fr JOIN users u ON u.id=fr.sender_id
                            WHERE fr.recipient_id=? AND fr.status='pending' ORDER BY fr.created_at""", (user["id"],)).fetchall()
    return [dict(r) for r in rows]


@app.get("/api/friend-requests/outgoing")
def outgoing_requests(user: sqlite3.Row = Depends(current_user)) -> list[dict]:
    with db() as conn:
        rows = conn.execute("""SELECT fr.id,fr.recipient_id,u.name recipient_name,u.avatar_emoji,
                            u.display_color,fr.created_at FROM friend_requests fr JOIN users u ON u.id=fr.recipient_id
                            WHERE fr.sender_id=? AND fr.status='pending' ORDER BY fr.created_at""", (user["id"],)).fetchall()
    return [dict(r) for r in rows]


@app.post("/api/friend-requests/{request_id}/respond")
def respond_request(request_id: int, payload: FriendRequestRespond, user: sqlite3.Row = Depends(current_user)) -> dict:
    new = "accepted" if payload.accept else "rejected"
    with db() as conn:
        cur = conn.execute("UPDATE friend_requests SET status=?,responded_at=? WHERE id=? AND recipient_id=? AND status='pending'",
                           (new, now_ts(), request_id, user["id"]))
        if cur.rowcount != 1: raise HTTPException(404, "Offene Anfrage nicht gefunden")
    return {"status": new}


@app.delete("/api/friend-requests/{request_id}")
def withdraw_request(request_id: int, user: sqlite3.Row = Depends(current_user)) -> dict:
    with db() as conn:
        cur = conn.execute("DELETE FROM friend_requests WHERE id=? AND sender_id=? AND status='pending'", (request_id, user["id"]))
        if cur.rowcount != 1: raise HTTPException(404, "Offene Anfrage nicht gefunden")
    return {"ok": True}


@app.get("/api/friends")
def list_friends(user: sqlite3.Row = Depends(current_user)) -> list[dict]:
    with db() as conn:
        rows = conn.execute("""SELECT u.* FROM friend_requests fr JOIN users u
                            ON u.id=CASE WHEN fr.sender_id=? THEN fr.recipient_id ELSE fr.sender_id END
                            WHERE fr.status='accepted' AND (fr.sender_id=? OR fr.recipient_id=?) ORDER BY u.name COLLATE NOCASE""",
                            (user["id"], user["id"], user["id"])).fetchall()
    return [user_view(r, "friends") for r in rows]


@app.delete("/api/friends/{friend_id}")
def remove_friend(friend_id: int, user: sqlite3.Row = Depends(current_user)) -> dict:
    with db() as conn:
        cur = conn.execute("""DELETE FROM friend_requests WHERE status='accepted' AND
                           ((sender_id=? AND recipient_id=?) OR (sender_id=? AND recipient_id=?))""",
                           (user["id"], friend_id, friend_id, user["id"]))
        if cur.rowcount < 1: raise HTTPException(404, "Freundschaft nicht gefunden")
    return {"ok": True}


@app.post("/api/blocks/{blocked_id}")
def block_user(blocked_id: int, user: sqlite3.Row = Depends(current_user)) -> dict:
    if blocked_id == user["id"]: raise HTTPException(422, "Du kannst dich nicht selbst blockieren")
    with db() as conn:
        if not conn.execute("SELECT 1 FROM users WHERE id=?", (blocked_id,)).fetchone(): raise HTTPException(404, "Benutzer nicht gefunden")
        conn.execute("INSERT OR IGNORE INTO blocks(blocker_id,blocked_id,created_at) VALUES (?,?,?)", (user["id"], blocked_id, now_ts()))
        conn.execute("DELETE FROM friend_requests WHERE (sender_id=? AND recipient_id=?) OR (sender_id=? AND recipient_id=?)",
                     (user["id"], blocked_id, blocked_id, user["id"]))
    return {"ok": True}


@app.delete("/api/blocks/{blocked_id}")
def unblock_user(blocked_id: int, user: sqlite3.Row = Depends(current_user)) -> dict:
    with db() as conn:
        conn.execute("DELETE FROM blocks WHERE blocker_id=? AND blocked_id=?", (user["id"], blocked_id))
    return {"ok": True}


@app.get("/api/blocks")
def list_blocks(user: sqlite3.Row = Depends(current_user)) -> list[dict]:
    with db() as conn:
        rows = conn.execute("SELECT u.* FROM blocks b JOIN users u ON u.id=b.blocked_id WHERE b.blocker_id=?", (user["id"],)).fetchall()
    return [user_view(r) for r in rows]


@app.post("/api/push-token")
def register_push_token(payload: PushTokenCreate, user: sqlite3.Row = Depends(current_user)) -> dict:
    with db() as conn:
        conn.execute("UPDATE sessions SET push_token=? WHERE token_hash=?", (payload.token, user["current_token_hash"]))
    return {"ok": True}


@app.post("/api/groups", status_code=201)
def create_group(payload: GroupCreate, user: sqlite3.Row = Depends(current_user)) -> dict:
    members = list(dict.fromkeys(payload.member_ids))
    if user["id"] in members: members.remove(user["id"])
    with db() as conn:
        if any(not are_friends(conn, user["id"], member) for member in members):
            raise HTTPException(403, "Gruppenmitglieder müssen mit dir befreundet sein")
        cur = conn.execute("INSERT INTO groups(name,owner_id,created_at) VALUES (?,?,?)", (payload.name.strip(), user["id"], now_ts()))
        group_id = int(cur.lastrowid)
        conn.executemany("INSERT INTO group_members(group_id,user_id) VALUES (?,?)", [(group_id, user["id"]), *[(group_id, m) for m in members]])
    return {"id": group_id}


@app.get("/api/groups")
def list_groups(user: sqlite3.Row = Depends(current_user)) -> list[dict]:
    with db() as conn:
        groups = conn.execute("""SELECT g.* FROM groups g JOIN group_members gm ON gm.group_id=g.id
                              WHERE gm.user_id=? ORDER BY g.name COLLATE NOCASE""", (user["id"],)).fetchall()
        result = []
        for group in groups:
            members = conn.execute("""SELECT u.id,u.name,u.avatar_emoji,u.display_color FROM group_members gm
                                   JOIN users u ON u.id=gm.user_id WHERE gm.group_id=? ORDER BY u.name""", (group["id"],)).fetchall()
            result.append({"id": group["id"], "name": group["name"], "owner_id": group["owner_id"],
                           "members": [dict(m) for m in members]})
    return result


def message_recipients(conn: sqlite3.Connection, payload: MessageCreate, sender_id: int) -> tuple[list[int], int | None]:
    if payload.group_id is not None:
        member = conn.execute("SELECT 1 FROM group_members WHERE group_id=? AND user_id=?", (payload.group_id, sender_id)).fetchone()
        if not member: raise HTTPException(403, "Du bist nicht Mitglied dieser Gruppe")
        ids = [r[0] for r in conn.execute("SELECT user_id FROM group_members WHERE group_id=? AND user_id!=?", (payload.group_id, sender_id))]
    else:
        ids = payload.recipient_ids.copy()
        if payload.recipient_id is not None: ids.append(payload.recipient_id)
        ids = list(dict.fromkeys(ids))
    if not ids: raise HTTPException(422, "Mindestens ein Empfänger ist erforderlich")
    if any(blocked(conn, sender_id, rid) or not are_friends(conn, sender_id, rid) for rid in ids):
        raise HTTPException(403, "Nachrichten sind nur unter nicht blockierten Freunden möglich")
    return ids, payload.group_id


def release_fields(payload: MessageCreate, current: int) -> tuple[str, int, int | None, int | None]:
    mode = "manual" if payload.manual_release else payload.mode
    if mode not in VALID_MODES: raise HTTPException(422, "Unbekannte Freigabeart")
    if mode == "timed":
        if payload.release_at is None or payload.release_at <= current: raise HTTPException(422, "Freigabezeit muss in der Zukunft liegen")
        if payload.release_at - current > MAX_RELEASE_SECONDS: raise HTTPException(422, "Freigabezeit liegt zu weit in der Zukunft")
        return mode, payload.release_at, None, None
    if mode == "random":
        if payload.random_from is None or payload.random_to is None or payload.random_from <= current or payload.random_to <= payload.random_from:
            raise HTTPException(422, "Zufallszeitraum ist ungültig")
        if payload.random_to - current > MAX_RELEASE_SECONDS: raise HTTPException(422, "Zufallszeitraum liegt zu weit in der Zukunft")
        chosen = payload.random_from + secrets.randbelow(payload.random_to - payload.random_from + 1)
        return mode, chosen, payload.random_from, payload.random_to
    if payload.release_at is not None: raise HTTPException(422, "Diese Freigabeart darf keinen Zeitpunkt enthalten")
    return mode, MANUAL_RELEASE_SENTINEL, None, None


@app.post("/api/messages", status_code=201)
def create_message(payload: MessageCreate, user: sqlite3.Row = Depends(current_user)) -> dict:
    current = now_ts()
    mode, stored_release, random_from, random_to = release_fields(payload, current)
    attachment_values = (payload.attachment_name, payload.attachment_mime, payload.attachment_ciphertext, payload.attachment_nonce)
    if any(attachment_values) and not all(attachment_values): raise HTTPException(422, "Anhangsdaten sind unvollständig")
    with db() as conn:
        recipients, group_id = message_recipients(conn, payload, user["id"])
        ids = []
        for recipient_id in recipients:
            cur = conn.execute("""INSERT INTO messages(
                sender_id,recipient_id,ciphertext,nonce,encryption_key,release_at,created_at,manual_release,released_at,
                title,mode,one_time,read_at,sender_approved,recipient_approved,random_from,random_to,
                attachment_name,attachment_mime,attachment_ciphertext,attachment_nonce,group_id)
                VALUES (?,?,?,?,?,?,?,?,NULL,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                (user["id"], recipient_id, payload.ciphertext, payload.nonce, payload.encryption_key, stored_release, current,
                 int(mode == "manual"), payload.title, mode, int(payload.one_time), None, 0, 0, random_from, random_to,
                 payload.attachment_name, payload.attachment_mime, payload.attachment_ciphertext, payload.attachment_nonce, group_id))
            ids.append(int(cur.lastrowid))
    return {"id": ids[0], "ids": ids}


def online(conn: sqlite3.Connection, user_id: int, current: int) -> bool:
    return conn.execute("SELECT 1 FROM sessions WHERE user_id=? AND last_seen_at>=? LIMIT 1", (user_id, current - ONLINE_SECONDS)).fetchone() is not None


def unlocked(conn: sqlite3.Connection, row: sqlite3.Row, current: int) -> bool:
    mode = row["mode"] or ("manual" if row["manual_release"] else "timed")
    result = False
    if mode in ("timed", "random"): result = current >= row["release_at"]
    elif mode == "manual": result = row["released_at"] is not None
    elif mode == "mutual": result = bool(row["sender_approved"] and row["recipient_approved"])
    elif mode == "presence": result = row["released_at"] is not None or (
        online(conn, row["sender_id"], current) and online(conn, row["recipient_id"], current)
    )
    if result and mode in ("mutual", "presence") and row["released_at"] is None:
        conn.execute("UPDATE messages SET released_at=? WHERE id=?", (current, row["id"]))
    return result


def reactions(conn: sqlite3.Connection, message_id: int) -> list[dict]:
    return [dict(r) for r in conn.execute("""SELECT mr.user_id,u.name, mr.emoji FROM message_reactions mr
                                         JOIN users u ON u.id=mr.user_id WHERE mr.message_id=?""", (message_id,))]


def message_meta(conn: sqlite3.Connection, row: sqlite3.Row, current: int, incoming: bool) -> dict:
    is_open = unlocked(conn, row, current)
    mode = row["mode"]
    result = {
        "id": row["id"], "title": row["title"], "created_at": row["created_at"], "mode": mode,
        "release_at": row["release_at"] if mode == "timed" else None,
        "random_from": row["random_from"], "random_to": row["random_to"], "manual_release": mode == "manual",
        "released_at": row["released_at"], "unlocked": is_open, "one_time": bool(row["one_time"]),
        "read_at": row["read_at"], "sender_approved": bool(row["sender_approved"]),
        "recipient_approved": bool(row["recipient_approved"]), "attachment_name": row["attachment_name"],
        "attachment_mime": row["attachment_mime"], "group_id": row["group_id"], "reactions": reactions(conn, row["id"]),
    }
    if incoming:
        result["sender_name"] = row["sender_name"]
    else:
        result["recipient_name"] = row["recipient_name"]
    # V2 compatibility: ordinary unlocked messages still include content.
    if incoming and is_open and not row["one_time"]:
        result.update(ciphertext=row["ciphertext"], nonce=row["nonce"], encryption_key=row["encryption_key"])
    else:
        result.update(ciphertext=None, nonce=None, encryption_key=None)
    return result


@app.get("/api/messages")
def list_messages(user: sqlite3.Row = Depends(current_user)) -> list[dict]:
    with db() as conn:
        rows = conn.execute("""SELECT m.*,u.name sender_name FROM messages m JOIN users u ON u.id=m.sender_id
                            WHERE m.recipient_id=? ORDER BY m.created_at DESC LIMIT 300""", (user["id"],)).fetchall()
        return [message_meta(conn, row, now_ts(), True) for row in rows]


@app.get("/api/outbox")
def list_outbox(user: sqlite3.Row = Depends(current_user)) -> list[dict]:
    with db() as conn:
        rows = conn.execute("""SELECT m.*,u.name recipient_name FROM messages m JOIN users u ON u.id=m.recipient_id
                            WHERE m.sender_id=? ORDER BY m.created_at DESC LIMIT 300""", (user["id"],)).fetchall()
        return [message_meta(conn, row, now_ts(), False) for row in rows]


@app.get("/api/messages/{message_id}/content")
def message_content(message_id: int, user: sqlite3.Row = Depends(current_user)) -> dict:
    with db() as conn:
        row = conn.execute("SELECT * FROM messages WHERE id=? AND recipient_id=?", (message_id, user["id"])).fetchone()
        if not row: raise HTTPException(404, "Nachricht nicht gefunden")
        if not unlocked(conn, row, now_ts()): raise HTTPException(423, "Nachricht ist noch gesperrt")
        if row["one_time"] and row["read_at"] is not None: raise HTTPException(410, "Einmal-Nachricht wurde bereits geöffnet")
        read_at = row["read_at"] or now_ts()
        conn.execute("UPDATE messages SET read_at=? WHERE id=? AND read_at IS NULL", (read_at, message_id))
        return {"ciphertext": row["ciphertext"], "nonce": row["nonce"], "encryption_key": row["encryption_key"],
                "attachment_name": row["attachment_name"], "attachment_mime": row["attachment_mime"],
                "attachment_ciphertext": row["attachment_ciphertext"], "attachment_nonce": row["attachment_nonce"]}


@app.post("/api/messages/{message_id}/release")
def release_message(message_id: int, user: sqlite3.Row = Depends(current_user)) -> dict:
    with db() as conn:
        row = conn.execute("SELECT * FROM messages WHERE id=? AND sender_id=?", (message_id, user["id"])).fetchone()
        if not row: raise HTTPException(404, "Nachricht nicht gefunden")
        if row["mode"] != "manual": raise HTTPException(409, "Diese Nachricht wird nicht manuell freigegeben")
        released_at = row["released_at"] or now_ts()
        conn.execute("UPDATE messages SET released_at=? WHERE id=?", (released_at, message_id))
    return {"released_at": released_at}


@app.post("/api/messages/{message_id}/approve")
def approve_message(message_id: int, user: sqlite3.Row = Depends(current_user)) -> dict:
    with db() as conn:
        row = conn.execute("SELECT * FROM messages WHERE id=? AND (sender_id=? OR recipient_id=?)", (message_id, user["id"], user["id"])).fetchone()
        if not row: raise HTTPException(404, "Nachricht nicht gefunden")
        if row["mode"] != "mutual": raise HTTPException(409, "Nachricht benötigt keine gegenseitige Freigabe")
        column = "sender_approved" if row["sender_id"] == user["id"] else "recipient_approved"
        conn.execute(f"UPDATE messages SET {column}=1 WHERE id=?", (message_id,))
        updated = conn.execute("SELECT * FROM messages WHERE id=?", (message_id,)).fetchone()
        is_open = unlocked(conn, updated, now_ts())
    return {"approved": True, "unlocked": is_open}


@app.delete("/api/messages/{message_id}")
def retract_message(message_id: int, user: sqlite3.Row = Depends(current_user)) -> dict:
    with db() as conn:
        row = conn.execute("SELECT * FROM messages WHERE id=? AND sender_id=?", (message_id, user["id"])).fetchone()
        if not row: raise HTTPException(404, "Nachricht nicht gefunden")
        if unlocked(conn, row, now_ts()): raise HTTPException(409, "Freigegebene Nachrichten können nicht zurückgezogen werden")
        conn.execute("DELETE FROM messages WHERE id=?", (message_id,))
    return {"ok": True}


@app.put("/api/messages/{message_id}/reaction")
def react(message_id: int, payload: ReactionCreate, user: sqlite3.Row = Depends(current_user)) -> dict:
    with db() as conn:
        row = conn.execute("SELECT * FROM messages WHERE id=? AND (sender_id=? OR recipient_id=?)", (message_id, user["id"], user["id"])).fetchone()
        if not row: raise HTTPException(404, "Nachricht nicht gefunden")
        conn.execute("""INSERT INTO message_reactions(message_id,user_id,emoji,created_at) VALUES (?,?,?,?)
                     ON CONFLICT(message_id,user_id) DO UPDATE SET emoji=excluded.emoji,created_at=excluded.created_at""",
                     (message_id, user["id"], payload.emoji, now_ts()))
    return {"ok": True}
