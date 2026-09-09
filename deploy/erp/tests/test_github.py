import copy
import json
import os
import tempfile
import unittest
from unittest.mock import patch
from urllib.request import Request

from fixtures import release, snapshots, inventory, NOW
from common import fingerprint
from github_release import GitHub, SafeRedirect
from retention import clean


class GitHubValidation(unittest.TestCase):
    def setUp(self):
        self.client = GitHub("cqz-cio/erp", "fake-not-a-real-token")

    def test_full_pagination_is_used(self):
        with patch.object(self.client, "request", side_effect=[list(range(100)), [100]]) as request:
            self.assertEqual(len(self.client.pages("/items")), 101)
            self.assertTrue(request.call_args_list[1].args[0].endswith("page=2"))

    def test_non_list_pagination_fails(self):
        with patch.object(self.client, "request", return_value={"items": []}):
            with self.assertRaises(ValueError):
                self.client.pages("/items")

    def test_pr_branch_failed_or_wrong_workflow_is_not_a_release(self):
        expected = release(1)
        valid = {"head_sha": expected["commit"], "head_branch": "main", "head_repository": {"full_name": "cqz-cio/erp"},
            "conclusion": "success", "event": "push", "path": ".github/workflows/database-and-backend-ci.yml"}
        with patch.object(self.client, "request", return_value=valid):
            self.client.verify_ci(expected)
        for changed in ({"head_branch": "feature"}, {"event": "pull_request"}, {"conclusion": "failure"},
                        {"head_sha": "2"*40}, {"path": ".github/workflows/arbitrary.yml"}):
            with self.subTest(changed=changed), patch.object(self.client, "request", return_value={**valid, **changed}):
                with self.assertRaises(ValueError):
                    self.client.verify_ci(expected)

    def test_production_gate_checks_the_exact_manifest(self):
        expected = release(1)
        deployment = {"id": 1, "environment": "test", "task": "erp-cd",
            "payload": {"release_hash": fingerprint(expected), "release_id": expected["id"]}}
        with patch.object(self.client, "pages", side_effect=[[deployment], [{"state": "success"}]]):
            self.assertTrue(self.client.test_passed(expected))
        mutated = copy.deepcopy(expected)
        mutated["images"]["backend"] = release(2)["images"]["backend"]
        with patch.object(self.client, "pages", return_value=[deployment]):
            self.assertFalse(self.client.test_passed(mutated))

    def test_failed_latest_attempt_does_not_count_as_success(self):
        expected = release(1)
        deployment = {"id": 1, "environment": "test", "task": "erp-cd",
            "payload": {"release_hash": fingerprint(expected), "release_id": expected["id"]}}
        with patch.object(self.client, "pages", side_effect=[[deployment], [{"state": "failure"}, {"state": "success"}]]):
            self.assertFalse(self.client.test_passed(expected))

    def test_later_failed_deployment_supersedes_older_success(self):
        expected = release(1)
        deployment = {"id": 2, "environment": "test", "task": "erp-cd",
            "payload": {"release_hash": fingerprint(expected), "release_id": expected["id"]}}
        with patch.object(self.client, "pages", side_effect=[[deployment, {**deployment, "id": 1}], [{"state": "failure"}], [{"state": "success"}]]):
            self.assertFalse(self.client.test_passed(expected))

    def test_download_redirect_drops_authorization(self):
        request = Request("https://api.github.com/repos/cqz-cio/erp/releases/assets/1", headers={"Authorization": "Bearer fake"})
        redirect = SafeRedirect().redirect_request(request, None, 302, "", {}, "https://release-assets.githubusercontent.com/a")
        self.assertIsNone(redirect.get_header("Authorization"))
        with self.assertRaises(ValueError):
            SafeRedirect().redirect_request(request, None, 302, "", {}, "https://attacker.example/asset")


class FakeGitHub:
    def __init__(self):
        self.catalog = [release(i) for i in range(1, 11)]
        self.versions, self.manifests = inventory(self.catalog)
        self.mutations = []

    def repo(self, path):
        return "/repos/cqz-cio/erp" + path

    def pages(self, path):
        if path.endswith("/releases"):
            return [{"draft": False, "tag_name": r["id"]} for r in self.catalog]
        package = next(p for p in self.versions if p.split("/")[1] in path)
        return self.versions[package]

    def release(self, rid, allow_retired=False):
        value = next(r for r in self.catalog if r["id"] == rid)
        return {"id": value["run_id"], "assets": []}, value

    def request(self, path, method="GET", body=None, raw=False):
        if path == "/users/cqz-cio":
            return {"type": "User"}
        if method == "DELETE":
            self.mutations.append((method, path))
            return
        package = next(p for p in self.versions if p.split("/")[1] in path)
        return next(v for v in self.versions[package] if v["id"] == int(path.rsplit("/", 1)[1]))

    def add_asset(self, record, name, value):
        self.mutations.append((name, record["id"]))

    def manifest(self, package, digest):
        return self.manifests[(package, digest)]


class CleanupIntegration(unittest.TestCase):
    def setUp(self):
        self.client = FakeGitHub()
        self.write = patch("retention.write_json")
        self.write.start()
        self.addCleanup(self.write.stop)
        self.clock = patch("common.utcnow", return_value=NOW)
        self.clock.start()
        self.addCleanup(self.clock.stop)

    def test_dry_run_has_zero_remote_mutations(self):
        result = clean(self.client, self.client, snapshots())
        self.assertEqual(len(result["delete"]), 6)
        self.assertEqual(self.client.mutations, [])

    def test_apply_without_leases_has_zero_mutations(self):
        with patch.dict(os.environ, GITHUB_RUN_ID="1", GITHUB_RUN_ATTEMPT="1"):
            with self.assertRaises(ValueError):
                clean(self.client, self.client, snapshots(), apply=True)
        self.assertEqual(self.client.mutations, [])

    def test_apply_marks_retired_before_deleting_both_image_packages(self):
        with patch("retention.valid_leases") as leases:
            result = clean(self.client, self.client, snapshots(), apply=True)
        self.assertEqual([m[0] for m in self.client.mutations[:2]], ["retirement.json", "retirement.json"])
        self.assertEqual(len([m for m in self.client.mutations if m[0] == "DELETE"]), 6)
        self.assertEqual(leases.call_count, 9)

    def test_a_missing_server_cannot_delete_any_image(self):
        states = snapshots()
        del states["production"]
        with self.assertRaises(ValueError):
            clean(self.client, self.client, states, apply=True)
        self.assertEqual(self.client.mutations, [])


if __name__ == "__main__":
    unittest.main()
