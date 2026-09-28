import type { PageParam, PageResult } from '@vben/request';
import { requestClient } from '#/api/request';
export namespace ErpCostResultApi {
  export interface Item {
    id?: number;
    lineNo?: number;
    externalMaterialCode?: string;
    materialName?: string;
    specification?: string;
    unitName?: string;
    productId?: number;
    productName?: string;
    productUnitId?: number;
    mappingStatus?: number;
    count?: number;
    unitPrice?: number;
    taxPercent?: number;
    totalPrice?: number;
    remark?: string;
  }
  export interface Result {
    id?: number;
    no?: string;
    name?: string;
    projectCode?: string;
    costVersion?: number;
    status?: number;
    processInstanceId?: string;
    businessStatus?: number;
    sourceType?: number;
    currency?: string;
    totalPrice?: number;
    mappedCount?: number;
    itemCount?: number;
    receivedAt?: string;
    convertedType?: number;
    convertedId?: number;
    remark?: string;
    items?: Item[];
    reverseApproveReason?: string;
  }
}
const base = '/erp/cost-result';
export const getCostResultPage = (params: PageParam) =>
  requestClient.get<PageResult<ErpCostResultApi.Result>>(`${base}/page`, {
    params,
  });
export const getCostResult = (id: number) =>
  requestClient.get<ErpCostResultApi.Result>(`${base}/get`, { params: { id } });
export const createCostResult = (data: ErpCostResultApi.Result) =>
  requestClient.post(`${base}/create`, data);
export const updateCostResult = (data: ErpCostResultApi.Result) =>
  requestClient.put(`${base}/update`, data);
export const deleteCostResult = (ids: number[]) =>
  requestClient.delete(`${base}/delete`, { params: { ids: ids.join(',') } });
export const submitCostResult = (id: number) =>
  requestClient.put(`${base}/submit`, null, { params: { id } });
/**
 * 转换造价结果
 *
 * convertedType：1 销售报价 / 2 销售订单 / 3 项目预算 / 4 归档
 * 其中 2（销售订单）会**真实创建销售订单**并要求传 customerId，
 * 返回值为生成的销售订单编号（其余类型返回空）。
 */
export const convertCostResult = (data: {
  id: number;
  convertedType: number;
  customerId?: number;
  saleUserId?: number;
  accountId?: number;
  warehouseCode?: string;
}) => requestClient.put<number>(`${base}/convert`, data);
/** 反审批造价结果（审批中 / 已审批 → 草稿），reason 为必填原因 */
export function reverseApproveCostResult(id: number, reason: string) {
  return requestClient.put(`${base}/reverse-approve`, null, {
    params: { id, reason },
  });
}
