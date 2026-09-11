import copy
import io
import hashlib
import json
import os
from pathlib import Path
import tempfile
import tarfile
import unittest
from unittest.mock import Mock, patch

import fixtures
from common import fingerprint, production_release, validate_release
from github_release import GitHub
from image_relay import validate_archive
from local_artifacts import assemble
from promote_local import prepare, main as promote_main
from registry_push import Publisher
import test_local_ci as local_fixtures
from test_local_ci import local_release, image, tar_bytes


def paired_release():
    value = local_release()
    value['production'] = {'admin_image': 'ghcr.io/cqz-cio/furniture-erp-admin@sha256:'+'e'*64,
        'api_base_url':'https://api.vanzhome.com','storefront_url':'https://www.vanzhome.com'}
    return value


class PromotionIdentity(unittest.TestCase):
    def test_production_preserves_both_digests_and_full_test_identity(self):
        source = paired_release()
        result = validate_release(production_release(source))
        self.assertEqual(result['source_test'], source)
        self.assertEqual(result['images']['backend'].split('@')[1], source['images']['backend'].split('@')[1])
        self.assertEqual(result['images']['admin']['production'], source['production']['admin_image'])
        self.assertEqual(result['id'], source['id'])

    def test_old_test_only_release_cannot_be_rebuilt_during_promotion(self):
        with self.assertRaisesRegex(ValueError, 'shared CI again'):
            production_release(local_release())

    def test_changed_backend_admin_or_origin_is_rejected(self):
        for field in ('backend','production','ci','source'):
            value = production_release(paired_release())
            if field == 'backend': value['images']['backend'] = value['images']['backend'][:-1]+'f'
            if field == 'production': value['images']['admin']['production'] = value['images']['admin']['production'][:-1]+'f'
            if field == 'ci': value['ci'] = {'run_id': 999, 'run_attempt':1}
            if field == 'source': value['source_test'] = {'schema':1}
            with self.subTest(field=field), self.assertRaises(ValueError):
                validate_release(value)

    def test_test_gate_uses_complete_local_manifest_not_just_commit(self):
        source = paired_release()
        client = GitHub(source['repository'], 'fake')
        record = {'id':1, 'environment':'test','task':'erp-cd',
                  'payload':{'release_hash':fingerprint(source),'release_id':source['id']}}
        with patch.object(client, 'pages', side_effect=[[record],[{'state':'success'}]]):
            self.assertTrue(client.test_passed(production_release(source)))
        changed = copy.deepcopy(source)
        changed['production']['admin_image'] = changed['production']['admin_image'][:-1]+'f'
        with patch.object(client, 'pages', return_value=[record]):
            self.assertFalse(client.test_passed(production_release(changed)))

    def test_later_failed_test_blocks_promotion(self):
        source = paired_release()
        client = GitHub(source['repository'], 'fake')
        record = {'id':1,'environment':'test','task':'erp-cd',
            'payload':{'release_id':source['id'],'release_hash':fingerprint(source)}}
        with patch.object(client, 'pages', side_effect=[[record],[{'state':'failure'},{'state':'success'}]]):
            self.assertFalse(client.test_passed(production_release(source)))

    def test_promoted_release_checks_cloud_and_local_builds(self):
        source = paired_release()
        client = GitHub(source['repository'],'fake')
        run = {'head_sha':source['commit'],'head_branch':'main','head_repository':{'full_name':source['repository']},
               'conclusion':'success','event':'push','path':'.github/workflows/database-and-backend-ci.yml'}
        with patch.object(client,'request',return_value=run) as request, patch('local_ci.verify_upstream') as upstream, \
                patch.object(client,'verify_local_build') as local:
            client.verify_ci(production_release(source))
        self.assertIn('/runs/123/attempts/1', request.call_args.args[0])
        upstream.assert_called_once_with(client,123,1,source['commit'])
        local.assert_called_once_with(source)


class PairedArtifacts(unittest.TestCase):
    def setUp(self):
        self.fixture = local_fixtures.LocalArtifactTests()
        self.fixture.setUp()
        self.addCleanup(self.fixture.doCleanups)

    def build(self, bad=False):
        f = self.fixture
        image(f.root/'backend.tar', f.backend_files, {'User':'yudao','Labels':f.labels,'ExposedPorts':{'48080/tcp':{}}})
        image(f.root/'admin.tar', f.admin_files, {'Labels':f.labels,'ExposedPorts':{'80/tcp':{}}})
        files = copy.deepcopy(f.admin_files)
        files['usr/share/nginx/html/admin/release.json'] = json.dumps({'release':f.value['id'],'environment':'production'}).encode()
        files['usr/share/nginx/html/admin/assets/index.js'] = b'https://api.vanzhome.com' + (f.api.encode() if bad else b'')
        image(f.root/'production.tar', files, {'Labels':f.labels,'ExposedPorts':{'80/tcp':{}}})
        identity = {key:f.value[key] for key in ('id','commit','run_id','run_attempt','ci')}
        return assemble(f.root/'backend.tar', f.root/'admin.tar', f.root/'complete', f.root, identity, f.api, f.root/'production.tar')

    def test_ci_generates_two_packages_with_identical_backend_bytes(self):
        source = self.build()
        root = self.fixture.root/'complete'
        validate_archive(root/'images.oci.tar', [source['images']['backend'],source['images']['admin']['test']])
        value = production_release(source)
        refs = [value['images']['backend'],value['images']['admin']['test'],value['images']['admin']['production']]
        validate_archive(root/'production.oci.tar', refs)
        header = json.loads((root/'header.json').read_text())
        self.assertEqual(header['release']['production'],source['production'])
        self.assertEqual(len(list(root.iterdir())),4)

    def test_test_api_in_production_artifact_blocks_ci_completion(self):
        with self.assertRaisesRegex(ValueError,'Test API present'):
            self.build(bad=True)
        self.assertFalse((self.fixture.root/'complete/release.json').exists())

    def test_archive_publication_preserves_original_manifests(self):
        source = self.build()
        promoted = production_release(source)
        refs = [promoted['images']['backend'],promoted['images']['admin']['test'],promoted['images']['admin']['production']]
        publisher = Publisher('actor','fake')
        writes = {}
        def request(package,method,path,data=None,headers=None,expected=(200,)):
            if method == 'GET' and '/manifests/cd-' in path:
                return 404,{},b''
            if method == 'PUT':
                import hashlib
                writes[(package,'sha256:'+hashlib.sha256(data).hexdigest())] = data
                return 201,{},b''
            return 200,{},writes[(package,path.rsplit('/',1)[1])]
        with patch.object(publisher,'request',side_effect=request), patch.object(publisher,'blob') as blob, \
                patch('registry_push.Registry') as public, patch('builtins.print'):
            public.return_value.manifest.side_effect = lambda p,d: writes[(p,d)]
            publisher.archive(self.fixture.root/'complete/production.oci.tar',refs,source['id'])
        self.assertEqual(set(writes),{tuple(r.removeprefix('ghcr.io/').split('@')) for r in refs})
        self.assertGreater(blob.call_count,0)


class ManualPreparation(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup)
        self.cache=Path(self.tmp.name)
        self.source=paired_release()
        complete=self.cache/self.source['id']/'complete'; complete.mkdir(parents=True)
        (complete/'release.json').write_text(json.dumps(self.source))
        (complete/'production.oci.tar').write_bytes(b'fixture')
        self.client=Mock(repository=self.source['repository'])
        self.client.release.side_effect=RuntimeError('GitHub GET failed: HTTP 404')
        self.client.test_passed.return_value=True
        self.publisher=Mock()

    def prepare(self,operation='deploy'):
        with patch('promote_local.verify_upstream'), patch('promote_local.validate_archive'):
            return prepare(self.client,self.cache,self.source['id'],operation,self.publisher)

    def test_manual_publish_never_calls_a_build_or_server(self):
        with patch('local_ci.command') as build, patch('runner.ssh_request') as ssh:
            result=self.prepare()
        self.publisher.archive.assert_called_once()
        self.client.publish.assert_called_once_with(result)
        build.assert_not_called(); ssh.assert_not_called()

    def test_failed_test_blocks_all_registry_writes(self):
        self.client.test_passed.return_value=False
        with self.assertRaisesRegex(ValueError,'not passed'):
            self.prepare()
        self.publisher.archive.assert_not_called(); self.client.publish.assert_not_called()

    def test_missing_variant_does_not_rebuild(self):
        path=self.cache/self.source['id']/'complete/production.oci.tar'; path.unlink()
        with self.assertRaisesRegex(ValueError,'artifact is missing'):
            self.prepare()
        self.publisher.archive.assert_not_called()

    def test_failed_publication_does_not_register_release(self):
        self.publisher.archive.side_effect=RuntimeError('upload failed')
        with self.assertRaisesRegex(RuntimeError,'upload failed'):
            self.prepare()
        self.client.publish.assert_not_called()

    def test_test_changed_during_upload_blocks_registration(self):
        self.client.test_passed.side_effect=[True,False]
        with self.assertRaisesRegex(ValueError,'changed while publishing'):
            self.prepare()
        self.client.publish.assert_not_called()

    def test_existing_release_reuses_registry_without_local_cache(self):
        self.client.release.side_effect=None
        self.client.release.return_value=({},production_release(self.source))
        prepare(self.client,self.cache/'missing',self.source['id'],'deploy',self.publisher)
        self.publisher.archive.assert_not_called()
        self.client.verify_ci.assert_called_once()

    def test_rollback_never_publishes_and_does_not_require_latest_test_success(self):
        with self.assertRaisesRegex(ValueError,'already published'):
            self.prepare('rollback')
        self.client.release.side_effect=None
        self.client.release.return_value=({},production_release(self.source))
        self.prepare('rollback')
        self.publisher.archive.assert_not_called(); self.client.test_passed.assert_not_called()

    def test_retired_or_incomplete_release_cannot_be_republished(self):
        self.client.release.side_effect=ValueError('This release is retired')
        with self.assertRaisesRegex(ValueError,'retired'):
            self.prepare()
        self.publisher.archive.assert_not_called()

    def test_automatic_workflow_cannot_promote(self):
        with patch.dict(os.environ,{'GITHUB_ACTIONS':'true','GITHUB_EVENT_NAME':'workflow_run'},clear=True), \
                patch('sys.argv',['promote_local.py','--release',self.source['id'],'--operation','deploy','--cache',str(self.cache)]), \
                patch('promote_local.prepare') as prepare_mock:
            with self.assertRaisesRegex(ValueError,'manual production'):
                promote_main()
            prepare_mock.assert_not_called()


class RegistryGuards(unittest.TestCase):
    def upload(self, bad_range=False):
        content = b'abc'*(2*1024**2)
        digest = 'sha256:'+hashlib.sha256(content).hexdigest()
        publisher = Publisher('actor','fake')
        package = 'cqz-cio/furniture-erp-backend'
        location = '/v2/'+package+'/blobs/uploads/id?_state=opaque'
        received = bytearray(); calls = []
        def request(pkg, method, url, data=None, headers=None, expected=(200,)):
            self.assertEqual(pkg,package)
            calls.append(method)
            if method == 'HEAD': return 404,{},b''
            if method == 'POST': return 202,{'Location':location},b''
            if method == 'PATCH':
                self.assertEqual(headers['Content-Range'],f'{len(received)}-{len(received)+len(data)-1}')
                received.extend(data)
                return 202,{'Location':location,'Range':'0-0' if bad_range else f'0-{len(received)-1}'},b''
            if method == 'PUT':
                self.assertIn('_state=opaque&digest=',url)
                self.assertEqual(hashlib.sha256(received).hexdigest(),digest[7:])
                return 201,{},b''
            self.assertEqual(method,'DELETE')
            return 204,{},b''
        with tarfile.open(fileobj=io.BytesIO(tar_bytes({'blobs/sha256/'+digest[7:]:content}))) as archive, \
                patch.object(publisher,'request',side_effect=request), patch('builtins.print'):
            if bad_range:
                with self.assertRaisesRegex(ValueError,'byte range'):
                    publisher.blob(archive,package,{'digest':digest,'size':len(content)})
            else:
                publisher.blob(archive,package,{'digest':digest,'size':len(content)})
        return calls

    def test_chunk_upload_commits_the_exact_original_bytes(self):
        self.assertEqual(self.upload(),['HEAD','POST','PATCH','PATCH','PUT'])

    def test_bad_chunk_ack_cancels_upload_without_committing(self):
        self.assertEqual(self.upload(bad_range=True),['HEAD','POST','PATCH','DELETE'])

    def test_upload_locations_cannot_leak_credentials(self):
        publisher=Publisher('actor','secret')
        package='cqz-cio/furniture-erp-backend'
        for url in ('https://evil.example/v2/'+package+'/blobs/uploads/a',
                    'http://ghcr.io/v2/'+package+'/blobs/uploads/a',
                    '/v2/other/package/blobs/uploads/a', '/v2/'+package+'/../escape'):
            with self.subTest(url=url), self.assertRaisesRegex(ValueError,'Unsafe'):
                publisher.url(package,url)

    def test_cached_blob_is_not_uploaded(self):
        publisher=Publisher('actor','fake')
        with patch.object(publisher,'request',return_value=(200,{},b'')) as request, patch('builtins.print'):
            publisher.blob(None,'cqz-cio/furniture-erp-backend',{'digest':'sha256:'+'a'*64,'size':5})
        self.assertEqual(request.call_count,1)

    def test_expired_deadline_fails_before_network(self):
        publisher=Publisher('actor','fake',seconds=-1)
        with self.assertRaisesRegex(ValueError,'deadline'):
            publisher.headers('cqz-cio/furniture-erp-backend')


if __name__ == '__main__':
    unittest.main()
