"""Trusted Windows Runner: checked main commit -> verified local OCI images. No deployment."""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time

from bootstrap_policy import PROFILE
from cache_retention import cache_lock
from common import require, write_json
from github_release import GitHub
from local_artifacts import assemble
from local_test import LocalConnection, stop

HERE = Path(__file__).resolve().parent
CHECKS = {'verify-deployment-scripts', 'verify-database-and-backend', 'verify-erp-admin'}


def command(args, root, logs, label, seconds=30):
    """Bound every child; capture both streams, stream progress, kill its tree on silence."""
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    logs = Path(logs); logs.mkdir(parents=True, exist_ok=True)
    path = logs/(str(time.time_ns())+'-'+label+'.log')
    started = changed = time.monotonic(); offset = 0
    print(json.dumps({'stage': label, 'log': str(path), 'timeout_seconds': seconds}), flush=True)
    with path.open('wb') as output:
        child = subprocess.Popen(args, cwd=root, stdin=subprocess.DEVNULL, stdout=output, stderr=output)
        try:
            while True:
                size = path.stat().st_size
                if size != offset:
                    with path.open('rb') as source:
                        source.seek(offset)
                        print(source.read().decode('utf-8', errors='replace'), end='', flush=True)
                        offset = source.tell()
                    changed = time.monotonic()
                if child.poll() is not None and path.stat().st_size == offset:
                    break
                require(time.monotonic()-started < seconds, label+' exceeded its deadline')
                require(time.monotonic()-changed < 60, label+' made no progress for 60 seconds')
                time.sleep(.25)
            require(child.returncode == 0, label+' failed; see '+str(path))
        finally:
            stop(child)
            write_json(path.with_suffix('.json'), {'pid': child.pid, 'exit_code': child.returncode,
                'elapsed_seconds': round(time.monotonic()-started, 2), 'log': str(path)})
    return path.read_text(encoding='utf-8', errors='replace').strip()


def verify_upstream(client, run_id, attempt, commit):
    require(re.fullmatch(r'[0-9a-f]{40}', commit) and run_id > 0 and attempt > 0, 'Invalid CI identity')
    run = client.request(client.repo(f'/actions/runs/{run_id}/attempts/{attempt}'))
    require(run['head_sha'] == commit and run['head_branch'] == 'main'
        and run['head_repository']['full_name'] == 'cqz-cio/furniture'
        and run['event'] in ('push', 'workflow_dispatch') and run['conclusion'] == 'success'
        and run['path'].split('@')[0] == '.github/workflows/database-and-backend-ci.yml', 'Untrusted or failed upstream CI')
    jobs = []
    for page in range(1, 21):
        batch = client.request(client.repo(f'/actions/runs/{run_id}/attempts/{attempt}/jobs?per_page=100&page={page}'))['jobs']
        jobs.extend(batch)
        if len(batch) < 100:
            break
    else:
        raise ValueError('Incomplete upstream job inventory')
    for name in CHECKS:
        matches = [job for job in jobs if job['name'] == name]
        require(len(matches) == 1 and matches[0]['conclusion'] == 'success', 'Required CI check did not pass: '+name)
    return {'run_id': run_id, 'run_attempt': attempt}


class BuiltConnection(LocalConnection):
    ci_verified = True

    def archive(self, release):
        saved = self.cache/release['id']/'complete'
        require(saved.is_dir(), 'Local build archive missing; automatic registry download is disabled')
        return super().archive(release)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--ci-run', type=int, required=True)
    parser.add_argument('--ci-attempt', type=int, required=True)
    parser.add_argument('--commit', required=True)
    parser.add_argument('--cache', required=True)
    args = parser.parse_args()
    require(os.environ.get('GITHUB_ACTIONS') == 'true' and os.environ.get('RUNNER_ENVIRONMENT') == 'self-hosted'
        and os.environ.get('GITHUB_REPOSITORY') == 'cqz-cio/furniture' and os.environ.get('GITHUB_EVENT_NAME') == 'workflow_run',
        'Only the repository trusted workflow_run on the self-hosted Runner may execute this entry point')
    require(os.environ.get('RUNNER_OS') == 'Windows', 'This entry point requires the configured Windows Runner')
    repository = HERE.parents[1]
    cache = Path(args.cache).resolve(); cache.mkdir(parents=True, exist_ok=True)
    with cache_lock(cache):
        require(not cache.is_relative_to(repository), 'Persistent build cache must be outside the checkout')
        logs = cache/'logs'
        client = GitHub()
        ci = verify_upstream(client, args.ci_run, args.ci_attempt, args.commit)
        if client.request(client.repo('/git/ref/heads/main'))['object']['sha'] != args.commit:
            print('A newer main commit exists; skipping superseded build.', flush=True)
            return
        require(command(['git','rev-parse','HEAD'], repository, logs, 'checkout-identity') == args.commit, 'Checkout differs from passed CI')
        require(not command(['git','status','--porcelain','--untracked-files=all'], repository, logs, 'clean-checkout'), 'Runner checkout has local modifications')
        docker = shutil.which('docker')
        require(docker, 'Docker CLI is missing')
        require(command([docker,'info','--format','{{.OSType}}/{{.Architecture}}'], repository, logs, 'docker-engine') in ('linux/x86_64','linux/amd64'),
                'Start the local Docker Desktop Linux engine')
        require(shutil.disk_usage(cache).free > 12 * 1024**3, 'At least 12 GiB local free space required for build/export')
        build_id, build_attempt = os.environ['GITHUB_RUN_ID'], os.environ['GITHUB_RUN_ATTEMPT']
        require(build_id.isdigit() and build_attempt.isdigit() and int(build_id)>0 and int(build_attempt)>0, 'Invalid local build run')
        ident = f'cd-{args.commit}-{build_id}-{build_attempt}'
        identity = {'id': ident, 'commit': args.commit, 'run_id': int(build_id), 'run_attempt': int(build_attempt), 'ci': ci}
        release_root = cache/ident; release_root.mkdir(exist_ok=True)
        require(not (release_root/'complete').exists(), 'This build attempt already has an immutable archive')
        builder = 'oakved-local-ci'
        probe = subprocess.run([docker,'buildx','inspect',builder], capture_output=True, timeout=15)
        if probe.returncode:
            command([docker,'buildx','create','--name',builder,'--driver','docker-container'], repository, logs, 'create-builder')
        command([docker,'buildx','inspect',builder,'--bootstrap'], repository, logs, 'start-builder',120)
        paths = {'backend': repository/'yudao电商管理平台前后端/yudao-cloud', 'admin': repository/'yudao电商管理平台前后端/yudao-ui-admin-vue3',
                 'admin-production': repository/'yudao电商管理平台前后端/yudao-ui-admin-vue3'}
        api = 'http://' + PROFILE['host']
        for kind, context in paths.items():
            output = release_root/(kind+'.oci.tar')
            require(not output.exists(), 'Partial build already exists; use a new workflow attempt')
            build = [docker,'buildx','build','--builder',builder,'--platform','linux/amd64','--progress','plain',
                '--provenance=false','--sbom=false','--label','org.opencontainers.image.revision='+args.commit,
                '--label','org.opencontainers.image.source=https://github.com/cqz-cio/furniture',
                '--output','type=oci,dest='+str(output),'-f',str(context/('yudao-server/Dockerfile' if kind=='backend' else 'Dockerfile'))]
            if kind != 'backend':
                production = kind == 'admin-production'
                build_api = 'https://api.vanzhome.com' if production else api
                storefront = 'https://www.vanzhome.com' if production else api
                for key,value in {'ERP_RELEASE_ID':ident,'ERP_DEPLOY_ENVIRONMENT':'production' if production else 'test','VITE_BASE_URL':build_api,
                    'VITE_API_URL':'/admin-api','VITE_BASE_PATH':'/admin/','VITE_FURNITURE_WEB_URL':storefront,'VITE_MALL_H5_DOMAIN':storefront}.items():
                    build += ['--build-arg',key+'='+value]
            command([*build,str(context)], repository, logs, 'build-'+kind,1800)
        print(json.dumps({'stage':'verify-and-assemble-local-images'}), flush=True)
        temporary = release_root/'assembling'
        release = assemble(release_root/'backend.oci.tar', release_root/'admin.oci.tar', temporary, repository, identity, api, release_root/'admin-production.oci.tar')
        temporary.rename(release_root/'complete')
        for kind in paths:
            (release_root/(kind+'.oci.tar')).unlink()
        summary = {'release_id': ident, 'commit': args.commit, 'ci': ci, 'status': 'images-verified',
                   'archive': str(release_root/'complete/images.oci.tar'),
                   'production_archive': str(release_root/'complete/production.oci.tar')}
        write_json(cache/'latest-result.json', summary)
        if os.environ.get('GITHUB_OUTPUT'):
            with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as output:
                output.write('release_id='+ident+'\n')
        print(json.dumps(summary), flush=True)
        try:
            command([docker,'buildx','prune','--builder',builder,'--max-used-space','10GB','--force'],
                    repository, logs, 'trim-owned-build-cache', 120)
        except Exception as error:
            print('Build cache trimming skipped: '+type(error).__name__, flush=True)
        print('Build complete. CI will upload these images to GHCR next: '+ident, flush=True)
        if os.environ.get('GITHUB_STEP_SUMMARY'):
            with open(os.environ['GITHUB_STEP_SUMMARY'], 'a', encoding='utf-8') as output:
                output.write(f'### Local image build complete\n\nRelease: `{ident}`\n\nNo deployment performed. '
                             'Backend, test admin and production admin were built together. '
                             'Wait for the automatic GHCR upload and the entire CI workflow to succeed. '
                             'Then run **ERP CD - test** manually and enter this release ID. '
                             'After test succeeds, select the same ID in **ERP CD - production**.\n')


if __name__ == '__main__':
    main()
