// Isolated UI fixture, never imported by the production app.
import { createRequire } from 'node:module';
import { fileURLToPath, pathToFileURL } from 'node:url';
import path from 'node:path';
const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../..');
const require = createRequire(path.join(root, 'package.json'));
const { createServer } = await import(pathToFileURL(require.resolve('vite')).href);
const vue = (await import(pathToFileURL(require.resolve('@vitejs/plugin-vue')).href)).default;
const server = await createServer({
  configFile: false, root: here, plugins: [vue()],
  resolve: { alias: { '#/api/cms/review': path.join(here, 'api.ts'), 'ant-design-vue': path.join(root, 'apps/web-antd/node_modules/ant-design-vue') } },
  server: { host: '127.0.0.1', port: 5188, strictPort: true, fs: { allow: [root] } },
});
await server.listen(); server.printUrls();
