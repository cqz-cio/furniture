"""Plan/apply GHCR retention; deletion is opt-in and requires both server leases."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import urllib.parse
import urllib.request

from common import PACKAGES, RELEASE, DIGEST, deletion_plan, fingerprint, release_plan, require, timestamp, utcnow, write_json
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


def valid_leases(snapshots):
    expected = "gc-" + os.environ["GITHUB_RUN_ID"] + "-" + os.environ["GITHUB_RUN_ATTEMPT"]
    for snapshot in snapshots.values():
        require(snapshot.get("lease_id") == expected and (timestamp(snapshot["lease_expires"]) - utcnow()).total_seconds() > 60,
                "Server cleanup lease is missing/near expiry; no more deletions")


def clean(github, registry, snapshots, apply=False):
    catalog, records, retired, pending = [], {}, set(), []
    for record in github.pages(github.repo("/releases")):
        if record["draft"] or not RELEASE.fullmatch(record["tag_name"]):
            continue
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
    policy = release_plan([r for r in catalog if r["id"] not in retired], snapshots,
                          pins=json.loads(os.environ.get("ERP_RETAIN_RELEASES_JSON") or "[]"))
    retired.update(policy["retire"])
    versions, manifests, endpoints = {}, {}, {}
    for package in PACKAGES.values():
        endpoints[package] = collection(github, package)
        versions[package] = github.pages(endpoints[package])
        for version in versions[package]:
            manifests[(package, version["name"])] = registry.manifest(package, version["name"])
    actions = deletion_plan(catalog, retired, versions, manifests, pending=pending)
    report = {**policy, "mode": "apply" if apply else "dry-run", "delete": actions}
    # Persist the reviewable plan before the first mutation, including on a partial failure.
    write_json("retention-plan.json", report)
    if apply:
        valid_leases(snapshots)
        marker = {"schema": 1, "created_at": utcnow().isoformat(), "plan_hash": fingerprint(actions), "delete": actions}
        for release_id in policy["retire"]:
            valid_leases(snapshots)
            github.add_asset(records[release_id], "retirement.json", marker)
        for action in actions:
            valid_leases(snapshots)
            path = endpoints[action["package"]] + "/" + str(action["version_id"])
            current = github.request(path)
            require(current["name"] == action["digest"], "Version identity changed; stop deletion")
            # A new manual tag is a pin even if it appeared after inventory collection.
            original = next(v for v in versions[action["package"]] if v["id"] == action["version_id"])
            require(current["metadata"]["container"]["tags"] == original["metadata"]["container"]["tags"], "Tags changed during cleanup")
            github.request(path, "DELETE")
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--snapshots", required=True)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    require(os.environ.get("GITHUB_REF") == "refs/heads/main", "Cleanup must use main")
    require(not args.apply or os.environ.get("ERP_IMAGE_CLEANUP_ENABLED") == "true", "Real image deletion has not been enabled")
    states = {e: json.loads((Path(args.snapshots) / ("snapshot-" + e + ".json")).read_text()) for e in ("test", "production")}
    result = clean(GitHub(), Registry(), states, args.apply)
    print(json.dumps(result, indent=2))
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as stream:
            stream.write("### ERP image retention\n\n```json\n" + json.dumps(result, indent=2) + "\n```\n")
