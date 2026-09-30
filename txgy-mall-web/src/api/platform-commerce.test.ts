import { beforeEach, describe, expect, it, vi } from 'vitest'

const request = vi.hoisted(() => vi.fn())
vi.mock('./http', () => ({ request }))
import { platformCartApi, platformCheckoutApi } from './platform-commerce'

describe('platform commerce api boundary', () => {
  beforeEach(() => request.mockReset())

  it('uses authenticated platform scope without a caller supplied tenant', async () => {
    await platformCartApi.add(601, 2)
    expect(request).toHaveBeenLastCalledWith({
      url: '/trade/platform-cart/add', method: 'POST', data: { skuId: 601, quantity: 2 }, scope: 'platform',
    })
    await platformCartApi.getList()
    expect(request).toHaveBeenLastCalledWith({ url: '/trade/platform-cart/list', scope: 'platform' })
  })

  it('submits only cart ids, address, idempotency key and remark', async () => {
    const data = { cartItemIds: [11, 12], addressId: 9, idempotencyKey: 'platform-test-123', remark: '送货前联系' }
    await platformCheckoutApi.preview({ cartItemIds: data.cartItemIds, addressId: data.addressId })
    expect(request).toHaveBeenLastCalledWith({
      url: '/trade/platform-checkout/preview', method: 'POST',
      data: { cartItemIds: [11, 12], addressId: 9 }, scope: 'platform',
    })
    await platformCheckoutApi.submit(data)
    expect(request).toHaveBeenLastCalledWith({
      url: '/trade/platform-checkout/submit', method: 'POST', data, scope: 'platform',
    })
  })

  it('reads checkout history only in authenticated platform scope', async () => {
    await platformCheckoutApi.page({ pageNo: 2, pageSize: 10 })
    expect(request).toHaveBeenLastCalledWith({
      url: '/trade/platform-checkout/page', params: { pageNo: 2, pageSize: 10 }, scope: 'platform',
    })
    await platformCheckoutApi.get('PC202609210001')
    expect(request).toHaveBeenLastCalledWith({
      url: '/trade/platform-checkout/PC202609210001', scope: 'platform',
    })
  })
})
