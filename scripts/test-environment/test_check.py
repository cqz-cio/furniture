import contextlib
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import urllib.error

import check
import remote_check


class CheckSafetyTests(unittest.TestCase):
    def setUp(self):
        self.env = dict(zip(("TENCENT_SSH_HOST", "TENCENT_SSH_USER", "TENCENT_SSH_PORT"), check.TARGET))
        self.env.update({name: "synthetic-test-value-" + name for name in check.CREDENTIALS + check.SSH_SECRETS})

    def test_configuration_rejects_production_and_shell_arguments(self):
        for name, value in (("TENCENT_SSH_HOST", "43.153.40.182"),
                            ("TENCENT_SSH_HOST", "124.220.2.69;whoami"),
                            ("TENCENT_SSH_USER", "root"), ("TENCENT_SSH_PORT", "2222")):
            with self.subTest(name=name, value=value), self.assertRaises(ValueError):
                check.validate_config(dict(self.env, **{name: value}))

    def test_missing_secrets_cannot_silently_use_server_credentials(self):
        for name in check.CREDENTIALS + check.SSH_SECRETS:
            with self.subTest(name=name), self.assertRaises(ValueError):
                check.validate_config(dict(self.env, **{name: ""}))

    def test_subprocess_environment_does_not_inherit_secrets(self):
        env = check.child_environment(dict(self.env, PATH="/usr/bin"))
        self.assertEqual(env["PATH"], "/usr/bin")
        for name in check.CREDENTIALS + check.SSH_SECRETS:
            self.assertNotIn(name, env)

    @patch("check.subprocess.run")
    def test_credentials_only_travel_on_stdin_with_verified_ssh(self, run):
        run.return_value = subprocess.CompletedProcess([], 0, '{"checks": [{"name": "backend_service_active", "status": "pass"}]}', "")
        check.remote_check(self.env, Path("test_key"), Path("trusted_hosts"), 48)
        args, kwargs = run.call_args
        command = args[0]
        self.assertIn("StrictHostKeyChecking=yes", command)
        self.assertIn("UserKnownHostsFile=trusted_hosts", command)
        self.assertLessEqual(kwargs["timeout"], 45)
        for name in check.CREDENTIALS:
            self.assertNotIn(self.env[name], " ".join(command))
            self.assertNotIn(name, kwargs["env"])
        payload = json.loads(kwargs["input"])
        self.assertEqual(payload["credentials"], {k: self.env[k] for k in check.CREDENTIALS})
        self.assertFalse(payload["server_config"])

    @patch("check.subprocess.run")
    def test_ssh_failure_does_not_expose_stderr(self, run):
        run.return_value = subprocess.CompletedProcess([], 255, "", "SENSITIVE_ERROR_SENTINEL")
        with self.assertRaises(RuntimeError) as caught:
            check.remote_check(self.env, Path("key"), Path("hosts"), 48)
        self.assertNotIn("SENSITIVE_ERROR_SENTINEL", str(caught.exception))

    @patch("check.subprocess.run")
    def test_empty_remote_report_is_not_accepted_as_success(self, run):
        run.return_value = subprocess.CompletedProcess([], 0, '{"checks": []}', "")
        with self.assertRaises(RuntimeError):
            check.remote_check(self.env, Path("key"), Path("hosts"), 48)

    def test_http_200_with_business_401_is_denied_not_successful(self):
        result = (200, {}, {"code": 401, "data": None})
        self.assertTrue(remote_check.denied_api(result))
        self.assertFalse(remote_check.successful_api(result))
        self.assertFalse(remote_check.denied_api((200, {}, {"code": 0})))
        self.assertFalse(remote_check.denied_api((500, {}, {"code": 500})))

    def test_html_or_missing_response_is_not_a_successful_cms_api(self):
        self.assertFalse(remote_check.successful_api((200, {}, None)))
        self.assertFalse(remote_check.successful_api((200, {}, {"msg": "OK"})))
        self.assertTrue(remote_check.successful_api((200, {}, {"code": 0, "data": None})))

    def test_http_redirects_to_other_hosts_are_not_followed(self):
        handler = check.NoRedirect()
        self.assertIsNone(handler.redirect_request(None, None, 302, "", {}, "https://api.vanzhome.com/admin/login"))

    @patch("check.run_checks")
    def test_timeout_output_is_redacted_and_temporary_keys_are_cleaned(self, run):
        paths = []

        def timeout(env, key, hosts):
            paths.extend([key, hosts])
            self.assertEqual(key.read_text().strip(), self.env[check.SSH_SECRETS[0]])
            raise subprocess.TimeoutExpired(["ssh", "SENSITIVE_COMMAND_SENTINEL"], 43,
                                            output="SENSITIVE_STDOUT_SENTINEL", stderr="SENSITIVE_STDERR_SENTINEL")

        run.side_effect = timeout
        with patch.dict(os.environ, self.env, clear=True), tempfile.TemporaryDirectory() as tmp:
            report = Path(tmp) / "report.json"
            summary = Path(tmp) / "summary.md"
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                status = check.main(["--report", str(report), "--summary", str(summary)])
            self.assertEqual(status, 1)
            all_output = output.getvalue() + report.read_text() + summary.read_text()
            self.assertNotIn("SENSITIVE_", all_output)
            for value in self.env.values():
                if value.startswith("synthetic-"):
                    self.assertNotIn(value, all_output)
            self.assertFalse(json.loads(report.read_text())["passed"])
        self.assertEqual(len(paths), 2)
        self.assertTrue(all(not p.exists() for p in paths))

    def test_health_success_does_not_claim_a_release_or_authenticated_login(self):
        report = check.finish_report({"checks": [{"name": "mysql_authentication", "status": "pass"},
                                                   {"name": "migration_matches_repository", "status": "warn"}]})
        self.assertTrue(report["passed"])
        self.assertFalse(report["release_ready"])
        report["checks"].append({"name": "redis_authentication", "status": "fail"})
        self.assertFalse(check.finish_report(report)["passed"])


if __name__ == "__main__":
    unittest.main()
