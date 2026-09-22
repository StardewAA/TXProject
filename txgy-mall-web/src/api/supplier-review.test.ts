import { beforeEach, describe, expect, it, vi } from 'vitest'
const { requestMock } = vi.hoisted(() => ({ requestMock: vi.fn().mockResolvedValue(true) }))
vi.mock('./http', () => ({ request: requestMock }))
import { supplierApi } from './supplier'

describe('供应商审核API请求契约', () => {
  beforeEach(() => requestMock.mockClear())
  it('送审携带归属与确切版本', async () => {
    await supplierApi.submitReview({ shopId: 7, id: 42, expectedVersion: 3 })
    expect(requestMock).toHaveBeenCalledWith({ url: '/product/supplier-product/review-submit', method: 'POST', data: { shopId: 7, id: 42, expectedVersion: 3 } })
  })
  it('撤回携带原提交批次防止撤回另一次申请', async () => {
    const submissionId = '6063a409-9704-4555-88d6-0351f9ab88b8'
    await supplierApi.withdrawReview({ shopId: 7, id: 42, expectedVersion: 3, submissionId })
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({ url: '/product/supplier-product/review-withdraw', data: { shopId: 7, id: 42, expectedVersion: 3, submissionId } }))
  })
  it('上下架保留原状态API并增加版本', async () => {
    await supplierApi.status(7, 42, 1, 3)
    expect(requestMock).toHaveBeenCalledWith({ url: '/product/supplier-product/status', method: 'PUT', params: { shopId: 7, id: 42, status: 1, expectedVersion: 3 } })
  })
  it('历史只查询当前商品与店铺', async () => {
    await supplierApi.reviewHistory(7, 42)
    expect(requestMock).toHaveBeenCalledWith({ url: '/product/supplier-product/review-history', params: { shopId: 7, id: 42 } })
  })
})
