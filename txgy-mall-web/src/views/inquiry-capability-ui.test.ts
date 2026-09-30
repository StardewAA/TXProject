import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const quoteEditor = readFileSync(new URL('./InquiryQuoteView.vue', import.meta.url), 'utf8')
const buyerQuotes = readFileSync(new URL('./InquiryReceivedQuotesView.vue', import.meta.url), 'utf8')
const publicDetail = readFileSync(new URL('./InquiryPublicDetailView.vue', import.meta.url), 'utf8')
const checkout = readFileSync(new URL('./CheckoutView.vue', import.meta.url), 'utf8')

describe('询价动作由服务端 capability 控制', () => {
  it('供应商创建、编辑、提交、撤回不以状态推导', () => {
    expect(quoteEditor).toContain('inquiryQuoteActions(')
    expect(quoteEditor).toContain('v-if="canEdit"')
    expect(quoteEditor).toContain('v-if="canSubmit"')
    expect(quoteEditor).toContain('v-if="canWithdraw"')
    expect(quoteEditor).not.toMatch(/v-if="editor\.status===1"[^>]*撤回/)
  })

  it('买方采纳与转订单分别要求 canAccept、canConvert', () => {
    expect(buyerQuotes).toContain('v-if="actions.accept"')
    expect(buyerQuotes).toContain('v-if="actions.convert"')
    expect(buyerQuotes).not.toMatch(/v-if="selected\.status===1"[^>]*采纳/)
    expect(buyerQuotes).not.toMatch(/v-if="selected\.status===2"[^>]*创建订单/)
  })

  it('公开参与报价入口要求 canQuote', () => {
    expect(publicDetail).toContain('v-if="row.capabilities.canQuote"')
  })

  it('买方详情展示实际 SKU 映射和兼容时间', () => {
    expect(buyerQuotes).toContain('row.productName')
    expect(buyerQuotes).toContain('row.skuId')
    expect(buyerQuotes).toContain('row.skuSpecification')
    expect(buyerQuotes).toContain('quoteDisplayTime(')
  })

  it('报价编辑器使用完整的可报价 SKU 投影而不是不含规格的商品概要分页', () => {
    expect(quoteEditor).toContain('supplierApi.quoteSkus(')
    expect(quoteEditor).not.toContain('supplierApi.page({ shopId: selectedShopId.value')
  })

  it('切换店铺或报价时先清空会话并拒绝迟到的旧报价消息', () => {
    for (const source of [quoteEditor, buyerQuotes]) {
      expect(source).toContain('messages.value = []')
      expect(source).toContain('messageGeneration')
      expect(source).toContain('ticket === messageGeneration')
    }
  })

  it('历史询价订单恢复查询只打开订单详情，不重新进入支付页', () => {
    expect(checkout).toContain('existingOrder = false')
    expect(checkout).toContain('if (!existingOrder && data.payOrderId)')
    expect(checkout).toContain('finishCreatedOrder(recovered, false, true)')
    expect(checkout).toContain('finishCreatedOrder(existing, false, true)')
  })
})
