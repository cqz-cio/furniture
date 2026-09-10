"""First Docker cutover for the confirmed test host, with a fresh isolated database.

The legacy database is never migrated. Before traffic opens, failures restore the
original service/routes. After traffic may have opened, recovery only rolls forward.
"""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import signal
import socket
import time
import urllib.request

from common import fingerprint, require, timestamp, utcnow, write_json
from bootstrap_policy import PROFILE, backend_environment, nginx_plan, owned_database, sha256, validate_audit_bundle, validate_target
from bootstrap_io import Commands, Database, emit
from bootstrap_image import capacity, migrate, prepare_images, verify_runtime
from server import Server, http_json, locked


class Bootstrap:
    def __init__(self, payload):
        self.payload, self.release = payload, payload["release"]
        self.root = Path(payload["root"])
        self.directory = self.root / "bootstrap"
        require(self.directory.resolve() == self.directory, "Unexpected bootstrap directory symlink")
        self.directory.mkdir(mode=0o700, exist_ok=True)
        self.path = self.directory / "state.json"
        self.state = json.loads(self.path.read_text()) if self.path.exists() else {"phase": "new"}
        command_dir = self.directory / ("commands-" + secrets.token_hex(8))
        command_dir.mkdir(mode=0o700)
        self.commands, self.database = Commands(command_dir), None
        self.database = Database(self.commands)
        import pwd
        self.owner = pwd.getpwnam(PROFILE["user"])

    def save(self, phase=None, **fields):
        if phase:
            self.state["phase"] = phase
        self.state.update(fields, updated_at=utcnow().isoformat())
        write_json(self.path, self.state)
        emit(self.state["phase"])

    def work(self):
        value = self.state["attempt"]
        require(re.fullmatch(r"[0-9a-f]{16}", value), "Invalid bootstrap attempt")
        path = self.directory / value
        require(path.resolve() == path and path.is_dir(), "Attempt directory unavailable")
        return path

    def run(self, args, label, seconds=30):
        return self.commands.run(args, label, seconds).strip()

    def source_runtime(self):
        require(self.run(["systemctl", "is-active", PROFILE["service"]], "legacy-status") == "active", "Legacy ERP must be active before preparation")
        pid = self.run(["systemctl", "show", PROFILE["service"], "-p", "MainPID", "--value"], "legacy-pid")
        require(pid.isdigit() and int(pid) > 0, "Legacy process unavailable")
        require(str(Path("/proc/" + pid + "/exe").resolve()) == PROFILE["java"], "Legacy Java runtime changed")
        values = dict(v.decode().split("=", 1) for v in Path("/proc/" + pid + "/environ").read_bytes().split(b"\0") if b"=" in v)
        # Run the same renderer used for the eventual backend; reject unknown overrides early.
        backend_environment(values, "oakved_cd_test_live_" + "0" * 16, "erp_cd_a_" + "0" * 16, "0" * 64)
        require(values.get("SERVER_PORT") == "48080", "Legacy backend port changed")
        require(values.get("YUDAO_REDIS_HOST") == "127.0.0.1" and values.get("YUDAO_REDIS_DATABASE") == "0", "Legacy Redis target changed")
        require(self.database.version(PROFILE["source_database"]) == 47, "Legacy schema changed; review onboarding profile")
        status, health = http_json("http://127.0.0.1:48080/actuator/health")
        require(status == 200 and isinstance(health, dict) and health.get("status") == "UP", "Legacy health check failed")
        return pid, values

    def routes(self):
        expanded = self.run(["nginx", "-T"], "nginx-inventory")
        sections = re.split(r"(?m)^# configuration file ([^\n]+):\n", expanded)
        actual = {sections[i]: sections[i+1] for i in range(1, len(sections)-1, 2)
                  if re.search(r"proxy_pass\s+http://127\.0\.0\.1:48080(?=[/;])", sections[i+1])}
        require(set(actual) == set(PROFILE["nginx_files"]), "Active ERP proxy inventory differs from the verified test profile")
        originals = {}
        for name in PROFILE["nginx_files"]:
            path = Path(name)
            require(path.resolve() == path and path.is_file(), "Unexpected proxy symlink")
            originals[name] = path.read_text()
        return originals, sha256(expanded.encode())

    def preflight(self):
        require(not (self.root / "state.json").exists() or not json.loads((self.root / "state.json").read_text()).get("current"), "ERP is already managed; use deploy")
        version = self.run(["docker", "compose", "version", "--short"], "compose-version").lstrip("v").split(".")
        require(tuple(map(int, version[:2])) >= (2, 30), "Docker Compose 2.30+ is required")
        for port in (PROFILE["backend_port"], PROFILE["admin_port"]):
            with socket.socket() as sock:
                sock.bind(("127.0.0.1", port))
        memory = {line.split(":")[0]: int(line.split()[1]) for line in Path("/proc/meminfo").read_text().splitlines()}
        require(memory["MemAvailable"] >= 768 * 1024, "At least 768 MiB available memory is required for rehearsal")
        capacity([self.root])
        pid, runtime = self.source_runtime()
        source = PROFILE["source_database"]
        require(self.database.query("SELECT @@partial_revokes") == "0", "Review MySQL schema-grant escaping for partial_revokes=ON")
        # This profile covers database-backed uploads only. Do not invent a mount for external files.
        require(self.database.query("SELECT COUNT(*) FROM infra_file f LEFT JOIN infra_file_config c ON c.id=f.config_id WHERE c.id IS NULL OR c.storage<>1", source) == "0", "External file storage requires a reviewed mount mapping")
        require(self.database.query("SELECT COUNT(*) FROM infra_file_config WHERE master=b'1' AND storage=1 AND deleted=b'0'", source) == "1", "Expected the existing database file store")
        for table, field in (("TRIGGERS", "TRIGGER_SCHEMA"), ("ROUTINES", "ROUTINE_SCHEMA"), ("EVENTS", "EVENT_SCHEMA"), ("VIEWS", "TABLE_SCHEMA")):
            require(self.database.query(f"SELECT COUNT(*) FROM information_schema.{table} WHERE {field}='{source}'") == "0", "Review database stored objects before isolated restoration")
        originals, nginx_hash = self.routes()
        token = secrets.token_hex(24)
        plan = nginx_plan(originals, token)
        enabled = self.run(["systemctl", "show", PROFILE["service"], "-p", "UnitFileState", "--value"], "legacy-enabled")
        require(enabled in ("enabled", "disabled"), "Unsupported legacy unit enable state")
        unit = self.run(["systemctl", "cat", PROFILE["service"]], "legacy-unit-backup")
        return {"pid": pid, "runtime": runtime, "originals": originals, "routes": plan, "probe_token": token,
                "nginx_hash": nginx_hash, "legacy_enabled": enabled,
                "unit": unit, "unit_hash": sha256(unit.encode()),
                "jar_hash": sha256(Path(PROFILE["jar"]).read_bytes())}

    def clone_and_migrate(self, target, backup, receipt, runtime):
        owned_database(target)
        self.database.create(target)
        user = "erp_cd_m_" + self.state["attempt"]
        self.save(migration_user=user)
        password = secrets.token_hex(32)
        with self.database.restricted(target, user, password) as env:
            self.database.restore(backup, receipt, target, user, env)
            before = self.database.counts(target)
            before_audit = self.database.audit(target, self.payload["audit"]["before"])
            result = migrate(self.commands, runtime, target, env)
            require(self.database.counts(target) == before, "Migration changed product, account or attachment counts")
            require(self.database.query("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('website_code_config','website_code_history')", target) == "2", "CMS tables missing after migration")
            after_audit = self.database.audit(target, self.payload["audit"]["after"])
        require(self.database.query(f"SELECT COUNT(*) FROM mysql.user WHERE User='{user}'") == "0", "Temporary account cleanup incomplete")
        result.update(before_audit=before_audit, after_audit=after_audit, counts=before)
        return result

    def prepare(self):
        require(self.state["phase"] in ("new", "prepared", "prepare-failed", "restored-legacy"), "Unfinished cutover: use recover before preparing again")
        snapshot = self.preflight()
        if self.state["phase"] == "prepared" and self.state.get("release_hash") == fingerprint(self.release):
            try:
                self.verify_prepared(snapshot)
                return self.result("prepared")
            except ValueError:
                emit("refresh-expired-or-changed-preparation")
        attempt = secrets.token_hex(8)
        work = self.directory / attempt
        work.mkdir(mode=0o700)
        self.state = {"attempt": attempt, "release_id": self.release["id"], "release_hash": fingerprint(self.release),
                      "writes_may_be_open": False, "candidate_started": False, "source_database": PROFILE["source_database"],
                      "rehearsal_database": "oakved_cd_test_rehearse_" + attempt, "live_database": "oakved_cd_test_live_" + attempt}
        self.save("preparing")
        write_json(work / "source.json", snapshot)
        write_json(work / "release.json", self.release)
        try:
            prepare_images(self.commands, self.release, work, self.payload["helper"], preloaded=self.payload.get("images_preloaded") is True)
            receipt = self.database.backup(PROFILE["source_database"], work / "rehearsal.sql")
            result = self.clone_and_migrate(self.state["rehearsal_database"], work / "rehearsal.sql", receipt, work / "runtime")
            pid, _ = self.source_runtime()
            require(pid == snapshot["pid"] and self.routes()[1] == snapshot["nginx_hash"], "Legacy service or routing changed during preparation")
            write_json(work / "rehearsal.json", result)
            self.save("prepared", prepared_at=utcnow().isoformat(), restore_passed=True, migration_passed=True,
                      backup=receipt, remaining_findings=[{k: v for k, v in row.items() if k != "breakdown"} for row in result["after_audit"]])
        except Exception:
            self.save("prepare-failed")
            raise
        return self.result("prepared")

    def verify_prepared(self, snapshot):
        require(self.state.get("release_hash") == fingerprint(self.release) and self.state.get("migration_passed") is True, "Prepare this exact release first")
        require(0 <= (utcnow() - timestamp(self.state["prepared_at"])).total_seconds() <= 86400, "Preparation expired; run prepare again")
        old = json.loads((self.work() / "source.json").read_text())
        require(old["pid"] == snapshot["pid"] and old["runtime"] == snapshot["runtime"] and old["nginx_hash"] == snapshot["nginx_hash"]
                and old["jar_hash"] == snapshot["jar_hash"] and old["unit_hash"] == snapshot["unit_hash"], "Legacy configuration changed; prepare again")
        require(self.database.version(self.state["rehearsal_database"]) == 49, "Rehearsal database changed")
        verify_runtime(self.work())

    def result(self, status):
        return {"status": status, "environment": "test", "release_id": self.state.get("release_id"),
                "phase": self.state["phase"], "restore_passed": self.state.get("restore_passed", False),
                "migration_passed": self.state.get("migration_passed", False),
                "source_database": PROFILE["source_database"], "live_database": self.state.get("live_database"),
                "remaining_findings": self.state.get("remaining_findings", []),
                "report_path": str(self.path), "traffic_open": self.state.get("writes_may_be_open", False)}

    def replace_routes(self, mode):
        snapshot = json.loads((self.work() / "source.json").read_text())
        originals, plans = snapshot["originals"], snapshot["routes"]
        target = originals if mode == "original" else plans[mode]
        for name in originals:
            path = Path(name)
            require(path.resolve() == path and path.read_text() in [originals[name], *(v[name] for v in plans.values())],
                    "Proxy changed outside CD; refusing to overwrite it")
        for name, value in target.items():
            path = Path(name)
            temporary = path.with_name(path.name + ".erp-cd-tmp")
            if temporary.exists():
                require(not temporary.is_symlink() and temporary.stat().st_uid == 0, "Unknown temporary proxy file")
                partial = temporary.read_text()
                require(any(v.startswith(partial) for v in [originals[name], *(p[name] for p in plans.values())]), "Unrecognized interrupted proxy write")
                temporary.unlink()
            temporary.write_text(value)
            temporary.chmod(path.stat().st_mode & 0o777)
            temporary.replace(path)
        self.run(["nginx", "-t"], "nginx-validate")
        self.run(["systemctl", "reload", "nginx"], "nginx-reload")

    def website_snapshot(self):
        result = {}
        for port in (80, 8081, 18081):
            request = urllib.request.Request(f"http://127.0.0.1:{port}/", headers={"Host": PROFILE["host"]})
            with urllib.request.urlopen(request, timeout=5) as response:
                require(response.status == 200, "Existing website is not healthy")
                result[str(port)] = sha256(response.read(1024 * 1024))
        return result

    def configure_candidate(self):
        work = self.work()
        snapshot = json.loads((work / "source.json").read_text())
        metadata = verify_runtime(work)
        user, password = "erp_cd_a_" + self.state["attempt"], secrets.token_hex(32)
        self.save(application_user=user)
        self.database.create_user(self.state["live_database"], user, password)
        config_dir = self.root / "config"
        require(config_dir.resolve() == config_dir, "Unexpected configuration directory symlink")
        config_dir.mkdir(mode=0o700, exist_ok=True)
        if (config_dir / "server.json").exists():
            require(json.loads((config_dir / "server.json").read_text()).get("initialized") is False, "Existing managed configuration cannot be replaced")
        # Uploads are in MySQL on this host. A dedicated empty mount supports future uploads
        # without pretending an unrelated old example directory is an active attachment store.
        data_root = Path("/opt/oakved-cd-data/test")
        require(data_root.resolve() == data_root, "Unexpected persistent directory symlink")
        data_root.mkdir(mode=0o755, parents=True, exist_ok=True)
        data_root.parent.chmod(0o755)
        data_root.chmod(0o755)
        for kind in ("uploads", "logs"):
            path = data_root / kind
            path.mkdir(mode=0o750, exist_ok=True)
            require(path.resolve() == path, "Unexpected data mount symlink")
            os.chown(path, metadata["uid"], self.owner.pw_gid)
            path.chmod(0o750)
        value = {"environment": "test", "initialized": True, "project": "oakved-erp-test",
            "backend_port": PROFILE["backend_port"], "admin_port": PROFILE["admin_port"],
            "api_base_url": "http://" + PROFILE["host"], "storefront_url": "http://" + PROFILE["host"],
            "admin_url": "http://" + PROFILE["host"] + "/admin/", "database_name": self.state["live_database"],
            "uploads_path": str(data_root / "uploads"), "uploads_target": "/opt/yudao/uploads", "logs_path": str(data_root / "logs"),
            "min_free_bytes": PROFILE["reserve_bytes"], "image_peak_bytes": metadata["image_peak_bytes"],
            "backup_estimate_bytes": self.state["fresh_backup"]["bytes"], "backup_timeout_seconds": 180,
            "migration_reviews": {}, "smoke_checks": [
                {"tenant_id": tid, "path": path} for tid in (121, 162) for path in (
                    "/app-api/seo/navigation/public?siteId=1&locale=en",
                    "/app-api/seo/blog/public?siteId=1&locale=en&pageSize=1")]}
        write_json(config_dir / "server.json", value)
        (config_dir / "backend.env").write_text(backend_environment(snapshot["runtime"], self.state["live_database"], user, password))
        (config_dir / "mysql.cnf").write_text(f"[client]\nhost=127.0.0.1\nport=3306\nuser={user}\npassword={password}\n")
        server = Server(self.root, "test")
        require(not server.state.get("current"), "An active managed release already exists")
        server.state["in_progress"] = {"operation": "bootstrap", "release_id": self.release["id"], "phase": "candidate"}
        server.save()
        server.stage(self.release, self.payload["compose"])
        # A prior failed first cutover may have staged this exact image release with a
        # different, now abandoned clone. Refresh its private env before container creation.
        shutil.copyfile(config_dir / "backend.env", server.record(self.release["id"]) / "backend.env")
        return server

    def start_candidate(self):
        server = self.configure_candidate()
        self.save("starting-candidate", candidate_started=True)
        server.compose(self.release["id"], "up", "--detach", "--no-build", "--pull", "never", "erp-backend", timeout=120)
        server.compose(self.release["id"], "up", "--detach", "--no-build", "--pull", "never", "erp-admin", timeout=120)
        server.wait_healthy(self.release, local_only=True)
        return server

    def grant_deployer_access(self):
        # Only the new CD config/release/state paths; bootstrap credentials stay root-only.
        paths = [self.root, self.root / "config", self.root / "releases", self.root / "state.json",
                 self.root / "deployment.log", self.root / "operation.lock", self.root / "command-logs"]
        for parent in (self.root / "config", self.root / "releases" / self.release["id"]):
            if parent.exists():
                paths += [parent, *parent.rglob("*")]
        for path in paths:
            if path.exists():
                require(path.resolve().is_relative_to(self.root) and not path.is_symlink(), "CD path ownership escapes the deployment root")
                os.chown(path, self.owner.pw_uid, self.owner.pw_gid)
                path.chmod(0o700 if path.is_dir() else 0o600)

    def finish(self, server):
        server.state.update(current=self.release["id"], history=[], pins=[], compatible=[], schema_version=49, in_progress=None,
                            bootstrap={"completed_at": utcnow().isoformat(), "source_database": PROFILE["source_database"]})
        server.save()
        self.save("complete", completed_at=utcnow().isoformat())
        self.grant_deployer_access()
        try:
            self.cleanup_scratch()
        except Exception:
            self.save("complete", scratch_cleanup="retained-for-inspection")
        return self.result("success")

    def cleanup_scratch(self):
        """Remove only this successful attempt's temporary runtime and rehearsal DB."""
        require(self.state["phase"] == "complete" and self.state["writes_may_be_open"], "Cleanup requires a completed cutover")
        name = owned_database(self.state["rehearsal_database"])
        require(name.startswith("oakved_cd_test_rehearse_") and name != self.state["live_database"], "Never remove the live database")
        current = json.loads((self.root / "config/server.json").read_text())
        require(current["database_name"] != name, "Rehearsal database has been adopted by the application")
        if self.database.query(f"SELECT COUNT(*) FROM information_schema.processlist WHERE DB='{name}'") == "0":
            self.database.query(f"DROP DATABASE IF EXISTS `{name}`")
        else:
            self.save("complete", scratch_cleanup="rehearsal-database-in-use")
            return
        runtime = self.work() / "runtime"
        require(runtime.resolve().is_relative_to(self.work()) and not runtime.is_symlink(), "Runtime cleanup escapes the attempt")
        if runtime.exists():
            metadata = verify_runtime(self.work())
            for relative in metadata["runtime_files"]:
                path = runtime / relative
                require(path.resolve().is_relative_to(runtime) and not path.is_symlink(), "Unexpected runtime file link")
            for relative in metadata["runtime_files"]:
                (runtime / relative).unlink()
            for path in sorted((p for p in runtime.rglob("*") if p.is_dir()), key=lambda p: len(p.parts), reverse=True):
                path.rmdir()
            runtime.rmdir()
        self.save("complete", scratch_cleanup="complete")

    def cutover(self):
        if self.state["phase"] == "complete":
            require(self.state.get("release_hash") == fingerprint(self.release), "Use deploy for another release")
            Server(self.root, "test").healthy(self.release)
            return self.result("already-current")
        require(self.state["phase"] == "prepared", "Run prepare successfully for this release before cutover")
        self.verify_prepared(self.preflight())
        work = self.work()
        snapshot = json.loads((work / "source.json").read_text())
        capacity([self.root], self.state["backup"]["bytes"] * 4)
        self.save("maintenance", website_before=self.website_snapshot())
        try:
            self.replace_routes("maintenance")
            self.save("stopping-legacy")
            self.run(["systemctl", "stop", PROFILE["service"]], "stop-legacy", seconds=45)
            self.save("fresh-backup")
            require(self.database.query("SELECT COUNT(*) FROM information_schema.processlist WHERE DB='" + PROFILE["source_database"] + "'") == "0", "Another connection still uses the source database")
            receipt = self.database.backup(PROFILE["source_database"], work / "cutover.sql")
            self.save("restoring-live-copy", fresh_backup=receipt)
            result = self.clone_and_migrate(self.state["live_database"], work / "cutover.sql", receipt, work / "runtime")
            require(result["counts"] == self.database.counts(PROFILE["source_database"]), "Fresh copy differs from the stopped ERP data")
            write_json(work / "cutover-migration.json", result)
            server = self.start_candidate()
            self.save("verifying-proxy")
            self.replace_routes("candidate")
            server.wait_healthy(self.release, headers={"X-ERP-CD-Probe": snapshot["probe_token"]})
            require(self.website_snapshot() == self.state["website_before"], "An existing website changed during cutover")
            require(self.database.version(PROFILE["source_database"]) == 47, "Legacy database changed unexpectedly")
            self.run(["systemctl", "disable", PROFILE["service"]], "disable-legacy-autostart")
            # Once this flag is durable, never restore the stale legacy database automatically.
            self.save("publishing", writes_may_be_open=True)
            self.replace_routes("open")
            server.healthy(self.release)
            return self.finish(server)
        except Exception:
            if self.state.get("writes_may_be_open"):
                self.save("roll-forward-required")
            else:
                try:
                    self.restore_legacy()
                except Exception:
                    self.save("recovery-required")
            raise

    def restore_legacy(self):
        require(not self.state.get("writes_may_be_open"), "Traffic may have opened; only roll-forward recovery is allowed")
        snapshot = json.loads((self.work() / "source.json").read_text())
        if self.state.get("candidate_started"):
            server = Server(self.root, "test")
            require(server.config["database_name"] == self.state["live_database"] and not server.state.get("current"), "Candidate ownership changed")
            server.compose(self.release["id"], "stop", "--timeout", "30", timeout=60)
            server.state["in_progress"] = None
            server.state["last_failure"] = {"status": "restored-legacy", "release_id": self.release["id"]}
            server.save()
        require(self.database.version(PROFILE["source_database"]) == 47, "Original database is not V047")
        require(sha256(Path(PROFILE["jar"]).read_bytes()) == snapshot["jar_hash"], "Legacy JAR changed; cannot restore automatically")
        require(sha256(self.run(["systemctl", "cat", PROFILE["service"]], "check-legacy-unit").encode()) == snapshot["unit_hash"], "Legacy unit changed; cannot restore automatically")
        self.run(["systemctl", "enable" if snapshot["legacy_enabled"] == "enabled" else "disable", PROFILE["service"]], "restore-autostart")
        self.run(["systemctl", "start", PROFILE["service"]], "restore-legacy", seconds=45)
        deadline = time.monotonic() + 150
        while True:
            try:
                status, data = http_json("http://127.0.0.1:48080/actuator/health")
                if status == 200 and isinstance(data, dict) and data.get("status") == "UP":
                    break
            except OSError:
                pass
            require(time.monotonic() < deadline, "Legacy recovery health check timed out")
            emit("waiting-for-legacy-health")
            time.sleep(5)
        self.replace_routes("original")
        for key in ("application_user", "migration_user"):
            if self.state.get(key):
                self.database.remove_user(self.state[key])
        config_path = self.root / "config/server.json"
        if config_path.exists():
            config = json.loads(config_path.read_text())
            require(config["database_name"] == self.state["live_database"], "Unrecognized candidate database")
            config["initialized"] = False
            write_json(config_path, config)
        state_path = self.root / "state.json"
        if state_path.exists():
            state = json.loads(state_path.read_text())
            require(not state.get("current"), "Managed deployment appeared during bootstrap recovery")
            state.update(in_progress=None, last_failure={"status": "restored-legacy", "release_id": self.release["id"]})
            write_json(state_path, state)
        self.save("restored-legacy")
        self.grant_deployer_access()

    def recover(self):
        require(self.state.get("release_hash") == fingerprint(self.release), "Recover the recorded release only")
        if self.state["phase"] == "complete":
            Server(self.root, "test").healthy(self.release)
            self.grant_deployer_access()
            return self.result("already-current")
        if self.state["phase"] == "restored-legacy":
            self.source_runtime()
            self.grant_deployer_access()
            return self.result("restored-legacy")
        if self.state["phase"] in ("preparing", "prepare-failed"):
            if self.state.get("migration_user"):
                self.database.remove_user(self.state["migration_user"])
            self.save("prepare-failed")
            return self.result("prepare-failed")
        if self.state.get("writes_may_be_open"):
            require(self.run(["systemctl", "show", PROFILE["service"], "-p", "MainPID", "--value"], "legacy-stopped") == "0", "Legacy service restarted; stop and inspect both writers")
            server = Server(self.root, "test")
            require(server.config["database_name"] == self.state["live_database"], "Managed database changed")
            server.wait_healthy(self.release, local_only=True)
            self.replace_routes("open")
            server.healthy(self.release)
            return self.finish(server)
        require(self.state["phase"] != "prepared", "No cutover to recover; run cutover when ready")
        self.restore_legacy()
        return self.result("restored-legacy")


def main(payload):
    validate_target(payload["environment"], payload["root"], payload["release"], payload["operation"], payload.get("confirm_cutover"))
    validate_audit_bundle(payload["audit"])
    require(os.geteuid() == 0 and os.environ.get("SUDO_USER") == PROFILE["user"], "Use the verified deployment user's sudo connection")
    require(os.uname().machine == "x86_64", "linux/amd64 required")
    key = Path("/etc/ssh/ssh_host_ed25519_key.pub").read_text().split()[1]
    require(base64.b64encode(hashlib.sha256(base64.b64decode(key)).digest()).decode().rstrip("=") == PROFILE["host_key"], "Wrong test server host identity")
    root = Path(payload["root"])
    require(root.resolve() == root, "Unexpected deployment path symlink")
    os.umask(0o077)
    root.mkdir(mode=0o700, parents=True, exist_ok=True)
    def interrupted(_signum, _frame):
        signal.signal(signal.SIGTERM, signal.SIG_IGN)
        signal.signal(signal.SIGHUP, signal.SIG_IGN)
        raise RuntimeError("Bootstrap interrupted; consult the persisted recovery phase")
    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGHUP, interrupted)
    with locked(root / "operation.lock"):
        bootstrap = Bootstrap(payload)
        result = getattr(bootstrap, payload["operation"])()
    emit("finished", result=result)
    print("ERP_CD_RESULT=" + json.dumps(result), flush=True)
