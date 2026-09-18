import { describe, expect, it } from 'vitest'
import { hasRejectedAfterSale, rejectedAfterSaleTitle } from './after-sale-display'

describe('buyer after-sale result display', () => {
  it('recognizes rejected refund and rejected return receipt', () => {
    expect(hasRejectedAfterSale({ latestAfterSaleStatus: 62 } as never)).toBe(true)
    expect(hasRejectedAfterSale({ latestAfterSaleStatus: 63 } as never)).toBe(true)
    expect(hasRejectedAfterSale({ latestAfterSaleStatus: 50 } as never)).toBe(false)
  })

  it('uses a clear buyer-facing title', () => {
    expect(rejectedAfterSaleTitle({ latestAfterSaleStatus: 62 } as never)).toBe('退款申请已被商家拒绝')
    expect(rejectedAfterSaleTitle({ latestAfterSaleStatus: 63 } as never)).toBe('退货被商家拒收')
  })
})
