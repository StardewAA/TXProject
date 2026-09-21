import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createImageRegistrationState, createCaptchaSession, normalizeLoginName, imageAuthPayload, validateImageChallenge } from '../sheep/helper/image-registration.mjs';
import { createTenantAuthClient } from '../sheep/helper/tenant-auth-client.mjs';

const binding = { tenantId: '1', loginName: 'Buyer_01', captchaSession: 'a'.repeat(32) };
const challenge = { tenantId: 1, challengeId: 'b'.repeat(32), imageDataUrl: 'data:image/png;base64,aGVsbG8=', expiresIn: 300, retryAfter: 2 };
const registration = { ...binding, password: 'Password123', confirmPassword: 'Password123', imageChallengeId: challenge.challengeId, imageCode: 'ab123', agree: true, memberIdentity: 'buyer' };
const identity = { tenantId: 1, userId: 7, accessToken: 'candidate', refreshToken: 'refresh' };
const ok = data => ({ code: 0, data });

test('login names normalize ASCII case and whitespace while rejecting mobile-shaped names', () => {
  assert.equal(normalizeLoginName(' Buyer_01 '), 'buyer_01');
  for (const value of ['19900000000', '123abc', 'abcde', 'a'.repeat(21), '会员12345', 'buy-er01', { toString: () => 'buyer_01' }]) assert.throws(() => normalizeLoginName(value));
});

test('captcha sessions require secure randomness and produce a valid independent form identifier', () => {
  assert.match(createCaptchaSession(), /^[a-f0-9]{32}$/);
  assert.notEqual(createCaptchaSession(), createCaptchaSession());
  assert.throws(() => createCaptchaSession({}), /安全连接/);
});

test('registration strips SMS credentials, privileges and empty optional contacts', () => {
  const data = imageAuthPayload('register-image', { ...registration, mobile: '19900000000', code: '123456', role: 'admin', contactMobile: ' ', nickname: '' });
  assert.equal(data.loginName, 'buyer_01'); assert.equal(data.imageCode, 'AB123');
  for (const field of ['mobile', 'code', 'role', 'contactMobile', 'nickname']) assert.equal(data[field], undefined);
  assert(Object.isFrozen(data));
});

test('image challenges are registration-only and cannot accept malformed sessions or old SMS answers', () => {
  for (const purpose of ['SMS_LOGIN', 'RESET_PASSWORD', undefined]) assert.throws(() => imageAuthPayload('image-challenge', { ...binding, purpose }));
  for (const change of [{ captchaSession: '' }, { imageChallengeId: '123456' }, { imageCode: '123456' }, { imageCode: 'ab1' }, { agree: false }]) {
    assert.throws(() => imageAuthPayload('register-image', { ...registration, ...change }));
  }
  assert.throws(() => imageAuthPayload('__proto__', registration));
});

test('image responses reject remote URLs, active formats, wrong tenant and unbounded lifetimes', () => {
  for (const change of [{ imageDataUrl: 'https://example.invalid/code.png' }, { imageDataUrl: 'data:image/svg+xml;base64,AAAA' }, { imageDataUrl: 'javascript:alert(1)' }, { tenantId: 2 }, { expiresIn: 601 }, { retryAfter: 301 }, { challengeId: 'X' }]) {
    assert.throws(() => validateImageChallenge({ ...challenge, ...change }, '1'));
  }
});

test('refresh retains the previous challenge ID and invalidates the old answer before HTTP completes', () => {
  let now = 0; const state = createImageRegistrationState(() => now);
  const first = state.begin(binding); state.accept(first, challenge);
  assert.equal(state.remaining(), 2); now += 2000;
  const next = state.begin(binding); assert.equal(next.previousChallengeId, challenge.challengeId);
  assert.throws(() => state.payload(binding, 'AB123'));
  state.accept(next, { ...challenge, challengeId: 'c'.repeat(32) });
  assert.equal(state.payload(binding, 'AB123').imageChallengeId, 'c'.repeat(32));
});

test('changing tenants, login names or validation sessions rejects a previously issued image', () => {
  for (const change of [{ tenantId: '2' }, { loginName: 'Buyer_02' }, { captchaSession: 'd'.repeat(32) }]) {
    const state = createImageRegistrationState(); state.accept(state.begin(binding), challenge);
    assert.throws(() => state.payload({ ...binding, ...change }, 'AB123'));
    assert.throws(() => state.payload(binding, 'AB123'));
  }
});

test('expired images and late responses cannot extend server validity', () => {
  let now = 0; const state = createImageRegistrationState(() => now), first = state.begin(binding);
  now = 299000; state.accept(first, challenge); assert.equal(state.expiresIn(), 1);
  now = 300000; assert.equal(state.expiresIn(), 0); assert.throws(() => state.payload(binding, 'AB123'));
  const late = state.begin(binding); now += 301000; assert.throws(() => state.accept(late, challenge));
});

test('invalidating a pending image rejects responses after switching away and back', () => {
  const state = createImageRegistrationState(), ticket = state.begin(binding);
  state.invalidate(); assert.equal(state.accept(ticket, challenge), false);
  assert.throws(() => state.payload(binding, 'AB123'));
});

test('new credentials require a matching candidate session before a caller can store them', async () => {
  for (const operation of ['register-image', 'account-login']) {
    const calls = [], client = createTenantAuthClient(async request => {
      calls.push(request); return ok(request.path.endsWith('/session') ? { tenantId: 1, userId: 7 } : identity);
    });
    const result = await client.execute(operation, operation === 'register-image' ? registration : { tenantId: 1, account: 'Buyer_01', password: 'p123' });
    assert.equal(result.userId, '7'); assert.equal(calls.length, 2);
    assert.equal(calls[0].headers.Authorization, undefined);
    assert.equal(calls[1].path, '/member/mall-tenant/session');
    assert.equal(calls[1].headers.Authorization, 'Bearer candidate');
  }
});

test('committed image registration with failed session proof never retries creating an account', async () => {
  const calls = [], client = createTenantAuthClient(async request => {
    calls.push(request); return request.path.endsWith('/session') ? { code: 401 } : ok(identity);
  });
  await assert.rejects(client.execute('register-image', registration), /请勿重复注册/);
  assert.equal(calls.filter(call => call.path.endsWith('/register-image')).length, 1);
});

test('PC and H5 use the same image registration contract and invalidation rules', () => {
  const h5 = readFileSync(new URL('../sheep/helper/image-registration.mjs', import.meta.url), 'utf8');
  const pc = readFileSync(new URL('../../txgy-mall-web/src/utils/image-registration.mjs', import.meta.url), 'utf8');
  assert.equal(h5.trimEnd(), pc.trimEnd());
});
