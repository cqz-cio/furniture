import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from cache_retention import cache_lock, clean_cache
from test_local_ci import local_release
from fixtures import release, inventory
from common import deletion_plan, PACKAGES
from retention import require_cleanup_context


class CacheRetention(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)/'cache'
        self.root.mkdir()
        self.report = Path(self.temp.name)/'report.json'

    def seed(self, number, production=True):
        value = local_release(number)
        folder = self.root/value['id']/'complete'
        folder.mkdir(parents=True)
        (folder/'release.json').write_text(json.dumps(value), encoding='utf-8')
        (folder/'header.json').write_text(json.dumps({'release':value,'bytes':1}), encoding='utf-8')
        (folder/'images.oci.tar').write_bytes(b'x')
        if production:
            (folder/'production.oci.tar').write_bytes(b'y')
        return folder

    def clean(self, apply=False):
        return clean_cache(self.root, apply, self.report)

    def test_preview_then_seven_to_five_complete_versions(self):
        folders = [self.seed(i, i % 2 == 0) for i in range(1,8)]
        plan = self.clean()
        self.assertEqual(len(plan['keep']),5)
        self.assertEqual(len(plan['retire']),2)
        self.assertGreater(plan['reclaimable_bytes'],0)
        self.assertTrue(all(path.exists() for path in folders))
        result = self.clean(True)
        self.assertEqual(set(result['removed']), {local_release(i)['id'] for i in (1,2)})
        self.assertTrue(all(path.exists() for path in folders[2:]))
        self.assertFalse(any(path.exists() for path in folders[:2]))
        self.assertEqual(self.clean(True)['removed'], [])

    def test_incomplete_build_and_unrelated_files_are_reported_and_preserved(self):
        folder = self.root/local_release(1)['id']
        folder.mkdir()
        (folder/'backend.oci.tar').write_bytes(b'x')
        (self.root/'other-project.txt').write_text('keep')
        result = self.clean(True)
        self.assertEqual(result['incomplete_skipped'], [folder.name])
        self.assertTrue((folder/'backend.oci.tar').exists())
        self.assertTrue((self.root/'other-project.txt').exists())

    def test_invalid_inventory_never_starts_deletion(self):
        folders = [self.seed(i) for i in range(1,8)]
        (folders[-1]/'images.oci.tar').write_bytes(b'changed')
        with self.assertRaisesRegex(ValueError,'header'):
            self.clean(True)
        self.assertTrue(all(path.exists() for path in folders))

    def test_unknown_file_blocks_deletion(self):
        folder = self.seed(1)
        (folder/'unexpected').write_bytes(b'x')
        with self.assertRaises(ValueError):
            self.clean(True)
        self.assertTrue((folder/'images.oci.tar').exists())

    def test_busy_cache_prevents_cleanup(self):
        self.seed(1)
        with cache_lock(self.root):
            with self.assertRaisesRegex(ValueError,'busy'):
                self.clean(True)

    def test_interrupted_delete_resumes_without_corrupting_complete_inventory(self):
        folders = [self.seed(i) for i in range(1,8)]
        original = Path.unlink
        def interrupt(path, *args, **kwargs):
            if path.name == 'production.oci.tar' and path.parent.name == 'retiring':
                raise OSError('simulated interruption')
            return original(path, *args, **kwargs)
        with patch.object(Path, 'unlink', interrupt):
            with self.assertRaisesRegex(OSError,'interruption'):
                self.clean(True)
        result = self.clean()
        self.assertEqual(len(result['resume']),1)
        result = self.clean(True)
        self.assertEqual(len(result['removed']),2)
        self.assertTrue(all(path.exists() for path in folders[2:]))
        self.assertEqual(self.clean()['retire'],[])

    def test_empty_retiring_directory_is_resumable(self):
        folder = self.root/local_release(1)['id']/'retiring'
        folder.mkdir(parents=True)
        self.assertEqual(self.clean(True)['removed'],[folder.parent.name])
        self.assertFalse(folder.parent.exists())

    def test_unknown_partial_retirement_is_not_deleted(self):
        folder = self.root/local_release(1)['id']/'retiring'
        folder.mkdir(parents=True)
        (folder/'unknown').write_text('keep')
        with self.assertRaises(ValueError):
            self.clean(True)
        self.assertTrue((folder/'unknown').exists())

    def test_symlink_cannot_escape_cache(self):
        outside = Path(self.temp.name)/'outside'
        outside.mkdir()
        link = self.root/local_release(1)['id']
        try:
            link.symlink_to(outside, target_is_directory=True)
        except OSError:
            self.skipTest('OS does not permit symlink creation')
        with self.assertRaises(ValueError):
            self.clean(True)
        self.assertTrue(outside.exists())


class StrictRegistryRetention(unittest.TestCase):
    def test_managed_old_pin_does_not_extend_latest_five(self):
        records = [release(1),release(2)]
        versions, manifests = inventory(records)
        versions[PACKAGES['backend']][0]['metadata']['container']['tags'].append('pin-old')
        actions = deletion_plan(records,{records[0]['id']},versions,manifests,strict=True)
        self.assertEqual(len(actions),3)

    def test_apply_requires_trusted_workflow_and_enabled_setting(self):
        valid = {'GITHUB_ACTIONS':'true','GITHUB_REF':'refs/heads/main',
                 'GITHUB_REPOSITORY':'cqz-cio/furniture','GITHUB_WORKFLOW':'ERP image retention',
                 'GITHUB_JOB':'clean','RUNNER_ENVIRONMENT':'self-hosted','ERP_IMAGE_CLEANUP_ENABLED':'true'}
        with patch.dict(os.environ, valid, clear=True):
            require_cleanup_context()
        for key in valid:
            with self.subTest(key=key), patch.dict(os.environ,{**valid,key:'wrong'},clear=True):
                with self.assertRaises(ValueError):
                    require_cleanup_context()
