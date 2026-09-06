import { requestClient } from '#/api/request';

export namespace MallErpShortageApi {
  export interface Demand {
    requestId: string;
    mallDemandNo: string;
    orderNo?: string;
    skuId: number;
    shortageQuantity: number;
    accepted?: boolean;
    erpRequestNo?: string;
    status?: 'ACCEPTED' | 'INVENTORY_READY';
    message?: string;
    createdTime?: string;
  }
}

export function submitShortageDemand(data: MallErpShortageApi.Demand) {
  return requestClient.post<MallErpShortageApi.Demand>('/product/erp/shortage/submit', data);
}

export function getShortageDemand(requestId: string) {
  return requestClient.get<MallErpShortageApi.Demand>(`/product/erp/shortage/${requestId}`);
}

/** 联调阶段模拟 ERP/WMS 到货，正式环境由回调或查询结果替换。 */
export function markShortageReady(requestId: string) {
  return requestClient.put<MallErpShortageApi.Demand>(`/product/erp/shortage/${requestId}/ready`);
}
