import { describe, expect, it } from 'vitest';

import {
  quoteActions,
  relatedQuoteSkus,
  validateQuoteSave,
  yuanToCents,
} from './model';

const quote = {
  id: 9,
  inquiryId: 3,
  shopId: 5,
  status: 0,
  version: 1,
  freightPrice: 0,
  totalPrice: 100,
  items: [],
  canEdit: true,
  canSubmit: true,
  canWithdraw: false,
  canMessage: true,
};

describe('supplier inquiry model', () => {
  it('requires both backend capability and matching frontend permission', () => {
    expect(quoteActions(quote, { message: false, quote: true }, true)).toMatchObject({
      edit: true,
      message: false,
      submit: true,
      withdraw: false,
    });
    expect(quoteActions(quote, { message: true, quote: false }, true)).toMatchObject({
      edit: false,
      message: true,
      submit: false,
    });
  });

  it('does not create a quote from a display-only opportunity flag', () => {
    expect(quoteActions(null, { message: false, quote: false }, true).create).toBe(false);
    expect(quoteActions(null, { message: false, quote: true }, false).create).toBe(false);
  });

  it('converts normal decimal yuan without floating point rejection', () => {
    expect(yuanToCents(19.99, '单价')).toBe(1999);
    expect(() => yuanToCents(1.001, '单价')).toThrow('最多保留两位小数');
  });

  it('rejects incomplete SKU mapping before calling the API', () => {
    expect(() => validateQuoteSave({
      inquiryId: 3,
      shopId: 5,
      version: 0,
      requestKey: 'quote-save-local-test',
      freightPrice: 0,
      deliveryDate: Date.now() + 86_400_000,
      validUntil: Date.now() + 86_400_000,
      items: [{ inquiryItemId: 7, skuId: 0, count: 1, unitPrice: 100 }],
    })).toThrow('有效 SKU');
  });

  it('accepts a partial quote containing at least one complete line', () => {
    expect(validateQuoteSave({
      inquiryId: 3,
      shopId: 5,
      version: 0,
      requestKey: 'quote-save-partial-test',
      freightPrice: 0,
      deliveryDate: Date.now() + 86_400_000,
      validUntil: Date.now() + 86_400_000,
      items: [{ inquiryItemId: 7, skuId: 9, count: 1, unitPrice: 100 }],
    }).items).toHaveLength(1);
  });

  it('filters quote SKU choices by material name instead of packaging alone', () => {
    const skus = [
      { id: 1, spuId: 1, productName: '陕北工矿 高温纤维粘结剂 20kg', specification: '20kg/桶', price: 100 },
      { id: 2, spuId: 2, productName: '陕北工矿 环氧煤沥青防腐漆 20kg', specification: '20kg/桶', price: 100 },
      { id: 3, spuId: 3, productName: '硅酸铝高温纤维粘结剂', specification: '25kg/桶', price: 100 },
    ];
    expect(relatedQuoteSkus({ name: '高温纤维粘结剂 20kg' }, skus).map((sku) => sku.id))
      .toEqual([1, 3]);
  });

  it('keeps an exact barcode match and an existing saved SKU mapping', () => {
    const skus = [
      { id: 1, spuId: 1, productName: '商品甲', barCode: 'SKU-A', price: 100 },
      { id: 2, spuId: 2, productName: '商品乙', barCode: 'SKU-B', price: 100 },
    ];
    expect(relatedQuoteSkus({ name: '其他物料', code: 'sku-a', skuId: 2 }, skus).map((sku) => sku.id))
      .toEqual([1, 2]);
  });
});
