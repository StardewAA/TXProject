import { beforeEach, expect, it, vi } from 'vitest'
const request = vi.hoisted(() => vi.fn())
vi.mock('./http', () => ({ request }))
import { comparisonApi } from './comparison'
beforeEach(() => request.mockReset())
it('public comparison uses browse scope and only explicit SKU/quantity/preference', async () => {
  const data = { skuIds: ['11', '22'], quantity: 3, preference: 'PRICE' as const }
  await comparisonApi.calculate(data)
  expect(request).toHaveBeenCalledWith({ url: '/product/comparison/calculate', method: 'POST', data, scope: 'browse', headers: { 'x-silent-error': true } })
})
it('saved list reads and CAS writes remain in private account scope', async () => {
  await comparisonApi.list(); expect(request).toHaveBeenLastCalledWith({ url: '/product/comparison/list', headers: { 'x-silent-error': true } })
  const data = { version: 7, skuIds: ['11'], quantity: 3, preference: 'BALANCED' as const }
  await comparisonApi.replace(data)
  expect(request).toHaveBeenLastCalledWith({ url: '/product/comparison/list', method: 'PUT', data, headers: { 'x-silent-error': true } })
})
it('private quote only sends an explicitly selected address identifier, no full address or fake price', async () => {
  const data = { skuIds: ['11'], quantity: 3, addressId: '44' }
  await comparisonApi.quote(data)
  expect(request).toHaveBeenCalledWith({ url: '/trade/product-comparison/quote', method: 'POST', data, headers: { 'x-silent-error': true } })
})
