"""Pull pinned ERP images using Docker's byte progress, not non-TTY CLI output.

The local Engine API streams progressDetail even when no terminal is attached.
Closing its HTTP connection cancels the pull; no subprocess/daemon job is detached.
"""
import http.client
import json
from pathlib import Path
import queue
import re
import socket
import threading
import time
from urllib.parse import quote, urlencode

from common import DIGEST, PACKAGES, require, utcnow, write_json


class EngineConnection(http.client.HTTPConnection):
    def __init__(self, timeout):
        super().__init__("localhost", timeout=timeout)
        self.active_socket = None

    def connect(self):
        if self.active_socket:
            self.active_socket.close()
        self.sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.sock.settimeout(self.timeout)
        self.sock.connect("/var/run/docker.sock")
        # Retain a cancellation handle when HTTPConnection gives ownership of a
        # Connection: close response to HTTPResponse (and clears self.sock).
        self.active_socket = self.sock.dup()

    def cancel(self):
        if self.active_socket:
            try:
                self.active_socket.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
            self.active_socket.close()
        self.close()


class Progress:
    def __init__(self):
        self.bytes = {}
        self.transitions = set()

    def update(self, event):
        status, layer = event.get("status", ""), event.get("id", "")
        detail = event.get("progressDetail") or {}
        require(isinstance(status, str) and isinstance(layer, str) and isinstance(detail, dict), "Invalid Docker progress event")
        changed = False
        # Retried messages and identical redraws must not keep a stalled pull alive.
        if status in ("Downloading", "Extracting"):
            current = detail.get("current", 0)
            require(type(current) is int and current >= 0, "Invalid Docker byte count")
            key = (layer, status)
            if current > self.bytes.get(key, 0):
                self.bytes[key], changed = current, True
        if status in ("Pulling fs layer", "Waiting", "Downloading", "Extracting", "Verifying Checksum",
                      "Download complete", "Pull complete", "Already exists"):
            key = (layer, status)
            if key not in self.transitions:
                self.transitions.add(key)
                changed = True
        return changed

    def summary(self):
        return {"downloaded_bytes": sum(v for (_, status), v in self.bytes.items() if status == "Downloading"),
                "extracted_bytes": sum(v for (_, status), v in self.bytes.items() if status == "Extracting"),
                "completed_layers": len({layer for layer, status in self.transitions if status in ("Pull complete", "Already exists")})}


def api_json(connection, path):
    connection.request("GET", path)
    with connection.getresponse() as response:
        require(response.status == 200, f"Docker inspection returned HTTP {response.status}")
        raw = response.read(1024 * 1024 + 1)
    require(len(raw) <= 1024 * 1024, "Oversized Docker inspection response")
    return json.loads(raw)


def pull_image(reference, label, directory, seconds=300, idle=60):
    package, separator, digest = reference.partition("@")
    require(separator and package in {"ghcr.io/" + p for p in PACKAGES.values()} and DIGEST.fullmatch(digest),
            "Only a pinned ERP image may be pulled")
    require(re.fullmatch(r"[a-z0-9-]+", label) and seconds > 0 and idle > 0, "Invalid pull limits")
    directory = Path(directory)
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    log = directory / (utcnow().strftime("%Y%m%dT%H%M%S%f-") + label + ".log")
    connection = EngineConnection(timeout=min(10, seconds, idle))
    events, cancelled = queue.Queue(maxsize=64), threading.Event()
    started = changed = notified = time.monotonic()
    progress, outcome = Progress(), "failed"

    def send(kind, value=None):
        while not cancelled.is_set():
            try:
                events.put((kind, value), timeout=0.1)
                return
            except queue.Full:
                pass

    def receive():
        try:
            version = api_json(connection, "/version")
            def parse(value):
                require(isinstance(value, str) and re.fullmatch(r"1\.\d+", value), "Invalid Docker API version")
                return tuple(map(int, value.split(".")))
            selected = min(parse(version["ApiVersion"]), (1, 52))
            require(parse(version.get("MinAPIVersion", "1.24")) <= selected and selected >= (1, 44), "Unsupported Docker API version")
            prefix = "/v" + ".".join(map(str, selected))
            require(not cancelled.is_set(), "Image pull cancelled")
            connection.request("POST", prefix + "/images/create?" + urlencode({"fromImage": reference, "platform": "linux/amd64"}), body=b"")
            connection.sock.settimeout(min(seconds, idle))
            with connection.getresponse() as response:
                require(response.status == 200, f"Docker pull returned HTTP {response.status}")
                while not cancelled.is_set():
                    raw = response.readline(65537)
                    if not raw:
                        break
                    require(len(raw) <= 65536 and raw.endswith(b"\n"), "Incomplete or oversized Docker progress event")
                    event = json.loads(raw)
                    require(isinstance(event, dict), "Invalid Docker progress response")
                    send("progress", event)
            require(not cancelled.is_set(), "Image pull cancelled")
            info = api_json(connection, prefix + "/images/" + quote(reference, safe="") + "/json")
            require(reference in info.get("RepoDigests", []) and info.get("Os") == "linux" and info.get("Architecture") == "amd64",
                    "Pulled image digest or platform differs from the requested release")
            send("complete")
        except Exception as error:
            send("error", error)
        finally:
            connection.cancel()

    def report(**fields):
        try:
            print(json.dumps({"stage": label, **fields}), flush=True)
        except BrokenPipeError:
            pass

    report(timeout_seconds=seconds, progress_source="docker-engine")
    worker = threading.Thread(target=receive, name="erp-image-pull", daemon=True)
    worker.start()
    try:
        with log.open("x", encoding="utf-8") as stream:
            while True:
                now = time.monotonic()
                require(now - started < seconds, f"{label} exceeded its {seconds}s deadline; download/extraction progress is recorded in the pull log")
                require(now - changed < idle, f"{label} had no download/extraction progress for {idle}s")
                try:
                    kind, value = events.get(timeout=0.1)
                except queue.Empty:
                    kind, value = None, None
                if kind == "progress":
                    # Private diagnostics exclude signed registry URLs and ANSI output.
                    safe = {k: value[k] for k in ("id", "status", "progressDetail", "error", "errorDetail") if k in value}
                    stream.write(re.sub(r"https?://[^\s\"\\]+", "<registry-url>", json.dumps(safe)) + "\n")
                    stream.flush()
                    require(not value.get("error") and not value.get("errorDetail"), f"{label}: Docker reported a registry/download error; inspect the private pull log")
                    if progress.update(value):
                        changed = time.monotonic()
                elif kind == "error":
                    message = re.sub(r"https?://[^\s]+", "<registry-url>", str(value))[:1000]
                    stream.write(json.dumps({"failure": type(value).__name__, "message": message}) + "\n")
                    raise RuntimeError(f"{label}: {type(value).__name__}: {message}") from value
                elif kind == "complete":
                    outcome = "success"
                    break
                if now - notified >= 10:
                    report(elapsed_seconds=round(now-started), **progress.summary())
                    notified = now
    finally:
        cancelled.set()
        connection.cancel()
        worker.join(timeout=min(10, seconds, idle) + 1)
        receipt = {"image": reference, "status": outcome, "elapsed_seconds": round(time.monotonic()-started, 2),
                   "timeout_seconds": seconds, "idle_seconds": idle, "reader_stopped": not worker.is_alive(), **progress.summary()}
        write_json(log.with_suffix(".json"), receipt)
    require(not worker.is_alive(), "Image pull reader did not terminate")
    report(status=outcome, **progress.summary())
    return receipt
