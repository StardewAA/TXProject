import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseAuthEntry, assertAuthAvailable, createChallengeState } from '../sheep/helper/auth-challenge.mjs';

const base = { tenantId: '1', mobile: '19900000000', purpose: 'REGISTER', protocol: 'challenge-v1' };
const result = { tenantId: 1, challengeId: 'a'.repeat(32), expiresIn: 300, retryAfter: 60, deliveryStatus: 'SUBMITTED_UNCONFIRMED' };
const validConfig = { tenantId: 1, protocol: 'challenge-v1', available: true, registrationAvailable: true, tenantSelection: true };
test('valid protocol still cannot release tenant selection', () => {
  assert.equal(parseAuthEntry(validConfig, '1').tenantSelection, false);
  assert.equal(parseAuthEntry({ ...validConfig, protocol: 'legacy' }, '1').protocol, 'legacy');
});
test('unknown protocol, malformed flags and foreign tenant are rejected', () => {
  for (const bad of [null, {}, { ...validConfig, protocol: 'v9' }, { ...validConfig, tenantId: 88 },
    { ...validConfig, available: 'true' }, { ...validConfig, registrationAvailable: undefined }, { ...validConfig, tenantSelection: null }]) {
    assert.throws(() => parseAuthEntry(bad, '1'));
  }
});
test('unavailable entry and independently closed registration fail closed', () => {
  assert.throws(() => assertAuthAvailable(null, 'REGISTER'));
  assert.throws(() => assertAuthAvailable({ ...validConfig, available: false }, 'PASSWORD_LOGIN'));
  assert.throws(() => assertAuthAvailable({ ...validConfig, registrationAvailable: false }, 'REGISTER'));
  assert.doesNotThrow(() => assertAuthAvailable({ ...validConfig, registrationAvailable: false }, 'PASSWORD_LOGIN'));
});
test('challenge is required and binds a six-digit code to explicit tenant', () => {
  const state = createChallengeState();
  assert.throws(() => state.payload(base, '123456'));
  const ticket = state.begin(base); assert.equal(state.accept(ticket, result), true);
  assert.deepEqual(state.payload(base, '123456'), { tenantId: '1', challengeId: result.challengeId });
  for (const code of ['1234', '1234567', 'abcdef', '']) assert.throws(() => state.payload(base, code));
});
test('changed mobile, tenant, purpose or protocol cannot reuse a challenge', () => {
  for (const changed of [{ mobile: '18800000000' }, { tenantId: '88' }, { purpose: 'SMS_LOGIN' }]) {
    const state = createChallengeState(); state.accept(state.begin(base), result);
    assert.throws(() => state.payload({ ...base, ...changed }, '123456'));
    assert.throws(() => state.payload(base, '123456'));
  }
});
test('late send response after A-B-A changes cannot populate the current form', () => {
  const state = createChallengeState(), ticket = state.begin(base);
  state.change({ ...base, mobile: '18800000000' }); state.change(base);
  assert.equal(state.accept(ticket, result), false);
  assert.throws(() => state.payload(base, '123456'));
});
test('unmounted or invalidated form ignores late response', () => {
  const state = createChallengeState(), ticket = state.begin(base);
  state.invalidate(); assert.equal(state.current(ticket), false); assert.equal(state.accept(ticket, result), false);
});
test('duplicate send suppressed; finished old request cannot overwrite new send', () => {
  const state = createChallengeState(), first = state.begin(base);
  assert.throws(() => state.begin(base)); state.finish(first);
  const next = state.begin(base); state.finish(first);
  assert.equal(state.accept(first, result), false); assert.equal(state.accept(next, result), true);
});
test('cooldown spans purpose and survives mobile changes in this form', () => {
  let now = 1000; const state = createChallengeState(() => now);
  state.accept(state.begin(base), result); assert.equal(state.remaining(base.mobile), 60);
  assert.throws(() => state.begin({ ...base, purpose: 'SMS_LOGIN' }));
  state.change({ ...base, mobile: '18800000000' }); state.change(base);
  assert.equal(state.remaining(base.mobile), 60); now += 60000; assert.equal(state.remaining(base.mobile), 0);
  assert.doesNotThrow(() => state.begin(base));
});
test('expiry starts before request and cannot be extended by network latency', () => {
  let now = 1000; const state = createChallengeState(() => now), ticket = state.begin(base);
  now += 290000; state.accept(ticket, result); state.payload(base, '123456');
  now += 10000; assert.throws(() => state.payload(base, '123456'));
});
test('response received after expiry is rejected', () => {
  let now = 1000; const state = createChallengeState(() => now), ticket = state.begin(base);
  now += 301000; assert.throws(() => state.accept(ticket, result));
});
test('foreign tenant, missing ID, invalid TTL or delivery state rejects response', () => {
  for (const bad of [{ ...result, tenantId: 88 }, { ...result, challengeId: 'x' },
    { ...result, expiresIn: 0 }, { ...result, expiresIn: 601 }, { ...result, retryAfter: 400 },
    { ...result, deliveryStatus: 'DELIVERED' }, null]) {
    const state = createChallengeState(), ticket = state.begin(base);
    assert.throws(() => state.accept(ticket, bad)); state.finish(ticket);
    assert.throws(() => state.payload(base, '123456'));
  }
});
test('legacy mode sends no fake challenge and still supports local countdown', () => {
  const state = createChallengeState(), legacy = { ...base, protocol: 'legacy' };
  assert.deepEqual(state.payload(legacy, '1234'), {});
  state.accept(state.begin(legacy), true); assert.equal(state.remaining(base.mobile), 60);
});
