"""GitHub release manifests and CI provenance, using only the standard library."""
import argparse
import json
import os
from pathlib import Path
import re
import urllib.error
import urllib.parse
import urllib.request

from common import PACKAGES, RELEASE, fingerprint, require, utcnow, validate_release, write_json


class SafeRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        target = urllib.parse.urlsplit(newurl)
        require(target.scheme == "https" and (target.hostname in ("api.github.com", "uploads.github.com")
            or target.hostname.endswith(".githubusercontent.com")), "Unexpected GitHub asset redirect")
        redirected = super().redirect_request(request, fp, code, msg, headers, newurl)
        if redirected is not None and target.hostname != urllib.parse.urlsplit(request.full_url).hostname:
            redirected.remove_header("Authorization")
        return redirected


class GitHub:
    def __init__(self, repository=None, token=None):
        self.repository = repository or os.environ["GITHUB_REPOSITORY"]
        require(re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", self.repository), "Invalid GitHub repository")
        self.token = token or os.environ["GH_TOKEN"]
        self.opener = urllib.request.build_opener(SafeRedirect())

    def request(self, path, method="GET", body=None, raw=False):
        url = path if path.startswith("https://uploads.github.com/") else "https://api.github.com" + path
        require(urllib.parse.urlsplit(url).hostname in ("api.github.com", "uploads.github.com"), "Unexpected API host")
        data = None if body is None else json.dumps(body).encode()
        req = urllib.request.Request(url, data=data, method=method, headers={
            "Authorization": "Bearer " + self.token, "Accept": "application/octet-stream" if raw else "application/vnd.github+json",
            "Content-Type": "application/json", "X-GitHub-Api-Version": "2022-11-28", "User-Agent": "oakved-erp-cd"})
        try:
            with self.opener.open(req, timeout=20) as response:
                content = response.read(8 * 1024 * 1024 + 1)
                require(len(content) <= 8 * 1024 * 1024, "API response too large")
                return content if raw else (json.loads(content) if content else None)
        except urllib.error.HTTPError as error:
            # Do not echo response bodies, credentials, or signed download URLs.
            raise RuntimeError(f"GitHub {method} failed: HTTP {error.code}") from None

    def pages(self, path):
        require("?" not in path, "Pass an unparameterized API collection")
        result = []
        for page in range(1, 101):
            batch = self.request(path + f"?per_page=100&page={page}")
            require(isinstance(batch, list), "Invalid paginated response")
            result.extend(batch)
            if len(batch) < 100:
                return result
        raise ValueError("API inventory exceeds limit; refusing incomplete pagination")

    def repo(self, suffix):
        return "/repos/" + self.repository + suffix

    def release(self, release_id, allow_retired=False):
        require(RELEASE.fullmatch(release_id), "Invalid release ID")
        record = self.request(self.repo("/releases/tags/" + release_id))
        require(not record["draft"] and record["tag_name"] == release_id, "Incomplete release record")
        names = [a["name"] for a in record["assets"]]
        require(allow_retired or "retirement.json" not in names, "This release is retired")
        assets = [a for a in record["assets"] if a["name"] == "release.json"]
        require(len(assets) == 1, "Release must contain one complete manifest")
        manifest = validate_release(json.loads(self.request(self.repo("/releases/assets/" + str(assets[0]["id"])), raw=True)))
        require(manifest["id"] == release_id and manifest["repository"] == self.repository, "Manifest origin mismatch")
        return record, manifest

    def verify_ci(self, manifest):
        validate_release(manifest)
        source = manifest.get('source_test', manifest)
        provenance = source["ci"] if source.get("schema") == 2 else source
        run = self.request(self.repo(f"/actions/runs/{provenance['run_id']}/attempts/{provenance['run_attempt']}"))
        require(run["head_sha"] == manifest["commit"] and run["head_branch"] == "main"
            and run["head_repository"]["full_name"] == self.repository and run["conclusion"] == "success"
            and run["event"] in ("push", "workflow_dispatch")
            and run["path"].split("@")[0] == ".github/workflows/database-and-backend-ci.yml",
            "Release was not produced by a successful trusted main CI attempt")
        if 'source_test' in manifest:
            from local_ci import verify_upstream
            verify_upstream(self, provenance['run_id'], provenance['run_attempt'], manifest['commit'])
            self.verify_local_build(source)

    def verify_local_build(self, manifest):
        require(manifest.get('schema') == 2, 'Expected a local build manifest')
        run = self.request(self.repo(f"/actions/runs/{manifest['run_id']}/attempts/{manifest['run_attempt']}"))
        require(run['head_sha'] == manifest['commit'] and run['head_branch'] == 'main'
            and run['head_repository']['full_name'] == self.repository and run['conclusion'] == 'success'
            and run['event'] == 'workflow_run'
            and run['path'].split('@')[0] == '.github/workflows/erp-local-ci.yml',
            'Local image build has not completed successfully in the trusted workflow')

    def add_asset(self, record, name, value):
        require(name in ("release.json", "retirement.json"), "Unexpected asset name")
        return self.request(f"https://uploads.github.com/repos/{self.repository}/releases/{record['id']}/assets?name={name}", "POST", value)

    def publish(self, manifest):
        validate_release(manifest)
        require(manifest["schema"] == 1, "Local build manifests remain in the local release cache")
        record = self.request(self.repo("/releases"), "POST", {"tag_name": manifest["id"], "target_commitish": manifest["commit"],
            "name": manifest["id"], "draft": True, "prerelease": True, "make_latest": "false",
            "body": "ERP build manifest. Deployment requires successful CI and environment checks. Images may later be retired by policy."})
        self.add_asset(record, "release.json", manifest)
        self.request(self.repo("/releases/" + str(record["id"])), "PATCH", {"draft": False, "prerelease": True, "make_latest": "false"})

    def test_passed(self, manifest):
        validate_release(manifest)
        manifest = manifest.get('source_test', manifest)
        for deployment in self.pages(self.repo("/deployments")):
            payload = deployment.get("payload") or {}
            if isinstance(payload, str):
                payload = json.loads(payload)
            if (deployment["environment"] == "test" and deployment["task"] == "erp-cd"
                    and payload.get("release_hash") == fingerprint(manifest)
                    and payload.get("release_id") == manifest["id"]):
                states = self.pages(self.repo(f"/deployments/{deployment['id']}/statuses"))
                # Deployments are returned newest first. A later failure supersedes earlier test success.
                return bool(states and states[0]["state"] in ("success", "inactive") and any(s["state"] == "success" for s in states))
        return False

    def deployment(self, manifest, environment):
        return self.request(self.repo("/deployments"), "POST", {"ref": manifest["commit"], "environment": environment,
            "task": "erp-cd", "auto_merge": False, "required_contexts": [], "production_environment": environment == "production",
            "payload": {"release_id": manifest["id"], "release_hash": fingerprint(manifest)}})

    def deployment_status(self, deployment, status):
        body = {"state": status, "auto_inactive": status == "success", "description": "ERP deployment: " + status}
        if os.environ.get("GITHUB_RUN_ID"):
            body["log_url"] = f"https://github.com/{self.repository}/actions/runs/{os.environ['GITHUB_RUN_ID']}"
        self.request(self.repo(f"/deployments/{deployment['id']}/statuses"), "POST", body)


def build_manifest(directory, env):
    files = sorted((Path(directory) / "yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations").glob("V*__*.sql"))
    require(files, "No migrations found")
    versions = [int(re.match(r"V(\d+)__", p.name)[1]) for p in files]
    import hashlib
    checksum = hashlib.sha256()
    for path in files:
        checksum.update(path.name.encode() + b"\0" + path.read_bytes().replace(b"\r\n", b"\n"))
    manifest = {"schema": 1, "id": f"cd-{env['GITHUB_SHA']}-{env['GITHUB_RUN_ID']}-{env['GITHUB_RUN_ATTEMPT']}",
        "commit": env["GITHUB_SHA"], "run_id": int(env["GITHUB_RUN_ID"]), "run_attempt": int(env["GITHUB_RUN_ATTEMPT"]),
        "repository": env["GITHUB_REPOSITORY"], "created_at": utcnow().isoformat(), "platform": "linux/amd64",
        "database_version": max(versions), "migrations_hash": checksum.hexdigest(),
        "images": {"backend": "ghcr.io/" + PACKAGES["backend"] + "@" + env["BACKEND_DIGEST"], "admin": {
            "test": "ghcr.io/" + PACKAGES["admin"] + "@" + env["TEST_ADMIN_DIGEST"],
            "production": "ghcr.io/" + PACKAGES["admin"] + "@" + env["PRODUCTION_ADMIN_DIGEST"]}},
        "config": {"test": {"api_base_url": env["ERP_TEST_API_BASE_URL"], "storefront_url": env["ERP_TEST_STOREFRONT_URL"]},
                   "production": {"api_base_url": "https://api.vanzhome.com", "storefront_url": "https://www.vanzhome.com"}}}
    return validate_release(manifest)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--publish", action="store_true")
    parser.add_argument("--output", default="release.json")
    args = parser.parse_args()
    value = build_manifest(Path(__file__).resolve().parents[2], os.environ)
    write_json(args.output, value)
    if args.publish:
        require(os.environ.get("GITHUB_REF") == "refs/heads/main", "Only main may register releases")
        GitHub().publish(value)
    print(value["id"])
