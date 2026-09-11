import copy
from datetime import timedelta
import unittest

from fixtures import NOW, IMAGE, INDEX, digest, release, snapshots, inventory
from common import PACKAGES, deletion_plan, latest_release_plan, validate_release


class ReleaseValidation(unittest.TestCase):
    def test_complete_release(self):
        self.assertEqual(validate_release(release(1)), release(1))

    def test_latest_cannot_be_deployed(self):
        value = release(1)
        value["images"]["backend"] = "ghcr.io/" + PACKAGES["backend"] + ":latest"
        with self.assertRaises(ValueError):
            validate_release(value)

    def test_environment_package_swap_rejected(self):
        value = release(1)
        value["images"]["backend"] = value["images"]["admin"]["test"]
        with self.assertRaises(ValueError):
            validate_release(value)

    def test_test_cannot_point_at_production(self):
        value = release(1)
        value["config"]["test"]["api_base_url"] = value["config"]["production"]["api_base_url"]
        with self.assertRaises(ValueError):
            validate_release(value)

    def test_explicit_default_port_does_not_hide_production_origin(self):
        value = release(1)
        value["config"]["test"]["api_base_url"] = "https://API.VANZHOME.COM:443"
        with self.assertRaises(ValueError):
            validate_release(value)

    def test_run_attempt_mismatch_rejected(self):
        value = release(1)
        value["run_attempt"] = 2
        with self.assertRaises(ValueError):
            validate_release(value)

    def test_incomplete_frontend_pair_rejected(self):
        value = release(1)
        del value["images"]["admin"]["test"]
        with self.assertRaises((ValueError, KeyError)):
            validate_release(value)


class RetentionPolicy(unittest.TestCase):
    def test_only_latest_five_are_retained(self):
        plan = latest_release_plan([release(i) for i in range(1, 11)])
        self.assertEqual(plan['keep'], [release(i)['id'] for i in range(10, 5, -1)])
        self.assertEqual(len(plan['retire']), 5)

    def test_small_and_empty_catalogs(self):
        for count in range(6):
            self.assertEqual(latest_release_plan([release(i+1) for i in range(count)])['retire'], [])

    def test_same_day_builds_have_no_extra_grace(self):
        values = [release(i) for i in range(1, 11)]
        for value in values:
            value['created_at'] = NOW.isoformat()
        plan = latest_release_plan(values)
        self.assertEqual(plan['keep'], [release(i)['id'] for i in range(10, 5, -1)])

    def test_duplicate_or_invalid_release_blocks_selection(self):
        for values in ([release(1),release(1)], [dict(release(1),schema=9)]):
            with self.assertRaises(ValueError):
                latest_release_plan(values)


class OciDeletion(unittest.TestCase):
    def setUp(self):
        self.releases = [release(1), release(2)]
        self.versions, self.manifests = inventory(self.releases)
        self.package = PACKAGES["backend"]

    def plan(self, **kwargs):
        return deletion_plan(self.releases, {release(1)["id"]}, self.versions, self.manifests, **kwargs)

    def add_child(self, parent, child):
        self.manifests[(self.package, parent)] = {"mediaType": INDEX, "manifests": [{"digest": child}]}
        if (self.package, child) not in self.manifests:
            self.manifests[(self.package, child)] = {"mediaType": IMAGE}
            self.versions[self.package].append({"id": 1001, "name": child, "metadata": {"container": {"tags": []}}})

    def test_frontend_and_backend_retired_together(self):
        self.assertEqual(len(self.plan()), 3)

    def test_untagged_child_is_deleted_after_parent(self):
        self.add_child(digest(10), digest(1001))
        entries = [x for x in self.plan() if x["package"] == self.package]
        self.assertEqual([x["digest"] for x in entries], [digest(10), digest(1001)])

    def test_child_referenced_by_kept_index_survives(self):
        self.add_child(digest(10), digest(1001))
        self.add_child(digest(20), digest(1001))
        self.assertNotIn(digest(1001), [x["digest"] for x in self.plan()])

    def test_unknown_tag_is_a_pin(self):
        self.versions[self.package][0]["metadata"]["container"]["tags"].append("manual-rollback")
        self.assertNotIn(digest(10), [x["digest"] for x in self.plan()])

    def test_unknown_parent_preserves_managed_child(self):
        self.add_child(digest(1002), digest(10))
        self.versions[self.package].append({"id": 1002, "name": digest(1002), "metadata": {"container": {"tags": ["old-unmanaged"]}}})
        self.assertNotIn(digest(10), [x["digest"] for x in self.plan()])

    def test_orphan_untagged_manifest_is_preserved(self):
        self.manifests[(self.package, digest(1001))] = {"mediaType": IMAGE}
        self.versions[self.package].append({"id": 1001, "name": digest(1001), "metadata": {"container": {"tags": []}}})
        self.assertNotIn(digest(1001), [x["digest"] for x in self.plan()])

    def test_incomplete_inventory_blocks_deletion(self):
        self.manifests[(self.package, digest(10))] = {"mediaType": INDEX, "manifests": [{"digest": digest(1001)}]}
        with self.assertRaises(ValueError):
            self.plan()

    def test_unknown_media_type_blocks_deletion(self):
        self.manifests[(self.package, digest(10))] = {"mediaType": "unknown/artifact"}
        with self.assertRaises(ValueError):
            self.plan()

    def test_partial_delete_resumes_saved_child(self):
        self.add_child(digest(10), digest(1001))
        original = self.plan()
        self.versions[self.package] = [v for v in self.versions[self.package] if v["name"] != digest(10)]
        self.assertIn(digest(1001), [x["digest"] for x in self.plan(pending=original)])

    def test_saved_plan_cannot_delete_another_package(self):
        with self.assertRaises(ValueError):
            self.plan(pending=[{"package": "other/website", "digest": digest(1), "version_id": 1}])

    def test_retained_release_sharing_digest_is_kept(self):
        self.releases[1]["images"]["backend"] = self.releases[0]["images"]["backend"]
        self.assertNotIn(digest(10), [x["digest"] for x in self.plan()])


if __name__ == "__main__":
    unittest.main()
