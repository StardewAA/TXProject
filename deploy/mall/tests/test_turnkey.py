"""Offline wiring contracts; never open or print real credential files."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
MALL = ROOT / 'deploy/mall'


class TurnkeyContracts(unittest.TestCase):
    def test_backend_source_reactor_contains_ai(self):
        dockerfile = (MALL / 'Dockerfile.backend').read_text(encoding='utf-8')
        self.assertIn('txgy-module-ai/txgy-module-ai-server', dockerfile)
        self.assertIn('-am package', dockerfile)

    def test_credentials_loaded_by_files_not_empty_environment_overrides(self):
        compose = (MALL / 'compose.yaml').read_text(encoding='utf-8')
        self.assertIn('MALL_OSS_ENV_FILE:-../oss-keys.env', compose)
        self.assertIn('MALL_DEEPSEEK_ENV_FILE:-../deepseek-keys.env', compose)
        self.assertNotIn('OSS_ACCESS_KEY:', compose)
        self.assertNotIn('OSS_ACCESS_SECRET:', compose)
        self.assertNotIn('DEEPSEEK_API_KEY:', compose)
        self.assertIn('ai: { condition: service_healthy }', compose)
        self.assertIn('REQUIRE_DEEPSEEK: "true"', compose)

    def test_frontends_mount_env_without_blank_overrides(self):
        for relative, target, prefix, command in (
            ('txgy-mall-web/Dockerfile', '/app/.env.local', 'VITE_', 'pnpm build'),
            ('deploy/uniapp/Dockerfile.mall', '/workspace/txgy-mall-uniapp/.env.local',
             'SHOPRO_', 'npm run build:h5'),
        ):
            with self.subTest(relative=relative):
                text = (ROOT / relative).read_text(encoding='utf-8')
                self.assertIn('id=amap_env,target=' + target, text)
                self.assertIn('if [ -f /run/secrets/amap_key ]; then export ' + prefix + 'AMAP_KEY=', text)
                self.assertNotIn('else echo', text)
                self.assertIn('ARG AMAP_BUILD_REV=', text)
                self.assertLess(text.index('id=amap_env'), text.index(command))

    def test_build_contexts_exclude_provider_credentials(self):
        for relative in ('.dockerignore', 'deploy/mall/Dockerfile.backend.dockerignore'):
            self.assertIn('**/*-keys.env', (ROOT / relative).read_text(encoding='utf-8'))
        self.assertIn('.env.local', (ROOT / 'txgy-mall-web/.dockerignore').read_text(encoding='utf-8'))
        self.assertIn('**/.env.*', (ROOT / 'deploy/uniapp/Dockerfile.mall.dockerignore').read_text(encoding='utf-8'))

    def test_both_entrypoints_preflight_before_cloud_and_build(self):
        powershell = (MALL / 'start.ps1').read_text(encoding='utf-8')
        shell = (MALL / 'start.sh').read_text(encoding='utf-8')
        for script in (powershell, shell):
            self.assertLess(script.index('preflight'), script.index('verify-assets'))
            self.assertLess(script.index('preflight'), script.index('foreach' if script is powershell else 'for service'))
            self.assertIn('ai', script)
        self.assertIn('if ($CheckConfigOnly)', powershell)
        self.assertIn('--check-config-only', shell)

    def test_default_capabilities_are_enabled_and_rotation_is_documented(self):
        config = (MALL / '.env.example').read_text(encoding='utf-8')
        self.assertIn('MALL_ADDRESS_MAP_ENABLED=true', config)
        self.assertIn('MALL_CUSTOMER_SERVICE_AI_ENABLED=true', config)
        self.assertIn('AMAP_BUILD_REV=', config)
        self.assertNotIn('DEEPSEEK_API_KEY=', config)
        self.assertNotIn('OSS_ACCESS_SECRET=', config)


if __name__ == '__main__':
    unittest.main()
