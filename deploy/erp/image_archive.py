"""Build an OCI archive on the Actions runner, preserving GHCR root digests."""
import hashlib
import io
import json
from pathlib import Path
import shutil
import tarfile
import time
import urllib.error
import urllib.parse
import urllib.request

from common import DIGEST, environment_images, require, validate_release

ACCEPT = ", ".join(("application/vnd.oci.image.index.v1+json", "application/vnd.oci.image.manifest.v1+json",
                    "application/vnd.docker.distribution.manifest.list.v2+json", "application/vnd.docker.distribution.manifest.v2+json"))


def digest(data):
    return "sha256:" + hashlib.sha256(data).hexdigest()


class Registry:
    def __init__(self):
        self.tokens = {}

    def headers(self, package):
        if package not in self.tokens:
            with urllib.request.urlopen("https://ghcr.io/token?" + urllib.parse.urlencode({"service": "ghcr.io", "scope": "repository:" + package + ":pull"}), timeout=15) as response:
                self.tokens[package] = json.load(response)["token"]
        return {"Authorization": "Bearer " + self.tokens[package], "Accept": ACCEPT}

    def manifest(self, package, reference):
        request = urllib.request.Request("https://ghcr.io/v2/" + package + "/manifests/" + reference, headers=self.headers(package))
        with urllib.request.urlopen(request, timeout=15) as response:
            data = response.read(2 * 1024**2 + 1)
        require(len(data) <= 2 * 1024**2 and digest(data) == reference, "Registry manifest digest mismatch")
        return data

    def blob(self, package, reference):
        # Bearer credentials belong to ghcr.io; do not forward them to CDN redirects.
        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self, *args):
                return None
        request = urllib.request.Request("https://ghcr.io/v2/" + package + "/blobs/" + reference, headers=self.headers(package))
        try:
            return urllib.request.build_opener(NoRedirect()).open(request, timeout=15)
        except urllib.error.HTTPError as error:
            require(error.code in (301, 302, 303, 307, 308), "GHCR refused a blob download")
            target = error.headers["Location"]
            error.close()
            url = urllib.parse.urlsplit(target)
            require(url.scheme == "https" and url.hostname and url.hostname.endswith(".githubusercontent.com")
                    and not url.username and not url.password, "Unexpected GHCR blob redirect")
            return urllib.request.urlopen(target, timeout=15)


def build_archive(release, environment, directory, registry=None):
    validate_release(release)
    registry = registry or Registry()
    directory = Path(directory)
    directory.mkdir(mode=0o700)
    blobs = directory / "blobs" / "sha256"
    blobs.mkdir(parents=True)
    started = notified = time.monotonic()
    objects, roots, pending = {}, [], {}
    transferred = 0

    def check():
        nonlocal notified
        now = time.monotonic()
        require(now - started < 300, "Actions image archive exceeded 300 seconds")
        if now - notified >= 10:
            print(json.dumps({"stage": "runner-image-download", "downloaded_bytes": transferred, "elapsed_seconds": round(now-started)}), flush=True)
            notified = now

    def visit(package, ref, depth=0):
        check()
        require(DIGEST.fullmatch(ref) and depth < 8 and len(objects) < 128, "Invalid OCI manifest graph")
        if ref in objects:
            return objects[ref]
        raw = registry.manifest(package, ref)
        require(digest(raw) == ref, "Manifest checksum differs from release")
        value = json.loads(raw)
        descriptor = {"digest": ref, "size": len(raw), "mediaType": value["mediaType"]}
        objects[ref] = descriptor
        (blobs / ref[7:]).write_bytes(raw)
        if "manifests" in value:
            for child in value["manifests"]:
                result = visit(package, child["digest"], depth + 1)
                require(result["size"] == child["size"], "OCI child size mismatch")
        else:
            require(value.get("schemaVersion") == 2 and "config" in value and "layers" in value, "Invalid OCI image manifest")
            for child in [value["config"], *value["layers"]]:
                require(DIGEST.fullmatch(child["digest"]) and type(child["size"]) is int and 0 <= child["size"] <= 2 * 1024**3
                        and not child.get("urls"), "Invalid OCI blob descriptor")
                if child["digest"] in pending:
                    require(pending[child["digest"]][1]["size"] == child["size"], "Conflicting blob sizes")
                pending[child["digest"]] = (package, child)
        return descriptor

    print(json.dumps({"stage": "runner-image-download", "timeout_seconds": 300}), flush=True)
    for reference in environment_images(release, environment).values():
        package, ref = reference.removeprefix("ghcr.io/").split("@")
        roots.append({**visit(package, ref), "annotations": {"io.containerd.image.name": reference}})
    total = sum(child["size"] for _, child in pending.values())
    require(total < 3 * 1024**3 and shutil.disk_usage(directory).free > total * 3 + 1024**3, "Insufficient Actions space for the image archive")
    for ref, (package, child) in pending.items():
        check()
        size, checksum = 0, hashlib.sha256()
        with registry.blob(package, ref) as response, (blobs / ref[7:]).open("xb") as output:
            while chunk := response.read1(1024 * 1024):
                size += len(chunk)
                transferred += len(chunk)
                require(size <= child["size"], "Downloaded blob exceeds declared size")
                checksum.update(chunk)
                output.write(chunk)
                check()
        require(size == child["size"] and "sha256:" + checksum.hexdigest() == ref, "Downloaded blob checksum mismatch")
    archive = directory / "images.oci.tar"
    with tarfile.open(archive, "w") as output:
        for name, value in (("oci-layout", {"imageLayoutVersion": "1.0.0"}), ("index.json", {"schemaVersion": 2, "manifests": roots})):
            raw = json.dumps(value).encode()
            entry = tarfile.TarInfo(name)
            entry.size, entry.mode = len(raw), 0o600
            output.addfile(entry, io.BytesIO(raw))
        for path in sorted(blobs.iterdir()):
            check()
            output.add(path, arcname="blobs/sha256/" + path.name, recursive=False)
    with archive.open("rb") as stream:
        checksum = hashlib.file_digest(stream, "sha256").hexdigest()
    print(json.dumps({"stage": "runner-image-archive-ready", "bytes": archive.stat().st_size}), flush=True)
    return archive, {"sha256": checksum, "bytes": archive.stat().st_size, "release": release, "environment": environment}
