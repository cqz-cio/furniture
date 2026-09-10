import copy
import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch

from common import fingerprint
from github_release import GitHub
from image_relay import scp_directory
from local_test import LocalConnection, remote_directory
from test_image_relay import MemoryRegistry


class LocalSCPTests(unittest.TestCase):
    def connection(self, root):
        key, known = root / 'key', root / 'known'
        key.write_text('test-only')
        known.write_text('test-only')
        return LocalConnection(key, known, root / 'cache')

    def test_remote_paths_cannot_escape_or_inject_shell(self):
        self.assertEqual(remote_directory('/var/tmp/oakved-local-scp-abcd1234'), '/var/tmp/oakved-local-scp-abcd1234')
        for path in ('/var/tmp', '/var/tmp/oakved-local-scp-abcd1234/../x', '/tmp/oakved-local-scp-abcd1234',
                     '/var/tmp/oakved-local-scp-abcd1234;id', None):
            with self.subTest(path=path), self.assertRaises(ValueError):
                remote_directory(path)

    def test_cached_archive_is_bound_to_manifest_and_hash(self):
        with tempfile.TemporaryDirectory() as tmp:
            connection = self.connection(Path(tmp))
            registry = MemoryRegistry()
            from image_archive import build_archive
            with patch('local_test.build_archive', side_effect=lambda r,e,d: build_archive(r,e,d,registry)) as build:
                archive, header = connection.archive(registry.release)
                self.assertTrue(archive.is_file())
                self.assertEqual(connection.archive(registry.release), (archive, header))
                self.assertEqual(build.call_count, 1)
                archive.write_bytes(b'x' * archive.stat().st_size)
                with self.assertRaisesRegex(ValueError, 'corrupt'):
                    connection.archive(registry.release)

    def test_failed_scp_cleans_staging_and_never_imports(self):
        with tempfile.TemporaryDirectory() as tmp:
            connection = self.connection(Path(tmp))
            release = MemoryRegistry().release
            replies = [dict(status='images-missing', release_hash=fingerprint(release)),
                       dict(status='scp-staged', directory='/var/tmp/oakved-local-scp-abcd1234'), dict(status='scp-cleanup')]
            with patch.object(connection, 'receipt', side_effect=replies) as receipt, \
                 patch.object(connection, 'archive', return_value=(Path(tmp)/'archive', {'bytes':10})), \
                 patch.object(connection, 'transfer', side_effect=ValueError('upload failed')):
                with self.assertRaisesRegex(ValueError, 'upload failed'):
                    connection.preload([],release,tmp)
                self.assertEqual([c.args[2]['operation'] for c in receipt.call_args_list], ['probe','scp-stage','scp-cleanup'])

    def test_import_failure_cannot_be_reported_as_ready(self):
        with tempfile.TemporaryDirectory() as tmp:
            connection = self.connection(Path(tmp)); release = MemoryRegistry().release
            replies = [dict(status='images-missing',release_hash=fingerprint(release)),
                       dict(status='scp-staged',directory='/var/tmp/oakved-local-scp-abcd1234'),
                       dict(status='images-ready',release_hash='wrong'), dict(status='scp-cleanup')]
            with patch.object(connection,'receipt',side_effect=replies), patch.object(connection,'archive',return_value=(Path(tmp)/'archive',{'bytes':10})), patch.object(connection,'transfer'):
                with self.assertRaisesRegex(ValueError,'Imported'):
                    connection.preload([],release,tmp)

    def test_verified_remote_cache_skips_scp_and_local_download(self):
        with tempfile.TemporaryDirectory() as tmp:
            connection=self.connection(Path(tmp)); release=MemoryRegistry().release
            with patch.object(connection,'receipt',return_value=dict(status='images-present',release_hash=fingerprint(release))), patch.object(connection,'archive') as archive, patch.object(connection,'transfer') as transfer:
                connection.preload([],release,tmp)
                archive.assert_not_called(); transfer.assert_not_called()

    def test_local_success_record_does_not_fabricate_actions_run(self):
        with patch.dict(os.environ, {}, clear=True):
            client=GitHub('cqz-cio/furniture','test')
            with patch.object(client,'request') as request:
                client.deployment_status({'id':1},'success')
                self.assertNotIn('log_url',request.call_args.args[2])

    @unittest.skipUnless(os.name == 'posix', 'Server filesystem validation')
    def test_server_refuses_symlink_archive(self):
        with tempfile.TemporaryDirectory(prefix='oakved-local-scp-',dir='/var/tmp') as tmp:
            root=Path(tmp); target=root.parent/'never-read-target'
            (root/'images.oci.tar').symlink_to(target)
            with patch('pwd.getpwnam',return_value=Mock(pw_uid=os.getuid())):
                with self.assertRaisesRegex(ValueError,'Unsafe SCP archive'):
                    scp_directory(tmp)


if __name__ == '__main__':
    unittest.main()
