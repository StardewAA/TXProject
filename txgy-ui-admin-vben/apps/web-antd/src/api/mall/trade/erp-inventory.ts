import { requestClient } from '#/api/request';

export interface ErpInventoryEntry {
  id: string; operation: string; mode: string; status: string;
  message: string; attempts: number; erpNo?: string; updateTime: string;
}
export const getErpInventory = (orderId: number) =>
  requestClient.get<ErpInventoryEntry[]>('/trade/erp-inventory/list', { params: { orderId } });
export const retryErpInventory = (requestId: string) =>
  requestClient.post('/trade/erp-inventory/retry', undefined, { params: { requestId } });
export const seedErpMockInventory = (skuId: number, quantity: number) =>
  requestClient.post('/trade/erp-inventory/mock/seed', { skuId, quantity });
