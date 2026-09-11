"""GitHub Actions SSH client for the two fixed environment entry points."""
import argparse
import base64
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import tempfile
import time

from common import fingerprint, require, write_json
from github_release import GitHub
from bootstrap_policy import OPERATIONS, PROFILE, validate_target, validate_audit_bundle, sha256

HERE = Path(__file__).resolve().parent
BOOTSTRAP = """import base64,json,sys,types
b=json.load(sys.stdin)
for name in b['modules']:
 m=types.ModuleType(name);sys.modules[name]=m
 exec(compile(base64.b64decode(b['modules'][name]),name+'.py','exec'),m.__dict__)
try:sys.modules[b['entry']].main(b['request'])
except Exception as e:
 print('ERP_CD_ERROR='+str(e),flush=True)
 sys.exit(1)
"""

RELAY_BOOTSTRAP = BOOTSTRAP.replace("b=json.load(sys.stdin)", """import os
def read_exact(count):
 data=bytearray()
 while len(data)<count:
  part=os.read(0,min(65536,count-len(data)))
  if not part:raise ValueError('Truncated relay header')
  data.extend(part)
 return data
length=int.from_bytes(read_exact(8),'big')
if not 0<length<1048576:raise ValueError('Invalid relay header size')
b=json.loads(read_exact(length))""")


def image_transport(environment):
    mode = os.environ.get("ERP_IMAGE_TRANSPORT") or ("ssh" if environment == "test" else "ghcr")
    require(mode in ("ssh", "ghcr"), "ERP_IMAGE_TRANSPORT must be ssh or ghcr; a regional registry is not configured")
    require(mode != "ssh" or environment == "test", "SSH image relay is limited to test")
    return mode


def bootstrap_bundle():
    """Compile trusted source with no package downloads and export existing read-only audits."""
    audit = json.loads(subprocess.run(["node", str(HERE / "bootstrap-audit.mjs")], capture_output=True, text=True,
                                     check=True, timeout=30).stdout)
    validate_audit_bundle(audit)
    with tempfile.TemporaryDirectory(prefix="erp-clone-helper-") as directory:
        javac = str(Path(os.environ["JAVA_HOME"]) / "bin" / ("javac.exe" if os.name == "nt" else "javac")) if os.environ.get("JAVA_HOME") else "javac"
        subprocess.run([javac, "--release", "17", "-d", directory, str(HERE / "CloneMigration.java")],
                       capture_output=True, check=True, timeout=30)
        code = (Path(directory) / "CloneMigration.class").read_bytes()
    return {"audit": audit, "helper": {"class": base64.b64encode(code).decode(), "sha256": sha256(code)}}


def transport(command, bundle, directory, timeout, input_path=None):
    """Stream sanitized server progress; keep SSH diagnostics out of public Actions logs."""
    incoming, outgoing, errors = (Path(directory) / name for name in ("request", "stdout", "stderr"))
    if input_path is None:
        incoming.write_text(json.dumps(bundle), encoding="utf-8")
    else:
        incoming = Path(input_path)
    start = changed = time.monotonic()
    offset = last_size = 0
    pending, results, remote_errors = "", [], []
    with incoming.open("rb") as source, outgoing.open("wb") as out, errors.open("wb") as err:
        child = subprocess.Popen(command, stdin=source, stdout=out, stderr=err)
        try:
            while True:
                size = outgoing.stat().st_size
                if size != last_size:
                    changed, last_size = time.monotonic(), size
                    with outgoing.open("rb") as reader:
                        reader.seek(offset)
                        pending += reader.read().decode("utf-8", errors="replace")
                        offset = reader.tell()
                    lines = pending.split("\n")
                    pending = lines.pop()
                    for line in lines:
                        if line.startswith("ERP_CD_RESULT="):
                            results.append(json.loads(line.removeprefix("ERP_CD_RESULT=")))
                        else:
                            if line.startswith("ERP_CD_ERROR="):
                                remote_errors.append(line.removeprefix("ERP_CD_ERROR=")[:1000])
                            print(line, flush=True)
                if child.poll() is not None and outgoing.stat().st_size == offset:
                    break
                require(time.monotonic() - start < timeout, "SSH operation exceeded its deadline; use recover to inspect the journal")
                require(time.monotonic() - changed < 60, "No server progress for 60 seconds; inspect the journal before retrying")
                time.sleep(0.2)
        finally:
            if child.poll() is None:
                child.terminate()
                try:
                    child.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    child.kill()
                    child.wait(timeout=5)
    require(child.returncode == 0, "SSH operation failed: " + ("; ".join(remote_errors)[-2000:] if remote_errors
            else "inspect the recorded bootstrap/deployment report on the server"))
    require(len(results) == 1 and not pending.strip(), "Missing or ambiguous server result")
    return results[0]


def preload_test_images(command, release, directory):
    from image_archive import build_archive
    modules = {name: base64.b64encode((HERE / (name + ".py")).read_bytes()).decode()
               for name in ("common", "bootstrap_policy", "bootstrap_io", "image_relay")}
    relay = {"entry": "image_relay", "request": {"operation": "probe", "release": release, "environment": "test"}, "modules": modules}
    probe_command = command[:-1] + ["sudo -n timeout --kill-after=15s 70s python3 -B -c " + shlex.quote(BOOTSTRAP)]
    receipt = transport(probe_command, relay, directory, 85)
    require(receipt.get("release_hash") == fingerprint(release) and receipt.get("status") in ("images-present", "images-missing"), "Invalid image cache receipt")
    if receipt["status"] == "images-present":
        print("Verified release images are already cached; skipping image download/transfer.", flush=True)
        return
    archive, header = build_archive(release, "test", Path(directory) / "image-archive")
    relay["request"] = header
    # Exact framing avoids OS argument limits and buffered JSON readers consuming tar bytes.
    incoming = Path(directory) / "image-transfer.bin"
    raw = json.dumps(relay).encode()
    with incoming.open("wb") as output, archive.open("rb") as source:
        output.write(len(raw).to_bytes(8, "big") + raw)
        shutil.copyfileobj(source, output, length=1024 * 1024)
    relay_command = command[:-1] + ["sudo -n timeout --signal=TERM --kill-after=15s 950s python3 -B -c " + shlex.quote(RELAY_BOOTSTRAP)]
    receipt = transport(relay_command, {}, directory, 970, input_path=incoming)
    require(receipt == {"status": "images-ready", "release_hash": fingerprint(release)}, "Incomplete image relay receipt")


def ssh_request(environment, operation, release=None, lease_id=None, confirm_cutover=False, connection=None):
    mode = "local-scp" if connection else image_transport(environment)
    host, user = os.environ["ERP_SSH_HOST"], os.environ["ERP_SSH_USER"]
    require(re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9.-]*", host) and re.fullmatch(r"[a-z_][a-z0-9_-]*", user), "Invalid SSH destination")
    port = int(os.environ.get("ERP_SSH_PORT") or "22")
    require(1 <= port <= 65535, "Invalid SSH port")
    if connection:
        require(environment == "test" and (host, user, port) == (PROFILE["host"], PROFILE["user"], 22)
                and (not os.environ.get("GITHUB_ACTIONS") or (getattr(connection, "ci_verified", False)
                     and os.environ.get("RUNNER_ENVIRONMENT") == "self-hosted")),
                "Local SCP requires the local entry point or verified self-hosted CI")
    root = os.environ.get("ERP_DEPLOY_ROOT") or "/opt/oakved-deploy/" + environment
    require(re.fullmatch(r"/[A-Za-z0-9_/-]+", root) and ".." not in root and root.endswith("/" + environment), "Invalid deployment root")
    request = {"root": root, "environment": environment, "operation": operation}
    if release:
        request.update(release=release, compose=(HERE / "compose.yml").read_text(encoding="utf-8"))
    if lease_id:
        request["lease_id"] = lease_id
    names = ["common", "image_pull", "server"]
    entry, remote_command = "server", "timeout --signal=TERM --kill-after=15s 1400s python3 -B -c "
    if operation in ("snapshot", "lease-start", "lease-check", "lease-end"):
        remote_command = "timeout --signal=TERM --kill-after=5s 45s python3 -B -c "
    if operation in OPERATIONS:
        validate_target(environment, root, release, operation, confirm_cutover)
        require((host, user, port) == (PROFILE["host"], PROFILE["user"], 22), "Bootstrap SSH target differs from the verified test host")
        request.update(bootstrap_bundle(), confirm_cutover=confirm_cutover)
        names += ["bootstrap_policy", "bootstrap_io", "bootstrap_image", "bootstrap"]
        entry = "bootstrap"
        remote_command = "sudo -n timeout --signal=TERM --kill-after=200s 1400s python3 -B -c "
    bundle = {"entry": entry, "modules": {name: base64.b64encode((HERE / (name + ".py")).read_bytes()).decode() for name in names}}
    bundle["request"] = request
    with tempfile.TemporaryDirectory(prefix="erp-cd-ssh-") as directory:
        key, known = Path(directory) / "identity", Path(directory) / "known_hosts"
        if connection:
            key, known = connection.key, connection.known
        else:
            key.write_text(os.environ["ERP_SSH_PRIVATE_KEY"].rstrip() + "\n", encoding="utf-8")
            known.write_text(os.environ["ERP_SSH_KNOWN_HOSTS"].rstrip() + "\n", encoding="utf-8")
            key.chmod(0o600)
            known.chmod(0o600)
        command = ["ssh", "-T", "-i", str(key), "-p", str(port), "-o", "BatchMode=yes", "-o", "IdentitiesOnly=yes",
            "-o", "StrictHostKeyChecking=yes", "-o", "UserKnownHostsFile=" + str(known), "-o", "ConnectTimeout=10",
            "-o", "ServerAliveInterval=10", "-o", "ServerAliveCountMax=3", user + "@" + host,
            remote_command + shlex.quote(BOOTSTRAP)]
        if connection:
            command = connection.command(remote_command + shlex.quote(BOOTSTRAP))
        if operation in ("prepare", "deploy", "rollback"):
            print(json.dumps({"stage": "image-delivery", "transport": mode, "environment": environment}), flush=True)
        if mode == "ssh" and operation in ("prepare", "deploy", "rollback"):
            require(environment == "test" and (host, user, port, root) == (PROFILE["host"], PROFILE["user"], 22, PROFILE["root"]),
                    "SSH image relay is limited to the verified test host")
            preload_test_images(command, release, directory)
            request["images_preloaded"] = True
        if connection and operation in ("prepare", "deploy", "rollback"):
            connection.preload(command, release, directory)
            request["images_preloaded"] = True
        # The remote journal remains unfinished if SSH disconnects, so a retry cannot blindly deploy twice.
        return transport(command, bundle, directory, 1700 if operation in OPERATIONS else (1500 if operation in ("preflight", "deploy", "rollback") else 60))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--environment", choices=("test", "production"), required=True)
    parser.add_argument("--operation", choices=(*OPERATIONS, "preflight", "deploy", "rollback", "snapshot", "lease-start", "lease-check", "lease-end"), default="deploy")
    parser.add_argument("--confirm-cutover", choices=("true", "false"), default="false")
    parser.add_argument("--release")
    parser.add_argument("--lease-id")
    parser.add_argument("--output", default="erp-cd-result.json")
    args = parser.parse_args()
    require(os.environ.get("GITHUB_REF") == "refs/heads/main", "Use the trusted main workflow")
    execute(args)


def execute(args, connection=None):
    try:
        return execute_operation(args, connection)
    except Exception as error:
        # Include failures before SSH (disabled CD, missing release, CI/test gate).
        write_json(args.output, {"status": "failed", "environment": args.environment,
            "operation": args.operation, "release_id": args.release, "error": str(error)})
        raise


def execute_operation(args, connection=None):
    options = {"connection": connection} if connection else {}
    if args.operation not in (*OPERATIONS, "preflight", "deploy", "rollback"):
        result = ssh_request(args.environment, args.operation, lease_id=args.lease_id, **options)
    else:
        if args.operation in ("deploy", "rollback"):
            require(os.environ.get("ERP_CD_ENABLED") == "true", "Enable daily CD after successful first cutover; use prepare/cutover for test onboarding")
        github = GitHub()
        _, release = github.release(args.release or "")
        github.verify_ci(release)
        if args.operation == "preflight":
            require(args.environment == "production", "Release preflight is a production-only operation")
        if args.environment == "production" and args.operation in ("preflight", "deploy"):
            require(github.test_passed(release), "This exact release manifest has not passed the test CD")
        if args.operation in OPERATIONS:
            validate_target(args.environment, os.environ.get("ERP_DEPLOY_ROOT") or PROFILE["root"], release, args.operation, args.confirm_cutover == "true")
        deployment = None if args.operation in ("preflight", "prepare", "recover") else github.deployment(release, args.environment)
        if deployment:
            github.deployment_status(deployment, "in_progress")
        try:
            result = ssh_request(args.environment, args.operation, release=release, confirm_cutover=args.confirm_cutover == "true", **options)
            accepted = ("preflight-passed",) if args.operation == "preflight" else ("prepared",) if args.operation == "prepare" else ("success", "already-current", "restored-legacy", "prepare-failed") if args.operation == "recover" else ("success", "already-current")
            require(result["status"] in accepted, "Remote operation did not complete")
        except Exception as error:
            write_json(args.output, {"status": "failed", "environment": args.environment, "operation": args.operation, "release_id": release["id"], "error": str(error)})
            try:
                if deployment:
                    github.deployment_status(deployment, "failure")
            except Exception:
                print("Could not update GitHub deployment status; inspect the server journal.", flush=True)
            raise
        if args.operation == "recover" and result["status"] in ("success", "already-current"):
            deployment = github.deployment(release, args.environment)
        if deployment:
            github.deployment_status(deployment, "success")
    write_json(args.output, result)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as stream:
            stream.write(f"### ERP {args.environment}: {args.operation}\n\n```json\n{json.dumps(result, indent=2)}\n```\n")


if __name__ == "__main__":
    main()
