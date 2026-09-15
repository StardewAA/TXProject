import { requestClient } from '#/api/request';

export interface ShipmentShortage {
  eventId: string;
  eventVersion: number;
  sourceLineNo: number;
  skuCode: string;
  skuName: string;
  warehouseName: string;
  requestedQuantity: number;
  availableQuantity: number;
  shortageQuantity: number;
  unit: string;
  targetSystem: string;
  shortageStatus: 'OPEN' | 'RESOLVED' | 'CANCELLED';
  notificationStatus: string;
  attempts: number;
  lastError: string;
  lastSentTime?: string;
}
export function getShipmentShortages(orderId: number) {
  return requestClient.get<ShipmentShortage[]>('/wms/shipment-shortage/list', { params: { orderId } });
}
export function recheckShipmentStock(orderId: number) {
  return requestClient.post<ShipmentShortage[]>('/wms/shipment-shortage/recheck', undefined, { params: { orderId } });
}
export function retryShipmentShortage(eventId: string) {
  return requestClient.post<boolean>('/wms/shipment-shortage/retry', undefined, { params: { eventId } });
}
