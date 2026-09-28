'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { run, config, boundedText } = require('./readiness-check.cjs');

function goodResponse(url) {
  const path = new URL(url).pathname;
  if (path === '/') return new Response('<html><body>商城</body></html>');
  let value = { code: 0, data: {} }, headers = {};
  if (path.endsWith('/mall-auth/config')) value.data = { registrationMethod: 'image-v1', tenantId: 1 };
  else if (path.endsWith('/mall-tenant/config')) value.data = { enabled: false };
  else if (path.endsWith('/kefu-chat/capability')) {
    value.data = { version: 'scoped-chat-v1', mode: 'FAQ_HUMAN', enabled: false, attachmentsEnabled: false };
    headers = { 'cache-control': 'private, no-store' };
  } else if (path.startsWith('/rpc-api/')) value = { code: 404, data: null };
  else if (path !== '/app-api/trade/config/get') value = { code: 401, data: null };
  return Response.json(value, { headers });
}

test('checks only known harmless GET routes anonymously, without redirects or credentials', async () => {
  const requests = [];
  const report = await run({}, async (url, options) => { requests.push([url, options]); return goodResponse(url); });
  assert.equal(report.ok, true); assert.equal(report.checks.length, 12);
  assert.deepEqual(report.capabilities, { registration: 'image-v1', tenantEntryEnabled: false, scopedChatEnabled: false });
  for (const [url, options] of requests) {
    assert.equal(options.method, 'GET'); assert.equal(options.credentials, 'omit'); assert.equal(options.redirect, 'error');
    assert.equal(options.body, undefined); assert.equal(options.headers.Authorization, undefined);
    assert(!/captcha|send-sms|spu\/get-detail|login|create|open|claim|refund/.test(url));
  }
});

test('rejects non-loopback origins, credentials, paths, and invalid tenant before fetching', async () => {
  for (const gateway of ['https://example.com', 'http://127.0.0.1.example.com', 'file:///x', 'http://u:p@localhost:48080', 'http://localhost:48080/path', 'http://localhost:48080/?token=secret']) {
    await assert.rejects(run({ gateway }, () => assert.fail('must not fetch')));
  }
  for (const tenant of [0, -1, 1.1, NaN, Infinity, Number.MAX_SAFE_INTEGER + 1]) assert.throws(() => config({ tenant }));
  assert.equal(config({ gateway: 'http://[::1]:48080/' }).gateway, 'http://[::1]:48080');
});

test('private endpoint leaking a success response fails without echoing its body', async () => {
  const report = await run({}, async url => url.includes('/pay/order/get')
    ? Response.json({ code: 0, data: { secret: 'PRIVATE_ORDER_SECRET' } }) : goodResponse(url));
  assert.equal(report.ok, false); assert.equal(report.checks.filter(c => !c.ok).length, 1);
  assert(!JSON.stringify(report).includes('PRIVATE_ORDER_SECRET'));
});

test('permission error is not mistaken for an authentication refusal', async () => {
  const report = await run({}, async url => url.includes('/comparison/list')
    ? Response.json({ code: 403, data: null }) : goodResponse(url));
  assert.equal(report.ok, false);
});

test('HTTP 401 authentication response is accepted without needing a JSON body', async () => {
  const report = await run({}, async url => url.includes('/comparison/list')
    ? new Response('Unauthorized', { status: 401 }) : goodResponse(url));
  assert.equal(report.ok, true);
});

test('HTTP 401 with a success code or nonempty private data must fail', async () => {
  for (const body of [{ code: 0, data: null }, { code: 401, data: { secret: 'PRIVATE_VALUE' } }, [{ secret: 'PRIVATE_VALUE' }]]) {
    const report = await run({}, async url => url.includes('/pay/order/get')
      ? Response.json(body, { status: 401 }) : goodResponse(url));
    assert.equal(report.ok, false); assert.equal(report.checks[7].ok, false);
    assert(!JSON.stringify(report).includes('PRIVATE_VALUE'));
  }
});

test('internal endpoint error HTTP statuses cannot hide a successful private response', async () => {
  for (const status of [401, 403, 404]) {
    const report = await run({}, async url => url.includes('/rpc-api/')
      ? Response.json({ code: 0, data: { secret: 'INTERNAL_VALUE' } }, { status }) : goodResponse(url));
    assert.equal(report.ok, false); assert.equal(report.checks[11].ok, false);
    assert(!JSON.stringify(report).includes('INTERNAL_VALUE'));
  }
});

test('wrong registration tenant and incompatible chat contracts fail', async () => {
  const report = await run({}, async url => {
    if (url.includes('/mall-auth/config')) return Response.json({ code: 0, data: { registrationMethod: 'image-v1', tenantId: 3000 } });
    if (url.includes('/kefu-chat/capability')) return Response.json({ code: 0, data: { version: 'legacy', enabled: true } });
    return goodResponse(url);
  });
  assert.equal(report.ok, false); assert.equal(report.checks.filter(c => !c.ok).length, 2);
});

test('enabled flags are reported, never silently enabled or presented as full acceptance', async () => {
  const report = await run({}, async url => {
    const response = goodResponse(url);
    if (!url.includes('/mall-tenant/config') && !url.includes('/kefu-chat/capability')) return response;
    const body = await response.json(); body.data.enabled = true;
    return Response.json(body, { headers: response.headers });
  });
  assert.equal(report.ok, true); assert.equal(report.capabilities.scopedChatEnabled, true);
  assert.equal(report.capabilities.tenantEntryEnabled, true); assert.match(report.scope, /does not prove/);
});

test('network/redirect errors and timeout are isolated and do not echo exception secrets', async () => {
  const report = await run({}, async url => {
    if (url.includes(':15175')) throw new Error('credential SECRET cannot redirect');
    if (url.includes(':3000')) throw new DOMException('private timeout value', 'TimeoutError');
    return goodResponse(url);
  });
  assert.equal(report.ok, false); assert.equal(report.checks.length, 12);
  assert.equal(report.checks[0].reason, 'NETWORK_OR_REDIRECT_ERROR'); assert.equal(report.checks[1].reason, 'TIMEOUT');
  assert(!JSON.stringify(report).includes('SECRET')); assert(!JSON.stringify(report).includes('private timeout value'));
});

test('server error HTML and invalid JSON never pass API checks', async () => {
  const report = await run({}, async url => url.includes('/trade/config/get')
    ? new Response('<html>private error details</html>', { status: 500 }) : goodResponse(url));
  assert.equal(report.ok, false); assert(!JSON.stringify(report).includes('private error details'));
});

test('oversized payload is cancelled and reported without retaining raw contents', async () => {
  let cancelled = false;
  const body = new ReadableStream({ start(controller) { controller.enqueue(new Uint8Array(262145)); }, cancel() { cancelled = true; } });
  await assert.rejects(boundedText(new Response(body)), /BODY_TOO_LARGE/);
  assert.equal(cancelled, true);
});
