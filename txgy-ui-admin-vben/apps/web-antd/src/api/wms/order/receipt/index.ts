import type { PageParam, PageResult } from '@vben/request';

import type { WmsReceiptOrderDetailApi } from './detail';

import { requestClient } from '#/api/request';

export namespace WmsReceiptOrderApi {
  /** WMS 入库单 */
  export interface ReceiptOrder {
    id?: number;
    no?: string;
    type?: number;
    orderTime?: string;
    status?: number;
    approvalStatus?: number;
    executionStatus?: number;
    processInstanceId?: string;
    bizOrderNo?: string;
    merchantId?: number;
    merchantName?: string;
    remark?: string;
    warehouseId?: number;
    warehouseName?: string;
    totalQuantity?: number;
    totalPrice?: number;
    details?: WmsReceiptOrderDetailApi.ReceiptOrderDetail[];
    createTime?: Date;
    creator?: string;
    creatorName?: string;
    updateTime?: Date;
    updater?: string;
    updaterName?: string;
  }
}

export function getReceiptOrderPage(params: PageParam) {
  return requestClient.get<PageResult<WmsReceiptOrderApi.ReceiptOrder>>('/wms/receipt-order/page', {
    params,
  });
}

export function getReceiptOrder(id: number) {
  return requestClient.get<WmsReceiptOrderApi.ReceiptOrder>(`/wms/receipt-order/get?id=${id}`);
}

export function getReceiptOrderDetailListByOrderId(orderId: number) {
  return requestClient.get<WmsReceiptOrderDetailApi.ReceiptOrderDetail[]>(
    `/wms/receipt-order-detail/list-by-order-id?orderId=${orderId}`
  );
}

export function createReceiptOrder(data: WmsReceiptOrderApi.ReceiptOrder) {
  return requestClient.post('/wms/receipt-order/create', data);
}

export function updateReceiptOrder(data: WmsReceiptOrderApi.ReceiptOrder) {
  return requestClient.put('/wms/receipt-order/update', data);
}

export function completeReceiptOrder(id: number) {
  return requestClient.put(`/wms/receipt-order/complete?id=${id}`);
}

export function cancelReceiptOrder(id: number) {
  return requestClient.put(`/wms/receipt-order/cancel?id=${id}`);
}

export function deleteReceiptOrder(id: number) {
  return requestClient.delete(`/wms/receipt-order/delete?id=${id}`);
}

export function exportReceiptOrder(params: any) {
  return requestClient.download('/wms/receipt-order/export-excel', { params });
}

export function submitReceiptApproval(id: number) {
  return requestClient.put(`/wms/receipt-order/submit-approval?id=${id}`);
}
export function simulateErpReceipt(data: {
  warehouseId: number;
  sourceOrderNo: string;
  details: { skuId: number; quantity: number }[];
}) {
  return requestClient.post<{ id: number; targetOrderNo: string }>(
    '/wms/receipt-order/simulate-erp',
    data
  );
}
export interface ReceiptExecution {
  targetOrderNo?: string;
  status?: string;
  completedQuantity?: number;
  notificationStatus?: string;
  notificationError?: string;
}
export function getReceiptExecution(id: number) {
  return requestClient.get<ReceiptExecution>(`/wms/receipt-order/execution?id=${id}`);
}
export function retryReceiptNotification(id: number) {
  return requestClient.post(`/wms/receipt-order/retry-notification?id=${id}`);
}
