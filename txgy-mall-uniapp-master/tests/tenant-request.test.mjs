import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createTenantContext } from '../sheep/helper/tenant-context.mjs';
import { createTenantAuthClient } from '../sheep/helper/tenant-auth-client.mjs';

const source = readFileSync(new URL('../sheep/request/index.js', import.meta.url), 'utf8')
  .replace(/^import[\s\S]*?;\r?\n/gm, '').replace(/^export \{[^}]*\};?\r?\n/gm, '')
  .replace(/export const /g, 'const ').replace('export default request;', '');
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; };
const settle = async () => { for (let n = 0; n < 20; n++) await Promise.resolve(); };

function harness({ ready, proof = { code: 0, data: { tenantId: 1, userId: 7 } }, unconfirmed = false, sessionError } = {}) {
  const context = createTenantContext('1'); if (!unconfirmed) context.setAccount(1, 7);
  const storage = { token: 'A', 'refresh-token': 'RA', 'tenant-id': '999' };
  const requests = [], refreshCalls = [], proofCalls = []; let logoutCount = 0;
  const pending = deferred();
  const user = { isLogin: true,
    setToken(token, refreshToken, identity) { storage.token = token; storage['refresh-token'] = refreshToken;
      context.invalidateAccount(); if (identity?.tenantId) context.setAccount(identity.tenantId, identity.userId); },
    logout() { logoutCount++; storage.token = ''; storage['refresh-token'] = ''; this.isLogin = false; context.invalidateAccount(); },
  };
  class Request {
    constructor(options) { this.options = options; this.interceptors = { request: { use: fn => { this.req = fn; } }, response: { use: fn => { this.res = fn; } } }; }
    async middleware(input) {
      const config = this.req({ ...this.options, ...input, header: { ...this.options.header, ...input.header }, custom: { ...this.options.custom, ...input.custom } });
      requests.push(config);
      return this.res({ config, data: config.header.Authorization === 'A' ? { code: 401 } : { code: 0, data: 'ok' } });
    }
  }
  const deps = { Request, createTenantAuthClient, readTenantToken: key => storage[key], tenantId: '1', apiPath: '/app-api', baseUrl: 'http://test', $store: () => user, $platform: { name: 'H5' }, showAuthModal() {},
    getTerminal: () => 1, tenantContext: context, ensureTenantReady: async () => {
      if (ready) await ready;
      if (context.state().blocked) throw new Error(context.state().blocked);
    }, ensureAccountSession: async () => { if (sessionError) throw sessionError; },
    captureTenantOperation: () => ({ token: storage.token, sessionEpoch: context.state().sessionEpoch }),
    isTenantOperationCurrent: operation => operation.token === storage.token && operation.sessionEpoch === context.state().sessionEpoch,
    captureTenantRequest: () => context.capture(storage.token, storage['refresh-token']),
    isTenantRequestCurrent: snapshot => context.current(snapshot, storage.token),
    getControlledTenantId: () => context.state().browseTenantId, getAccountTenantId: () => context.state().accountTenantId,
    tenantTransport: (path, config) => {
      if (path === '/member/mall-tenant/session') { proofCalls.push({ path, config }); return Promise.resolve(proof); }
      refreshCalls.push({ path, config }); return pending.promise;
    },
    uni: { getStorageSync: key => storage[key], showLoading() {}, hideLoading() {}, showToast() {} },
  };
  const api = new Function(...Object.keys(deps), `${source}\nreturn {request, getTenantId};`)(...Object.values(deps));
  return { ...api, storage, context, user, requests, refreshCalls, proofCalls, pending, logoutCount: () => logoutCount };
}

test('actual request implementation pins headers to controlled legacy entry, ignoring tenant-id storage', async () => {
  const h = harness(); h.storage.token = 'B';
  await h.request({ url: '/trade/cart/list' });
  assert.equal(h.requests[0].header['tenant-id'], '1'); assert.equal(h.getTenantId(), '1');
});

test('concurrent 401 responses share one refresh and replay with the new identity', async () => {
  const h = harness(); const first = h.request({ url: '/trade/cart/list' }); const second = h.request({ url: '/trade/order/page' });
  await settle(); assert.equal(h.refreshCalls.length, 1);
  assert.equal(h.refreshCalls[0].config.header['tenant-id'], '1');
  h.pending.resolve({ code: 0, data: { accessToken: 'A2', refreshToken: 'RA2', tenantId: 1, userId: 7 } });
  assert.equal((await first).code, 0); assert.equal((await second).code, 0);
  assert.equal(h.requests.at(-1).header.Authorization, 'A2');
  assert.equal(h.proofCalls.length, 1); assert.equal(h.proofCalls[0].config.header.Authorization, 'Bearer A2');
});

test('refresh with another or absent member cannot replay a cart or order request', async () => {
  for (const userId of [8, undefined]) {
    const h = harness(); const pending = h.request({ url: '/trade/cart/add', method: 'POST', data: { skuId: 7 } });
    const rejected = assert.rejects(pending); await settle();
    h.pending.resolve({ code: 0, data: { accessToken: 'A2', refreshToken: 'RA2', tenantId: 1, userId } });
    await rejected; assert.equal(h.requests.length, 1); assert.equal(h.proofCalls.length, 0);
    assert.equal(h.storage.token, ''); assert.equal(h.logoutCount(), 1);
  }
});
test('wrong session proof rejects refresh and never dispatches the queued cart write', async () => {
  const h = harness({ proof: { code: 0, data: { tenantId: 2, userId: 7 } } });
  const pending = h.request({ url: '/trade/cart/add', method: 'POST', data: { skuId: 7 } });
  const rejected = assert.rejects(pending); await settle();
  h.pending.resolve({ code: 0, data: { accessToken: 'A2', refreshToken: 'RA2', tenantId: 1, userId: 7 } });
  await rejected; assert.equal(h.requests.length, 1); assert.equal(h.logoutCount(), 1);
});
test('late session proof cannot replace or log out a newer account during refresh', async () => {
  const proof = deferred(); const h = harness({ proof: proof.promise });
  const pending = h.request({ url: '/trade/order/page' }); const rejected = assert.rejects(pending);
  await settle(); h.pending.resolve({ code: 0, data: { accessToken: 'A2', refreshToken: 'RA2', tenantId: 1, userId: 7 } });
  await settle(); assert.equal(h.proofCalls.length, 1);
  h.user.setToken('B', 'RB', { tenantId: 1, userId: 8 }); proof.resolve({ code: 0, data: { tenantId: 1, userId: 7 } });
  await rejected; assert.equal(h.storage.token, 'B'); assert.equal(h.requests.length, 1); assert.equal(h.logoutCount(), 0);
});

for (const result of [{ code: 0, data: { accessToken: 'OLD', refreshToken: 'ROLD', tenantId: 1 } }, { code: 401 }]) {
  test(`late refresh ${result.code === 0 ? 'success' : 'failure'} cannot overwrite or log out a new login`, async () => {
    const h = harness(); const request = h.request({ url: '/trade/cart/list' }); const rejected = assert.rejects(request, /状态已变化/);
    await settle(); h.user.setToken('B', 'RB', { tenantId: 1, userId: 8 });
    h.pending.resolve(result); await rejected;
    assert.equal(h.storage.token, 'B'); assert.equal(h.logoutCount(), 0); assert.equal(h.requests.length, 1);
  });
}

test('login and register SMS are anonymous, while existing password-change SMS keeps account context', async () => {
  const h = harness(); h.storage.token = 'B';
  await h.request({ url: '/member/auth/login', data: { mobile: 'x' } });
  assert.equal(h.requests.at(-1).header.Authorization, undefined);
  await h.request({ url: '/member/auth/send-sms-code', data: { scene: 1 } });
  assert.equal(h.requests.at(-1).header.Authorization, undefined);
  await h.request({ url: '/member/auth/send-sms-code', data: { scene: 3 } });
  assert.equal(h.requests.at(-1).header.Authorization, 'B');
});

test('a write initiated by A while bootstrap waits is never sent using a later B login', async () => {
  const gate = deferred(); const h = harness({ ready: gate.promise });
  const request = h.request({ url: '/trade/cart/add', method: 'POST', data: { skuId: 7 } });
  const rejected = assert.rejects(request, /状态已变化/);
  h.user.setToken('B', 'RB', { tenantId: 1, userId: 8 }); gate.resolve(); await rejected;
  assert.equal(h.requests.length, 0); assert.equal(h.refreshCalls.length, 0);
});

test('blocked deep links settle business requests without an unhandled rejection or clearing the existing login', async () => {
  const h = harness(); h.context.validateEntry(['2001']);
  const result = await h.request({ url: '/product/spu/get-detail', params: { id: 7 } });
  assert.equal(result.code, 403); assert.match(result.msg, /入口尚未开放/);
  assert.equal(h.requests.length, 0); assert.equal(h.storage.token, 'A'); assert.equal(h.logoutCount(), 0);
});

test('unavailable bootstrap never sends a private read, cart write or export', async () => {
  const h = harness({ unconfirmed: true, sessionError: new Error('暂时无法核验') });
  for (const config of [{ url: '/trade/order/page' }, { url: '/trade/cart/add', method: 'POST' }, { url: '/member/purchase-inquiry/export' }]) {
    const result = await h.request(config); assert.notEqual(result.code, 0); assert.equal(result.data, null);
  }
  assert.equal(h.requests.length, 0); assert.equal(h.storage.token, 'A'); assert.equal(h.logoutCount(), 0);
});

test('public GET and auth remain usable anonymously while stored session is unavailable; public POST cannot bypass', async () => {
  const h = harness({ unconfirmed: true, sessionError: new Error('暂时无法核验') });
  for (const config of [{ url: '/product/spu/page' }, { url: '/promotion/diy-template/used' },
    { url: '/member/auth/login', method: 'POST' }]) {
    assert.equal((await h.request(config)).code, 0); assert.equal(h.requests.at(-1).header.Authorization, undefined);
  }
  assert.notEqual((await h.request({ url: '/product/spu/page', method: 'POST' })).code, 0);
  assert.equal(h.requests.length, 3);
});
