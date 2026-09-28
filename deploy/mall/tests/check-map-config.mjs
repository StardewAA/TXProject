// Read the actual project Vite configuration without writing bundles or showing keys.
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const repo = resolve(import.meta.dirname, '../../..');
for (const [project, prefix] of [['txgy-mall-web', 'VITE_'], ['txgy-mall-uniapp-master', 'SHOPRO_']]) {
  try {
    const root = resolve(repo, project);
    const { loadEnv } = await import(pathToFileURL(resolve(root, 'node_modules/vite/dist/node/index.js')));
    const config = loadEnv('production', root, prefix);
    if (![prefix + 'AMAP_KEY', prefix + 'AMAP_SECURITY_CODE'].every(name => config[name]?.trim())) {
      throw new Error('missing setting');
    }
    console.log(JSON.stringify({ project, viteProductionConfig: 'passed', valuesPrinted: false }));
  } catch {
    console.error(`${project}: configuration check failed (install project dependencies and check the two map settings)`);
    process.exitCode = 1;
  }
}
