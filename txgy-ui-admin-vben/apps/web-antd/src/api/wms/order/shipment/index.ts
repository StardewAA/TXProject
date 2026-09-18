import type { PageParam, PageResult } from '@vben/request';

import type { WmsShipmentOrderDetailApi } from './detail';

import { requestClient } from '#/api/request';

export namespace WmsShipmentOrderApi {
  /** WMS 出库单 */
  export interface ShipmentOrder {
    id?: number;
    no?: string;
    type?: number;
    orderTime?: string;
    status?: number;
    approvalStatus?: number;
    processInstanceId?: string;
    approvalTime?: string;
    approvalReason?: string;
    executionStatus?: number;
    executionError?: string;
    sourceModule?: string;
    simulation?: boolean;
    bizOrderNo?: string;
    merchantId?: number;
    merchantName?: string;
    remark?: string;
    warehouseId?: number;
    warehouseName?: string;
    totalQuantity?: number;
    totalPrice?: number;
    details?: WmsShipmentOrderDetailApi.ShipmentOrderDetail[];
    createTime?: Date;
    creator?: string;
    creatorName?: string;
    updateTime?: Date;
    updater?: string;
    updaterName?: string;
  }
}

export interface ShipmentExecution {
  targetOrderNo?: string;
  status?: string;
  completedQuantity?: number;
  notificationStatus?: string;
  notificationError?: string;
}
export function getShipmentExecution(id: number) {
  return requestClient.get<ShipmentExecution>('/wms/shipment-order/execution', { params: { id } });
}

export function getShipmentOrderPage(params: PageParam) {
  return requestClient.get<PageResult<WmsShipmentOrderApi.ShipmentOrder>>(
    '/wms/shipment-order/page',
    { params },
  );
}

export function getShipmentOrder(id: number) {
  return requestClient.get<WmsShipmentOrderApi.ShipmentOrder>(
    `/wms/shipment-order/get?id=${id}`,
  );
}

export function getShipmentOrderDetailListByOrderId(orderId: number) {
  return requestClient.get<WmsShipmentOrderDetailApi.ShipmentOrderDetail[]>(
    `/wms/shipment-order-detail/list-by-order-id?orderId=${orderId}`,
  );
}

export function createShipmentOrder(data: WmsShipmentOrderApi.ShipmentOrder) {
  return requestClient.post('/wms/shipment-order/create', data);
}

export function updateShipmentOrder(data: WmsShipmentOrderApi.ShipmentOrder) {
  return requestClient.put('/wms/shipment-order/update', data);
}

export function completeShipmentOrder(id: number) {
  return requestClient.put(`/wms/shipment-order/complete?id=${id}`);
}

export function submitShipmentOrderApproval(id: number) {
  return requestClient.post<string>(
    `/wms/shipment-order/submit-approval?id=${id}`,
  );
}

export function cancelShipmentOrder(id: number) {
  return requestClient.put(`/wms/shipment-order/cancel?id=${id}`);
}

export function deleteShipmentOrder(id: number) {
  return requestClient.delete(`/wms/shipment-order/delete?id=${id}`);
}

export function exportShipmentOrder(params: any) {
  return requestClient.download('/wms/shipment-order/export-excel', { params });
}
