import type { OrderStatusCounts, TodoAction, TodoEntry, WorkbenchRole } from '@/api/order-workbench'

export const todoActionNames: Record<TodoAction, string> = {
  PAYMENT: '可支付订单', RECEIVE: '待确认收货', COMMENT: '待评价', BUYER_RETURN: '待寄回商品',
  DELIVERY: '可发货订单', AFTER_AUDIT: '待审核售后', AFTER_RECEIVE: '待验收退货',
  PLATFORM_REFUND: '待平台退款', ERP_EXCEPTION: '历史履约异常',
}
export const orderTabs: Array<{ value: string; label: string; count: keyof OrderStatusCounts }> = [
  { value: 'all', label: '全部', count: 'allCount' }, { value: '0', label: '待支付', count: 'unpaidCount' },
  { value: '10', label: '待发货', count: 'undeliveredCount' }, { value: '20', label: '待收货', count: 'deliveredCount' },
  { value: '30', label: '已完成', count: 'completedCount' }, { value: '40', label: '已取消', count: 'canceledCount' },
]
export function workbenchRowEntries(entries: TodoEntry[], id: string | number, afterSale = false) {
  return entries.filter((entry) => entry.unread && String(afterSale ? entry.afterSaleId : entry.orderId) === String(id))
}
export function workbenchDestination(entry: TodoEntry, role: WorkbenchRole, buyerBase: '/member' | '/purchase', shopId?: number) {
  if (role === 'SUPPLIER') return {
    path: `/sales/shops/${shopId}/${entry.afterSaleId ? 'after-sales' : 'orders'}`,
    query: { [entry.afterSaleId ? 'openAfter' : 'openOrder']: String(entry.afterSaleId || entry.orderId) },
  }
  if (entry.afterSaleId) return { path: `${buyerBase}/after-sales`, query: { openAfter: String(entry.afterSaleId) } }
  return { path: `${buyerBase}/orders/${entry.orderId}${entry.action === 'COMMENT' ? '/comment' : ''}` }
}
