import type { PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace SupplierInquiryApi {
  export interface Shop {
    id: number;
    enterpriseId: number;
    name: string;
    logoUrl?: string;
    status: number;
  }

  export interface Summary {
    id: number;
    inquiryTenantId?: number;
    no?: string;
    title: string;
    enterpriseName?: string;
    deliveryArea?: string;
    itemSummary?: string;
    visibility?: 'INVITED_SHOPS' | 'PLATFORM_PUBLIC' | 'TENANT_PUBLIC';
    quantitySummary?: string;
    deadline?: number;
    expectedDate?: number;
    status: number;
    quoteCount?: number;
    itemCount?: number;
    createTime?: number | string;
    /** 仅代表询价时间窗开放；最终报价资格仍由后台权限和店铺授权校验。 */
    canQuote?: boolean;
  }

  export interface InquiryItem {
    id: number;
    code?: string;
    name: string;
    category?: string;
    specification?: string;
    quantity: number;
    unit?: string;
    remark?: string;
    picUrl?: string;
  }

  export interface Detail {
    order: Summary;
    remark?: string;
    items: InquiryItem[];
  }

  export interface QuoteItem {
    inquiryItemId: number;
    skuId?: number;
    productName?: string;
    skuName?: string;
    skuSpecification?: string;
    name?: string;
    specification?: string;
    quantity?: number;
    unit?: string;
    count: number;
    unitPrice: number;
    totalPrice?: number;
  }

  export interface Quote {
    id?: number;
    inquiryId: number;
    inquiryNo?: string;
    inquiryTitle?: string;
    shopId: number;
    supplierTenantId?: number;
    shopName?: string;
    /** 0 草稿，1 已提交，2 已采纳，3 未采纳，4 已撤回。 */
    status: number;
    version: number;
    goodsPrice?: number;
    freightPrice: number;
    totalPrice: number;
    deliveryDate?: number;
    validUntil?: number;
    remark?: string;
    createTime?: number | string;
    updateTime?: number | string;
    submittedAt?: number | string;
    acceptedAt?: number | string;
    items: QuoteItem[];
    canQuote?: boolean;
    canEdit?: boolean;
    canSubmit?: boolean;
    canWithdraw?: boolean;
    canMessage?: boolean;
  }

  export interface QuoteSku {
    id: number;
    spuId: number;
    productName: string;
    specification?: string;
    price: number;
    barCode?: string;
    picUrl?: string;
  }

  export interface QuoteSave {
    id?: number;
    inquiryId: number;
    shopId: number;
    version: number;
    requestKey: string;
    freightPrice: number;
    deliveryDate: number;
    validUntil: number;
    remark?: string;
    items: Array<{
      count: number;
      inquiryItemId: number;
      skuId: number;
      unitPrice: number;
    }>;
  }

  export interface Message {
    id: number;
    quoteId: number;
    senderRole: string;
    clientMessageId?: string;
    content: string;
    createTime?: number | string;
  }
}

const supplierPath = '/product/purchase-inquiry/supplier';
const quotePath = `${supplierPath}/quote`;

export function getSupplierShops() {
  return requestClient.get<SupplierInquiryApi.Shop[]>(
    '/product/admin-business-scope/shops',
  );
}

export function getOpportunityPage(params: {
  keyword?: string;
  pageNo: number;
  pageSize: number;
  shopId: number;
  state?: string;
}) {
  return requestClient.get<PageResult<SupplierInquiryApi.Summary>>(
    `${supplierPath}/opportunity-page`,
    { params },
  );
}

export function getOpportunity(id: number, shopId: number) {
  return requestClient.get<SupplierInquiryApi.Detail>(
    `${supplierPath}/opportunity-get`,
    { params: { id, shopId } },
  );
}

export function getMyQuote(inquiryId: number, shopId: number) {
  return requestClient.get<null | SupplierInquiryApi.Quote>(`${quotePath}/mine`, {
    params: { inquiryId, shopId },
  });
}

export function getQuotePage(params: {
  pageNo: number;
  pageSize: number;
  shopId: number;
  status?: number;
}) {
  return requestClient.get<PageResult<SupplierInquiryApi.Quote>>(
    `${quotePath}/page`,
    { params },
  );
}

export function getQuote(id: number) {
  return requestClient.get<SupplierInquiryApi.Quote>(`${quotePath}/get`, {
    params: { id },
  });
}

export function getQuoteSkuOptions(shopId: number) {
  return requestClient.get<SupplierInquiryApi.QuoteSku[]>(
    `${quotePath}/sku-options`,
    { params: { shopId } },
  );
}

export function saveQuote(data: SupplierInquiryApi.QuoteSave) {
  return requestClient.post<number>(`${quotePath}/save`, data);
}

export function submitQuote(id: number, version: number) {
  return requestClient.put<boolean>(`${quotePath}/submit`, undefined, {
    params: { id, version },
  });
}

export function withdrawQuote(id: number, version: number) {
  return requestClient.put<boolean>(`${quotePath}/withdraw`, undefined, {
    params: { id, version },
  });
}

export function getQuoteMessages(
  quoteId: number,
  afterId = 0,
  pageSize = 100,
) {
  return requestClient.get<SupplierInquiryApi.Message[]>(`${quotePath}/messages`, {
    params: { afterId, pageSize, quoteId },
  });
}

export function sendQuoteMessage(data: {
  clientMessageId: string;
  content: string;
  quoteId: number;
}) {
  return requestClient.post<number>(`${quotePath}/messages`, data);
}
