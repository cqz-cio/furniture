import copy
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from fixtures import release
from common import environment_images, write_json
from server import Server


class FakeServer(Server):
    def __init__(self, root):
        self.root, self.environment = Path(root), "test"
        self.state_path = self.root / "state.json"
        self.state = {"environment": "test", "current": release(1)["id"], "history": [], "pins": [], "in_progress": None, "compatible": []}
        self.config = {}
        self.calls, self.releases = [], {release(1)["id"]: release(1)}
        self.schema, self.target_schema = 49, 49
        self.review = {}
        self.fail_pull, self.fail_health = False, False
        self.cleanup_ran = False

    def ensure_no_lease(self):
        pass

    def preflight(self, value, operation):
        return self.schema, self.review

    def stage(self, value, compose_source):
        self.releases[value["id"]] = value

    def manifest(self, release_id):
        return self.releases[release_id]

    def compose(self, release_id, *args, timeout=60):
        self.calls.append((release_id, args))
        return ""

    def pull_images(self, value):
        self.calls.append((value["id"], ("pull",)))
        if self.fail_pull:
            raise RuntimeError("pull failed")

    def healthy(self, value):
        self.calls.append((value["id"], ("health",)))

    def wait_healthy(self, value):
        self.calls.append((value["id"], ("wait",)))
        if self.fail_health and value["id"] != release(1)["id"]:
            raise RuntimeError("health failed")

    def database_version(self):
        return self.target_schema

    def backup(self, release_id):
        self.calls.append((release_id, ("backup",)))
        return str(self.root / "backup.sql")

    def log(self, message):
        pass

    def local_cleanup(self):
        self.cleanup_ran = True


class DeploymentState(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.server = FakeServer(self.tmp.name)

    def test_success_moves_current_and_preserves_previous(self):
        result = self.server.deploy(release(2), "deploy", "compose")
        self.assertEqual(result["status"], "success")
        self.assertEqual(self.server.state["history"], [release(1)["id"]])
        self.assertEqual(self.server.state["current"], release(2)["id"])
        self.assertTrue(self.server.cleanup_ran)
        self.assertIsNone(json.loads(self.server.state_path.read_text())["in_progress"])

    def test_pull_failure_never_stops_old_service(self):
        self.server.fail_pull = True
        with self.assertRaisesRegex(RuntimeError, "failed-before-switch"):
            self.server.deploy(release(2), "deploy", "compose")
        self.assertFalse(any(args[0] == "stop" for _, args in self.server.calls))
        self.assertEqual(self.server.state["current"], release(1)["id"])
        self.assertFalse(self.server.cleanup_ran)

    def test_startup_failure_rolls_back_a_pair_without_database_change(self):
        self.server.fail_health = True
        with self.assertRaisesRegex(RuntimeError, "rolled-back"):
            self.server.deploy(release(2), "deploy", "compose")
        self.assertTrue(any(rid == release(1)["id"] and args[0] == "up" for rid, args in self.server.calls))
        self.assertEqual(self.server.state["history"], [])
        self.assertIsNone(self.server.state["in_progress"])

    def test_failed_mysql_ddl_cannot_be_assumed_safe_from_unchanged_ledger(self):
        self.server.fail_health = True
        self.server.review = {"compatible_release_ids": [release(1)["id"]]}
        with self.assertRaisesRegex(RuntimeError, "manual-recovery-required"):
            self.server.deploy(release(2, schema=50), "deploy", "compose")
        self.assertIsNotNone(self.server.state["in_progress"])
        self.assertFalse(any(rid == release(1)["id"] and args[0] == "up" for rid, args in self.server.calls))

    def test_complete_compatible_migration_allows_program_rollback(self):
        self.server.fail_health, self.server.target_schema = True, 50
        self.server.review = {"compatible_release_ids": [release(1)["id"]]}
        with self.assertRaisesRegex(RuntimeError, "rolled-back"):
            self.server.deploy(release(2, schema=50), "deploy", "compose")
        self.assertEqual(self.server.state["schema_version"], 50)
        self.assertIn(release(1)["id"], self.server.state["compatible"])

    def test_unknown_compatibility_never_rolls_back_migrated_database(self):
        self.server.fail_health, self.server.target_schema = True, 50
        with self.assertRaisesRegex(RuntimeError, "manual-recovery-required"):
            self.server.deploy(release(2, schema=50), "deploy", "compose")

    def test_unfinished_journal_blocks_retry(self):
        self.server.state["in_progress"] = {"phase": "switch"}
        with self.assertRaisesRegex(ValueError, "unfinished"):
            self.server.deploy(release(2), "deploy", "compose")
        self.assertEqual(self.server.calls, [])

    def test_same_release_is_checked_without_restarting(self):
        result = self.server.deploy(release(1), "deploy", "compose")
        self.assertEqual(result["status"], "already-current")
        self.assertEqual(len(self.server.calls), 1)

    def test_unregistered_rollback_refused(self):
        with self.assertRaisesRegex(ValueError, "not a protected"):
            self.server.deploy(release(2), "rollback", "compose")

    def test_rollback_failure_keeps_journal_for_recovery(self):
        def fail_everywhere(value):
            raise RuntimeError("unhealthy")
        self.server.wait_healthy = fail_everywhere
        with self.assertRaisesRegex(RuntimeError, "manual-recovery-required"):
            self.server.deploy(release(2), "deploy", "compose")
        self.assertIsNotNone(self.server.state["in_progress"])


class EnvironmentAndLeases(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name) / "test"
        (self.root / "config").mkdir(parents=True)
        write_json(self.root / "config/server.json", {"environment": "test", "initialized": True,
            "project": "oakved-erp-test", "database_name": "erp_test"})

    def test_wrong_environment_root_is_rejected(self):
        with self.assertRaises(ValueError):
            Server(self.root, "production")

    def test_wrong_project_is_rejected(self):
        path = self.root / "config/server.json"
        value = json.loads(path.read_text())
        value["project"] = "oakved-erp-production"
        write_json(path, value)
        with self.assertRaises(ValueError):
            Server(self.root, "test")

    def test_setup_is_required(self):
        path = self.root / "config/server.json"
        value = json.loads(path.read_text())
        value["initialized"] = False
        write_json(path, value)
        with self.assertRaises(ValueError):
            Server(self.root, "test")

    def test_cleanup_lease_blocks_deploy_until_released(self):
        server = Server(self.root, "test")
        server.snapshot = lambda: {"environment": "test"}
        result = server.lease("lease-start", "gc-1-1")
        self.assertEqual(result["lease_id"], "gc-1-1")
        with self.assertRaises(ValueError):
            server.ensure_no_lease()
        server.lease("lease-end", "gc-1-1")
        server.ensure_no_lease()

    def test_another_run_cannot_release_active_lease(self):
        server = Server(self.root, "test")
        server.snapshot = lambda: {}
        server.lease("lease-start", "gc-1-1")
        with self.assertRaises(ValueError):
            server.lease("lease-end", "gc-2-1")

    def test_snapshot_refuses_unfinished_deployment(self):
        server = Server(self.root, "test")
        server.state["in_progress"] = {"phase": "switch"}
        with self.assertRaises(ValueError):
            server.snapshot()

    def test_backup_target_must_match_backend_database(self):
        server = Server(self.root, "test")
        (self.root / "config/mysql.cnf").write_text("[client]\nhost=127.0.0.1\nport=3306\nuser=backup\npassword=fake\n")
        (self.root / "config/backend.env").write_text("YUDAO_DB_URL=jdbc:mysql://127.0.0.1:3306/erp_production\n")
        real_stat = Path.stat

        def private_file(path, *args, **kwargs):
            values = list(real_stat(path, *args, **kwargs))
            values[0] = 0o100600
            return os.stat_result(values)

        with patch.object(Path, "stat", private_file):
            with self.assertRaisesRegex(ValueError, "differs"):
                server.database_arguments()
            (self.root / "config/backend.env").write_text("YUDAO_DB_URL=jdbc:mysql://127.0.0.1:3306/erp_test\n")
            arguments = server.database_arguments()
            self.assertIn("--host=127.0.0.1", arguments)
            self.assertFalse(any("fake" in a for a in arguments))


class VersionProof(unittest.TestCase):
    def setUp(self):
        self.server = FakeServer("unused")
        self.server.config = {"backend_port": 48081, "admin_port": 18080, "api_base_url": "https://api-test.example.com",
            "admin_url": "https://admin-test.example.com/admin/", "smoke_checks": []}
        self.server.verify_containers = lambda value: None
        self.receipt = {"release": release(1)["id"], "environment": "test"}
        self.wrong_backend = self.wrong_admin = False

    def response(self, url, headers=None):
        if url.endswith("/actuator/health"):
            return 200, {"status": "UP"}
        if url.endswith("/actuator/info"):
            receipt = {**self.receipt, "environment": "production"} if self.wrong_backend else self.receipt
            return 200, {"erp": receipt}
        if url.endswith("/release.json"):
            receipt = {**self.receipt, "release": release(2)["id"]} if self.wrong_admin else self.receipt
            return 200, receipt
        if url.endswith("/get-permission-info"):
            return 200, {"code": 401}
        return 200, None

    def test_health_requires_exact_backend_and_admin_receipts(self):
        with patch("server.http_json", side_effect=self.response):
            Server.healthy(self.server, release(1))

    def test_healthy_api_from_other_environment_is_rejected(self):
        self.wrong_backend = True
        with patch("server.http_json", side_effect=self.response):
            with self.assertRaisesRegex(ValueError, "another release or environment"):
                Server.healthy(self.server, release(1))

    def test_healthy_static_server_with_old_assets_is_rejected(self):
        self.wrong_admin = True
        with patch("server.http_json", side_effect=self.response):
            with self.assertRaisesRegex(ValueError, "another release or environment"):
                Server.healthy(self.server, release(1))


class LocalCleanup(unittest.TestCase):
    def test_previous_two_and_current_survive_failed_candidates_are_collected(self):
        with tempfile.TemporaryDirectory() as tmp:
            server = FakeServer(tmp)
            server.releases = {release(i)["id"]: release(i) for i in range(1, 6)}
            server.state.update(current=release(4)["id"], history=[release(3)["id"], release(2)["id"], release(1)["id"]])
            for value in server.releases.values():
                write_json(server.record(value["id"]) / "release.json", value)
            with patch("server.run", return_value="") as command:
                Server.local_cleanup(server)
            removed = [c.args[0][-1] for c in command.call_args_list if c.args[0][1:3] == ["image", "rm"]]
            expected = [ref for i in (1, 5) for ref in environment_images(release(i), "test").values()]
            self.assertCountEqual(removed, expected)


if __name__ == "__main__":
    unittest.main()
