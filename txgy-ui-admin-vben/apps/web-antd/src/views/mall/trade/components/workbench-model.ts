/** Only the existing main states belong in the order total. After-sale is separate. */
export const ORDER_STATUS_TABS = [
  { key: 'all', label: '全部' },
  { key: '0', label: '待支付' },
  { key: '10', label: '待发货' },
  { key: '20', label: '待收货' },
  { key: '30', label: '已完成' },
  { key: '40', label: '已取消' },
] as const;

/** Counts use submitted common filters, never pagination or the active state. */
export function commonFilters(values: Record<string, unknown>) {
  const { status: _status, pageNo: _pageNo, pageSize: _pageSize, pickUpStoreId, ...rest } = values;
  if (pickUpStoreId !== undefined && pickUpStoreId !== null && pickUpStoreId !== '') {
    rest.pickUpStoreIds = [Number(pickUpStoreId)];
  }
  return rest;
}

/** Apply the selected tab last so a stale form field cannot override it. */
export function pageFilters(values: Record<string, unknown>, activeStatus: string) {
  return { ...commonFilters(values), status: activeStatus === 'all' ? undefined : Number(activeStatus) };
}

export function countText(value: unknown, failed = false) {
  return !failed && typeof value === 'number' && Number.isFinite(value) && value >= 0
    ? String(value) : '—';
}

/** Fail closed when old cached responses do not carry the server action decision. */
export function canDeliver(order: { deliverable?: boolean; deliveryType?: number; status?: number }) {
  return order.deliverable === true && order.deliveryType === 1 && order.status === 10;
}

export function exactEventIds(events: { eventId: string; unread: boolean }[]) {
  return [...new Set(events.filter((event) => event.unread).map((event) => event.eventId))]
    .filter((id) => /^[1-9]\d*$/.test(id));
}

interface NoticeSnapshot {
  scopeKey: string;
  resetRequired: boolean;
  newCount: number;
  pendingCount: number;
  unreadCount: number;
}

/** Presentation only: neither dismissing this banner nor polling writes reading progress. */
export function nextBannerCount(previous: NoticeSnapshot | undefined, next: NoticeSnapshot, oldBanner: number) {
  if (!previous || previous.scopeKey !== next.scopeKey || next.resetRequired) return 0;
  if (next.pendingCount <= 0 || next.unreadCount <= 0) return 0;
  return Math.min(next.newCount > 0 ? next.newCount : oldBanner, next.pendingCount, next.unreadCount);
}
