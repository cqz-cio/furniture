"""Receive and import a verified OCI archive into the test Docker image cache."""
import hashlib
import json
import os
from pathlib import Path
import re
import select
import signal
import shutil
import subprocess
import sys
import tarfile
import tempfile
import time

from common import environment_images, fingerprint, require, validate_release
from bootstrap_policy import PROFILE
from bootstrap_io import Commands, emit


def validate_archive(path, references):
    """Never extract a tar member into the filesystem; verify named root digests."""
    sizes, manifests = {}, {}
    with tarfile.open(path, "r:") as archive:
        for member in archive:
            require(len(sizes) < 1024 and member.isfile() and member.name not in sizes, "Invalid OCI archive member")
            require(member.name in ("oci-layout", "index.json") or re.fullmatch(r"blobs/sha256/[0-9a-f]{64}", member.name), "Unexpected OCI archive path")
            sizes[member.name] = member.size
            checksum, raw = hashlib.sha256(), bytearray()
            with archive.extractfile(member) as stream:
                while chunk := stream.read(1024 * 1024):
                    checksum.update(chunk)
                    if member.size <= 2 * 1024**2:
                        raw.extend(chunk)
            if member.name.startswith("blobs/"):
                require(checksum.hexdigest() == member.name.rsplit("/", 1)[1], "OCI blob content was corrupted")
            if raw:
                try:
                    manifests[member.name] = json.loads(raw)
                except (ValueError, UnicodeDecodeError):
                    pass
    require(manifests.get("oci-layout") == {"imageLayoutVersion": "1.0.0"}, "Invalid OCI layout")
    roots = manifests.get("index.json", {}).get("manifests", [])
    require(len(roots) == len(references) and {v.get("annotations", {}).get("io.containerd.image.name") for v in roots} == set(references), "Archive names differ from release")

    def walk(descriptor, depth=0):
        ref = descriptor["digest"]
        require(re.fullmatch(r"sha256:[0-9a-f]{64}", ref) and depth < 8, "Invalid archive descriptor")
        name = "blobs/sha256/" + ref[7:]
        require(sizes.get(name) == descriptor["size"], "Archive has missing or truncated content")
        value = manifests.get(name, {})
        if not isinstance(value, dict):
            return
        if "manifests" in value:
            for child in value["manifests"]:
                walk(child, depth + 1)
        elif "layers" in value:
            for child in [value["config"], *value["layers"]]:
                walk(child, depth + 1)
    for root in roots:
        require(root["annotations"]["io.containerd.image.name"].endswith("@" + root["digest"]), "Archive root digest differs from release")
        walk(root)


def receive(stream, path, length, seconds=600):
    started = changed = notified = time.monotonic()
    received, checksum = 0, hashlib.sha256()
    with path.open("xb") as output:
        while received < length:
            now = time.monotonic()
            require(now - started < seconds and now - changed < 60, "SSH image transfer timed out")
            ready, _, _ = select.select([stream], [], [], 1)
            if ready:
                chunk = os.read(stream.fileno(), min(1024 * 1024, length - received))
                require(chunk, "SSH image archive was truncated")
                output.write(chunk)
                checksum.update(chunk)
                received += len(chunk)
                changed = now
            if now - notified >= 10:
                emit("ssh-image-transfer", received_bytes=received, total_bytes=length, elapsed_seconds=round(now-started))
                notified = now
    return checksum.hexdigest()


def cache_ready(release, commands):
    for reference in environment_images(release, "test").values():
        try:
            info = json.loads(commands.run(["docker", "image", "inspect", reference], "verify-relayed-image"))[0]
        except ValueError:
            return False
        if not (reference in info.get("RepoDigests", []) and info["Architecture"] == "amd64" and info["Os"] == "linux"
                and info["Config"].get("Labels", {}).get("org.opencontainers.image.revision") == release["commit"]):
            return False
    return True


def main(request):
    def interrupted(signum, frame):
        # Give Commands.run and TemporaryDirectory time to cancel the owned
        # Docker client and remove this attempt's tar before timeout sends KILL.
        signal.signal(signal.SIGTERM, signal.SIG_IGN)
        signal.signal(signal.SIGHUP, signal.SIG_IGN)
        raise RuntimeError("Image relay interrupted; its temporary archive is being removed")
    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGHUP, interrupted)
    release = validate_release(request["release"])
    require(request["environment"] == "test" and release["config"]["test"]["api_base_url"] == "http://" + PROFILE["host"], "Image relay is limited to the verified test environment")
    require(os.geteuid() == 0 and os.environ.get("SUDO_USER") == PROFILE["user"], "Use the verified test deploy account")
    key = subprocess.run(["ssh-keygen", "-lf", "/etc/ssh/ssh_host_ed25519_key.pub"], capture_output=True, text=True, check=True, timeout=5).stdout
    require("SHA256:" + PROFILE["host_key"] in key, "Relay destination host key differs from the verified server")
    root = Path(PROFILE["root"])
    require(root.resolve() == root, "Unexpected test deployment directory")
    os.umask(0o077)
    logs = root / "image-relay-logs" / str(time.time_ns())
    logs.mkdir(mode=0o700, parents=True)
    commands = Commands(logs)
    operation = request.get("operation", "receive")
    require(operation in ("probe", "receive"), "Unknown image relay operation")
    if operation == "probe":
        status = "images-present" if cache_ready(release, commands) else "images-missing"
        print("ERP_CD_RESULT=" + json.dumps({"status": status, "release_hash": fingerprint(release)}), flush=True)
        return
    length = request["bytes"]
    require(type(length) is int and 0 < length < 3 * 1024**3 and re.fullmatch(r"[0-9a-f]{64}", request["sha256"]), "Invalid archive size or hash")
    driver = commands.run(["docker", "info", "--format", "{{json .DriverStatus}}"], "relay-docker-storage")
    require("io.containerd.snapshotter.v1" in driver, "OCI relay requires the verified containerd image store")
    require(shutil.disk_usage("/var/tmp").free > PROFILE["reserve_bytes"] + length * 4, "Insufficient space for image relay and Docker import")
    with tempfile.TemporaryDirectory(prefix="oakved-image-relay-", dir="/var/tmp") as directory:
        path = Path(directory) / "images.oci.tar"
        emit("ssh-image-transfer", total_bytes=length, timeout_seconds=600)
        require(receive(sys.stdin.buffer, path, length) == request["sha256"], "SSH image archive checksum mismatch")
        validate_archive(path, environment_images(release, "test").values())
        with path.open("rb") as source:
            commands.run(["docker", "image", "load", "--platform", "linux/amd64"], "load-relayed-images", seconds=300, input_file=source)
        require(cache_ready(release, commands), "Imported image identity differs from the CI release")
    print("ERP_CD_RESULT=" + json.dumps({"status": "images-ready", "release_hash": fingerprint(release)}), flush=True)
