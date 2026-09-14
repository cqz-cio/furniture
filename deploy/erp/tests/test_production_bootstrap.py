import copy
import hashlib
import io
import json
import os
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

import production_policy as policy
from bootstrap_io import Database
from common import file_sha256, fingerprint, write_json
from production_bootstrap import ProductionBootstrap
from runner import execute, ssh_request
from test_bootstrap import CutoverHarness, test_release


def production_release():
    value = test_release()
    value['database_version'] = 50
    return value


class ProductionPolicy(unittest.TestCase):
    def test_changed_preparation_contract_cannot_be_reused(self):
        engine=object.__new__(ProductionBootstrap)
        engine.release=production_release()
        engine.payload={'helper':{'sha256':'a'*64},'audit':{'before':[],'after':[]},'compose':'reviewed'}
        engine.state={'release_hash':fingerprint(engine.release),'migration_passed':True,
                      'preparation_contract':engine.preparation_contract()}
        engine.payload['compose']='changed'
        with self.assertRaisesRegex(ValueError,'configuration changed'):
            engine.verify_prepared({})

    def test_file_checksum_handles_multiple_chunks_without_python311_api(self):
        value=b'erp backup\x00'*300000
        self.assertEqual(file_sha256(io.BytesIO(value)),hashlib.sha256(value).hexdigest())
        self.assertEqual(file_sha256(io.BytesIO(b'')),hashlib.sha256(b'').hexdigest())

    def test_only_published_tested_profile_and_confirmed_switch(self):
        value = production_release()
        policy.validate_target('production', policy.PROFILE['root'], value, 'prepare')
        policy.validate_target('production', policy.PROFILE['root'], value, 'cutover', True)
        for environment, operation, confirmed in [('test','prepare',False),('production','cutover',False),('production','recover','true')]:
            with self.subTest(operation=operation), self.assertRaises(ValueError):
                policy.validate_target(environment, policy.PROFILE['root'], value, operation, confirmed)
        value['database_version'] = 51
        with self.assertRaisesRegex(ValueError,'review later'):
            policy.validate_target('production', policy.PROFILE['root'], value, 'prepare')

    def test_clone_credentials_cannot_target_source_or_test_database(self):
        runtime = {'YUDAO_DB_URL':'jdbc:mysql://127.0.0.1:3306/'+policy.PROFILE['source_database'],
                   'YUDAO_DB_PASSWORD':'old', 'SPRING_DATA_REDIS_PASSWORD':'redis-secret'}
        user = 'erp_pd_a_'+'a'*16
        result = policy.backend_environment(runtime,'oakved_cd_production_live_'+'a'*16,user,'b'*64)
        self.assertIn('YUDAO_DB_USERNAME='+user, result)
        self.assertNotIn(policy.PROFILE['source_database'], result)
        self.assertIn('SPRING_DATA_REDIS_PASSWORD=redis-secret', result)
        for database in [policy.PROFILE['source_database'], 'oakved_cd_test_live_'+'a'*16, 'mysql']:
            with self.subTest(database=database), self.assertRaises(ValueError):
                policy.backend_environment(runtime,database,user,'b'*64)
        instance = Database(Mock(),policy)
        instance.query = Mock()
        with self.assertRaises(ValueError):
            instance.create('oakved_cd_test_live_'+'a'*16)
        instance.query.assert_not_called()

    def test_https_proxy_gates_every_erp_route_and_preserves_redirect_and_cache(self):
        original = (Path(__file__).parent/'production-nginx.conf').read_text()
        path = policy.PROFILE['nginx_main']
        result = policy.nginx_plan({path:original},'a'*48)
        http = original[original.index('\nserver {'):]
        for mode in ('maintenance','candidate','open'):
            text = result[mode][path]
            self.assertTrue(text.endswith(http))
            for line in original.splitlines():
                if any(k in line for k in ('ssl_', 'proxy_cache', 'proxy_ignore_headers', 'add_header')):
                    self.assertIn(line,text)
            self.assertEqual(text.count('if ($http_x_erp_cd_probe'),{'maintenance':4,'candidate':6,'open':0}[mode])
        self.assertNotIn('127.0.0.1:48081;',result['open'][path])
        self.assertEqual(result['open'][path].count('127.0.0.1:48082;'),5)
        with self.assertRaisesRegex(ValueError,'changed'):
            policy.nginx_plan({path:original+'\n'},'a'*48)


class ProductionHarness(CutoverHarness, ProductionBootstrap):
    def __init__(self, directory, fail=None):
        super().__init__(directory,fail)
        self.release = production_release()
        self.state.update(release_id=self.release['id'],release_hash=fingerprint(self.release),
            live_database='oakved_cd_production_live_'+'a'*16)
        self.database.version.return_value = 48
        self.old = {name:{'id':str(i)*64,'image':digest,'runtime':{},'restart':{'Name':'unless-stopped','MaximumRetryCount':0},'running':True}
                    for i,(name,digest) in enumerate(policy.PROFILE['source_images'].items(),1)}
        write_json(self.root/'source.json',{'probe_token':'a'*48,'containers':copy.deepcopy(self.old)})

    def containers(self,running=None):
        if running is not None and any(v['running'] is not running for v in self.old.values()):
            raise ValueError('running state changed')
        return copy.deepcopy(self.old)

    def run(self,args,label,seconds=30):
        self.step(label)
        if args[0]=='docker':
            info=next(v for v in self.old.values() if v['id']==args[-1])
            if args[1]=='update': info['restart']['Name']=args[2].split('=',1)[1]
            if args[1] in ('start','stop'): info['running']=args[1]=='start'
        return '0'


class ProductionCutover(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        patcher=patch('bootstrap.capacity');patcher.start();self.addCleanup(patcher.stop)

    def test_both_docker_services_and_background_java_stop_before_backup(self):
        engine=ProductionHarness(self.tmp.name)
        result=engine.cutover()
        self.assertEqual(result['environment'],'production')
        for service in ('erp-backend','erp-admin'):
            self.assertLess(engine.calls.index('disable-old-'+service),engine.calls.index('stop-old-'+service))
            self.assertLess(engine.calls.index('stop-old-'+service),engine.calls.index('backup'))
        self.assertLess(engine.calls.index('stop-legacy'),engine.calls.index('backup'))
        self.assertTrue(all(not v['running'] and v['restart']['Name']=='no' for v in engine.old.values()))

    def test_partial_stop_restores_and_never_opens_traffic(self):
        for label in ('stop-old-erp-admin','stop-legacy','clone-migration','health'):
            engine=ProductionHarness(self.tmp.name,label)
            with self.subTest(label=label),self.assertRaises(RuntimeError): engine.cutover()
            self.assertIn('restore-legacy',engine.calls)
            self.assertNotIn('routes:open',engine.calls)

    def test_saved_container_identity_and_restart_policy_are_used_for_recovery(self):
        engine=ProductionHarness(self.tmp.name)
        snapshot=json.loads((engine.root/'source.json').read_text())
        engine.stop_legacy()
        engine.restore_previous_containers(snapshot)
        self.assertTrue(all(v['running'] and v['restart']['Name']=='unless-stopped' for v in engine.old.values()))
        engine.old['erp-admin']['id']='f'*64
        with self.assertRaisesRegex(ValueError,'changed outside'):
            engine.restore_previous_containers(snapshot)

    def test_after_publication_failure_never_restores_stale_database(self):
        engine=ProductionHarness(self.tmp.name,'routes:open')
        with self.assertRaises(RuntimeError): engine.cutover()
        self.assertEqual(engine.state['phase'],'roll-forward-required')
        self.assertNotIn('restore-legacy',engine.calls)
        engine.old['erp-admin']['running']=True
        with self.assertRaises(ValueError): engine.recover()
        self.assertNotIn('routes:open',engine.calls[engine.calls.index('save:roll-forward-required')+1:])


class ProductionGates(unittest.TestCase):
    def test_preparation_and_cutover_require_exact_successful_test_release(self):
        for operation in ('prepare','cutover'):
            with tempfile.TemporaryDirectory() as directory:
                args=SimpleNamespace(environment='production',operation=operation,release=production_release()['id'],
                                     confirm_cutover='true',output=str(Path(directory)/'result.json'))
                github=Mock();github.release.return_value=({},production_release());github.test_passed.return_value=False
                with patch('runner.GitHub',return_value=github),patch('runner.ssh_request') as ssh:
                    with self.assertRaisesRegex(ValueError,'has not passed'): execute(args)
                ssh.assert_not_called();github.deployment.assert_not_called()

    def test_prepare_does_not_register_a_live_deployment(self):
        with tempfile.TemporaryDirectory() as directory:
            args=SimpleNamespace(environment='production',operation='prepare',release=production_release()['id'],
                                 confirm_cutover='false',output=str(Path(directory)/'result.json'))
            github=Mock();github.release.return_value=({},production_release());github.test_passed.return_value=True
            with patch.dict(os.environ,{},clear=True),patch('runner.GitHub',return_value=github),\
                    patch('runner.ssh_request',return_value={'status':'prepared'}): execute(args)
            github.deployment.assert_not_called()

    def test_wrong_privileged_destination_is_rejected_before_ssh(self):
        env={'ERP_SSH_HOST':'124.220.2.69','ERP_SSH_USER':'ubuntu'}
        with patch.dict(os.environ,env,clear=True),patch('runner.transport') as transport:
            with self.assertRaisesRegex(ValueError,'verified production destination'): ssh_request('production','snapshot')
        transport.assert_not_called()


if __name__=='__main__': unittest.main()
