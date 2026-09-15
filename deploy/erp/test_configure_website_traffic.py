import json
import unittest
from configure_website_traffic import configure


class WebsiteTrafficConfigTest(unittest.TestCase):
    def test_secrets_are_generated_once_and_other_settings_are_preserved(self):
        source = 'DATABASE_PASSWORD=keep-existing\n# operator comment\n'
        output = configure(source, 163, '2026-09-15', lambda _: 'test-only-' * 8)
        self.assertIn(source, output)
        self.assertEqual(output, configure(output, 163, '2026-09-16', lambda _: self.fail('key was rotated')))
        values = dict(line.split('=', 1) for line in output.splitlines() if '=' in line)
        stats = json.loads(values['SPRING_APPLICATION_JSON'])['yudao']['statistics']
        self.assertEqual([163], stats['behavior']['enabled-tenant-ids'])
        self.assertTrue(stats['behavior']['consent-required'])
        self.assertEqual({'163': '2026-09-15'}, stats['website']['enabled-from'])

    def test_existing_tenant_and_unrelated_json_remain(self):
        config = {'other': {'flag': True}, 'yudao': {'statistics': {'behavior': {'enabled-tenant-ids': [162]}}}}
        result = configure('SPRING_APPLICATION_JSON=' + json.dumps(config), 163, '2026-09-15', lambda _: 'x' * 64)
        values = dict(line.split('=', 1) for line in result.splitlines() if '=' in line)
        updated = json.loads(values['SPRING_APPLICATION_JSON'])
        self.assertEqual(config['other'], updated['other'])
        self.assertEqual([162, 163], updated['yudao']['statistics']['behavior']['enabled-tenant-ids'])

    def test_ambiguous_configuration_fails_without_replacing_secrets(self):
        with self.assertRaises(ValueError):
            configure('YUDAO_STATISTICS_BEHAVIOR_ENABLED=false', 163, '2026-09-15')
        with self.assertRaises(ValueError):
            configure('SPRING_APPLICATION_JSON={"yudao.statistics.behavior.enabled":true}', 163, '2026-09-15')
        with self.assertRaises(ValueError):
            configure('SPRING_APPLICATION_JSON={"yudao":{"statistics":{"behavior":{"hmac-tenants":{"163":{"active-version":1}}}}}}', 163, '2026-09-15')


if __name__ == '__main__':
    unittest.main()
