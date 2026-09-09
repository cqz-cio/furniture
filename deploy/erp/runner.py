"""GitHub Actions SSH client for the two fixed environment entry points."""
import argparse
import base64
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import tempfile

from common import require, write_json
from github_release import GitHub

HERE = Path(__file__).resolve().parent
BOOTSTRAP = """import base64,json,sys,types
b=json.load(sys.stdin)
m=types.ModuleType('common');sys.modules['common']=m
exec(compile(base64.b64decode(b['common']),'common.py','exec'),m.__dict__)
s=types.ModuleType('erp_server')
exec(compile(base64.b64decode(b['server']),'server.py','exec'),s.__dict__)
try:s.main(b['request'])
except Exception as e:
 print('ERP_CD_ERROR='+str(e),flush=True)
 sys.exit(1)
"""


def ssh_request(environment, operation, release=None, lease_id=None):
    host, user = os.environ["ERP_SSH_HOST"], os.environ["ERP_SSH_USER"]
    require(re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9.-]*", host) and re.fullmatch(r"[a-z_][a-z0-9_-]*", user), "Invalid SSH destination")
    port = int(os.environ.get("ERP_SSH_PORT") or "22")
    require(1 <= port <= 65535, "Invalid SSH port")
    root = os.environ.get("ERP_DEPLOY_ROOT") or "/opt/oakved-deploy/" + environment
    require(re.fullmatch(r"/[A-Za-z0-9_/-]+", root) and ".." not in root and root.endswith("/" + environment), "Invalid deployment root")
    request = {"root": root, "environment": environment, "operation": operation}
    if release:
        request.update(release=release, compose=(HERE / "compose.yml").read_text(encoding="utf-8"))
    if lease_id:
        request["lease_id"] = lease_id
    bundle = {name: base64.b64encode((HERE / (name + ".py")).read_bytes()).decode() for name in ("common", "server")}
    bundle["request"] = request
    with tempfile.TemporaryDirectory(prefix="erp-cd-ssh-") as directory:
        key, known = Path(directory) / "identity", Path(directory) / "known_hosts"
        key.write_text(os.environ["ERP_SSH_PRIVATE_KEY"].rstrip() + "\n", encoding="utf-8")
        known.write_text(os.environ["ERP_SSH_KNOWN_HOSTS"].rstrip() + "\n", encoding="utf-8")
        key.chmod(0o600)
        known.chmod(0o600)
        command = ["ssh", "-T", "-i", str(key), "-p", str(port), "-o", "BatchMode=yes", "-o", "IdentitiesOnly=yes",
            "-o", "StrictHostKeyChecking=yes", "-o", "UserKnownHostsFile=" + str(known), "-o", "ConnectTimeout=10",
            "-o", "ServerAliveInterval=10", "-o", "ServerAliveCountMax=3", user + "@" + host,
            "python3 -B -c " + shlex.quote(BOOTSTRAP)]
        # The remote journal remains unfinished if SSH disconnects, so a retry cannot blindly deploy twice.
        result = subprocess.run(command, input=json.dumps(bundle), capture_output=True, text=True,
                                timeout=1500 if operation in ("deploy", "rollback") else 60)
        for line in result.stdout.splitlines():
            if not line.startswith("ERP_CD_RESULT="):
                print(line, flush=True)
        require(result.returncode == 0, "SSH deployment failed; inspect the server's deployment.log/state.json")
        values = [line[len("ERP_CD_RESULT="):] for line in result.stdout.splitlines() if line.startswith("ERP_CD_RESULT=")]
        require(len(values) == 1, "Missing or ambiguous server result")
        return json.loads(values[0])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--environment", choices=("test", "production"), required=True)
    parser.add_argument("--operation", choices=("deploy", "rollback", "snapshot", "lease-start", "lease-check", "lease-end"), default="deploy")
    parser.add_argument("--release")
    parser.add_argument("--lease-id")
    parser.add_argument("--output", default="erp-cd-result.json")
    args = parser.parse_args()
    require(os.environ.get("GITHUB_REF") == "refs/heads/main", "Use the trusted main workflow")
    if args.operation not in ("deploy", "rollback"):
        result = ssh_request(args.environment, args.operation, lease_id=args.lease_id)
    else:
        require(os.environ.get("ERP_CD_ENABLED") == "true", "Enable this environment only after first-cutover rehearsal")
        github = GitHub()
        _, release = github.release(args.release or "")
        github.verify_ci(release)
        if args.environment == "production" and args.operation == "deploy":
            require(github.test_passed(release), "This exact release manifest has not passed the test CD")
        deployment = github.deployment(release, args.environment)
        github.deployment_status(deployment, "in_progress")
        try:
            result = ssh_request(args.environment, args.operation, release=release)
            require(result["status"] in ("success", "already-current"), "Remote deployment did not complete")
        except Exception:
            try:
                github.deployment_status(deployment, "failure")
            except Exception:
                print("Could not update GitHub deployment status; inspect the server journal.", flush=True)
            raise
        github.deployment_status(deployment, "success")
    write_json(args.output, result)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as stream:
            stream.write(f"### ERP {args.environment}: {args.operation}\n\n```json\n{json.dumps(result, indent=2)}\n```\n")


if __name__ == "__main__":
    main()
