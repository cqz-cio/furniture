import contextlib
import hashlib
import http.client
import io
import json
import os
import ssl
import unittest
import urllib.error
from unittest.mock import Mock, patch

from github_release import GitHub
from retention import Registry
from common import PACKAGES
from network_retry import retry_read, DELAYS
from runner_network import wait_for_network, probe

def response(body):
    result = Mock()
    result.__enter__ = Mock(return_value=result)
    result.__exit__ = Mock(return_value=False)
    result.read.return_value = body
    return result

class NetworkRetries(unittest.TestCase):
    def test_read_recovers_from_ssl_eof_and_partial_body(self):
        client = GitHub("cqz-cio/furniture", "secret-test-token")
        partial = response(b"")
        partial.read.side_effect = http.client.IncompleteRead(b"{")
        client.opener.open = Mock(side_effect=[
            urllib.error.URLError(ssl.SSLEOFError("EOF")), partial, response(b'{"ok": true}')])
        with patch("network_retry.time.sleep") as sleep:
            self.assertEqual(client.request("/test"), {"ok": True})
        self.assertEqual(client.opener.open.call_count, 3)
        self.assertEqual([c.args[0] for c in sleep.call_args_list], [2, 5])
        partial.__exit__.assert_called_once()

    def test_retries_are_bounded(self):
        operation = Mock(side_effect=TimeoutError("timed out"))
        with patch("network_retry.time.sleep") as sleep, self.assertRaises(TimeoutError):
            retry_read(operation, "test")
        self.assertEqual(operation.call_count, 5)
        self.assertEqual([c.args[0] for c in sleep.call_args_list], list(DELAYS))

    def test_certificate_auth_validation_errors_fail_immediately(self):
        errors = [
            urllib.error.URLError(ssl.SSLCertVerificationError("bad certificate")),
            urllib.error.HTTPError("https://api.github.com", 401, "unauthorized", {}, None),
            urllib.error.HTTPError("https://api.github.com", 403, "forbidden", {}, None),
            urllib.error.HTTPError("https://api.github.com", 404, "missing", {}, None),
            ValueError("invalid manifest"),
        ]
        for error in errors:
            with self.subTest(error=type(error).__name__):
                operation = Mock(side_effect=error)
                with patch("network_retry.time.sleep") as sleep, self.assertRaises(type(error)):
                    retry_read(operation, "read")
                operation.assert_called_once()
                sleep.assert_not_called()
                if isinstance(error, urllib.error.HTTPError):
                    error.close()

    def test_retry_output_does_not_leak_exception_credentials(self):
        operation = Mock(side_effect=[ConnectionResetError("token=secret-test-token"), b"ok"])
        output = io.StringIO()
        with contextlib.redirect_stdout(output), patch("network_retry.time.sleep"):
            self.assertEqual(retry_read(operation, "read"), b"ok")
        self.assertNotIn("secret-test-token", output.getvalue())

    def test_http_503_read_is_retried(self):
        client = GitHub("cqz-cio/furniture", "fake")
        client.opener.open = Mock(side_effect=[
            urllib.error.HTTPError("https://api.github.com", 503, "", {}, None),
            response(b"[]")])
        with patch("network_retry.time.sleep"):
            self.assertEqual(client.request("/items"), [])

    def test_mutations_are_never_retried(self):
        for method in ("POST", "PATCH", "PUT", "DELETE"):
            for error in (urllib.error.URLError(ssl.SSLEOFError("EOF")),
                          urllib.error.HTTPError("https://api.github.com", 503, "", {}, None)):
                client = GitHub("cqz-cio/furniture", "fake")
                client.opener.open = Mock(side_effect=error)
                with self.subTest(method=method), patch("network_retry.time.sleep") as sleep:
                    with self.assertRaises((urllib.error.URLError, RuntimeError)):
                        client.request("/items/1", method)
                    client.opener.open.assert_called_once()
                    sleep.assert_not_called()

    def test_ghcr_token_and_manifest_reads_recover(self):
        body = b'{"schemaVersion": 2}'
        digest = "sha256:" + hashlib.sha256(body).hexdigest()
        package = next(iter(PACKAGES.values()))
        with patch.dict(os.environ, GITHUB_ACTOR="cqz-cio", GH_TOKEN="fake"):
            with patch("retention.urllib.request.urlopen", side_effect=[
                urllib.error.URLError(ssl.SSLEOFError("EOF")),
                response(b'{"token": "fake"}'),
                TimeoutError(), response(body)]) as open_url, patch("network_retry.time.sleep"):
                self.assertEqual(Registry().manifest(package, digest), {"schemaVersion": 2})
                self.assertEqual(open_url.call_count, 4)

    def test_ghcr_digest_failure_is_not_retried(self):
        package = next(iter(PACKAGES.values()))
        registry = Registry()
        registry.tokens[package] = "fake"
        with patch("retention.urllib.request.urlopen", return_value=response(b"{}")) as open_url:
            with patch("network_retry.time.sleep") as sleep, self.assertRaises(ValueError):
                registry.manifest(package, "sha256:" + "a" * 64)
            open_url.assert_called_once()
            sleep.assert_not_called()

class RunnerReadiness(unittest.TestCase):
    def test_failure_resets_stability_counter(self):
        now = [0]
        def sleep(seconds):
            now[0] += seconds
        def check(endpoint):
            return endpoint[0], now[0] != 10
        self.assertTrue(wait_for_network(timeout=60, check=check,
                                        clock=lambda: now[0], sleep=sleep))
        self.assertEqual(now[0], 40)

    def test_outage_exits_at_deadline(self):
        now = [0]
        def sleep(seconds):
            now[0] += seconds
        self.assertFalse(wait_for_network(timeout=25, check=lambda e: (e[0], False),
                                         clock=lambda: now[0], sleep=sleep))
        self.assertEqual(now[0], 25)

    def test_expected_unauthenticated_registry_status_is_healthy(self):
        opener = Mock()
        opener.open.side_effect = urllib.error.HTTPError("https://ghcr.io/v2/", 401, "", {}, None)
        with patch("runner_network.urllib.request.build_opener", return_value=opener):
            self.assertEqual(probe(("ghcr.io", "https://ghcr.io/v2/", {401})), ("ghcr.io", True))

    def test_tls_or_unexpected_http_failure_is_not_healthy(self):
        for error in [urllib.error.URLError(ssl.SSLEOFError("EOF")),
                      urllib.error.HTTPError("https://api.github.com/", 503, "", {}, None)]:
            opener = Mock()
            opener.open.side_effect = error
            with patch("runner_network.urllib.request.build_opener", return_value=opener):
                self.assertEqual(probe(("api.github.com", "https://api.github.com/", {200})),
                                 ("api.github.com", False))

if __name__ == "__main__":
    unittest.main()
