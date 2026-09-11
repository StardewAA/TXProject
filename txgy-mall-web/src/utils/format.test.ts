import { describe, expect, it } from 'vitest'

import { formatDate, formatMoney, propertyText } from './format'

describe('商城显示格式', () => {
  it('按后端分为单位格式化金额', () => {
    expect(formatMoney(4300)).toBe('43.00')
    expect(formatMoney('3055')).toBe('30.55')
    expect(formatMoney()).toBe('0.00')
  })

  it('组合 SKU 属性且忽略空属性', () => {
    expect(propertyText([
      { propertyName: '规格', valueName: 'Φ48×3.0mm' },
      { propertyName: '单位', valueName: 'm' },
    ])).toBe('规格:Φ48×3.0mm；单位:m')
    expect(propertyText('规格:Φ48×3.0mm')).toBe('规格:Φ48×3.0mm')
    expect(propertyText()).toBe('')
  })

  it('将 ISO 日期转换成页面格式', () => {
    expect(formatDate('2026-09-10T15:30:20')).toBe('2026-09-10 15:30:20')
    expect(formatDate(1788683558000)).toMatch(/^2026-09-06 \d{2}:\d{2}:\d{2}$/)
    expect(formatDate()).toBe('-')
  })
})
