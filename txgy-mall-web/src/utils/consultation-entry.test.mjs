import { test } from 'vitest';
import assert from 'node:assert/strict';
import { consultationId, createConsultationAction } from './consultation-entry.mjs';
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done; }); return { promise, resolve }; };
function setup(type = 'PRODUCT') {
  const calls = [], state = {}, context = { valid: true, resource: { type, id: 11 }, accept: false };
  const api = { readProduct: async id => ({ id, shopId: 7 }), readOrder: async id => ({ id, shopId: 8 }) };
  const open = createConsultationAction({
    resource: () => context.resource, current: () => context.valid,
    readProduct: id => { calls.push(['product', id]); return api.readProduct(id); },
    readOrder: id => { calls.push(['order', id]); return api.readOrder(id); },
    confirmPlatform: async () => { calls.push(['confirm']); return context.accept; },
    navigate: target => calls.push(['navigate', target]), update: value => Object.assign(state, value),
  });
  return { calls, state, context, api, open };
}
test('only safe positive IDs are accepted', () => {
  for (const value of [null, undefined, 0, -1, 1.2, '01', '1e2', '9007199254740992', Number.MAX_SAFE_INTEGER + 1]) assert.equal(consultationId(value), null);
  assert.equal(consultationId(123), '123');
});
test('product resolves actual shop and passes only explicit resource reference', async () => {
  const h = setup(); assert.equal(await h.open(), true);
  assert.deepEqual(h.calls, [['product', '11'], ['navigate', { scope: 'SHOP', shopId: 7, resourceType: 'PRODUCT', resourceId: 11 }]]);
});
test('order uses private owner endpoint, not product endpoint', async () => {
  const h = setup('ORDER'); await h.open();
  assert.deepEqual(h.calls, [['order', '11'], ['navigate', { scope: 'SHOP', shopId: 8, resourceType: 'ORDER', resourceId: 11 }]]);
});
test('explicit generic platform entry reads no resource and has no card', async () => {
  const h = setup('PLATFORM'); await h.open();
  assert.deepEqual(h.calls, [['navigate', { scope: 'PLATFORM', shopId: 0 }]]);
});
test('missing shop cancellation never navigates', async () => {
  const h = setup(); h.api.readProduct = async id => ({ id }); assert.equal(await h.open(), false);
  assert.deepEqual(h.calls, [['product', '11'], ['confirm']]);
});
test('missing shop explicitly confirmed uses platform with no resource ref', async () => {
  const h = setup(); h.api.readProduct = async id => ({ id, shopId: 0 }); h.context.accept = true; await h.open();
  assert.deepEqual(h.calls.at(-1), ['navigate', { scope: 'PLATFORM', shopId: 0 }]);
});
test('owner lookup failure never silently falls back to platform', async () => {
  const h = setup('ORDER'); h.api.readOrder = async () => { throw Error('无本人订单权限'); }; assert.equal(await h.open(), false);
  assert.deepEqual(h.calls, [['order', '11']]); assert.equal(h.state.error, '无本人订单权限'); assert.equal(h.state.busy, false);
});
test('mismatched resource response cannot navigate or confirm platform', async () => {
  const h = setup(); h.api.readProduct = async () => ({ id: 99, shopId: 8 }); assert.equal(await h.open(), false);
  assert.deepEqual(h.calls, [['product', '11']]);
});
test('double clicks share a single lookup', async () => {
  const h = setup(), pending = deferred(); h.api.readProduct = () => pending.promise;
  const first = h.open(); assert.equal(await h.open(), false); pending.resolve({ id: 11, shopId: 7 }); await first;
  assert.equal(h.calls.filter(c => c[0] === 'product').length, 1); assert.equal(h.calls.filter(c => c[0] === 'navigate').length, 1);
});
test('late response after account switch or page hide cannot navigate', async () => {
  const h = setup(), pending = deferred(); h.api.readProduct = () => pending.promise;
  const first = h.open(); h.context.valid = false; pending.resolve({ id: 11, shopId: 7 }); assert.equal(await first, false);
  assert.deepEqual(h.calls, [['product', '11']]); assert.equal(h.state.busy, false);
});
test('changed item identity rejects stale row response', async () => {
  const h = setup(), pending = deferred(); h.api.readProduct = () => pending.promise;
  const first = h.open(); h.context.resource = { type: 'PRODUCT', id: 12 }; pending.resolve({ id: 11, shopId: 7 });
  assert.equal(await first, false); assert.deepEqual(h.calls, [['product', '11']]);
});
test('invalid resource cannot call endpoints or fallback', async () => {
  const h = setup(); h.context.resource.id = '0'; assert.equal(await h.open(), false); assert.deepEqual(h.calls, []);
});
test('each cart item independently resolves its shop, never first-shop fallback', async () => {
  const h = setup(); await h.open(); h.context.resource = { type: 'PRODUCT', id: 12 }; h.api.readProduct = async id => ({ id, shopId: 8 }); await h.open();
  assert.deepEqual(h.calls.filter(c => c[0] === 'navigate').map(c => c[1].shopId), [7, 8]);
});
