import { describe, expect, it } from 'vitest';

import { quoteActions, validateQuoteSave, yuanToCents } from './model';

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
});
