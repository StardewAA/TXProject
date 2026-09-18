import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const deferred = () => { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b; }); return { promise, resolve, reject }; };
const row = (id, selected = true) => ({ id, selected, count: 2, sku: { id: id + 100, price: 100, stock: 9 }, spu: { id: id + 200, status: 1, categoryId: 8 } });
const response = (valid = [row(1)], invalid = [row(2)]) => ({ code: 0, data: { validList: valid, invalidList: invalid } });
function harness() {
  let epoch = 1;
  const calls = [];
  const api = { getCartList: async () => response() };
  for (const method of ['addCart', 'updateCartCount', 'deleteCart', 'updateCartSelected']) api[method] = async data => { calls.push([method, data]); return { code: 0 }; };
  const captureTenantOperation = () => epoch;
  const isTenantOperationCurrent = value => value === epoch;
  const source = readFileSync(new URL('../sheep/store/cart.js', import.meta.url), 'utf8')
    .replace(/^import .*;\r?\n/gm, '').replace(/export default cart;/, 'return cart;');
  const definition = new Function('defineStore', 'CartApi', 'captureTenantOperation', 'isTenantOperationCurrent', source)
    ((_name, options) => options, api, captureTenantOperation, isTenantOperationCurrent);
  const cart = definition.state();
  for (const [name, action] of Object.entries(definition.actions)) cart[name] = action.bind(cart);
  return { cart, api, calls, captureTenantOperation, isTenantOperationCurrent, change() { epoch++; cart.emptyList(); } };
}

test('list failures retain confirmed rows and visibly block checkout until retry', async () => {
  const h = harness(); await h.cart.getList();
  h.api.getCartList = async () => { throw new Error('network'); };
  assert.equal(await h.cart.getList(), false);
  assert.equal(h.cart.list.length, 2); assert.match(h.cart.loadError, /重试/); assert.equal(h.cart.canOperate(), false);
  h.api.getCartList = async () => response(); await h.cart.getList(); assert.equal(h.cart.canOperate(), true);
});
test('business failure and malformed lists cannot become a successful empty cart', async () => {
  for (const bad of [false, { code: 500 }, { code: 0, data: {} }]) {
    const h = harness(); await h.cart.getList(); h.api.getCartList = async () => bad;
    await h.cart.getList(); assert.equal(h.cart.list.length, 2); assert.ok(h.cart.loadError);
  }
});
test('same-account read sequence ignores older success and failure', async () => {
  for (const reject of [false, true]) {
    const h = harness(), first = deferred(), second = deferred(); let n = 0;
    h.api.getCartList = () => ++n === 1 ? first.promise : second.promise;
    const a = h.cart.getList(), b = h.cart.getList(); second.resolve(response([row(3)], [])); await b;
    if (reject) first.reject(new Error('old')); else first.resolve(response()); await a;
    assert.deepEqual(h.cart.list.map(item => item.id), [3]); assert.equal(h.cart.loadError, ''); assert.equal(h.cart.loading, false);
  }
});
test('invalid rows cannot be selected, totalled or included by normal select-all', async () => {
  const h = harness(); await h.cart.getList();
  assert.deepEqual(h.cart.selectedIds, [1]); assert.equal(h.cart.totalPriceSelected, 200);
  assert.equal(await h.cart.selectSingle(2), false); await h.cart.selectAll(true);
  assert.deepEqual(h.calls, [['updateCartSelected', { ids: [1], selected: true }]]);
});
test('edit selection is local, permits invalid deletion, and never includes invalid money', async () => {
  const h = harness(); await h.cart.getList(); h.cart.onChangeEditMode(true); await h.cart.selectSingle(2);
  assert.deepEqual(h.cart.selectedIds, [2]); assert.equal(h.cart.totalPriceSelected, 0); assert.deepEqual(h.calls, []);
  await h.cart.delete(h.cart.selectedIds); assert.deepEqual(h.calls, [['deleteCart', '2']]);
});
test('quantity, selection and deletion are serialized through one pending write', async () => {
  const h = harness(), gate = deferred(); await h.cart.getList();
  h.api.updateCartCount = data => { h.calls.push(['updateCartCount', data]); return gate.promise; };
  const pending = h.cart.update({ goods_id: 1, goods_num: 3 });
  assert.equal(await h.cart.selectSingle(1), false); assert.equal(await h.cart.delete([1]), false);
  assert.equal(await h.cart.update({ goods_id: 1, goods_num: 4 }), false);
  gate.resolve({ code: 0 }); await pending; assert.equal(h.calls.length, 1); assert.equal(h.cart.busy, false);
});
test('failed quantity request is not optimistically persisted and requires reconciliation', async () => {
  const h = harness(); await h.cart.getList(); const revision = h.cart.revision;
  h.api.updateCartCount = async () => false;
  assert.equal(await h.cart.update({ goods_id: 1, goods_num: 3 }), false);
  assert.equal(h.cart.list[0].count, 2); assert.ok(h.cart.operationError); assert.equal(h.cart.canOperate(), false);
  assert.ok(h.cart.revision > revision);
});
test('invalid quantities and invalid-row quantities cause no writes', async () => {
  const h = harness(); await h.cart.getList();
  for (const goods_num of [0, -1, 1.5, Infinity, 2147483648]) assert.equal(await h.cart.update({ goods_id: 1, goods_num }), false);
  assert.equal(await h.cart.update({ goods_id: 2, goods_num: 3 }), false); assert.deepEqual(h.calls, []);
});
test('late old-account mutation cannot fetch or overwrite the next account cart', async () => {
  const h = harness(), gate = deferred(); await h.cart.getList(); let reads = 0;
  h.api.updateCartCount = () => gate.promise; h.api.getCartList = async () => { reads++; return response([row(3)], []); };
  const pending = h.cart.update({ goods_id: 1, goods_num: 3 }); h.change(); await h.cart.getList();
  gate.resolve({ code: 0 }); await pending;
  assert.equal(reads, 1); assert.deepEqual(h.cart.list.map(item => item.id), [3]); assert.equal(h.cart.busy, false);
});
test('captured deletion ownership refuses replacement account even with identical cart IDs', async () => {
  const h = harness(); await h.cart.getList(); const owner = h.captureTenantOperation(); h.change(); await h.cart.getList();
  assert.equal(await h.cart.delete([1], owner), false); assert.deepEqual(h.calls, []);
});
test('empty cart is confirmed explicitly and is never shown as all-selected', async () => {
  const h = harness(); h.api.getCartList = async () => response([], []); await h.cart.getList();
  assert.equal(h.cart.loaded, true); assert.equal(h.cart.isAllSelected, false); assert.equal(h.cart.totalPriceSelected, 0);
});
test('confirmed add remains successful when refresh fails and blocks another write until reconciliation', async () => {
  const h = harness(); await h.cart.getList(); h.api.getCartList = async () => { throw new Error('refresh failed'); };
  assert.equal(await h.cart.add({ id: 101, goods_num: 2 }), true);
  assert.match(h.cart.loadError, /已更新.*无需重复操作/); assert.equal(await h.cart.add({ id: 101, goods_num: 2 }), false);
  assert.equal(h.calls.length, 1); assert.equal(h.cart.canOperate(), false);
  h.api.getCartList = async () => response(); await h.cart.getList(); assert.equal(h.cart.canOperate(), true);
});
test('invalid add arguments and malformed advertised SKU/price never become purchasable', async () => {
  const h = harness();
  for (const value of [undefined, null, {}, { id: 0 }, { id: 101, goods_num: 0 }]) assert.equal(await h.cart.add(value), false);
  assert.deepEqual(h.calls, []);
  const malformed = [row(3), row(4), row(5), row(6)];
  malformed[0].sku = null; malformed[1].sku.price = -1; malformed[2].sku.price = Number.MAX_SAFE_INTEGER;
  malformed[3].sku.price = '100';
  h.api.getCartList = async () => response(malformed, []); await h.cart.getList();
  assert.equal(h.cart.list.length, 4); assert.deepEqual(h.cart.newList, []); assert.deepEqual(h.cart.selectedIds, []);
  assert.equal(h.cart.totalPriceSelected, 0); assert.equal(await h.cart.selectAll(true), false);
});

function pageHarness() {
  const h = harness(), routes = [], toasts = [], handlers = {}, modals = [];
  const source = readFileSync(new URL('../pages/index/cart.vue', import.meta.url), 'utf8')
    .split('<script setup>')[1].split('</script>')[0].replace(/^\s*import[^\n]+\r?\n/gm, '');
  const deps = {
    sheep: { $platform: { navbar: 0 }, $store: () => h.cart, $helper: { toast: value => toasts.push(value) }, $router: { go: (...args) => routes.push(args) } },
    uni: { hideTabBar() {}, $on: (name, fn) => { handlers[name] = fn; }, $off() {}, showModal: options => modals.push(options) },
    computed: fn => ({ computed: true, get value() { return fn(); } }),
    reactive: value => new Proxy(value, { get: (target, name) => target[name]?.computed ? target[name].value : target[name] }),
    onShow() {}, onHide: fn => { handlers.hide = fn; }, onUnmounted() {},
    SpuApi: { getSpuListByIds: async ids => ({ code: 0, data: ids.split(',').map(id => ({ id: Number(id), deliveryTypes: [1] })) }) },
    isEmpty: value => !value?.length, fen2yuan: value => value / 100,
    captureTenantOperation: h.captureTenantOperation, isTenantOperationCurrent: h.isTenantOperationCurrent,
  };
  const page = new Function(...Object.keys(deps), source + ';return {state,getCartList,onConfirm,onDelete,onNumberChange,checkDeliveryConflicts};')(...Object.values(deps));
  return { ...h, page, deps, routes, toasts, modals, replace() { h.change(); handlers['mall:session-changed'](); }, hide: () => handlers.hide() };
}
test('checkout submits only saleable rows and captures counts without changing them', async () => {
  const h = pageHarness(); await h.page.getCartList(); await h.page.onConfirm();
  assert.equal(h.routes.length, 1);
  assert.deepEqual(JSON.parse(h.routes[0][1].data).items, [{ skuId: '101', count: 2, cartId: 1, categoryId: 8 }]);
});
test('account replacement during delivery lookup forbids navigation and old errors', async () => {
  for (const fail of [false, true]) {
    const h = pageHarness(), gate = deferred(); await h.page.getCartList(); h.deps.SpuApi.getSpuListByIds = () => gate.promise;
    const pending = h.page.onConfirm(); h.replace();
    if (fail) gate.reject(new Error('old')); else gate.resolve({ code: 0, data: [{ id: 201, deliveryTypes: [1] }] });
    await pending; assert.deepEqual(h.routes, []); assert.deepEqual(h.toasts, []);
  }
});
test('leaving the page during delivery lookup forbids late checkout navigation', async () => {
  const h = pageHarness(), gate = deferred(); await h.page.getCartList(); h.deps.SpuApi.getSpuListByIds = () => gate.promise;
  const pending = h.page.onConfirm(); h.hide(); gate.resolve({ code: 0, data: [{ id: 201, deliveryTypes: [1] }] }); await pending;
  assert.deepEqual(h.routes, []);
});
test('repeated checkout taps request delivery data only once', async () => {
  const h = pageHarness(), gate = deferred(); await h.page.getCartList(); let calls = 0;
  h.deps.SpuApi.getSpuListByIds = () => { calls++; return gate.promise; };
  const pending = h.page.onConfirm(); await h.page.onConfirm(); assert.equal(calls, 1);
  gate.resolve({ code: 0, data: [{ id: 201, deliveryTypes: [1] }] }); await pending; assert.equal(h.routes.length, 1);
});
test('missing delivery rows and empty global delivery intersection refuse checkout', async () => {
  const h = pageHarness(); await h.page.getCartList(); h.deps.SpuApi.getSpuListByIds = async () => ({ code: 0, data: [{ id: 999, deliveryTypes: [1] }] });
  await h.page.onConfirm(); assert.deepEqual(h.routes, []);
  assert.equal(h.page.checkDeliveryConflicts([[1, 2], [2, 3], [1, 3]]), true);
});
test('delete modal binds initiating identity and snapshots selected IDs', async () => {
  const h = pageHarness(); await h.page.getCartList(); h.cart.onChangeEditMode(true); await h.cart.selectSingle(2);
  const pending = h.page.onDelete(); assert.equal(h.modals.length, 1); h.replace(); await h.page.getCartList();
  h.modals[0].success({ confirm: true }); await pending; assert.deepEqual(h.calls, []);
});
test('confirming valid delete sends once and a canceled modal sends nothing', async () => {
  for (const confirm of [true, false]) {
    const h = pageHarness(); await h.page.getCartList(); h.cart.onChangeEditMode(true); await h.cart.selectSingle(2);
    const pending = h.page.onDelete(); await h.page.onDelete(); assert.equal(h.modals.length, 1);
    h.modals[0].success({ confirm }); await pending; assert.equal(h.calls.length, confirm ? 1 : 0);
    if (confirm) assert.deepEqual(h.calls[0], ['deleteCart', '2']);
  }
});
