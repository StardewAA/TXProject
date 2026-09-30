import test from 'node:test';
import assert from 'node:assert/strict';
import { normalizeFileUrl, normalizeRichImageUrls } from '../sheep/helper/file-url.mjs';
import { normalizePlatformCheckoutDetail } from '../sheep/helper/platform-commerce.mjs';
import { cashierPaymentState, stampPaymentClock } from '../sheep/helper/payment-deadline.mjs';

test('historical loopback images use same-origin file ACL including rich content', () => {
  const url = 'http://127.0.0.1:48080/admin-api/infra/file/4/get/a%2Bb.png';
  assert.equal(normalizeFileUrl(url), '/app-api/infra/file/4/get/a%2Bb.png');
  assert.match(normalizeRichImageUrls(`<p><img src="${url}"></p>`), /src="\/app-api\//);
  assert.equal(normalizeFileUrl('https://cdn.example/a.png'), 'https://cdn.example/a.png');
  assert.equal(normalizeFileUrl('http://127.0.0.1:48080/other/a.png'), 'http://127.0.0.1:48080/other/a.png');
});

test('formal checkout is not mistaken for a draft and payment uses its aggregate relationship', () => {
  const checkout = normalizePlatformCheckoutDetail({
    checkoutNo: 'pc-test-1', goodsAmount: 15800, freightAmount: 1200, discountAmount: 0,
    payAmount: 17000, paidAmount: 0, refundedAmount: 0, aggregateStatus: 'ALL_UNPAID',
    payStatus: 'UNPAID', planningOnly: false, payable: true, platformPayOrderId: 33,
    childOrders: [{ orderId: 45, tenantId: 2024, payAmount: 17000 }], childPlans: [],
  }, 'pc-test-1');
  assert.equal(checkout.planningOnly, false);
  assert.equal(checkout.childOrders.length, 1);
  const pay = stampPaymentClock({ id: 33, merchantOrderId: '-9', price: 17000, status: 0,
    serverTime: 10000, expireTimestamp: 20000 }, 0);
  assert.equal(cashierPaymentState(pay, checkout, 'platform', 1).canPay, true);
  for (const changed of [{ platformPayOrderId: 44 }, { payAmount: 1 }, { payable: false }, { planningOnly: true }]) {
    assert.equal(cashierPaymentState(pay, { ...checkout, ...changed }, 'platform', 1).canPay, false);
  }
  assert.equal(cashierPaymentState(pay, checkout, 'goods', 1).canPay, false);
  assert.equal(cashierPaymentState(pay, checkout, 'platform', 20000).canPay, false);
});

test('child-order cashier accepts an authorized aggregate payment, never an unrelated pay ID', () => {
  const pay = stampPaymentClock({ id: 33, merchantOrderId: '-9', price: 17000, status: 0,
    serverTime: 10000, expireTimestamp: 20000 }, 0);
  const child = stampPaymentClock({ id: 45, payOrderId: 33, status: 0, canPay: true,
    serverTime: 10000, payExpireTimestamp: 20000 }, 0);
  assert.equal(cashierPaymentState(pay, child, 'goods', 1).canPay, true);
  assert.equal(cashierPaymentState(pay, { ...child, canPay: false }, 'goods', 1).canPay, false);
  assert.equal(cashierPaymentState(pay, { ...child, payOrderId: 34 }, 'goods', 1).canPay, false);
  assert.equal(cashierPaymentState({ ...pay, merchantOrderId: '46' }, child, 'goods', 1).canPay, false);
});
