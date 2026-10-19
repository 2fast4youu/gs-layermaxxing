#!/usr/bin/python3.12
"""Auto-deploy controller for the Gerfried test backend (user systemd, stdlib only).

One run = one decision: fetch the configured branch into a private bare git cache, take the exact
remote HEAD SHA, and deploy it only if *that* SHA has a completed, successful run of the configured
workflow. A deploy builds an immutable release from `git archive` (never from a working tree), an
isolated venv, backs up the live SQLite DB (0600), migrates + smoke-tests a separate *copy*, then
switches an atomic `current` symlink plus a dedicated service drop-in and restarts only the GS
service. A failed restart/readback restores the previous symlink/drop-in and restarts the old code;
the live DB is never restored from a backup automatically.

See docs/gerfried-autoupdate.md.
"""
from __future__ import annotations

import argparse
import errno
import fcntl
import hashlib
import io
import json
import os
import shlex
import shutil
import socket
import sqlite3
import subprocess
import sys
import tarfile
import time
import urllib.request
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path

DEFAULT_ROOT = Path.home() / ".local/share/gs-layermaxxing"


@dataclass
class Config:
    root: Path = DEFAULT_ROOT
    repo_url: str = "https://github.com/2fast4youu/gs-layermaxxing.git"
    repo_slug: str = "2fast4youu/gs-layermaxxing"
    branch: str = "gerfried-erweiterung"
    workflow: str = "gerfried-apk.yml"
    service: str = "gs-layermaxxing.service"
    unit_dir: Path = Path.home() / ".config/systemd/user"
    dropin_name: str = "60-gerfried-autodeploy.conf"
    db_name: str = "layermaxxing.db"
    python: str = "/usr/bin/python3.12"
    uv: str = str(Path.home() / ".local/bin/uv")
    git: str = "/usr/bin/git"
    gh: str = "/usr/bin/gh"
    systemctl: str = "/usr/bin/systemctl"
    notify_command: list[str] = field(default_factory=list)  # argv; the message is appended as last arg
    health_timeout: float = 40.0
    deps_timeout: float = 600.0

    @property
    def deploy_dir(self) -> Path: return self.root / "deploy"
    @property
    def cache_dir(self) -> Path: return self.deploy_dir / "repo.git"
    @property
    def state_file(self) -> Path: return self.deploy_dir / "state.json"
    @property
    def lock_file(self) -> Path: return self.deploy_dir / "autoupdate.lock"
    @property
    def releases_dir(self) -> Path: return self.root / "releases"
    @property
    def backups_dir(self) -> Path: return self.root / "backups"
    @property
    def staging_dir(self) -> Path: return self.deploy_dir / "staging"
    @property
    def current(self) -> Path: return self.root / "current"
    @property
    def db_path(self) -> Path: return self.root / self.db_name
    @property
    def unit_file(self) -> Path: return self.unit_dir / self.service
    @property
    def dropin_dir(self) -> Path: return self.unit_dir / f"{self.service}.d"
    @property
    def dropin(self) -> Path: return self.dropin_dir / self.dropin_name

    @classmethod
    def load(cls, path: Path | None) -> "Config":
        cfg = cls()
        path = path or (DEFAULT_ROOT / "deploy/config.json")
        if path.is_file():
            for key, value in json.loads(path.read_text()).items():
                if not hasattr(cfg, key):
                    raise DeployError(f"unknown config key {key!r}")
                default = getattr(cls(), key)
                setattr(cfg, key, Path(value).expanduser() if isinstance(default, Path) else value)
        return cfg


class DeployError(Exception):
    pass


class LockBusy(Exception):
    pass


def now_iso() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def log(msg: str) -> None:
    print(f"gerfried-autoupdate: {msg}", flush=True)


def atomic_write(path: Path, data: str, mode: int = 0o600) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(f".{path.name}.tmp{os.getpid()}")
    fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, mode)
    with os.fdopen(fd, "w") as fh:
        fh.write(data)
        fh.flush()
        os.fsync(fh.fileno())
    os.chmod(tmp, mode)
    os.replace(tmp, path)


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def safe_extract(tar_bytes: bytes, dest: Path, required: tuple[str, ...] = ("server/app/main.py",)) -> int:
    """Extract a `git archive` tar into a new dir; only plain files/dirs, no links, no traversal."""
    if dest.exists():
        raise DeployError(f"release target already exists: {dest}")
    with tarfile.open(fileobj=io.BytesIO(tar_bytes), mode="r:") as tar:
        members = tar.getmembers()
        for m in members:
            name = m.name
            parts = Path(name).parts
            if name.startswith("/") or ".." in parts or "\\" in name or not parts:
                raise DeployError(f"unsafe path in archive: {name!r}")
            if not (m.isfile() or m.isdir()):
                raise DeployError(f"refusing non-regular archive member: {name!r}")
        names = {m.name.rstrip("/") for m in members if m.isfile()}
        for req in required:
            if req not in names:
                raise DeployError(f"archive misses required file {req}")
        if not any(n.startswith("server/app/geometry/") for n in names):
            raise DeployError("archive misses server/app/geometry/")
        dest.mkdir(parents=True)
        tar.extractall(dest, filter="data")
    return len(names)


def freeze_tree(path: Path) -> None:
    """Make a release read-only (files 0444/0555, dirs 0555).

    Refuses hardlinked files: chmod on a shared inode (e.g. a uv wheel-cache link) would change other envs too.
    """
    for dirpath, dirnames, filenames in os.walk(path, topdown=False):
        for name in filenames:
            p = Path(dirpath, name)
            if p.is_symlink():
                continue
            if p.lstat().st_nlink > 1:
                raise DeployError(f"refusing to freeze hardlinked file {p.relative_to(path)}")
            os.chmod(p, 0o555 if os.access(p, os.X_OK) else 0o444)
        os.chmod(dirpath, 0o555)


def sqlite_summary(db: Path) -> dict:
    """Integrity/FK state + table row counts (numbers only, never values)."""
    conn = sqlite3.connect(f"file:{db}?mode=ro", uri=True)
    try:
        integrity = conn.execute("PRAGMA integrity_check").fetchone()[0]
        fk_violations = len(conn.execute("PRAGMA foreign_key_check").fetchall())
        tables = [r[0] for r in conn.execute(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")]
        counts = {t: conn.execute(f'SELECT COUNT(*) FROM "{t}"').fetchone()[0] for t in tables}
    finally:
        conn.close()
    return {"integrity": integrity, "fk_violations": fk_violations, "rows": counts}


def compare_db(before: dict, after: dict) -> None:
    if before["integrity"] != "ok":
        raise DeployError("live DB backup failed integrity_check")
    if after["integrity"] != "ok":
        raise DeployError("migrated copy failed integrity_check")
    if after["fk_violations"] > before["fk_violations"]:
        raise DeployError(f"migration added FK violations ({before['fk_violations']} -> {after['fk_violations']})")
    for table, n in before["rows"].items():
        if table not in after["rows"]:
            raise DeployError(f"migration dropped table {table}")
        if after["rows"][table] < n:
            raise DeployError(f"migration lost rows in {table} ({n} -> {after['rows'][table]})")


def backup_sqlite(src: Path, dest: Path) -> None:
    """Consistent online backup (sqlite backup API) into a new 0600 file."""
    dest.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.close(os.open(dest, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600))
    s = sqlite3.connect(f"file:{src}?mode=ro", uri=True)
    d = sqlite3.connect(dest)
    try:
        s.backup(d)
    finally:
        d.close()
        s.close()
    os.chmod(dest, 0o600)


def free_port() -> int:
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def http_json(url: str, timeout: float = 3.0) -> dict:
    with urllib.request.urlopen(url, timeout=timeout) as resp:
        if resp.status != 200:
            raise DeployError(f"{url} -> HTTP {resp.status}")
        return json.loads(resp.read())


def check_info(health: dict, info: dict) -> dict:
    if health.get("status") != "ok":
        raise DeployError(f"/health not ok: {health.get('status')!r}")
    if info.get("role") != "test":
        raise DeployError(f"server-info role is {info.get('role')!r}, expected 'test'")
    grid = info.get("grid_version")
    if not isinstance(grid, int) or isinstance(grid, bool) or grid < 1:
        raise DeployError(f"server-info grid_version invalid: {grid!r}")
    return {"version": info.get("version"), "grid_version": grid}


def poll_server(base: str, timeout: float, proc: subprocess.Popen | None = None) -> dict:
    deadline = time.monotonic() + timeout
    last: Exception | None = None
    while time.monotonic() < deadline:
        if proc is not None and proc.poll() is not None:
            raise DeployError(f"candidate server exited early with code {proc.returncode}")
        try:
            return check_info(http_json(base + "/health"), http_json(base + "/api/server-info"))
        except (OSError, ValueError, DeployError) as exc:
            last = exc
            time.sleep(0.5)
    raise DeployError(f"no healthy answer from {base}: {last}")


class Controller:
    def __init__(self, cfg: Config):
        self.cfg = cfg

    # ---------- external commands (overridden in unit tests) ----------
    def run(self, argv: list[str], *, timeout: float = 120, env: dict | None = None, cwd: Path | None = None,
            binary: bool = False) -> subprocess.CompletedProcess:
        res = subprocess.run(argv, capture_output=True, timeout=timeout, env=env, cwd=cwd, text=not binary)
        if res.returncode != 0:
            err = res.stderr if not binary else res.stderr.decode(errors="replace")
            raise DeployError(f"{Path(argv[0]).name} {argv[1] if len(argv) > 1 else ''} failed: {err.strip()[-400:]}")
        return res

    def git(self, *args: str, binary: bool = False, timeout: float = 120) -> subprocess.CompletedProcess:
        return self.run([self.cfg.git, f"--git-dir={self.cfg.cache_dir}", *args], binary=binary, timeout=timeout)

    def fetch_remote_sha(self) -> str:
        cache = self.cfg.cache_dir
        if not (cache / "HEAD").exists():
            cache.mkdir(parents=True, exist_ok=True)
            self.run([self.cfg.git, "init", "--bare", "--quiet", str(cache)])
        ref = f"refs/remotes/origin/{self.cfg.branch}"
        # explicit URL + refspec: no configured remotes, no tags, nothing outside this cache is touched
        self.git("fetch", "--quiet", "--no-tags", "--force", self.cfg.repo_url,
                 f"+refs/heads/{self.cfg.branch}:{ref}", timeout=300)
        sha = self.git("rev-parse", "--verify", f"{ref}^{{commit}}").stdout.strip()
        if len(sha) != 40:
            raise DeployError(f"unexpected rev-parse output {sha!r}")
        return sha

    def ci_runs(self, sha: str) -> list[dict]:
        path = (f"repos/{self.cfg.repo_slug}/actions/workflows/{self.cfg.workflow}/runs"
                f"?head_sha={sha}&per_page=30")
        return json.loads(self.run([self.cfg.gh, "api", path]).stdout).get("workflow_runs", [])

    def archive(self, sha: str) -> bytes:
        return self.git("archive", "--format=tar", sha, "server", binary=True).stdout

    def build_venv(self, release: Path) -> None:
        env = {**os.environ, "UV_PYTHON_DOWNLOADS": "never", "VIRTUAL_ENV": "",
               "UV_LINK_MODE": "copy"}  # private copies: freeze_tree must never chmod shared cache inodes
        self.run([self.cfg.uv, "venv", "--quiet", "--python", self.cfg.python, str(release / ".venv")], env=env)
        self.run([self.cfg.uv, "pip", "install", "--quiet", "--python", str(release / ".venv/bin/python"),
                  "-r", str(release / "server/requirements.txt")], env=env, timeout=self.cfg.deps_timeout)

    def candidate_env(self, db: Path) -> dict:
        env = {k: v for k, v in os.environ.items() if k in ("PATH", "HOME", "LANG", "LC_ALL", "TMPDIR")}
        return {**env, "DB_PATH": str(db), "SERVER_ROLE": "test", "PYTHONDONTWRITEBYTECODE": "1"}

    def migrate_copy(self, release: Path, db: Path) -> None:
        self.run([str(release / ".venv/bin/python"), "-c", "from app import main; main.initialize_database()"],
                 env=self.candidate_env(db), cwd=release / "server", timeout=120)

    def smoke(self, release: Path, db: Path) -> dict:
        port = free_port()
        proc = subprocess.Popen(
            [str(release / ".venv/bin/uvicorn"), "app.main:app", "--host", "127.0.0.1", "--port", str(port)],
            cwd=release / "server", env=self.candidate_env(db),
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            return poll_server(f"http://127.0.0.1:{port}", self.cfg.health_timeout, proc)
        finally:  # only our own child, never a global kill
            proc.terminate()
            try:
                proc.wait(10)
            except subprocess.TimeoutExpired:
                proc.kill()
                proc.wait(5)

    def systemctl(self, *args: str) -> str:
        return self.run([self.cfg.systemctl, "--user", *args], timeout=90).stdout

    def live_readback(self, port: int) -> dict:
        return poll_server(f"http://127.0.0.1:{port}", self.cfg.health_timeout)

    def main_pid_cwd(self) -> Path | None:
        pid = self.systemctl("show", "-p", "MainPID", "--value", self.cfg.service).strip()
        if not pid.isdigit() or pid == "0":
            return None
        try:
            return Path(os.readlink(f"/proc/{pid}/cwd")).resolve()
        except OSError:
            return None

    def notify(self, message: str) -> None:
        if not self.cfg.notify_command:
            return
        try:
            subprocess.run([*self.cfg.notify_command, message], timeout=60, capture_output=True)
        except (OSError, subprocess.TimeoutExpired) as exc:
            log(f"notifier failed: {exc}")

    # ---------- state ----------
    def load_state(self) -> dict:
        try:
            return json.loads(self.cfg.state_file.read_text())
        except FileNotFoundError:
            return {}

    def save_state(self, state: dict) -> None:
        state["updated_at"] = now_iso()
        atomic_write(self.cfg.state_file, json.dumps(state, indent=2, sort_keys=True) + "\n")

    # ---------- service unit ----------
    def service_exec_args(self) -> list[str]:
        """uvicorn args of the effective ExecStart (unit + drop-ins except ours), so host/port stay unchanged."""
        files = [self.cfg.unit_file]
        if self.cfg.dropin_dir.is_dir():
            files += sorted(p for p in self.cfg.dropin_dir.glob("*.conf") if p.name != self.cfg.dropin_name)
        exec_start: str | None = None
        for f in files:
            for line in f.read_text().splitlines():
                line = line.strip()
                if line.startswith("ExecStart="):
                    value = line[len("ExecStart="):].strip()
                    exec_start = value or None
        if not exec_start:
            raise DeployError("no ExecStart found in service unit")
        argv = shlex.split(exec_start.lstrip("-@:+!"))
        if not argv or Path(argv[0]).name != "uvicorn" or "app.main:app" not in argv:
            raise DeployError(f"unexpected ExecStart, refusing to rewrite: {argv[:2]}")
        return argv[1:]

    def port_from_args(self, args: list[str]) -> int:
        if "--port" in args:
            return int(args[args.index("--port") + 1])
        for a in args:
            if a.startswith("--port="):
                return int(a.split("=", 1)[1])
        return 8000

    def dropin_text(self, args: list[str]) -> str:
        cur = self.cfg.current
        return ("# Managed by gerfried-autoupdate.py - points the GS service at the CI-verified release.\n"
                "[Service]\n"
                f"WorkingDirectory={cur}/server\n"
                "ExecStart=\n"
                f"ExecStart={shlex.join([str(cur / '.venv/bin/uvicorn'), *args])}\n")

    def set_current(self, target: Path | None) -> None:
        cur = self.cfg.current
        if target is None:
            if cur.is_symlink():
                cur.unlink()
            return
        if cur.exists() and not cur.is_symlink():
            raise DeployError(f"{cur} exists and is not a symlink")
        tmp = cur.with_name(f".current.tmp{os.getpid()}")
        if tmp.is_symlink() or tmp.exists():
            tmp.unlink()
        tmp.symlink_to(target)
        os.replace(tmp, cur)

    def set_dropin(self, text: str | None) -> None:
        if text is None:
            if self.cfg.dropin.exists():
                self.cfg.dropin.unlink()
        else:
            atomic_write(self.cfg.dropin, text, 0o644)

    # ---------- decision ----------
    def green_run(self, sha: str) -> dict | None:
        """Newest matching run (run_number, run_attempt, id) must be completed/success; older greens don't count."""
        matching = [run for run in self.ci_runs(sha)
                    if run.get("head_sha") == sha and run.get("head_branch") == self.cfg.branch
                    and str(run.get("path", "")).endswith(self.cfg.workflow)]
        if not matching:
            return None
        newest = max(matching, key=lambda run: (int(run.get("run_number") or 0), int(run.get("run_attempt") or 0),
                                                int(run.get("id") or 0)))
        if newest.get("status") == "completed" and newest.get("conclusion") == "success":
            return newest
        return None

    def plan(self, force: bool) -> dict:
        state = self.load_state()
        sha = self.fetch_remote_sha()
        run = self.green_run(sha)
        plan = {"remote_sha": sha, "deployed_sha": state.get("deployed_sha"),
                "ci_run": run.get("id") if run else None, "force": force}
        if run is None:
            plan["action"] = "wait_ci"
        elif sha == state.get("deployed_sha") and not force:
            plan["action"] = "noop"
        elif sha == state.get("failed_sha") and not force:
            plan["action"] = "blocked_failed"
        else:
            plan["action"] = "deploy"
        return plan

    def execute(self, check: bool = False, force: bool = False) -> dict:
        cfg = self.cfg
        cfg.deploy_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
        lock_fd = os.open(cfg.lock_file, os.O_RDWR | os.O_CREAT, 0o600)
        try:
            try:
                fcntl.flock(lock_fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except OSError as exc:
                if exc.errno in (errno.EWOULDBLOCK, errno.EAGAIN):
                    raise LockBusy("another autoupdate run holds the lock") from None
                raise
            plan = self.plan(force)
            log(json.dumps(plan, sort_keys=True))
            if check or plan["action"] != "deploy":
                if not check and plan["action"] == "wait_ci":
                    state = self.load_state()
                    if state.get("waiting_sha") != plan["remote_sha"]:
                        state["waiting_sha"] = plan["remote_sha"]
                        self.save_state(state)
                return plan
            return self.deploy(plan["remote_sha"], plan["ci_run"])
        finally:
            os.close(lock_fd)

    # ---------- deploy ----------
    def deploy(self, sha: str, ci_run) -> dict:
        cfg = self.cfg
        state = self.load_state()
        stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        release = cfg.releases_dir / f"gerfried-auto-{sha[:8]}-{stamp}"
        n = 1
        while release.exists() or (cfg.backups_dir / f"layermaxxing-{stamp}-pre-{sha[:8]}.db").exists():
            n += 1  # never reuse or overwrite an existing release/backup (e.g. --force within one second)
            stamp = f"{stamp.split('-')[0]}-{n}"
            release = cfg.releases_dir / f"gerfried-auto-{sha[:8]}-{stamp}"
        if release.resolve().parent != cfg.releases_dir.resolve():
            raise DeployError("release path escapes releases dir")
        attempt = {"sha": sha, "ci_run": ci_run, "release": str(release), "started_at": now_iso()}
        state.update(status="deploying", attempt=attempt)
        self.save_state(state)
        stage = cfg.staging_dir / f"{sha[:8]}-{stamp}"
        switched = False
        try:
            cfg.releases_dir.mkdir(parents=True, exist_ok=True)
            files = safe_extract(self.archive(sha), release)
            reqs = release / "server/requirements.txt"
            if not reqs.is_file():
                raise DeployError("release has no server/requirements.txt")
            manifest = {"sha": sha, "ci_run": ci_run, "files": files,
                        "server_tree": self.git("rev-parse", f"{sha}:server").stdout.strip(),
                        "requirements_sha256": sha256_file(reqs), "created_at": now_iso()}
            self.build_venv(release)
            atomic_write(release / "DEPLOY-MANIFEST.json", json.dumps(manifest, indent=2) + "\n", 0o444)
            freeze_tree(release)

            backup = None
            if cfg.db_path.exists():
                backup = cfg.backups_dir / f"layermaxxing-{stamp}-pre-{sha[:8]}.db"
                backup_sqlite(cfg.db_path, backup)
                before = sqlite_summary(backup)
                stage.mkdir(parents=True, mode=0o700)
                copy = stage / cfg.db_name
                shutil.copyfile(backup, copy)
                os.chmod(copy, 0o600)
                self.migrate_copy(release, copy)
                compare_db(before, sqlite_summary(copy))
                attempt["db_check"] = {"tables": len(before["rows"]), "fk_violations": before["fk_violations"]}
            else:
                stage.mkdir(parents=True, mode=0o700)
                copy = stage / cfg.db_name
                self.migrate_copy(release, copy)
            attempt["backup"] = str(backup) if backup else None
            cand = self.smoke(release, copy)
            attempt["candidate"] = cand
            log(f"candidate smoke ok: {cand}")

            # ---- switch ----
            args = self.service_exec_args()
            port = self.port_from_args(args)
            prev_target = Path(os.readlink(cfg.current)) if cfg.current.is_symlink() else None
            prev_dropin = cfg.dropin.read_text() if cfg.dropin.exists() else None
            attempt["previous_current"] = str(prev_target) if prev_target else None
            switched = True
            try:
                self.set_current(release)
                self.set_dropin(self.dropin_text(args))
                self.systemctl("daemon-reload")
                self.systemctl("restart", cfg.service)
                live = self.live_readback(port)
                if live != cand:
                    raise DeployError(f"live readback {live} does not match candidate {cand}")
                cwd = self.main_pid_cwd()
                if cwd is None:  # fail closed: an unverifiable MainPID is not a validated switch
                    raise DeployError("could not determine service MainPID cwd after restart")
                if cwd != (release / "server").resolve():
                    raise DeployError(f"service runs from {cwd}, not the new release")
            except Exception as exc:
                rb = self.rollback(prev_target, prev_dropin, port)
                raise DeployError(f"{exc}; rollback: {rb}") from None
        except Exception as exc:
            state = self.load_state()
            attempt["error"] = str(exc)
            state.update(status="rolled_back" if switched else "failed", failed_sha=sha,
                         last_error=str(exc), attempt=attempt)
            self.save_state(state)
            log(f"deploy of {sha[:12]} FAILED: {exc}")
            self.notify(f"GS Gerfried auto-deploy {sha[:8]} failed: {exc}"[:900])
            return {"action": "deploy", "result": state["status"], "remote_sha": sha, "error": str(exc)}
        finally:
            if stage.exists():
                shutil.rmtree(stage, ignore_errors=True)
        state = self.load_state()
        state.update(status="deployed", deployed_sha=sha, ci_run=ci_run, release=str(release),
                     candidate=attempt["candidate"], deployed_at=now_iso(), last_error=None, attempt=attempt)
        state.pop("failed_sha", None)
        self.save_state(state)
        log(f"deployed {sha[:12]} (CI run {ci_run}) -> {release}")
        self.notify(f"GS Gerfried auto-deploy {sha[:8]} live (grid {attempt['candidate']['grid_version']}, CI {ci_run})")
        return {"action": "deploy", "result": "deployed", "remote_sha": sha, "release": str(release)}

    def rollback(self, prev_target: Path | None, prev_dropin: str | None, port: int) -> str:
        """Restore symlink + drop-in (or remove them on a first takeover) and restart the old code. DB untouched."""
        try:
            self.set_current(prev_target)
            self.set_dropin(prev_dropin)
            self.systemctl("daemon-reload")
            self.systemctl("restart", self.cfg.service)
            info = self.live_readback(port)
            log(f"rollback ok, old server healthy: {info}")
            return f"ok {info}"
        except Exception as exc:  # report, the operator must look
            log(f"ROLLBACK PROBLEM: {exc}")
            return f"PROBLEM {exc}"


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--check", action="store_true", help="only fetch + print the deployment plan")
    ap.add_argument("--force", action="store_true",
                    help="redeploy the current remote SHA even if already deployed/failed (still requires green CI)")
    ap.add_argument("--status", action="store_true", help="print the state file")
    ap.add_argument("--config", type=Path, help="JSON config (default ROOT/deploy/config.json)")
    args = ap.parse_args(argv)
    try:
        ctl = Controller(Config.load(args.config))
        if args.status:
            print(json.dumps(ctl.load_state(), indent=2, sort_keys=True))
            return 0
        result = ctl.execute(check=args.check, force=args.force)
    except LockBusy as exc:
        log(str(exc))
        return 0
    except (DeployError, OSError, subprocess.SubprocessError, ValueError) as exc:
        log(f"error: {exc}")  # e.g. network down: old server keeps running
        return 1
    print(json.dumps(result, sort_keys=True))
    return 1 if result.get("result") in ("failed", "rolled_back") else 0


if __name__ == "__main__":
    sys.exit(main())
