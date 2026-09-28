import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const source = readFileSync(new URL('../sheep/data/mall-catalog-runtime.js', import.meta.url), 'utf8')
  .replace(/^import .*;\r?\n/gm, '').replace(/export /g, '');
function harness() {
  let tenant = '1', calls = 0, response = { code: 0, data: { list: [] } }, categoryResponse = { code: 0, data: [] };
  const load = new Function('CategoryApi', 'SpuApi', 'mallCategories', 'mallProducts', 'ensureTenantReady', 'getControlledTenantId',
    `${source}\nreturn loadMallCatalog;`)({ getCategoryList: async () => categoryResponse },
    { getSpuPage: async () => { calls++; return response; } }, [], [{ id: 99, name: 'seed', price: 100 }], async () => {}, () => tenant);
  return { load, calls: () => calls, tenant: next => { tenant = next; }, response: next => { response = next; }, categories: next => { categoryResponse = next; } };
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

test('tenant-specific categories group populated leaves under their root and omit empty roots', async () => {
  const h = harness();
  h.tenant('2009');
  h.categories({ code: 0, data: [
    { id: 940001, parentId: 0, name: '防腐材料' },
    { id: 940002, parentId: 940001, name: '底漆与中间漆' },
    { id: 940003, parentId: 940001, name: '防腐面漆' },
    { id: 940099, parentId: 0, name: '空分类' },
  ] });
  h.response({ code: 0, data: { list: [
    { id: 1, name: '环氧底漆', categoryId: 940002 },
    { id: 2, name: '聚氨酯面漆', categoryId: 940003 },
  ] } });
  const catalog = await h.load();
  assert.deepEqual(catalog.categories.map(item => item.name), ['全部商品', '防腐材料']);
  assert.deepEqual(catalog.categories[1].categoryIds, [940002, 940003]);
  assert.deepEqual(catalog.products.map(item => item.categoryKey), ['category-group-940001', 'category-group-940001']);
});

test('an old tenant catalog response cannot populate the newly selected tenant cache', async () => {
  const h = harness(); let resolve;
  h.response(new Promise(r => { resolve = r; })); const old = h.load(); const rejected = assert.rejects(old, /商城已变化/);
  for (let n = 0; n < 4; n++) await Promise.resolve();
  h.tenant('2'); h.response({ code: 0, data: { list: [] } }); assert.deepEqual((await h.load()).products, []);
  resolve({ code: 0, data: { list: [{ id: 1, name: 'A' }] } }); await rejected;
  assert.deepEqual((await h.load()).products, []);
});
