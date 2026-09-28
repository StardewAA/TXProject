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
  /** 受邀供应商（询价单送达对象，供应商数据隔离依据） */
  export interface InvitedSupplier {
    supplierId?: number;
    supplierName?: string;
    /** 报价状态：0-未报价，20-已报价 */
    quoteStatus?: number;
    invitedAt?: string;
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
    /** 保存时提交的受邀供应商编号；留空表示不限受邀范围 */
    supplierIds?: number[];
    items?: Item[];
    quotes?: Quote[];
    /** 供应商数据隔离：受邀供应商（询价送达对象） */
    invitedSuppliers?: InvitedSupplier[];
    /** 反审批原因（团队 master 引入） */
    reverseApproveReason?: string;
    awardReverseApproveReason?: string;
  }
  export interface PageReq extends PageParam {
    awardOnly?: boolean;
  }
  /** 横向比价 - 单家供应商对某物料的报价 */
  export interface ComparisonQuote {
    quoteItemId?: number;
    quoteId?: number;
    quoteNo?: string;
    supplierId?: number;
    supplierName?: string;
    count?: number;
    productPrice?: number;
    taxPercent?: number;
    freightPrice?: number;
    totalPrice?: number;
    deliveryDate?: string;
    paymentTerm?: string;
    awarded?: boolean;
    priceScore?: number;
    deliveryScore?: number;
    paymentScore?: number;
    totalScore?: number;
    rank?: number;
    lowestPrice?: boolean;
    recommended?: boolean;
    /** HIGH 偏高 / LOW 偏低 / 空 正常 */
    priceAnomaly?: string;
    scoreDetail?: string;
  }
  /** 横向比价 - 物料维度 */
  export interface ComparisonMaterial {
    inquiryItemId?: number;
    productId?: number;
    productCode?: string;
    productName?: string;
    productUnitName?: string;
    count?: number;
    lowestPrice?: number;
    averagePrice?: number;
    medianPrice?: number;
    highestPrice?: number;
    recommendedQuoteItemId?: number;
    recommendedSupplierId?: number;
    recommendedSupplierName?: string;
    recommendedScore?: number;
    awarded?: boolean;
    quotes?: ComparisonQuote[];
  }
  /** 横向比价结果 */
  export interface Comparison {
    inquiryId?: number;
    inquiryNo?: string;
    title?: string;
    supplierCount?: number;
    materials?: ComparisonMaterial[];
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
/** 获得横向比价（物料维度，含加权评分、排名、最优推荐、价格异常标记） */
export const getInquiryComparison = (inquiryId: number) =>
  requestClient.get<ErpInquiryApi.Comparison>(`${base}/comparison`, {
    params: { inquiryId },
  });
/** 当前登录账号绑定的供应商编号；内部账号返回 null */
export const getLoginSupplierId = () =>
  requestClient.get<null | number>(`${base}/login-supplier`);
/** 反审批询价单（审批中 / 已审批 → 草稿），reason 为必填原因 */
export function reverseApproveInquiry(id: number, reason: string) {
  return requestClient.put(`${base}/reverse-approve`, null, {
    params: { id, reason },
  });
}
/** 反审批定标结果（定标审批中 / 已定标 → 未定标），reason 为必填原因 */
export function reverseApproveAward(id: number, reason: string) {
  return requestClient.put(`${base}/award/reverse-approve`, null, {
    params: { id, reason },
  });
}
