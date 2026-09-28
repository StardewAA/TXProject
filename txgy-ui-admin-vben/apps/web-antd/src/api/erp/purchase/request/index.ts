import type { PageParam, PageResult } from '@vben/request';
import { requestClient } from '#/api/request';
export namespace ErpPurchaseRequestApi {
  export interface Item {
    id?: number;
    productId?: number;
    productName?: string;
    productUnitId?: number;
    productUnitName?: string;
    count?: number;
    targetPrice?: number;
    remark?: string;
  }
  export interface Request {
    id?: number;
    no?: string;
    status?: number;
    processInstanceId?: string;
    requestTime?: string | number;
    requiredDate?: string | number;
    departmentId?: number;
    totalCount?: number;
    budgetAmount?: number;
    purpose?: string;
    remark?: string;
    productNames?: string;
    creatorName?: string;
    createTime?: string;
    items?: Item[];
    reverseApproveReason?: string;
  }
}
const base = '/erp/purchase-request';
export const getPurchaseRequestPage = (params: PageParam) =>
  requestClient.get<PageResult<ErpPurchaseRequestApi.Request>>(`${base}/page`, {
    params,
  });
export const getPurchaseRequest = (id: number) =>
  requestClient.get<ErpPurchaseRequestApi.Request>(`${base}/get`, {
    params: { id },
  });
export const createPurchaseRequest = (data: ErpPurchaseRequestApi.Request) =>
  requestClient.post(`${base}/create`, data);
export const updatePurchaseRequest = (data: ErpPurchaseRequestApi.Request) =>
  requestClient.put(`${base}/update`, data);
export const deletePurchaseRequest = (ids: number[]) =>
  requestClient.delete(`${base}/delete`, { params: { ids: ids.join(',') } });
export const submitPurchaseRequest = (id: number) =>
  requestClient.put(`${base}/submit`, null, { params: { id } });
/** 反审批采购申请（审批中 / 已审批 → 草稿），reason 为必填原因 */
export function reverseApprovePurchaseRequest(id: number, reason: string) {
  return requestClient.put(`${base}/reverse-approve`, null, {
    params: { id, reason },
  });
}
