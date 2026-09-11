import copy
import json
import os
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

from fixtures import release
from runner import execute, ssh_request
from server import interrupted
from test_server import FakeServer


class ProductionPreflight(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.server = FakeServer(self.tmp.name)
        self.server.environment = self.server.state['environment'] = 'production'

    def test_preflight_only_pulls_images_and_checks_existing_service(self):
        before = copy.deepcopy(self.server.state)
        result = self.server.release_preflight(release(2))
        self.assertEqual(result['status'], 'preflight-passed')
        self.assertEqual(self.server.state, before)
        self.assertFalse(self.server.state_path.exists())
        self.assertNotIn(release(2)['id'], self.server.releases)
        self.assertEqual([args[0] for _, args in self.server.calls], ['health', 'pull', 'health'])
        self.assertFalse(self.server.cleanup_ran)

    def test_failed_pull_does_not_stop_service_or_change_state(self):
        before = copy.deepcopy(self.server.state)
        self.server.fail_pull = True
        with self.assertRaisesRegex(RuntimeError, 'pull failed'):
            self.server.release_preflight(release(2))
        self.assertEqual(self.server.state, before)
        self.assertFalse(self.server.state_path.exists())
        self.assertFalse(any(args[0] == 'stop' for _, args in self.server.calls))

    def test_no_registered_production_release_blocks_preflight_and_deploy(self):
        self.server.state['current'] = None
        for call in (lambda: self.server.release_preflight(release(2)),
                     lambda: self.server.deploy(release(2), 'deploy', 'compose')):
            with self.assertRaisesRegex(ValueError, 'first cutover'):
                call()
        self.assertEqual(self.server.calls, [])

    def test_unfinished_operation_blocks_preflight(self):
        self.server.state['in_progress'] = {'phase': 'switch'}
        with self.assertRaisesRegex(ValueError, 'unfinished'):
            self.server.release_preflight(release(2))
        self.assertEqual(self.server.calls, [])

    def test_migration_review_failure_precedes_download(self):
        self.server.preflight = Mock(side_effect=ValueError('Migration requires review'))
        with self.assertRaisesRegex(ValueError, 'Migration requires review'):
            self.server.release_preflight(release(2, schema=50))
        self.assertEqual(self.server.calls, [])


    def test_interruption_preserves_journal_without_starting_rollback(self):
        self.server.pull_images = lambda value: interrupted(15, None)
        with self.assertRaises(SystemExit):
            self.server.deploy(release(2), 'deploy', 'compose')
        self.assertEqual(json.loads(self.server.state_path.read_text())['in_progress']['phase'], 'pull')
        self.assertEqual(self.server.state['current'], release(1)['id'])
        self.assertEqual(self.server.calls, [])


class ProductionRunner(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.output = Path(self.tmp.name) / 'result.json'
        self.args = SimpleNamespace(environment='production', operation='preflight',
            release=release(2)['id'], confirm_cutover='false', output=str(self.output))
        self.github = Mock()
        self.github.release.return_value = ({}, release(2))
        self.github.test_passed.return_value = True

    def test_preflight_requires_test_success_and_never_records_deployment(self):
        with patch('runner.GitHub', return_value=self.github), patch('runner.ssh_request',
                return_value={'status': 'preflight-passed'}) as ssh, patch.dict(os.environ, ERP_CD_ENABLED='false'):
            execute(self.args)
        self.github.verify_ci.assert_called_once_with(release(2))
        self.github.test_passed.assert_called_once_with(release(2))
        self.github.deployment.assert_not_called()
        self.github.deployment_status.assert_not_called()
        self.assertEqual(ssh.call_args.args[:2], ('production', 'preflight'))

    def test_failed_test_gate_writes_report_before_any_ssh(self):
        self.github.test_passed.return_value = False
        with patch('runner.GitHub', return_value=self.github), patch('runner.ssh_request') as ssh:
            with self.assertRaisesRegex(ValueError, 'not passed'):
                execute(self.args)
        ssh.assert_not_called()
        self.github.deployment.assert_not_called()
        self.assertEqual(json.loads(self.output.read_text())['status'], 'failed')

    def test_disabled_daily_cd_is_reported(self):
        self.args.operation = 'deploy'
        with patch.dict(os.environ, ERP_CD_ENABLED='false'), patch('runner.ssh_request') as ssh:
            with self.assertRaisesRegex(ValueError, 'Enable daily CD'):
                execute(self.args)
        ssh.assert_not_called()
        self.assertEqual(json.loads(self.output.read_text())['operation'], 'deploy')

    def test_preflight_success_cannot_be_used_as_deployment_success(self):
        self.args.operation = 'deploy'
        with patch.dict(os.environ, ERP_CD_ENABLED='true'), patch('runner.GitHub', return_value=self.github), \
                patch('runner.ssh_request', return_value={'status': 'preflight-passed'}):
            with self.assertRaisesRegex(ValueError, 'did not complete'):
                execute(self.args)
        self.github.deployment_status.assert_called_with(self.github.deployment.return_value, 'failure')

    def test_production_ssh_has_remote_deadline_and_no_relay(self):
        env = {'ERP_SSH_HOST': 'production.example.com', 'ERP_SSH_USER': 'deploy',
            'ERP_SSH_PORT': '22', 'ERP_DEPLOY_ROOT': '/opt/oakved-deploy/production',
            'ERP_SSH_PRIVATE_KEY': 'fake', 'ERP_SSH_KNOWN_HOSTS': 'fake', 'ERP_IMAGE_TRANSPORT': 'ghcr'}
        with patch.dict(os.environ, env), patch('runner.Path.read_text', return_value='compose'), \
                patch('runner.transport', return_value={}) as transport, \
                patch('runner.preload_test_images') as relay:
            ssh_request('production', 'preflight', release(2))
        command, bundle, _, timeout = transport.call_args.args
        self.assertTrue(command[-1].startswith('timeout --signal=TERM --kill-after=15s 1400s python3'))
        self.assertEqual(timeout, 1500)
        self.assertEqual(bundle['request']['operation'], 'preflight')
        self.assertNotIn('images_preloaded', bundle['request'])
        relay.assert_not_called()


if __name__ == '__main__':
    unittest.main()
