import { test } from 'node:test';
import assert from 'node:assert/strict';
import { publicInquirySummary, publicInquiryDetail, publicInquiryPage, inquiryStatusText, canPublicQuote } from '../sheep/helper/inquiry-market.mjs';

const inquiry = { id: 7, no: 'XJ7', title: '采购保温材料', enterpriseName: '示例企业', deliveryArea: '陕西省西安市', deadline: Date.now() + 60_000, status: 1, quoteCount: 2, itemCount: 1 };

test('public inquiry uses an explicit privacy allow-list', () => {
  const row = publicInquirySummary({ ...inquiry, contactName: '秘密联系人', contactMobile: '13800000000', address: '精确门牌', memberId: 9 });
  assert.equal(row.title, inquiry.title);
  for (const field of ['contactName', 'contactMobile', 'address', 'memberId']) assert.equal(Object.hasOwn(row, field), false);
});

test('detail keeps only public item snapshots', () => {
  const detail = publicInquiryDetail({ order: inquiry, remark: '按图报价', items: [{ id: 3, name: '岩棉板', category: '保温材料', quantity: 20, unit: '立方米', remark: '防水包装', privateCost: 1 }] });
  assert.equal(detail.items[0].name, '岩棉板');
  assert.equal(detail.items[0].remark, '防水包装');
  assert.equal(detail.order.remark, '按图报价');
  assert.equal(Object.hasOwn(detail.items[0], 'privateCost'), false);
});

test('malformed rows are errors rather than empty inquiry cards', () => {
  for (const value of [null, {}, { ...inquiry, id: 0 }, { ...inquiry, title: '' }, { ...inquiry, deadline: 0 }]) assert.throws(() => publicInquirySummary(value));
  assert.throws(() => publicInquiryPage({ list: null, total: 0 }));
});

test('status and quote action follow server state and deadline', () => {
  const future = Date.now() + 10_000, past = Date.now() - 10_000;
  assert.equal(inquiryStatusText(1, future), '报价中');
  assert.equal(inquiryStatusText(1, past), '已截止');
  assert.equal(inquiryStatusText(4, future), '已成交');
  assert.equal(canPublicQuote({ status: 1, deadline: future }), true);
  assert.equal(canPublicQuote({ status: 1, deadline: past }), false);
});
