import os
import unittest
from unittest.mock import Mock, patch

from bootstrap_policy import PROFILE
from runner import ssh_request
from test_local_ci import local_release


class TestDeploymentPermissions(unittest.TestCase):
    def environment(self):
        return {'ERP_SSH_HOST':PROFILE['host'],'ERP_SSH_USER':PROFILE['user'],
                'ERP_SSH_PORT':'22','ERP_DEPLOY_ROOT':PROFILE['root']}

    def test_daily_operations_use_noninteractive_sudo_with_existing_deadline(self):
        connection = Mock()
        connection.command.side_effect = lambda command: ['ssh','verified-test',command]
        for operation in ('deploy','rollback','snapshot','lease-start','lease-check','lease-end'):
            with self.subTest(operation=operation), patch.dict(os.environ,self.environment(),clear=True), \
                    patch('runner.Path.read_text',return_value='compose fixture'), \
                    patch('runner.transport',return_value={}) as transport:
                ssh_request('test',operation,release=local_release() if operation in ('deploy','rollback') else None,
                            connection=connection)
                command, bundle, _, timeout = transport.call_args.args
                self.assertTrue(command[-1].startswith('sudo -n timeout --signal=TERM --kill-after='))
                self.assertEqual(bundle['entry'],'server')
                self.assertEqual(bundle['request']['operation'],operation)
                self.assertEqual(timeout,1500 if operation in ('deploy','rollback') else 60)

    def test_sudo_is_not_sent_to_a_different_test_destination(self):
        for change in ({'ERP_SSH_HOST':'other.example.com'},{'ERP_SSH_USER':'other'},
                       {'ERP_SSH_PORT':'2222'},{'ERP_DEPLOY_ROOT':'/other/test'}):
            with self.subTest(change=change), patch.dict(os.environ,{**self.environment(),**change},clear=True), \
                    patch('runner.transport') as transport:
                with self.assertRaisesRegex(ValueError,'verified test destination'):
                    ssh_request('test','snapshot')
                transport.assert_not_called()
