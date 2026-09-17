import { test } from 'node:test';

import assert from 'node:assert/strict';
import { createTenantAuthClient, tenantAuthPayload, validateTenantIdentity } from '../sheep/helper/tenant-auth-client.mjs';
const ok = data => ({ code: 0, data });
const identity = (tenantId = '9001', userId = '7') => ({ tenantId, userId, accessToken: 'candidate', refreshToken: 'refresh-candidate' });
const credentials = { tenantId: '9001', mobile: '19900000000', password: 'Pass123', code: '123456',
  challengeId: 'a'.repeat(32), nickname: '会员', agree: true, memberIdentity: 'buyer' };
const config = tenantId => ({ protocol: 'challenge-v1', tenantId, available: true, registrationAvailable: true, tenantSelection: false });
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; };
function harness(answer) {
  const calls = [];
  const client = createTenantAuthClient(async input => {
    calls.push(input);
    if (answer) return answer(input);
    return ok(input.path.endsWith('/session') ? { tenantId: '9001', userId: '7' } : identity());
  });
  return { client, calls };
}
test('explicit target discovery is anonymous and does not rewrite legacy context', async () => {
  const h = harness(() => ok(config('9001')));
  const result = await h.client.discover('9001');
  assert.equal(result.tenantId, '9001'); assert.equal(result.tenantSelection, false);
  assert.deepEqual(h.calls[0], { path: '/member/mall-auth/config', method: 'GET', headers: {}, params: { tenantId: '9001' } });
});
test('only absent original entry permits legacy fallback, never a foreign target', async () => {
  for (const response of [{ code: 404 }, Object.assign(new Error('404'), { status: 404 })]) {
    const h = harness(() => { if (response instanceof Error) throw response; return response; });
    assert.equal((await h.client.discover('1')).protocol, 'legacy');
    await assert.rejects(h.client.discover('9001'));
  }
});
test('foreign target cannot accept legacy protocol, wrong target or malformed config', async () => {
  for (const value of [{ ...config('9001'), protocol: 'legacy' }, config('1'), null, { ...config('9001'), available: 1 }]) {
    await assert.rejects(harness(() => ok(value)).client.discover('9001'));
  }
});
test('unavailable discovery is retained without guessing enabled status', async () => {
  const h = harness(() => ok({ ...config('9001'), available: false, registrationAvailable: false }));
  assert.equal((await h.client.discover('9001')).available, false);
});
test('malformed target is blocked before discovery network request', async () => {
  const h = harness();
  for (const value of ['01', 0, -1, true, {}, ['9001'], '9001 ', Number.MAX_SAFE_INTEGER + 1]) {
    await assert.rejects(h.client.discover(value));
  }
  assert.equal(h.calls.length, 0);
});
for (const operation of ['login', 'register', 'sms-login']) test(operation + ' verifies candidate tenant/member before returning tokens', async () => {
  const h = harness();
  const result = await h.client.execute(operation, credentials);
  assert.equal(result.tenantId, '9001'); assert(Object.isFrozen(result));
  assert.equal(h.calls[0].headers.Authorization, undefined);
  assert.equal(h.calls[0].headers['tenant-id'], '9001');
  assert.equal(h.calls[1].path, '/member/mall-tenant/session');
  assert.equal(h.calls[1].headers.Authorization, 'Bearer candidate');
  assert.equal(h.calls[1].headers['tenant-id'], '9001');
});
test('caller cannot inject headers, endpoint, role, enterprise or tenant overrides', async () => {
  const h = harness();
  await h.client.execute('register', { ...credentials, role: 'admin', enterpriseId: 1, headers: { Authorization: 'A' }, url: 'https://external/' });
  assert.equal(h.calls[0].data.role, undefined); assert.equal(h.calls[0].data.enterpriseId, undefined);
  assert.equal(h.calls[0].data.headers, undefined); assert.equal(h.calls[0].data.url, undefined);
  await assert.rejects(h.client.execute('../admin', credentials));
  await assert.rejects(h.client.execute('__proto__', credentials));
  assert.equal(h.calls.length, 2);
});
test('mutable form input is copied before dispatch and is not passed by reference', async () => {
  const input = { ...credentials }; const gate = deferred();
  const h = harness(req => req.path.endsWith('/session') ? ok({ tenantId: '9001', userId: '7' }) : gate.promise);
  const pending = h.client.execute('login', input); input.tenantId = '9002'; input.password = 'changed';
  assert.equal(h.calls[0].data.tenantId, '9001'); assert.equal(h.calls[0].data.password, 'Pass123');
  gate.resolve(ok(identity())); await pending;
});
test('token response missing/wrong tenant, invalid user or token cannot reach session verifier', async () => {
  for (const change of [{ tenantId: undefined }, { tenantId: '1' }, { tenantId: true }, { userId: 0 },
    { userId: [] }, { accessToken: '' }, { accessToken: 'a\r\nX: b' }, { refreshToken: null }]) {
    const h = harness(() => ok({ ...identity(), ...change }));
    await assert.rejects(h.client.execute('login', credentials)); assert.equal(h.calls.length, 1);
  }
});
test('registration proof failure warns against duplicate registration and does not retry creating the member', async () => {
  const h = harness(req => req.path.endsWith('/session') ? { code: 500 } : ok(identity()));
  await assert.rejects(h.client.execute('register', credentials), /请勿重复注册/);
  assert.equal(h.calls.filter(req => req.path.endsWith('/register')).length, 1);
});
test('session identity must match both target tenant and member', async () => {
  for (const proof of [{ tenantId: '1', userId: '7' }, { tenantId: '9001', userId: '8' }, {}, null]) {
    const h = harness(req => ok(req.path.endsWith('/session') ? proof : identity()));
    await assert.rejects(h.client.execute('login', credentials)); assert.equal(h.calls.length, 2);
  }
});
test('session endpoint missing, unavailable or unauthorized cannot downgrade to legacy', async () => {
  for (const code of [404, 500, 401]) {
    const h = harness(req => req.path.endsWith('/session') ? { code } : ok(identity()));
    await assert.rejects(h.client.execute('login', credentials)); assert.equal(h.calls.length, 2);
  }
});
test('invalidated authentication response never starts a candidate session request', async () => {
  let active = true; const gate = deferred(); const h = harness(() => gate.promise);
  const pending = h.client.execute('login', credentials, () => active); active = false; gate.resolve(ok(identity()));
  await assert.rejects(pending, /上下文/); assert.equal(h.calls.length, 1);
});
test('invalidated session proof never returns credentials to caller', async () => {
  let active = true; const gate = deferred();
  const h = harness(req => req.path.endsWith('/session') ? gate.promise : ok(identity()));
  const pending = h.client.execute('login', credentials, () => active);
  for (let i = 0; i < 10; i++) await Promise.resolve();
  assert.equal(h.calls.length, 2); active = false; gate.resolve(ok({ tenantId: '9001', userId: '7' }));
  await assert.rejects(pending, /上下文/);
});
test('already invalid operation makes no network requests', async () => {
  const h = harness(); await assert.rejects(h.client.execute('login', credentials, () => false)); assert.equal(h.calls.length, 0);
});
test('registration validates agreement, role, nickname and challenge before request', async () => {
  const h = harness();
  for (const change of [{ agree: false }, { memberIdentity: 'admin' }, { nickname: ' ' }, { code: '1234' }, { challengeId: '' }, { password: '123' }]) {
    await assert.rejects(h.client.execute('register', { ...credentials, ...change }));
  }
  assert.equal(h.calls.length, 0);
});
test('SMS purpose allowlist rejects login-password or forged business code requests', async () => {
  const h = harness(); for (const purpose of ['PASSWORD_LOGIN', 'CHANGE_MOBILE', null, 1]) {
    await assert.rejects(h.client.execute('send-code', { ...credentials, purpose }));
  }
  assert.equal(h.calls.length, 0);
});
test('send response must bind target and never promises actual delivery', async () => {
  const response = { tenantId: '9001', challengeId: 'a'.repeat(32), expiresIn: 300, retryAfter: 60, deliveryStatus: 'SUBMITTED_UNCONFIRMED' };
  const h = harness(() => ok(response));
  assert.equal((await h.client.execute('send-code', { ...credentials, purpose: 'REGISTER' })).deliveryStatus, 'SUBMITTED_UNCONFIRMED');
  assert.equal(h.calls.length, 1); assert.equal(h.calls[0].data.password, undefined);
  for (const change of [{ tenantId: '1' }, { expiresIn: 9999 }, { retryAfter: 301 }, { deliveryStatus: 'DELIVERED' }]) {
    await assert.rejects(harness(() => ok({ ...response, ...change })).client.execute('send-code', { ...credentials, purpose: 'REGISTER' }));
  }
});
test('reset confirmation uses only target auth endpoint and no token', async () => {
  const h = harness(() => ok(true));
  assert.equal(await h.client.execute('reset-password', credentials), true);
  assert.equal(h.calls[0].headers.Authorization, undefined);
  assert.equal(h.calls[0].data.tenantId, '9001'); assert.equal(h.calls.length, 1);
  await assert.rejects(harness(() => ok(false)).client.execute('reset-password', credentials));
});
test('refresh pins original target/member and verifies the new token without old bearer', async () => {
  const h = harness(); const result = await h.client.refresh(identity());
  assert.equal(result.userId, '7'); assert.equal(h.calls[0].path, '/member/auth/refresh-token');
  assert.equal(h.calls[0].headers.Authorization, undefined);
  assert.deepEqual(h.calls[0].params, { refreshToken: 'refresh-candidate' });
  assert.equal(h.calls[1].headers.Authorization, 'Bearer candidate');
});
test('refresh wrong tenant/member never reaches verifier or returns credentials', async () => {
  for (const next of [identity('1'), identity('9001', '8'), { ...identity(), userId: undefined }]) {
    const h = harness(() => ok(next)); await assert.rejects(h.client.refresh(identity())); assert.equal(h.calls.length, 1);
  }
});
test('late refresh after logout fails before it can replace another account', async () => {
  let current = true; const gate = deferred(); const h = harness(() => gate.promise);
  const pending = h.client.refresh(identity(), () => current); current = false; gate.resolve(ok(identity()));
  await assert.rejects(pending, /上下文/); assert.equal(h.calls.length, 1);
});
test('two target clients have no shared account or mutable tenant state', async () => {
  const h = harness(req => ok(req.path.endsWith('/session') ? { tenantId: req.headers['tenant-id'], userId: '7' } : identity(req.headers['tenant-id'])));
  const [a, b] = await Promise.all([h.client.execute('login', credentials), h.client.execute('login', { ...credentials, tenantId: '9002' })]);
  assert.equal(a.tenantId, '9001'); assert.equal(b.tenantId, '9002');
});
test('server errors do not reflect raw messages with secrets into UI', async () => {
  const h = harness(() => ({ code: 500, msg: 'password=secret123' }));
  await assert.rejects(h.client.execute('login', credentials), error => !error.message.includes('secret123'));
});
test('identity validation does not accept coercible object identifiers', () => {
  assert.throws(() => validateTenantIdentity({ ...identity(), tenantId: { toString: () => '9001' } }, '9001'));
  assert.throws(() => tenantAuthPayload('login', { ...credentials, mobile: 19900000000 }));
});
