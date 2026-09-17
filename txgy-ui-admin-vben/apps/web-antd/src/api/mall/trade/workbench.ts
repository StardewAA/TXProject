import type { PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export type TodoAction = 'AFTER_AUDIT' | 'AFTER_RECEIVE' | 'BUYER_RETURN' | 'COMMENT' | 'DELIVERY' | 'ERP_EXCEPTION' | 'PAYMENT' | 'PLATFORM_REFUND' | 'RECEIVE';
export interface TodoEntry {
  eventId: string;
  entityType: 'AFTER_SALE' | 'ERP' | 'ORDER';
  entityId: number;
  orderId: number;
  afterSaleId?: number;
  requestId?: string;
  no: string;
  shopId?: number;
  action: TodoAction;
  reason: string;
  unread: boolean;
  createdTime: string;
}
export interface OrderStatusCounts {
  allCount: number;
  unpaidCount: number;
  undeliveredCount: number;
  deliveredCount: number;
  completedCount: number;
  canceledCount: number;
  uncommentedCount: number;
}
export interface WorkbenchSummary {
  scopeKey: string;
  serverTime: number;
  cursor: string;
  resetRequired: boolean;
  pollIntervalSeconds: number;
  pendingCount: number;
  unreadCount: number;
  newCount: number;
  todoCounts: Record<TodoAction, number>;
  orderStatusCounts: OrderStatusCounts;
  entries: TodoEntry[];
  rowEntries: TodoEntry[];
  hasMore: boolean;
}
export const getWorkbenchSummary = (params: Record<string, unknown>, signal?: AbortSignal) =>
  requestClient.get<WorkbenchSummary>('/trade/order-workbench/summary', { params: { ...params, role: 'ADMIN' }, signal, headers: { 'X-Silent-Error': 'true' } });
export const getWorkbenchPage = (params: { action?: TodoAction; pageNo: number; pageSize: number; unreadOnly?: boolean }, signal?: AbortSignal) =>
  requestClient.get<PageResult<TodoEntry>>('/trade/order-workbench/page', { params: { ...params, role: 'ADMIN' }, signal, headers: { 'X-Silent-Error': 'true' } });
export const readWorkbenchEvents = (eventIds: string[]) =>
  requestClient.post<boolean>('/trade/order-workbench/read', { role: 'ADMIN', eventIds });
