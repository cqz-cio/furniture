"""Manual production preparation: publish verified cached bytes, never rebuild."""
import argparse
import json
import os
from pathlib import Path

from common import RELEASE, require, production_release, validate_release, write_json
from github_release import GitHub
from image_relay import validate_archive
from local_ci import verify_upstream
from registry_push import Publisher


def existing_release(client, ident):
    try:
        return client.release(ident)[1]
    except RuntimeError as error:
        if str(error) == 'GitHub GET failed: HTTP 404':
            return None
        raise


def prepare(client, cache, ident, operation, publisher=None):
    require(RELEASE.fullmatch(ident), 'Invalid release ID')
    require(operation in ('preflight', 'deploy', 'rollback'), 'Invalid operation')
    existing = existing_release(client, ident)
    if existing is not None:
        client.verify_ci(existing)
        if operation != 'rollback':
            require(client.test_passed(existing), 'This exact build has not passed test CD')
        return existing
    require(operation != 'rollback', 'Rollback requires an already published release; no build or publication on rollback')
    cache = Path(cache).resolve()
    complete = cache/ident/'complete'
    require(complete.resolve().is_relative_to(cache) and not complete.is_symlink(), 'Unsafe cache directory')
    for name in ('release.json', 'production.oci.tar'):
        path = complete/name
        require(path.is_file() and not path.is_symlink(), 'Complete production artifact is missing; run shared CI')
    source = validate_release(json.loads((complete/'release.json').read_text(encoding='utf-8')))
    require(source['id'] == ident and source['repository'] == client.repository, 'Cache identity mismatch')
    manifest = production_release(source)
    verify_upstream(client, source['ci']['run_id'], source['ci']['run_attempt'], source['commit'])
    client.verify_local_build(source)
    require(client.test_passed(source), 'This exact build has not passed test CD')
    references = [manifest['images']['backend'], manifest['images']['admin']['test'], manifest['images']['admin']['production']]
    archive = complete/'production.oci.tar'
    validate_archive(archive, references)
    publisher = publisher or Publisher(os.environ['GITHUB_ACTOR'], os.environ['GH_TOKEN'])
    publisher.archive(archive, references, ident)
    # Re-check after upload: a newer failed test cannot be hidden by a prior pass.
    require(client.test_passed(source), 'Test result changed while publishing; production release not registered')
    client.publish(manifest)
    return manifest


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--release', required=True)
    parser.add_argument('--operation', choices=('preflight','deploy','rollback'), required=True)
    parser.add_argument('--cache', required=True)
    parser.add_argument('--output', default='production-promotion-result.json')
    args = parser.parse_args()
    require(os.environ.get('GITHUB_ACTIONS') == 'true' and os.environ.get('GITHUB_EVENT_NAME') == 'workflow_dispatch'
        and os.environ.get('GITHUB_REF') == 'refs/heads/main' and os.environ.get('RUNNER_ENVIRONMENT') == 'self-hosted'
        and os.environ.get('GITHUB_REPOSITORY') == 'cqz-cio/furniture'
        and os.environ.get('GITHUB_WORKFLOW') == 'ERP CD - production', 'Use the manual production workflow on trusted main')
    try:
        if args.operation in ('deploy','rollback'):
            require(os.environ.get('ERP_CD_ENABLED') == 'true', 'Production CD has not been enabled')
        manifest = prepare(GitHub(), args.cache, args.release, args.operation)
        result = {'status':'published-and-verified','release_id':manifest['id'],
                  'source':'local-ci' if 'source_test' in manifest else 'legacy-ghcr', 'deployed':False}
        write_json(args.output, result)
        print(json.dumps(result), flush=True)
    except Exception as error:
        write_json(args.output, {'status':'failed','release_id':args.release,'error':str(error),'deployed':False})
        raise


if __name__ == '__main__':
    main()
