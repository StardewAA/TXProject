"""Synthetic credentials only. No provider requests and no real key output."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

MALL = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('mall_preflight', MALL / 'preflight.py')
preflight = importlib.util.module_from_spec(spec)
spec.loader.exec_module(preflight)


class CredentialPreflightTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.fixture = 'synthetic-only-abcdefghijklmnop'
        self.files = {
            'oss.env': ('OSS_ACCESS_KEY', 'OSS_ACCESS_SECRET'),
            'deepseek.env': ('DEEPSEEK_API_KEY',),
            'pc.env': ('VITE_AMAP_KEY', 'VITE_AMAP_SECURITY_CODE'),
            'h5.env': ('SHOPRO_AMAP_KEY', 'SHOPRO_AMAP_SECURITY_CODE'),
        }
        for name, keys in self.files.items():
            (self.root / name).write_text('\n'.join(f'{key}={self.fixture}' for key in keys), encoding='utf-8')

    def test_success_never_discloses_credentials_or_claims_cloud_acceptance(self):
        result = preflight.check(self.root)
        self.assertNotIn(self.fixture, json.dumps(result))
        self.assertEqual(result['status'], 'configuration-only-passed')
        self.assertFalse(result['externalApisCalled'])
        self.assertFalse(result['credentialsPrinted'])

    def test_missing_file_fails_without_dumping_values(self):
        (self.root / 'oss.env').unlink()
        with self.assertRaisesRegex(preflight.ConfigurationError, 'oss.env: required'):
            preflight.check(self.root)

    def test_empty_secret_is_not_hidden_by_present_key(self):
        (self.root / 'oss.env').write_text('OSS_ACCESS_KEY='+self.fixture+'\nOSS_ACCESS_SECRET=')
        with self.assertRaisesRegex(preflight.ConfigurationError, 'OSS_ACCESS_SECRET'):
            preflight.check(self.root)

    def test_rejects_unrelated_database_credentials(self):
        with (self.root / 'oss.env').open('a') as handle:
            handle.write('\nMYSQL_ROOT_PASSWORD=do-not-copy-this')
        with self.assertRaises(preflight.ConfigurationError) as error:
            preflight.check(self.root)
        self.assertNotIn('do-not-copy-this', str(error.exception))

    def test_rejects_duplicate_or_interpolated_values(self):
        for content in ('DEEPSEEK_API_KEY='+self.fixture+'\nDEEPSEEK_API_KEY='+self.fixture,
                        'DEEPSEEK_API_KEY=${HOST_SECRET}', 'DEEPSEEK_API_KEY=$(command)'):
            with self.subTest():
                (self.root / 'deepseek.env').write_text(content)
                with self.assertRaises(preflight.ConfigurationError):
                    preflight.check(self.root)

    def test_crlf_bom_comments_and_quoted_values(self):
        (self.root / 'deepseek.env').write_bytes(('\ufeff# demo\r\nDEEPSEEK_API_KEY="'+self.fixture+'"\r\n').encode())
        self.assertEqual(preflight.check(self.root)['status'], 'configuration-only-passed')

    def test_missing_amap_security_code_fails(self):
        (self.root / 'pc.env').write_text('VITE_AMAP_KEY='+self.fixture)
        with self.assertRaisesRegex(preflight.ConfigurationError, 'VITE_AMAP_SECURITY_CODE'):
            preflight.check(self.root)

    def test_known_placeholders_fail(self):
        for value in ('your-deepseek-key', 'sk-xxxxxxxxxxxxxxxxxxxx', 'change-me-abcdefghijklmnop'):
            (self.root / 'deepseek.env').write_text('DEEPSEEK_API_KEY='+value)
            with self.assertRaises(preflight.ConfigurationError):
                preflight.check(self.root)

    def test_explicitly_disabled_optional_capability_accepts_empty_settings(self):
        (self.root / 'deepseek.env').write_text('DEEPSEEK_API_KEY=')
        result = preflight.check(self.root, require_deepseek=False)
        self.assertFalse(next(item for item in result['checks'] if item['file'] == 'deepseek.env')['required'])


if __name__ == '__main__':
    unittest.main()
