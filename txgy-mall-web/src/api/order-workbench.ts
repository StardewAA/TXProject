import { request } from './http'
import type { PageResult } from '@/types/mall'

export type WorkbenchRole = 'BUYER' | 'SUPPLIER'
export type TodoAction = 'PAYMENT' | 'RECEIVE' | 'COMMENT' | 'BUYER_RETURN' | 'DELIVERY' | 'AFTER_AUDIT' | 'AFTER_RECEIVE' | 'PLATFORM_REFUND' | 'ERP_EXCEPTION'
export interface TodoEntry {
  eventId: string; entityType: 'ORDER' | 'AFTER_SALE' | 'ERP'; entityId: number
  orderId: number; afterSaleId?: number; requestId?: string; no: string; shopId?: number
  action: TodoAction; reason: string; unread: boolean; createdTime: string
}
export interface WorkbenchScope { role: WorkbenchRole; shopId?: number }
export interface OrderStatusCounts {
  allCount: number; unpaidCount: number; undeliveredCount: number; deliveredCount: number
  completedCount: number; canceledCount: number; uncommentedCount: number
}
export interface WorkbenchSummary {
  scopeKey: string; serverTime: number; cursor: string; resetRequired: boolean; pollIntervalSeconds: number
  pendingCount: number; unreadCount: number; newCount: number; todoCounts: Partial<Record<TodoAction, number>>
  orderStatusCounts: OrderStatusCounts; entries: TodoEntry[]; hasMore: boolean; rowEntries: TodoEntry[]
}
export interface WorkbenchQuery extends WorkbenchScope {
  afterCursor?: string; orderIds?: string; afterSaleIds?: string; no?: string; commentStatus?: boolean
}
const base = '/trade/order-workbench'
const headers = { 'x-silent-error': true }
export const orderWorkbenchApi = {
  summary: (params: WorkbenchQuery) => request<WorkbenchSummary>({ url: `${base}/summary`, params, headers }),
  page: (params: WorkbenchScope & { pageNo: number; pageSize: number; action?: TodoAction; unreadOnly?: boolean }) =>
    request<PageResult<TodoEntry>>({ url: `${base}/page`, params, headers }),
  read: (data: WorkbenchScope & { eventIds: string[] }) => request<boolean>({ url: `${base}/read`, method: 'POST', data, headers }),
}
