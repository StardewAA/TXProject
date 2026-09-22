import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('./http', () => ({ request: vi.fn() }))
import { request } from './http'
import { inquiryMarketApi, inquiryTradeApi } from './inquiry-market'

const mockedRequest = vi.mocked(request)

describe('询价市场 API 合同', () => {
  beforeEach(() => mockedRequest.mockReset())

  it('公开列表和详情固定使用 browse scope', async () => {
    mockedRequest.mockResolvedValueOnce({ list: [{ id: 1, title: '公开需求', status: 1 }], total: 1 })
    await inquiryMarketApi.publicPage({ pageNo: 1, pageSize: 12, state: 'open' })
    expect(mockedRequest).toHaveBeenLastCalledWith({ url: '/product/purchase-inquiry/public-page', params: { pageNo: 1, pageSize: 12, state: 'open' }, scope: 'entry' })
    mockedRequest.mockResolvedValueOnce({ order: { id: 1, title: '公开需求' }, items: [] })
    await inquiryMarketApi.publicGet(1)
    expect(mockedRequest).toHaveBeenLastCalledWith({ url: '/product/purchase-inquiry/public-get', params: { id: 1 }, scope: 'entry' })
  })

  it('供应商单笔与列表接口不混用，并由服务端校验店铺绑定', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 3, inquiryId: 1, version: 0, items: [] })
    await inquiryMarketApi.mine(1, 9)
    expect(mockedRequest).toHaveBeenLastCalledWith({ url: '/product/purchase-inquiry/quote/mine', params: { inquiryId: 1, shopId: 9 } })
    mockedRequest.mockResolvedValueOnce({ list: [], total: 0 })
    await inquiryMarketApi.supplierPage({ shopId: 9, pageNo: 1, pageSize: 12 })
    expect(mockedRequest).toHaveBeenLastCalledWith({ url: '/product/purchase-inquiry/quote/supplier-page', params: { shopId: 9, pageNo: 1, pageSize: 12 } })
  })

  it('保存按整数分、整数 count、版本和幂等键提交', async () => {
    mockedRequest.mockResolvedValueOnce(31)
    const data = { inquiryId: 1, shopId: 9, version: 0, requestKey: 'quote-save-123', freightPrice: 300, deliveryDate: 1_800_000_000_000, validUntil: 1_700_000_000_000, remark: '', items: [{ inquiryItemId: 2, skuId: 8, count: 3, unitPrice: 1250 }] }
    await inquiryMarketApi.save(data)
    expect(mockedRequest).toHaveBeenLastCalledWith({ url: '/product/purchase-inquiry/quote/save', method: 'POST', data })
    mockedRequest.mockResolvedValueOnce(true)
    await inquiryMarketApi.submit(31, 4)
    expect(mockedRequest).toHaveBeenLastCalledWith({ url: '/product/purchase-inquiry/quote/submit', method: 'PUT', params: { id: 31, version: 4 } })
  })

  it('沟通记录按 afterId 继续拉取，超过首页仍能看到新消息', async () => {
    mockedRequest
      .mockResolvedValueOnce(Array.from({ length: 200 }, (_, index) => ({ id: index + 1, quoteId: 31, senderRole: 'BUYER', content: `m${index + 1}` })))
      .mockResolvedValueOnce([{ id: 201, quoteId: 31, senderRole: 'SUPPLIER', content: 'new' }])
    const rows = await inquiryMarketApi.messages(31)
    expect(rows).toHaveLength(201)
    expect(rows.at(-1)?.content).toBe('new')
    expect(mockedRequest).toHaveBeenNthCalledWith(1, { url: '/product/purchase-inquiry/quote/messages', params: { quoteId: 31, afterId: 0, pageSize: 200 }, headers: { 'x-silent-error': true } })
    expect(mockedRequest).toHaveBeenNthCalledWith(2, { url: '/product/purchase-inquiry/quote/messages', params: { quoteId: 31, afterId: 200, pageSize: 200 }, headers: { 'x-silent-error': true } })
  })

  it('成交只调用询价专用结算、创建和订单恢复接口', async () => {
    mockedRequest.mockResolvedValueOnce({ items: [], price: {} })
    await inquiryTradeApi.settlement(31, 6)
    expect(mockedRequest).toHaveBeenLastCalledWith({ url: '/trade/order/inquiry-settlement', params: { quoteId: 31, addressId: 6 } })
    mockedRequest.mockResolvedValueOnce({ id: 99 })
    const data = { quoteId: 31, addressId: 6, remark: '', requestKey: 'inquiry-order-123' }
    await inquiryTradeApi.create(data)
    expect(mockedRequest).toHaveBeenLastCalledWith({ url: '/trade/order/inquiry-create', method: 'POST', data })
    mockedRequest.mockResolvedValueOnce({ id: 99 })
    await inquiryTradeApi.order(31)
    expect(mockedRequest).toHaveBeenLastCalledWith({ url: '/trade/order/inquiry-order', params: { quoteId: 31 }, headers: { 'x-silent-error': true } })
  })
})
