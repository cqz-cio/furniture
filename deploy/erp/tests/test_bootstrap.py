import copy
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

from fixtures import release
from bootstrap import Bootstrap
from bootstrap_io import Database, Commands
from bootstrap_policy import PROFILE, backend_environment, nginx_plan, owned_database, validate_target
from common import fingerprint, write_json
from runner import transport
import runner


def test_release():
    value = release(1)
    value["repository"] = "cqz-cio/furniture"
    value["config"]["test"] = {"api_base_url": "http://124.220.2.69", "storefront_url": "http://124.220.2.69"}
    return value


def proxy_files():
    main = '''server {
    listen 80;
    server_name 124.220.2.69;
    root /opt/oakved/frontend/furniture;
    location /admin/ {
        alias /opt/oakved/frontend/admin/;
        try_files $uri $uri/ /admin/index.html;
    }
    location /admin-api/ {
        proxy_pass http://127.0.0.1:48080/admin-api/;
    }
    location /app-api/ {
        proxy_pass http://127.0.0.1:48080/app-api/;
    }
    location /catalog/ { root /var/www; try_files $uri =404; }
    location / { try_files $uri /index.html; }
}
'''
    tob = '''limit_req_zone $binary_remote_addr zone=inquiry:10m rate=6r/m;
server {
    listen 8081;
    root /opt/oakved/frontend/tob;
    location = /api/public/inquiries {
        if ($request_method != POST) { return 405; }
        proxy_pass http://127.0.0.1:48080/app-api/system/website-inquiry/notify;
        include /etc/nginx/snippets/vanz-inquiry-secret.conf;
    }
    location /app-api/ {
        proxy_pass http://127.0.0.1:48080;
    }
    location /assets/ { try_files $uri =404; }
}
'''
    return dict(zip(PROFILE["nginx_files"], (main, tob)))


class IsolationPolicy(unittest.TestCase):
    def test_bootstrap_requires_test_host_release_and_explicit_cutover(self):
        value = test_release()
        validate_target("test", PROFILE["root"], value, "prepare")
        for environment, root, operation, confirmed in (("production", "/opt/oakved-deploy/production", "prepare", False),
                ("test", PROFILE["root"], "cutover", False), ("test", PROFILE["root"], "recover", "true")):
            with self.subTest(operation=operation, environment=environment), self.assertRaises(ValueError):
                validate_target(environment, root, value, operation, confirmed)
        value["config"]["test"]["api_base_url"] = "https://api-other.example.com"
        with self.assertRaises(ValueError):
            validate_target("test", PROFILE["root"], value, "prepare")

    def test_business_databases_and_identifier_injection_are_rejected(self):
        for name in (PROFILE["source_database"], "production", "oakved_cd_test_live_", "oakved_cd_test_live_1234';DROP DATABASE x"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                owned_database(name)

    def test_literal_secrets_survive_env_render_without_os_process_state(self):
        runtime = {"YUDAO_DB_URL": "jdbc:mysql://127.0.0.1:3306/" + PROFILE["source_database"] + "?useSSL=false",
                   "YUDAO_DB_PASSWORD": "legacy-secret", "YUDAO_REDIS_PASSWORD": "quotes'\"$# remain literal",
                   "SPRING_DATA_REDIS_DATABASE": "0", "HOME": "/root", "JAVA_TOOL_OPTIONS": "-bad-option"}
        result = backend_environment(runtime, "oakved_cd_test_live_" + "a" * 16, "erp_cd_a_" + "a" * 16, "c" * 64)
        self.assertIn("YUDAO_REDIS_PASSWORD=quotes'\"$# remain literal\n", result)
        self.assertNotIn("legacy-secret", result)
        self.assertNotIn("HOME=", result)
        self.assertNotIn("JAVA_TOOL_OPTIONS", result)
        with self.assertRaises(ValueError):
            backend_environment({**runtime, "SPRING_DATASOURCE_URL": "other"}, "oakved_cd_test_live_" + "a" * 16, "erp_cd_a_" + "a" * 16, "c" * 64)

    def test_all_erp_routes_are_gated_while_static_sites_remain_identical(self):
        originals = proxy_files()
        plans = nginx_plan(originals, "a" * 48)
        for mode in ("maintenance", "candidate", "open"):
            for path, data in plans[mode].items():
                for line in originals[path].splitlines():
                    if "root " in line or "location /catalog/" in line or "location /assets/" in line or "include " in line:
                        self.assertIn(line, data)
                if mode == "open":
                    self.assertNotIn("return 503", data)
                    self.assertNotIn(":48080", data)
                else:
                    self.assertGreaterEqual(data.count("$http_x_erp_cd_probe"), 2)
        main = plans["candidate"][PROFILE["nginx_main"]]
        self.assertEqual(main.count("location = /actuator/"), 2)
        self.assertIn("proxy_pass http://127.0.0.1:18080;", main)

    def test_unknown_proxy_shape_or_additional_site_blocks_mutation(self):
        originals = proxy_files()
        originals["/etc/nginx/conf.d/another.conf"] = "server {}"
        with self.assertRaises(ValueError):
            nginx_plan(originals, "a" * 48)
        originals = proxy_files()
        originals[PROFILE["nginx_main"]] = originals[PROFILE["nginx_main"]].replace("alias /opt/oakved/frontend/admin/", "alias /some/other/site/")
        with self.assertRaises(ValueError):
            nginx_plan(originals, "a" * 48)

    def test_existing_account_is_never_removed_on_create_refusal(self):
        database = Database(Mock())
        database.query = Mock(return_value="1")
        database.remove_user = Mock()
        with self.assertRaises(ValueError):
            with database.restricted("oakved_cd_test_rehearse_" + "a" * 16, "erp_cd_m_" + "a" * 16, "b" * 64):
                self.fail("Account should not be used")
        database.remove_user.assert_not_called()


class CutoverHarness(Bootstrap):
    def __init__(self, directory, fail=None):
        self.root = Path(directory)
        self.path = self.root / "state.json"
        self.release = test_release()
        self.payload = {"release": self.release}
        self.state = {"attempt": "a" * 16, "phase": "prepared", "release_id": self.release["id"],
                      "release_hash": fingerprint(self.release), "backup": {"bytes": 100}, "writes_may_be_open": False,
                      "live_database": "oakved_cd_test_live_" + "a" * 16}
        write_json(self.root / "source.json", {"probe_token": "a" * 48})
        self.calls, self.fail = [], fail
        self.database = Mock()
        self.database.query.return_value = "0"
        self.database.counts.return_value = {"products": "unchanged"}
        self.database.version.return_value = 47
        self.database.backup.side_effect = lambda *a: self.step("backup") or {"bytes": 100}
        self.candidate = Mock()
        self.candidate.wait_healthy.side_effect = lambda *a, **k: self.step("health")
        self.candidate.healthy.side_effect = lambda *a, **k: self.step("final-health")
    def step(self, name):
        self.calls.append(name)
        if name == self.fail:
            raise RuntimeError("injected " + name)
    def work(self):
        return self.root
    def preflight(self):
        self.step("preflight")
    def verify_prepared(self, snapshot):
        self.step("verify-prepared")
    def save(self, phase=None, **fields):
        if phase:
            self.state["phase"] = phase
        self.state.update(fields)
        self.calls.append("save:" + self.state["phase"])
    def website_snapshot(self):
        return {"site": "unchanged"}
    def run(self, args, label, seconds=30):
        self.step(label)
        return "0"
    def replace_routes(self, mode):
        self.step("routes:" + mode)
    def clone_and_migrate(self, *args):
        self.step("clone-migration")
        return {"counts": {"products": "unchanged"}}
    def start_candidate(self):
        self.step("start-candidate")
        return self.candidate
    def restore_legacy(self):
        self.step("restore-legacy")
        self.save("restored-legacy")
    def finish(self, server):
        self.step("finish")
        self.save("complete")
        return self.result("success")


class CutoverFailures(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.capacity = patch("bootstrap.capacity")
        self.capacity.start()
        self.addCleanup(self.capacity.stop)

    def test_stop_fresh_backup_migration_health_then_publish(self):
        engine = CutoverHarness(self.tmp.name)
        result = engine.cutover()
        self.assertEqual(result["status"], "success")
        ordered = ["routes:maintenance", "stop-legacy", "backup", "clone-migration", "start-candidate", "routes:candidate", "health", "save:publishing", "routes:open", "finish"]
        self.assertEqual(sorted(ordered, key=engine.calls.index), ordered)
        self.assertTrue(engine.state["writes_may_be_open"])
        self.assertNotIn("restore-legacy", engine.calls)

    def test_failure_before_traffic_opens_restores_legacy(self):
        for failed in ("routes:maintenance", "stop-legacy", "backup", "clone-migration", "start-candidate", "routes:candidate", "health", "disable-legacy-autostart"):
            with self.subTest(failed=failed):
                engine = CutoverHarness(self.tmp.name, failed)
                with self.assertRaises(RuntimeError):
                    engine.cutover()
                self.assertIn("restore-legacy", engine.calls)
                self.assertNotIn("routes:open", engine.calls)
                self.assertEqual(engine.state["phase"], "restored-legacy")

    def test_no_stale_database_rollback_after_publish_attempt(self):
        for failed in ("routes:open", "final-health", "finish"):
            with self.subTest(failed=failed):
                engine = CutoverHarness(self.tmp.name, failed)
                with self.assertRaises(RuntimeError):
                    engine.cutover()
                self.assertNotIn("restore-legacy", engine.calls)
                self.assertEqual(engine.state["phase"], "roll-forward-required")

    def test_other_database_connections_prevent_snapshot_and_candidate(self):
        engine = CutoverHarness(self.tmp.name)
        engine.database.query.return_value = "1"
        with self.assertRaises(ValueError):
            engine.cutover()
        engine.database.backup.assert_not_called()
        self.assertNotIn("start-candidate", engine.calls)
        self.assertIn("restore-legacy", engine.calls)

    def test_unprepared_or_incomplete_cutover_cannot_be_repeated(self):
        engine = CutoverHarness(self.tmp.name)
        for phase in ("new", "preparing", "fresh-backup", "publishing", "recovery-required"):
            engine.state["phase"] = phase
            with self.assertRaises(ValueError):
                engine.cutover()
        self.assertEqual(engine.calls, [])

    def test_recover_after_publication_verifies_new_service_and_never_starts_legacy(self):
        engine = CutoverHarness(self.tmp.name)
        engine.state.update(phase="roll-forward-required", writes_may_be_open=True)
        engine.candidate.config = {"database_name": engine.state["live_database"]}
        with patch("bootstrap.Server", return_value=engine.candidate):
            result = engine.recover()
        self.assertEqual(result["status"], "success")
        self.assertNotIn("restore-legacy", engine.calls)
        self.assertIn("routes:open", engine.calls)


class TransportTests(unittest.TestCase):
    def test_streaming_result_is_parsed_without_exposing_stderr(self):
        with tempfile.TemporaryDirectory() as directory, patch("builtins.print") as output:
            script = "import sys; print('progress',flush=True); print('ERP_CD_RESULT={\"status\":\"prepared\"}'); print('private-secret',file=sys.stderr)"
            result = transport([sys.executable, "-c", script], {}, directory, 10)
        self.assertEqual(result, {"status": "prepared"})
        self.assertNotIn("private-secret", str(output.call_args_list))

    def test_failed_ssh_cannot_be_misreported_as_prepared(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, "SSH operation failed"):
                transport([sys.executable, "-c", "raise SystemExit(1)"], {}, directory, 10)

    @unittest.skipIf(os.name == "nt", "Server command process groups require Linux")
    def test_command_timeout_kills_its_own_process_group(self):
        with tempfile.TemporaryDirectory() as directory:
            command = Commands(directory)
            with self.assertRaisesRegex(ValueError, "deadline"):
                command.run([sys.executable, "-c", "import time; time.sleep(30)"], "timeout-test", seconds=0.1)
            metadata = json.loads(next(Path(directory).glob("*.json")).read_text())
            self.assertIsNotNone(metadata["exit_code"])
            with self.assertRaises(ProcessLookupError):
                os.kill(metadata["pid"], 0)


class WorkflowGates(unittest.TestCase):
    def test_prepare_and_legacy_recovery_never_register_a_successful_deployment(self):
        for operation, result in (("prepare", "prepared"), ("recover", "restored-legacy")):
            with self.subTest(operation=operation), tempfile.TemporaryDirectory() as directory:
                client = Mock()
                client.release.return_value = ({}, test_release())
                argv = ["runner.py", "--environment", "test", "--operation", operation, "--release", test_release()["id"],
                        "--confirm-cutover", "true", "--output", str(Path(directory)/"result.json")]
                with patch.object(sys, "argv", argv), patch.dict(os.environ, {"GITHUB_REF": "refs/heads/main"}, clear=True), \
                        patch("runner.GitHub", return_value=client), patch("runner.ssh_request", return_value={"status": result}):
                    runner.main()
                client.deployment.assert_not_called()
                client.deployment_status.assert_not_called()

    def test_unconfirmed_cutover_or_production_bootstrap_cannot_reach_ssh(self):
        for environment, operation in (("test", "cutover"), ("production", "prepare")):
            client = Mock()
            client.release.return_value = ({}, test_release())
            argv = ["runner.py", "--environment", environment, "--operation", operation, "--release", test_release()["id"]]
            with patch.object(sys, "argv", argv), patch.dict(os.environ, {"GITHUB_REF": "refs/heads/main"}, clear=True), \
                    patch("runner.GitHub", return_value=client), patch("runner.ssh_request") as ssh:
                with self.assertRaises(ValueError):
                    runner.main()
            ssh.assert_not_called()
            client.deployment.assert_not_called()

    def test_daily_cd_off_switch_remains_effective(self):
        argv = ["runner.py", "--environment", "test", "--operation", "deploy", "--release", test_release()["id"]]
        with patch.object(sys, "argv", argv), patch.dict(os.environ, {"GITHUB_REF": "refs/heads/main", "ERP_CD_ENABLED": "false"}, clear=True), \
                patch("runner.ssh_request") as ssh:
            with self.assertRaises(ValueError):
                runner.main()
        ssh.assert_not_called()


class ScratchCleanup(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.engine = object.__new__(Bootstrap)
        self.engine.root = Path(self.tmp.name)
        self.engine.state = {"phase": "complete", "writes_may_be_open": True,
            "rehearsal_database": "oakved_cd_test_rehearse_" + "a"*16,
            "live_database": "oakved_cd_test_live_" + "a"*16}
        self.engine.database = Mock()
        self.engine.database.query.return_value = "0"
        self.engine.save = Mock()
        self.engine.work = lambda: self.engine.root / "attempt"
        write_json(self.engine.root / "config/server.json", {"database_name": self.engine.state["live_database"]})

    def test_live_or_adopted_database_is_never_deleted(self):
        self.engine.state["rehearsal_database"] = self.engine.state["live_database"]
        with self.assertRaises(ValueError):
            self.engine.cleanup_scratch()
        self.engine.database.query.assert_not_called()

    def test_open_rehearsal_connection_prevents_cleanup(self):
        self.engine.database.query.return_value = "1"
        self.engine.cleanup_scratch()
        self.assertFalse(any("DROP" in call.args[0] for call in self.engine.database.query.call_args_list))

    def test_only_verified_runtime_is_removed_and_backup_survives(self):
        runtime = self.engine.work() / "runtime"
        (runtime / "lib").mkdir(parents=True)
        (runtime / "lib/test.jar").write_bytes(b"owned")
        backup = self.engine.work() / "cutover.sql"
        backup.write_text("retained backup")
        with patch("bootstrap.verify_runtime", return_value={"runtime_files": {"lib/test.jar": "checked"}}):
            self.engine.cleanup_scratch()
        self.assertFalse(runtime.exists())
        self.assertEqual(backup.read_text(), "retained backup")
        drops = [c.args[0] for c in self.engine.database.query.call_args_list if "DROP" in c.args[0]]
        self.assertEqual(drops, ["DROP DATABASE IF EXISTS `" + self.engine.state["rehearsal_database"] + "`"])


if __name__ == "__main__":
    unittest.main()
