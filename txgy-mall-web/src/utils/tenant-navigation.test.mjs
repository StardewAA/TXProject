import { test } from 'vitest';
import assert from 'node:assert/strict';
import { scopeLocalPath, safeReturnPath, tenantShareLink } from './tenant-navigation.mjs';
import { entryHint } from './tenant-entry.mjs';
for (const tenant of ['1', '9001']) {
  for (const path of ['/goods/7','/shops/7','/member/orders/7','/purchase/inquiries/7','/sales/shops/7/orders','/pages/goods/index?id=7','/pages/order/detail?id=7']) {
    test(tenant + ' link preserves resource and survives fresh tab: ' + path, () => {
      const link = scopeLocalPath(path, tenant), original = new URL(path, 'https://mall');
      const result = new URL(link, 'https://mall');
      assert.equal(result.pathname, original.pathname); assert.equal(result.searchParams.get('id'), original.searchParams.get('id'));
      assert.equal(entryHint(undefined, '1', result.href).tenantId, tenant);
      assert.equal(result.searchParams.getAll('tenantId').length, 1);
      assert.equal(scopeLocalPath(link, tenant), link);
    });
  }
}
test('legacy link overrides recipient foreign tab hint without changing credentials', () => {
  const values = new Map([['mall-scoped-entry','9001'],['token','keep']]);
  const storage = {getItem:key=>values.get(key),setItem:(key,value)=>values.set(key,value)};
  const hint = entryHint(storage,'1','https://mall'+scopeLocalPath('/goods/7','1'));
  assert.equal(hint.tenantId,'1'); assert.equal(values.get('token'),'keep');
});
test('query aliases canonicalize, preserve repeated filters, Chinese and hash', () => {
  const link = scopeLocalPath('/search?name=%E4%BF%9D%E6%B8%A9&tag=a&tag=b&tenant-id=9001#spec','9001');
  const u = new URL(link,'https://mall');
  assert.equal(u.searchParams.get('name'),'保温'); assert.deepEqual(u.searchParams.getAll('tag'),['a','b']);
  assert.equal(u.hash,'#spec'); assert.equal(u.searchParams.has('tenant-id'),false);
});
test('conflicting aliases, duplicate hints, malformed hints and hash hints fail closed', () => {
  for (const path of ['/goods/7?tenantId=1','/goods/7?tenant_id=bad','/goods/7?tenantId=9001&tenantId=1',
    '/goods/7?tenantId=9001&tenant-id=1','/goods/7?tenantId=','/goods/7?tenantId=09001',
    '/goods/7?tenantId=9001#/detail?tenant_id=1']) assert.throws(()=>scopeLocalPath(path,'9001'));
});
test('external, protocol-relative, control-character and backslash paths never become local links', () => {
  for (const path of ['https://other/goods/7','//other/goods/7','/\\other/x','/x\n?tenantId=9001','javascript:alert(1)',null,{}]) {
    assert.throws(()=>scopeLocalPath(path,'9001'));
    assert.equal(safeReturnPath(path,'9001'),'/member?tenantId=9001');
  }
});
test('post-login return retains current-tenant order only; foreign redirect is not rewritten', () => {
  assert.equal(safeReturnPath('/member/orders/7?tenantId=9001','9001'),'/member/orders/7?tenantId=9001');
  assert.equal(safeReturnPath('/member/orders/7?tenantId=1','9001'),'/member?tenantId=9001');
});
for (const address of ['', '/pages/index/index?campaign=x', 'https://mall/#/pages/index/index?campaign=x',
  'https://mall/pages/index/index?token=SECRET&code=CODE&redirect=/member/orders/7']) {
  test('public share has one query and no private parameters: '+address,()=>{
    const link=tenantShareLink(address,'0.1.7.1.3','1','https://mall');
    const hint=entryHint(undefined,'9001',link); assert.equal(hint.tenantId,'1'); assert.equal(hint.invalid,false);
    assert(!link.includes('SECRET'));assert(!link.includes('CODE'));assert(!link.includes('/member/orders'));
    assert.equal(link.split('?').length,2);assert(link.includes('spm=0.1.7.1.3'));
  });
}
test('foreign or unsafe share base falls back to current site without leaking source queries',()=>{
  for(const address of ['javascript:alert(1)','https://user:secret@mall/','https://mall/?tenantId=1','https://mall/#/pages/index/index?tenant_id=1']){
    assert.equal(tenantShareLink(address,'0.1.7.1.3','9001','https://mall'),'https://mall/pages/index/index?spm=0.1.7.1.3&tenantId=9001');
  }
  assert.throws(()=>tenantShareLink('','0.1.7&token=secret.1.3','9001','https://mall'));
});
