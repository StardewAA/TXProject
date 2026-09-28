import { beforeEach, afterEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
const mocks = vi.hoisted(() => ({ epoch:1, getList:vi.fn(), add:vi.fn(), updateCount:vi.fn(), updateSelected:vi.fn(), delete:vi.fn() }))
vi.mock('@/api/platform-commerce',()=>({platformCartApi:mocks}))
vi.mock('@/api/http',()=>({StaleSessionError:class extends Error {}}))
vi.mock('@/utils/tenant-context',()=>({getTenantContext:()=>({epoch:mocks.epoch}),isCurrentTenantEpoch:(epoch:number)=>epoch===mocks.epoch}))
import { useCartStore } from './cart'
const item=(id=1,quantity=2,valid=true)=>({id,tenantId:2009,enterpriseId:301,shopId:401,spuId:id,spuName:`商品${id}`,skuId:id,price:120,quantity,selected:true,valid,properties:[]})
const response=(valid=[item()],invalid=[item(2,3,false)])=>({itemCount:valid.length+invalid.length,totalQuantity:[...valid,...invalid].reduce((sum,value)=>sum+value.quantity,0),selectedQuantity:valid.filter(value=>value.selected).reduce((sum,value)=>sum+value.quantity,0),tenantGroups:[{tenantId:2009,tenantName:'科盾',shops:[{enterpriseId:301,enterpriseName:'企业',shopId:401,shopName:'店铺',items:[...valid,...invalid]}]}]})
function deferred<T>() { let resolve!:(value:T)=>void;let reject!:(reason:unknown)=>void;const promise=new Promise<T>((a,b)=>{resolve=a;reject=b});return {resolve,reject,promise} }
beforeEach(()=>{
  mocks.epoch=1;for(const value of Object.values(mocks))if(vi.isMockFunction(value))value.mockReset().mockResolvedValue(undefined)
  mocks.getList.mockResolvedValue(response())
  vi.stubGlobal('window',new EventTarget());setActivePinia(createPinia())
})
afterEach(()=>vi.unstubAllGlobals())
it('retains invalid entries for removal while excluding them from selection and totals',async()=>{
  const store=useCartStore();await store.fetchCart();expect(store.invalidItems).toHaveLength(1);expect(store.count).toBe(5)
  expect(store.selectedItems.map(value=>value.id)).toEqual([1]);expect(store.selectedAmount).toBe(240)
})
it('an all-invalid cart is not confused with a genuinely empty cart',async()=>{
  mocks.getList.mockResolvedValue(response([], [item(2,2,false)]));const store=useCartStore();await store.fetchCart()
  expect(store.ready).toBe(true);expect(store.invalidItems).toHaveLength(1);expect(store.selectedItems).toEqual([])
})
it('failure is explicit, disables checkout data, and is not a successful empty result',async()=>{
  const store=useCartStore();await store.fetchCart();mocks.getList.mockRejectedValueOnce(new Error('network'))
  await store.fetchCart();expect(store.ready).toBe(false);expect(store.error).toContain('加载失败');expect(store.items).toEqual([])
  await store.fetchCart();expect(store.ready).toBe(true);expect(store.error).toBe('')
})
it('genuine empty results have no load error',async()=>{
  mocks.getList.mockResolvedValue(response([], []));const store=useCartStore();await store.fetchCart()
  expect(store.ready).toBe(true);expect(store.error).toBe('');expect(store.count).toBe(0)
})
it('select-all includes only valid rows and never invalid row ids',async()=>{
  const store=useCartStore();await store.fetchCart();await store.selectAll(true);expect(mocks.updateSelected).toHaveBeenCalledWith([1],true)
})
it('invalid entries may be explicitly removed but not selected or quantity-edited',async()=>{
  const store=useCartStore();await store.fetchCart();const invalid=store.invalidItems[0]!
  await expect(store.select(invalid,true)).rejects.toThrow();await expect(store.changeCount(invalid,1)).rejects.toThrow()
  await store.remove([2]);expect(mocks.delete).toHaveBeenCalledWith([2]);expect(mocks.updateCount).not.toHaveBeenCalled()
})
it('empty deletion is a no-op and unknown stale ids are not sent',async()=>{
  const store=useCartStore();await store.fetchCart();await store.remove([]);await expect(store.remove([99])).rejects.toThrow()
  expect(mocks.delete).not.toHaveBeenCalled()
})
it('out-of-order reads in the same account cannot overwrite the latest refresh',async()=>{
  const first=deferred<unknown>();mocks.getList.mockReturnValueOnce(first.promise);const store=useCartStore();const old=store.fetchCart()
  mocks.getList.mockResolvedValueOnce(response([item(9)],[]));await store.fetchCart();first.resolve(response([item(1)],[]));await old
  expect(store.items[0]!.id).toBe(9)
})
it('account switch clears ready/error and suppresses a late read',async()=>{
  const wait=deferred<unknown>();mocks.getList.mockReturnValueOnce(wait.promise);const store=useCartStore();const old=store.fetchCart()
  mocks.epoch++;window.dispatchEvent(new Event('mall:tenant-context-updated'));wait.resolve(response([item()],[]));await old
  expect(store.items).toEqual([]);expect(store.ready).toBe(false);expect(store.error).toBe('');expect(store.loading).toBe(false)
})
it('a late mutation cannot fetch or mutate the newly logged-in account',async()=>{
  const store=useCartStore();await store.fetchCart();mocks.getList.mockClear();const wait=deferred<unknown>();mocks.delete.mockReturnValueOnce(wait.promise)
  const old=store.remove([1]);const rejected=expect(old).rejects.toBeInstanceOf(Error)
  mocks.epoch++;window.dispatchEvent(new Event('mall:tenant-context-updated'));wait.resolve(true);await rejected
  expect(mocks.getList).not.toHaveBeenCalled();expect(store.mutating).toBe(false)
})
it('serializes mutations and keeps local quantity until the server confirms',async()=>{
  const store=useCartStore();await store.fetchCart();const wait=deferred<unknown>();mocks.updateCount.mockReturnValueOnce(wait.promise)
  const updating=store.changeCount(store.items[0]!,4);expect(store.items[0]!.quantity).toBe(2)
  await expect(store.remove([1])).rejects.toThrow('正在更新');wait.resolve(true);await updating;expect(store.mutating).toBe(false)
})
it('invalid quantity does not issue a write',async()=>{
  const store=useCartStore();await store.fetchCart();for(const value of [0,1.5,NaN])await expect(store.changeCount(store.items[0]!,value)).rejects.toThrow()
  expect(mocks.updateCount).not.toHaveBeenCalled()
})
