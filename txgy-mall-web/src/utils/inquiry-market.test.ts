import { describe, expect, it } from 'vitest'
import {
  inquiryQuoteActions,
  quoteAmount,
  quoteDisplayTime,
  quoteStatusText,
  sanitizeInquiryMessages,
  sanitizeInquiryQuote,
  sanitizePublicInquiry,
  yuanToCents,
} from './inquiry-market'

describe('公开询价边界', () => {
  it('仅复制公开 allow-list，并保留最多三位小数的需求量', () => {
    const result = sanitizePublicInquiry({
      order: {
        id: 7,
        no: 'XJ7',
        title: '管道保温材料',
        itemSummary: '保温棉、岩棉板',
        enterpriseName: '示例企业',
        deliveryArea: '石家庄市',
        deadline: 1_800_000_000_000,
        status: 1,
        canQuote: true,
        memberId: 991,
        contactName: '不应进入页面状态',
        contactMobile: '13800000000',
        address: '完整门牌地址',
      },
      remark: '按图纸报价',
      items: [{ id: 11, name: '保温棉', quantity: '1.125', unit: '立方米', remark: '防水包装', supplierCost: 1 }],
    })
    expect(result.items[0]?.quantity).toBe(1.125)
    expect(result.addressSummary).toBe('石家庄市')
    expect(result.itemSummary).toBe('保温棉、岩棉板')
    expect(result.remark).toBe('按图纸报价')
    expect(result.items[0]?.remark).toBe('防水包装')
    expect(result.capabilities.canQuote).toBe(true)
    expect(result).not.toHaveProperty('memberId')
    expect(result).not.toHaveProperty('contactName')
    expect(result).not.toHaveProperty('contactMobile')
    expect(result).not.toHaveProperty('address')
    expect(result.items[0]).not.toHaveProperty('supplierCost')
  })

  it('拒绝精度超过三位或非正数的公开需求量', () => {
    expect(sanitizePublicInquiry({ id: 1, title: 'A', items: [{ name: 'A', quantity: 1.1259 }] }).items[0]?.quantity).toBe(1)
    expect(sanitizePublicInquiry({ id: 1, title: 'A', items: [{ name: 'A', quantity: 0 }] }).items[0]?.quantity).toBe(1)
  })
})

describe('报价映射', () => {
  it('将合法两位小数金额稳定转换为整数分', () => {
    expect(yuanToCents(19.99)).toBe(1999)
    expect(yuanToCents(0.1)).toBe(10)
    expect(() => yuanToCents(19.999)).toThrow('两位小数')
    expect(() => yuanToCents(-1)).toThrow('非负金额')
  })

  it('使用整数 count、版本和服务端能力字段', () => {
    const quote = sanitizeInquiryQuote({
      id: 31,
      inquiryId: 7,
      version: 4,
      status: 2,
      freightPrice: 300,
      canConvert: true,
      submittedAt: '2026-09-21T10:00:00',
      items: [{ inquiryItemId: 11, skuId: 88, productName: '耐高温岩棉板', skuName: '100mm 加厚款', skuSpecification: '1200×600×100mm', quantity: 1.125, count: 3, unitPrice: 1250, totalPrice: 3750 }],
    })
    expect(quote.version).toBe(4)
    expect(quote.items[0]).toMatchObject({ skuId: 88, productName: '耐高温岩棉板', skuName: '100mm 加厚款', skuSpecification: '1200×600×100mm', quantity: 1.125, orderCount: 3, unitPrice: 1250, amount: 3750 })
    expect(quote.capabilities.canConvert).toBe(true)
    expect(quote.capabilities.canCheckout).toBe(true)
    expect(quoteDisplayTime(quote)).toBe('2026-09-21T10:00:00')
    expect(quoteAmount(quote.items, quote.freightPrice)).toBe(4050)
    expect(quoteStatusText(2)).toBe('已采纳')
    expect(quoteStatusText(4)).toBe('已撤回')
  })

  it('兼容旧商品名与规格字段，并严格按 capability 产生动作', () => {
    const quote = sanitizeInquiryQuote({
      id: 9, inquiryId: 7, status: 1, canEdit: true, canSubmit: false, canWithdraw: true,
      items: [{ skuId: 99, name: '旧字段商品名', specification: '旧字段规格', count: 2, unitPrice: 100 }],
    })
    expect(quote.items[0]).toMatchObject({ productName: '旧字段商品名', skuSpecification: '旧字段规格' })
    expect(inquiryQuoteActions(quote.capabilities, true)).toEqual({ create: false, edit: true, submit: false, withdraw: true, accept: false, convert: false })
    expect(inquiryQuoteActions({ ...quote.capabilities, canQuote: true }, false)).toEqual({ create: true, edit: false, submit: false, withdraw: false, accept: false, convert: false })
  })

  it('报价显示时间优先使用真实提交时间，再回退到更新和创建时间', () => {
    expect(quoteDisplayTime({ updateTime: 3, submittedAt: 2, createTime: 1 })).toBe(2)
    expect(quoteDisplayTime({ submittedAt: 2, createTime: 1 })).toBe(2)
    expect(quoteDisplayTime({ createTime: 1 })).toBe(1)
  })

  it('兼容消息 senderRole 并保留客户端幂等键', () => {
    expect(sanitizeInquiryMessages([{ id: 1, senderRole: 'BUYER', content: '请确认交期', clientMessageId: 'message-123' }])[0])
      .toMatchObject({ senderSide: 'BUYER', content: '请确认交期', clientMessageId: 'message-123' })
  })
})
