"""Exercise streaming Docker responses over a real HTTP socket, without Docker."""
import http.client
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import socket
import tempfile
import threading
import time
import unittest
from unittest.mock import patch
from urllib.parse import parse_qs, urlsplit

from fixtures import release
from image_pull import EngineConnection, Progress, pull_image

REFERENCE = release(1)["images"]["backend"]


class TestConnection(EngineConnection):
    def __init__(self, host, port, timeout):
        super().__init__(timeout)
        self.destination = (host, port)

    def connect(self):
        if self.active_socket:
            self.active_socket.close()
        self.sock = socket.create_connection(self.destination, timeout=self.timeout)
        self.active_socket = self.sock.dup()


class FakeEngine:
    def __init__(self, events, mismatch=False, disconnect=False, http_error=False, close_response=False):
        self.events, self.mismatch = events, mismatch
        self.disconnect, self.http_error = disconnect, http_error
        self.close_response = close_response
        self.stopped = threading.Event()
        self.paths = []
        engine = self

        class Handler(BaseHTTPRequestHandler):
            protocol_version = "HTTP/1.1"
            def log_message(self, *args):
                pass

            def do_GET(self):
                engine.paths.append(self.path)
                data = ({"ApiVersion": "1.52", "MinAPIVersion": "1.44"} if self.path == "/version" else
                        {"RepoDigests": [] if engine.mismatch else [REFERENCE], "Os": "linux", "Architecture": "amd64"})
                payload = json.dumps(data).encode()
                self.send_response(200)
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)

            def do_POST(self):
                engine.paths.append(self.path)
                if engine.http_error:
                    self.send_response(500)
                    self.send_header("Content-Length", "0")
                    self.end_headers()
                    return
                self.send_response(200)
                self.send_header("Transfer-Encoding", "chunked")
                if engine.close_response:
                    self.send_header("Connection", "close")
                self.end_headers()
                try:
                    for delay, event in engine.events:
                        if engine.stopped.wait(delay):
                            return
                        payload = event if isinstance(event, bytes) else json.dumps(event).encode() + b"\n"
                        # A JSON event may span multiple HTTP chunks and TCP packets.
                        for chunk in (payload[:7], payload[7:]):
                            if chunk:
                                self.wfile.write(f"{len(chunk):x}\r\n".encode() + chunk + b"\r\n")
                                self.wfile.flush()
                    if engine.disconnect:
                        self.close_connection = True
                        return
                    self.wfile.write(b"0\r\n\r\n")
                    self.wfile.flush()
                except OSError:
                    pass

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, kwargs={"poll_interval": 0.01}, daemon=True)
        self.thread.start()

    def connection(self, timeout):
        return TestConnection(*self.server.server_address, timeout=timeout)

    def close(self):
        self.stopped.set()
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)


def download(current):
    return {"id": "012345abcdef", "status": "Downloading", "progressDetail": {"current": current, "total": 1000}}


class StreamingPullTests(unittest.TestCase):
    def run_pull(self, events, seconds=2, idle=0.3, **options):
        engine = FakeEngine(events, **options)
        self.addCleanup(engine.close)
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.directory, self.engine = Path(directory.name), engine
        with patch("image_pull.EngineConnection", side_effect=engine.connection), patch("builtins.print"):
            return pull_image(REFERENCE, "pull-erp-backend", directory.name, seconds, idle)

    def receipt(self):
        return json.loads(next(self.directory.glob("*.json")).read_text())

    def test_download_longer_than_idle_window_succeeds_when_bytes_advance(self):
        result = self.run_pull([(0.06, download(n)) for n in range(1, 13)], idle=0.3)
        self.assertEqual(result["status"], "success")
        self.assertEqual(result["downloaded_bytes"], 12)
        self.assertGreater(result["elapsed_seconds"], 0.3)
        request = next(path for path in self.engine.paths if "images/create" in path)
        self.assertEqual(parse_qs(urlsplit(request).query), {"fromImage": [REFERENCE], "platform": ["linux/amd64"]})
        self.assertTrue(result["reader_stopped"])

    def test_identical_progress_and_retry_messages_cannot_hide_a_stall(self):
        events = [(0.03, download(1)), (0.03, {"status": "Retrying in 1 second", "id": "012345abcdef"})] * 15
        with self.assertRaisesRegex(ValueError, "no download/extraction progress"):
            self.run_pull(events, idle=0.2)
        self.assertEqual(self.receipt()["status"], "failed")
        self.assertTrue(self.receipt()["reader_stopped"])

    def test_no_events_and_open_connection_is_cancelled_within_deadline(self):
        started = time.monotonic()
        with self.assertRaises((RuntimeError, ValueError)):
            self.run_pull([(4, download(1))], idle=0.2)
        self.assertLess(time.monotonic() - started, 1)
        self.assertTrue(self.receipt()["reader_stopped"])

    def test_continuous_progress_still_obeys_total_deadline(self):
        with self.assertRaisesRegex(ValueError, "deadline"):
            self.run_pull([(0.03, download(n)) for n in range(100)], seconds=0.25)
        self.assertTrue(self.receipt()["reader_stopped"])

    def test_http_200_with_docker_error_is_failure(self):
        with self.assertRaisesRegex(ValueError, "registry/download error"):
            self.run_pull([(0, {"errorDetail": {"message": "download unavailable"}, "error": "download unavailable"})])
        self.assertEqual(self.receipt()["status"], "failed")

    def test_http_error_is_not_a_success(self):
        with self.assertRaisesRegex(RuntimeError, "HTTP 500"):
            self.run_pull([], http_error=True)

    def test_clean_eof_with_wrong_image_is_failure(self):
        with self.assertRaisesRegex(RuntimeError, "digest or platform"):
            self.run_pull([(0, download(10))], mismatch=True)

    def test_broken_chunked_response_is_failure(self):
        with self.assertRaises(RuntimeError):
            self.run_pull([(0, download(10))], disconnect=True)
        self.assertTrue(self.receipt()["reader_stopped"])

    def test_cached_digest_verified_even_when_no_download_needed(self):
        result = self.run_pull([(0, {"id": "012345abcdef", "status": "Already exists"})])
        self.assertEqual(result["completed_layers"], 1)
        self.assertEqual(result["downloaded_bytes"], 0)
        self.assertEqual(result["status"], "success")

    def test_server_can_close_response_after_a_complete_pull(self):
        result = self.run_pull([(0, download(1))], close_response=True)
        self.assertEqual(result["status"], "success")

    def test_connection_close_response_can_still_be_cancelled(self):
        with self.assertRaises((ValueError, RuntimeError)):
            self.run_pull([(3, download(1))], idle=0.2, close_response=True)
        self.assertTrue(self.receipt()["reader_stopped"])

    def test_invalid_stream_is_not_treated_as_healthy_progress(self):
        with self.assertRaises(RuntimeError):
            self.run_pull([(0, b"not-json\n")])

    def test_unpinned_image_is_refused_before_connecting(self):
        with patch("image_pull.EngineConnection") as connect:
            with self.assertRaises(ValueError):
                pull_image("ghcr.io/cqz-cio/furniture-erp-backend:main", "pull", ".")
            connect.assert_not_called()


class ProgressTests(unittest.TestCase):
    def test_extraction_and_cached_layers_count_as_real_progress(self):
        progress = Progress()
        event = {"id": "aaa", "status": "Extracting", "progressDetail": {"current": 30}}
        self.assertTrue(progress.update(event))
        self.assertFalse(progress.update(event))
        self.assertTrue(progress.update({"id": "aaa", "status": "Pull complete"}))
        self.assertEqual(progress.summary()["extracted_bytes"], 30)
        self.assertEqual(progress.summary()["completed_layers"], 1)

    def test_downloading_high_water_mark_cannot_be_reset_by_retry(self):
        progress = Progress()
        self.assertTrue(progress.update(download(100)))
        self.assertFalse(progress.update(download(1)))
        self.assertFalse(progress.update({"id": "aaa", "status": "Retrying in 2 seconds"}))
