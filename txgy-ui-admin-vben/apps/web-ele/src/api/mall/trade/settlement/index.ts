import type { PageParam, PageResult } from '@vben/request';
import { requestClient } from '#/api/request';
export interface ShopSettlement { id?: number; no?: string; shopId?: number; shopName?: string; periodStartTime?: string; periodEndTime?: string; orderCount?: number; orderPayAmount?: number; refundAmount?: number; settlementBaseAmount?: number; commissionRate?: number; platformCommissionAmount?: number; settlementAmount?: number; status?: number; auditRemark?: string; createTime?: string; orders?: any[]; }
export function getShopSettlementPage(params: PageParam & Partial<ShopSettlement>) { return requestClient.get<PageResult<ShopSettlement>>('/trade/shop-settlement/page', { params }); }
export function generateShopSettlement(data: { shopId: number; periodStartTime: string; periodEndTime: string; commissionRate: number }) { return requestClient.post('/trade/shop-settlement/generate', data); }
export function auditShopSettlement(data: { id: number; status: number; auditRemark?: string }) { return requestClient.put('/trade/shop-settlement/audit', data); }
export function confirmShopSettlement(id: number) { return requestClient.put(`/trade/shop-settlement/confirm?id=${id}`); }
export function getShopSettlement(id: number) { return requestClient.get<ShopSettlement>(`/trade/shop-settlement/get?id=${id}`); }
