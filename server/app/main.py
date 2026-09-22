from __future__ import annotations

import base64
import binascii
import hashlib
import json
import os
import secrets
import sqlite3
from contextlib import asynccontextmanager, contextmanager
from datetime import datetime, timezone
from pathlib import Path

from fastapi import Depends, FastAPI, Header, HTTPException, Query, status
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException
from pydantic import BaseModel, Field, field_validator
from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, utils

DB_PATH = Path(os.getenv("DB_PATH", "/data/layermaxxing.db"))
APP_VERSION = "4.0.0"
SERVER_NAME = os.getenv("SERVER_NAME", "GS Layermaxxing")
SERVER_ROLE = "production" if os.getenv("SERVER_ROLE", "test").lower() == "production" else "test"
TESTSERVER_WARNING = "TESTSERVER VON GERFRIED – NUR ZUM AUSPROBIEREN"
SUPPORTED_FEATURES = ["letters", "chats", "friendship_settings", "ep", "verification_exports", "creative_mode", "sparks"]
# One-time creative entitlement for the accounts that already exist on the test
# server when this version first starts. The marker row makes the migration
# idempotent: later restarts and newly registered accounts stay unentitled.
CREATIVE_MIGRATION_KEY = "creative_entitlement_migrated_v1"
# Freundesfunke: a short, anonymous, one-way positive note between friends.
# The server knows the sender for abuse handling; no recipient-facing response
# may ever carry a sender field or a timestamp that would allow correlation.
SPARK_MAX_CIPHERTEXT = 800
SPARK_PAIR_DAILY_LIMIT = 5
SPARK_SENDER_DAILY_LIMIT = 20
MAX_RELEASE_SECONDS = 365 * 24 * 60 * 60
MANUAL_RELEASE_SENTINEL = 2_147_483_647
# The client computes a release time from its own clock right before it seals the
# letter, so a few seconds of clock skew or network latency must not reject an
# otherwise valid boundary value. The tolerance only affects creation checks;
# unlocking still compares against the stored absolute release time.
MINIMUM_DELAY_TOLERANCE = 5
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
            "creative_entitled": "INTEGER NOT NULL DEFAULT 0",
        })
        conn.execute("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        if SERVER_ROLE == "test" and not conn.execute(
            "SELECT 1 FROM meta WHERE key=?", (CREATIVE_MIGRATION_KEY,)
        ).fetchone():
            conn.execute("UPDATE users SET creative_entitled=1")
            conn.execute("INSERT INTO meta(key,value) VALUES (?,?)", (CREATIVE_MIGRATION_KEY, str(now_ts())))
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
            "message_class": "TEXT NOT NULL DEFAULT 'letter'", "cover_note": "TEXT NOT NULL DEFAULT ''",
            "commitment_salt": "TEXT", "plaintext_sha256": "TEXT", "commitment": "TEXT",
            "signing_public_key": "TEXT", "signature": "TEXT", "protocol_version": "INTEGER",
            "canonical_metadata": "TEXT", "minimum_release_at": "INTEGER",
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
        CREATE TABLE IF NOT EXISTS topics (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            creator_id INTEGER NOT NULL,
            peer_user_id INTEGER,
            group_id INTEGER,
            ciphertext TEXT NOT NULL,
            nonce TEXT NOT NULL,
            encryption_key TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            completed_at INTEGER,
            completed_by_id INTEGER,
            CHECK(peer_user_id IS NULL OR group_id IS NULL),
            FOREIGN KEY(creator_id) REFERENCES users(id) ON DELETE CASCADE,
            FOREIGN KEY(peer_user_id) REFERENCES users(id) ON DELETE CASCADE,
            FOREIGN KEY(group_id) REFERENCES groups(id) ON DELETE CASCADE,
            FOREIGN KEY(completed_by_id) REFERENCES users(id) ON DELETE SET NULL
        );
        CREATE INDEX IF NOT EXISTS idx_topics_creator ON topics(creator_id,completed_at,created_at DESC);
        CREATE INDEX IF NOT EXISTS idx_topics_peer ON topics(peer_user_id,completed_at,created_at DESC);
        CREATE INDEX IF NOT EXISTS idx_topics_group ON topics(group_id,completed_at,created_at DESC);
        CREATE TABLE IF NOT EXISTS friendships (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_low_id INTEGER NOT NULL,
            user_high_id INTEGER NOT NULL,
            created_at INTEGER NOT NULL,
            active INTEGER NOT NULL DEFAULT 1,
            UNIQUE(user_low_id,user_high_id),
            CHECK(user_low_id < user_high_id),
            FOREIGN KEY(user_low_id) REFERENCES users(id) ON DELETE CASCADE,
            FOREIGN KEY(user_high_id) REFERENCES users(id) ON DELETE CASCADE
        );
        CREATE TABLE IF NOT EXISTS friendship_settings (
            friendship_id INTEGER PRIMARY KEY,
            letters_enabled INTEGER NOT NULL DEFAULT 1,
            chats_enabled INTEGER NOT NULL DEFAULT 1,
            ep_enabled INTEGER NOT NULL DEFAULT 0,
            min_letter_delay_seconds INTEGER NOT NULL DEFAULT 0,
            updated_at INTEGER NOT NULL,
            FOREIGN KEY(friendship_id) REFERENCES friendships(id) ON DELETE CASCADE
        );
        CREATE TABLE IF NOT EXISTS friendship_setting_proposals (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            friendship_id INTEGER NOT NULL,
            proposer_id INTEGER NOT NULL,
            letters_enabled INTEGER NOT NULL,
            chats_enabled INTEGER NOT NULL,
            ep_enabled INTEGER NOT NULL,
            min_letter_delay_seconds INTEGER NOT NULL,
            status TEXT NOT NULL DEFAULT 'pending'
                CHECK(status IN ('pending','accepted','rejected','withdrawn','cancelled')),
            created_at INTEGER NOT NULL,
            responded_at INTEGER,
            FOREIGN KEY(friendship_id) REFERENCES friendships(id) ON DELETE CASCADE,
            FOREIGN KEY(proposer_id) REFERENCES users(id) ON DELETE CASCADE
        );
        CREATE UNIQUE INDEX IF NOT EXISTS idx_one_pending_settings_proposal
            ON friendship_setting_proposals(friendship_id) WHERE status='pending';
        CREATE TABLE IF NOT EXISTS ep_proposals (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            friendship_id INTEGER NOT NULL,
            proposer_id INTEGER NOT NULL,
            beneficiary_id INTEGER NOT NULL,
            points INTEGER NOT NULL CHECK(points BETWEEN 1 AND 1000),
            title TEXT NOT NULL,
            description TEXT,
            letter_id INTEGER,
            status TEXT NOT NULL DEFAULT 'pending'
                CHECK(status IN ('pending','accepted','rejected','cancelled')),
            created_at INTEGER NOT NULL,
            responded_at INTEGER,
            FOREIGN KEY(friendship_id) REFERENCES friendships(id) ON DELETE CASCADE,
            FOREIGN KEY(proposer_id) REFERENCES users(id) ON DELETE CASCADE,
            FOREIGN KEY(beneficiary_id) REFERENCES users(id) ON DELETE CASCADE,
            FOREIGN KEY(letter_id) REFERENCES messages(id) ON DELETE SET NULL
        );
        CREATE INDEX IF NOT EXISTS idx_ep_participants ON ep_proposals(proposer_id,beneficiary_id,status,created_at DESC);
        CREATE TABLE IF NOT EXISTS sparks (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            sender_id INTEGER NOT NULL,
            recipient_id INTEGER NOT NULL,
            ciphertext TEXT NOT NULL,
            nonce TEXT NOT NULL,
            encryption_key TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            opened_at INTEGER,
            FOREIGN KEY(sender_id) REFERENCES users(id) ON DELETE CASCADE,
            FOREIGN KEY(recipient_id) REFERENCES users(id) ON DELETE CASCADE
        );
        CREATE INDEX IF NOT EXISTS idx_sparks_recipient ON sparks(recipient_id,id DESC);
        CREATE INDEX IF NOT EXISTS idx_sparks_sender ON sparks(sender_id,created_at DESC);
        CREATE TABLE IF NOT EXISTS spark_mutes (
            recipient_id INTEGER NOT NULL,
            sender_id INTEGER NOT NULL,
            created_at INTEGER NOT NULL,
            PRIMARY KEY(recipient_id,sender_id),
            FOREIGN KEY(recipient_id) REFERENCES users(id) ON DELETE CASCADE,
            FOREIGN KEY(sender_id) REFERENCES users(id) ON DELETE CASCADE
        );
        CREATE TABLE IF NOT EXISTS spark_reports (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            spark_id INTEGER NOT NULL,
            reporter_id INTEGER NOT NULL,
            created_at INTEGER NOT NULL,
            FOREIGN KEY(spark_id) REFERENCES sparks(id) ON DELETE CASCADE,
            FOREIGN KEY(reporter_id) REFERENCES users(id) ON DELETE CASCADE
        );
        CREATE TABLE IF NOT EXISTS test_advances (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            message_id INTEGER NOT NULL,
            actor_id INTEGER NOT NULL,
            action TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            FOREIGN KEY(message_id) REFERENCES messages(id) ON DELETE CASCADE,
            FOREIGN KEY(actor_id) REFERENCES users(id) ON DELETE CASCADE
        );
        """)

        accepted = conn.execute(
            "SELECT sender_id,recipient_id,created_at FROM friend_requests WHERE status='accepted'"
        ).fetchall()
        for row in accepted:
            low, high = sorted((row["sender_id"], row["recipient_id"]))
            conn.execute(
                """INSERT INTO friendships(user_low_id,user_high_id,created_at,active)
                   VALUES (?,?,?,1) ON CONFLICT(user_low_id,user_high_id) DO UPDATE SET active=1""",
                (low, high, row["created_at"]),
            )
        conn.execute("""INSERT OR IGNORE INTO friendship_settings(friendship_id,updated_at)
                        SELECT id,created_at FROM friendships""")

    # V1 message tables referenced devices. Rebuild once without losing rows.
    # `legacy_alter_table=ON` keeps every REFERENCES messages(...) clause in
    # message_reactions and ep_proposals pointed at `messages` across the
    # rename; a plain rename would silently rewrite those clauses to the
    # temporary name and leave them dangling after the drop.
    raw = sqlite3.connect(DB_PATH)
    try:
        foreign_keys = list(raw.execute("PRAGMA foreign_key_list(messages)"))
        if any(row[2] == "devices" for row in foreign_keys):
            raw.execute("PRAGMA foreign_keys=OFF")
            raw.execute("PRAGMA legacy_alter_table=ON")
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
                message_class TEXT NOT NULL DEFAULT 'letter', cover_note TEXT NOT NULL DEFAULT '',
                commitment_salt TEXT, plaintext_sha256 TEXT, commitment TEXT, signing_public_key TEXT,
                signature TEXT, protocol_version INTEGER, canonical_metadata TEXT, minimum_release_at INTEGER,
                FOREIGN KEY(sender_id) REFERENCES users(id) ON DELETE CASCADE,
                FOREIGN KEY(recipient_id) REFERENCES users(id) ON DELETE CASCADE
            );
            INSERT INTO messages(
                id,sender_id,recipient_id,ciphertext,nonce,encryption_key,release_at,created_at,
                manual_release,released_at,title,mode,one_time,read_at,sender_approved,
                recipient_approved,random_from,random_to,attachment_name,attachment_mime,
                attachment_ciphertext,attachment_nonce,group_id,message_class,cover_note,commitment_salt,
                plaintext_sha256,commitment,signing_public_key,signature,protocol_version,canonical_metadata,
                minimum_release_at
            ) SELECT
                id,sender_id,recipient_id,ciphertext,nonce,encryption_key,release_at,created_at,
                manual_release,released_at,title,mode,one_time,read_at,sender_approved,
                recipient_approved,random_from,random_to,attachment_name,attachment_mime,
                attachment_ciphertext,attachment_nonce,group_id,message_class,cover_note,commitment_salt,
                plaintext_sha256,commitment,signing_public_key,signature,protocol_version,canonical_metadata,
                minimum_release_at
              FROM messages_legacy_device_fk;
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


app = FastAPI(title="GS Layermaxxing", version=APP_VERSION, docs_url=None, redoc_url=None, lifespan=lifespan)


@app.middleware("http")
async def server_role_header(request, call_next):
    response = await call_next(request)
    response.headers["X-Layermaxxing-Role"] = SERVER_ROLE
    return response


# Stable, machine-readable error codes for every non-2xx response: HTTPException
# (including the framework's own 404 for unmatched routes) and request
# validation both get an `error_code` field alongside the existing `detail`, so
# older/newer client and server builds can be told apart from a bare "Not Found".
@app.exception_handler(StarletteHTTPException)
async def http_exception_handler(request, exc: StarletteHTTPException) -> JSONResponse:
    return JSONResponse(
        status_code=exc.status_code,
        content={"detail": exc.detail, "error_code": f"LM-HTTP-{exc.status_code}"},
        headers=exc.headers,
    )


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request, exc: RequestValidationError) -> JSONResponse:
    return JSONResponse(
        status_code=422,
        content={"detail": jsonable_encoder(exc.errors()), "error_code": "LM-HTTP-422"},
    )


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


class FriendshipSettingsProposalCreate(BaseModel):
    friend_id: int
    letters_enabled: bool
    chats_enabled: bool
    ep_enabled: bool
    min_letter_delay_seconds: int = Field(ge=0, le=MAX_RELEASE_SECONDS)


class ProposalRespond(BaseModel):
    accept: bool


class EpProposalCreate(BaseModel):
    beneficiary_id: int
    # Product rule for newly created proposals. The database deliberately keeps
    # its wider historical range so existing multi-point entries stay intact.
    points: int = Field(ge=1, le=1, strict=True)
    title: str = Field(min_length=1, max_length=100)
    description: str | None = Field(default=None, max_length=1000)
    letter_id: int | None = None

    @field_validator("title")
    @classmethod
    def clean_title(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("Titel darf nicht leer sein")
        return value


class ReactionCreate(BaseModel):
    emoji: str = Field(min_length=1, max_length=8)


class PushTokenCreate(BaseModel):
    token: str = Field(min_length=8, max_length=4096)


class GroupCreate(BaseModel):
    name: str = Field(min_length=1, max_length=60)
    member_ids: list[int] = Field(min_length=1, max_length=20)


class TopicCreate(BaseModel):
    peer_user_id: int | None = None
    group_id: int | None = None
    ciphertext: str = Field(min_length=1, max_length=100_000)
    nonce: str = Field(min_length=1, max_length=256)
    encryption_key: str = Field(min_length=1, max_length=256)


class TopicUpdate(BaseModel):
    completed: bool


class ChatMessageCreate(BaseModel):
    ciphertext: str = Field(min_length=1, max_length=100_000)
    nonce: str = Field(min_length=1, max_length=256)
    encryption_key: str = Field(min_length=1, max_length=256)


class SparkCreate(BaseModel):
    recipient_id: int
    # Deliberately tight: a spark is one short, kind line, not a channel.
    ciphertext: str = Field(min_length=1, max_length=SPARK_MAX_CIPHERTEXT)
    nonce: str = Field(min_length=1, max_length=256)
    encryption_key: str = Field(min_length=1, max_length=256)


class TestAdvance(BaseModel):
    phase: str = "release"


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
    cover_note: str = Field(default="", max_length=1000)
    commitment_salt: str | None = Field(default=None, max_length=128)
    plaintext_sha256: str | None = Field(default=None, max_length=128)
    commitment: str | None = Field(default=None, max_length=128)
    public_signing_key: str | None = Field(default=None, max_length=1024)
    signature: str | None = Field(default=None, max_length=1024)
    protocol_version: int | None = None
    canonical_metadata: str | None = Field(default=None, max_length=10_000)


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


def friendship_row(conn: sqlite3.Connection, first: int, second: int) -> sqlite3.Row | None:
    low, high = sorted((first, second))
    return conn.execute(
        "SELECT * FROM friendships WHERE user_low_id=? AND user_high_id=? AND active=1",
        (low, high),
    ).fetchone()


def friendship_settings_row(conn: sqlite3.Connection, first: int, second: int) -> sqlite3.Row | None:
    return conn.execute(
        """SELECT fs.*,f.id friendship_id FROM friendships f
           JOIN friendship_settings fs ON fs.friendship_id=f.id
           WHERE f.active=1 AND ((f.user_low_id=? AND f.user_high_id=?) OR
                                 (f.user_low_id=? AND f.user_high_id=?))""",
        (first, second, second, first),
    ).fetchone()


def materialize_friendship(conn: sqlite3.Connection, first: int, second: int, created_at: int) -> int:
    low, high = sorted((first, second))
    existing = conn.execute(
        "SELECT id,active FROM friendships WHERE user_low_id=? AND user_high_id=?", (low, high)
    ).fetchone()
    reactivated = existing is not None and not existing["active"]
    conn.execute(
        """INSERT INTO friendships(user_low_id,user_high_id,created_at,active) VALUES (?,?,?,1)
           ON CONFLICT(user_low_id,user_high_id) DO UPDATE SET active=1""",
        (low, high, created_at),
    )
    friendship_id = conn.execute(
        "SELECT id FROM friendships WHERE user_low_id=? AND user_high_id=?", (low, high)
    ).fetchone()["id"]
    conn.execute(
        "INSERT OR IGNORE INTO friendship_settings(friendship_id,updated_at) VALUES (?,?)",
        (friendship_id, created_at),
    )
    if reactivated:
        # A friendship that was removed or blocked and is then agreed again starts
        # from the documented defaults. Reviving the stored row would silently
        # re-enable EP or a relaxed minimum letter delay that nobody confirmed a
        # second time, which would break the bilateral-only rule.
        conn.execute(
            """UPDATE friendship_settings SET letters_enabled=1,chats_enabled=1,ep_enabled=0,
               min_letter_delay_seconds=0,updated_at=? WHERE friendship_id=?""",
            (created_at, friendship_id),
        )
    return int(friendship_id)


def user_view(row: sqlite3.Row, relationship: str = "none") -> dict:
    return {"id": row["id"], "name": row["name"], "avatar_emoji": row["avatar_emoji"],
            "display_color": row["display_color"], "relationship": relationship}


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "version": APP_VERSION}


@app.get("/api/server-info")
def server_info() -> dict:
    return {
        "name": SERVER_NAME,
        "role": SERVER_ROLE,
        "version": APP_VERSION,
        "warning": TESTSERVER_WARNING if SERVER_ROLE == "test" else "",
        "features": SUPPORTED_FEATURES,
    }


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
    # Creative mode needs both signals: the role of the server answering this
    # request and the account's entitlement. The client may only combine them,
    # never persist them, so a revoked entitlement wins on the next refresh.
    return {"user_id": user["id"], "name": user["name"], "needs_password": user["password_hash"] is None,
            "avatar_emoji": user["avatar_emoji"], "display_color": user["display_color"],
            "discoverable": bool(user["discoverable"]),
            "server_role": SERVER_ROLE, "creative_entitled": bool(user["creative_entitled"])}


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
            "avatar_emoji": row["avatar_emoji"], "display_color": row["display_color"], "discoverable": bool(row["discoverable"]),
            "server_role": SERVER_ROLE, "creative_entitled": bool(row["creative_entitled"])}


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
        request = conn.execute(
            "SELECT * FROM friend_requests WHERE id=? AND recipient_id=? AND status='pending'",
            (request_id, user["id"]),
        ).fetchone()
        if not request:
            raise HTTPException(404, "Offene Anfrage nicht gefunden")
        cur = conn.execute("UPDATE friend_requests SET status=?,responded_at=? WHERE id=? AND recipient_id=? AND status='pending'",
                           (new, now_ts(), request_id, user["id"]))
        if cur.rowcount != 1: raise HTTPException(404, "Offene Anfrage nicht gefunden")
        if payload.accept:
            materialize_friendship(conn, request["sender_id"], request["recipient_id"], now_ts())
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
        friendship = friendship_row(conn, user["id"], friend_id)
        if friendship:
            conn.execute("UPDATE friendship_setting_proposals SET status='cancelled',responded_at=? WHERE friendship_id=? AND status='pending'",
                         (now_ts(), friendship["id"]))
            conn.execute("UPDATE ep_proposals SET status='cancelled',responded_at=? WHERE friendship_id=? AND status='pending'",
                         (now_ts(), friendship["id"]))
            conn.execute("UPDATE friendships SET active=0 WHERE id=?", (friendship["id"],))
    return {"ok": True}


@app.post("/api/blocks/{blocked_id}")
def block_user(blocked_id: int, user: sqlite3.Row = Depends(current_user)) -> dict:
    if blocked_id == user["id"]: raise HTTPException(422, "Du kannst dich nicht selbst blockieren")
    with db() as conn:
        if not conn.execute("SELECT 1 FROM users WHERE id=?", (blocked_id,)).fetchone(): raise HTTPException(404, "Benutzer nicht gefunden")
        conn.execute("INSERT OR IGNORE INTO blocks(blocker_id,blocked_id,created_at) VALUES (?,?,?)", (user["id"], blocked_id, now_ts()))
        friendship = friendship_row(conn, user["id"], blocked_id)
        if friendship:
            conn.execute("UPDATE friendship_setting_proposals SET status='cancelled',responded_at=? WHERE friendship_id=? AND status='pending'",
                         (now_ts(), friendship["id"]))
            conn.execute("UPDATE ep_proposals SET status='cancelled',responded_at=? WHERE friendship_id=? AND status='pending'",
                         (now_ts(), friendship["id"]))
            conn.execute("UPDATE friendships SET active=0 WHERE id=?", (friendship["id"],))
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


def settings_proposal_view(row: sqlite3.Row) -> dict:
    return {
        "id": row["id"],
        "friend_id": row["friend_id"],
        "friend_name": row["friend_name"],
        "proposer_id": row["proposer_id"],
        "letters_enabled": bool(row["letters_enabled"]),
        "chats_enabled": bool(row["chats_enabled"]),
        "ep_enabled": bool(row["ep_enabled"]),
        "min_letter_delay_seconds": row["min_letter_delay_seconds"],
        "status": row["status"],
        "created_at": row["created_at"],
    }


def pending_settings_proposals(conn: sqlite3.Connection, user_id: int) -> list[sqlite3.Row]:
    return conn.execute(
        """SELECT p.*,
                  CASE WHEN f.user_low_id=? THEN f.user_high_id ELSE f.user_low_id END friend_id,
                  u.name friend_name
           FROM friendship_setting_proposals p
           JOIN friendships f ON f.id=p.friendship_id
           JOIN users u ON u.id=CASE WHEN f.user_low_id=? THEN f.user_high_id ELSE f.user_low_id END
           WHERE f.active=1 AND p.status='pending' AND (f.user_low_id=? OR f.user_high_id=?)
           ORDER BY p.created_at,p.id""",
        (user_id, user_id, user_id, user_id),
    ).fetchall()


@app.get("/api/friendship-settings")
def list_friendship_settings(user: sqlite3.Row = Depends(current_user)) -> list[dict]:
    with db() as conn:
        proposals = pending_settings_proposals(conn, user["id"])
        by_friend = {row["friend_id"]: row for row in proposals}
        rows = conn.execute(
            """SELECT fs.*,CASE WHEN f.user_low_id=? THEN f.user_high_id ELSE f.user_low_id END friend_id,
                      u.name friend_name
               FROM friendships f JOIN friendship_settings fs ON fs.friendship_id=f.id
               JOIN users u ON u.id=CASE WHEN f.user_low_id=? THEN f.user_high_id ELSE f.user_low_id END
               WHERE f.active=1 AND (f.user_low_id=? OR f.user_high_id=?)
               ORDER BY u.name COLLATE NOCASE""",
            (user["id"], user["id"], user["id"], user["id"]),
        ).fetchall()
        result = []
        for row in rows:
            proposal = by_friend.get(row["friend_id"])
            proposal_data = settings_proposal_view(proposal) if proposal else None
            result.append({
                "friend_id": row["friend_id"], "friend_name": row["friend_name"],
                "letters_enabled": bool(row["letters_enabled"]),
                "chats_enabled": bool(row["chats_enabled"]), "ep_enabled": bool(row["ep_enabled"]),
                "min_letter_delay_seconds": row["min_letter_delay_seconds"],
                "incoming_proposal": proposal_data if proposal and proposal["proposer_id"] != user["id"] else None,
                "outgoing_proposal": proposal_data if proposal and proposal["proposer_id"] == user["id"] else None,
            })
    return result


@app.get("/api/friendship-settings/proposals")
def list_friendship_settings_proposals(user: sqlite3.Row = Depends(current_user)) -> list[dict]:
    with db() as conn:
        return [settings_proposal_view(row) for row in pending_settings_proposals(conn, user["id"])]


@app.post("/api/friendship-settings/proposals", status_code=201)
def create_friendship_settings_proposal(
    payload: FriendshipSettingsProposalCreate, user: sqlite3.Row = Depends(current_user),
) -> dict:
    with db() as conn:
        friendship = friendship_row(conn, user["id"], payload.friend_id)
        if not friendship or blocked(conn, user["id"], payload.friend_id):
            raise HTTPException(403, "Einstellungen können nur unter nicht blockierten Freunden vereinbart werden")
        try:
            cur = conn.execute(
                """INSERT INTO friendship_setting_proposals(
                    friendship_id,proposer_id,letters_enabled,chats_enabled,ep_enabled,
                    min_letter_delay_seconds,status,created_at
                ) VALUES (?,?,?,?,?,?,'pending',?)""",
                (friendship["id"], user["id"], int(payload.letters_enabled), int(payload.chats_enabled),
                 int(payload.ep_enabled), payload.min_letter_delay_seconds, now_ts()),
            )
        except sqlite3.IntegrityError:
            raise HTTPException(409, "Für diese Freundschaft gibt es bereits einen offenen Vorschlag")
    return {"id": int(cur.lastrowid)}


@app.post("/api/friendship-settings/proposals/{proposal_id}/respond")
def respond_friendship_settings_proposal(
    proposal_id: int, payload: ProposalRespond, user: sqlite3.Row = Depends(current_user),
) -> dict:
    with db() as conn:
        proposal = conn.execute(
            """SELECT p.*,f.user_low_id,f.user_high_id,f.active FROM friendship_setting_proposals p
               JOIN friendships f ON f.id=p.friendship_id WHERE p.id=? AND p.status='pending'""",
            (proposal_id,),
        ).fetchone()
        if not proposal or user["id"] not in (proposal["user_low_id"], proposal["user_high_id"]):
            raise HTTPException(404, "Offener Vorschlag nicht gefunden")
        if proposal["proposer_id"] == user["id"]:
            raise HTTPException(403, "Der eigene Vorschlag kann nicht angenommen werden")
        if not proposal["active"]:
            raise HTTPException(409, "Die Freundschaft besteht nicht mehr")
        new_status = "accepted" if payload.accept else "rejected"
        responded_at = now_ts()
        conn.execute(
            "UPDATE friendship_setting_proposals SET status=?,responded_at=? WHERE id=?",
            (new_status, responded_at, proposal_id),
        )
        if payload.accept:
            conn.execute(
                """UPDATE friendship_settings SET letters_enabled=?,chats_enabled=?,ep_enabled=?,
                   min_letter_delay_seconds=?,updated_at=? WHERE friendship_id=?""",
                (proposal["letters_enabled"], proposal["chats_enabled"], proposal["ep_enabled"],
                 proposal["min_letter_delay_seconds"], responded_at, proposal["friendship_id"]),
            )
            if not proposal["ep_enabled"]:
                conn.execute(
                    """UPDATE ep_proposals SET status='cancelled',responded_at=?
                       WHERE friendship_id=? AND status='pending'""",
                    (responded_at, proposal["friendship_id"]),
                )
    return {"status": new_status}


@app.delete("/api/friendship-settings/proposals/{proposal_id}")
def withdraw_friendship_settings_proposal(
    proposal_id: int, user: sqlite3.Row = Depends(current_user),
) -> dict:
    with db() as conn:
        proposal = conn.execute(
            "SELECT * FROM friendship_setting_proposals WHERE id=? AND status='pending'", (proposal_id,)
        ).fetchone()
        if not proposal:
            raise HTTPException(404, "Offener Vorschlag nicht gefunden")
        if proposal["proposer_id"] != user["id"]:
            raise HTTPException(403, "Nur der Absender kann den Vorschlag zurückziehen")
        conn.execute(
            "UPDATE friendship_setting_proposals SET status='withdrawn',responded_at=? WHERE id=?",
            (now_ts(), proposal_id),
        )
    return {"ok": True}


def ep_view(row: sqlite3.Row) -> dict:
    return {
        "id": row["id"], "proposer_id": row["proposer_id"], "proposer_name": row["proposer_name"],
        "beneficiary_id": row["beneficiary_id"], "beneficiary_name": row["beneficiary_name"],
        "points": row["points"], "title": row["title"], "description": row["description"],
        "letter_id": row["letter_id"], "status": row["status"], "created_at": row["created_at"],
        "responded_at": row["responded_at"],
    }


def ep_level(total: int) -> dict:
    levels = ((500, "Ebenen-Legende"), (200, "Ebenen-Profi"), (50, "Ebenen-Entdecker"), (0, "Ebenen-Neuling"))
    threshold, name = next(level for level in levels if total >= level[0])
    return {"name": name, "points": total, "threshold": threshold}


@app.get("/api/ep")
def list_ep(user: sqlite3.Row = Depends(current_user)) -> dict:
    with db() as conn:
        rows = conn.execute(
            """SELECT ep.*,p.name proposer_name,b.name beneficiary_name FROM ep_proposals ep
               JOIN users p ON p.id=ep.proposer_id JOIN users b ON b.id=ep.beneficiary_id
               WHERE ep.proposer_id=? OR ep.beneficiary_id=? ORDER BY ep.created_at DESC,ep.id DESC""",
            (user["id"], user["id"]),
        ).fetchall()
        accepted = [ep_view(row) for row in rows if row["status"] == "accepted"]
        given = sum(row["points"] for row in rows if row["status"] == "accepted" and row["proposer_id"] == user["id"])
        received = sum(row["points"] for row in rows if row["status"] == "accepted" and row["beneficiary_id"] == user["id"])
        incoming = [ep_view(row) for row in rows if row["status"] == "pending" and row["beneficiary_id"] == user["id"]]
        outgoing = [ep_view(row) for row in rows if row["status"] == "pending" and row["proposer_id"] == user["id"]]
    return {
        "incoming_pending": incoming, "outgoing_pending": outgoing, "history": accepted,
        "totals": {"given": given, "received": received}, "level": ep_level(given + received),
    }


@app.post("/api/ep/proposals", status_code=201)
def create_ep_proposal(payload: EpProposalCreate, user: sqlite3.Row = Depends(current_user)) -> dict:
    if payload.beneficiary_id == user["id"]:
        raise HTTPException(422, "Ebenen-Punkte können nicht an dich selbst vergeben werden")
    with db() as conn:
        settings = friendship_settings_row(conn, user["id"], payload.beneficiary_id)
        if not settings or blocked(conn, user["id"], payload.beneficiary_id) or not settings["ep_enabled"]:
            raise HTTPException(403, "Ebenen-Punkte sind in dieser Freundschaft nicht aktiviert")
        if payload.letter_id is not None:
            linked = conn.execute(
                "SELECT sender_id,recipient_id,message_class FROM messages WHERE id=?", (payload.letter_id,)
            ).fetchone()
            if (not linked or linked["message_class"] != "letter" or
                    {linked["sender_id"], linked["recipient_id"]} != {user["id"], payload.beneficiary_id}):
                raise HTTPException(422, "Der verknüpfte Brief gehört nicht zu dieser Freundschaft")
        cur = conn.execute(
            """INSERT INTO ep_proposals(friendship_id,proposer_id,beneficiary_id,points,title,description,
               letter_id,status,created_at) VALUES (?,?,?,?,?,?,?,'pending',?)""",
            (settings["friendship_id"], user["id"], payload.beneficiary_id, payload.points,
             payload.title, payload.description.strip() if payload.description else None, payload.letter_id, now_ts()),
        )
    return {"id": int(cur.lastrowid)}


@app.post("/api/ep/proposals/{proposal_id}/respond")
def respond_ep_proposal(
    proposal_id: int, payload: ProposalRespond, user: sqlite3.Row = Depends(current_user),
) -> dict:
    with db() as conn:
        proposal = conn.execute(
            "SELECT * FROM ep_proposals WHERE id=? AND status='pending'", (proposal_id,)
        ).fetchone()
        if not proposal:
            raise HTTPException(404, "Offener EP-Vorschlag nicht gefunden")
        if proposal["beneficiary_id"] != user["id"]:
            raise HTTPException(403, "Nur die vorgeschlagene Person kann antworten")
        settings = conn.execute(
            """SELECT fs.ep_enabled,f.active FROM friendship_settings fs JOIN friendships f ON f.id=fs.friendship_id
               WHERE fs.friendship_id=?""", (proposal["friendship_id"],)
        ).fetchone()
        if not settings or not settings["active"] or not settings["ep_enabled"]:
            raise HTTPException(409, "Ebenen-Punkte sind nicht mehr aktiviert")
        new_status = "accepted" if payload.accept else "rejected"
        conn.execute(
            "UPDATE ep_proposals SET status=?,responded_at=? WHERE id=?",
            (new_status, now_ts(), proposal_id),
        )
    return {"status": new_status}


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


def topic_view(row: sqlite3.Row, user_id: int) -> dict:
    if row["group_id"] is not None:
        target_type, target_name, target_id = "group", row["group_name"], row["group_id"]
    elif row["peer_user_id"] is not None:
        target_type = "friend"
        target_name = row["peer_name"] if row["creator_id"] == user_id else row["creator_name"]
        target_id = row["peer_user_id"] if row["creator_id"] == user_id else row["creator_id"]
    else:
        target_type, target_name, target_id = "personal", "Nur für mich", None
    return {
        "id": row["id"], "creator_id": row["creator_id"], "creator_name": row["creator_name"],
        "target_type": target_type, "target_name": target_name,
        "target_id": target_id,
        "ciphertext": row["ciphertext"], "nonce": row["nonce"], "encryption_key": row["encryption_key"],
        "created_at": row["created_at"], "completed_at": row["completed_at"],
        "completed_by_name": row["completed_by_name"], "can_delete": row["creator_id"] == user_id,
    }


def topic_rows(conn: sqlite3.Connection, user_id: int, topic_id: int | None = None) -> list[sqlite3.Row]:
    topic_filter = "AND t.id=?" if topic_id is not None else ""
    params: tuple[int, ...] = (user_id, user_id, user_id) + ((topic_id,) if topic_id is not None else ())
    return conn.execute(f"""SELECT t.*,creator.name creator_name,peer.name peer_name,g.name group_name,
                         completed.name completed_by_name
                         FROM topics t
                         JOIN users creator ON creator.id=t.creator_id
                         LEFT JOIN users peer ON peer.id=t.peer_user_id
                         LEFT JOIN groups g ON g.id=t.group_id
                         LEFT JOIN users completed ON completed.id=t.completed_by_id
                         WHERE (t.creator_id=? OR t.peer_user_id=? OR EXISTS(
                             SELECT 1 FROM group_members gm WHERE gm.group_id=t.group_id AND gm.user_id=?
                         )) {topic_filter}
                         ORDER BY (t.completed_at IS NOT NULL),t.created_at DESC,t.id DESC""", params).fetchall()


@app.get("/api/topics")
def list_topics(user: sqlite3.Row = Depends(current_user)) -> list[dict]:
    with db() as conn:
        return [topic_view(row, user["id"]) for row in topic_rows(conn, user["id"])]


@app.post("/api/topics", status_code=201)
def create_topic(payload: TopicCreate, user: sqlite3.Row = Depends(current_user)) -> dict:
    if payload.peer_user_id is not None and payload.group_id is not None:
        raise HTTPException(422, "Wähle entweder einen Freund oder eine Gruppe")
    if payload.peer_user_id == user["id"]:
        raise HTTPException(422, "Nutze für eigene Themen die persönliche Liste")
    with db() as conn:
        if payload.peer_user_id is not None:
            if blocked(conn, user["id"], payload.peer_user_id) or not are_friends(conn, user["id"], payload.peer_user_id):
                raise HTTPException(403, "Gemeinsame Themen sind nur unter Freunden möglich")
        if payload.group_id is not None and not conn.execute(
            "SELECT 1 FROM group_members WHERE group_id=? AND user_id=?", (payload.group_id, user["id"]),
        ).fetchone():
            raise HTTPException(403, "Du bist nicht Mitglied dieser Gruppe")
        cur = conn.execute("""INSERT INTO topics(
            creator_id,peer_user_id,group_id,ciphertext,nonce,encryption_key,created_at
        ) VALUES (?,?,?,?,?,?,?)""", (
            user["id"], payload.peer_user_id, payload.group_id, payload.ciphertext,
            payload.nonce, payload.encryption_key, now_ts(),
        ))
    return {"id": int(cur.lastrowid)}


@app.patch("/api/topics/{topic_id}")
def update_topic(topic_id: int, payload: TopicUpdate, user: sqlite3.Row = Depends(current_user)) -> dict:
    with db() as conn:
        if not topic_rows(conn, user["id"], topic_id):
            raise HTTPException(404, "Thema nicht gefunden")
        if payload.completed:
            conn.execute("UPDATE topics SET completed_at=?,completed_by_id=? WHERE id=?", (now_ts(), user["id"], topic_id))
        else:
            conn.execute("UPDATE topics SET completed_at=NULL,completed_by_id=NULL WHERE id=?", (topic_id,))
    return {"ok": True}


@app.delete("/api/topics/{topic_id}")
def delete_topic(topic_id: int, user: sqlite3.Row = Depends(current_user)) -> dict:
    with db() as conn:
        visible = topic_rows(conn, user["id"], topic_id)
        if not visible:
            raise HTTPException(404, "Thema nicht gefunden")
        if visible[0]["creator_id"] != user["id"]:
            raise HTTPException(403, "Nur der Ersteller kann das Thema löschen")
        conn.execute("DELETE FROM topics WHERE id=?", (topic_id,))
    return {"ok": True}


def message_recipients(conn: sqlite3.Connection, payload: MessageCreate, sender_id: int) -> tuple[list[int], int | None, int]:
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
    settings = [friendship_settings_row(conn, sender_id, rid) for rid in ids]
    if any(row is None or not row["letters_enabled"] for row in settings):
        raise HTTPException(403, "Briefe sind in dieser Freundschaft nicht aktiviert")
    minimum_delay = max(int(row["min_letter_delay_seconds"]) for row in settings if row is not None)
    return ids, payload.group_id, minimum_delay


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
        if payload.random_to - payload.random_from < 60:
            raise HTTPException(422, "Zufallszeitraum muss mindestens eine Minute umfassen")
        if payload.random_to - current > MAX_RELEASE_SECONDS: raise HTTPException(422, "Zufallszeitraum liegt zu weit in der Zukunft")
        chosen = payload.random_from + secrets.randbelow(payload.random_to - payload.random_from + 1)
        return mode, chosen, payload.random_from, payload.random_to
    if payload.release_at is not None: raise HTTPException(422, "Diese Freigabeart darf keinen Zeitpunkt enthalten")
    return mode, MANUAL_RELEASE_SENTINEL, None, None


def decode_evidence_field(name: str, value: str, expected_length: int | None = None) -> bytes:
    try:
        decoded = base64.b64decode(value, validate=True)
    except (binascii.Error, ValueError):
        raise HTTPException(422, f"{name} ist kein gültiges Base64")
    if expected_length is not None and len(decoded) != expected_length:
        raise HTTPException(422, f"{name} hat eine ungültige Länge")
    return decoded


def validate_message_evidence(payload: MessageCreate) -> None:
    fields = (
        payload.commitment_salt, payload.plaintext_sha256, payload.commitment,
        payload.public_signing_key, payload.signature, payload.protocol_version,
        payload.canonical_metadata,
    )
    if not any(value is not None for value in fields):
        return
    if not all(value is not None for value in fields):
        raise HTTPException(422, "Kryptografische Nachweisdaten sind unvollständig")
    if payload.protocol_version != 1:
        raise HTTPException(422, "Unbekannte Nachweisprotokoll-Version")
    salt = decode_evidence_field("commitment_salt", payload.commitment_salt, 32)
    del salt
    decode_evidence_field("plaintext_sha256", payload.plaintext_sha256, 32)
    commitment = decode_evidence_field("commitment", payload.commitment, 32)
    nonce = decode_evidence_field("nonce", payload.nonce, 12)
    ciphertext = decode_evidence_field("ciphertext", payload.ciphertext)
    decode_evidence_field("encryption_key", payload.encryption_key, 32)
    if not ciphertext:
        raise HTTPException(422, "ciphertext darf nicht leer sein")
    if payload.attachment_ciphertext is not None:
        attachment_nonce = decode_evidence_field("attachment_nonce", payload.attachment_nonce, 12)
        decode_evidence_field("attachment_ciphertext", payload.attachment_ciphertext)
        if attachment_nonce == nonce:
            raise HTTPException(422, "Anhang und Brief müssen verschiedene Nonces verwenden")
    try:
        metadata = json.loads(payload.canonical_metadata)
    except (json.JSONDecodeError, TypeError):
        raise HTTPException(422, "canonical_metadata ist kein gültiges JSON")
    canonical = json.dumps(metadata, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    if not isinstance(metadata, dict) or canonical != payload.canonical_metadata:
        raise HTTPException(422, "canonical_metadata ist nicht kanonisch")
    public_der = decode_evidence_field("public_signing_key", payload.public_signing_key)
    signature = decode_evidence_field("signature", payload.signature)
    try:
        public_key = serialization.load_der_public_key(public_der)
        if not isinstance(public_key, ec.EllipticCurvePublicKey) or not isinstance(public_key.curve, ec.SECP256R1):
            raise ValueError("not P-256")
        digest = hashlib.sha256(
            b"GS-LM-SEAL-V1" + canonical.encode("utf-8") + commitment + ciphertext + nonce
        ).digest()
        public_key.verify(signature, digest, ec.ECDSA(utils.Prehashed(hashes.SHA256())))
    except (ValueError, TypeError, InvalidSignature):
        raise HTTPException(422, "ECDSA-Signatur oder öffentlicher P-256-Schlüssel ist ungültig")


def validate_canonical_metadata_matches_request(payload: MessageCreate, mode: str) -> None:
    if payload.canonical_metadata is None:
        return
    recipient_ids = [] if payload.group_id is not None else payload.recipient_ids.copy()
    if payload.group_id is None and payload.recipient_id is not None:
        recipient_ids.append(payload.recipient_id)
    expected = {
        "cover_note": payload.cover_note,
        "group_id": payload.group_id,
        "message_class": "letter",
        "mode": mode,
        "one_time": payload.one_time,
        "random_from": payload.random_from,
        "random_to": payload.random_to,
        "recipient_ids": sorted(set(recipient_ids)),
        "release_at": payload.release_at,
        "title": payload.title,
    }
    canonical = json.dumps(expected, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    if canonical != payload.canonical_metadata:
        raise HTTPException(422, "canonical_metadata stimmt nicht mit den Briefmetadaten überein")


def require_chat_enabled(conn: sqlite3.Connection, user_id: int, friend_id: int) -> sqlite3.Row:
    settings = friendship_settings_row(conn, user_id, friend_id)
    if not settings or blocked(conn, user_id, friend_id) or not settings["chats_enabled"]:
        raise HTTPException(403, "Chat ist in dieser Freundschaft nicht aktiviert")
    return settings


def require_letters_enabled(conn: sqlite3.Connection, user_id: int, peer_id: int) -> sqlite3.Row:
    """Letters are only usable inside an active, unblocked friendship.

    Deactivating letters therefore hides already stored letters and disables
    every action on them instead of leaving them readable.
    """
    settings = friendship_settings_row(conn, user_id, peer_id)
    if not settings or blocked(conn, user_id, peer_id) or not settings["letters_enabled"]:
        raise HTTPException(403, "Briefe sind in dieser Freundschaft nicht aktiviert")
    return settings


def letter_pair_allowed(conn: sqlite3.Connection, user_id: int, peer_id: int) -> bool:
    settings = friendship_settings_row(conn, user_id, peer_id)
    return bool(settings and not blocked(conn, user_id, peer_id) and settings["letters_enabled"])


@app.get("/api/chats")
def list_chat_threads(user: sqlite3.Row = Depends(current_user)) -> list[dict]:
    with db() as conn:
        rows = conn.execute(
            """SELECT m.*,CASE WHEN m.sender_id=? THEN m.recipient_id ELSE m.sender_id END friend_id
               FROM messages m WHERE m.message_class='instant' AND (m.sender_id=? OR m.recipient_id=?)
               ORDER BY m.created_at DESC,m.id DESC""", (user["id"], user["id"], user["id"])
        ).fetchall()
        threads: dict[int, dict] = {}
        for row in rows:
            friend_id = row["friend_id"]
            settings = friendship_settings_row(conn, user["id"], friend_id)
            if not settings or not settings["chats_enabled"] or blocked(conn, user["id"], friend_id):
                continue
            if friend_id not in threads:
                friend = conn.execute("SELECT name,avatar_emoji,display_color FROM users WHERE id=?", (friend_id,)).fetchone()
                # Rows arrive newest first, so the first row of a friend is the
                # preview for the conversation list. Instant messages are released
                # on creation and their key is already handed out by
                # /api/chats/{id}/messages, so this adds no new disclosure.
                # Sealed letters deliberately get no preview: their key stays
                # withheld until the release gate opens.
                threads[friend_id] = {
                    "friend_id": friend_id, "friend_name": friend["name"],
                    "avatar_emoji": friend["avatar_emoji"], "display_color": friend["display_color"],
                    "last_message_at": row["created_at"], "unread_count": 0,
                    "last_sender_id": row["sender_id"], "last_ciphertext": row["ciphertext"],
                    "last_nonce": row["nonce"], "last_encryption_key": row["encryption_key"],
                }
            if row["recipient_id"] == user["id"] and row["read_at"] is None:
                threads[friend_id]["unread_count"] += 1
    return list(threads.values())


@app.get("/api/chats/{friend_id}/messages")
def list_chat_messages(friend_id: int, user: sqlite3.Row = Depends(current_user)) -> list[dict]:
    with db() as conn:
        require_chat_enabled(conn, user["id"], friend_id)
        read_at = now_ts()
        conn.execute(
            """UPDATE messages SET read_at=? WHERE message_class='instant' AND sender_id=?
               AND recipient_id=? AND read_at IS NULL""", (read_at, friend_id, user["id"]),
        )
        rows = conn.execute(
            """SELECT id,sender_id,recipient_id,ciphertext,nonce,encryption_key,created_at,read_at
               FROM messages WHERE message_class='instant' AND
               ((sender_id=? AND recipient_id=?) OR (sender_id=? AND recipient_id=?))
               ORDER BY created_at,id""", (user["id"], friend_id, friend_id, user["id"]),
        ).fetchall()
    return [dict(row) for row in rows]


@app.post("/api/chats/{friend_id}/messages", status_code=201)
def create_chat_message(
    friend_id: int, payload: ChatMessageCreate, user: sqlite3.Row = Depends(current_user),
) -> dict:
    current = now_ts()
    with db() as conn:
        require_chat_enabled(conn, user["id"], friend_id)
        cur = conn.execute(
            """INSERT INTO messages(sender_id,recipient_id,ciphertext,nonce,encryption_key,release_at,
               created_at,released_at,title,mode,message_class)
               VALUES (?,?,?,?,?,?,?,?,?,'instant','instant')""",
            (user["id"], friend_id, payload.ciphertext, payload.nonce, payload.encryption_key,
             current, current, current, ""),
        )
    return {"id": int(cur.lastrowid)}


# ---------------------------------------------------------------------------
# Freundesfunke: anonymous, one-way, positive. The sender is stored for abuse
# handling but never appears in any recipient-facing response — and reading
# never filters on friendship or blocks, because a spark that vanished right
# after unfriending X would reveal X as its sender. Muting one anonymous
# sender via one of their sparks is the recipient's only removal tool.
# ---------------------------------------------------------------------------


@app.post("/api/sparks", status_code=201)
def send_spark(payload: SparkCreate, user: sqlite3.Row = Depends(current_user)) -> dict:
    if payload.recipient_id == user["id"]:
        raise HTTPException(422, "Ein Funke an dich selbst ist nicht möglich")
    current = now_ts()
    with db() as conn:
        if blocked(conn, user["id"], payload.recipient_id) or not are_friends(conn, user["id"], payload.recipient_id):
            raise HTTPException(403, "Funken sind nur unter nicht blockierten Freunden möglich")
        day_ago = current - 86400
        pair = conn.execute(
            "SELECT COUNT(*) c FROM sparks WHERE sender_id=? AND recipient_id=? AND created_at>=?",
            (user["id"], payload.recipient_id, day_ago),
        ).fetchone()["c"]
        total = conn.execute(
            "SELECT COUNT(*) c FROM sparks WHERE sender_id=? AND created_at>=?",
            (user["id"], day_ago),
        ).fetchone()["c"]
        if pair >= SPARK_PAIR_DAILY_LIMIT or total >= SPARK_SENDER_DAILY_LIMIT:
            raise HTTPException(429, "Zu viele Funken. Bitte später wieder")
        # A muted sender is accepted silently: a rejection would tell the sender
        # that exactly this recipient muted them.
        cur = conn.execute(
            "INSERT INTO sparks(sender_id,recipient_id,ciphertext,nonce,encryption_key,created_at) VALUES (?,?,?,?,?,?)",
            (user["id"], payload.recipient_id, payload.ciphertext, payload.nonce, payload.encryption_key, current),
        )
    return {"id": int(cur.lastrowid)}


@app.get("/api/sparks")
def list_sparks(user: sqlite3.Row = Depends(current_user)) -> list[dict]:
    # Anonymity by construction: no sender field, no timestamp. Adding either
    # would open a correlation channel toward the sender's identity.
    with db() as conn:
        rows = conn.execute(
            """SELECT s.id,s.ciphertext,s.nonce,s.encryption_key,s.opened_at FROM sparks s
               WHERE s.recipient_id=? AND NOT EXISTS(
                   SELECT 1 FROM spark_mutes m WHERE m.recipient_id=s.recipient_id AND m.sender_id=s.sender_id
               ) ORDER BY s.id DESC""",
            (user["id"],),
        ).fetchall()
    return [{"id": r["id"], "ciphertext": r["ciphertext"], "nonce": r["nonce"],
             "encryption_key": r["encryption_key"], "opened": r["opened_at"] is not None} for r in rows]


def recipient_spark(conn: sqlite3.Connection, spark_id: int, user_id: int) -> sqlite3.Row:
    row = conn.execute("SELECT * FROM sparks WHERE id=? AND recipient_id=?", (spark_id, user_id)).fetchone()
    if not row:
        raise HTTPException(404, "Funke nicht gefunden")
    return row


@app.post("/api/sparks/{spark_id}/open")
def open_spark(spark_id: int, user: sqlite3.Row = Depends(current_user)) -> dict:
    with db() as conn:
        recipient_spark(conn, spark_id, user["id"])
        conn.execute("UPDATE sparks SET opened_at=? WHERE id=? AND opened_at IS NULL", (now_ts(), spark_id))
    return {"ok": True, "opened": True}


@app.post("/api/sparks/{spark_id}/mute")
def mute_spark_sender(spark_id: int, user: sqlite3.Row = Depends(current_user)) -> dict:
    # Mutes the anonymous sender of this spark for this recipient. The response
    # confirms nothing about who was muted.
    with db() as conn:
        row = recipient_spark(conn, spark_id, user["id"])
        conn.execute(
            "INSERT OR IGNORE INTO spark_mutes(recipient_id,sender_id,created_at) VALUES (?,?,?)",
            (user["id"], row["sender_id"], now_ts()),
        )
    return {"ok": True}


@app.post("/api/sparks/{spark_id}/report")
def report_spark(spark_id: int, user: sqlite3.Row = Depends(current_user)) -> dict:
    with db() as conn:
        recipient_spark(conn, spark_id, user["id"])
        conn.execute(
            "INSERT INTO spark_reports(spark_id,reporter_id,created_at) VALUES (?,?,?)",
            (spark_id, user["id"], now_ts()),
        )
    return {"ok": True}


@app.get("/api/sparks/outbox")
def spark_outbox(user: sqlite3.Row = Depends(current_user)) -> list[dict]:
    # The sender learns exactly one bit per spark and no time: underway or opened.
    with db() as conn:
        rows = conn.execute(
            """SELECT s.id,s.recipient_id,u.name recipient_name,s.opened_at FROM sparks s
               JOIN users u ON u.id=s.recipient_id WHERE s.sender_id=? ORDER BY s.id DESC""",
            (user["id"],),
        ).fetchall()
    return [{"id": r["id"], "recipient_id": r["recipient_id"], "recipient_name": r["recipient_name"],
             "status": "geöffnet" if r["opened_at"] is not None else "unterwegs"} for r in rows]


@app.post("/api/test/letters/{message_id}/advance")
def advance_test_letter(message_id: int, payload: TestAdvance, user: sqlite3.Row = Depends(current_user)) -> dict:
    """Test-only fast-forward: pulls a time-gated release into the present.

    Exists only on SERVER_ROLE=test and only between two creative-entitled
    accounts. It never touches consent gates: mutual, manual and presence
    letters keep their real flow, and the letter itself stays untouched apart
    from its release clock. Every use is recorded in test_advances.
    """
    if SERVER_ROLE != "test":
        raise HTTPException(404, "Nicht gefunden")
    if payload.phase != "release":
        raise HTTPException(422, "Unbekannte Phase")
    current = now_ts()
    with db() as conn:
        row = conn.execute(
            "SELECT * FROM messages WHERE id=? AND message_class='letter' AND (sender_id=? OR recipient_id=?)",
            (message_id, user["id"], user["id"]),
        ).fetchone()
        if not row:
            raise HTTPException(404, "Brief nicht gefunden")
        other = row["recipient_id"] if row["sender_id"] == user["id"] else row["sender_id"]
        entitled = conn.execute(
            "SELECT COUNT(*) c FROM users WHERE id IN (?,?) AND creative_entitled=1",
            (user["id"], other),
        ).fetchone()["c"]
        if entitled != 2:
            raise HTTPException(403, "Vorspulen braucht die Kreativ-Berechtigung beider Konten")
        if not letter_pair_allowed(conn, user["id"], other):
            raise HTTPException(403, "Briefe sind in dieser Freundschaft nicht aktiviert")
        if row["mode"] not in ("timed", "random"):
            raise HTTPException(422, "Nur zeit- oder zufallsgesteuerte Briefe lassen sich vorspulen")
        if unlocked(conn, row, current):
            return {"advanced": False, "unlocked": True}
        conn.execute(
            "UPDATE messages SET release_at=?, minimum_release_at=MIN(COALESCE(minimum_release_at,?),?) WHERE id=?",
            (current, current, current, message_id),
        )
        conn.execute(
            "INSERT INTO test_advances(message_id,actor_id,action,created_at) VALUES (?,?,?,?)",
            (message_id, user["id"], "release", current),
        )
        updated = conn.execute("SELECT * FROM messages WHERE id=?", (message_id,)).fetchone()
        return {"advanced": True, "unlocked": unlocked(conn, updated, current)}


@app.post("/api/messages", status_code=201)
def create_message(payload: MessageCreate, user: sqlite3.Row = Depends(current_user)) -> dict:
    current = now_ts()
    validate_message_evidence(payload)
    mode, stored_release, random_from, random_to = release_fields(payload, current)
    validate_canonical_metadata_matches_request(payload, mode)
    attachment_values = (payload.attachment_name, payload.attachment_mime, payload.attachment_ciphertext, payload.attachment_nonce)
    if any(attachment_values) and not all(attachment_values): raise HTTPException(422, "Anhangsdaten sind unvollständig")
    with db() as conn:
        recipients, group_id, minimum_delay = message_recipients(conn, payload, user["id"])
        if mode == "timed" and stored_release < current + minimum_delay - MINIMUM_DELAY_TOLERANCE:
            raise HTTPException(422, "Freigabezeit unterschreitet die vereinbarte Mindestverzögerung")
        if mode == "random" and random_from is not None and random_from < current + minimum_delay - MINIMUM_DELAY_TOLERANCE:
            raise HTTPException(422, "Zufallsfenster unterschreitet die vereinbarte Mindestverzögerung")
        ids = []
        for recipient_id in recipients:
            columns = """sender_id,recipient_id,ciphertext,nonce,encryption_key,release_at,created_at,
                manual_release,released_at,title,mode,one_time,read_at,sender_approved,recipient_approved,
                random_from,random_to,attachment_name,attachment_mime,attachment_ciphertext,attachment_nonce,
                group_id,cover_note,commitment_salt,plaintext_sha256,commitment,signing_public_key,signature,
                protocol_version,canonical_metadata,minimum_release_at"""
            values = (
                user["id"], recipient_id, payload.ciphertext, payload.nonce, payload.encryption_key,
                stored_release, current, int(mode == "manual"), None, payload.title, mode,
                int(payload.one_time), None, 0, 0, random_from, random_to, payload.attachment_name,
                payload.attachment_mime, payload.attachment_ciphertext, payload.attachment_nonce, group_id,
                payload.cover_note, payload.commitment_salt, payload.plaintext_sha256, payload.commitment,
                payload.public_signing_key, payload.signature, payload.protocol_version,
                payload.canonical_metadata, current + minimum_delay,
            )
            cur = conn.execute(f"""INSERT INTO messages(
                {columns}) VALUES ({','.join('?' for _ in values)})""", values)
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
    if row["minimum_release_at"] is not None and current < row["minimum_release_at"]:
        result = False
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
        "id": row["id"], "title": row["title"], "cover_note": row["cover_note"],
        "message_class": row["message_class"], "created_at": row["created_at"], "mode": mode,
        "release_at": row["release_at"] if mode == "timed" else None,
        "random_from": row["random_from"], "random_to": row["random_to"], "manual_release": mode == "manual",
        "released_at": row["released_at"], "unlocked": is_open, "one_time": bool(row["one_time"]),
        "read_at": row["read_at"], "sender_approved": bool(row["sender_approved"]),
        "recipient_approved": bool(row["recipient_approved"]), "attachment_name": row["attachment_name"],
        "attachment_mime": row["attachment_mime"], "group_id": row["group_id"], "reactions": reactions(conn, row["id"]),
        "proof_status": "sealed" if row["protocol_version"] else "legacy",
    }
    if incoming:
        result["sender_id"] = row["sender_id"]
        result["sender_name"] = row["sender_name"]
    else:
        result["recipient_id"] = row["recipient_id"]
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
        # The id tiebreaker keeps the order stable: one letter addressed to several
        # recipients stores rows with an identical created_at, and a conversation
        # thread that merges letters with chat needs a deterministic sequence.
        rows = conn.execute("""SELECT m.*,u.name sender_name FROM messages m JOIN users u ON u.id=m.sender_id
                            WHERE m.recipient_id=? AND m.message_class='letter'
                            ORDER BY m.created_at DESC,m.id DESC LIMIT 300""", (user["id"],)).fetchall()
        rows = [row for row in rows if letter_pair_allowed(conn, user["id"], row["sender_id"])]
        return [message_meta(conn, row, now_ts(), True) for row in rows]


@app.get("/api/outbox")
def list_outbox(user: sqlite3.Row = Depends(current_user)) -> list[dict]:
    with db() as conn:
        rows = conn.execute("""SELECT m.*,u.name recipient_name FROM messages m JOIN users u ON u.id=m.recipient_id
                            WHERE m.sender_id=? AND m.message_class='letter'
                            ORDER BY m.created_at DESC,m.id DESC LIMIT 300""", (user["id"],)).fetchall()
        rows = [row for row in rows if letter_pair_allowed(conn, user["id"], row["recipient_id"])]
        return [message_meta(conn, row, now_ts(), False) for row in rows]


def proof_message_view(conn: sqlite3.Connection, row: sqlite3.Row, current: int) -> dict:
    is_open = unlocked(conn, row, current)
    public_key_fingerprint = None
    release_key = None
    release_key_sha256 = None
    evidence = None
    if row["protocol_version"] is not None:
        public_key = base64.b64decode(row["signing_public_key"], validate=True)
        public_key_fingerprint = hashlib.sha256(public_key).hexdigest()
        if is_open:
            release_key = row["encryption_key"]
            release_key_sha256 = hashlib.sha256(
                base64.b64decode(release_key, validate=True)
            ).hexdigest()
        attachment = None
        if row["attachment_ciphertext"] is not None:
            attachment = {
                "name": row["attachment_name"], "mime": row["attachment_mime"],
                "ciphertext": row["attachment_ciphertext"], "nonce": row["attachment_nonce"],
            }
        evidence = {
            "protocol_version": row["protocol_version"],
            "canonical_metadata": row["canonical_metadata"],
            "commitment_salt": row["commitment_salt"],
            "plaintext_sha256": row["plaintext_sha256"],
            "commitment": row["commitment"],
            "public_signing_key": row["signing_public_key"],
            "public_key_fingerprint": public_key_fingerprint,
            "signature": row["signature"],
            "ciphertext": row["ciphertext"], "nonce": row["nonce"],
            "attachment": attachment,
            "release_key": release_key, "release_key_sha256": release_key_sha256,
        }
    return {
        "id": row["id"], "message_class": row["message_class"], "title": row["title"],
        "cover_note": row["cover_note"], "sender": {"id": row["sender_id"], "name": row["sender_name"]},
        "recipient": {"id": row["recipient_id"], "name": row["recipient_name"]},
        "created_at": row["created_at"], "legacy": evidence is None,
        "release_rule": {
            "mode": row["mode"], "release_at": row["release_at"] if row["mode"] == "timed" else None,
            "random_from": row["random_from"], "random_to": row["random_to"],
            "released_at": row["released_at"], "unlocked": is_open,
        },
        "evidence": evidence,
    }


def proof_export(conn: sqlite3.Connection, rows: list[sqlite3.Row], current: int) -> dict:
    return {
        "format": "gs-layermaxxing-verification", "version": 1, "exported_at": current,
        "messages": [proof_message_view(conn, row, current) for row in rows],
    }


def proof_row(conn: sqlite3.Connection, message_id: int, user_id: int) -> sqlite3.Row:
    row = conn.execute(
        """SELECT m.*,s.name sender_name,r.name recipient_name FROM messages m
           JOIN users s ON s.id=m.sender_id JOIN users r ON r.id=m.recipient_id
           WHERE m.id=? AND m.message_class='letter' AND (m.sender_id=? OR m.recipient_id=?)""",
        (message_id, user_id, user_id),
    ).fetchone()
    if not row:
        raise HTTPException(404, "Brief nicht gefunden")
    return row


@app.get("/api/messages/{message_id}/proof")
def get_message_proof(message_id: int, user: sqlite3.Row = Depends(current_user)) -> dict:
    with db() as conn:
        current = now_ts()
        return proof_export(conn, [proof_row(conn, message_id, user["id"])], current)


@app.get("/api/proofs/pending")
def get_pending_proofs(user: sqlite3.Row = Depends(current_user)) -> dict:
    with db() as conn:
        current = now_ts()
        rows = conn.execute(
            """SELECT m.*,s.name sender_name,r.name recipient_name FROM messages m
               JOIN users s ON s.id=m.sender_id JOIN users r ON r.id=m.recipient_id
               WHERE m.message_class='letter' AND m.protocol_version IS NOT NULL
                 AND (m.sender_id=? OR m.recipient_id=?) ORDER BY m.created_at DESC,m.id DESC""",
            (user["id"], user["id"]),
        ).fetchall()
        pending = [row for row in rows if not unlocked(conn, row, current)]
        return proof_export(conn, pending, current)


@app.get("/api/proofs/{message_id}")
def get_proof(message_id: int, user: sqlite3.Row = Depends(current_user)) -> dict:
    return get_message_proof(message_id, user)


@app.get("/api/messages/{message_id}/content")
def message_content(message_id: int, user: sqlite3.Row = Depends(current_user)) -> dict:
    with db() as conn:
        row = conn.execute("SELECT * FROM messages WHERE id=? AND recipient_id=? AND message_class='letter'", (message_id, user["id"])).fetchone()
        if not row: raise HTTPException(404, "Nachricht nicht gefunden")
        require_letters_enabled(conn, user["id"], row["sender_id"])
        if not unlocked(conn, row, now_ts()): raise HTTPException(423, "Nachricht ist noch gesperrt")
        if row["one_time"] and row["read_at"] is not None: raise HTTPException(410, "Einmal-Nachricht wurde bereits geöffnet")
        read_at = row["read_at"] or now_ts()
        conn.execute("UPDATE messages SET read_at=? WHERE id=? AND read_at IS NULL", (read_at, message_id))
        return {"ciphertext": row["ciphertext"], "nonce": row["nonce"], "encryption_key": row["encryption_key"],
                "attachment_name": row["attachment_name"], "attachment_mime": row["attachment_mime"],
                "attachment_ciphertext": row["attachment_ciphertext"], "attachment_nonce": row["attachment_nonce"],
                "canonical_metadata": row["canonical_metadata"]}


@app.post("/api/messages/{message_id}/release")
def release_message(message_id: int, user: sqlite3.Row = Depends(current_user)) -> dict:
    with db() as conn:
        row = conn.execute("SELECT * FROM messages WHERE id=? AND sender_id=?", (message_id, user["id"])).fetchone()
        if not row: raise HTTPException(404, "Nachricht nicht gefunden")
        require_letters_enabled(conn, user["id"], row["recipient_id"])
        if row["mode"] != "manual": raise HTTPException(409, "Diese Nachricht wird nicht manuell freigegeben")
        released_at = row["released_at"] or now_ts()
        conn.execute("UPDATE messages SET released_at=? WHERE id=?", (released_at, message_id))
    return {"released_at": released_at}


@app.post("/api/messages/{message_id}/approve")
def approve_message(message_id: int, user: sqlite3.Row = Depends(current_user)) -> dict:
    with db() as conn:
        row = conn.execute("SELECT * FROM messages WHERE id=? AND (sender_id=? OR recipient_id=?)", (message_id, user["id"], user["id"])).fetchone()
        if not row: raise HTTPException(404, "Nachricht nicht gefunden")
        peer_id = row["recipient_id"] if row["sender_id"] == user["id"] else row["sender_id"]
        require_letters_enabled(conn, user["id"], peer_id)
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
        require_letters_enabled(conn, user["id"], row["recipient_id"])
        if unlocked(conn, row, now_ts()): raise HTTPException(409, "Freigegebene Nachrichten können nicht zurückgezogen werden")
        conn.execute("DELETE FROM messages WHERE id=?", (message_id,))
    return {"ok": True}


@app.put("/api/messages/{message_id}/reaction")
def react(message_id: int, payload: ReactionCreate, user: sqlite3.Row = Depends(current_user)) -> dict:
    with db() as conn:
        row = conn.execute("SELECT * FROM messages WHERE id=? AND (sender_id=? OR recipient_id=?)", (message_id, user["id"], user["id"])).fetchone()
        if not row: raise HTTPException(404, "Nachricht nicht gefunden")
        peer_id = row["recipient_id"] if row["sender_id"] == user["id"] else row["sender_id"]
        require_letters_enabled(conn, user["id"], peer_id)
        conn.execute("""INSERT INTO message_reactions(message_id,user_id,emoji,created_at) VALUES (?,?,?,?)
                     ON CONFLICT(message_id,user_id) DO UPDATE SET emoji=excluded.emoji,created_at=excluded.created_at""",
                     (message_id, user["id"], payload.emoji, now_ts()))
    return {"ok": True}
