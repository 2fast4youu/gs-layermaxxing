"""UNIT FIXTURES ONLY: CI (gh), venv build, migration, candidate smoke, systemctl and the live readback are
mocked. Git fetch/archive runs for real against a throwaway local repo in tmp_path. A green test here is no
evidence that a real deploy against GitHub/systemd works - Hermes verifies that with --check and a real run.
"""
from __future__ import annotations

import fcntl
import importlib.util
import io
import json
import os
import sqlite3
import subprocess
import sys
import tarfile
from pathlib import Path

import pytest

SCRIPT = Path(__file__).resolve().parents[1] / "gerfried-autoupdate.py"
spec = importlib.util.spec_from_file_location("gerfried_autoupdate", SCRIPT)
au = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = au
spec.loader.exec_module(au)

BRANCH = "gerfried-erweiterung"
WORKFLOW = "gerfried-apk.yml"


def sh(*args, cwd):
    return subprocess.run(args, cwd=cwd, check=True, capture_output=True, text=True).stdout.strip()


def make_repo(path: Path) -> str:
    path.mkdir()
    sh("git", "init", "-q", "-b", BRANCH, cwd=path)
    (path / "server/app/geometry").mkdir(parents=True)
    (path / "server/app/main.py").write_text("# app\n")
    (path / "server/app/geometry/island_masks_v6.json").write_text("{}\n")
    (path / "server/requirements.txt").write_text("fastapi\n")
    sh("git", "add", ".", cwd=path)
    sh("git", "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-qm", "init", cwd=path)
    (path / "server/untracked-local.db").write_text("must never be deployed")
    return sh("git", "rev-parse", "HEAD", cwd=path)


def green(sha, status="completed", conclusion="success", branch=BRANCH, run_id=42, run_number=1, run_attempt=1):
    return {"id": run_id, "head_sha": sha, "head_branch": branch, "status": status, "conclusion": conclusion,
            "path": f".github/workflows/{WORKFLOW}", "run_number": run_number, "run_attempt": run_attempt}


class FakeController(au.Controller):
    """UNIT FIXTURE: replaces every external side effect except git with recorded fakes."""

    def __init__(self, cfg, runs=None):
        super().__init__(cfg)
        self.runs = runs or []
        self.calls: list[tuple] = []
        self.fail_venv = False
        self.fail_live = False
        self.lose_rows = False
        self.grid = 7
        self.on_restart = None
        self.pid_cwd = "release"  # "release" = cwd of the switched release; None = undeterminable

    def ci_runs(self, sha):
        return [r for r in self.runs if r["head_sha"] == sha] + [r for r in self.runs if r["head_sha"] != sha]

    def build_venv(self, release):
        self.calls.append(("venv", release))
        if self.fail_venv:
            raise au.DeployError("uv pip install failed: network unreachable")
        (release / ".venv/bin").mkdir(parents=True)

    def migrate_copy(self, release, db):
        self.calls.append(("migrate", db))
        conn = sqlite3.connect(db)
        conn.execute("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT)")
        if self.lose_rows:
            conn.execute("DELETE FROM users")
        conn.commit()
        conn.close()

    def smoke(self, release, db):
        self.calls.append(("smoke", db))
        return {"version": "4.0.0", "grid_version": self.grid}

    def systemctl(self, *args):
        self.calls.append(("systemctl", *args))
        if args[0] == "restart" and self.on_restart:
            self.on_restart()
        return ""

    def live_readback(self, port):
        self.calls.append(("readback", port))
        if self.fail_live and self.cfg.dropin.exists():
            raise au.DeployError("no healthy answer")
        return {"version": "4.0.0", "grid_version": self.grid}

    def main_pid_cwd(self):
        if self.pid_cwd == "release":
            return (self.cfg.current.resolve() / "server") if self.cfg.current.is_symlink() else None
        return self.pid_cwd

    def notify(self, message):
        self.calls.append(("notify", message))

    def restarts(self):
        return [c for c in self.calls if c[:2] == ("systemctl", "restart")]


@pytest.fixture
def env(tmp_path):
    repo = tmp_path / "remote"
    sha = make_repo(repo)
    root = tmp_path / "data"
    root.mkdir()
    unit_dir = tmp_path / "units"
    (unit_dir / "gs-layermaxxing.service.d").mkdir(parents=True)
    (unit_dir / "gs-layermaxxing.service").write_text(
        "[Service]\nEnvironment=DB_PATH=%s/layermaxxing.db\nExecStart=/old/.venv/bin/uvicorn app.main:app "
        "--host 127.0.0.1 --port 8794\n" % root)
    old50 = "[Service]\nWorkingDirectory=/old/releases/grid5/server\n"
    (unit_dir / "gs-layermaxxing.service.d/50-hermes-grid-version.conf").write_text(old50)
    conn = sqlite3.connect(root / "layermaxxing.db")
    conn.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT)")
    conn.executemany("INSERT INTO users(name) VALUES (?)", [("a",), ("b",)])
    conn.commit()
    conn.close()
    cfg = au.Config(root=root, repo_url=str(repo), unit_dir=unit_dir, branch=BRANCH, workflow=WORKFLOW)
    return {"cfg": cfg, "sha": sha, "repo": repo, "root": root, "old50": old50}


def test_pending_failed_and_wrong_sha_block_deploy(env):
    sha = env["sha"]
    for runs in ([green(sha, status="in_progress", conclusion=None)], [green(sha, conclusion="failure")],
                 [green("f" * 40)], [green(sha, branch="main")], []):
        ctl = FakeController(env["cfg"], runs)
        assert ctl.execute()["action"] == "wait_ci"
        assert not ctl.calls
        assert not (env["root"] / "current").exists()
        assert not (env["root"] / "releases").exists()


def test_force_cannot_bypass_ci(env):
    ctl = FakeController(env["cfg"], [green(env["sha"], conclusion="failure")])
    assert ctl.execute(force=True)["action"] == "wait_ci"
    assert not ctl.calls


def test_check_only_plans(env):
    ctl = FakeController(env["cfg"], [green(env["sha"])])
    plan = ctl.execute(check=True)
    assert plan["action"] == "deploy" and plan["remote_sha"] == env["sha"] and plan["ci_run"] == 42
    assert not ctl.calls and not (env["root"] / "releases").exists()


def test_first_takeover_deploys_release_dropin_and_backup(env):
    cfg, sha = env["cfg"], env["sha"]
    ctl = FakeController(cfg, [green(sha)])
    result = ctl.execute()
    assert result["result"] == "deployed"
    release = Path(result["release"])
    assert (cfg.current).is_symlink() and Path(os.readlink(cfg.current)) == release
    assert (release / "server/app/geometry/island_masks_v6.json").is_file()
    assert not (release / "server/untracked-local.db").exists()
    assert not os.access(release / "server/app/main.py", os.W_OK)
    manifest = json.loads((release / "DEPLOY-MANIFEST.json").read_text())
    assert manifest["sha"] == sha and len(manifest["requirements_sha256"]) == 64
    dropin = cfg.dropin.read_text()
    assert f"WorkingDirectory={cfg.current}/server" in dropin
    assert f"ExecStart={cfg.current}/.venv/bin/uvicorn app.main:app --host 127.0.0.1 --port 8794" in dropin
    assert (cfg.dropin_dir / "50-hermes-grid-version.conf").read_text() == env["old50"]
    backups = list(cfg.backups_dir.glob("*.db"))
    assert len(backups) == 1 and (backups[0].stat().st_mode & 0o777) == 0o600
    assert not list(cfg.staging_dir.glob("*"))  # staged copy cleaned up
    state = ctl.load_state()
    assert state["deployed_sha"] == sha and state["candidate"]["grid_version"] == 7
    assert (cfg.state_file.stat().st_mode & 0o777) == 0o600
    assert len(ctl.restarts()) == 1 and ("readback", 8794) in ctl.calls
    assert [c for c in ctl.calls if c[0] == "notify"]
    # same SHA again: noop, no restart, no message
    again = FakeController(cfg, [green(sha)])
    assert again.execute()["action"] == "noop" and not again.calls
    # --force reinstalls the CI-checked SHA
    forced = FakeController(cfg, [green(sha)])
    assert forced.execute(force=True)["result"] == "deployed" and len(forced.restarts()) == 1


def test_lock_prevents_parallel_runs(env):
    cfg = env["cfg"]
    cfg.deploy_dir.mkdir(parents=True)
    fd = os.open(cfg.lock_file, os.O_RDWR | os.O_CREAT, 0o600)
    fcntl.flock(fd, fcntl.LOCK_EX)
    try:
        with pytest.raises(au.LockBusy):
            FakeController(cfg, [green(env["sha"])]).execute()
    finally:
        os.close(fd)


def tar_with(member: tarfile.TarInfo, data: bytes = b"") -> bytes:
    buf = io.BytesIO()
    with tarfile.open(fileobj=buf, mode="w") as tar:
        for name in ("server/app/main.py", "server/app/geometry/x.json"):
            info = tarfile.TarInfo(name)
            info.size = 1
            tar.addfile(info, io.BytesIO(b"x"))
        tar.addfile(member, io.BytesIO(data) if member.isfile() else None)
    return buf.getvalue()


@pytest.mark.parametrize("kind", ["traversal", "absolute", "symlink", "hardlink"])
def test_archive_rejects_unsafe_members(tmp_path, kind):
    info = tarfile.TarInfo({"traversal": "server/../../evil", "absolute": "/tmp/evil"}.get(kind, "server/link"))
    if kind == "symlink":
        info.type, info.linkname = tarfile.SYMTYPE, "/etc/passwd"
    elif kind == "hardlink":
        info.type, info.linkname = tarfile.LNKTYPE, "server/app/main.py"
    else:
        info.size = 1
    with pytest.raises(au.DeployError):
        au.safe_extract(tar_with(info, b"x"), tmp_path / "rel")
    assert not (tmp_path / "evil").exists()


def test_archive_requires_geometry_and_fresh_target(tmp_path):
    buf = io.BytesIO()
    with tarfile.open(fileobj=buf, mode="w") as tar:
        info = tarfile.TarInfo("server/app/main.py")
        info.size = 1
        tar.addfile(info, io.BytesIO(b"x"))
    with pytest.raises(au.DeployError, match="geometry"):
        au.safe_extract(buf.getvalue(), tmp_path / "rel")
    (tmp_path / "exists").mkdir()
    with pytest.raises(au.DeployError, match="exists"):
        au.safe_extract(tar_with(tarfile.TarInfo("server/x")), tmp_path / "exists")


def test_failed_restart_on_first_takeover_rolls_back_without_db_restore(env):
    cfg = env["cfg"]
    ctl = FakeController(cfg, [green(env["sha"])])
    ctl.fail_live = True

    def user_write_during_switch():  # a user write after the backup must survive the rollback
        conn = sqlite3.connect(cfg.db_path)
        conn.execute("INSERT INTO users(name) VALUES ('late')")
        conn.commit()
        conn.close()
    ctl.on_restart = user_write_during_switch
    result = ctl.execute()
    assert result["result"] == "rolled_back"
    assert not cfg.current.exists() and not cfg.current.is_symlink()
    assert not cfg.dropin.exists()
    assert (cfg.dropin_dir / "50-hermes-grid-version.conf").read_text() == env["old50"]
    assert len(ctl.restarts()) == 2
    conn = sqlite3.connect(cfg.db_path)
    assert conn.execute("SELECT COUNT(*) FROM users").fetchone()[0] == 4  # 2 + 2 late writes, nothing restored
    conn.close()
    state = ctl.load_state()
    assert state["failed_sha"] == env["sha"] and state["status"] == "rolled_back"
    # the failed candidate is not retried every minute ...
    again = FakeController(cfg, [green(env["sha"])])
    assert again.execute()["action"] == "blocked_failed" and not again.calls
    # ... but --force may retry it
    retry = FakeController(cfg, [green(env["sha"])])
    assert retry.execute(force=True)["result"] == "deployed"


def test_rollback_restores_previous_current_and_dropin(env):
    cfg = env["cfg"]
    prev = cfg.releases_dir / "previous"
    prev.mkdir(parents=True)
    cfg.current.symlink_to(prev)
    old_dropin = "[Service]\n# previous managed drop-in\n"
    cfg.dropin.write_text(old_dropin)
    ctl = FakeController(cfg, [green(env["sha"])])
    ctl.fail_live = True
    # live check fails only while *some* drop-in exists; make the old one healthy
    orig = ctl.live_readback

    def readback(port):
        if cfg.dropin.exists() and cfg.dropin.read_text() == old_dropin:
            return {"version": "4.0.0", "grid_version": 5}
        return orig(port)
    ctl.live_readback = readback
    assert ctl.execute()["result"] == "rolled_back"
    assert Path(os.readlink(cfg.current)) == prev
    assert cfg.dropin.read_text() == old_dropin
    assert "rollback: ok" in ctl.load_state()["last_error"]


def test_dependency_failure_keeps_old_server(env):
    cfg = env["cfg"]
    ctl = FakeController(cfg, [green(env["sha"])])
    ctl.fail_venv = True
    result = ctl.execute()
    assert result["result"] == "failed" and "network" in result["error"]
    assert not [c for c in ctl.calls if c[0] == "systemctl"]
    assert not cfg.current.exists() and not cfg.dropin.exists()
    assert ctl.load_state()["failed_sha"] == env["sha"]


def test_migration_losing_rows_blocks_switch(env):
    cfg = env["cfg"]
    ctl = FakeController(cfg, [green(env["sha"])])
    ctl.lose_rows = True
    result = ctl.execute()
    assert result["result"] == "failed" and "lost rows" in result["error"]
    assert not [c for c in ctl.calls if c[0] == "systemctl"]
    conn = sqlite3.connect(cfg.db_path)
    assert conn.execute("SELECT COUNT(*) FROM users").fetchone()[0] == 2  # live DB never migrated by staging
    conn.close()


def test_new_commit_after_failure_is_tried(env):
    cfg = env["cfg"]
    ctl = FakeController(cfg, [green(env["sha"])])
    ctl.fail_venv = True
    ctl.execute()
    (env["repo"] / "server/app/main.py").write_text("# v2\n")
    sh("git", "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-qam", "v2", cwd=env["repo"])
    new_sha = sh("git", "rev-parse", "HEAD", cwd=env["repo"])
    nxt = FakeController(cfg, [green(new_sha, run_id=43)])
    assert nxt.execute()["result"] == "deployed"
    assert nxt.load_state()["deployed_sha"] == new_sha and nxt.load_state()["ci_run"] == 43


def test_unexpected_execstart_is_refused(env):
    cfg = env["cfg"]
    cfg.unit_file.write_text("[Service]\nExecStart=/bin/sh -c 'rm -rf /'\n")
    (cfg.dropin_dir / "50-hermes-grid-version.conf").unlink()
    ctl = FakeController(cfg, [green(env["sha"])])
    assert ctl.execute()["result"] == "failed"
    assert not cfg.current.exists() and not [c for c in ctl.calls if c[0] == "systemctl"]


def test_check_info_requires_real_fields():
    with pytest.raises(au.DeployError):
        au.check_info({"status": "ok"}, {"role": "production", "grid_version": 5})
    with pytest.raises(au.DeployError):
        au.check_info({"status": "ok"}, {"role": "test", "grid_version": "5"})
    assert au.check_info({"status": "ok"}, {"role": "test", "grid_version": 9, "version": "x"})["grid_version"] == 9


@pytest.mark.parametrize("newest", [dict(status="in_progress", conclusion=None), dict(conclusion="failure"),
                                    dict(status="queued", conclusion=None, run_number=1, run_attempt=2)])
def test_newest_pending_or_failed_run_beats_older_green(env, newest):
    sha = env["sha"]
    newer = {"run_number": 2, "run_attempt": 1, **newest}
    runs = [green(sha, run_id=42), green(sha, run_id=50, **{k: v for k, v in newer.items()})]
    for order in (runs, runs[::-1]):
        ctl = FakeController(env["cfg"], order)
        assert ctl.execute()["action"] == "wait_ci"
        assert not ctl.calls and not (env["root"] / "releases").exists()


def test_latest_green_run_is_selected(env):
    sha = env["sha"]
    runs = [green(sha, conclusion="failure", run_id=40, run_number=1),
            green(sha, run_id=41, run_number=2, run_attempt=1),
            green(sha, run_id=44, run_number=2, run_attempt=2),
            green(sha, conclusion="failure", run_id=99, run_number=9, branch="main")]
    plan = FakeController(env["cfg"], runs).execute(check=True)
    assert plan["action"] == "deploy" and plan["ci_run"] == 44


def test_undeterminable_main_pid_cwd_fails_closed_and_rolls_back(env):
    cfg = env["cfg"]
    ctl = FakeController(cfg, [green(env["sha"])])
    ctl.pid_cwd = None
    result = ctl.execute()
    assert result["result"] == "rolled_back" and "MainPID" in result["error"]
    assert not cfg.current.exists() and not cfg.current.is_symlink() and not cfg.dropin.exists()
    assert (cfg.dropin_dir / "50-hermes-grid-version.conf").read_text() == env["old50"]
    assert len(ctl.restarts()) == 2
    state = ctl.load_state()
    assert state["status"] == "rolled_back" and state.get("deployed_sha") is None


def test_wrong_main_pid_cwd_rolls_back(env, tmp_path):
    ctl = FakeController(env["cfg"], [green(env["sha"])])
    ctl.pid_cwd = tmp_path / "elsewhere"
    assert ctl.execute()["result"] == "rolled_back"


def test_build_venv_forces_uv_copy_link_mode(env, tmp_path):
    seen = []

    class Recorder(au.Controller):
        def run(self, argv, *, timeout=120, env=None, cwd=None, binary=False):
            seen.append((argv, env))
            return subprocess.CompletedProcess(argv, 0, "", "")

    os.environ["UV_LINK_MODE"] = "hardlink"  # an inherited setting must not win
    try:
        Recorder(env["cfg"]).build_venv(tmp_path / "rel")
    finally:
        del os.environ["UV_LINK_MODE"]
    installs = [e for argv, e in seen if argv[1:3] == ["pip", "install"]]
    assert installs and all(e["UV_LINK_MODE"] == "copy" for e in installs)


def test_freeze_tree_never_chmods_hardlinked_shared_files(tmp_path):
    shared = tmp_path / "uv-cache/wheel.py"
    shared.parent.mkdir()
    shared.write_text("x")
    shared.chmod(0o644)
    rel = tmp_path / "rel/.venv/lib"
    rel.mkdir(parents=True)
    os.link(shared, rel / "wheel.py")
    with pytest.raises(au.DeployError, match="hardlinked"):
        au.freeze_tree(tmp_path / "rel")
    assert (shared.stat().st_mode & 0o777) == 0o644
    os.unlink(rel / "wheel.py")
    (rel / "own.py").write_text("y")
    au.freeze_tree(tmp_path / "rel")
    assert not os.access(rel / "own.py", os.W_OK) and (shared.stat().st_mode & 0o777) == 0o644
    for d in (rel, rel.parent, rel.parent.parent):
        d.chmod(0o755)  # let tmp_path cleanup proceed


@pytest.mark.parametrize("effective, expected", [("", None), ("GS_ISLAND_GRID_VERSION=7", "7"),
    ('DB_PATH=/private/real.db "SERVER_NAME=Gerfried Test" GS_ISLAND_GRID_VERSION=8 SECRET=never-copy', "8")])
def test_candidate_uses_effective_service_grid_only(tmp_path, monkeypatch, effective, expected):
    from types import SimpleNamespace
    c = au.Controller(SimpleNamespace(service="fixture.service"))
    calls = []
    def show(*args):
        calls.append(args)
        return effective
    c.systemctl = show
    monkeypatch.setenv("GS_ISLAND_GRID_VERSION", "invalid-inherited")
    monkeypatch.setenv("SECRET", "never-copy")
    db = tmp_path / "snapshot.db"
    env = c.candidate_env(db)
    assert env.get("GS_ISLAND_GRID_VERSION") == expected
    assert env["DB_PATH"] == str(db) and env["SERVER_ROLE"] == "test"
    assert "SECRET" not in env and "SERVER_NAME" not in env
    assert calls == [("show", "-p", "Environment", "--value", "fixture.service")]


@pytest.mark.parametrize("effective", ["GS_ISLAND_GRID_VERSION=9", "GS_ISLAND_GRID_VERSION=08", "GS_ISLAND_GRID_VERSION=", '"unterminated'])
def test_candidate_rejects_invalid_effective_runtime_grid(tmp_path, effective):
    from types import SimpleNamespace
    c = au.Controller(SimpleNamespace(service="fixture.service"))
    c.systemctl = lambda *args: effective
    with pytest.raises(au.DeployError):
        c.candidate_env(tmp_path / "snapshot.db")
