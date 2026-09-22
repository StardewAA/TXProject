import type { ComparisonItem, ComparisonQuote, ComparisonResult } from '@/types/comparison'
import { comparisonIds, difference } from './comparison-selection'
import { formatMoney } from './format'

export function comparisonMoney(value: unknown): string {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0 ? `¥${formatMoney(value)}` : '待确认'
}
export function assertComparisonResult(result: ComparisonResult, skuIds: string[], quantity: number, preference: string) {
  if (!result || result.quantity !== quantity || result.preference !== preference || result.quantityUnit !== 'SALE_UNIT'
    || !Array.isArray(result.items) || JSON.stringify(comparisonIds(result.items.map(item => item.skuId))) !== JSON.stringify(skuIds)) throw new Error('对比结果与当前选择不一致，请重试')
  return result
}
export function assertComparisonQuote(result: ComparisonQuote, skuIds: string[], quantity: number, addressId: string) {
  if (!result || result.quantity !== quantity || result.priceBasis !== 'CHECKOUT_ESTIMATE'
    || String(result.addressId ?? '') !== addressId || !Array.isArray(result.items)
    || JSON.stringify(comparisonIds(result.items.map(item => item.skuId))) !== JSON.stringify(skuIds)
    || result.items.some(item => item.quantity !== quantity)) throw new Error('费用结果与当前选择不一致，请重新估算')
  if (result.items.some(item => item.status !== 'AVAILABLE' || item.amountsComplete !== true)) {
    return { ...result, items: result.items.map(item => item.status === 'AVAILABLE' && item.amountsComplete === true ? item : { ...item, shippingFee: null, totalAmount: null, amountsComplete: false }) }
  }
  return result
}
export function comparisonRows(items: ComparisonItem[]) {
  const names = [...new Set(items.flatMap(item => (item.properties || []).map(property => property.name)))].filter(Boolean)
  const rows = [
    { label: '店铺', values: items.map(item => item.available ? item.shopName || '待补充' : '不可用') },
    { label: 'SKU 原标价 / 销售份', values: items.map(item => item.available ? comparisonMoney(item.price) : '不可用') },
    ...names.map(name => ({ label: name, values: items.map(item => (item.properties || []).filter(property => property.name === name).map(property => property.value).join('、') || '待补充') })),
    { label: '同款与单位标准', values: items.map(item => item.standardMessage || '资料不足，暂不判定同款') },
    { label: '库存说明', values: items.map(item => item.stockMessage || '以下单和 ERP 确认为准') },
    { label: '服务端差异说明', values: items.map(item => item.differences?.join('；') || '尚无可信差异结论') },
    { label: '未知项', values: items.map(item => item.unknowns?.join('；') || '待确认') },
  ]
  return rows.map(row => ({ ...row, different: difference(row.values) }))
}
