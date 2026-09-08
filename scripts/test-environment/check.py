"""Read-only smoke test for the explicitly designated Tencent test server."""

import argparse
import base64
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request

TARGET = ("124.220.2.69", "ubuntu", "22")
CREDENTIALS = (
    "YUDAO_DB_PASSWORD",
    "YUDAO_REDIS_PASSWORD",
    "VANZ_WEBSITE_INQUIRY_SHARED_SECRET",
)
SSH_SECRETS = ("TENCENT_SSH_PRIVATE_KEY", "TENCENT_SSH_KNOWN_HOSTS")


def validate_config(env, server_config=False):
    target = tuple(env.get(name, "") for name in (
        "TENCENT_SSH_HOST", "TENCENT_SSH_USER", "TENCENT_SSH_PORT"))
    if target != TARGET:
        raise ValueError("Target must be the approved test server, ubuntu@124.220.2.69:22.")
    required = () if server_config else SSH_SECRETS + CREDENTIALS
    missing = [name for name in required if not env.get(name, "").strip()]
    if missing:
        raise ValueError("Missing required configuration: " + ", ".join(missing))


def latest_migration(root):
    folder = root / "yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations"
    versions = [int(m.group(1)) for p in folder.glob("V*__*.sql")
                if (m := re.match(r"V(\d+)__", p.name))]
    if not versions:
        raise ValueError("Repository migration files are missing.")
    return max(versions)


def child_environment(env):
    # SSH receives the three application credentials only through encrypted stdin.
    return {k: v for k, v in env.items() if k not in CREDENTIALS + SSH_SECRETS}


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def public_page(url):
    # Never follow a redirect to a production hostname.
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
    with opener.open(url, timeout=4) as response:
        body = response.read(262144).lower()
        return response.status == 200 and b"<html" in body


def remote_check(env, key_path, hosts_path, version, server_config=False):
    source = Path(__file__).with_name("remote_check.py").read_bytes()
    encoded = base64.b64encode(source).decode("ascii")
    program = "import base64; exec(compile(base64.b64decode('" + encoded + "'), '<test-check>', 'exec'))"
    command = "sudo -n timeout --kill-after=2s 35s python3 -B -c " + shlex.quote(program)
    args = ["ssh", "-F", "none", "-i", str(key_path), "-p", TARGET[2],
            "-o", "BatchMode=yes", "-o", "IdentitiesOnly=yes",
            "-o", "StrictHostKeyChecking=yes",
            "-o", "UserKnownHostsFile=" + str(hosts_path),
            "-o", "GlobalKnownHostsFile=/dev/null",
            "-o", "ConnectTimeout=8", "-o", "ConnectionAttempts=1",
            "-o", "ServerAliveInterval=5", "-o", "ServerAliveCountMax=2",
            TARGET[1] + "@" + TARGET[0], command]
    payload = {"latest_migration": version, "server_config": server_config,
               "credentials": {} if server_config else {k: env[k] for k in CREDENTIALS}}
    result = subprocess.run(args, input=json.dumps(payload), text=True,
                            capture_output=True, timeout=43, env=child_environment(env))
    # Never echo SSH stderr or subprocess exceptions; they may contain credentials.
    if result.returncode != 0:
        raise RuntimeError("SSH or remote check failed; no raw command output was logged.")
    data = json.loads(result.stdout)
    if not isinstance(data, dict) or not isinstance(data.get("checks"), list) or not data["checks"]:
        raise RuntimeError("Remote check did not return a valid report.")
    return data


def run_checks(env, key_path, hosts_path, server_config=False):
    validate_config(env, server_config)
    version = latest_migration(Path(__file__).resolve().parents[2])
    data = remote_check(env, key_path, hosts_path, version, server_config)
    for name, url in (
        ("public_admin_login_page", "http://124.220.2.69/admin/login?redirect=/index"),
        ("public_corporate_website", "http://124.220.2.69:18081/"),
    ):
        try:
            ok = public_page(url)
        except Exception:
            ok = False
        data["checks"].append({"name": name, "status": "pass" if ok else "fail"})
    return data


def finish_report(data):
    data["passed"] = all(c["status"] != "fail" for c in data["checks"])
    data["release_ready"] = False
    data["scope"] = "Current services and credentials only; no release or CMS editing was performed."
    return data


def write_report(data, report, summary):
    rendered = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
    if report:
        Path(report).write_text(rendered, encoding="utf-8")
    if summary:
        lines = ["## ERP test environment check", "",
                 "Target: `124.220.2.69` (test). This job does not deploy, migrate, or edit content.", "",
                 "Credential source: `" + data.get("credential_source", "unavailable") + "`.", "",
                 "| Check | Result |", "| --- | --- |"]
        lines.extend("| " + c["name"] + " | " + c["status"] + " |" for c in data["checks"])
        lines.extend(["", "Migration versions: database `" + str(data.get("database_migration", "unknown"))
                      + "`, deployed JAR `" + str(data.get("jar_migration", "unknown"))
                      + "`, repository `" + str(data.get("repository_migration", "unknown")) + "`.", "",
                      "Release readiness has not been established. Review migration and frontend build settings separately.", ""])
        with Path(summary).open("a", encoding="utf-8") as output:
            output.write("\n".join(lines))
    print(rendered, end="")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report")
    parser.add_argument("--summary")
    parser.add_argument("--server-config", action="store_true",
                        help="Local diagnostic only: use active server credentials, not GitHub Secrets.")
    parser.add_argument("--ssh-key-file", type=Path)
    parser.add_argument("--known-hosts-file", type=Path)
    args = parser.parse_args(argv)
    data = {"checks": [], "credential_source": "active_server" if args.server_config else "provided_secrets"}
    try:
        validate_config(os.environ, args.server_config)
        if args.server_config:
            if not args.ssh_key_file or not args.known_hosts_file:
                raise ValueError("Local diagnostics require explicit SSH key and known-hosts paths.")
            data = run_checks(os.environ, args.ssh_key_file, args.known_hosts_file, True)
        else:
            if args.ssh_key_file or args.known_hosts_file:
                raise ValueError("File-based SSH credentials are only supported with --server-config.")
            with tempfile.TemporaryDirectory(prefix="erp-test-ssh-") as directory:
                os.chmod(directory, 0o700)
                key = Path(directory) / "key"
                hosts = Path(directory) / "known_hosts"
                for path, name in ((key, SSH_SECRETS[0]), (hosts, SSH_SECRETS[1])):
                    path.write_text(os.environ[name].replace("\r\n", "\n").rstrip("\n") + "\n", encoding="utf-8")
                    os.chmod(path, 0o600)
                data = run_checks(os.environ, key, hosts)
    except Exception:
        # Report no exception messages, environment dumps, response bodies, or command output.
        data["checks"].append({"name": "configuration_or_connection", "status": "fail"})
    finish_report(data)
    write_report(data, args.report, args.summary)
    return 0 if data["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
