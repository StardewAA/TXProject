import { requestClient } from '#/api/request';

export interface ErpInventoryEntry {
  id: string; operation: string; mode: string; status: string;
  message: string; attempts: number; erpNo?: string; updateTime: string;
}
export const getErpInventory = (orderId: number) =>
  requestClient.get<ErpInventoryEntry[]>('/trade/erp-inventory/list', { params: { orderId } });
