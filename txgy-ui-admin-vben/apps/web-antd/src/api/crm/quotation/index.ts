import type { PageParam, PageResult } from '@vben/request';

import type { CrmPermissionApi } from '#/api/crm/permission';

import { requestClient } from '#/api/request';

export namespace CrmQuotationApi {
  /** 报价单信息 */
  export interface Quotation {
    id?: number;
    name: string;
    no?: string;
    quotationType?: number; // 报价类型：1=购销报价 2=采购报价
    /** 采购报价的供应商（仅前端表单字段，提交时映射到 customerId） */
    supplierId?: number;
    /** 供应商联系人（采购报价，随供应商自动带出） */
    supplierContact?: string;
    /** 供应商联系电话（采购报价） */
    supplierPhone?: string;
    /** 供应商地址（采购报价） */
    supplierAddress?: string;
    customerId: number;
    customerName?: string;
    businessId?: number;
    businessName?: string;
    ownerUserId: number;
    ownerUserName?: string;
    ownerUserDeptName?: string;
    processInstanceId?: string;
    auditStatus: number;
    orderDate: string;
    validUntil?: string;
    totalProductPrice?: number;
    discountPercent: number;
    totalPrice?: number;
    contractId?: number;
    remark?: string;
    createTime?: string;
    creator?: string;
    creatorName?: string;
    updateTime?: string;
    products?: QuotationProduct[];
  }

  /** 报价单产品信息 */
  export interface QuotationProduct {
    id?: number;
    productId: number;
    productName: string;
    productNo: string;
    productUnit: number;
    productPrice: number;
    quotationPrice: number;
    count: number;
    totalPrice: number;
  }

  /** 文本智能解析结果 */
  export interface ParseResult {
    customerName?: string;
    contactName?: string;
    phone?: string;
    email?: string;
    address?: string;
    qq?: string;
    remark?: string;
    products?: ParseProduct[];
  }

  /** 解析出的产品项 */
  export interface ParseProduct {
    productName: string;
    count: number;
    unit: string;
    price?: number;
  }
}

/** 查询报价单列表 */
export function getQuotationPage(params: PageParam) {
  return requestClient.get<PageResult<CrmQuotationApi.Quotation>>(
    '/crm/quotation/page',
    { params },
  );
}

/** 查询报价单详情 */
export function getQuotation(id: number) {
  return requestClient.get<CrmQuotationApi.Quotation>(
    `/crm/quotation/get?id=${id}`,
  );
}

/** 新增报价单 */
export function createQuotation(data: CrmQuotationApi.Quotation) {
  return requestClient.post('/crm/quotation/create', data);
}

/** 修改报价单 */
export function updateQuotation(data: CrmQuotationApi.Quotation) {
  return requestClient.put('/crm/quotation/update', data);
}

/** 删除报价单 */
export function deleteQuotation(id: number) {
  return requestClient.delete(`/crm/quotation/delete?id=${id}`);
}

/** 导出报价单 */
export function exportQuotation(params: any) {
  return requestClient.download('/crm/quotation/export-excel', { params });
}

/** 提交报价单审批 */
export function submitQuotation(id: number) {
  return requestClient.put(`/crm/quotation/submit?id=${id}`);
}

/** 报价单转移 */
export function transferQuotation(data: CrmPermissionApi.BusinessTransferReqVO) {
  return requestClient.put('/crm/quotation/transfer', data);
}

/** 报价单转为合同：返回生成的合同编号 */
export function createContractByQuotation(id: number) {
  return requestClient.post<number>(`/crm/quotation/create-contract?id=${id}`);
}

/** 智能解析报价单文本 */
export function parseQuotationText(content: string) {
  return requestClient.post<CrmQuotationApi.ParseResult>('/crm/quotation/parse-text', {
    content,
  });
}
