import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace ErpLogisticsApi {
  export interface Task {
    id: number;
    logisticsTaskNo: string;
    waybillNo: string;
    sourceOrderType: string;
    sourceOrderNo: string;
    carrierName: string;
    logisticsStatus: string;
    latestNodeName?: string;
    latestNodeTime?: string;
    latestLocation?: string;
    estimatedArrivalTime?: string;
    locationOnline: boolean;
    hasException: boolean;
  }

  export interface Attachment {
    fileId: number;
    url: string;
    fileName: string;
    mimeType?: string;
    sha256?: string;
  }

  export interface Item {
    id: number;
    lineNo: number;
    sourceItemNo?: string;
    materialCode?: string;
    itemNameSnapshot: string;
    specificationSnapshot?: string;
    unitNameSnapshot: string;
    quantity: number;
  }

  export interface Node {
    id: number;
    nodeCode: string;
    nodeName: string;
    occurredAt: string;
    receivedAt: string;
    locationDescription?: string;
    remark?: string;
    reportSource?: string;
    eventVersion: number;
    attachments: Attachment[];
  }

  export interface Receipt {
    id: number;
    receiptType: string;
    receiverName: string;
    receiptTime: string;
    receiptQuantity: number;
    remark?: string;
    eventVersion: number;
    attachments: Attachment[];
  }

  export interface ExceptionRecord {
    id: number;
    nodeId?: number;
    exceptionCode: string;
    exceptionName: string;
    description?: string;
    status: string;
    occurredAt: string;
    handlerUserId?: number;
    handleResult?: string;
    handleTime?: string;
  }

  export interface FulfillmentEvent {
    id: number;
    eventCode: string;
    eventName: string;
    eventDescription?: string;
    occurredAt: string;
    eventVersion?: number;
  }

  export interface EventLog {
    id: number;
    requestId: string;
    logisticsTaskNo: string;
    eventType: string;
    eventVersion: number;
    processStatus: string;
    processMessage?: string;
    createTime: string;
    updateTime: string;
  }

  export interface Detail extends Task {
    requestId: string;
    businessChainNo: string;
    carrierCode?: string;
    vehicleNo?: string;
    driverName?: string;
    driverPhoneMasked?: string;
    plannedDepartureTime?: string;
    resumeStatus?: string;
    latestEventVersion: number;
    latestNodeCode?: string;
    trackUrl?: string;
    lastOnlineTime?: string;
    createTime: string;
    items: Item[];
    nodes: Node[];
    receipts: Receipt[];
    exceptions: ExceptionRecord[];
    fulfillmentEvents: FulfillmentEvent[];
  }
}

const BASE_URL = '/erp/logistics';

export function getLogisticsPage(params: PageParam) {
  return requestClient.get<PageResult<ErpLogisticsApi.Task>>(
    `${BASE_URL}/page`,
    { params },
  );
}

export function getLogisticsTrack(id: number) {
  return requestClient.get<ErpLogisticsApi.Detail>(
    `${BASE_URL}/track?id=${id}`,
  );
}

export function handleLogisticsException(data: {
  handleResult: string;
  id: number;
}) {
  return requestClient.put(`${BASE_URL}/exception/handle`, data);
}

export function getLogisticsEventPage(params: PageParam) {
  return requestClient.get<PageResult<ErpLogisticsApi.EventLog>>(
    `${BASE_URL}/event-page`,
    { params },
  );
}

export function retryLogisticsEvent(id: number) {
  return requestClient.post(`${BASE_URL}/event/retry`, null, { params: { id } });
}

export const SOURCE_ORDER_TYPE_OPTIONS = [
  { label: '销售订单', value: 'SALE_ORDER' },
  { label: '销售出库单', value: 'SALE_OUT' },
  { label: '采购退货单', value: 'PURCHASE_RETURN' },
  { label: '客户退货单', value: 'SALE_RETURN' },
];

export const LOGISTICS_STATUS_OPTIONS = [
  { label: '待派车', value: 'PENDING_DISPATCH' },
  { label: '待装车', value: 'PENDING_LOADING' },
  { label: '运输中', value: 'IN_TRANSIT' },
  { label: '已到达', value: 'ARRIVED' },
  { label: '已签收', value: 'SIGNED' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '异常处理中', value: 'EXCEPTION_HANDLING' },
  { label: '已取消', value: 'CANCELLED' },
  { label: '已关闭', value: 'CLOSED' },
];

export function getLogisticsStatusText(status?: string) {
  return LOGISTICS_STATUS_OPTIONS.find((item) => item.value === status)?.label
    ?? status
    ?? '';
}

export function getSourceOrderTypeText(type?: string) {
  return SOURCE_ORDER_TYPE_OPTIONS.find((item) => item.value === type)?.label
    ?? type
    ?? '';
}

export function getLogisticsStatusColor(status?: string) {
  if (status === 'COMPLETED' || status === 'SIGNED') return 'success';
  if (status === 'EXCEPTION_HANDLING') return 'error';
  if (status === 'CANCELLED' || status === 'CLOSED') return 'default';
  if (status === 'IN_TRANSIT' || status === 'ARRIVED') return 'processing';
  return 'warning';
}
