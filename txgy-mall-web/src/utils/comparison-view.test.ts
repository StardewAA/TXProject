import { expect, it } from 'vitest'
import { assertComparisonQuote, assertComparisonResult, comparisonMoney, comparisonRows } from './comparison-view'
import type { ComparisonItem, ComparisonQuote, ComparisonResult } from '@/types/comparison'
const item = (skuId: string, extra = {}): ComparisonItem => ({ skuId, available: true, properties: [], standardStatus: 'UNKNOWN', standardMessage: '资料不足', stockStatus: 'PENDING_ERP', stockMessage: '等待 ERP', unknowns: ['包装'], matchedRequirements: [], differences: [], ...extra })
const result: ComparisonResult = { quantity: 2, preference: 'PRICE', quantityUnit: 'SALE_UNIT', calculatedAt: 1, strategyVersion: 'v1', recommendationStatus: 'INSUFFICIENT_STANDARD_DATA', recommendationReason: '资料不足', recommendedSkuId: null, items: [item('1'), item('2')], warnings: [] }
const quote: ComparisonQuote = { quantity: 2, addressId: '41', calculatedAt: 1, warnings: [], priceBasis: 'CHECKOUT_ESTIMATE', items: [{ skuId: '1', quantity: 2, status: 'ADDRESS_REQUIRED', message: '待选地址', amountsComplete: false }] }
it.each([null, undefined, NaN, Infinity, -1, '100', 1.5])('unknown/invalid money %s is not free shipping', value => expect(comparisonMoney(value)).toBe('待确认'))
it('only formats exact server amounts and never derives totals', () => { expect(comparisonMoney(0)).toBe('¥0.00'); expect(comparisonMoney(123)).toBe('¥1.23') })
it('rejects stale SKU, quantity, preference and unit responses', () => {
  expect(assertComparisonResult(result, ['1', '2'], 2, 'PRICE')).toBe(result)
  for (const patch of [{ quantity: 3 }, { preference: 'BALANCED' }, { quantityUnit: 'M2' }, { items: [item('3'), item('2')] }]) expect(() => assertComparisonResult({ ...result, ...patch } as ComparisonResult, ['1', '2'], 2, 'PRICE')).toThrow()
})
it('rejects quotes for another address, quantity, SKU or pricing basis', () => {
  expect(assertComparisonQuote(quote, ['1'], 2, '41')).toMatchObject({ quantity: 2, items: [{ shippingFee: null, totalAmount: null, amountsComplete: false }] })
  for (const patch of [{ addressId: '42' }, { quantity: 3 }, { priceBasis: 'CHEAPEST' }, { items: [{ ...quote.items[0], skuId: '2' }] }]) expect(() => assertComparisonQuote({ ...quote, ...patch } as ComparisonQuote, ['1'], 2, '41')).toThrow()
})
it('renders raw specifications as differences without claiming same-product certification', () => {
  const rows = comparisonRows([item('1', { price: 3000, properties: [{ name: '厚度', value: '20mm' }] }), item('2', { price: 3100, properties: [] })])
  expect(rows.find(row => row.label === '厚度')).toEqual({ label: '厚度', values: ['20mm', '待补充'], different: true })
  expect(rows.find(row => row.label === '同款与单位标准')?.different).toBe(false)
  expect(rows.some(row => /最低|推荐|排名/.test(row.label))).toBe(false)
})
it('unavailable SKU prices and shop names stay hidden', () => {
  const rows = comparisonRows([item('1', { available: false, price: 1, shopName: 'private' })])
  expect(rows[0]?.values).toEqual(['不可用']); expect(rows[1]?.values).toEqual(['不可用'])
})
