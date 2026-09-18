import { readFileSync } from 'node:fs'
import { computed, reactive, ref } from 'vue'
import { transpileModule, ScriptTarget, ModuleKind } from 'typescript'
import { expect, it, vi } from 'vitest'
import { openCheckoutIntent } from '../src/utils/checkout-intent.mjs'
import { manualAddressPayload } from '../src/utils/fulfillment'

const raw = readFileSync(new URL('../src/views/CheckoutView.vue', import.meta.url), 'utf8')
  .split('<script setup lang="ts">')[1]!.split('</script>')[0]!.replace(/^import[^\n]+\r?\n/gm, '')
const code = transpileModule(raw, { compilerOptions: { target: ScriptTarget.ES2022, module: ModuleKind.None } }).outputText
function deferred<T>() { let resolve!: (value: T) => void; let reject!: (reason: unknown) => void; const promise = new Promise<T>((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }
function harness() {
  let alive = true, epoch = 1, mounted!: () => Promise<void>
  const events = new EventTarget()
  const stored = new Map<string,string>(), localStorage = { getItem: (key:string) => stored.get(key) ?? null, setItem: (key:string,value:string) => stored.set(key,value) }
  const intentId='intent-11111111111111111111111111111111'
  const router = { replace: vi.fn().mockResolvedValue(undefined), push: vi.fn() }
  const message = { success: vi.fn(), warning: vi.fn() }
  const cart = { fetchCart: vi.fn().mockResolvedValue(undefined) }
  const draft = { clear: vi.fn(), read: () => [{ skuId: 211, count: 1 }], snapshot: () => ({intentId,items:[{skuId:211,count:1}]}) }
  const member = { getAddresses: vi.fn().mockResolvedValue([{ id: 1, name: '本机隔离收件人', defaultStatus: true }]), createAddress: vi.fn().mockResolvedValue(7) }
  const trade = { createOrder: vi.fn().mockResolvedValue({ id: 42, payOrderId: 99 }), settlement: vi.fn().mockResolvedValue({ items: [{ skuId: 211, count: 1 }], price: { payPrice: 1000 } }) }
  const dependencies = { computed, reactive, ref, watch: () => {}, onMounted: (fn: () => Promise<void>) => { mounted = fn }, onBeforeUnmount: () => {},
    checkoutDraft: draft, getTenantContext: () => ({ epoch,accountTenantId:'1',accountUserId:'77' }), isCurrentTenantEpoch: (value: number) => value === epoch,openCheckoutIntent,localStorage,
    ElMessage: message, useRouter: () => router, memberApi: member, systemApi: { getAreaTree: vi.fn().mockResolvedValue([]) }, tradeApi: trade,
    useCartStore: () => cart, useAccountOperation: () => ({ current: () => alive && epoch === 1 }), manualAddressPayload, window: events }
  const page = new Function(...Object.keys(dependencies), code + ';checkoutIntent=openCheckoutIntent(localStorage,"'+intentId+'",{tenantId:"1",memberId:"77"});return {items,addresses,addressId,settlement,submitting,submitted,needsVerification,addressForm,addressDialog,savingAddress,submit,saveAddress,settle,openAddressDialog};')(...Object.values(dependencies))
  page.items.value = [{ skuId: 211, count: 1 }]; page.addressId.value = 1; page.settlement.value = { items: [{ skuId: 211, count: 1 }], price: { payPrice: 1000 } }
  Object.assign(page.addressForm, { name: '隔离收件人', mobile: '19900000000', areaId: 1, detailAddress: '隔离地址' })
  return { page, router, message, cart, draft, member, trade, mounted: () => mounted(),
    change() { epoch++; events.dispatchEvent(new Event('mall:token-updated')) }, leave() { alive = false } }
}

it('account switch during post-order cart refresh cannot navigate to or announce the old order', async () => {
  const h = harness(), gate = deferred<void>(); h.cart.fetchCart.mockReturnValue(gate.promise)
  const work = h.page.submit(); await Promise.resolve(); expect(h.cart.fetchCart).toHaveBeenCalledTimes(1)
  h.change(); gate.resolve(); await work
  expect(h.router.replace).not.toHaveBeenCalled(); expect(h.message.success).not.toHaveBeenCalled(); expect(h.page.items.value).toEqual([])
})
it('late created-order result cannot clear a replacement-account draft or launch its cart request', async () => {
  const h = harness(), gate = deferred<{ id: number }>(); h.trade.createOrder.mockReturnValue(gate.promise)
  const work = h.page.submit(); h.change(); gate.resolve({ id: 42 }); await work
  expect(h.draft.clear).not.toHaveBeenCalled(); expect(h.cart.fetchCart).not.toHaveBeenCalled(); expect(h.router.replace).not.toHaveBeenCalled()
})
it('page departure invalidates order navigation even without an account change', async () => {
  const h = harness(), gate = deferred<void>(); h.cart.fetchCart.mockReturnValue(gate.promise)
  const work = h.page.submit(); await Promise.resolve(); h.leave(); gate.resolve(); await work; expect(h.router.replace).not.toHaveBeenCalled()
})
it('a secondary cart refresh failure cannot turn a committed order into a retryable create failure', async () => {
  const h = harness(); h.cart.fetchCart.mockRejectedValue(new Error('cart unavailable')); await h.page.submit()
  expect(h.trade.createOrder).toHaveBeenCalledTimes(1); expect(h.draft.clear).toHaveBeenCalledTimes(1); expect(h.router.replace).toHaveBeenCalledWith('/pay/99?orderId=42')
  expect(h.message.success).toHaveBeenCalledWith('订单创建成功')
})
it('rapid order submit clicks start one create request', async () => {
  const h = harness(), gate = deferred<{ id: number }>(); h.trade.createOrder.mockReturnValue(gate.promise)
  const work = h.page.submit(); await h.page.submit(); expect(h.trade.createOrder).toHaveBeenCalledTimes(1); gate.resolve({ id: 42 }); await work
})
it('address create is serialized before any await and stays successful when its list refresh fails', async () => {
  const h = harness(), gate = deferred<number>(); h.member.createAddress.mockReturnValue(gate.promise); h.member.getAddresses.mockRejectedValue(new Error('list unavailable'))
  h.page.addressDialog.value = true; const work = h.page.saveAddress(); await h.page.saveAddress(); expect(h.member.createAddress).toHaveBeenCalledTimes(1)
  gate.resolve(7); await work; expect(h.page.addressId.value).toBe(7); expect(h.page.addressDialog.value).toBe(false); expect(h.message.success).toHaveBeenCalledWith('收货地址已新增')
})
it('replaced-account address completion cannot read or select an address in the replacement session', async () => {
  const h = harness(), gate = deferred<number>(); h.member.createAddress.mockReturnValue(gate.promise)
  const work = h.page.saveAddress(); h.change(); gate.resolve(7); await work
  expect(h.member.getAddresses).not.toHaveBeenCalled(); expect(h.page.addressId.value).toBeUndefined(); expect(h.message.success).not.toHaveBeenCalled()
})
it('expired address form cannot issue a new create request', async () => {
  const h = harness(); h.change(); await h.page.saveAddress(); expect(h.member.createAddress).not.toHaveBeenCalled()
})
it('late address list and settlement cannot restore private old-account data', async () => {
  const h = harness(), addressGate = deferred<unknown[]>(), priceGate = deferred<unknown>(); h.member.getAddresses.mockReturnValue(addressGate.promise); h.trade.settlement.mockReturnValue(priceGate.promise)
  const loading = h.mounted(), pricing = h.page.settle(); h.change(); addressGate.resolve([{ id: 7, name: '旧账号私有地址' }]); priceGate.resolve({ items: [{ skuId: 211 }], price: { payPrice: 1000 } }); await Promise.all([loading, pricing])
  expect(h.page.addresses.value).toEqual([]); expect(h.page.settlement.value).toBeNull()
})
it('lost create acknowledgment blocks original form retry', async () => {
  const h=harness(); h.trade.createOrder.mockRejectedValue(new Error('timeout')); await h.page.submit(); await h.page.submit();
  expect(h.page.needsVerification.value).toBe(true); expect(h.trade.createOrder).toHaveBeenCalledTimes(1)
})
it('success with missing order id remains uncertain, not a failed-and-retryable order', async () => {
  const h=harness(); h.trade.createOrder.mockResolvedValue(null as never); await h.page.submit(); await h.page.submit();
  expect(h.page.needsVerification.value).toBe(true); expect(h.trade.createOrder).toHaveBeenCalledTimes(1)
})
it('known success followed by failed navigation is never submitted twice', async () => {
  const h=harness(); h.router.replace.mockRejectedValue(new Error('navigation failed')); await h.page.submit(); await h.page.submit();
  expect(h.page.submitted.value).toBe(true); expect(h.trade.createOrder).toHaveBeenCalledTimes(1)
})
it('explicit business rejection permits correction and retry', async () => {
  const h=harness(); h.trade.createOrder.mockRejectedValueOnce({code:1011003001}); await h.page.submit(); expect(h.page.needsVerification.value).toBe(false); await h.page.submit(); expect(h.trade.createOrder).toHaveBeenCalledTimes(2)
})
