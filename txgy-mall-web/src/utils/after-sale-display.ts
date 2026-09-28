import type { OrderItem } from '@/types/mall'

export const rejectedAfterSaleStatuses = new Set([62, 63])

export function hasRejectedAfterSale(item: OrderItem) {
  return rejectedAfterSaleStatuses.has(Number(item.latestAfterSaleStatus))
}

export function rejectedAfterSaleTitle(item: OrderItem) {
  return Number(item.latestAfterSaleStatus) === 63 ? '退货被商家拒收' : '退款申请已被商家拒绝'
}
