"""Verify real Boot extraction, unchanged dependencies, and both migration layouts.

Runs without starting ERP or connecting to a database. The CI build calls this
against its freshly built JAR before the Docker image is published.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

from bootstrap_image import extract_migrations
from common import require


def file_hash(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--migrations', type=Path, required=True)
    args = parser.parse_args()
    java_home = Path(os.environ['JAVA_HOME']) if os.environ.get('JAVA_HOME') else None
    java = str(java_home / 'bin' / ('java.exe' if os.name == 'nt' else 'java')) if java_home else 'java'
    javac = str(java_home / 'bin' / ('javac.exe' if os.name == 'nt' else 'javac')) if java_home else 'javac'
    checksum = hashlib.sha256()
    migrations = sorted(args.migrations.glob('V[0-9][0-9][0-9]__*.sql'))
    require(migrations, 'Missing source migrations')
    for path in migrations:
        checksum.update(path.name.encode() + b'\0' + path.read_bytes().replace(b'\r\n', b'\n'))
    release = {'database_version': int(migrations[-1].name[1:4]), 'migrations_hash': checksum.hexdigest()}
    with tempfile.TemporaryDirectory(prefix='erp-image-layers-', dir=args.jar.resolve().parent) as directory:
        root = Path(directory)
        shutil.copyfile(args.jar, root / 'app.jar')
        print('Extracting the real backend JAR with Spring Boot tools mode.', flush=True)
        subprocess.run([java, '-Djarmode=tools', '-jar', 'app.jar', 'extract', '--layers', '--destination', 'extracted'], cwd=root, check=True, timeout=60)
        layers = root / 'extracted'
        flat = root / 'flat'
        sizes = {}
        for name in ('dependencies', 'spring-boot-loader', 'snapshot-dependencies', 'application'):
            source = layers / name
            require(source.is_dir(), 'Missing Boot layer: ' + name)
            sizes[name] = sum(p.stat().st_size for p in source.rglob('*') if p.is_file())
            shutil.copytree(source, flat, dirs_exist_ok=True, copy_function=os.link)
        with zipfile.ZipFile(args.jar) as original:
            libraries = {Path(n).name: hashlib.sha256(original.read(n)).hexdigest() for n in original.namelist()
                         if n.startswith('BOOT-INF/lib/') and n.endswith('.jar')}
        actual = {p.name: file_hash(p) for p in (flat / 'lib').glob('*.jar')}
        require(libraries == actual, 'Layer extraction changed or lost backend dependencies')
        runtime = root / 'migration-runtime'
        runtime.mkdir()
        shutil.copytree(flat / 'lib', runtime / 'lib', copy_function=os.link)
        # This is a disposable CI verification directory, not the deployment
        # host. Avoid applying the server's 10 GiB reserve to a hosted runner.
        def ci_space(paths, extra):
            require(shutil.disk_usage(root).free > extra + 128 * 1024**2, 'Insufficient CI verification space')
        extract_migrations(flat / 'app.jar', runtime, release, layered=True, space_check=ci_space)
        require(len(list((runtime / 'sql/db/migration').glob('V*.sql'))) == len(migrations), 'Lost migration files')
        # Java resolves the transformed JAR's Class-Path entries. Do not invoke
        # the application main method or run static initializers/database code.
        probe = root / 'LayoutProbe.java'
        probe.write_text('public class LayoutProbe { public static void main(String[] args) throws Exception { '
            'Class.forName("cn.iocoder.yudao.server.YudaoServerApplication", false, LayoutProbe.class.getClassLoader())'
            '.getMethod("main", String[].class); System.out.println("ERP_LAYER_CLASSPATH_OK"); } }', encoding='utf-8')
        subprocess.run([javac, '--release', '17', str(probe)], check=True, timeout=20)
        subprocess.run([java, '-cp', str(root) + os.pathsep + str(flat / 'app.jar'), 'LayoutProbe'], check=True, timeout=20)
        print(json.dumps({'status': 'verified', 'layer_bytes_uncompressed': sizes, 'dependency_jars': len(actual),
                          'migration_version': release['database_version'], 'database_started': False}), flush=True)


if __name__ == '__main__':
    main()
