"""Plan/apply latest-five GHCR retention under the local/publication workflow locks."""
import argparse
import base64
import hashlib
import json
import os
import urllib.parse
import urllib.request

from common import PACKAGES, RELEASE, DIGEST, deletion_plan, fingerprint, image_refs, latest_release_plan, manifest_children, require, utcnow, write_json
from github_release import GitHub


class Registry:
    def __init__(self):
        self.tokens = {}

    def manifest(self, package, digest):
        require(package in PACKAGES.values() and DIGEST.fullmatch(digest), "Unexpected registry reference")
        if package not in self.tokens:
            auth = base64.b64encode((os.environ["GITHUB_ACTOR"] + ":" + os.environ["GH_TOKEN"]).encode()).decode()
            url = "https://ghcr.io/token?" + urllib.parse.urlencode({"service": "ghcr.io", "scope": "repository:" + package + ":pull"})
            req = urllib.request.Request(url, headers={"Authorization": "Basic " + auth})
            with urllib.request.urlopen(req, timeout=20) as response:
                self.tokens[package] = json.load(response)["token"]
        req = urllib.request.Request("https://ghcr.io/v2/" + package + "/manifests/" + digest, headers={
            "Authorization": "Bearer " + self.tokens[package], "Accept": ",".join((
                "application/vnd.oci.image.index.v1+json", "application/vnd.oci.image.manifest.v1+json",
                "application/vnd.docker.distribution.manifest.list.v2+json", "application/vnd.docker.distribution.manifest.v2+json"))})
        with urllib.request.urlopen(req, timeout=20) as response:
            raw = response.read(8 * 1024 * 1024 + 1)
            require(len(raw) <= 8 * 1024 * 1024 and "sha256:" + hashlib.sha256(raw).hexdigest() == digest, "Registry digest verification failed")
            return json.loads(raw)


def collection(github, package):
    owner, name = package.split("/")
    account = github.request("/users/" + owner)
    prefix = "/orgs/" if account["type"] == "Organization" else "/users/"
    return prefix + owner + "/packages/container/" + urllib.parse.quote(name, safe="") + "/versions"


def require_cleanup_context():
    require(os.environ.get('GITHUB_ACTIONS') == 'true' and os.environ.get('GITHUB_REF') == 'refs/heads/main'
        and os.environ.get('GITHUB_REPOSITORY') == 'cqz-cio/furniture'
        and os.environ.get('GITHUB_WORKFLOW') == 'ERP image retention' and os.environ.get('GITHUB_JOB') == 'clean'
        and os.environ.get('RUNNER_ENVIRONMENT') == 'self-hosted'
        and os.environ.get('ERP_IMAGE_CLEANUP_ENABLED') == 'true',
        'Actual cleanup requires the trusted retention workflow and both concurrency locks')


def clean(github, registry, apply=False):
    catalog, records, retired, pending = [], {}, set(), []
    for record in github.pages(github.repo("/releases")):
        if record["draft"] or not RELEASE.fullmatch(record["tag_name"]):
            continue
        print("Inspect release: " + record["tag_name"], flush=True)
        record, manifest = github.release(record["tag_name"], allow_retired=True)
        catalog.append(manifest)
        records[manifest["id"]] = record
        tombstones = [a for a in record["assets"] if a["name"] == "retirement.json"]
        require(len(tombstones) <= 1, "Ambiguous retirement state")
        if tombstones:
            retired.add(manifest["id"])
            marker = json.loads(github.request(github.repo("/releases/assets/" + str(tombstones[0]["id"])), raw=True))
            require(marker["schema"] == 1 and marker["plan_hash"] == fingerprint(marker["delete"]), "Invalid saved retirement plan")
            pending.extend(marker["delete"])
    policy = latest_release_plan([r for r in catalog if r['id'] not in retired])
    retired.update(policy["retire"])
    versions, manifests, endpoints = {}, {}, {}
    for package in PACKAGES.values():
        endpoints[package] = collection(github, package)
        print("Inspect package: " + package, flush=True)
        versions[package] = github.pages(endpoints[package])
        for version in versions[package]:
            print("Inspect manifest: " + version["name"], flush=True)
            manifests[(package, version["name"])] = registry.manifest(package, version["name"])
    actions = deletion_plan(catalog, retired, versions, manifests, pending=pending, strict=True)
    unmanaged = []
    for package in PACKAGES.values():
        present = {version['name'] for version in versions[package]}
        managed = {digest for release in catalog for owner, digest in image_refs(release) if owner == package}
        managed.update(entry['digest'] for entry in pending if entry['package'] == package)
        queue = list(managed & present)
        visited = set()
        while queue:
            digest = queue.pop()
            if digest not in visited:
                visited.add(digest)
                queue.extend(manifest_children(manifests[(package, digest)]) - visited)
        unmanaged.extend({'package':package,'digest':digest} for digest in sorted(present - visited))
    report = {**policy, "mode": "apply" if apply else "dry-run", "delete": actions,
              "unmanaged_preserved": unmanaged}
    # Persist the reviewable plan before the first mutation, including on a partial failure.
    write_json("retention-plan.json", report)
    if apply:
        require_cleanup_context()
        marker = {"schema": 1, "created_at": utcnow().isoformat(), "plan_hash": fingerprint(actions), "delete": actions}
        for release_id in policy["retire"]:
            require_cleanup_context()
            github.add_asset(records[release_id], "retirement.json", marker)
        for action in actions:
            require_cleanup_context()
            path = endpoints[action["package"]] + "/" + str(action["version_id"])
            current = github.request(path)
            require(current["name"] == action["digest"], "Version identity changed; stop deletion")
            # Unexpected mutations outside the shared workflow lock stop deletion.
            original = next(v for v in versions[action["package"]] if v["id"] == action["version_id"])
            require(current["metadata"]["container"]["tags"] == original["metadata"]["container"]["tags"], "Tags changed during cleanup")
            github.request(path, "DELETE")
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    require(os.environ.get("GITHUB_REF") == "refs/heads/main", "Cleanup must use main")
    if args.apply:
        require_cleanup_context()
    result = clean(GitHub(), Registry(), args.apply)
    print(json.dumps(result, indent=2))
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as stream:
            stream.write("### ERP image retention\n\n```json\n" + json.dumps(result, indent=2) + "\n```\n")
