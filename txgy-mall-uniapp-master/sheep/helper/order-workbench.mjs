export const orderTabs = Object.freeze([
  { name: '全部', count: 'allCount', legacyType: 0 },
  { name: '待付款', status: 0, count: 'unpaidCount', legacyType: 1 },
  { name: '待发货', status: 10, count: 'undeliveredCount', legacyType: 2 },
  { name: '待收货', status: 20, count: 'deliveredCount', legacyType: 3 },
  { name: '已完成', status: 30, count: 'completedCount', legacyType: 5 },
  { name: '已取消', status: 40, count: 'canceledCount', legacyType: 6 },
  { name: '待评价', status: 30, commentStatus: false, count: 'uncommentedCount', legacyType: 4 },
]);
export const buyerTodoNames = Object.freeze({ PAYMENT: '可付款', RECEIVE: '可确认收货', COMMENT: '待评价', BUYER_RETURN: '售后待寄回' });
export function initialOrderTab(options = {}) {
  if (options.status !== undefined) {
    const status = Number(options.status);
    const comment = options.commentStatus === false || options.commentStatus === 'false';
    const index = orderTabs.findIndex(t => t.status === status && Boolean(t.commentStatus === false) === comment);
    return index < 0 ? 0 : index;
  }
  const index = orderTabs.findIndex(t => t.legacyType === Number(options.type));
  return index < 0 ? 0 : index;
}
export function orderTabQuery(index) {
  const tab = orderTabs[index] || orderTabs[0];
  return { status: tab.status, commentStatus: tab.commentStatus };
}
export function tabsWithCounts(summary) {
  return orderTabs.map(tab => ({ ...tab, name: `${tab.name} (${summary?.orderStatusCounts?.[tab.count] ?? '—'})` }));
}
export function validEventId(value) { return typeof value === 'string' && /^[1-9]\d{0,18}$/.test(value); }
export function noticeDestination(entry) {
  if (!entry || !buyerTodoNames[entry.action] || !validEventId(entry.eventId)) return null;
  if (entry.action === 'BUYER_RETURN') {
    return Number.isSafeInteger(entry.afterSaleId) && entry.afterSaleId > 0
      ? { path: '/pages/order/aftersale/detail', query: { id: entry.afterSaleId } } : null;
  }
  return Number.isSafeInteger(entry.orderId) && entry.orderId > 0
    ? { path: '/pages/order/detail', query: { id: entry.orderId } } : null;
}
export function validateSummary(data, expectedScope) {
  if (!data || typeof data.scopeKey !== 'string' || typeof data.cursor !== 'string' || !/^\d+$/.test(data.cursor)
      || !Number.isFinite(data.serverTime) || !Array.isArray(data.entries) || !Array.isArray(data.rowEntries)) throw new Error('工作台响应不完整');
  if (expectedScope && data.scopeKey !== expectedScope) throw new Error('工作台账号范围不一致');
  for (const value of [data.pendingCount, data.unreadCount, data.newCount, ...orderTabs.map(t => data.orderStatusCounts?.[t.count]), ...Object.keys(buyerTodoNames).map(k => data.todoCounts?.[k])]) {
    if (!Number.isSafeInteger(value) || value < 0) throw new Error('工作台计数无效');
  }
  if (data.entries.some(e => !noticeDestination(e)) || data.rowEntries.some(e => !noticeDestination(e))) throw new Error('工作台条目无效');
  return data;
}
export function createNoticeGate() {
  let version = 0;
  return { capture: () => version, current: token => token === version, invalidate: () => ++version };
}
export function unreadForOrder(summary, id, afterSale = false) {
  return (summary?.rowEntries || []).filter(e => e.unread && (afterSale ? e.afterSaleId === id : e.orderId === id));
}
