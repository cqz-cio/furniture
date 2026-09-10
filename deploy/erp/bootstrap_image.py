"""Inspect pinned GHCR images and extract their migration-only Java runtime."""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import urllib.request
import zipfile

from common import environment_images, require, write_json
from bootstrap_policy import PROFILE, sha256
from image_pull import pull_image


def capacity(paths, extra=0):
    for path in paths:
        path = Path(path)
        while not path.exists():
            path = path.parent
        usage = shutil.disk_usage(path)
        require(usage.free >= PROFILE["reserve_bytes"] + extra and usage.used / usage.total < 0.9,
                "Insufficient disk reserve for bootstrap; no unrelated files will be deleted")


def registry_size(reference):
    name, digest = reference.removeprefix("ghcr.io/").split("@")
    with urllib.request.urlopen("https://ghcr.io/token?service=ghcr.io&scope=repository:" + name + ":pull", timeout=15) as response:
        token = json.load(response)["token"]
    def fetch(value):
        request = urllib.request.Request("https://ghcr.io/v2/" + name + "/manifests/" + value, headers={
            "Authorization": "Bearer " + token,
            "Accept": "application/vnd.oci.image.index.v1+json, application/vnd.oci.image.manifest.v1+json, application/vnd.docker.distribution.manifest.v2+json"})
        with urllib.request.urlopen(request, timeout=15) as response:
            data = response.read(2 * 1024**2)
        require("sha256:" + sha256(data) == value, "Registry manifest digest mismatch")
        return json.loads(data)
    manifest_digest = digest
    manifest = fetch(digest)
    if "manifests" in manifest:
        children = [v for v in manifest["manifests"] if v.get("platform", {}).get("os") == "linux"
                    and v.get("platform", {}).get("architecture") == "amd64"]
        require(len(children) == 1, "Ambiguous image platform")
        manifest_digest = children[0]["digest"]
        manifest = fetch(manifest_digest)
    return {"compressed_bytes": sum(v["size"] for v in manifest["layers"]), "config_digest": manifest["config"]["digest"],
            "root_digest": digest, "manifest_digest": manifest_digest}


def verify_image(info, reference, metadata, commit):
    # Containerd reports the root manifest/index as Id; legacy stores report config.
    require(info["Id"] in {metadata[key] for key in ("config_digest", "root_digest", "manifest_digest")}
            and reference in info.get("RepoDigests", []) and info["Architecture"] == "amd64" and info["Os"] == "linux",
            "Image identity or platform mismatch")
    require(info.get("Config", {}).get("Labels", {}).get("org.opencontainers.image.revision") == commit, "Image commit differs from the CI release")


def extract_migrations(jar, runtime, release, layered=False, space_check=None):
    """Accept both old fat JARs and the flat JAR produced by Boot tools mode."""
    prefix = "" if layered else "BOOT-INF/classes/"
    with zipfile.ZipFile(jar) as archive:
        require(len(archive.namelist()) == len(set(archive.namelist())), "Duplicate entries in CI JAR")
        migrations = sorted(n for n in archive.namelist() if re.fullmatch(
            re.escape(prefix) + r"db/migration/V\d{3}__[a-z0-9_]+\.sql", n))
        require([int(Path(n).name[1:4]) for n in migrations] == list(range(1, release["database_version"] + 1)), "Incomplete CI migration catalog")
        digest = hashlib.sha256()
        for name in migrations:
            digest.update(Path(name).name.encode() + b"\0" + archive.read(name).replace(b"\r\n", b"\n"))
        require(digest.hexdigest() == release["migrations_hash"], "Migration SQL differs from the CI release")
        selected = [n for n in archive.namelist() if re.fullmatch(
            re.escape(prefix) + r"db/migration/[BV]\d{3}__[a-z0-9_]+\.sql", n)
            or (not layered and re.fullmatch(r"BOOT-INF/lib/[A-Za-z0-9_.+\-]+\.jar", n))]
        (space_check or capacity)([runtime], sum(archive.getinfo(n).file_size for n in selected))
        for name in selected:
            target = runtime / ("lib/" + Path(name).name if name.startswith("BOOT-INF/lib/") else "sql/db/migration/" + Path(name).name)
            target.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
            with archive.open(name) as source, target.open("xb") as output:
                shutil.copyfileobj(source, output)
    libraries = list((runtime / "lib").iterdir()) if (runtime / "lib").is_dir() else []
    require(libraries and all(p.is_file() and not p.is_symlink() and re.fullmatch(r"[A-Za-z0-9_.+\-]+\.jar", p.name) for p in libraries),
            "Invalid CI migration library layout")
    require(any(p.name.startswith("flyway-core-") for p in libraries) and any(p.name.startswith("flyway-mysql-") for p in libraries),
            "CI image lacks Flyway libraries")


def prepare_images(commands, release, work, helper, preloaded=False):
    refs = environment_images(release, "test")
    metadata = {name: registry_size(ref) for name, ref in refs.items()}
    docker_root = commands.run(["docker", "info", "--format", "{{.DockerRootDir}}"], "docker-directory").strip()
    require(Path(docker_root).is_absolute(), "Docker directory unavailable")
    # Includes compressed content, unpacked layers and extraction working space.
    # Relayed layers are already in Docker. Budget extraction space without
    # charging the occupied image storage a second time.
    peak = metadata["erp-backend"]["compressed_bytes"] * 2 if preloaded else sum(v["compressed_bytes"] for v in metadata.values()) * 4
    capacity([work, docker_root], peak)
    for name, ref in refs.items():
        if not preloaded:
            pull_image(ref, "pull-" + name, commands.directory)
        info = json.loads(commands.run(["docker", "image", "inspect", ref], "inspect-" + name))[0]
        verify_image(info, ref, metadata[name], release["commit"])
        metadata[name]["size_bytes"] = info["Size"]
        metadata[name]["layout"] = info.get("Config", {}).get("Labels", {}).get("io.oakved.image.layout", "fat-jar")
    layout = metadata["erp-backend"]["layout"]
    require(layout in ("fat-jar", "spring-boot-tools-v1"), "Unsupported backend image layout")
    runtime = work / "runtime"
    require(not runtime.exists(), "Runtime extraction already exists; inspect the prior prepare attempt")
    runtime.mkdir(mode=0o700)
    container = commands.run(["docker", "create", "--label", "oakved.purpose=cd-migration-extraction", refs["erp-backend"]], "create-extraction").strip()
    require(re.fullmatch(r"[a-f0-9]{64}", container), "Invalid extraction container identity")
    write_json(work / "extraction.json", {"container": container, "image": refs["erp-backend"]})
    try:
        jar = runtime / "backend.jar"
        commands.run(["docker", "cp", container + ":/opt/yudao/app.jar", str(jar)], "copy-ci-jar", seconds=45)
        if layout == "spring-boot-tools-v1":
            (runtime / "lib").mkdir(mode=0o700)
            commands.run(["docker", "cp", container + ":/opt/yudao/lib/.", str(runtime / "lib")], "copy-ci-libraries", seconds=60)
        commands.run(["docker", "cp", container + ":/etc/passwd", str(runtime / "passwd")], "copy-image-passwd")
        commands.run(["docker", "cp", container + ":/etc/group", str(runtime / "group")], "copy-image-group")
    finally:
        commands.run(["docker", "rm", container], "remove-extraction")
        (work / "extraction.json").unlink()
    user = json.loads(commands.run(["docker", "image", "inspect", refs["erp-backend"]], "image-user"))[0]["Config"]["User"]
    rows = [line.split(":") for line in (runtime / "passwd").read_text().splitlines() if line.split(":")[0] == user]
    require(len(rows) == 1 and rows[0][2].isdigit() and int(rows[0][2]) > 0 and rows[0][3].isdigit(), "Cannot establish the backend image's non-root identity")
    metadata["uid"], metadata["gid"] = int(rows[0][2]), int(rows[0][3])
    extract_migrations(jar, runtime, release, layered=layout == "spring-boot-tools-v1")
    jar.unlink()  # Only the transient copy created in this attempt.
    code = base64.b64decode(helper["class"])
    require(sha256(code) == helper["sha256"] and code[:4] == b"\xca\xfe\xba\xbe", "Invalid migration helper")
    (runtime / "CloneMigration.class").write_bytes(code)
    metadata["runtime_files"] = {str(p.relative_to(runtime)): sha256(p.read_bytes()) for p in runtime.rglob("*") if p.is_file()}
    # Subsequent CD pulls do not extract a second application JAR. Use measured
    # compressed + unpacked sizes for their capacity budget.
    metadata["image_peak_bytes"] = sum(metadata[name]["compressed_bytes"] + metadata[name]["size_bytes"] for name in refs)
    write_json(work / "images.json", metadata)
    return metadata


def verify_runtime(work):
    metadata = json.loads((work / "images.json").read_text())
    runtime = work / "runtime"
    actual = {str(p.relative_to(runtime)): sha256(p.read_bytes()) for p in runtime.rglob("*") if p.is_file()}
    require(actual == metadata["runtime_files"], "Prepared migration runtime has changed")
    return metadata


def migrate(commands, runtime, database, env):
    safe_env = {k: v for k, v in env.items() if k not in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")}
    output = commands.run([PROFILE["java"], "-Xms32m", "-Xmx256m", "-XX:MaxMetaspaceSize=192m", "-XX:ActiveProcessorCount=1",
        "-cp", str(runtime) + ":" + str(runtime / "sql") + ":" + str(runtime / "lib/*"),
        "CloneMigration", database, PROFILE["source_database"]], "flyway-clone-migration", seconds=120, env=safe_env)
    markers = [v.removeprefix("ERP_CLONE_RESULT=") for v in output.splitlines() if v.startswith("ERP_CLONE_RESULT=")]
    require(len(markers) == 1, "Missing migration receipt")
    result = json.loads(markers[0])
    require(result == {"version": 49, "migrations_executed": 2, "repeat_migrations_executed": 0,
                       "checksums_valid": True, "source_access_denied": True}, "Incomplete clone migration verification")
    return result
