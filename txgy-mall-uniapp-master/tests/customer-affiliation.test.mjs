import { test } from 'node:test';
import { normalizeCustomerAffiliation, createCustomerAffiliationLoader, customerAffiliationMessage } from '../sheep/helper/customer-affiliation.mjs';
import assert from 'node:assert/strict';
const active = { state: 'ACTIVE', serviceEnterpriseName: '平台客户服务企业' };
const state = () => ({ result: null, loading: false, error: false, scope: '' });
function deferred() { let resolve, reject; const promise = new Promise((a,b) => { resolve=a; reject=b; }); return { promise, resolve, reject }; }
test('active profile uses only the minimal projection', () => {
  assert.deepEqual(normalizeCustomerAffiliation({ ...active, license: 'private', role: 'supplier' }), active);
});
test('unavailable profiles never expose a stale enterprise name', () => {
  for (const status of ['DISABLED', 'NONE', 'OWNER_UNAVAILABLE', 'TEMPORARILY_UNAVAILABLE']) {
    assert.deepEqual(normalizeCustomerAffiliation({ state: status, serviceEnterpriseName: 'stale' }), { state: status, serviceEnterpriseName: null });
  }
});
test('unknown and malformed responses are not treated as an empty relationship', () => {
  for (const value of [null, {}, { state: 'APPROVED' }, { state: 'ACTIVE' }, { state: 'ACTIVE', serviceEnterpriseName: ' ' }, { state: 'ACTIVE', serviceEnterpriseName: 1 }]) {
    assert.throws(() => normalizeCustomerAffiliation(value));
  }
});
test('anonymous viewer makes no request', async () => {
  const s=state(); let calls=0;
  const loader=createCustomerAffiliationLoader(s,()=>'',async()=>{ calls++; return active; });
  assert.equal(await loader.refresh(),false); assert.equal(calls,0); assert.equal(s.result,null);
});
test('successful response is scoped and no longer loading', async () => {
  const s=state(), loader=createCustomerAffiliationLoader(s,()=>'1:9001:81',async()=>active);
  assert.equal(await loader.refresh(),true); assert.deepEqual(s.result,active); assert.equal(s.loading,false);
});
test('disabled response is explicit and not displayed as an error', async () => {
  const s=state(), loader=createCustomerAffiliationLoader(s,()=>'session',async()=>({ state: 'DISABLED' }));
  await loader.refresh(); assert.equal(s.error,false); assert.equal(customerAffiliationMessage(s),'');
});
test('network failure clears the old owner and offers retry', async () => {
  const s=state(); s.result=active;
  const loader=createCustomerAffiliationLoader(s,()=>'session',async()=>{ throw new Error('private transport'); });
  await loader.refresh(); assert.equal(s.result,null); assert.equal(s.error,true);
  assert.match(customerAffiliationMessage(s),/暂时无法查询/); assert.doesNotMatch(customerAffiliationMessage(s),/private transport/);
});
test('retry recovers without keeping a stale error', async () => {
  const s=state(); let fail=true;
  const loader=createCustomerAffiliationLoader(s,()=>'session',async()=>{ if(fail) throw new Error(); return active; });
  await loader.refresh(); fail=false; await loader.refresh(); assert.equal(s.error,false); assert.deepEqual(s.result,active);
});
test('logout or account/tenant change discards late responses', async () => {
  for (const next of ['', '2:9001:82', '2:9002:81']) {
    const s=state(), pending=deferred(); let scope='1:9001:81';
    const loader=createCustomerAffiliationLoader(s,()=>scope,()=>pending.promise);
    const work=loader.refresh(); scope=next; loader.reset(); pending.resolve(active);
    assert.equal(await work,false); assert.equal(s.result,null); assert.equal(s.loading,false);
  }
});
test('scope change is rejected even before watcher reset', async () => {
  const s=state(), pending=deferred(); let scope='1:9001:81';
  const loader=createCustomerAffiliationLoader(s,()=>scope,()=>pending.promise);
  const work=loader.refresh(); scope='2:9002:81'; pending.resolve(active);
  assert.equal(await work,false); assert.equal(s.result,null);
});
test('A-B-A and unmount invalidate the original request', async () => {
  const s=state(), pending=deferred();
  const loader=createCustomerAffiliationLoader(s,()=>'session-A',()=>pending.promise);
  const work=loader.refresh(); loader.reset(); loader.reset(); pending.resolve(active);
  assert.equal(await work,false); assert.equal(s.result,null);
});
test('older request cannot overwrite a newer result or loading state', async () => {
  const s=state(), first=deferred(), second=deferred(); let calls=0;
  const loader=createCustomerAffiliationLoader(s,()=>'session',()=>++calls===1?first.promise:second.promise);
  const a=loader.refresh(), b=loader.refresh(); first.reject(new Error());
  await a; assert.equal(s.loading,true); assert.equal(s.error,false);
  second.resolve(active); await b; assert.deepEqual(s.result,active); assert.equal(s.loading,false);
});
test('revoked owner and temporary failure have different messages', () => {
  const s=state(); s.result={ state: 'OWNER_UNAVAILABLE' };
  assert.match(customerAffiliationMessage(s),/未被自动变更/);
  s.result={ state: 'TEMPORARILY_UNAVAILABLE' }; assert.match(customerAffiliationMessage(s),/不代表企业资质已失效/);
  s.result={ state: 'NONE' }; assert.match(customerAffiliationMessage(s),/尚未关联/);
});
