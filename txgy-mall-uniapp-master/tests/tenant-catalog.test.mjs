import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const source = readFileSync(new URL('../sheep/data/mall-catalog-runtime.js', import.meta.url), 'utf8')
  .replace(/^import .*;\r?\n/gm, '').replace(/export /g, '');
function harness() {
  let tenant = '1', calls = 0, response = { code: 0, data: { list: [] } };
  const load = new Function('CategoryApi', 'SpuApi', 'mallCategories', 'mallProducts', 'ensureTenantReady', 'getControlledTenantId',
    `${source}\nreturn loadMallCatalog;`)({ getCategoryList: async () => ({ code: 0, data: [] }) },
    { getSpuPage: async () => { calls++; return response; } }, [], [{ id: 99, name: 'seed', price: 100 }], async () => {}, () => tenant);
  return { load, calls: () => calls, tenant: next => { tenant = next; }, response: next => { response = next; } };
}
test('successful empty tenant catalog remains empty and never receives legacy seed products', async () => {
  const h = harness(); assert.deepEqual((await h.load()).products, []); assert.equal((await h.load()).source, 'api');
});
test('catalog cache and in-flight requests are separated by tenant', async () => {
  const h = harness(); h.response({ code: 0, data: { list: [{ id: 1, name: 'A' }] } });
  assert.equal((await h.load()).products[0].name, 'A');
  h.tenant('2'); h.response({ code: 0, data: { list: [{ id: 2, name: 'B' }] } });
  assert.equal((await h.load()).products[0].name, 'B'); assert.equal(h.calls(), 2);
  h.tenant('1'); assert.equal((await h.load()).products[0].name, 'A'); assert.equal(h.calls(), 2);
});
test('failed catalog rejects without caching a fabricated product list', async () => {
  const h = harness(); h.response({ code: 500 }); await assert.rejects(h.load(), /暂无法加载/);
  h.response({ code: 0, data: { list: [] } }); assert.deepEqual((await h.load()).products, []); assert.equal(h.calls(), 2);
});

test('an old tenant catalog response cannot populate the newly selected tenant cache', async () => {
  const h = harness(); let resolve;
  h.response(new Promise(r => { resolve = r; })); const old = h.load(); const rejected = assert.rejects(old, /商城已变化/);
  for (let n = 0; n < 4; n++) await Promise.resolve();
  h.tenant('2'); h.response({ code: 0, data: { list: [] } }); assert.deepEqual((await h.load()).products, []);
  resolve({ code: 0, data: { list: [{ id: 1, name: 'A' }] } }); await rejected;
  assert.deepEqual((await h.load()).products, []);
});
