import { test } from 'node:test';
import assert from 'node:assert/strict';
import { isPlatformAccountAddress, isPlatformPortalAddress, platformNavigationTarget, platformPublicPath, scopeLocalPath, safeReturnPath, stripPlatformTenantHints, tenantShareLink } from '../sheep/helper/tenant-navigation.mjs';
import { entryHint } from '../sheep/helper/tenant-entry.mjs';
for (const tenant of ['1', '9001']) {
  for (const path of ['/goods/7','/shops/7','/member/orders/7','/purchase/inquiries/7','/sales/shops/7/orders','/pages/goods/index?id=7']) {
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
test('platform home and tenant-slug catalog routes never inherit a tenant id', () => {
  assert.equal(scopeLocalPath('/pages/index/index?tenantId=9001', '9001'), '/pages/index/index');
  assert.equal(scopeLocalPath('/pages/goods/list?portal=1&tenantSlug=kedun&tenant_id=9001', '9001'),
    '/pages/goods/list?portal=1&tenantSlug=kedun');
  assert.equal(platformPublicPath('/pages/goods/list?portal=1&tenantSlug=kedun&tenantId=2024'),
    '/pages/goods/list?portal=1&tenantSlug=kedun');
  assert.equal(scopeLocalPath('/pages/goods/list?portal=1&tenantSlug=kedun&tenantId=2024', '2009'),
    '/pages/goods/list?portal=1&tenantSlug=kedun');
  assert.equal(isPlatformPortalAddress('https://mall/#/pages/goods/list?tenantSlug=kedun&tenantId=1'), true);
  assert.equal(stripPlatformTenantHints('https://mall/#/pages/goods/list?tenantSlug=kedun&tenantId=1'),
    'https://mall/#/pages/goods/list?tenantSlug=kedun');
  assert.equal(scopeLocalPath('/pages/goods/index?portal=1&tenantSlug=kedun&id=7&tenantId=9001', '9001'),
    '/pages/goods/index?portal=1&tenantSlug=kedun&id=7');
  assert.equal(isPlatformPortalAddress('https://mall/#/pages/goods/index?tenantSlug=kedun&id=7&tenantId=1'), true);
  assert.equal(stripPlatformTenantHints('https://mall/#/pages/goods/index?tenantSlug=kedun&id=7&tenantId=1'),
    'https://mall/#/pages/goods/index?tenantSlug=kedun&id=7');
  assert.equal(isPlatformPortalAddress('https://mall/#/pages/order/list?tenantId=1'), false);
  assert.equal(isPlatformAccountAddress('https://mall/pages/index/user?tenantId=1', '1'), true);
  assert.equal(isPlatformAccountAddress('https://mall/#/pages/order/list?tenantId=1', '1'), true);
  assert.equal(isPlatformAccountAddress('https://mall/pages/index/user?tenantId=2009', '1'), false);
  assert.equal(scopeLocalPath('/pages/order/list?tenantId=9001', '9001'), '/pages/order/list?tenantId=9001');
  assert.equal(scopeLocalPath('/pages/index/user?tenant_id=9001', '9001'), '/pages/index/user?tenantId=9001');
});

test('platform cart and checkout routes never inherit a tenant id', () => {
  assert.equal(scopeLocalPath('/pages/platform/cart?tenantId=9001', '9001'), '/pages/platform/cart');
  assert.equal(scopeLocalPath('/pages/platform/checkout?cartItemIds=11%2C12&tenant_id=9001', '9001'),
    '/pages/platform/checkout?cartItemIds=11%2C12');
  assert.equal(isPlatformPortalAddress('https://mall/#/pages/platform/cart?tenantId=1'), true);
  assert.equal(isPlatformPortalAddress('https://mall/#/pages/platform/checkout?cartItemIds=11%2C12'), true);
  assert.equal(platformNavigationTarget('/pages/index/cart', 'https://mall/#/pages/index/index'), '/pages/platform/cart');
  assert.equal(platformNavigationTarget('/pages/index/cart', 'https://mall/#/pages/index/user?tenantId=1'), '/pages/platform/cart');
  assert.equal(platformNavigationTarget('/pages/index/category', 'https://mall/#/pages/index/user?tenantId=1'), '/pages/goods/list?portal=1');
  assert.equal(platformNavigationTarget('/pages/index/search', 'https://mall/#/pages/index/index'), '/pages/goods/list?portal=1');
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
