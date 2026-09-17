import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const source = readFileSync(new URL('../pages/order/aftersale/apply.vue', import.meta.url), 'utf8')
  .split('<script setup>')[1].split('</script>')[0].replace(/^\s*import[^\n]+\r?\n/gm, '');
const deferred = () => { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b; }); return { promise, resolve, reject }; };
function harness() {
  let alive = true, expire, load;
  const calls = [], toasts = [], routes = [];
  const order = { id: 42, status: 20, shopId: 7, items: [{ id: 12, spuName: '原账号商品', payPrice: 1000, properties: [] }] };
  const dependencies = {
    reactive: value => value, ref: value => ({ value }), onLoad: fn => { load = fn; },
    useTenantTask: fn => { expire = fn; return { current: () => alive }; },
    OrderApi: { getOrderDetail: async () => ({ code: 0, data: order }) },
    TradeConfigApi: { getTradeConfig: async () => ({ code: 0, data: { afterSaleRefundReasons: ['不需要了'], afterSaleReturnReasons: ['商品破损'] } }) },
    AfterSaleApi: { createAfterSale: async data => { calls.push(data); return { code: 0, data: 50 }; } },
    sheep: { $helper: { toast: value => toasts.push(value) }, $router: { redirect: value => routes.push(value), go: (...args) => routes.push(args) } },
    uni: { showToast: value => toasts.push(value) }, chatEntry: (shopId, resourceType, resourceId) => ({ shopId, resourceType, resourceId }),
  };
  const page = new Function(...Object.keys(dependencies), source + ';return {state,formData,submit,onRefundChange,onChange,onReason,openCustomerService};')(...Object.values(dependencies));
  return { ...page, dependencies, calls, toasts, routes, load: () => load({ orderId: '42', itemId: '12' }),
    change() { alive = false; expire(); },
    fill() { page.onRefundChange({ detail: { value: 10 } }); page.onChange({ detail: { value: '不需要了' } }); page.onReason(); page.formData.applyDescription = '原账号私有草稿'; page.formData.applyPicUrls = ['isolated-image']; },
  };
}

test('same-account after-sale uses existing backend fields and navigates only after success', async () => {
  const h = harness(); await h.load(); h.fill(); await h.submit();
  assert.deepEqual(h.calls, [{ orderItemId: '12', refundPrice: 1000, way: 10, applyReason: '不需要了', applyDescription: '原账号私有草稿', applyPicUrls: ['isolated-image'] }]);
  assert.deepEqual(h.routes, ['/pages/order/aftersale/list']);
});
test('account replacement clears order, draft, evidence and popup and forbids former-account writes', async () => {
  const h = harness(); await h.load(); h.fill(); h.state.showModal = true; h.change();
  assert.equal(h.state.expired, true); assert.deepEqual(h.state.item, {}); assert.deepEqual(h.state.order, {}); assert.equal(h.state.showModal, false);
  assert.deepEqual(h.formData, { way: '', applyReason: '', applyDescription: '', applyPicUrls: [] });
  await h.submit(); h.openCustomerService(); h.onRefundChange({ detail: { value: 20 } }); h.onChange({ detail: { value: '商品破损' } }); h.onReason();
  assert.deepEqual(h.calls, []); assert.deepEqual(h.routes, []); assert.equal(h.formData.way, '');
});
test('late order response after replacement cannot restore a private order or start configuration read', async () => {
  const h = harness(), gate = deferred(); let configCalls = 0;
  h.dependencies.OrderApi.getOrderDetail = () => gate.promise;
  h.dependencies.TradeConfigApi.getTradeConfig = async () => { configCalls++; return { code: 0, data: {} }; };
  const work = h.load(); h.change(); gate.resolve({ code: 0, data: { id: 42, items: [{ id: 12, payPrice: 1000 }] } }); await work;
  assert.deepEqual(h.state.item, {}); assert.equal(configCalls, 0);
});
test('late configuration response cannot repopulate expired page data', async () => {
  const h = harness(), gate = deferred(); h.dependencies.TradeConfigApi.getTradeConfig = () => gate.promise;
  const work = h.load(); await Promise.resolve(); h.change(); gate.resolve({ code: 0, data: { afterSaleRefundReasons: ['旧原因'] } }); await work;
  assert.deepEqual(h.state.config, {}); assert.deepEqual(h.state.item, {});
});
test('a completed old-account request cannot toast success or navigate the replacement account', async () => {
  const h = harness(), gate = deferred(); await h.load(); h.fill();
  h.dependencies.AfterSaleApi.createAfterSale = data => { h.calls.push(data); return gate.promise; };
  const work = h.submit(); h.change(); gate.resolve({ code: 0, data: 50 }); await work;
  assert.equal(h.calls.length, 1); assert.deepEqual(h.routes, []); assert.deepEqual(h.toasts, []);
});
test('duplicate submit taps send once and payload evidence is snapshotted', async () => {
  const h = harness(), gate = deferred(); await h.load(); h.fill();
  h.dependencies.AfterSaleApi.createAfterSale = data => { h.calls.push(data); return gate.promise; };
  const work = h.submit(); await h.submit(); h.formData.applyPicUrls.push('late-image');
  assert.equal(h.calls.length, 1); assert.deepEqual(h.calls[0].applyPicUrls, ['isolated-image']); gate.resolve({ code: 0 }); await work;
});
test('an old-account failed request does not show errors in the replacement account', async () => {
  const h = harness(), gate = deferred(); await h.load(); h.fill(); h.dependencies.AfterSaleApi.createAfterSale = () => gate.promise;
  const work = h.submit(); h.change(); gate.reject(new Error('old request')); await work;
  assert.deepEqual(h.toasts, []); assert.deepEqual(h.routes, []);
});
