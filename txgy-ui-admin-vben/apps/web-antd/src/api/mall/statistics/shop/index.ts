import { requestClient } from '#/api/request';
export interface ShopOperationStatistics { shopId?: number; shopName?: string; orderCount?: number; paidOrderCount?: number; paidAmount?: number; refundOrderCount?: number; refundAmount?: number; settledAmount?: number; unsettledAmount?: number; }
export function getShopOperationStatistics(params?: { shopId?: number; times?: string[] }) { return requestClient.get<ShopOperationStatistics[]>('/statistics/shop/operation', { params }); }
