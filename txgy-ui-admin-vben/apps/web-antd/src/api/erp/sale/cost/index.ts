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
    remark?: string;
    items?: Item[];
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
export const convertCostResult = (id: number, convertedType: number) =>
  requestClient.put(`${base}/convert`, { id, convertedType });
