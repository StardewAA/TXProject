import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { ref, computed, watch, reactive, effectScope } from 'vue';
import { parseAuthEntry, assertAuthAvailable, createChallengeState } from '../sheep/helper/auth-challenge.mjs';
import { parseRegistrationOptions, resolveRegistrationChoice, assertRegistrationContext, registrationChoiceNotice } from '../sheep/helper/registration-choice.mjs';
import { createTenantAuthClient } from '../sheep/helper/tenant-auth-client.mjs';
import { createImageRegistrationState, createCaptchaSession, normalizeLoginName } from '../sheep/helper/image-registration.mjs';

const source = readFileSync(new URL('../sheep/hooks/useMallAuthForm.js', import.meta.url), 'utf8')
  .replace(/^import .*;\r?\n/gm, '').replace(/export (function|const) /g, '$1 ');
const settle = async () => { for (let n = 0; n < 15; n++) await Promise.resolve(); };
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; };
const config = { protocol: 'challenge-v1', tenantId: 1, available: true, registrationAvailable: true, tenantSelection: false };
const identity = { accessToken: 'test-access', refreshToken: 'test-refresh', tenantId: 1, userId: 7 };
const challenge = { tenantId: 1, challengeId: 'a'.repeat(32), expiresIn: 300, retryAfter: 60, deliveryStatus: 'SUBMITTED_UNCONFIRMED' };
const imageConfig = { ...config, protocol: 'legacy', registrationMethod: 'image-v1', loginNameEnabled: true };
const imageChallenge = { tenantId: 1, challengeId: 'b'.repeat(32), imageDataUrl: 'data:image/png;base64,aGVsbG8=', expiresIn: 300, retryAfter: 2 };
const ok = data => ({ code: 0, data });
function harness({ purpose = 'PASSWORD_LOGIN', entry = ok(config), options = ok({enabled:false,tenants:[],crossTenantReady:false}), answer, legacyAnswer } = {}) {
  const calls = [], writes = [], toasts = [], cleanup = [], timers = new Set(); let epoch = 0;
  const model = reactive({ mobile: '19900000000', account: '19900000000', password: 'Password123', confirmPassword: 'Password123', code: '', nickname: '测试会员', memberIdentity: 'buyer', loginName: 'Buyer_01', contactMobile: '', imageCode: '' });
  const dependencies = { ref, computed, watch, onMounted() {}, onBeforeUnmount: fn => cleanup.push(fn),
    parseAuthEntry, assertAuthAvailable, createChallengeState,
    createImageRegistrationState, createCaptchaSession, normalizeLoginName,
    parseRegistrationOptions, resolveRegistrationChoice, assertRegistrationContext, registrationChoiceNotice,
    ensureTenantReady: async () => {}, getControlledTenantId: () => '1',
    captureTenantOperation: () => epoch, isTenantOperationCurrent: value => value === epoch,
    $store: () => ({ setToken(...args) { writes.push(args); epoch++; }, logout() { writes.push('logout'); epoch++; } }),
    uni: { showToast: value => toasts.push(value.title) },
    setInterval: fn => { timers.add(fn); return fn; }, clearInterval: fn => timers.delete(fn),
    tenantTransport: async (path, transportOptions) => {
      calls.push({ type: 'new', path, options: transportOptions });
      if (path.endsWith('/config')) { if (entry instanceof Error) throw entry; return entry; }
      if (path.endsWith('/registration-options')) { if (options instanceof Error) throw options; return options; }
      if (answer) return answer(path, transportOptions);
      return ok(path.endsWith('/send-code') ? challenge : path.endsWith('/image-challenge') ? imageChallenge : path.endsWith('/reset-password') ? true : identity);
    },
    request: async options => { calls.push({ type: 'legacy', options }); return legacyAnswer ? legacyAnswer(options) : ok(identity); },
  };
  dependencies.targetAuth = createTenantAuthClient(({ path, method, headers, data, params }) =>
    dependencies.tenantTransport(path + (params ? '?' + new URLSearchParams(params) : ''), { method, header: headers, data }));
  const create = new Function(...Object.keys(dependencies), source + '\nreturn useMallAuthForm;')(...Object.values(dependencies));
  const scope = effectScope(); const form = scope.run(() => create(model, purpose));
  const dispose = () => { cleanup.forEach(fn => fn()); scope.stop(); };
  return { form, model, calls, writes, toasts, timers, dispose, changeSession() { epoch++; } };
}

test('new login uses anonymous tenant-bound route and commits only verified identity', async () => {
  const h = harness(); try {
    await h.form.refresh(); assert.equal(h.form.available.value, true);
    assert.equal((await h.form.submit('login', { ...h.model })).code, 0);
    const call = h.calls.find(call => call.path === '/member/mall-auth/login');
    assert.equal(call.options.header['tenant-id'], '1'); assert.equal(call.options.header.Authorization, undefined);
    assert.equal(call.options.data.tenantId, '1'); assert.equal(h.writes.length, 1);
  } finally { h.dispose(); }
});
test('a successful token response is not stored when session proof has wrong tenant or member', async () => {
  for (const proof of [{ tenantId: 2, userId: 7 }, { tenantId: 1, userId: 8 }, null]) {
    const h = harness({ answer: path => ok(path.endsWith('/session') ? proof : identity) });
    try { await h.form.refresh(); assert.equal(await h.form.submit('login', { ...h.model }), null); assert.equal(h.writes.length, 0); }
    finally { h.dispose(); }
  }
});
test('session proof uses candidate token only, and its failure cannot trigger old auth', async () => {
  const h = harness({ answer: path => path.endsWith('/session') ? { code: 404 } : ok(identity) });
  try {
    await h.form.refresh(); assert.equal(await h.form.submit('login', { ...h.model }), null);
    assert.equal(h.calls.at(-1).options.header.Authorization, 'Bearer test-access');
    assert.equal(h.calls.filter(call => call.type === 'legacy').length, 0); assert.equal(h.writes.length, 0);
  } finally { h.dispose(); }
});
test('closing modal while session verification waits cannot save or toast successful login', async () => {
  const gate = deferred();
  const h = harness({ answer: path => path.endsWith('/session') ? gate.promise : ok(identity) });
  await h.form.refresh(); const pending = h.form.submit('login', { ...h.model }); await settle();
  assert.equal(h.calls.at(-1).path, '/member/mall-tenant/session'); h.dispose(); gate.resolve(ok(identity));
  assert.equal(await pending, null); assert.equal(h.writes.length, 0); assert(!h.toasts.includes('登录成功'));
});

test('closed or absent image capability cannot expose SMS registration or create accounts', async () => {
  for (const entry of [ok(config), ok({ ...config, registrationMethod: 'sms', loginNameEnabled: false }), { code: 404 }]) {
    const h = harness({ purpose: 'REGISTER', entry }); try {
      await h.form.refresh(); assert.equal(h.form.available.value, false);
      await h.form.send(); await h.form.loadImage(); h.model.code = '123456';
      await h.form.submit('register', { ...h.model, agree: true });
      assert.equal(h.calls.filter(call => call.options?.method === 'POST').length, 0);
      assert.equal(h.writes.length, 0);
    } finally { h.dispose(); }
  }
});
test('SMS login and reset use different purposes and correct POST routes', async () => {
  for (const [purpose, kind] of [['SMS_LOGIN', 'sms-login'], ['RESET_PASSWORD', 'reset-password']]) {
    const h = harness({ purpose }); try {
      await h.form.refresh(); await h.form.send(); assert.equal(h.calls.at(-1).options.data.purpose, purpose);
      h.model.code = '123456'; await h.form.submit(kind, { ...h.model });
      const call = h.calls.find(call => call.path === '/member/mall-auth/' + kind);
      assert.equal(call.options.method, 'POST');
      assert.equal(h.writes.length, 1); if (kind === 'reset-password') assert.equal(h.writes[0], 'logout');
    } finally { h.dispose(); }
  }
});
test('only configuration 404 permits legacy compatibility, with deferred token commit', async () => {
  for (const entry of [{ code: 404 }, Object.assign(new Error('not found'), { statusCode: 404 }), ok({ ...config, protocol: 'legacy' })]) {
    const h = harness({ entry }); try {
      await h.form.refresh(); await h.form.submit('login', { ...h.model });
      assert.equal(h.calls.at(-1).type, 'legacy'); assert.equal(h.calls.at(-1).options.url, '/member/auth/login');
      assert.equal(h.calls.at(-1).options.custom.deferAuthToken, true); assert.equal(h.writes.length, 1);
    } finally { h.dispose(); }
  }
});
test('configuration network failure, 500, malformed or foreign target cannot downgrade', async () => {
  for (const entry of [new Error('offline'), { code: 500 }, ok({}), ok({ ...config, tenantId: 88 })]) {
    const h = harness({ entry }); try {
      await h.form.refresh(); assert.equal(h.form.available.value, false); assert(h.form.error.value);
      assert.equal(await h.form.submit('login', { ...h.model }), null);
      assert.equal(h.calls.length, 1); assert.equal(h.writes.length, 0);
    } finally { h.dispose(); }
  }
});
test('unavailable protocol and independently closed registration cannot issue credentials', async () => {
  for (const [purpose, entry] of [['PASSWORD_LOGIN', { ...config, available: false }], ['REGISTER', { ...config, registrationAvailable: false }]]) {
    const h = harness({ purpose, entry: ok(entry) }); try {
      await h.form.refresh(); await h.form.send(); assert.equal(h.form.available.value, false); assert.equal(h.calls.filter(c=>c.options?.method==='POST').length, 0);
    } finally { h.dispose(); }
  }
});
test('new login failure including 404 never retries old auth endpoint', async () => {
  const h = harness({ answer: () => ({ code: 404, msg: 'not found' }) }); try {
    await h.form.refresh(); assert.equal(await h.form.submit('login', { ...h.model }), null);
    assert.equal(h.calls.filter(c => c.type === 'legacy').length, 0); assert.equal(h.writes.length, 0);
  } finally { h.dispose(); }
});
test('duplicate submit issues one request', async () => {
  const pending = deferred(), h = harness({ answer: () => pending.promise }); try {
    await h.form.refresh(); const first = h.form.submit('login', { ...h.model });
    assert.equal(await h.form.submit('login', { ...h.model }), null); assert.equal(h.calls.length, 2);
    pending.resolve(ok(identity)); await first; assert.equal(h.writes.length, 1);
  } finally { h.dispose(); }
});
test('changed mobile or another login prevents stale token commit', async () => {
  for (const action of ['mobile', 'session']) {
    const pending = deferred(), h = harness({ answer: () => pending.promise }); try {
      await h.form.refresh(); const login = h.form.submit('login', { ...h.model });
      if (action === 'mobile') h.model.mobile = '18800000000'; else h.changeSession();
      pending.resolve(ok(identity)); assert.equal(await login, null); assert.equal(h.writes.length, 0);
    } finally { h.dispose(); }
  }
});
test('closing popup discards late login and clears its timer', async () => {
  const pending = deferred(), h = harness({ answer: () => pending.promise });
  await h.form.refresh(); const login = h.form.submit('login', { ...h.model }); h.dispose();
  pending.resolve(ok(identity)); assert.equal(await login, null); assert.equal(h.writes.length, 0); assert.equal(h.timers.size, 0);
});
test('foreign or incomplete login identity cannot write token', async () => {
  for (const data of [{ ...identity, tenantId: 88 }, { ...identity, tenantId: undefined }, { ...identity, refreshToken: '' }, { ...identity, userId: 0 }]) {
    const h = harness({ answer: () => ok(data) }); try {
      await h.form.refresh(); assert.equal(await h.form.submit('login', { ...h.model }), null); assert.equal(h.writes.length, 0);
    } finally { h.dispose(); }
  }
});
test('changing mobile while sending cannot fill challenge or start countdown', async () => {
  const pending = deferred(), h = harness({ purpose: 'SMS_LOGIN', answer: () => pending.promise }); try {
    await h.form.refresh(); const send = h.form.send(); h.model.mobile = '18800000000';
    pending.resolve(ok(challenge)); await send; assert.equal(h.form.countdown.value, 0);
    h.model.code = '123456'; assert.equal(await h.form.submit('sms-login', { ...h.model }), null);
    assert.equal(h.calls.filter(c=>c.options?.method==='POST').length, 1); assert.equal(h.writes.length, 0);
  } finally { h.dispose(); }
});
test('reset cannot log out a new session while response is pending', async () => {
  const pending = deferred(), h = harness({ purpose: 'RESET_PASSWORD', answer: path => path.endsWith('/send-code') ? ok(challenge) : pending.promise }); try {
    await h.form.refresh(); await h.form.send(); h.model.code = '123456';
    const reset = h.form.submit('reset-password', { ...h.model }); h.changeSession();
    pending.resolve(ok(true)); await reset; assert.equal(h.writes.length, 0);
  } finally { h.dispose(); }
});
test('late configuration response after closing form does not populate it', async () => {
  const pending = deferred(), h = harness({ entry: pending.promise });
  const ready = h.form.refresh(); await settle(); h.dispose(); pending.resolve(ok(config)); await ready;
  assert.equal(h.form.entry.value, null); assert.equal(h.timers.size, 0);
});
test('shared PC/H5 challenge semantics stay byte-identical', () => {
  assert.equal(readFileSync(new URL('../sheep/helper/auth-challenge.mjs', import.meta.url), 'utf8'),
    readFileSync(new URL('../../txgy-mall-web/src/utils/auth-challenge.mjs', import.meta.url), 'utf8'));
});

test('configuration refresh invalidates an already pending password login', async () => {
  const pending = deferred(), h = harness({ answer: () => pending.promise }); try {
    await h.form.refresh(); const login = h.form.submit('login', { ...h.model });
    await h.form.refresh(); pending.resolve(ok(identity));
    assert.equal(await login, null); assert.equal(h.writes.length, 0);
  } finally { h.dispose(); }
});
test('validation completing after popup disposal cannot initiate any auth request', async () => {
  const h = harness(); await h.form.refresh(); h.dispose();
  assert.equal(await h.form.submit('login', { ...h.model }), null); await h.form.send(); await h.form.refresh();
  assert.equal(h.calls.length, 1); assert.equal(h.writes.length, 0);
});

const registrationDirectory = ok({enabled:true,publicTenantId:9001,tenants:[{tenantId:9001,name:'公共商城'},{tenantId:1,name:'当前商城'}],crossTenantReady:false});
test('public default cannot issue an unusable foreign-tenant account', async () => {
  const h=harness({purpose:'REGISTER',options:registrationDirectory});try {
    await h.form.refresh();assert.equal(h.form.registrationChoice.value.tenantId,'9001');assert.equal(h.form.available.value,false);
    await h.form.send();h.model.code='123456';await h.form.submit('register',{...h.model,agree:true});
    assert.equal(h.calls.filter(c=>c.options?.method==='POST').length,0);assert.equal(h.writes.length,0);
  }finally{h.dispose();}
});
test('selecting current tenant preserves image registration', async () => {
  const h=harness({purpose:'REGISTER',entry:ok(imageConfig),options:registrationDirectory});try {
    await h.form.refresh();h.form.selectedTenant.value='1';assert.equal(h.form.available.value,true);
    await h.form.loadImage();h.model.imageCode='AB123';await h.form.submit('register',{...h.model,agree:true});
    assert.equal(h.calls.find(call => call.path === '/member/mall-auth/register-image').options.data.tenantId,'1');assert.equal(h.writes.length,1);
  }finally{h.dispose();}
});
test('clearing chosen tenant during pending image request discards old challenge', async () => {
  const pending=deferred(),h=harness({purpose:'REGISTER',entry:ok(imageConfig),options:registrationDirectory,answer:()=>pending.promise});try {
    await h.form.refresh();h.form.selectedTenant.value='1';const send=h.form.loadImage();
    h.form.selectedTenant.value='';h.form.selectedTenant.value='1';pending.resolve(ok(imageChallenge));await send;
    assert.equal(h.form.imageCountdown.value,0);assert.equal(h.model.imageCode,'');
    h.model.imageCode='AB123';await h.form.submit('register',{...h.model,agree:true});assert.equal(h.writes.length,0);
    assert.equal(h.calls.filter(c=>c.options?.method==='POST').length,1);
  }finally{h.dispose();}
});
test('failed directory blocks registration without protocol downgrade', async () => {
  for(const options of [{code:500},ok(null),new Error('offline')]) {
    const h=harness({purpose:'REGISTER',options});try {
      await h.form.refresh();assert.equal(h.form.available.value,false);assert(h.form.error.value);
      await h.form.send();assert.equal(h.calls.filter(c=>c.options?.method==='POST').length,0);assert.equal(h.writes.length,0);
    }finally{h.dispose();}
  }
});
test('closing form before directory arrives does not apply its options', async () => {
  const pending=deferred(),h=harness({purpose:'REGISTER',options:pending.promise});
  const ready=h.form.refresh();await settle();h.dispose();pending.resolve(registrationDirectory);await ready;
  assert.equal(h.form.entry.value,null);assert.equal(h.form.registrationOptions.value,null);
});
test('missing options endpoint keeps fixed-entry image protocol, not legacy auth', async () => {
  const h=harness({purpose:'REGISTER',entry:ok(imageConfig),options:{code:404}});try {
    await h.form.refresh();assert.equal(h.form.entry.value.protocol,'legacy');assert.equal(h.form.registrationOptions.value.enabled,false);
    await h.form.loadImage();assert.equal(h.calls.at(-1).path,'/member/mall-auth/image-challenge');
  }finally{h.dispose();}
});

test('image registration never sends SMS and verifies the server identity before storing tokens', async () => {
  const h = harness({ purpose: 'REGISTER', entry: ok(imageConfig) }); try {
    await h.form.refresh(); assert.equal(h.form.imageRegistration.value, true);
    await h.form.send(); assert.equal(h.calls.filter(call => call.path?.endsWith('/send-code')).length, 0);
    await h.form.loadImage();
    const imageRequest = h.calls.at(-1);
    assert.equal(imageRequest.path, '/member/mall-auth/image-challenge');
    assert.equal(imageRequest.options.data.loginName, 'buyer_01');
    assert.equal(imageRequest.options.data.purpose, 'REGISTER');
    assert.match(imageRequest.options.data.captchaSession, /^[a-f0-9]{32}$/);
    assert.equal(imageRequest.options.data.mobile, undefined);
    h.model.imageCode = 'aB123'; h.model.nickname = ''; h.model.mobile = '';
    const result = await h.form.submit('register', { ...h.model, agree: true, role: 'admin', mobileVerified: true });
    assert.equal(result?.code, 0);
    const registered = h.calls.find(call => call.path === '/member/mall-auth/register-image');
    assert.equal(registered.options.data.imageChallengeId, imageChallenge.challengeId);
    assert.equal(registered.options.data.imageCode, 'AB123');
    assert.equal(registered.options.data.confirmPassword, h.model.password);
    assert.equal(registered.options.data.captchaSession, imageRequest.options.data.captchaSession);
    for (const field of ['mobile', 'code', 'role', 'mobileVerified', 'contactMobile', 'nickname']) assert.equal(registered.options.data[field], undefined);
    assert.equal(h.calls.at(-1).path, '/member/mall-tenant/session'); assert.equal(h.writes.length, 1);
  } finally { h.dispose(); }
});

test('contact mobile is optional unverified information and never becomes identity mobile', async () => {
  const h = harness({ purpose: 'REGISTER', entry: ok(imageConfig) }); try {
    await h.form.refresh(); await h.form.loadImage();
    h.model.imageCode = 'AB123'; h.model.contactMobile = '18800000000';
    await h.form.submit('register', { ...h.model, agree: true });
    const data = h.calls.find(call => call.path === '/member/mall-auth/register-image').options.data;
    assert.equal(data.contactMobile, '18800000000'); assert.equal(data.mobile, undefined);
    assert.equal(data.mobileVerified, undefined);
  } finally { h.dispose(); }
});

test('image registration rejects confirmation, agreement and identity errors before creating a member', async () => {
  for (const change of [{ confirmPassword: 'otherpass' }, { agree: false }, { memberIdentity: 'admin' }, { contactMobile: 'invalid' }]) {
    const h = harness({ purpose: 'REGISTER', entry: ok(imageConfig) }); try {
      await h.form.refresh(); await h.form.loadImage(); h.model.imageCode = 'AB123';
      assert.equal(await h.form.submit('register', { ...h.model, agree: true, ...change }), null);
      assert.equal(h.calls.filter(call => call.path === '/member/mall-auth/register-image').length, 0);
      assert.equal(h.writes.length, 0);
    } finally { h.dispose(); }
  }
});

test('new login accepts normalized login names and legacy mobiles with four-character passwords', async () => {
  for (const [account, expected] of [[' Buyer_01 ', 'buyer_01'], ['19900000000', '19900000000']]) {
    const h = harness({ entry: ok(imageConfig) }); try {
      await h.form.refresh(); h.model.account = account; h.model.password = 'p123';
      assert.equal((await h.form.submit('login', { ...h.model }))?.code, 0);
      const request = h.calls.find(call => call.path === '/member/mall-auth/account-login');
      assert.equal(request.options.data.account, expected); assert.equal(request.options.data.password, 'p123');
      assert.equal(request.options.data.mobile, undefined); assert.equal(h.writes.length, 1);
    } finally { h.dispose(); }
  }
});

test('old phone login keeps four-character password compatibility without image capability', async () => {
  for (const entry of [ok(config), { code: 404 }]) {
    const h = harness({ entry }); try {
      await h.form.refresh(); h.model.password = 'p123';
      assert.equal((await h.form.submit('login', { ...h.model }))?.code, 0); assert.equal(h.writes.length, 1);
    } finally { h.dispose(); }
  }
});

test('image registration rejects unavailable tenant choices and invalid login names before issuing a challenge', async () => {
  const blocked = harness({ purpose: 'REGISTER', entry: ok(imageConfig), options: registrationDirectory });
  try { await blocked.form.refresh(); await blocked.form.loadImage(); assert.equal(blocked.calls.filter(call => call.options?.method === 'POST').length, 0); }
  finally { blocked.dispose(); }
  const invalid = harness({ purpose: 'REGISTER', entry: ok(imageConfig) });
  try { await invalid.form.refresh(); invalid.model.loginName = '123456'; await invalid.form.loadImage(); assert.equal(invalid.calls.filter(call => call.options?.method === 'POST').length, 0); }
  finally { invalid.dispose(); }
});

test('changing the login name, tenant choice or closing the form discards pending image responses', async () => {
  for (const change of ['name', 'tenant', 'close', 'session']) {
    const pending = deferred(), h = harness({ purpose: 'REGISTER', entry: ok(imageConfig), answer: () => pending.promise });
    try {
      await h.form.refresh(); const loading = h.form.loadImage(); await settle();
      if (change === 'name') { h.model.loginName = 'buyer_02'; h.model.loginName = 'Buyer_01'; }
      else if (change === 'tenant') { h.form.selectedTenant.value = '2'; h.form.selectedTenant.value = ''; }
      else if (change === 'session') h.changeSession(); else h.dispose();
      pending.resolve(ok(imageChallenge)); await loading;
      assert.equal(h.form.imageChallenge.value, null); assert.equal(h.model.imageCode, '');
      assert.equal(h.writes.length, 0);
    } finally { h.dispose(); }
  }
});

test('duplicate image requests and an immediate refresh are suppressed', async () => {
  const pending = deferred(), h = harness({ purpose: 'REGISTER', entry: ok(imageConfig), answer: () => pending.promise }); try {
    await h.form.refresh(); const loading = h.form.loadImage(); await h.form.loadImage();
    assert.equal(h.calls.filter(call => call.path === '/member/mall-auth/image-challenge').length, 1);
    pending.resolve(ok(imageChallenge)); await loading; await h.form.loadImage();
    assert.equal(h.calls.filter(call => call.path === '/member/mall-auth/image-challenge').length, 1);
    assert.equal(h.form.imageCountdown.value, 2);
  } finally { h.dispose(); }
});

test('image registration failure clears the image and cannot downgrade or repeat member creation', async () => {
  const h = harness({ purpose: 'REGISTER', entry: ok(imageConfig), answer: path => path.endsWith('/image-challenge') ? ok(imageChallenge) : { code: 404 } }); try {
    await h.form.refresh(); await h.form.loadImage(); h.model.imageCode = 'AB123';
    assert.equal(await h.form.submit('register', { ...h.model, agree: true }), null);
    assert.equal(h.form.imageChallenge.value, null); assert.equal(h.model.imageCode, '');
    h.model.imageCode = 'AB123'; await h.form.submit('register', { ...h.model, agree: true });
    assert.equal(h.calls.filter(call => call.path === '/member/mall-auth/register-image').length, 1);
    assert.equal(h.calls.filter(call => call.type === 'legacy').length, 0); assert.equal(h.writes.length, 0);
  } finally { h.dispose(); }
});

test('late image registration cannot replace a new session or a changed form', async () => {
  for (const change of ['name', 'session', 'close']) {
    const pending = deferred(), h = harness({ purpose: 'REGISTER', entry: ok(imageConfig), answer: path => path.endsWith('/image-challenge') ? ok(imageChallenge) : pending.promise });
    try {
      await h.form.refresh(); await h.form.loadImage(); h.model.imageCode = 'AB123';
      const submitted = h.form.submit('register', { ...h.model, agree: true });
      if (change === 'name') h.model.loginName = 'other_01'; else if (change === 'session') h.changeSession(); else h.dispose();
      pending.resolve(ok(identity)); assert.equal(await submitted, null); assert.equal(h.writes.length, 0);
    } finally { h.dispose(); }
  }
});

test('image capability cannot redirect SMS login and reset to a graphic challenge', async () => {
  for (const [purpose, kind] of [['SMS_LOGIN', 'sms-login'], ['RESET_PASSWORD', 'reset-password']]) {
    const h = harness({ purpose, entry: ok({ ...imageConfig, protocol: 'challenge-v1' }) }); try {
      await h.form.refresh(); await h.form.loadImage(); assert.equal(h.calls.filter(call => call.path === '/member/mall-auth/image-challenge').length, 0);
      await h.form.send(); h.model.code = '123456'; await h.form.submit(kind, { ...h.model });
      assert.equal(h.calls.some(call => call.path === '/member/mall-auth/' + kind), true);
    } finally { h.dispose(); }
  }
});

test('malformed image capability fails closed', async () => {
  for (const invalid of [{ ...imageConfig, registrationMethod: 'custom' }, { ...imageConfig, loginNameEnabled: 'true' }]) {
    const h = harness({ purpose: 'REGISTER', entry: ok(invalid) }); try {
      await h.form.refresh(); assert.equal(h.form.available.value, false); await h.form.loadImage();
      assert.equal(h.calls.filter(call => call.options?.method === 'POST').length, 0);
    } finally { h.dispose(); }
  }
});

test('independent image capability preserves legacy SMS routes and never falls back after image errors', async () => {
  for (const [purpose, kind, expectedPath] of [['SMS_LOGIN', 'sms-login', '/member/auth/sms-login'], ['RESET_PASSWORD', 'reset-password', '/member/user/reset-password']]) {
    const h = harness({ purpose, entry: ok(imageConfig) }); try {
      await h.form.refresh(); assert.equal(h.form.newProtocol.value, false);
      await h.form.send(); assert.equal(h.calls.at(-1).options.url, '/member/auth/send-sms-code');
      h.model.code = '123456'; await h.form.submit(kind, { ...h.model });
      assert.equal(h.calls.at(-1).options.url, expectedPath);
      assert.equal(h.calls.some(call => call.path?.endsWith('/image-challenge')), false);
    } finally { h.dispose(); }
  }
});

test('image signup and account login support both SMS protocol settings with server session proof', async () => {
  for (const protocol of ['legacy', 'challenge-v1']) {
    for (const purpose of ['REGISTER', 'PASSWORD_LOGIN']) {
      const h = harness({ purpose, entry: ok({ ...imageConfig, protocol }) }); try {
        await h.form.refresh(); assert.equal(h.form.available.value, true);
        if (purpose === 'REGISTER') { await h.form.loadImage(); h.model.imageCode = 'AB123'; }
        else h.model.account = 'buyer_01';
        const result = await h.form.submit(purpose === 'REGISTER' ? 'register' : 'login', { ...h.model, agree: true });
        assert.equal(result?.code, 0); assert.equal(h.writes.length, 1);
        const endpoint = purpose === 'REGISTER' ? '/member/mall-auth/register-image' : '/member/mall-auth/account-login';
        assert.equal(h.calls.find(call => call.path === endpoint).options.data.tenantId, '1');
        assert.equal(h.calls.at(-1).path, '/member/mall-tenant/session');
        assert.equal(h.calls.some(call => call.type === 'legacy'), false);
      } finally { h.dispose(); }
    }
  }
});
