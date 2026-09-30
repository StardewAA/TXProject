import { beforeEach, expect, it, vi } from 'vitest'
const request = vi.hoisted(() => vi.fn())
vi.mock('./http', () => ({ request }))
import { fulfillmentApi } from './fulfillment'
beforeEach(() => request.mockReset())
it('logistics read stays in account scope and passes only order id, without force-refresh bypass', async () => {
  await fulfillmentApi.getLogistics(42)
  expect(request).toHaveBeenCalledWith({ url: '/trade/order/get-logistics', params: { id: 42 }, headers: { 'x-silent-error': true } })
})
it('enterprise purchase logistics uses the independently authorized purchase endpoint', async () => {
  await fulfillmentApi.getPurchaseLogistics(43)
  expect(request).toHaveBeenCalledWith({ url: '/trade/purchase-order/get-logistics', params: { id: 43 }, headers: { 'x-silent-error': true } })
})
it('map capability stays in account scope and sends no address, contacts, browser location or secrets', async () => {
  await fulfillmentApi.getLocationCapability()
  expect(request).toHaveBeenCalledWith({ url: '/member/address/location-capability', headers: { 'x-silent-error': true } })
})
