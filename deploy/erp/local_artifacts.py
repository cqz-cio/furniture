"""Verify local BuildKit OCI outputs and assemble the exact test deployment archive."""
from contextlib import ExitStack
import hashlib
import io
import json
from pathlib import Path
import re
import tarfile
import zipfile

from common import PACKAGES, require, utcnow, validate_release, write_json


class OCI:
    def __init__(self, path):
        self.tar = tarfile.open(path, 'r:')
        self.members = {}
        try:
            for member in self.tar:
                name = member.name.rstrip('/')
                if member.isdir() and name in ('blobs', 'blobs/sha256'):
                    continue
                require(member.isfile() and name not in self.members and len(self.members) < 1024,
                        'Invalid or duplicate OCI member')
                require(name in ('index.json', 'oci-layout') or re.fullmatch(r'blobs/sha256/[0-9a-f]{64}', name),
                        'Unsafe OCI path')
                self.members[name] = member
                if name.startswith('blobs/'):
                    with self.tar.extractfile(member) as source:
                        require(hashlib.file_digest(source, 'sha256').hexdigest() == name.rsplit('/', 1)[1], 'Corrupt build blob')
            require(self.read_json('oci-layout') == {'imageLayoutVersion': '1.0.0'}, 'Invalid OCI layout')
            roots = self.read_json('index.json')['manifests']
            require(len(roots) == 1, 'Expected one platform; disable provenance/SBOM on this archive')
            self.root = roots[0]
            self.manifest = self.descriptor_json(self.root)
            require('layers' in self.manifest and 'config' in self.manifest, 'Expected single-platform image manifest')
            self.config = self.descriptor_json(self.manifest['config'])
            require(self.config.get('os') == 'linux' and self.config.get('architecture') == 'amd64', 'Wrong image platform')
            for entry in self.manifest['layers']:
                self.member(entry)
        except BaseException:
            self.tar.close()
            raise

    def close(self):
        self.tar.close()

    def read_json(self, name):
        require(self.members[name].size < 2 * 1024**2, 'Oversized OCI metadata')
        with self.tar.extractfile(self.members[name]) as source:
            return json.load(source)

    def member(self, descriptor):
        require(re.fullmatch(r'sha256:[0-9a-f]{64}', descriptor['digest']), 'Invalid descriptor digest')
        name = 'blobs/sha256/' + descriptor['digest'][7:]
        require(name in self.members and self.members[name].size == descriptor['size'], 'Missing OCI content')
        return self.members[name]

    def descriptor_json(self, descriptor):
        return self.read_json(self.member(descriptor).name)

    def files(self, wanted):
        result, total = {}, 0
        for descriptor in self.manifest['layers']:
            with self.tar.extractfile(self.member(descriptor)) as stream, tarfile.open(fileobj=stream, mode='r|*') as layer:
                for member in layer:
                    name = member.name.removeprefix('./')
                    # OCI overlay deletions must remove artifacts from earlier layers.
                    parent, _, base = name.rpartition('/')
                    if base == '.wh..wh..opq':
                        prefix = parent + '/' if parent else ''
                        result = {key: value for key, value in result.items() if not key.startswith(prefix)}
                        continue
                    if base.startswith('.wh.'):
                        removed = (parent + '/' if parent else '') + base[4:]
                        result = {key: value for key, value in result.items()
                                  if key != removed and not key.startswith(removed + '/')}
                        continue
                    if not wanted(name):
                        continue
                    require(member.isfile() and member.size <= 128 * 1024**2, 'Invalid application artifact')
                    total += member.size
                    require(total <= 256 * 1024**2, 'Oversized application artifacts')
                    with layer.extractfile(member) as source:
                        result[name] = source.read()
        return result


def migration_identity(repository):
    folder = Path(repository) / 'yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations'
    files = sorted(folder.glob('V*__*.sql'))
    require(files, 'No database migrations')
    digest = hashlib.sha256()
    for path in files:
        digest.update(path.name.encode() + b'\0' + path.read_bytes().replace(b'\r\n', b'\n'))
    return files, max(int(re.match(r'V(\d+)__', p.name)[1]) for p in files), digest.hexdigest()


def verify_images(backend, admin, repository, commit, release_id, api):
    for image, port in ((backend, '48080/tcp'), (admin, '80/tcp')):
        config = image.config['config']
        require(config.get('Labels', {}).get('org.opencontainers.image.revision') == commit, 'Image is from another commit')
        require(port in config.get('ExposedPorts', {}), 'Unexpected service port')
    require(backend.config['config'].get('User') == 'yudao', 'Backend must run as its non-root user')
    require(backend.config['config']['Labels'].get('io.oakved.image.layout') == 'spring-boot-tools-v1', 'Unsupported backend layout')
    content = backend.files(lambda name: name == 'opt/yudao/app.jar')
    require('opt/yudao/app.jar' in content, 'Backend application JAR missing')
    with zipfile.ZipFile(io.BytesIO(content['opt/yudao/app.jar'])) as jar:
        files, _, _ = migration_identity(repository)
        for path in files:
            require(jar.read('db/migration/' + path.name).replace(b'\r\n', b'\n') == path.read_bytes().replace(b'\r\n', b'\n'),
                    'Packaged migration differs from checked source')
    prefix = 'usr/share/nginx/html/admin/'
    content = admin.files(lambda name: name in (prefix+'index.html', prefix+'release.json') or
                         (name.startswith(prefix+'assets/') and name.endswith('.js')))
    require(content.get(prefix+'index.html'), 'Admin index missing')
    require(json.loads(content[prefix+'release.json']) == {'release': release_id, 'environment': 'test'}, 'Wrong admin release receipt')
    scripts = [v for k, v in content.items() if k.endswith('.js')]
    require(scripts and any(api.encode() in value for value in scripts), 'Test API absent from admin bundle')
    require(all(b'https://api.vanzhome.com' not in value for value in scripts), 'Production API present in test bundle')


def assemble(backend_path, admin_path, destination, repository, identity, api):
    destination = Path(destination)
    require(not destination.exists(), 'Complete artifact directory already exists')
    destination.mkdir(parents=True)
    with ExitStack() as stack:
        images = []
        for path in (backend_path, admin_path):
            image = OCI(path)
            stack.callback(image.close)
            images.append(image)
        backend, admin = images
        verify_images(backend, admin, repository, identity['commit'], identity['id'], api)
        _, version, migrations_hash = migration_identity(repository)
        refs = ['localhost/' + PACKAGES[k] + '@' + image.root['digest'] for k, image in zip(('backend','admin'), images)]
        manifest = validate_release({**identity, 'schema': 2, 'environment': 'test', 'delivery': 'local-build-scp',
            'repository': 'cqz-cio/furniture', 'created_at': utcnow().isoformat(), 'platform': 'linux/amd64',
            'database_version': version, 'migrations_hash': migrations_hash,
            'images': {'backend': refs[0], 'admin': {'test': refs[1]}},
            'config': {'test': {'api_base_url': api, 'storefront_url': api}}})
        archive = destination/'images.oci.tar'
        with tarfile.open(archive, 'w', format=tarfile.USTAR_FORMAT) as output:
            def add(name, data):
                entry = tarfile.TarInfo(name); entry.size = len(data); entry.mode = 0o600
                output.addfile(entry, io.BytesIO(data))
            add('oci-layout', b'{"imageLayoutVersion":"1.0.0"}')
            roots = [{**image.root, 'annotations': {'io.containerd.image.name': ref}} for image, ref in zip(images, refs)]
            add('index.json', json.dumps({'schemaVersion': 2, 'manifests': roots}).encode())
            seen = set()
            for image in images:
                for name, member in image.members.items():
                    if not name.startswith('blobs/') or name in seen:
                        continue
                    seen.add(name)
                    entry = tarfile.TarInfo(name); entry.size = member.size; entry.mode = 0o600
                    with image.tar.extractfile(member) as source:
                        output.addfile(entry, source)
        with archive.open('rb') as source:
            checksum = hashlib.file_digest(source, 'sha256').hexdigest()
        header = {'release': manifest, 'environment': 'test', 'bytes': archive.stat().st_size, 'sha256': checksum}
        write_json(destination/'release.json', manifest)
        write_json(destination/'header.json', header)
        return manifest
