import test from 'node:test';
import assert from 'node:assert/strict';
import { comparisonDefault, comparisonList, mergeComparisonLists, comparisonRows,
  comparisonMoney, comparisonResult, comparisonQuote, createComparisonController } from '../sheep/helper/comparison.mjs';

const list = (skuIds = [], more = {}) => ({ ...comparisonDefault(), skuIds, ...more });
for (const bad of [null, {}, list([0]), list([-1]), list([1, 1]), list([1, 2, 3, 4, 5]), list(['1']),
  list([Number.MAX_SAFE_INTEGER + 1]), list([], { quantity: 0 }), list([], { quantity: 1000 }),
  list([], { quantity: 1.5 }), list([], { preference: 'HIDDEN_COMMISSION' }), list([], { version: -1 })]) {
  test(`reject invalid comparison ${JSON.stringify(bad)}`, () => assert.throws(() => comparisonList(bad)));
}
test('normalization strips injected owner/tenant/price fields', () => assert.deepEqual(
  comparisonList({ ...list([2, 3]), tenantId: 2, memberId: 666, price: 1 }), list([2, 3])));
test('merge deduplicates and preserves server quantity/preference/version', () => assert.deepEqual(
  mergeComparisonLists(list([2, 3], { version: 8, quantity: 7 }), list([3, 4], { quantity: 2 })), list([2, 3, 4], { version: 8, quantity: 7 })));
test('merge does not truncate excess guest items', () => assert.throws(() => mergeComparisonLists(list([1, 2, 3]), list([4, 5]))));
test('unknown money never means zero', () => {
  for (const value of [null, undefined, '0', NaN, -1, 1.2]) assert.equal(comparisonMoney(value), '待确认');
  assert.equal(comparisonMoney(0), '￥0.00'); assert.equal(comparisonMoney(123), '￥1.23');
});
test('attribute rows preserve dimensions and duplicate raw values', () => {
  const rows = comparisonRows([
    { available: true, properties: [{ name: '单位', value: 'kg' }, { name: '规格', value: 'DN50' }, { name: '规格', value: '外径60' }] },
    { available: true, properties: [{ name: '单位', value: 'L' }, { name: '规格', value: 'DN50' }] },
    { available: false, properties: [{ name: '私有字段', value: '不可见' }] },
  ], true);
  assert.deepEqual(rows.map(row => row.name), ['单位', '规格']);
  assert.deepEqual(rows[0].values, ['kg', 'L', '不可用']);
  assert.equal(rows[1].values[0], 'DN50 / 外径60');
});
test('same text only hides raw differences, not declares equivalence', () => assert.deepEqual(comparisonRows([
  { available: true, properties: [{ name: '名称', value: '板' }] },
  { available: true, properties: [{ name: '名称', value: '板' }] },
], true), []));
test('reject mismatched server comparison result', () => {
  const source = list([1, 2]);
  for (const value of [null, { ...source, items: [{ skuId: 2 }, { skuId: 1 }] }, { ...source, quantity: 2, items: [{ skuId: 1 }, { skuId: 2 }] }]) {
    assert.throws(() => comparisonResult(value, source));
  }
});
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no; }); return { promise, resolve, reject }; };
function harness(authenticated = true) {
  const state = {}, storage = new Map(), calls = [];
  let epoch = 1, tenantId = '1', server = list([10], { version: 2 }), read = async () => server, write = async next => { server = { ...next, version: next.version + 1 }; return server; };
  const deps = {
    capture: () => ({ epoch, tenantId, authenticated }), current: scope => scope.epoch === epoch && scope.tenantId === tenantId,
    readGuest: scope => storage.get(scope.tenantId), writeGuest: (scope, value) => storage.set(scope.tenantId, value),
    readList: () => { calls.push('read'); return read(); }, saveList: value => { calls.push(value); return write(value); },
  };
  const controller = createComparisonController(deps, state);
  return { controller, state, storage, calls, setRead(value) { read = value; }, setWrite(value) { write = value; },
    changeTenant(value) { tenantId = value; epoch++; controller.reset(); }, changeAccount() { epoch++; controller.reset(); } };
}
test('guest basket isolated by tenant and no private requests', async () => {
  const h = harness(false); await h.controller.load(); await h.controller.add(1);
  assert.deepEqual(h.state.list.skuIds, [1]); assert.deepEqual(h.calls, []);
  h.changeTenant('2'); await h.controller.load(); assert.deepEqual(h.state.list.skuIds, []);
  h.changeTenant('1'); await h.controller.load(); assert.deepEqual(h.state.list.skuIds, [1]);
});
test('private list reads server only, guest merge is explicit', async () => {
  const h = harness(); h.storage.set('1', list([2])); await h.controller.load();
  assert.deepEqual(h.state.list.skuIds, [10]); assert.equal(h.calls.length, 1);
  await h.controller.merge(); assert.deepEqual(h.state.list.skuIds, [10, 2]);
  assert.equal(h.state.list.version, 3); assert.deepEqual(h.storage.get('1').skuIds, []);
});
test('CAS failure preserves guest and disables writes until fresh read', async () => {
  const h = harness(); h.storage.set('1', list([2])); await h.controller.load();
  h.setWrite(async () => { throw new Error('409 请刷新'); });
  assert.equal(await h.controller.merge(), false); assert.equal(h.state.ready, false);
  assert.deepEqual(h.storage.get('1').skuIds, [2]);
  await assert.rejects(() => h.controller.save(list([3])), /刷新/);
});
test('late previous account list never populates new account', async () => {
  const h = harness(), wait = deferred(); h.setRead(() => wait.promise);
  const pending = h.controller.load(); h.changeAccount(); wait.resolve(list([99]));
  assert.equal(await pending, false); assert.deepEqual(h.state.list.skuIds, []);
});
test('late save cannot clear a new session guest basket', async () => {
  const h = harness(), wait = deferred(); h.storage.set('1', list([2])); await h.controller.load();
  h.setWrite(() => wait.promise); const pending = h.controller.merge(); h.changeAccount();
  wait.resolve(list([10, 2], { version: 3 })); assert.equal(await pending, false);
  assert.deepEqual(h.storage.get('1').skuIds, [2]); assert.deepEqual(h.state.list.skuIds, []);
});
test('parallel save is rejected instead of losing updates', async () => {
  const h = harness(), wait = deferred(); await h.controller.load(); h.setWrite(() => wait.promise);
  const pending = h.controller.add(2); await assert.rejects(() => h.controller.add(3), /稍候/);
  wait.resolve(list([10, 2], { version: 3 })); await pending;
});
test('latest load wins', async () => {
  const h = harness(), old = deferred(); h.setRead(() => old.promise);
  const pending = h.controller.load(); h.setRead(async () => list([22], { version: 7 })); await h.controller.load();
  old.resolve(list([99])); await pending; assert.deepEqual(h.state.list.skuIds, [22]);
});
test('failed initial read never creates empty replacement', async () => {
  const h = harness(); h.setRead(async () => { throw new Error('网络错误'); });
  assert.equal(await h.controller.add(8), false); assert.deepEqual(h.calls, ['read']); assert.equal(h.state.ready, false);
});
test('quantity and preference save with server version', async () => {
  const h = harness(); await h.controller.load(); await h.controller.save({ ...h.state.list, quantity: 3, preference: 'BALANCED' });
  assert.equal(h.calls[1].version, 2); assert.equal(h.state.list.quantity, 3); assert.equal(h.state.list.preference, 'BALANCED');
});
test('guest changed in another tab during merge must not be cleared', async () => {
  const h = harness(), wait = deferred(); h.storage.set('1', list([2])); await h.controller.load();
  h.setWrite(() => wait.promise); const pending = h.controller.merge(); h.storage.set('1', list([2, 3]));
  wait.resolve(list([10, 2], { version: 3 })); assert.equal(await pending, true);
  assert.deepEqual(h.storage.get('1').skuIds, [2, 3]); assert.deepEqual(h.state.guest.skuIds, [2, 3]);
});
test('quotation rejects another quantity, address, order or price basis', () => {
  const input = list([11, 22], { quantity: 3 });
  const valid = { priceBasis: 'CHECKOUT_ESTIMATE', quantity: 3, addressId: 41, items: [{ skuId: 11, quantity: 3 }, { skuId: 22, quantity: 3 }] };
  assert.equal(comparisonQuote(valid, input, 41), valid);
  for (const bad of [{ ...valid, quantity: 4 }, { ...valid, addressId: 42 }, { ...valid, priceBasis: 'GUARANTEED' },
    { ...valid, items: [...valid.items].reverse() }, { ...valid, items: [{ skuId: 11, quantity: 1 }, valid.items[1]] }]) {
    assert.throws(() => comparisonQuote(bad, input, 41));
  }
});
test('comparison requires explicit sales-unit contract', () => {
  const input = list([1]), valid = { ...input, quantityUnit: 'SALE_UNIT', items: [{ skuId: 1 }] };
  assert.equal(comparisonResult(valid, input), valid);
  assert.throws(() => comparisonResult({ ...valid, quantityUnit: 'KG' }, input));
});
