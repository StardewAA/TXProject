import assert from 'node:assert/strict';
import fs from 'node:fs';
import { createRequire, stripTypeScriptTypes } from 'node:module';
import test from 'node:test';
import vm from 'node:vm';

const src = new URL('../apps/web-antd/src/', import.meta.url);
const access = fs.readFileSync(new URL('router/access.ts', src), 'utf8');
const normalize = access.slice(access.indexOf('function normalizeMenuPath('), access.indexOf('function getMenuPathSegments('));
const transform = access.slice(access.indexOf('function transformWmsMenus('), access.indexOf('/**\n * 在后端菜单中注入'));
const context = vm.createContext({});
vm.runInContext(stripTypeScriptTypes(normalize + transform), context);
const run = (menus) => JSON.parse(JSON.stringify(context.transformWmsMenus(structuredClone(menus))));

test('non-WMS analysis and AI menus retain their route and component', () => {
  const menus = [{ path: '/dashboard', children: [{ path: 'analytics', name: '分析页', component: 'views/dashboard/analytics/index.vue' }, { path: 'ai', name: 'AI 智能助手' }] }];
  assert.deepEqual(run(menus), menus);
});
test('old and AI home become one menu, preserving the existing AI route identity', () => {
  const menus = [{ path: '/wms', children: [{ id: 1, path: 'home', name: 'WMS 首页', component: 'views/wms/home/index.vue' }, { id: 2, path: 'ai-center', name: 'AI 智能助手', componentName: 'WmsAi', component: 'views/wms/ai/index.vue' }] }];
  const [parent] = run(menus);
  assert.equal(parent.name, '智慧仓储系统');
  assert.equal(parent.children.length, 1);
  assert.equal(parent.children[0].id, 2);
  assert.equal(parent.children[0].componentName, 'WmsAi');
  assert.equal(parent.children[0].name, '仓储首页');
});
test('standalone old home keeps the absolute WMS URL and original route identity', () => {
  const [home] = run([{ path: '/wms/home', name: 'WMS 首页', component: 'views/wms/home/index.vue' }]);
  assert.equal(home.path, '/wms/ai-center');
  assert.equal(home.componentName, 'WMS 首页');
});
test('relative legacy WMS menu without component is migrated', () => {
  const [parent] = run([{ path: '/wms', children: [{ path: 'home', name: 'WMS 首页' }] }]);
  assert.equal(parent.children[0].path, 'ai-center');
  assert.equal(parent.children[0].component, 'views/wms/ai/index.vue');
});

const offline = fs.readFileSync(new URL('offline-icons.ts', src), 'utf8');
const picker = offline.slice(offline.indexOf('function pickIcons('), offline.indexOf('offlineIconPrefixes.forEach('));
const helperUrl = new URL('utils/iconify-subset.mjs', src);
const helpers = fs.existsSync(helperUrl) ? await import(helperUrl.href) : {};
const iconContext = vm.createContext({ ...helpers });
vm.runInContext(stripTypeScriptTypes(picker), iconContext);
test('runtime icon subset preserves alias chains and transformations', () => {
  const data = { prefix: 'test', icons: { base: { body: '<path />' }, unused: { body: '<g />' } }, aliases: { middle: { parent: 'base', rotate: 1 }, requested: { parent: 'middle', hFlip: true } } };
  const subset = JSON.parse(JSON.stringify(iconContext.pickIcons(data, ['requested'])));
  assert.deepEqual(subset.icons, { base: data.icons.base });
  assert.deepEqual(subset.aliases, data.aliases);
});

test('generated offline collections register all aliases with the real Iconify runtime', () => {
  const require = createRequire(new URL('../apps/web-antd/package.json', import.meta.url));
  const { addCollection, getIcon } = require('@iconify/vue');
  const generated = fs.readFileSync(new URL('offline-icons.generated.ts', src), 'utf8');
  const collections = vm.runInNewContext(generated.split('export const offlineIconCollections = ')[1].split(' as unknown')[0]);
  for (const collection of collections) {
    assert.equal(addCollection(collection), true, collection.prefix);
    for (const name of Object.keys(collection.aliases ?? {})) {
      assert.ok(getIcon(`${collection.prefix}:${name}`)?.body, `${collection.prefix}:${name}`);
    }
  }
  assert.ok(getIcon('lucide:home')?.body);
});
