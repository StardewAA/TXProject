import type { PageParam, PageResult } from '@vben/request';
import { requestClient } from '#/api/request';
export namespace ErpInquiryApi {
  export interface Item {
    id?: number;
    purchaseRequestItemId?: number;
    productId?: number;
    productName?: string;
    productUnitId?: number;
    productUnitName?: string;
    count?: number;
    qualityRequirement?: string;
    remark?: string;
  }
  export interface QuoteItem extends Item {
    quoteId?: number;
    inquiryItemId?: number;
    productPrice?: number;
    taxPercent?: number;
    taxPrice?: number;
    freightPrice?: number;
    totalPrice?: number;
    awarded?: boolean;
    awardCount?: number;
    awardReason?: string;
  }
  export interface Quote {
    id?: number;
    no?: string;
    inquiryId?: number;
    supplierId?: number;
    supplierName?: string;
    status?: number;
    version?: number;
    deliveryDate?: string;
    paymentTerm?: string;
    totalPrice?: number;
    remark?: string;
    items?: QuoteItem[];
  }
  export interface Inquiry {
    id?: number;
    no?: string;
    purchaseRequestId?: number;
    purchaseRequestNo?: string;
    status?: number;
    processInstanceId?: string;
    businessStatus?: number;
    awardStatus?: number;
    awardProcessInstanceId?: string;
    title?: string;
    inquiryMode?: number;
    deadline?: string | number;
    deliveryDate?: string | number;
    deliveryAddress?: string;
    currency?: string;
    paymentTerm?: string;
    remark?: string;
    totalCount?: number;
    quoteCount?: number;
    purchaseOrderIds?: string;
    productNames?: string;
    creatorName?: string;
    items?: Item[];
    quotes?: Quote[];
  }
  export interface PageReq extends PageParam {
    awardOnly?: boolean;
  }
}
const base = '/erp/inquiry';
export const getInquiryPage = (params: ErpInquiryApi.PageReq) =>
  requestClient.get<PageResult<ErpInquiryApi.Inquiry>>(`${base}/page`, {
    params,
  });
export const getInquiry = (id: number) =>
  requestClient.get<ErpInquiryApi.Inquiry>(`${base}/get`, { params: { id } });
export const createInquiry = (data: ErpInquiryApi.Inquiry) =>
  requestClient.post(`${base}/create`, data);
export const updateInquiry = (data: ErpInquiryApi.Inquiry) =>
  requestClient.put(`${base}/update`, data);
export const deleteInquiry = (ids: number[]) =>
  requestClient.delete(`${base}/delete`, { params: { ids: ids.join(',') } });
export const createInquiryFromRequest = (requestId: number) =>
  requestClient.post(`${base}/create-from-request`, null, {
    params: { requestId },
  });
export const submitInquiry = (id: number) =>
  requestClient.put(`${base}/submit`, null, { params: { id } });
export const publishInquiry = (id: number) =>
  requestClient.put(`${base}/publish`, null, { params: { id } });
export const closeInquiry = (id: number) =>
  requestClient.put(`${base}/close`, null, { params: { id } });
export const saveInquiryQuote = (data: ErpInquiryApi.Quote) =>
  requestClient.post(`${base}/quote/save`, data);
export const saveAward = (data: any) =>
  requestClient.put(`${base}/award/save`, data);
export const submitAward = (id: number) =>
  requestClient.put(`${base}/award/submit`, null, { params: { id } });
export const confirmAward = (id: number) =>
  requestClient.put<number[]>(`${base}/award/confirm`, null, {
    params: { id },
  });
