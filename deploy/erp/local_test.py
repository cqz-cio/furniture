"""Local-only test deployment: verified CI images -> SCP -> existing server journal."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import shlex
import subprocess
import sys
import tempfile
import time

from bootstrap_policy import PROFILE
from common import RELEASE, fingerprint, require, validate_release, write_json
from github_release import GitHub
from image_archive import build_archive
from runner import BOOTSTRAP, HERE, execute, transport


def stop(child):
    if child.poll() is not None:
        return
    if os.name == "nt":
        subprocess.run(["taskkill", "/PID", str(child.pid), "/T", "/F"], capture_output=True, timeout=15)
    else:
        child.terminate()
    try:
        child.wait(timeout=5)
    except subprocess.TimeoutExpired:
        child.kill()
        child.wait(timeout=5)


def tool(name):
    windows = {"gh": r"C:\Program Files\GitHub CLI\gh.exe",
               "ssh": r"C:\Windows\System32\OpenSSH\ssh.exe",
               "scp": r"C:\Windows\System32\OpenSSH\scp.exe"}
    found = windows.get(name) if os.name == "nt" and Path(windows.get(name, "missing")).is_file() else shutil.which(name)
    require(found, "Missing local tool: " + name)
    return found


def remote_directory(value):
    require(isinstance(value, str) and re.fullmatch(r"/var/tmp/oakved-local-scp-[a-z0-9_]{8}", value), "Invalid remote SCP directory")
    return value


class LocalConnection:
    def __init__(self, key, known, cache):
        self.key, self.known, self.cache = Path(key).resolve(), Path(known).resolve(), Path(cache).resolve()
        require(self.key.is_file() and self.known.is_file(), "SSH key or known_hosts is missing")
        self.options = ["-F", "NUL" if os.name == "nt" else "/dev/null", "-i", str(self.key),
                        "-o", "BatchMode=yes", "-o", "IdentitiesOnly=yes", "-o", "StrictHostKeyChecking=yes",
                        "-o", "UserKnownHostsFile=" + str(self.known), "-o", "GlobalKnownHostsFile=" + ("NUL" if os.name == "nt" else "/dev/null"),
                        "-o", "ConnectTimeout=8", "-o", "ServerAliveInterval=5", "-o", "ServerAliveCountMax=2"]
        self.target = PROFILE["user"] + "@" + PROFILE["host"]

    def command(self, remote):
        return [tool("ssh"), "-T", *self.options, "-p", "22", self.target, remote]

    def receipt(self, release, directory, request):
        modules = {n: base64.b64encode((HERE / (n + ".py")).read_bytes()).decode()
                   for n in ("common", "bootstrap_policy", "bootstrap_io", "image_relay")}
        bundle = {"entry": "image_relay", "modules": modules,
                  "request": {"release": release, "environment": "test", **request}}
        command = self.command("sudo -n timeout --kill-after=15s 400s python3 -B -c " + shlex.quote(BOOTSTRAP))
        return transport(command, bundle, directory, 420)

    def archive(self, release):
        root = self.cache / release["id"]
        root.mkdir(parents=True, exist_ok=True)
        saved = root / "complete"
        if saved.is_dir():
            header = json.loads((saved / "header.json").read_text())
            archive = saved / "images.oci.tar"
            require(header["release"] == release and header["environment"] == "test", "Cached release differs")
            require(archive.stat().st_size == header["bytes"], "Cached archive is incomplete")
            with archive.open("rb") as source:
                require(hashlib.file_digest(source, "sha256").hexdigest() == header["sha256"], "Cached archive is corrupt")
            return archive, header
        with tempfile.TemporaryDirectory(prefix="download-", dir=root) as tmp:
            # Real local download reached 760 MB at 293 s; the CI runner's 300 s
            # deadline is too short here. Keep a finite local-only 15 min budget.
            archive, header = build_archive(release, "test", Path(tmp) / "bundle", timeout_seconds=900)
            write_json(archive.parent / "header.json", header)
            # Complete cache becomes visible only after every blob was verified.
            archive.parent.rename(saved)
        return saved / "images.oci.tar", header

    def transfer(self, archive, remote, size, release, directory):
        remote_directory(remote)
        command = [tool("scp"), "-B", *self.options, "-P", "22", str(archive), self.target + ":" + remote + "/images.oci.tar"]
        start = changed = last_probe = time.monotonic()
        previous = 0
        # An 800 MB archive needs ~19 min at the measured local SSH rate; allow 30 min.
        logs = self.cache / "transfer-logs"
        logs.mkdir(parents=True, exist_ok=True)
        log_path = logs / (str(time.time_ns()) + "-scp.log")
        with log_path.open("wb") as log:
            child = subprocess.Popen(command, stdin=subprocess.DEVNULL, stdout=log, stderr=log)
            print(json.dumps({"stage": "local-scp", "pid": child.pid, "bytes": size, "timeout_seconds": 1800, "log": str(log_path)}), flush=True)
            try:
                while child.poll() is None:
                    time.sleep(.25)
                    now = time.monotonic()
                    if now - last_probe >= 10:
                        # A stat request has its own short deadline, independent of the bulk stream.
                        p = subprocess.run(self.command("timeout 5s stat -c %s " + shlex.quote(remote + "/images.oci.tar")),
                                           capture_output=True, text=True, timeout=12)
                        require(p.returncode == 0 and p.stdout.strip().isdigit(), "Cannot read SCP upload progress")
                        received = int(p.stdout.strip())
                        require(previous <= received <= size, "Unexpected remote SCP file size")
                        if received > previous:
                            changed = now
                        previous, last_probe = received, now
                        print(json.dumps({"stage": "local-scp", "received_bytes": received, "total_bytes": size,
                                          "elapsed_seconds": round(now-start)}), flush=True)
                    require(now-start < 1800 and now-changed < 60, "SCP timed out or made no progress for 60 seconds")
                require(child.returncode == 0, "SCP failed; see " + str(log_path))
            finally:
                stop(child)

    def preload(self, command, release, directory):
        probe = self.receipt(release, directory, {"operation": "probe"})
        require(probe.get("release_hash") == fingerprint(release), "Remote release receipt differs")
        if probe["status"] == "images-present":
            print("Verified test images are already on the server; skipping SCP.", flush=True)
            return
        require(probe["status"] == "images-missing", "Unknown image cache state")
        archive, header = self.archive(release)
        staged = self.receipt(release, directory, {**header, "operation": "scp-stage"})
        remote = remote_directory(staged["directory"])
        try:
            self.transfer(archive, remote, header["bytes"], release, directory)
            receipt = self.receipt(release, directory, {**header, "operation": "scp-import", "directory": remote})
            require(receipt == {"status": "images-ready", "release_hash": fingerprint(release)}, "Imported images differ from release")
        finally:
            try:
                self.receipt(release, directory, {"operation": "scp-cleanup", "directory": remote})
            except Exception:
                print("SCP staging cleanup could not finish; inspect the owned directory: " + remote, flush=True)


def worker(args):
    require_manual_context(args)
    if args.local_built:
        from cache_retention import cache_lock
        with cache_lock(args.cache):
            return worker_unlocked(args)
    return worker_unlocked(args)


def require_manual_context(args):
    manual_runner = (args.local_built and os.environ.get('GITHUB_ACTIONS') == 'true'
        and os.environ.get('RUNNER_ENVIRONMENT') == 'self-hosted'
        and os.environ.get('GITHUB_EVENT_NAME') == 'workflow_dispatch'
        and os.environ.get('GITHUB_REF') == 'refs/heads/main'
        and os.environ.get('GITHUB_REPOSITORY') == 'cqz-cio/furniture'
        and os.environ.get('GITHUB_WORKFLOW') == 'ERP CD - test')
    require(not os.environ.get("GITHUB_ACTIONS") or manual_runner,
            "Deployment requires a local manual command or the trusted manual test CD workflow")


def worker_unlocked(args):
    os.environ.update(GITHUB_REPOSITORY="cqz-cio/furniture", ERP_SSH_HOST=PROFILE["host"],
                      ERP_SSH_USER=PROFILE["user"], ERP_SSH_PORT="22", ERP_DEPLOY_ROOT=PROFILE["root"])
    if not os.environ.get("GH_TOKEN"):
        token = subprocess.run([tool("gh"), "auth", "token"], capture_output=True, text=True, timeout=15)
        require(token.returncode == 0 and token.stdout.strip(), "Log in with gh auth login first")
        os.environ["GH_TOKEN"] = token.stdout.strip()
    connection = LocalConnection(args.key, args.known_hosts, args.cache)
    tool("scp")
    if args.local_built:
        from local_ci import BuiltConnection, verify_upstream
        require(args.operation in ('deploy', 'rollback'), 'Local build cache supports daily deploy or rollback')
        require(RELEASE.fullmatch(args.release), 'Invalid cached release identity')
        cached = Path(args.cache).resolve()/args.release/'complete/release.json'
        require(cached.resolve().is_relative_to(Path(args.cache).resolve()), 'Cache path escapes its root')
        release = validate_release(json.loads(cached.read_text(encoding='utf-8')))
        require(release['schema'] == 2 and release['id'] == args.release and release['repository'] == 'cqz-cio/furniture',
                'Expected a local test build manifest')
        client = GitHub()
        verify_upstream(client, release['ci']['run_id'], release['ci']['run_attempt'], release['commit'])
        client.verify_local_build(release)
        connection = BuiltConnection(args.key, args.known_hosts, args.cache)
        connection.archive(release)
        if args.check_only:
            print(json.dumps({'status':'local-build-preflight-passed','release_id':release['id']}), flush=True)
            return
        from runner import ssh_request
        deployment = client.deployment(release, 'test')
        client.deployment_status(deployment, 'in_progress')
        try:
            result = ssh_request('test', args.operation, release=release, connection=connection)
            require(result.get('status') in ('success','already-current'), 'Deployment did not complete')
        except Exception:
            try:
                client.deployment_status(deployment, 'failure')
            except Exception:
                pass
            raise
        client.deployment_status(deployment, 'success')
        write_json(Path(args.cache)/'latest-deployment.json', result)
        print(json.dumps(result), flush=True)
        return
    # Fail before uploading if migration-audit tools are missing.
    if args.operation in ("prepare", "cutover", "recover"):
        require(shutil.which("node"), "Node.js is required for the test database audit")
        java = Path(os.environ.get("JAVA_HOME", "")) / "bin" / ("javac.exe" if os.name == "nt" else "javac")
        require(java.is_file(), "Set JAVA_HOME to JDK 17 or newer")
    if args.check_only:
        client = GitHub()
        _, release = client.release(args.release)
        client.verify_ci(release)
        print(json.dumps({"status": "local-preflight-passed", "release_id": release["id"], "host": PROFILE["host"],
                          "note": "No upload, image import, or deployment performed"}), flush=True)
        return
    args.environment, args.lease_id = "test", None
    if args.operation in ("deploy", "rollback"):
        os.environ["ERP_CD_ENABLED"] = "true"  # Server still requires successful first cutover.
    execute(args, connection=connection)


def main():
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--release", required=True)
    parser.add_argument("--operation", choices=("prepare", "cutover", "recover", "deploy", "rollback"), default="prepare")
    parser.add_argument("--confirm-cutover", choices=("true", "false"), default="false")
    parser.add_argument("--key", default=str(Path.home() / ".ssh/tripeer_github_actions"))
    parser.add_argument("--known-hosts", default=str(Path.home() / ".ssh/known_hosts"))
    parser.add_argument("--cache", default=str(HERE.parents[1] / "work/local-test-images"))
    parser.add_argument("--check-only", action="store_true")
    parser.add_argument("--local-built", action="store_true", help="Use verified local CI cache, with no registry download")
    parser.add_argument("--worker", action="store_true", help=argparse.SUPPRESS)
    parser.add_argument("--output", default="")
    args = parser.parse_args()
    if args.worker:
        worker(args)
        return
    logs = HERE.parents[1] / "work/codex-logs"
    logs.mkdir(parents=True, exist_ok=True)
    stamp = time.strftime("%Y%m%d-%H%M%S") + "-" + str(os.getpid())
    output = logs / (stamp + "-local-test-result.json")
    log_path = logs / (stamp + "-local-test.log")
    command = [sys.executable, "-u", "-B", str(Path(__file__).resolve()), *sys.argv[1:], "--worker", "--output", str(output)]
    start = changed = time.monotonic()
    offset = 0
    print("Log: " + str(log_path), flush=True)
    with log_path.open("wb") as log:
        child = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
        print("PID: " + str(child.pid), flush=True)
        try:
            while child.poll() is None:
                time.sleep(.25)
                size = log_path.stat().st_size
                if size > offset:
                    with log_path.open("rb") as source:
                        source.seek(offset)
                        print(source.read().decode("utf-8", errors="replace"), end="", flush=True)
                    offset, changed = size, time.monotonic()
                now = time.monotonic()
                require(now-start < 4000 and now-changed < 60, "Local deployment exceeded its time or idle limit; inspect the server journal before retrying")
            with log_path.open("rb") as source:
                source.seek(offset)
                print(source.read().decode("utf-8", errors="replace"), end="", flush=True)
        finally:
            stop(child)
            write_json(logs / (stamp + "-process.json"), {"pid": child.pid, "command": command, "cwd": os.getcwd(),
                "elapsed_seconds": round(time.monotonic()-start, 2), "exit_code": child.returncode, "log": str(log_path)})
    raise SystemExit(child.returncode)


if __name__ == "__main__":
    main()
