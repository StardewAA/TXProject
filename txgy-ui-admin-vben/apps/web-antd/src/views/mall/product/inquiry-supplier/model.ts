import type { SupplierInquiryApi } from '#/api/mall/product/inquiry-supplier';

export const INQUIRY_QUERY_PERMISSION = 'product:inquiry-supplier:query';
export const INQUIRY_QUOTE_PERMISSION = 'product:inquiry-supplier:quote';
export const INQUIRY_MESSAGE_PERMISSION = 'product:inquiry-supplier:message';

export const inquiryStatus: Record<number, string> = {
  1: '报价中',
  2: '已截止',
  3: '已关闭',
  4: '已成交',
};

export const quoteStatus: Record<number, string> = {
  0: '草稿',
  1: '已提交',
  2: '已采纳',
  3: '未采纳',
  4: '已撤回',
};

export function positiveId(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0;
}
export function money(cents?: number) {
  return (Number.isSafeInteger(cents) ? Number(cents) / 100 : 0).toFixed(2);
}

export function yuanToCents(value: number, label: string) {
  if (!Number.isFinite(value) || value < 0) {
    throw new Error(`${label}必须是非负金额`);
  }
  const cents = Math.round(value * 100);
  if (!Number.isSafeInteger(cents) || Math.abs(value * 100 - cents) > 1e-6) {
    throw new Error(`${label}最多保留两位小数`);
  }
  return cents;
}

export function makeRequestKey(prefix: 'quote-message' | 'quote-save') {
  const suffix = globalThis.crypto?.randomUUID?.()
    ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `${prefix}-${suffix}`;
}

function normalizedSkuText(value: unknown): string {
  return String(value ?? '').toLocaleLowerCase().replace(/[^\p{L}\p{N}]+/gu, '');
}

function textBigrams(value: string): Set<string> {
  const result = new Set<string>();
  for (let index = 0; index < value.length - 1; index += 1) {
    result.add(value.slice(index, index + 2));
  }
  return result;
}

function relatedProductName(requestedName: string, productName: string): boolean {
  const requested = normalizedSkuText(requestedName);
  const candidate = normalizedSkuText(productName);
  if (!requested || !candidate) return false;
  if (requested.includes(candidate) || candidate.includes(requested)) return true;
  if (requested.length < 4 || candidate.length < 4) return false;
  const requestedPairs = textBigrams(requested);
  const candidatePairs = textBigrams(candidate);
  let shared = 0;
  requestedPairs.forEach((pair) => {
    if (candidatePairs.has(pair)) shared += 1;
  });
  return shared >= 2
    && (2 * shared) / (requestedPairs.size + candidatePairs.size) >= 0.45;
}

export function relatedQuoteSkus(
  item: { code?: string; name: string; skuId?: number },
  skus: SupplierInquiryApi.QuoteSku[],
) {
  const requestedCode = normalizedSkuText(item.code);
  return skus.filter((sku) => sku.id === item.skuId
    || Boolean(requestedCode && requestedCode === normalizedSkuText(sku.barCode))
    || relatedProductName(item.name, sku.productName));
}

export function quoteActions(
  quote: null | SupplierInquiryApi.Quote | undefined,
  permissions: { message: boolean; quote: boolean },
  opportunityOpen = false,
) {
  return {
    create: !quote && permissions.quote && opportunityOpen,
    edit: !!quote && permissions.quote && quote.canEdit === true,
    message: !!quote?.id && permissions.message && quote.canMessage === true,
    submit: !!quote?.id && permissions.quote && quote.canSubmit === true,
    withdraw: !!quote?.id && permissions.quote && quote.canWithdraw === true,
  };
}

export function validateQuoteSave(data: SupplierInquiryApi.QuoteSave) {
  if (!positiveId(data.inquiryId) || !positiveId(data.shopId)) {
    throw new Error('询价单或报价店铺无效，请刷新后重试');
  }
  if (!Number.isSafeInteger(data.version) || data.version < 0) {
    throw new Error('报价版本无效，请刷新后重试');
  }
  if (!/^[\w-]{8,64}$/.test(data.requestKey)) {
    throw new Error('报价请求标识无效，请刷新后重试');
  }
  if (!Number.isSafeInteger(data.freightPrice) || data.freightPrice < 0) {
    throw new Error('运费必须是非负金额');
  }
  if (!Number.isSafeInteger(data.validUntil) || data.validUntil <= Date.now()) {
    throw new Error('报价有效期必须晚于当前时间');
  }
  if (!Number.isSafeInteger(data.deliveryDate) || data.deliveryDate <= Date.now()) {
    throw new Error('预计交货日期必须晚于当前时间');
  }
  if (!data.items.length) throw new Error('请至少选择一项可供应的 SKU 并填写报价');
  for (const item of data.items) {
    if (!positiveId(item.inquiryItemId) || !positiveId(item.skuId)) {
      throw new Error('每项需求都必须映射为本店铺有效 SKU');
    }
    if (!positiveId(item.count) || !positiveId(item.unitPrice)) {
      throw new Error('包装件数和含税单价必须大于零');
    }
  }
  return data;
}
