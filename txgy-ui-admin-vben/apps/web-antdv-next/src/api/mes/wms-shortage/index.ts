import type { PageParam } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace WmsShortageApi {
  /** WMS 缺料消息（payload 平铺 + unread） */
  export interface Message {
    eventId: string;
    eventVersion: number;
    eventType: string;
    sourceOrderNo: string;
    sourceLineNo?: number;
    wmsOrderNo?: string;
    warehouseName?: string;
    skuId?: number;
    skuCode: string;
    skuName: string;
    requestedQuantity?: number | string;
    currentQuantity?: number | string;
    availableQuantity?: number | string;
    shortageQuantity?: number | string;
    unit?: string;
    occurredAt: string;
    shortageStatus: 'OPEN' | 'RESOLVED' | 'CANCELLED';
    unread?: boolean;
  }
}

/** 分页查询缺料消息 */
export function getShortagePage(params: PageParam) {
  return requestClient.get<{
    list: WmsShortageApi.Message[];
    total: number;
  }>('/mes/wms-shortage/page', { params });
}

/** 未读数 */
export function getUnreadCount() {
  return requestClient.get<number>('/mes/wms-shortage/unread-count');
}

/** 标记已读（读到指定版本） */
export function markShortageRead(eventId: string, eventVersion: number) {
  return requestClient.post(
    `/mes/wms-shortage/read?eventId=${eventId}&eventVersion=${eventVersion}`,
  );
}
