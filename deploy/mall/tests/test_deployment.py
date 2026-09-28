"""Offline deployment contracts. These do not replace Docker/SQL/cloud acceptance."""
import contextlib
import importlib.util
import io
import json
import re
from pathlib import Path
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
MALL = ROOT / 'deploy/mall'
spec = importlib.util.spec_from_file_location('mall_verify', MALL / 'verify.py')
verify = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verify)


class DeploymentContracts(unittest.TestCase):
    def test_pc_build_context_contains_copy_inputs(self):
        script = (ROOT / 'deploy/patch-image/build-base.sh').read_text(encoding='utf-8')
        line = next(line for line in script.splitlines() if 'docker build -f txgy-mall-web/' in line)
        context = ROOT / line.split()[-1]
        for filename in ('package.json', 'pnpm-lock.yaml', 'pnpm-workspace.yaml', 'nginx.conf'):
            self.assertTrue((context / filename).is_file(), filename)

    def test_product_patch_replaces_the_launched_jar(self):
        dockerfile = (ROOT / 'deploy/patch-image/Dockerfile.patch-product').read_text(encoding='utf-8')
        self.assertIn('COPY app.jar /app/app.jar', dockerfile)
        self.assertIn('exec java $JAVA_OPTS -jar /app/app.jar', dockerfile)
        self.assertIn('CMD []', dockerfile)

    def test_patch_admin_routes_api_before_static_fallback(self):
        config = (ROOT / 'deploy/patch-image/nginx-spa.conf').read_text(encoding='utf-8')
        for prefix in ('/admin-api/', '/app-api/', '/infra/ws'):
            self.assertIn('location ^~ ' + prefix, config)
        self.assertIn('proxy_pass $gateway_upstream;', config)
        self.assertIn('try_files $uri =404;', config)

    def test_mysql_requires_final_migration_marker(self):
        scripts = sorted(path.name for path in (MALL / 'sql').iterdir() if path.suffix in ('.sql', '.sh'))
        self.assertEqual(scripts[-1], '011-ready.sql')
        compose = (MALL / 'compose.yaml').read_text(encoding='utf-8')
        self.assertIn("version='2026-09-25-file-acl'", compose)
        self.assertIn('promote_status, promote_fail_count, promote_last_error', compose)
        self.assertIn('infra_file_upload_ticket', compose)

    def test_public_refs_include_oss_not_only_gateway_urls(self):
        sql = (MALL / 'sql/009-file-public-recompute.sql').read_text(encoding='utf-8')
        self.assertNotIn("LIKE '%/infra/file/%'", sql)
        self.assertIn("NULLIF(`pic_url`, '') IS NOT NULL", sql)
        self.assertIn("NULLIF(f.`path`, '') IS NOT NULL", sql)

    def test_clean_compose_does_not_use_historical_patch_images(self):
        compose = (MALL / 'compose.yaml').read_text(encoding='utf-8')
        self.assertNotIn('file-acl-20260924', compose)
        self.assertNotIn('pull_policy: never', compose)
        for module in ('txgy-gateway', 'txgy-module-pay/txgy-module-pay-server',
                       'txgy-module-wms/txgy-module-wms-server'):
            self.assertIn('MODULE_PATH: ' + module, compose)

    def test_shell_sources_use_lf(self):
        for path in (MALL / 'start.sh', MALL / 'sql/004-public-origin.sh'):
            self.assertNotIn(b'\r\n', path.read_bytes(), str(path))

    def test_admin_container_does_not_override_heap_via_root_build_script(self):
        dockerfile = (ROOT / 'deploy/frontend/Dockerfile').read_text(encoding='utf-8')
        self.assertIn('pnpm exec turbo run build --filter=@vben/web-antd --concurrency=1', dockerfile)
        self.assertNotIn('RUN pnpm run build:antd', dockerfile)

    def test_admin_dependency_install_has_a_heap_bound(self):
        dockerfile = (ROOT / 'deploy/frontend/Dockerfile').read_text(encoding='utf-8')
        self.assertIn('ARG INSTALL_NODE_HEAP_MB=2048', dockerfile)
        self.assertIn('ARG BUILD_NODE_HEAP_MB=4096', dockerfile)
        self.assertLess(dockerfile.index('ENV NODE_OPTIONS=--max-old-space-size=${INSTALL_NODE_HEAP_MB}'),
                        dockerfile.index('pnpm install --frozen-lockfile'))
        self.assertNotIn('pnpm install --no-frozen-lockfile', dockerfile)

    def test_h5_lockfile_is_not_explicitly_ignored(self):
        rules = (ROOT / 'txgy-mall-uniapp-master/.gitignore').read_text(encoding='utf-8').splitlines()
        self.assertNotIn('pnpm-lock.yaml', rules)
        self.assertIn('!pnpm-lock.yaml', rules)
        self.assertTrue((ROOT / 'txgy-mall-uniapp-master/pnpm-lock.yaml').is_file())

    def test_admin_turbo_passes_build_configuration_and_heap(self):
        config = json.loads((ROOT / 'txgy-ui-admin-vben/turbo.json').read_text(encoding='utf-8'))
        self.assertIn('VITE_*', config['globalEnv'])
        self.assertIn('NODE_OPTIONS', config['globalEnv'])

    def test_order_detail_audit_table_is_shipped_without_erp_data(self):
        schema = (MALL / 'sql/001-schema.sql').read_text(encoding='utf-8')
        demo = (MALL / 'sql/002-demo.sql').read_text(encoding='utf-8')
        self.assertIn('CREATE TABLE `trade_erp_inventory_request`', schema)
        self.assertNotIn('INSERT INTO `trade_erp_inventory_request`', demo)
        self.assertIn('trade_erp_inventory_request', (MALL / 'sql/011-ready.sql').read_text(encoding='utf-8'))

    def test_every_seed_sku_uses_the_real_wms_unit_contract(self):
        unit_spec = importlib.util.spec_from_file_location('fixture_units', MALL / 'tools/fixture_units.py')
        units = importlib.util.module_from_spec(unit_spec)
        unit_spec.loader.exec_module(units)
        mall_units, warehouse_units = {}, {}
        for line in (MALL / 'sql/002-demo.sql').read_text(encoding='utf-8').splitlines():
            if line.startswith('INSERT INTO `product_sku` '):
                sku = re.search(r'VALUES \((\d+),', line)[1]
                properties = json.loads(re.search(r"'(\[\{.*?\}\])'", line)[1])
                unit = next(p['valueName'] for p in properties if p['propertyName'] in ('单位', '计量单位'))
                mall_units[sku] = units.resolve_unit(unit)
            if line.startswith('INSERT INTO `wms_item` '):
                sku = re.search(r'VALUES \((\d+),', line)[1]
                warehouse_units[sku] = re.search(r"'([A-Z0-9]+)','FINISHED_GOODS'", line)[1]
        self.assertEqual(len(mall_units), 903)
        self.assertEqual(mall_units, warehouse_units)
        self.assertEqual(units.resolve_unit('桶'), 'BUCKET')
        self.assertEqual(units.resolve_unit('罐'), 'CAN')
        with self.assertRaises(ValueError):
            units.resolve_unit('unknown-demo-unit')


class SmokeModes(unittest.TestCase):
    def fake_api(self, path, payload=None, headers=None, origin=None):
        if '/portal/products?' in path:
            return {'total': 1, 'list': [{'spuId': 1}]}
        if '/portal/products/' in path:
            return {'saleable': True, 'skus': [1], 'picUrl': 'https://example.invalid/picture.png'}
        if path.endswith('platform-login'):
            return {'tenantId': 2009 if payload['account'] == 'buyer2009' else 1, 'accessToken': 'fixture-token'}
        if path.endswith('/session'):
            return {'platformAccountId': 1}
        if path.endswith('/auth/login'):
            self.assertEqual(origin, 'http://admin')
            return {'accessToken': 'fixture-token'}
        raise AssertionError(path)

    def fake_get(self, url, *args, **kwargs):
        if url.startswith('https:'):
            raise OSError('simulated unavailable OSS')
        return b'<html></html>', 'text/html'

    def test_local_mode_never_fetches_oss_and_marks_it_unverified(self):
        stdout = io.StringIO()
        with patch.object(verify, 'api', side_effect=self.fake_api), \
             patch.object(verify, 'get', side_effect=self.fake_get) as fetch, \
             contextlib.redirect_stdout(stdout):
            verify.check(include_oss=False)
        self.assertEqual(fetch.call_count, 3)
        result = json.loads(stdout.getvalue())
        self.assertEqual(result['status'], 'local-only-passed')
        self.assertFalse(result['ossVerified'])
        self.assertTrue(result['notVerified'])

    def test_default_full_mode_fails_when_oss_unavailable(self):
        with patch.object(verify, 'api', side_effect=self.fake_api), \
             patch.object(verify, 'get', side_effect=self.fake_get):
            with self.assertRaises(OSError):
                verify.check()

    def test_cloud_read_success_does_not_claim_business_or_device_acceptance(self):
        def fetch(url, *args, **kwargs):
            if url.startswith('https:'):
                return b'fixture-image' * 20, 'image/png'
            return self.fake_get(url, *args, **kwargs)

        stdout = io.StringIO()
        with patch.object(verify, 'api', side_effect=self.fake_api), \
             patch.object(verify, 'get', side_effect=fetch), \
             contextlib.redirect_stdout(stdout):
            verify.check()
        result = json.loads(stdout.getvalue())
        self.assertEqual(result['status'], 'passed')
        self.assertEqual(result['scope'], 'startup-smoke-only')
        self.assertTrue(result['ossVerified'])
        self.assertIn('New uploads and OSS promotion/retries', result['notVerified'])
        self.assertIn('Amap browser SDK and live AI customer replies', result['notVerified'])
        self.assertIn('Browser interaction and Android device', result['notVerified'])

    def test_local_mode_still_fails_on_broken_api(self):
        with patch.object(verify, 'api', side_effect=RuntimeError('broken API')), \
             patch.object(verify, 'get', side_effect=self.fake_get):
            with self.assertRaises(RuntimeError):
                verify.check(include_oss=False)


if __name__ == '__main__':
    unittest.main()
