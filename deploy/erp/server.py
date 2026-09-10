"""Linux server deployment engine. Called over SSH; never provisions an existing database."""
from contextlib import contextmanager
import configparser
import hashlib
from datetime import datetime, timezone
import json
import logging
from logging.handlers import RotatingFileHandler
import os
from pathlib import Path
import platform
import re
import shutil
import signal
import socket
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
from urllib.parse import urlsplit

from common import environment_images, fingerprint, require, timestamp, utcnow, validate_release, write_json, RELEASE

COMMAND_LOG_DIRECTORY = None


@contextmanager
def locked(path):
    import fcntl
    with path.open("a") as stream:
        try:
            fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise ValueError("Another server operation holds the deployment lock") from None
        yield


def run(args, timeout=30, output=None):
    retained = timeout > 60 and COMMAND_LOG_DIRECTORY is not None
    if retained:
        COMMAND_LOG_DIRECTORY.mkdir(mode=0o700, exist_ok=True)
    def stream(suffix):
        return tempfile.NamedTemporaryFile(prefix=utcnow().strftime("%Y%m%dT%H%M%S-"), suffix=suffix,
            dir=COMMAND_LOG_DIRECTORY if retained else None, delete=not retained)
    with stream(".stdout.log") as stdout, stream(".stderr.log") as stderr:
        process = subprocess.Popen(args, stdin=subprocess.DEVNULL, stdout=output or stdout,
                                   stderr=stderr, start_new_session=True)
        started = changed = notified = time.monotonic()
        size = 0
        try:
            while process.poll() is None:
                time.sleep(0.25)
                now = time.monotonic()
                current = os.fstat((output or stdout).fileno()).st_size + os.fstat(stderr.fileno()).st_size
                if current != size:
                    size, changed = current, now
                if now - started >= timeout:
                    raise RuntimeError(f"Command deadline reached: {Path(args[0]).name}")
                if now - changed >= 60:
                    raise RuntimeError(f"Command made no progress for 60 seconds: {Path(args[0]).name}")
                if now - notified >= 10:
                    print(f"ERP command {Path(args[0]).name}: elapsed={round(now-started)}s, output_bytes={size}", flush=True)
                    notified = now
            if process.returncode:
                raise RuntimeError(f"Command failed (exit {process.returncode}): {Path(args[0]).name}")
        finally:
            if process.poll() is None:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait(timeout=5)
        stdout.seek(0)
        return stdout.read().decode("utf-8") if output is None else ""


def http_json(url, headers=None):
    request = urllib.request.Request(url, headers=headers or {})
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, response_headers, newurl):
            return None
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
    try:
        response = opener.open(request, timeout=5)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        body = response.read(2 * 1024 * 1024)
        try:
            data = json.loads(body)
        except (ValueError, UnicodeDecodeError):
            data = None
        return response.status, data


class Server:
    def __init__(self, root, environment):
        global COMMAND_LOG_DIRECTORY
        self.root = Path(root).resolve()
        self.environment = environment
        require(environment in ("test", "production") and self.root.name == environment
                and self.root.is_dir(), "Use a preconfigured environment-specific deployment directory")
        self.config = json.loads((self.root / "config/server.json").read_text())
        require(self.config["environment"] == environment and self.config.get("initialized") is True,
                "Complete and record the first Docker/database cutover before enabling CD")
        require(self.config["project"] == "oakved-erp-" + environment, "Compose project/environment mismatch")
        self.state_path = self.root / "state.json"
        self.state = json.loads(self.state_path.read_text()) if self.state_path.exists() else {
            "environment": environment, "current": None, "history": [], "pins": [], "in_progress": None, "compatible": []}
        require(self.state["environment"] == environment, "State belongs to another environment")
        self.log_path = self.root / "deployment.log"
        COMMAND_LOG_DIRECTORY = self.root / "command-logs"

    def log(self, message):
        message = utcnow().isoformat() + " " + message
        print(message, flush=True)
        handler = RotatingFileHandler(self.log_path, maxBytes=5 * 1024**2, backupCount=3, encoding="utf-8")
        try:
            handler.emit(logging.LogRecord("erp-cd", logging.INFO, "", 0, message, (), None))
        finally:
            handler.close()

    def save(self):
        write_json(self.state_path, self.state)

    def record(self, release_id):
        require(RELEASE.fullmatch(release_id), "Invalid recorded release")
        directory = self.root / "releases" / release_id
        require(directory.resolve().is_relative_to(self.root), "Release directory escapes deployment root")
        return directory

    def manifest(self, release_id):
        return validate_release(json.loads((self.record(release_id) / "release.json").read_text()))

    def compose(self, release_id, *args, timeout=60):
        directory = self.record(release_id)
        return run(["docker", "compose", "--project-name", self.config["project"], "--env-file", str(directory / "images.env"),
                    "--file", str(directory / "compose.yml"), *args], timeout)

    def current_verified(self):
        require(self.state.get("current"), "No registered running version")
        self.verify_containers(self.manifest(self.state["current"]))

    def verify_containers(self, release):
        for service, image in environment_images(release, self.environment).items():
            ids = self.compose(release["id"], "ps", "--all", "--quiet", service).split()
            require(len(ids) == 1, "Expected exactly one " + service + " container")
            value = run(["docker", "inspect", "--format", "{{.Config.Image}}", ids[0]]).strip()
            status = run(["docker", "inspect", "--format", "{{.State.Status}}", ids[0]]).strip()
            require(value == image and status == "running", "Running container differs from registered version")

    def snapshot(self):
        require(not self.state.get("in_progress"), "Unfinished deployment blocks registry cleanup")
        self.current_verified()
        return {"environment": self.environment, "verified": True, "checked_at": utcnow().isoformat(),
            "current": self.state["current"], "rollback": self.state["history"][:2], "pins": self.state.get("pins", []),
            "in_progress": self.state.get("in_progress")}

    def lease(self, action, lease_id):
        require(re.fullmatch(r"[A-Za-z0-9-]{1,100}", lease_id), "Invalid cleanup lease")
        path = self.root / "cleanup-lease.json"
        old = json.loads(path.read_text()) if path.exists() else None
        if action == "lease-start":
            require(not old or old["expires"] < time.time() or old["id"] == lease_id, "Another cleanup holds a lease")
            snapshot = self.snapshot()
            expires = time.time() + 900
            write_json(path, {"id": lease_id, "expires": expires})
            snapshot.update(lease_id=lease_id, lease_expires=datetime.fromtimestamp(expires, timezone.utc).isoformat())
            return snapshot
        require(old and old["id"] == lease_id and old["expires"] > time.time(), "Cleanup lease unavailable or expired")
        if action == "lease-end":
            path.unlink()
            return {"released": True}
        return self.snapshot()

    def ensure_no_lease(self):
        path = self.root / "cleanup-lease.json"
        require(not path.exists() or json.loads(path.read_text())["expires"] < time.time(), "Registry cleanup is in progress")

    def database_arguments(self):
        defaults = self.root / "config/mysql.cnf"
        require(defaults.is_file() and not defaults.stat().st_mode & 0o077, "mysql.cnf must exist with mode 0600")
        settings = configparser.ConfigParser(interpolation=None)
        settings.read(defaults)
        client = settings["client"]
        host, port = client["host"], int(client.get("port", "3306"))
        require(re.fullmatch(r"[A-Za-z0-9_.-]+", host) and 1 <= port <= 65535, "Invalid database endpoint")
        database = self.config["database_name"]
        require(re.fullmatch(r"[A-Za-z0-9_]+", database), "Invalid database name")
        env_files = [self.root / "config/backend.env"]
        if self.state.get("current"):
            env_files.append(self.record(self.state["current"]) / "backend.env")
        for env_file in env_files:
            values = dict(line.split("=", 1) for line in env_file.read_text().splitlines() if "=" in line and not line.lstrip().startswith("#"))
            url = urlsplit(values.get("YUDAO_DB_URL", "").removeprefix("jdbc:"))
            require(url.scheme == "mysql" and url.hostname == host and (url.port or 3306) == port
                    and url.path == "/" + database, "Backup/check database differs from the running or candidate backend")
        return ["--defaults-extra-file=" + str(defaults), "--protocol=TCP", "--host=" + host,
                "--port=" + str(port), "--user=" + client["user"]]

    def database_version(self):
        arguments = self.database_arguments()
        sql = ("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0; "
               "SELECT COALESCE(MAX(CAST(version AS UNSIGNED)),0) FROM flyway_schema_history WHERE success = 1")
        values = run(["mysql", *arguments, "--connect-timeout=5", "--batch",
                      "--skip-column-names", "--database=" + self.config["database_name"], "--execute=" + sql], 20).strip().splitlines()
        require(len(values) == 2 and values[0] == "0" and values[1].isdigit() and int(values[1]) > 0,
                "Existing Flyway history is missing or failed; first-time migration requires a separate rehearsal")
        return int(values[1])

    def backup(self, release_id):
        directory = self.root / "backups"
        directory.mkdir(exist_ok=True)
        path = directory / (release_id + ".sql")
        require(not path.exists(), "A backup for this attempt already exists; inspect the interrupted release")
        self.log("Creating pre-migration database backup")
        with path.open("xb") as output:
            run(["mysqldump", *self.database_arguments(), "--single-transaction",
                 "--quick", "--routines", "--triggers", "--events", "--set-gtid-purged=OFF", "--no-tablespaces",
                 "--databases", self.config["database_name"]], int(self.config.get("backup_timeout_seconds", 300)), output)
        require(path.stat().st_size > 100, "Backup is empty")
        with path.open("rb") as stream:
            digest = hashlib.file_digest(stream, "sha256").hexdigest()
        write_json(path.with_suffix(".json"), {"sha256": digest, "bytes": path.stat().st_size, "created_at": utcnow().isoformat()})
        return str(path)

    def preflight(self, release, operation):
        require(sys.version_info >= (3, 11), "Python 3.11+ is required on the deployment server")
        require(platform.system() == "Linux" and platform.machine() in ("x86_64", "amd64"), "linux/amd64 server required")
        require(self.config["api_base_url"] == release["config"][self.environment]["api_base_url"]
            and self.config["storefront_url"] == release["config"][self.environment]["storefront_url"], "Image/environment URL mismatch")
        ports = [self.config["backend_port"], self.config["admin_port"]]
        require(all(type(port) is int and 1024 <= port <= 65535 for port in ports) and ports[0] != ports[1], "Invalid or overlapping ports")
        for name in ("uploads_path", "logs_path"):
            require(Path(self.config[name]).is_absolute() and Path(self.config[name]).is_dir(), "Required persistent directory missing")
        require((self.root / "config/backend.env").is_file(), "Backend configuration missing")
        require(self.config.get("smoke_checks") and {c["tenant_id"] for c in self.config["smoke_checks"]} >= {121, 162},
                "Configure read-only CMS checks for both existing tenants")
        # Host networking preserves existing loopback database/Redis URLs, but the old service must be stopped explicitly.
        active = subprocess.run(["systemctl", "is-active", "--quiet", "oakved-yudao.service"], timeout=10).returncode
        require(active != 0, "Legacy systemd backend is active; perform the first cutover separately")
        version = run(["docker", "compose", "version", "--short"]).strip().lstrip("v").split(".")
        require(tuple(map(int, version[:2])) >= (2, 30), "Docker Compose 2.30+ required for literal secret env values")
        if self.state.get("current"):
            self.current_verified()
        else:
            for port in ports:
                with socket.socket() as sock:
                    sock.bind(("127.0.0.1", port))
        current_schema = self.database_version()
        target_schema = release["database_version"]
        review = self.config.get("migration_reviews", {}).get(release["id"], {})
        if target_schema < current_schema:
            require(operation == "rollback" and release["id"] in self.state.get("compatible", []), "Database compatibility has not been verified for this rollback")
        elif target_schema > current_schema:
            require(review.get("from_version") == current_schema and review.get("to_version") == target_schema
                and review.get("backward_compatible") is True and review.get("restore_rehearsal_passed") is True,
                "Migration requires a matching compatibility review and tested backup recovery")
            if self.state.get("current"):
                require(self.state["current"] in review.get("compatible_release_ids", []), "Current program is not covered by migration compatibility review")
        reserve = self.config.get("min_free_bytes", 10 * 1024**3)
        require(type(reserve) is int and reserve > 0 and self.config.get("image_peak_bytes", 0) > 0, "Invalid disk budget")
        required = reserve
        if target_schema > current_schema:
            require(self.config.get("backup_estimate_bytes", 0) > 0, "Measure database backup capacity first")
            required += self.config["backup_estimate_bytes"] * 1.2
        docker_root = run(["docker", "info", "--format", "{{.DockerRootDir}}"]).strip()
        require(Path(docker_root).is_absolute(), "Docker data directory unknown")
        for directory, needed in ((self.root, required), (docker_root, reserve + self.config["image_peak_bytes"]),
                                  (self.config["logs_path"], reserve), (self.config["uploads_path"], reserve)):
            usage = shutil.disk_usage(directory)
            require(usage.free >= needed and usage.used / usage.total < 0.9, "Insufficient disk capacity for download/backup/runtime")
        return current_schema, review

    def stage(self, release, compose_source):
        directory = self.record(release["id"])
        if directory.exists():
            require(self.manifest(release["id"]) == release, "Existing release manifest changed")
            return
        directory.mkdir(parents=True)
        (directory / "compose.yml").write_text(compose_source, encoding="utf-8")
        shutil.copyfile(self.root / "config/backend.env", directory / "backend.env")
        images = environment_images(release, self.environment)
        values = {"ERP_BACKEND_IMAGE": images["erp-backend"], "ERP_ADMIN_IMAGE": images["erp-admin"],
            "ERP_RELEASE_ID": release["id"], "ERP_DEPLOY_ENVIRONMENT": self.environment,
            "ERP_BACKEND_PORT": self.config["backend_port"], "ERP_ADMIN_PORT": self.config["admin_port"],
            "ERP_UPLOADS_PATH": self.config["uploads_path"], "ERP_UPLOADS_TARGET": self.config["uploads_target"],
            "ERP_LOGS_PATH": self.config["logs_path"]}
        require(all(re.fullmatch(r"[A-Za-z0-9_./:@-]+", str(v)) for v in values.values()), "Compose paths must be simple absolute paths without interpolation")
        (directory / "images.env").write_text("".join(f"{k}={v}\n" for k, v in values.items()))
        write_json(directory / "release.json", release)

    def healthy(self, release, headers=None, local_only=False):
        self.verify_containers(release)
        local_api = f"http://127.0.0.1:{self.config['backend_port']}"
        api = local_api if local_only else self.config["api_base_url"]
        urls = [local_api + "/actuator/health"]
        if not local_only:
            urls.append(api + "/actuator/health")
        for url in urls:
            status, data = http_json(url, headers)
            require(status == 200 and isinstance(data, dict) and data.get("status") == "UP", "Backend/proxy health failed")
            status, data = http_json(url.removesuffix("/health") + "/info", headers)
            require(status == 200 and isinstance(data, dict) and data.get("erp") == {
                "release": release["id"], "environment": self.environment}, "Backend/proxy serves another release or environment")
        admin_urls = [f"http://127.0.0.1:{self.config['admin_port']}/healthz"]
        receipts = [f"http://127.0.0.1:{self.config['admin_port']}/admin/release.json"]
        if not local_only:
            admin_urls.append(self.config["admin_url"])
            receipts.append(self.config["admin_url"].rstrip("/") + "/release.json")
        for url in admin_urls:
            require(http_json(url, headers)[0] == 200, "Admin/proxy HTTP check failed")
        for url in receipts:
            status, data = http_json(url, headers)
            require(status == 200 and data == {"release": release["id"], "environment": self.environment},
                    "Admin/proxy serves another release or environment")
        status, data = http_json(api + "/admin-api/system/auth/get-permission-info", {**(headers or {}), "tenant-id": "1"})
        require(status in (401, 403) or (status == 200 and isinstance(data, dict) and data.get("code") in (401, 403)), "Anonymous admin access is not denied")
        for check in self.config["smoke_checks"]:
            require(check["path"].startswith("/app-api/") and ".." not in check["path"], "Only public read-only smoke checks are supported")
            status, data = http_json(api + check["path"], {**(headers or {}), "tenant-id": str(check["tenant_id"])})
            require(status == 200 and isinstance(data, dict) and data.get("code") == 0, "Tenant public API smoke check failed")
        require(self.database_version() >= release["database_version"], "Migration target was not reached")

    def wait_healthy(self, release, **options):
        deadline = time.monotonic() + 180
        last_log = 0
        while True:
            try:
                self.healthy(release, **options)
                return
            except (ValueError, RuntimeError, OSError):
                if time.monotonic() >= deadline:
                    raise RuntimeError("Service readiness/smoke checks did not pass within 180 seconds") from None
                if time.monotonic() - last_log >= 10:
                    self.log("Waiting for service readiness and tenant smoke checks")
                    last_log = time.monotonic()
                time.sleep(3)

    def deploy(self, release, operation, compose_source):
        self.ensure_no_lease()
        require(not self.state.get("in_progress"), "Previous operation is unfinished; inspect state before retrying")
        release = validate_release(release)
        previous = self.state.get("current")
        if operation == "rollback":
            require(release["id"] in self.state["history"] and release["id"] in self.state["history"][:2] + self.state.get("pins", []),
                    "Rollback target is not a protected previously successful deployment")
        if previous == release["id"]:
            self.healthy(release)
            return {"status": "already-current", "release_id": previous}
        schema, review = self.preflight(release, operation)
        self.stage(release, compose_source)
        self.state["in_progress"] = {"release_id": release["id"], "previous": previous, "phase": "pull", "started_at": utcnow().isoformat()}
        self.save()
        switched = False
        try:
            self.log("Pulling " + release["id"])
            self.compose(release["id"], "pull", timeout=300)
            if release["database_version"] > schema:
                self.state["in_progress"]["backup"] = self.backup(release["id"])
                self.save()
            self.state["in_progress"]["phase"] = "switch"
            self.save()
            switched = True
            if previous:
                self.compose(previous, "stop", "--timeout", "30", timeout=60)
            self.log("Starting backend; Flyway validates the existing database")
            self.compose(release["id"], "up", "--detach", "--no-build", "--pull", "never", "erp-backend", timeout=120)
            self.compose(release["id"], "up", "--detach", "--no-build", "--pull", "never", "erp-admin", timeout=120)
            self.wait_healthy(release)
        except Exception as error:
            result = {"status": "failed-before-switch", "release_id": release["id"], "error": str(error)}
            if switched:
                result["status"] = "manual-recovery-required"
                try:
                    self.compose(release["id"], "stop", "--timeout", "30", timeout=60)
                    after = self.database_version()
                    safe = (release["database_version"] <= schema and after == schema) or (
                        release["database_version"] > schema and after == release["database_version"]
                        and previous in review.get("compatible_release_ids", []))
                    if previous and safe:
                        self.compose(previous, "up", "--detach", "--no-build", "--pull", "never", timeout=120)
                        self.wait_healthy(self.manifest(previous))
                        result["status"] = "rolled-back"
                        self.state["schema_version"] = after
                        if after > schema:
                            self.state["compatible"] = review.get("compatible_release_ids", [])
                except Exception:
                    pass
            self.state["last_failure"] = result
            if result["status"] != "manual-recovery-required":
                self.state["in_progress"] = None
            self.save()
            raise RuntimeError(result["status"] + ": " + str(error)) from None
        history = [x for x in [previous, *self.state["history"]] if x and x != release["id"]]
        self.state["history"] = list(dict.fromkeys(history))
        self.state["current"] = release["id"]
        self.state["schema_version"] = self.database_version()
        if release["database_version"] > schema:
            self.state["compatible"] = review.get("compatible_release_ids", [])
        else:
            self.state["compatible"] = list(set(self.state.get("compatible", []) + [x for x in history if self.manifest(x)["database_version"] == schema]))
        self.state["in_progress"] = None
        self.save()
        self.log("Deployment healthy: " + release["id"])
        try:
            self.local_cleanup()
        except Exception as error:
            self.log("Local image cleanup skipped: " + str(error))
        return {"status": "success", "release_id": release["id"], "schema_version": self.state["schema_version"]}

    def local_cleanup(self):
        protected = {self.state["current"], *self.state["history"][:2], *self.state.get("pins", [])}
        keep_images = {ref for rid in protected for ref in environment_images(self.manifest(rid), self.environment).values()}
        for directory in (self.root / "releases").iterdir():
            rid = directory.name
            if not RELEASE.fullmatch(rid) or not (directory / "release.json").is_file():
                continue
            if rid in protected:
                continue
            for image in environment_images(self.manifest(rid), self.environment).values():
                if image in keep_images:
                    continue
                try:
                    run(["docker", "image", "inspect", "--format", "{{.Id}}", image])
                except RuntimeError:
                    continue
                if run(["docker", "ps", "--all", "--quiet", "--filter", "ancestor=" + image]).strip():
                    continue
                # No force/prune/volume deletion; Docker refuses images still referenced elsewhere.
                try:
                    run(["docker", "image", "rm", image], 30)
                except RuntimeError:
                    pass


def main(payload):
    os.umask(0o077)
    root = Path(payload["root"]).resolve()
    require(root.is_dir() and root.name == payload["environment"], "Invalid environment directory")
    with locked(root / "operation.lock"):
        server = Server(payload["root"], payload["environment"])
        action = payload["operation"]
        if action in ("lease-start", "lease-check", "lease-end"):
            result = server.lease(action, payload["lease_id"])
        elif action == "snapshot":
            result = server.snapshot()
        else:
            require(action in ("deploy", "rollback"), "Unknown server operation")
            result = server.deploy(payload["release"], action, payload["compose"])
    print("ERP_CD_RESULT=" + json.dumps(result, sort_keys=True), flush=True)
