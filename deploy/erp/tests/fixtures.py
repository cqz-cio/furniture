from datetime import datetime, timezone, timedelta
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from common import PACKAGES

NOW = datetime(2026, 9, 9, 8, tzinfo=timezone.utc)
IMAGE = "application/vnd.oci.image.manifest.v1+json"
INDEX = "application/vnd.oci.image.index.v1+json"


def digest(number):
    return "sha256:" + f"{number:064x}"


def release(number, schema=49):
    sha = f"{number:040x}"
    return {"schema": 1, "id": f"cd-{sha}-{number}-1", "commit": sha, "run_id": number, "run_attempt": 1,
        "repository": "cqz-cio/erp", "created_at": (NOW - timedelta(days=20-number)).isoformat(), "platform": "linux/amd64",
        "database_version": schema, "migrations_hash": "a" * 64,
        "images": {"backend": "ghcr.io/" + PACKAGES["backend"] + "@" + digest(number * 10),
            "admin": {"test": "ghcr.io/" + PACKAGES["admin"] + "@" + digest(number*10 + 1),
                      "production": "ghcr.io/" + PACKAGES["admin"] + "@" + digest(number*10 + 2)}},
        "config": {"test": {"api_base_url": "https://api-test.example.com", "storefront_url": "https://test.example.com"},
            "production": {"api_base_url": "https://api.vanzhome.com", "storefront_url": "https://www.vanzhome.com"}}}


def snapshots(current=10, rollback=(9, 8)):
    return {e: {"environment": e, "verified": True, "checked_at": NOW.isoformat(), "current": release(current)["id"],
        "rollback": [release(i)["id"] for i in rollback], "pins": [], "in_progress": None} for e in ("test", "production")}


def inventory(releases):
    versions = {p: [] for p in PACKAGES.values()}
    manifests = {}
    for item in releases:
        for key, ref in [("backend", item["images"]["backend"]), ("test", item["images"]["admin"]["test"]),
                         ("production", item["images"]["admin"]["production"])]:
            package, sha = ref.removeprefix("ghcr.io/").split("@")
            tag = item["id"] + ("" if key == "backend" else "-" + key)
            versions[package].append({"id": int(sha[7:], 16), "name": sha, "metadata": {"container": {"tags": [tag]}}})
            manifests[(package, sha)] = {"mediaType": IMAGE}
    return versions, manifests
