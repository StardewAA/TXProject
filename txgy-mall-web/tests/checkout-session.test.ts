import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const checkout = readFileSync(new URL('../src/views/CheckoutView.vue', import.meta.url), 'utf8')

describe('platform checkout session boundary', () => {
  it('uses the platform preview and submit boundary for ordinary cart checkout', () => {
    expect(checkout).toContain('platformCheckoutApi.preview({ cartItemIds: cartItemIds.value, addressId: addressId.value })')
    expect(checkout).toContain('platformCheckoutApi.submit({')
    expect(checkout).toContain('cartItemIds: cartItemIds.value')
    expect(checkout).toContain('idempotencyKey: platformRequestKey.value')
    expect(checkout).not.toContain('tradeApi.createOrder')
  })

  it('keeps the planning-only boundary visible and never pretends an order was created', () => {
    expect(checkout).toContain("platformSubmitError.value = error instanceof Error ? error.message : '平台结算提交失败'")
    expect(checkout).toContain('平台结算未创建任何订单')
    expect(checkout).toContain('当前可保存经服务端复核的不可支付拆单规划')
    expect(checkout).toContain('拆单规划已保存，暂不可支付')
    expect(checkout).toContain('不会创建订单、扣库存或清理购物车')
  })

  it('continues to suppress late account data and serializes address creation', () => {
    expect(checkout).toContain('if (!operation.current()) { discardStaleDraft(); return }')
    expect(checkout).toContain('if (!operation.current() || request !== settlementRequest) return')
    expect(checkout).toContain('if (!operation.current() || savingAddress.value) return')
    expect(checkout).toContain('savingAddress.value = true')
  })

  it('retains the dedicated inquiry-order recovery flow', () => {
    expect(checkout).toContain('existingOrder = false')
    expect(checkout).toContain('if (!existingOrder && data.payOrderId)')
    expect(checkout).toContain('finishCreatedOrder(recovered, false, true)')
    expect(checkout).toContain('finishCreatedOrder(existing, false, true)')
  })
})
