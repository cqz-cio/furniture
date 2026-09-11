"""Publish the completed build job's original images during CI, without deploying."""
import argparse
import json
import os
from pathlib import Path

from cache_retention import cache_lock
from common import RELEASE, require, production_release, validate_release, write_json
from github_release import GitHub
from image_relay import validate_archive
from local_ci import verify_upstream
from registry_push import Publisher


def verify_build_job(client, source):
    # The full workflow is still running. Require its *build job* to have passed;
    # CD later requires this exact workflow attempt (including upload) to succeed.
    run_id, attempt = source['run_id'], source['run_attempt']
    run = client.request(client.repo(f'/actions/runs/{run_id}/attempts/{attempt}'))
    require(run['head_sha'] == source['commit'] and run['head_branch'] == 'main'
        and run['head_repository']['full_name'] == client.repository
        and run['event'] == 'workflow_run'
        and run['path'].split('@')[0] == '.github/workflows/erp-local-ci.yml', 'Untrusted local CI run')
    jobs = []
    for page in range(1, 21):
        batch = client.request(client.repo(f'/actions/runs/{run_id}/attempts/{attempt}/jobs?per_page=100&page={page}'))['jobs']
        jobs.extend(batch)
        if len(batch) < 100:
            break
    else:
        raise ValueError('Incomplete local CI job inventory')
    build = [job for job in jobs if job['name'] == 'local-build']
    require(len(build) == 1 and build[0]['conclusion'] == 'success', 'Local build job has not succeeded')


def publish(client, cache, ident, publisher=None, env=None):
    env = os.environ if env is None else env
    require(RELEASE.fullmatch(ident), 'Invalid release ID')
    require(env.get('GITHUB_ACTIONS') == 'true' and env.get('GITHUB_EVENT_NAME') == 'workflow_run'
        and env.get('GITHUB_REF') == 'refs/heads/main' and env.get('RUNNER_ENVIRONMENT') == 'self-hosted'
        and env.get('GITHUB_REPOSITORY') == client.repository == 'cqz-cio/furniture'
        and env.get('GITHUB_WORKFLOW') == 'ERP local image CI' and env.get('GITHUB_JOB') == 'publish-images',
        'Only the automatic local CI publication job may upload images')
    cache = Path(cache).resolve()
    with cache_lock(cache):
        complete = cache/ident/'complete'
        require(complete.resolve().is_relative_to(cache) and not complete.is_symlink(), 'Unsafe cache directory')
        for name in ('release.json', 'production.oci.tar'):
            path = complete/name
            require(path.is_file() and not path.is_symlink(), 'Complete production artifact is missing; run shared CI')
        source = validate_release(json.loads((complete/'release.json').read_text(encoding='utf-8')))
        require(source['id'] == ident and source['repository'] == client.repository, 'Cache identity mismatch')
        require(str(source['run_id']) == env.get('GITHUB_RUN_ID')
            and str(source['run_attempt']) == env.get('GITHUB_RUN_ATTEMPT')
            and source['commit'] == env.get('ERP_SOURCE_SHA'),
            'Artifact belongs to another CI attempt; use Re-run all jobs to build and publish a new release')
        manifest = production_release(source)
        verify_upstream(client, source['ci']['run_id'], source['ci']['run_attempt'], source['commit'])
        verify_build_job(client, source)
        existing = None
        try:
            existing = client.release(ident)[1]
        except RuntimeError as error:
            if str(error) != 'GitHub GET failed: HTTP 404':
                raise
        if existing is not None:
            require(existing == manifest, 'Published release has different content')
        references = [manifest['images']['backend'], manifest['images']['admin']['test'], manifest['images']['admin']['production']]
        archive = complete/'production.oci.tar'
        validate_archive(archive, references)
        publisher = publisher or Publisher(env['GITHUB_ACTOR'], env['GH_TOKEN'])
        publisher.archive(archive, references, ident)
        if existing is None:
            client.publish(manifest)
        return manifest


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--release', required=True)
    parser.add_argument('--cache', required=True)
    args = parser.parse_args()
    output = Path('ci-image-publication-result.json')
    try:
        manifest = publish(GitHub(), args.cache, args.release)
        result = {'status':'published-and-verified', 'release_id':manifest['id'], 'deployed':False}
        write_json(output, result)
        if os.environ.get('GITHUB_STEP_SUMMARY'):
            with open(os.environ['GITHUB_STEP_SUMMARY'], 'a', encoding='utf-8') as summary:
                summary.write(f'### CI images uploaded to GHCR\n\nRelease: `{manifest["id"]}`\n\n'
                    'No deployment performed. After this workflow succeeds, deploy to test manually. '
                    'Production CD uses GHCR directly and still requires test success.\n')
        print(json.dumps(result), flush=True)
    except Exception as error:
        write_json(output, {'status':'failed','release_id':args.release,'error':str(error),'deployed':False})
        raise


if __name__ == '__main__':
    main()
