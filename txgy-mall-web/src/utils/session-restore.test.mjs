import { test } from 'vitest';
import assert from 'node:assert/strict';
import { createTenantAuthClient } from './tenant-auth-client.mjs';

const stored = { tenantId: '1', accessToken: 'saved', refreshToken: 'refresh-saved' };
const ok = data => ({ code: 0, data });
const proof = { tenantId: 1, userId: 7 };
const legacy = { protocol: 'legacy', tenantId: 1, available: true, registrationAvailable: true, tenantSelection: false };
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; };
function harness(answer) {
  const calls = [];
  const client = createTenantAuthClient(async req => { calls.push(req); return answer(req, calls.length); });
  return { client, calls };
}

test('restoring stored tokens requires a server proof and ignores stored member metadata', async () => {
  const h = harness(() => ok(proof));
  const result = await h.client.restoreSession({ ...stored, userId: 999 });
  assert.equal(result.userId, '7'); assert.equal(result.tenantId, '1');
  assert.equal(h.calls.length, 1); assert.equal(h.calls[0].headers.Authorization, 'Bearer saved');
  assert.equal(h.calls[0].headers['tenant-id'], '1');
});
test('previous Bearer-prefixed access token is normalized only for transport', async () => {
  const h = harness(() => ok(proof));
  assert.equal((await h.client.restoreSession({ ...stored, accessToken: 'Bearer saved' })).accessToken, 'Bearer saved');
  assert.equal(h.calls[0].headers.Authorization, 'Bearer saved');
});
for (const value of [null, {}, { tenantId: 2, userId: 7 }, { tenantId: 1, userId: 0 }, { tenantId: 1, userId: '9007199254740992' }]) {
  test(`invalid session proof never authorizes restored account: ${JSON.stringify(value)}`, async () => {
    const h = harness(() => ok(value));
    await assert.rejects(h.client.restoreSession(stored), error => error.kind === 'invalid');
    assert.equal(h.calls.length, 1);
  });
}
for (const status of [403, 500, 503, 'network']) {
  test(`session ${status} neither downgrades nor rotates a stored session`, async () => {
    const h = harness(() => { throw Object.assign(new Error('secret-detail'), { statusCode: status }); });
    await assert.rejects(h.client.restoreSession(stored), error => error.kind === (status === 403 ? 'invalid' : 'unavailable') && !error.message.includes('secret-detail'));
    assert.equal(h.calls.length, 1);
  });
}
test('legacy compatibility requires session 404, legacy discovery and authenticated profile', async () => {
  const h = harness(req => req.path.endsWith('/session') ? { code: 404 }
    : req.path.endsWith('/config') ? ok(legacy) : ok({ id: 7 }));
  assert.equal((await h.client.restoreSession(stored)).userId, '7');
  assert.equal(h.calls.length, 3); assert.deepEqual(h.calls[1].headers, {});
  assert.equal(h.calls[2].headers.Authorization, 'Bearer saved');
});
test('a challenge backend missing session proof cannot fall back to profile', async () => {
  const h = harness(req => req.path.endsWith('/session') ? { code: 404 } : ok({ ...legacy, protocol: 'challenge-v1' }));
  await assert.rejects(h.client.restoreSession(stored), error => error.kind === 'invalid');
  assert.equal(h.calls.length, 2);
});
test('foreign target never uses legacy profile fallback', async () => {
  const h = harness(() => ({ code: 404 }));
  await assert.rejects(h.client.restoreSession({ ...stored, tenantId: '2' }), error => error.kind === 'unavailable');
  assert.equal(h.calls.length, 1);
});
test('wrong legacy profile tenant cannot establish identity', async () => {
  const h = harness(req => req.path.endsWith('/session') ? { code: 404 }
    : req.path.endsWith('/config') ? ok(legacy) : ok({ id: 7, tenantId: 2 }));
  await assert.rejects(h.client.restoreSession(stored), error => error.kind === 'invalid');
});
test('expired stored token rotates once and verifies candidate before returning it', async () => {
  const h = harness((req, n) => n === 1 ? { code: 401 } : req.path.endsWith('refresh-token')
    ? ok({ ...proof, accessToken: 'fresh', refreshToken: 'fresh-refresh' }) : ok(proof));
  const result = await h.client.restoreSession(stored);
  assert.equal(result.accessToken, 'fresh'); assert.equal(h.calls.length, 3);
  assert.equal(h.calls[1].headers.Authorization, undefined);
  assert.deepEqual(h.calls[1].params, { refreshToken: 'refresh-saved' });
  assert.equal(h.calls[2].headers.Authorization, 'Bearer fresh');
});
test('refresh candidate proof must match returned member and cannot loop on 401', async () => {
  for (const response of [ok({ tenantId: 1, userId: 8 }), { code: 401 }, { code: 403 }]) {
    const h = harness((req, n) => n === 1 ? { code: 401 } : req.path.endsWith('refresh-token')
      ? ok({ ...proof, accessToken: 'fresh', refreshToken: 'fresh-refresh' }) : response);
    await assert.rejects(h.client.restoreSession(stored), error => error.kind === 'invalid');
    assert.equal(h.calls.length, 3);
  }
});
test('wrong tenant or invalid tokens on refresh never reach candidate proof', async () => {
  for (const value of [{ tenantId: 2, accessToken: 'x', refreshToken: 'y' }, { tenantId: 1, accessToken: '', refreshToken: 'y' }]) {
    const h = harness((req, n) => n === 1 ? { code: 401 } : ok(value));
    await assert.rejects(h.client.restoreSession(stored), error => error.kind === 'invalid');
    assert.equal(h.calls.length, 2);
  }
});
test('401 without a refresh token requires login and never sends refresh', async () => {
  const h = harness(() => ({ code: 401 }));
  await assert.rejects(h.client.restoreSession({ ...stored, refreshToken: '' }), error => error.kind === 'invalid');
  assert.equal(h.calls.length, 1);
});
test('late proof after a new login cannot authorize or invalidate the newer account', async () => {
  const gate = deferred(); let current = true;
  const h = harness(() => gate.promise);
  const pending = h.client.restoreSession(stored, () => current);
  current = false; gate.resolve(ok(proof));
  await assert.rejects(pending, error => error.kind === 'stale');
});
test('already obsolete restore operation never sends a request', async () => {
  const h = harness(() => ok(proof));
  await assert.rejects(h.client.restoreSession(stored, () => false), error => error.kind === 'stale');
  assert.equal(h.calls.length, 0);
});
