import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const deferred = () => { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b; }); return { promise, resolve, reject }; };
function instantiate(file, dependencies) {
  const source = readFileSync(new URL(file, import.meta.url), 'utf8').replace(/^import .*;\r?\n/gm, '')
    .replace(/export default (\w+);/, 'return $1;');
  const definition = new Function('defineStore', ...Object.keys(dependencies), source)((_name, options) => options, ...Object.values(dependencies));
  const store = definition.state();
  for (const [name, action] of Object.entries(definition.actions)) store[name] = action.bind(store);
  return store;
}
test('an obsolete cart failure or success cannot erase or replace the next account cart', async () => {
  for (const fail of [true, false]) {
    let generation = 1; const pending = deferred();
    const cart = instantiate('../sheep/store/cart.js', { CartApi: { getCartList: () => pending.promise },
      captureTenantOperation: () => generation, isTenantOperationCurrent: captured => captured === generation });
    const load = cart.getList(); generation++; cart.list = [{ id: 'B' }]; cart.newList = [{ id: 'B' }];
    if (fail) pending.reject(new Error('stale')); else pending.resolve({ code: 0, data: { validList: [{ id: 'A' }] } });
    await load; assert.deepEqual(cart.list, [{ id: 'B' }]); assert.deepEqual(cart.newList, [{ id: 'B' }]);
  }
});

function userHarness({ bindMobile = false } = {}) {
  let generation = 1, derivedCalls = 0, clears = 0; const storage = { token: 'A' };
  const modals = [], platformSessions = [];
  const user = instantiate('../sheep/store/user.js', {
    clone: value => ({ ...value }), cloneDeep: structuredClone, cart: () => ({ emptyList() { clears++; }, getList() { derivedCalls++; } }),
    app: () => ({ platform: { bind_mobile: bindMobile } }), showAuthModal: type => modals.push(type), UserApi: {}, PayWalletApi: {}, OrderApi: {}, CouponApi: {},
    $share: { getShareInfo() { derivedCalls++; }, bindBrokerageUser() { derivedCalls++; } },
    captureTenantOperation: () => generation, isTenantOperationCurrent: captured => captured === generation,
    recordTenantSession() { generation++; },
    recordPlatformSession(token, identity, refreshing) { platformSessions.push({ token, identity, refreshing }); generation++; },
    ensureAccountSession: async () => {}, assertAccountProfile() {},
    readTenantToken: key => storage[key], writeTenantToken: (key, value) => { if (value) storage[key] = value; else delete storage[key]; },
    uni: { getStorageSync: key => storage[key], setStorageSync: (key, value) => { storage[key] = value; }, removeStorageSync: key => { delete storage[key]; } },
  });
  return { user, storage, modals, platformSessions, change: () => { generation++; }, derivedCalls: () => derivedCalls, clears: () => clears };
}

test('login-name accounts keep optional contact without interrupting enterprise completion to bind a phone', async () => {
  for (const [profile, prompts] of [[{ id: 7, loginName: 'buyer_01', contactMobile: '18800000000', mobile: null }, []], [{ id: 7, mobile: null }, ['changeMobile']]]) {
    const h = userHarness({ bindMobile: true }); h.user.userInfo = profile;
    h.user.updateUserData = async () => profile;
    await h.user.loginAfter(); assert.deepEqual(h.modals, prompts);
  }
});
test('new login clears former account assets and the five-second throttle before loading the new member', () => {
  const h = userHarness(); h.user.loginAfter = () => {};
  h.user.userInfo = { id: 7 }; h.user.userWallet = { balance: 9900 }; h.user.numData.orderCount.allCount = 8; h.user.lastUpdateTime = Date.now();
  h.user.setToken('B', 'RB', { tenantId: 1, userId: 8 });
  assert.equal(h.user.userInfo.id, undefined); assert.equal(h.user.userWallet.balance, 0);
  assert.equal(h.user.numData.orderCount.allCount, 0); assert.equal(h.user.lastUpdateTime, 0); assert.equal(h.clears(), 1);
  h.user.lastUpdateTime = 42; h.user.resetUserData(); assert.equal(h.user.lastUpdateTime, 0);
});
test('platform login stores the proven carrier token without starting tenant-owned profile or cart reads', () => {
  const h = userHarness(); h.user.userInfo = { id: 7 }; h.user.userWallet = { balance: 9900 };
  const identity = { tenantId: 2009, userId: 316, sourceTenantId: '2009', canonicalMemberId: '316' };
  assert.equal(h.user.setPlatformToken('P', 'RP', identity), true);
  assert.equal(h.storage.token, 'P'); assert.equal(h.storage['refresh-token'], 'RP');
  assert.deepEqual(h.platformSessions, [{ token: 'P', identity, refreshing: false }]);
  assert.equal(h.user.userInfo.id, undefined); assert.equal(h.user.userWallet.balance, 0);
  assert.equal(h.derivedCalls(), 0); assert.equal(h.clears(), 1);
});
test('old loginAfter cannot launch cart, share or referral actions in the next account', async () => {
  const h = userHarness(), pending = deferred(); h.user.updateUserData = () => pending.promise;
  const loginAfter = h.user.loginAfter(); h.change(); pending.resolve(); await loginAfter;
  assert.equal(h.derivedCalls(), 0);
});

test('unconfirmed profile cannot launch wallet, order counters, cart or referral work', async () => {
  const h = userHarness(); let calls = 0;
  h.user.getInfo = async () => undefined;
  h.user.getWallet = () => { calls++; }; h.user.getNumData = () => { calls++; };
  await h.user.loginAfter(); assert.equal(calls, 0); assert.equal(h.derivedCalls(), 0);
  assert.equal(h.user.lastUpdateTime, 0);
});

test('external session replacement clears private view data without removing the new token', () => {
  const h = userHarness(); h.user.userInfo = { id: 7 }; h.user.userWallet = { balance: 9900 };
  h.user.numData.orderCount.allCount = 99; h.user.lastUpdateTime = 100; h.storage.token = 'B';
  h.user.clearAccountData(); assert.equal(h.user.userInfo.id, undefined); assert.equal(h.user.userWallet.balance, 0);
  assert.equal(h.user.numData.orderCount.allCount, 0); assert.equal(h.storage.token, 'B'); assert.equal(h.clears(), 1);
});
