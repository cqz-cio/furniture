import copy
import gzip
import hashlib
import io
import json
import os
from pathlib import Path
import tarfile
import tempfile
import sys
import unittest
from types import SimpleNamespace
from unittest.mock import Mock, patch
import zipfile

from fixtures import release, snapshots, NOW
from common import environment_images, validate_release, deletion_plan
from local_ci import CHECKS, cleanup_plan, verify_upstream, command
from local_artifacts import OCI, assemble
from image_relay import validate_archive
from local_test import worker


def local_release(number=1):
    value = release(number)
    value.update(schema=2, environment='test', delivery='local-build-scp', repository='cqz-cio/furniture',
                 ci={'run_id': 123, 'run_attempt': 1})
    value['images']['backend'] = value['images']['backend'].replace('ghcr.io/', 'localhost/')
    value['images']['admin'] = {'test': value['images']['admin']['test'].replace('ghcr.io/', 'localhost/')}
    value['config'] = {'test': value['config']['test']}
    return value


def tar_bytes(files):
    result = io.BytesIO()
    with tarfile.open(fileobj=result, mode='w') as output:
        for name, data in files.items():
            member = tarfile.TarInfo(name)
            member.size = len(data)
            output.addfile(member, io.BytesIO(data))
    return result.getvalue()


def image(path, files, config, extra_layers=()):
    blobs = {}
    def descriptor(data, media):
        digest = 'sha256:' + hashlib.sha256(data).hexdigest()
        blobs['blobs/sha256/' + digest[7:]] = data
        return {'mediaType': media, 'digest': digest, 'size': len(data)}
    raw = [tar_bytes(files), *[tar_bytes(layer) for layer in extra_layers]]
    layers = [descriptor(gzip.compress(layer), 'application/vnd.oci.image.layer.v1.tar+gzip') for layer in raw]
    configuration = {'architecture': 'amd64', 'os': 'linux', 'config': config,
                     'rootfs': {'type': 'layers', 'diff_ids': ['sha256:'+hashlib.sha256(v).hexdigest() for v in raw]}}
    cfg = descriptor(json.dumps(configuration).encode(), 'application/vnd.oci.image.config.v1+json')
    manifest = descriptor(json.dumps({'schemaVersion': 2, 'config': cfg, 'layers': layers}).encode(),
                          'application/vnd.oci.image.manifest.v1+json')
    path.write_bytes(tar_bytes({'oci-layout': b'{"imageLayoutVersion":"1.0.0"}',
        'index.json': json.dumps({'schemaVersion': 2, 'manifests': [manifest]}).encode(), **blobs}))


class LocalReleaseTests(unittest.TestCase):
    def test_windows_legacy_console_can_stream_unicode_build_output(self):
        with tempfile.TemporaryDirectory() as directory:
            output = io.BytesIO()
            console = io.TextIOWrapper(output, encoding='gbk')
            with patch('sys.stdout', console):
                result = command([sys.executable,'-c',"import sys;sys.stdout.buffer.write('x\\u2009WARN\\n'.encode('utf-8'))"],
                                 directory, directory, 'unicode', 5)
                console.flush()
            self.assertEqual(result, 'x\u2009WARN')
            self.assertIn('\u2009WARN'.encode('utf-8'), output.getvalue())
    def test_server_never_pulls_a_local_manifest(self):
        from server import Server
        server = Server.__new__(Server)
        server.environment = 'test'
        server.images_preloaded = False
        with patch('server.pull_image') as pull:
            with self.assertRaisesRegex(ValueError, 'imported before deployment'):
                server.pull_images(local_release())
            pull.assert_not_called()

    def test_manual_retry_reads_local_manifest_and_checks_ci_without_registry_download(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            value = local_release()
            complete = root/value['id']/'complete'
            complete.mkdir(parents=True)
            (complete/'release.json').write_text(json.dumps(value))
            args = SimpleNamespace(local_built=True, operation='deploy', release=value['id'], cache=str(root),
                key='unused', known_hosts='unused', check_only=True)
            with patch.dict(os.environ, {'GH_TOKEN':'test-token'}, clear=True), patch('builtins.print'), \
                    patch('local_test.LocalConnection'), patch('local_test.tool'), patch('local_test.GitHub'), \
                    patch('local_ci.BuiltConnection') as connection, patch('local_ci.verify_upstream') as check, \
                    patch('local_test.build_archive') as download, patch('local_test.execute') as cloud:
                worker(args)
                check.assert_called_once()
                self.assertEqual(check.call_args.args[1:], (123,1,value['commit']))
                connection.return_value.archive.assert_called_once_with(value)
                download.assert_not_called()
                cloud.assert_not_called()
    def test_local_identity_is_test_only_and_never_ghcr_deletion_input(self):
        value = validate_release(local_release())
        self.assertTrue(environment_images(value, 'test')['erp-backend'].startswith('localhost/'))
        with self.assertRaisesRegex(ValueError, 'production'):
            environment_images(value, 'production')
        with self.assertRaisesRegex(ValueError, 'GHCR deletion'):
            deletion_plan([value], [], {}, {})
        for field, replacement in [('ci', {}), ('environment', 'production'), ('delivery', 'ghcr')]:
            with self.assertRaises(ValueError):
                validate_release({**value, field: replacement})

    def test_cleanup_keeps_five_plus_current_rollback_and_pins(self):
        records = [local_release(n) for n in range(1, 13)]
        snapshot = snapshots(current=1, rollback=(2,))['test']
        snapshot['pins'] = [local_release(3)['id']]
        with patch('local_ci.utcnow', return_value=NOW):
            self.assertEqual(set(cleanup_plan(records, snapshot)), {local_release(n)['id'] for n in (4,5,6,7)})
            for changes in ({'verified': False}, {'in_progress': 'deploying'}, {'checked_at': '2020-01-01T00:00:00Z'}):
                with self.assertRaises(ValueError):
                    cleanup_plan(records, {**snapshot, **changes})

    def test_required_ci_jobs_and_same_commit_are_mandatory(self):
        sha = 'a'*40
        run = {'head_sha': sha, 'head_branch': 'main', 'head_repository': {'full_name':'cqz-cio/furniture'},
               'event': 'push', 'conclusion': 'success', 'path': '.github/workflows/database-and-backend-ci.yml'}
        jobs = [{'name': name, 'conclusion': 'success'} for name in CHECKS]
        client = Mock()
        client.repo.side_effect = lambda value: value
        client.request.side_effect = [run, {'jobs': jobs}]
        self.assertEqual(verify_upstream(client, 12, 1, sha), {'run_id':12, 'run_attempt':1})
        for changes in ({'event':'pull_request'}, {'head_sha':'b'*40}, {'conclusion':'failure'},
                        {'head_repository':{'full_name':'foreign/fork'}}):
            client.request.side_effect = [{**run, **changes}]
            with self.assertRaises(ValueError):
                verify_upstream(client, 12, 1, sha)
        client.request.side_effect = [run, {'jobs':[dict(job, conclusion='skipped') for job in jobs]}]
        with self.assertRaisesRegex(ValueError, 'did not pass'):
            verify_upstream(client, 12, 1, sha)


class LocalArtifactTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.value = local_release()
        self.api = 'http://124.220.2.69'
        self.folder = self.root/'yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations'
        self.folder.mkdir(parents=True)
        (self.folder/'V001__sample.sql').write_bytes(b'SELECT 1;\n')
        jar = io.BytesIO()
        with zipfile.ZipFile(jar, 'w') as output:
            output.writestr('db/migration/V001__sample.sql', b'SELECT 1;\n')
        self.backend_files = {'opt/yudao/app.jar': jar.getvalue()}
        self.admin_files = {'usr/share/nginx/html/admin/index.html': b'<html/>',
            'usr/share/nginx/html/admin/release.json': json.dumps({'release':self.value['id'], 'environment':'test'}).encode(),
            'usr/share/nginx/html/admin/assets/index.js': self.api.encode()}
        self.labels = {'org.opencontainers.image.revision':self.value['commit'], 'io.oakved.image.layout':'spring-boot-tools-v1'}

    def build(self, extra_layers=()):
        image(self.root/'backend.tar', self.backend_files, {'User':'yudao','Labels':self.labels,'ExposedPorts':{'48080/tcp':{}}})
        image(self.root/'admin.tar', self.admin_files, {'Labels':self.labels,'ExposedPorts':{'80/tcp':{}}}, extra_layers)
        identity = {key: self.value[key] for key in ('id','commit','run_id','run_attempt','ci')}
        return assemble(self.root/'backend.tar', self.root/'admin.tar', self.root/'complete', self.root, identity, self.api)

    def test_exact_archives_pass_existing_server_validation(self):
        manifest = self.build()
        validate_archive(self.root/'complete/images.oci.tar', environment_images(manifest, 'test').values())
        header = json.loads((self.root/'complete/header.json').read_text())
        self.assertEqual(header['release'], manifest)
        self.assertEqual(header['sha256'], hashlib.sha256((self.root/'complete/images.oci.tar').read_bytes()).hexdigest())

    def test_wrong_api_or_commit_or_migration_blocks_artifact_publication(self):
        self.admin_files['usr/share/nginx/html/admin/assets/index.js'] += b';https://api.vanzhome.com'
        with self.assertRaisesRegex(ValueError, 'Production API'):
            self.build()
        self.assertFalse((self.root/'complete/release.json').exists())

    def test_deleted_receipt_cannot_pass_using_a_lower_layer(self):
        with self.assertRaises(KeyError):
            self.build([{'usr/share/nginx/html/admin/.wh.release.json':b''}])

    def test_wrong_packaged_migration_is_rejected(self):
        (self.folder/'V001__sample.sql').write_bytes(b'SELECT 2;\n')
        with self.assertRaisesRegex(ValueError, 'migration differs'):
            self.build()

    def test_corrupt_oci_blob_is_rejected(self):
        image(self.root/'bad.tar', {'x':b'content'}, {'Labels':self.labels})
        data = (self.root/'bad.tar').read_bytes()
        self.assertIn(b'linux', data)
        (self.root/'bad.tar').write_bytes(data.replace(b'linux',b'LINUX',1))
        with self.assertRaisesRegex(ValueError, 'Corrupt build blob'):
            OCI(self.root/'bad.tar')
