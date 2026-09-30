import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createTenantContext, tenantHints } from '../sheep/helper/tenant-context.mjs';
import { createTenantAuthClient, SessionRestoreError } from '../sheep/helper/tenant-auth-client.mjs';
import { entryHint, requireEntry, scopedTokenKey } from '../sheep/helper/tenant-entry.mjs';
import { createTenantUploader } from '../sheep/helper/tenant-upload.mjs';
import { isPlatformPortalAddress, stripPlatformTenantHints } from '../sheep/helper/tenant-navigation.mjs';

const source = readFileSync(new URL('../sheep/helper/tenant-runtime.js', import.meta.url), 'utf8')
  .replace(/^import .*;\r?\n/gm, '').replace(/export /g, '');
const fileSource = readFileSync(new URL('../sheep/api/infra/file.js', import.meta.url), 'utf8')
  .replace(/^import .*;\r?\n/gm, '').replace('export default FileApi;', '');
function harness({ href = 'https://mall/pages/index/user?tenantId=1', sessionTenant = 1, oldServer = false, configError = false, holdConfig = false, answer, initialStorage = {}, directory } = {}) {
  const storage = { token: 'A', 'refresh-token': 'RA', 'tenant-id': '999', ...initialStorage }, calls = [], uploads = [];
  const respond = options => {
    if (answer?.(options)) return;
    if (configError && options.url.endsWith('/config')) { options.fail(new Error('offline')); return; }
    options.success({ statusCode: oldServer && !options.url.endsWith('/member/user/get') ? 404 : 200, data: options.url.endsWith('/member/user/get')
      ? { code: 0, data: { id: 7 } } : options.url.endsWith('/config')
      ? { code: 0, data: directory || { enabled: false, legacyTenantId: 1, publicTenantId: 99, tenants: [] } }
      : { code: 0, data: { tenantId: sessionTenant, userId: 7 } } });
  };
  const uni = { getStorageSync: key => storage[key], showLoading() {}, hideLoading() {}, showToast() {},
    $on() {}, $off() {},
    setStorageSync: (key, value) => { storage[key] = value; }, removeStorageSync: key => { delete storage[key]; },
    request(options) { calls.push(options); if (!holdConfig || !options.url.endsWith('/config')) respond(options); },
    uploadFile(options) { uploads.push(options); },
  };
  const names = ['baseUrl', 'apiPath', 'tenantId', 'createTenantContext', 'tenantHints', 'uni', 'window', 'createTenantAuthClient', 'SessionRestoreError', 'entryHint', 'requireEntry', 'scopedTokenKey', 'isPlatformPortalAddress', 'stripPlatformTenantHints'];
  const runtime = new Function(...names, `${source}\nreturn {tenantContext, ensureTenantReady, ensureAccountSession, prepareTenantUpload, recordTenantSession, recordPlatformSession, verifyPlatformSession, readPlatformCarrier, readPlatformMemberId, captureTenantRequest, isTenantRequestCurrent, captureTenantOperation, isTenantOperationCurrent, getTenantBootstrapStatus, assertAccountProfile};`)
    ('http://test', '/app-api', '1', createTenantContext, tenantHints, uni, { location: { href } }, createTenantAuthClient, SessionRestoreError, entryHint, requireEntry, scopedTokenKey, isPlatformPortalAddress, stripPlatformTenantHints);
  const file = new Function('baseUrl', 'apiPath', 'request', 'prepareTenantUpload', 'isTenantRequestCurrent', 'uni', 'createTenantUploader', `${fileSource}\nreturn FileApi;`)
    ('http://test', '/app-api', () => {}, runtime.prepareTenantUpload, runtime.isTenantRequestCurrent, uni, createTenantUploader);
  return { ...runtime, file, storage, calls, uploads, respond };
}
const settle = async () => { for (let n = 0; n < 30; n++) await Promise.resolve(); };

const scopedDirectory = () => ({ enabled: true, legacyTenantId: 1, publicTenantId: 9001, tenants: [{tenantId:9001,name:'隔离测试商城'}], capabilities:{scopedEntry:true} });
const platformProof = (sourceTenantId = 2009, memberId = 316) => ({
  platformAccountId: 88, platformNo: 'PM0088', canonicalMemberId: memberId,
  legacyMemberId: memberId, sourceTenantId, memberships: [],
});

test('platform portal ignores stale tenant hints and proves the stored source carrier without a tenant header', async () => {
  const h = harness({ href: 'https://mall/pages/index/index?tenantId=9001',
    initialStorage: { 'platform-source-tenant': '2009', 'platform-member-id': '316',
      'token:tenant:9001': 'FOREIGN', 'refresh-token:tenant:9001': 'FOREIGN-R' },
    answer: options => {
      if (options.url.endsWith('/platform-account/session')) {
        assert.equal(options.header.Authorization, 'Bearer A');
        assert.equal(options.header['tenant-id'], undefined);
        options.success({ statusCode: 200, data: { code: 0, data: platformProof() } }); return true;
      }
    } });
  await h.ensureAccountSession();
  assert.equal(h.calls.length, 1); assert.equal(h.calls.some(call => call.url.endsWith('/mall-tenant/config')), false);
  assert.equal(h.tenantContext.state().browseTenantId, '1'); assert.equal(h.tenantContext.state().accountTenantId, '2009');
  assert.equal(h.readPlatformCarrier(), '2009'); assert.equal(h.readPlatformMemberId(), '316');
});

test('platform account page keeps browse context separate and restores the same platform session', async () => {
  const h = harness({ href: 'https://mall/#/pages/order/list?tenantId=9001', directory: scopedDirectory(),
    initialStorage: { 'platform-source-tenant': '2009', 'platform-member-id': '316' },
    answer: options => {
      if (options.url.endsWith('/platform-account/session')) {
        assert.equal(options.header.Authorization, 'Bearer A');
        options.success({ statusCode: 200, data: { code: 0, data: platformProof() } }); return true;
      }
    } });
  await h.ensureAccountSession();
  assert.equal(h.calls.some(call => call.url.endsWith('/mall-tenant/config')), true);
  assert.equal(h.tenantContext.state().browseTenantId, '9001');
  assert.equal(h.tenantContext.state().accountTenantId, '2009');
});

test('platform session keeps its credential slot while a product page browses another tenant', async () => {
  const h = harness({ href: 'https://mall/#/pages/goods/index?id=7&tenantId=9001', directory: scopedDirectory(),
    initialStorage: { 'platform-source-tenant': '2009', 'platform-member-id': '316',
      'token:tenant:9001': 'FOREIGN', 'refresh-token:tenant:9001': 'FOREIGN-R' },
    answer: options => {
      if (options.url.endsWith('/platform-account/session')) {
        assert.equal(options.header.Authorization, 'Bearer A');
        options.success({ statusCode: 200, data: { code: 0, data: platformProof() } }); return true;
      }
    } });
  await h.ensureAccountSession();
  assert.equal(h.tenantContext.state().browseTenantId, '9001');
  assert.equal(h.tenantContext.state().accountTenantId, '2009');
  assert.equal(h.storage['token:tenant:9001'], 'FOREIGN');
});

test('platform startup refresh uses only the persisted source carrier then re-proves the renewed token', async () => {
  let proofCount = 0;
  const h = harness({ href: 'https://mall/pages/index/index',
    initialStorage: { 'platform-source-tenant': '2009', 'platform-member-id': '316' },
    answer: options => {
      if (options.url.endsWith('/platform-account/session')) {
        proofCount++;
        if (proofCount === 1) options.success({ statusCode: 200, data: { code: 401 } });
        else {
          assert.equal(options.header.Authorization, 'Bearer A2');
          assert.equal(options.header['tenant-id'], undefined);
          options.success({ statusCode: 200, data: { code: 0, data: platformProof() } });
        }
        return true;
      }
      if (options.url.includes('/member/auth/refresh-token?')) {
        assert.equal(options.header['tenant-id'], '2009'); assert.equal(options.header.Authorization, undefined);
        options.success({ statusCode: 200, data: { code: 0, data: {
          accessToken: 'A2', refreshToken: 'RA2', tenantId: 2009, userId: 316,
        } } }); return true;
      }
    } });
  await h.ensureAccountSession();
  assert.equal(proofCount, 2); assert.equal(h.storage.token, 'A2'); assert.equal(h.storage['refresh-token'], 'RA2');
  assert.equal(h.readPlatformCarrier(), '2009'); assert.equal(h.readPlatformMemberId(), '316');
});

test('platform session without a trusted carrier fails closed and clears its unusable tokens', async () => {
  const h = harness({ href: 'https://mall/pages/index/index' });
  await assert.rejects(h.ensureAccountSession(), /平台登录已失效/);
  assert.equal(h.calls.length, 0); assert.equal(h.storage.token, undefined); assert.equal(h.storage['refresh-token'], undefined);
});

test('platform session verification failure clears the carrier session instead of borrowing a tenant context', async () => {
  const h = harness({ href: 'https://mall/pages/index/index',
    initialStorage: { 'platform-source-tenant': '2009', 'platform-member-id': '316' },
    answer: options => {
      if (options.url.endsWith('/platform-account/session')) { options.fail(new Error('offline')); return true; }
    } });
  await assert.rejects(h.ensureAccountSession(), /暂时无法核验/);
  assert.equal(h.storage.token, undefined); assert.equal(h.storage['refresh-token'], undefined);
  assert.equal(h.readPlatformCarrier(), ''); assert.equal(h.readPlatformMemberId(), '');
});

test('foreign entry does not discover or reuse legacy token without its own login', async () => {
  const h = harness({href:'https://mall/pages/index/user?tenantId=9001',directory:scopedDirectory()});
  await h.ensureAccountSession(); assert.equal(h.calls.length,1); assert.equal(h.storage.token,'A');
  assert.equal(h.tenantContext.state().browseTenantId,'9001'); assert.equal(h.tenantContext.state().accountTenantId,null);
});
test('foreign proof and upload use only namespaced credentials; invalidation leaves legacy account untouched', async () => {
  const h = harness({href:'https://mall/pages/index/user?tenantId=9001',sessionTenant:9001,directory:scopedDirectory(),initialStorage:{'token:tenant:9001':'B','refresh-token:tenant:9001':'RB'}});
  const upload = await h.prepareTenantUpload(); assert.equal(upload.header['tenant-id'],'9001'); assert.equal(upload.header.Authorization,'B');
  assert.equal(h.calls[1].header.Authorization,'Bearer B');
  assert.throws(()=>h.assertAccountProfile({id:8}),/会员资料/);
  assert.equal(h.storage['token:tenant:9001'],undefined); assert.equal(h.storage.token,'A'); assert.equal(h.storage['refresh-token'],'RA');
});
test('foreign directory revocation is checked again before next upload', async () => {
  const directory=scopedDirectory();
  const h=harness({href:'https://mall/pages/index/user?tenantId=9001',directory,sessionTenant:9001,initialStorage:{'token:tenant:9001':'B','refresh-token:tenant:9001':'RB'}});
  await h.prepareTenantUpload(); directory.capabilities.scopedEntry=false;
  await assert.rejects(h.prepareTenantUpload(),/入口尚未开放/); assert.equal(h.storage['token:tenant:9001'],'B'); assert.equal(h.storage.token,'A');
});
test('foreign session 404 cannot fallback to legacy user profile', async () => {
  const h=harness({href:'https://mall/pages/index/user?tenantId=9001',directory:scopedDirectory(),initialStorage:{'token:tenant:9001':'B','refresh-token:tenant:9001':'RB'},answer:options=>{
    if(options.url.endsWith('/session')){options.success({statusCode:404});return true;}
  }});
  await assert.rejects(h.ensureAccountSession()); assert.equal(h.calls.some(c=>c.url.endsWith('/member/user/get')),false); assert.equal(h.storage.token,'A');
});

test('bootstrap config is anonymous and session verification is pinned without trusting stored tenant', async () => {
  const h = harness(); await Promise.all([h.ensureAccountSession(), h.ensureAccountSession()]);
  assert.equal(h.calls.length, 2); assert.deepEqual(h.calls[0].header, {});
  assert.equal(h.calls[1].header['tenant-id'], '1'); assert.equal(h.calls[1].header.Authorization, 'Bearer A');
  assert.equal(h.tenantContext.state().accountTenantId, '1'); assert.equal(h.getTenantBootstrapStatus().confirmed, true);
});
test('only 404 means old server; network failure remains explicitly unconfirmed', async () => {
  const old = harness({ oldServer: true }); const upload = await old.prepareTenantUpload();
  assert.equal(upload.header['tenant-id'], '1'); assert.equal(old.storage.token, 'A');
  assert.equal(old.getTenantBootstrapStatus().status, 'legacy-server');
  const offline = harness({ configError: true }); await offline.ensureTenantReady();
  assert.deepEqual(offline.getTenantBootstrapStatus(), { status: 'unavailable', confirmed: false });
  assert.equal(offline.tenantContext.state().browseTenantId, '1');
});
test('even an old server rejects a foreign deep link without touching its existing token', async () => {
  const h = harness({ oldServer: true, href: 'https://mall/pages/index/user?tenantId=2' });
  await assert.rejects(h.prepareTenantUpload(), /入口尚未开放/);
  assert.equal(h.calls.length, 1); assert.equal(h.storage.token, 'A');
});
test('server-confirmed foreign account cannot create an upload for the fixed browse tenant', async () => {
  const h = harness({ sessionTenant: 2 }); await assert.rejects(h.file.uploadFile('/tmp/test'), /身份无法确认/);
  assert.equal(h.uploads.length, 0); assert.equal(h.storage.token, undefined);
});
test('actual upload uses verified headers and rejects a late result after login changes', async () => {
  const h = harness(); const upload = h.file.uploadFile('/tmp/test'); const rejected = assert.rejects(upload, /登录状态已变化/);
  await settle(); assert.equal(h.uploads[0].header['tenant-id'], '1'); assert.equal(h.uploads[0].header.Authorization, 'A');
  h.storage.token = 'B'; h.recordTenantSession('B', { tenantId: 1, userId: 8 });
  h.uploads[0].success({ data: JSON.stringify({ code: 0, data: 'old-url' }) }); await rejected;
});
test('upload started before bootstrap cannot acquire a different later account', async () => {
  const h = harness({ holdConfig: true }); const upload = h.file.uploadFile('/tmp/test'); const rejected = assert.rejects(upload, /登录状态已变化/);
  h.storage.token = 'B'; h.recordTenantSession('B', { tenantId: 1, userId: 8 }); h.respond(h.calls[0]); await rejected;
  assert.equal(h.uploads.length, 0); assert.equal(h.calls.length, 1);
});
test('legitimate token rotation preserves initiating account operations; replacement logins do not', async () => {
  const h = harness(); await h.ensureAccountSession(); const operation = h.captureTenantOperation();
  h.storage.token = 'A2'; h.recordTenantSession('A2', { tenantId: 1, userId: 7 }, true);
  assert.equal(h.isTenantOperationCurrent(operation), true);
  h.storage.token = 'B'; assert.equal(h.isTenantOperationCurrent(operation), false);
  h.recordTenantSession('B', { tenantId: 1, userId: 8 }); assert.equal(h.isTenantOperationCurrent(operation), false);
});

test('failed bootstrap proof blocks upload, retains tokens on transient error and retries successfully', async () => {
  let unavailable = true;
  const h = harness({ answer: options => {
    if (unavailable && options.url.endsWith('/session')) { options.success({ statusCode: 503 }); return true; }
  } });
  await assert.rejects(h.prepareTenantUpload(), /暂时无法核验/);
  assert.equal(h.uploads.length, 0); assert.equal(h.storage.token, 'A'); assert.equal(h.tenantContext.state().accountTenantId, null);
  unavailable = false; const next = await h.prepareTenantUpload(); assert.equal(next.header.Authorization, 'A');
  assert.equal(h.calls.filter(c => c.url.endsWith('/session')).length, 2);
});

test('invalid bootstrap member proof clears stored credentials and blocks upload', async () => {
  const h = harness({ answer: options => {
    if (options.url.endsWith('/session')) { options.success({ statusCode: 200, data: { code: 0, data: { tenantId: 1, userId: 0 } } }); return true; }
  } });
  await assert.rejects(h.prepareTenantUpload(), /身份无法确认/);
  assert.equal(h.storage.token, undefined); assert.equal(h.storage['refresh-token'], undefined); assert.equal(h.uploads.length, 0);
});

test('startup expired token rotates once and proves new identity before returning upload headers', async () => {
  const h = harness({ answer: options => {
    if (options.url.includes('/refresh-token?')) {
      assert.equal(options.header.Authorization, undefined);
      options.success({ statusCode: 200, data: { code: 0, data: { tenantId: 1, userId: 7, accessToken: 'A2', refreshToken: 'RA2' } } }); return true;
    }
    if (options.url.endsWith('/session') && options.header.Authorization === 'Bearer A') {
      options.success({ statusCode: 200, data: { code: 401 } }); return true;
    }
  } });
  const operation = h.captureTenantOperation();
  const upload = await h.prepareTenantUpload();
  assert.equal(upload.header.Authorization, 'A2'); assert.equal(h.storage['refresh-token'], 'RA2');
  assert.equal(h.isTenantOperationCurrent(operation), true);
  assert.equal(h.calls.filter(c => c.url.includes('/refresh-token?')).length, 1);
});

test('late bootstrap proof for A cannot authorize upload or clear newer B', async () => {
  for (const code of [0, 403]) {
    let pending;
    const h = harness({ answer: options => {
      if (options.url.endsWith('/session')) { pending = options; return true; }
    } });
    const upload = h.prepareTenantUpload(); const rejected = assert.rejects(upload, /状态已变化/);
    await settle(); h.storage.token = 'B'; h.recordTenantSession('B', { tenantId: 1, userId: 8 });
    pending.success({ statusCode: 200, data: { code, data: { tenantId: 1, userId: 7 } } }); await rejected;
    assert.equal(h.storage.token, 'B'); assert.equal(h.uploads.length, 0);
  }
});

test('profile must match the proven member before populating private stores', async () => {
  for (const profile of [{ id: 8, tenantId: 1 }, { id: 7, tenantId: 2 }, {}]) {
    const h = harness(); await h.ensureAccountSession();
    assert.throws(() => h.assertAccountProfile(profile), /会员资料/);
    assert.equal(h.storage.token, undefined); assert.equal(h.tenantContext.state().accountTenantId, null);
  }
  const h = harness(); await h.ensureAccountSession();
  h.assertAccountProfile({ id: 7 }); assert.equal(h.storage.token, 'A');
});
