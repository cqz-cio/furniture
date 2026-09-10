import copy
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import unittest
from unittest.mock import Mock, patch

from fixtures import release
from bootstrap_image import verify_image
from common import environment_images, fingerprint
from image_archive import build_archive, digest
from image_relay import cache_ready, receive, validate_archive
from runner import RELAY_BOOTSTRAP, preload_test_images, transport


class MemoryRegistry:
    """A valid empty OCI image with a nested index, used only for cache probes."""
    def __init__(self):
        self.release = release(1)
        self.release['repository'] = 'cqz-cio/furniture'
        self.release['config']['test'] = {'api_base_url': 'http://124.220.2.69', 'storefront_url': 'http://124.220.2.69'}
        config = json.dumps({'architecture': 'amd64', 'os': 'linux', 'rootfs': {'type': 'layers', 'diff_ids': []}, 'history': [],
            'config': {'Cmd': ['/never-started'], 'Labels': {'oakved.purpose': 'cd-relay-import-probe',
                'org.opencontainers.image.revision': self.release['commit']}}}).encode()
        self.config_digest = digest(config)
        manifest = json.dumps({'schemaVersion': 2, 'mediaType': 'application/vnd.oci.image.manifest.v1+json',
            'config': {'mediaType': 'application/vnd.oci.image.config.v1+json', 'digest': digest(config), 'size': len(config)}, 'layers': []}).encode()
        self.manifest_digest = digest(manifest)
        index = json.dumps({'schemaVersion': 2, 'mediaType': 'application/vnd.oci.image.index.v1+json', 'manifests': [
            {'mediaType': 'application/vnd.oci.image.manifest.v1+json', 'size': len(manifest), 'digest': digest(manifest),
             'platform': {'architecture': 'amd64', 'os': 'linux'}}]}).encode()
        self.root_digest = digest(index)
        self.objects = {digest(v): v for v in (config, manifest, index)}
        self.release['images']['backend'] = 'ghcr.io/cqz-cio/furniture-erp-backend@' + digest(index)
        self.release['images']['admin']['test'] = 'ghcr.io/cqz-cio/furniture-erp-admin@' + digest(index)
        self.corrupt = False

    def manifest(self, package, ref):
        return self.objects[ref]

    def blob(self, package, ref, offset=0):
        data = b'\0' * len(self.objects[ref]) if self.corrupt else self.objects[ref]
        response = io.BytesIO(data[offset:])
        response.status = 206 if offset else 200
        response.headers = {'Content-Range': f'bytes {offset}-{len(data)-1}/{len(data)}'} if offset else {}
        return response


class ArchiveTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root, self.registry = Path(self.directory.name), MemoryRegistry()

    def build(self):
        with patch('builtins.print'):
            return build_archive(self.registry.release, 'test', self.root / 'archive', self.registry)

    def test_named_release_digests_survive_oci_packaging_and_validation(self):
        archive, header = self.build()
        validate_archive(archive, environment_images(self.registry.release, 'test').values())
        self.assertEqual(header['sha256'], digest(archive.read_bytes())[7:])
        self.assertEqual(header['bytes'], archive.stat().st_size)

    def test_blob_corruption_is_rejected_before_transfer(self):
        self.registry.corrupt = True
        with self.assertRaisesRegex(ValueError, 'checksum mismatch'):
            self.build()

    def test_foreign_archive_image_name_is_rejected(self):
        archive, _ = self.build()
        with self.assertRaisesRegex(ValueError, 'names differ'):
            validate_archive(archive, ['ghcr.io/someone/other@' + self.registry.root_digest])

    def test_tar_path_traversal_or_symlink_is_rejected_without_extraction(self):
        for name, kind in (('../escape', tarfile.REGTYPE), ('index.json', tarfile.SYMTYPE)):
            path = self.root / 'bad.tar'
            with tarfile.open(path, 'w') as output:
                entry = tarfile.TarInfo(name)
                entry.type, entry.linkname = kind, '/etc/passwd'
                output.addfile(entry, io.BytesIO())
            with self.assertRaises(ValueError):
                validate_archive(path, [])

    def test_root_digest_cannot_be_relabelled_as_a_different_release(self):
        archive, _ = self.build()
        with tarfile.open(archive) as original:
            entries = [(copy.copy(member), original.extractfile(member).read()) for member in original]
        changed = self.root / 'changed.tar'
        names = []
        with tarfile.open(changed, 'w') as output:
            for member, raw in entries:
                if member.name == 'index.json':
                    index = json.loads(raw)
                    for root in index['manifests']:
                        old = root['annotations']['io.containerd.image.name']
                        root['annotations']['io.containerd.image.name'] = old.split('@')[0] + '@sha256:' + 'f'*64
                        names.append(root['annotations']['io.containerd.image.name'])
                    raw = json.dumps(index).encode()
                    member.size = len(raw)
                output.addfile(member, io.BytesIO(raw))
        with self.assertRaisesRegex(ValueError, 'root digest differs'):
            validate_archive(changed, names)


class IdentityTests(unittest.TestCase):
    def test_both_docker_id_formats_require_exact_repo_digest_and_revision(self):
        registry = MemoryRegistry()
        metadata = {key: getattr(registry, key) for key in ('config_digest', 'root_digest', 'manifest_digest')}
        ref = registry.release['images']['backend']
        info = {'Id': registry.root_digest, 'RepoDigests': [ref], 'Architecture': 'amd64', 'Os': 'linux',
                'Config': {'Labels': {'org.opencontainers.image.revision': registry.release['commit']}}}
        for identifier in metadata.values():
            verify_image({**info, 'Id': identifier}, ref, metadata, registry.release['commit'])
        for wrong in ({**info, 'RepoDigests': []}, {**info, 'Id': 'sha256:'+'f'*64}, {**info, 'Architecture': 'arm64'}):
            with self.assertRaises(ValueError):
                verify_image(wrong, ref, metadata, registry.release['commit'])
        with self.assertRaisesRegex(ValueError, 'commit differs'):
            verify_image(info, ref, metadata, 'wrong-commit')


class RelayTransportTests(unittest.TestCase):
    def test_verified_cache_avoids_downloading_and_duplicate_disk_budget(self):
        value = MemoryRegistry().release
        with tempfile.TemporaryDirectory() as directory, patch('builtins.print'), \
                patch('runner.transport', return_value={'status': 'images-present', 'release_hash': fingerprint(value)}), \
                patch('image_archive.build_archive') as build:
            preload_test_images(['ssh', 'unused'], value, directory)
            build.assert_not_called()

    def test_unverified_cache_receipt_cannot_skip_image_transfer(self):
        value = MemoryRegistry().release
        with tempfile.TemporaryDirectory() as directory, \
                patch('runner.transport', return_value={'status': 'images-present', 'release_hash': 'wrong'}), \
                patch('image_archive.build_archive') as build:
            with self.assertRaisesRegex(ValueError, 'cache receipt'):
                preload_test_images(['ssh', 'unused'], value, directory)
            build.assert_not_called()

    def test_partial_or_wrong_revision_cache_is_not_ready(self):
        value = MemoryRegistry().release
        commands = Mock()
        commands.run.side_effect = ValueError('image missing')
        self.assertFalse(cache_ready(value, commands))
        commands.run.side_effect = None
        commands.run.return_value = json.dumps([{'RepoDigests': list(environment_images(value, 'test').values()),
            'Architecture': 'amd64', 'Os': 'linux', 'Config': {'Labels': {'org.opencontainers.image.revision': 'wrong'}}}])
        self.assertFalse(cache_ready(value, commands))

    @unittest.skipIf(os.name == 'nt', 'select on pipe file descriptors is Linux-only')
    def test_transfer_preserves_all_bytes_and_detects_a_truncated_pipe(self):
        for data, expected in ((b'\x00\xffbinary\nbytes', 14), (b'short', 20)):
            with tempfile.TemporaryDirectory() as directory:
                read, write = os.pipe()
                os.write(write, data)
                os.close(write)
                with os.fdopen(read, 'rb', buffering=0) as stream:
                    if expected == len(data):
                        self.assertEqual(receive(stream, Path(directory)/'archive', expected), digest(data)[7:])
                    else:
                        with self.assertRaisesRegex(ValueError, 'truncated'):
                            receive(stream, Path(directory)/'archive', expected)

    def test_binary_transport_uses_input_file_without_json_conversion(self):
        import base64
        with tempfile.TemporaryDirectory() as directory, patch('builtins.print'):
            path = Path(directory)/'binary'
            data = bytes(range(256))*40
            module = "import os,json\ndef main(request):\n data=os.read(0,10240)\n print('ERP_CD_RESULT='+json.dumps({'bytes':len(data),'first':data[0],'last':data[-1]}))\n"
            bundle = {'entry': 'probe', 'request': {}, 'modules': {'probe': base64.b64encode(module.encode()).decode()}}
            raw = json.dumps(bundle).encode()
            path.write_bytes(len(raw).to_bytes(8,'big') + raw + data)
            result = transport([sys.executable,'-c',RELAY_BOOTSTRAP], {}, directory, 5, input_path=path)
            self.assertEqual(result, {'bytes': len(data), 'first': 0, 'last': 255})
