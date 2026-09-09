"""Shared release validation and retention rules; no network or deletion here."""
from datetime import datetime, timezone, timedelta
import hashlib
import json
from pathlib import Path
import re
from urllib.parse import urlsplit

PACKAGES = {"admin": "cqz-cio/furniture-erp-admin", "backend": "cqz-cio/furniture-erp-backend"}
RELEASE = re.compile(r"cd-([0-9a-f]{40})-([1-9][0-9]*)-([1-9][0-9]*)\Z")
DIGEST = re.compile(r"sha256:[0-9a-f]{64}\Z")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def utcnow():
    return datetime.now(timezone.utc)


def timestamp(value):
    result = datetime.fromisoformat(value.replace("Z", "+00:00"))
    require(result.tzinfo is not None, "Timestamp must include timezone")
    return result


def fingerprint(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def write_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    temporary.replace(path)


def public_url(value):
    parsed = urlsplit(value)
    require(parsed.scheme in ("http", "https") and parsed.hostname and not parsed.username
            and not parsed.password and not parsed.query and not parsed.fragment,
            "Expected an HTTP(S) URL without credentials/query/fragment")
    require(not any(c.isspace() for c in value) and value == value.rstrip("/"),
            "URL must have no whitespace or trailing slash")
    return value


def validate_release(value):
    match = RELEASE.fullmatch(value.get("id", ""))
    require(value.get("schema") == 1 and match, "Invalid release format")
    require(value.get("commit") == match[1] and str(value.get("run_id")) == match[2]
            and str(value.get("run_attempt")) == match[3], "Release identity mismatch")
    require(re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", value.get("repository", "")), "Invalid repository")
    require(value.get("platform") == "linux/amd64", "Only the verified linux/amd64 platform is supported")
    timestamp(value["created_at"])
    require(type(value["database_version"]) is int and value["database_version"] > 0, "Invalid migration target")
    require(re.fullmatch(r"[0-9a-f]{64}", value["migrations_hash"]), "Invalid migration fingerprint")
    refs = image_refs(value)
    for package, digest in refs:
        require(package in PACKAGES.values() and DIGEST.fullmatch(digest), "Invalid image digest")
    require(value["images"]["backend"].startswith("ghcr.io/" + PACKAGES["backend"] + "@"), "Wrong backend package")
    for environment in ("test", "production"):
        require(value["images"]["admin"][environment].startswith("ghcr.io/" + PACKAGES["admin"] + "@"), "Wrong admin package")
        public_url(value["config"][environment]["api_base_url"])
        public_url(value["config"][environment]["storefront_url"])
    require(api_origin(value["config"]["test"]["api_base_url"]) != api_origin(value["config"]["production"]["api_base_url"]),
            "Test API must not be the production API")
    return value


def api_origin(value):
    parsed = urlsplit(public_url(value))
    return parsed.hostname.lower(), parsed.port or (443 if parsed.scheme == "https" else 80)


def image_refs(value):
    result = set()
    for ref in [value["images"]["backend"], *value["images"]["admin"].values()]:
        require(isinstance(ref, str) and ref.startswith("ghcr.io/") and ref.count("@") == 1, "Image must be pinned by digest")
        package, digest = ref[len("ghcr.io/"):].split("@")
        result.add((package, digest))
    return result


def environment_images(value, environment):
    require(environment in ("test", "production"), "Invalid environment")
    return {"erp-backend": value["images"]["backend"], "erp-admin": value["images"]["admin"][environment]}


def release_plan(releases, snapshots, pins=(), now=None, keep=5):
    """Protect both environments, then retain five additional ordinary releases."""
    now = now or utcnow()
    require(keep == 5, "This project's ordinary retention count is fixed at five")
    require(set(snapshots) == {"test", "production"}, "Both environment snapshots are required")
    by_id = {r["id"]: validate_release(r) for r in releases}
    require(len(by_id) == len(releases), "Duplicate release IDs")
    protected = set(pins)
    for environment, snapshot in snapshots.items():
        require(snapshot.get("environment") == environment and snapshot.get("verified") is True, "Unverified environment snapshot")
        age = now - timestamp(snapshot["checked_at"])
        require(timedelta(seconds=-30) <= age <= timedelta(minutes=5), "Stale snapshot")
        require(not snapshot.get("in_progress"), "An unfinished deployment blocks cleanup")
        require(snapshot.get("current"), "No registered current release; onboard this environment first")
        protected.update([snapshot["current"], *snapshot.get("rollback", []), *snapshot.get("pins", [])])
    require(protected <= by_id.keys(), "Protected version has no release manifest; stop cleanup")
    ordinary = sorted((r for r in releases if r["id"] not in protected),
                      key=lambda r: (timestamp(r["created_at"]), r["id"]), reverse=True)
    retained = protected | {r["id"] for r in ordinary[:keep]}
    retained |= {r["id"] for r in releases if now - timestamp(r["created_at"]) < timedelta(hours=24)}
    return {"protected": sorted(protected), "keep": sorted(retained),
            "retire": sorted(by_id.keys() - retained), "ordinary_limit": keep}


def manifest_children(manifest):
    media = manifest.get("mediaType", "")
    if media in ("application/vnd.oci.image.index.v1+json", "application/vnd.docker.distribution.manifest.list.v2+json"):
        result = {m["digest"] for m in manifest["manifests"]}
    elif media in ("application/vnd.oci.image.manifest.v1+json", "application/vnd.docker.distribution.manifest.v2+json"):
        result = set()
    else:
        raise ValueError("Unknown OCI media type; cleanup cannot prove dependencies")
    # OCI referrers can share their subject's lifecycle; retain them conservatively.
    if "subject" in manifest:
        result.add(manifest["subject"]["digest"])
    require(all(DIGEST.fullmatch(x) for x in result), "Invalid child digest")
    return result


def deletion_plan(releases, retired_ids, versions, manifests, pending=()):
    """Delete a release's OCI closure only when no retained/unknown root references it."""
    roots = {p: set() for p in PACKAGES.values()}
    preserved = {p: set() for p in PACKAGES.values()}
    allowed_tags = {p: {} for p in PACKAGES.values()}
    for release in releases:
        validate_release(release)
        for package, digest in image_refs(release):
            (roots if release["id"] in retired_ids else preserved)[package].add(digest)
            allowed_tags[package].setdefault(digest, set()).update({release["id"], release["id"] + "-test",
                release["id"] + "-production", release["commit"]})
    for entry in pending:
        require(entry["package"] in PACKAGES.values() and DIGEST.fullmatch(entry["digest"])
                and type(entry["version_id"]) is int, "Invalid saved deletion entry")
        matches = [v for v in versions[entry["package"]] if v["id"] == entry["version_id"]]
        if matches:
            require(matches[0]["name"] == entry["digest"], "Saved deletion identity changed")
            roots[entry["package"]].add(entry["digest"])
    result = []
    for package in PACKAGES.values():
        entries = {v["name"]: v for v in versions[package]}
        require(len(entries) == len(versions[package]), "Duplicate package digest")
        graph = {digest: manifest_children(manifests[(package, digest)]) for digest in entries}
        children = set().union(*graph.values()) if graph else set()
        require(children <= entries.keys(), "Incomplete registry inventory; stop cleanup")

        def closure(start):
            visited, pending = set(), list(start)
            while pending:
                digest = pending.pop()
                if digest not in visited:
                    require(digest in graph, "Referenced digest missing from inventory")
                    visited.add(digest)
                    pending.extend(graph[digest] - visited)
            return visited

        keep_roots = set(preserved[package])
        for digest, entry in entries.items():
            tags = set(entry["metadata"]["container"]["tags"])
            unknown_tags = tags - allowed_tags[package].get(digest, set())
            if unknown_tags or (tags and digest not in roots[package]) or (not tags and digest not in children and digest not in roots[package]):
                keep_roots.add(digest)
        # Previously retired manifests may already be partly deleted; only existing roots remain candidates.
        removable = closure(roots[package] & entries.keys()) - closure(keep_roots)
        # Parents first: if a deletion fails, do not delete children still referenced by that parent.
        while removable:
            ready = sorted(d for d in removable if not any(d in graph[parent] for parent in removable))
            require(ready, "OCI dependency cycle")
            for digest in ready:
                result.append({"package": package, "digest": digest, "version_id": entries[digest]["id"]})
                removable.remove(digest)
    return result
