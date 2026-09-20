import { beforeEach, afterEach, expect, it, vi } from 'vitest'
const state = vi.hoisted(() => ({ browseTenantId:'1', accountTenantId:'1' as string | null, accountUserId:'7' as string | null }))
vi.mock('./tenant-context', () => ({getTenantContext:()=>({...state})}))
import { checkoutDraft } from './checkout-draft'
beforeEach(() => {
  Object.assign(state,{browseTenantId:'1',accountTenantId:'1',accountUserId:'7'})
  const values=new Map<string,string>(); vi.stubGlobal('sessionStorage',{getItem:(key:string)=>values.get(key)??null,setItem:(key:string,value:string)=>values.set(key,value),removeItem:(key:string)=>values.delete(key)})
})
afterEach(()=>vi.unstubAllGlobals())
it('same SKU ids in different tenants or members never share a checkout draft',()=>{
  checkoutDraft.save([{skuId:7,count:1}]); Object.assign(state,{browseTenantId:'9001',accountTenantId:'9001'})
  expect(checkoutDraft.read()).toEqual([]); checkoutDraft.save([{skuId:7,count:2}]);
  state.accountUserId='8'; expect(checkoutDraft.read()).toEqual([])
  state.accountUserId='7'; expect(checkoutDraft.read()).toEqual([{skuId:7,count:2}]);checkoutDraft.clear()
  Object.assign(state,{browseTenantId:'1',accountTenantId:'1'});expect(checkoutDraft.read()).toEqual([{skuId:7,count:1}])
})
it('unverified or foreign account cannot save a draft',()=>{
  state.accountUserId=null; expect(()=>checkoutDraft.save([{skuId:7,count:1}])).toThrow();expect(checkoutDraft.read()).toEqual([])
  state.accountUserId='7';state.accountTenantId='9001';expect(()=>checkoutDraft.save([{skuId:7,count:1}])).toThrow()
})
it('unscoped legacy drafts are never migrated into the new account',()=>{
  sessionStorage.setItem('mall-checkout-items',JSON.stringify([{skuId:7,count:8}]))
  expect(checkoutDraft.read()).toEqual([])
})
it('corrupt draft, invalid quantity and untrusted price fields do not reach settlement',()=>{
  const key='mall-checkout-items:tenant:1:member:7'
  for(const value of ['invalid','{}','[{"skuId":7,"count":-1}]','[{"skuId":"x","count":1}]']){sessionStorage.setItem(key,value);expect(checkoutDraft.read()).toEqual([])}
  sessionStorage.setItem(key,JSON.stringify([{skuId:7,count:1,price:1,tenantId:999}]))
  expect(checkoutDraft.read()).toEqual([{skuId:7,count:1}])
})
it('legacy array migration is stable across reads and new page snapshots',()=>{
  sessionStorage.setItem('mall-checkout-items:tenant:1:member:7',JSON.stringify([{skuId:7,count:1}]))
  const first=checkoutDraft.snapshot(); expect(checkoutDraft.snapshot()?.intentId).toBe(first?.intentId)
})
it('intentional new shopping creates a fresh intent even for identical SKUs',()=>{
  checkoutDraft.save([{skuId:7,count:1}]);const first=checkoutDraft.snapshot();checkoutDraft.save([{skuId:7,count:1}]);expect(checkoutDraft.snapshot()?.intentId).not.toBe(first?.intentId)
})
it('same original draft does not appear under a replacement member',()=>{
  checkoutDraft.save([{skuId:7,count:1}]);state.accountUserId='8';expect(checkoutDraft.snapshot()).toBeNull()
})
it('legacy migration cannot hand out a fresh nonce when its durable write is discarded',()=>{
  sessionStorage.setItem('mall-checkout-items:tenant:1:member:7',JSON.stringify([{skuId:7,count:1}]))
  sessionStorage.setItem=()=>{};expect(checkoutDraft.snapshot()).toBeNull();expect(()=>checkoutDraft.save([{skuId:7,count:1}])).toThrow()
})
