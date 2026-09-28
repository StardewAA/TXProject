import { beforeEach, describe, expect, it, vi } from 'vitest'

const request = vi.hoisted(() => vi.fn())
vi.mock('./http', () => ({ request }))
import { tradeApi } from './mall'

describe('enterprise purchase order api boundary', () => {
  beforeEach(() => request.mockReset())

  it('uses dedicated enterprise endpoints for page and detail', async () => {
    await tradeApi.getPurchaseOrders({ pageNo: 1, pageSize: 10, status: 20 })
    expect(request).toHaveBeenLastCalledWith({
      url: '/trade/purchase-order/page', params: { pageNo: 1, pageSize: 10, status: 20 },
    })
    await tradeApi.getPurchaseOrder(42, true)
    expect(request).toHaveBeenLastCalledWith({
      url: '/trade/purchase-order/get-detail', params: { id: 42, sync: true },
    })
  })

  it('keeps personal order endpoints separate', async () => {
    await tradeApi.getOrders({ pageNo: 1, pageSize: 10 })
    expect(request).toHaveBeenLastCalledWith({
      url: '/trade/order/page', params: { pageNo: 1, pageSize: 10 },
    })
    await tradeApi.getOrder(42, false)
    expect(request).toHaveBeenLastCalledWith({
      url: '/trade/order/get-detail', params: { id: 42, sync: false },
    })
  })

  it('routes enterprise mutations through the independently authorized endpoints', async () => {
    await tradeApi.cancelPurchaseOrder(42)
    expect(request).toHaveBeenLastCalledWith({
      url: '/trade/purchase-order/cancel', method: 'DELETE', params: { id: 42 },
    })
    await tradeApi.receivePurchaseOrder(42)
    expect(request).toHaveBeenLastCalledWith({
      url: '/trade/purchase-order/receive', method: 'PUT', params: { id: 42 },
    })
    await tradeApi.updatePurchaseOrderAddress({ id: 42, addressId: 9 })
    expect(request).toHaveBeenLastCalledWith({
      url: '/trade/purchase-order/update-address', method: 'PUT', data: { id: 42, addressId: 9 },
    })
  })

  it('keeps enterprise after-sales on dedicated endpoints', async () => {
    await tradeApi.getPurchaseAfterSales({ pageNo: 1, pageSize: 20, statuses: 10 })
    expect(request).toHaveBeenLastCalledWith({
      url: '/trade/purchase-after-sale/page', params: { pageNo: 1, pageSize: 20, statuses: 10 },
    })
    await tradeApi.getPurchaseAfterSale(7)
    expect(request).toHaveBeenLastCalledWith({ url: '/trade/purchase-after-sale/get', params: { id: 7 } })
    await tradeApi.createPurchaseAfterSale({ orderItemId: 8, refundPrice: 100 })
    expect(request).toHaveBeenLastCalledWith({
      url: '/trade/purchase-after-sale/create', method: 'POST', data: { orderItemId: 8, refundPrice: 100 },
    })
    await tradeApi.deliverPurchaseAfterSale({ id: 7, logisticsId: 2, logisticsNo: 'SF1' })
    expect(request).toHaveBeenLastCalledWith({
      url: '/trade/purchase-after-sale/delivery', method: 'PUT', data: { id: 7, logisticsId: 2, logisticsNo: 'SF1' },
    })
    await tradeApi.cancelPurchaseAfterSale(7)
    expect(request).toHaveBeenLastCalledWith({
      url: '/trade/purchase-after-sale/cancel', method: 'DELETE', params: { id: 7 },
    })
  })
})
