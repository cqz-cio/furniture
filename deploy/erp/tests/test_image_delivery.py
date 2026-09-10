import hashlib
import io
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import urllib.error
import zipfile

from bootstrap_image import extract_migrations
from image_archive import digest, download_blob
from runner import image_transport


class MigrationLayouts(unittest.TestCase):
    def test_old_and_layered_images_produce_identical_migration_runtime(self):
        with tempfile.TemporaryDirectory() as directory, patch('bootstrap_image.capacity'):
            root = Path(directory)
            sql = {'V001__first.sql': b'SELECT 1;\r\n', 'V002__next.sql': b'SELECT 2;\n', 'B002__baseline.sql': b'SELECT 3;'}
            checksum = hashlib.sha256()
            for name in sorted(n for n in sql if n.startswith('V')):
                checksum.update(name.encode() + b'\0' + sql[name].replace(b'\r\n', b'\n'))
            release = {'database_version': 2, 'migrations_hash': checksum.hexdigest()}
            libraries = {'flyway-core-11.jar': b'core', 'flyway-mysql-11.jar': b'mysql', 'mysql-connector-j-9.jar': b'driver'}
            results = []
            for layered in (False, True):
                runtime = root / str(layered)
                runtime.mkdir()
                jar = runtime / 'backend.jar'
                with zipfile.ZipFile(jar, 'w') as archive:
                    prefix = '' if layered else 'BOOT-INF/classes/'
                    for name, content in sql.items():
                        archive.writestr(prefix + 'db/migration/' + name, content)
                    archive.writestr(prefix + 'application-prod.yaml', b'not part of migration runtime')
                    if not layered:
                        for name, content in libraries.items():
                            archive.writestr('BOOT-INF/lib/' + name, content)
                if layered:
                    (runtime / 'lib').mkdir()
                    for name, content in libraries.items():
                        (runtime / 'lib' / name).write_bytes(content)
                extract_migrations(jar, runtime, release, layered)
                results.append({p.relative_to(runtime).as_posix(): p.read_bytes() for p in runtime.rglob('*') if p.is_file() and p != jar})
            self.assertEqual(results[0], results[1])
            self.assertEqual(len(results[0]), 6)

    def test_changed_or_missing_migration_is_refused_before_extraction(self):
        for layered in (False, True):
            with self.subTest(layered=layered), tempfile.TemporaryDirectory() as directory, patch('bootstrap_image.capacity'):
                root = Path(directory)
                jar = root / 'app.jar'
                with zipfile.ZipFile(jar, 'w') as z:
                    z.writestr(('' if layered else 'BOOT-INF/classes/') + 'db/migration/V001__first.sql', b'SELECT 1;')
                with self.assertRaisesRegex(ValueError, 'differs'):
                    extract_migrations(jar, root, {'database_version': 1, 'migrations_hash': '0'*64}, layered)
                with self.assertRaisesRegex(ValueError, 'Incomplete'):
                    extract_migrations(jar, root, {'database_version': 2, 'migrations_hash': '0'*64}, layered)
                self.assertFalse((root / 'sql').exists())


class TransportConfiguration(unittest.TestCase):
    def test_test_defaults_to_relay_and_production_keeps_direct_pull(self):
        with patch.dict(os.environ, {}, clear=True):
            self.assertEqual(image_transport('test'), 'ssh')
            self.assertEqual(image_transport('production'), 'ghcr')
        with patch.dict(os.environ, {'ERP_IMAGE_TRANSPORT': 'ghcr'}):
            self.assertEqual(image_transport('test'), 'ghcr')

    def test_unknown_registry_or_typo_cannot_silently_select_ghcr(self):
        for mode in ('tcr', 'sah', 'https://registry.example'):
            with patch.dict(os.environ, {'ERP_IMAGE_TRANSPORT': mode}), self.assertRaises(ValueError):
                image_transport('test')
        with patch.dict(os.environ, {'ERP_IMAGE_TRANSPORT': 'ssh'}), self.assertRaises(ValueError):
            image_transport('production')


class BlobResume(unittest.TestCase):
    def run_download(self, responses):
        calls = []
        class Registry:
            def blob(self, package, ref, offset=0):
                calls.append(offset)
                value = responses.pop(0)
                if isinstance(value, Exception):
                    raise value
                return value
        with tempfile.TemporaryDirectory() as directory, patch('builtins.print'):
            path = Path(directory) / 'blob'
            download_blob(Registry(), 'package', digest(b'abcdef'), {'size': 6}, path, lambda: None, lambda count: None)
            return calls, path.read_bytes()

    def test_partial_blob_resumes_at_verified_offset(self):
        remainder = io.BytesIO(b'def')
        remainder.status = 206
        remainder.headers = {'Content-Range': 'bytes 3-5/6'}
        calls, content = self.run_download([io.BytesIO(b'abc'), remainder])
        self.assertEqual(calls, [0, 3])
        self.assertEqual(content, b'abcdef')

    def test_ignored_range_or_wrong_offset_is_not_appended(self):
        for status, header in ((200, ''), (206, 'bytes 0-5/6')):
            remainder = io.BytesIO(b'def')
            remainder.status, remainder.headers = status, {'Content-Range': header}
            with self.subTest(status=status), self.assertRaisesRegex(ValueError, 'resume range'):
                self.run_download([io.BytesIO(b'abc'), remainder])

    def test_corruption_is_not_retried(self):
        with self.assertRaisesRegex(ValueError, 'checksum mismatch'):
            self.run_download([io.BytesIO(b'badbad')])

    def test_transient_error_has_only_one_retry(self):
        with self.assertRaisesRegex(RuntimeError, 'download failed'):
            self.run_download([TimeoutError(), TimeoutError()])

    def test_authentication_failure_is_not_retried(self):
        with self.assertRaisesRegex(RuntimeError, 'download failed'):
            self.run_download([urllib.error.HTTPError('https://ghcr.io/', 403, 'Forbidden', {}, None)])
