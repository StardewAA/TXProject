import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  formatPlatformProperties,
  normalizeCartItemIds,
  normalizePlatformCart,
  normalizePlatformCheckoutDetail,
  normalizePlatformCheckoutPage,
  normalizePlatformPreview,
  openPlatformCheckoutIntent,
  platformCheckoutAggregateLabel,
  platformCheckoutHistoryPath,
  platformCheckoutPath,
  platformCheckoutPayLabel,
  platformSelectedIds,
} from '../sheep/helper/platform-commerce.mjs';

const cartPayload = () => ({
  tenantGroups: [{
    tenantId: 2009,
    tenantName: '科盾公司',
    shops: [{
      enterpriseId: 81,
      enterpriseName: '科盾防腐科技',
      shopId: 91,
      shopName: '科盾旗舰店',
      items: [
        { id: 11, tenantId: 2009, enterpriseId: 81, shopId: 91, spuId: 101, skuId: 1001,
          spuName: '环氧富锌底漆', quantity: 2, price: 4300, selected: true, valid: true,
          properties: [{ propertyName: '包装', valueName: '20kg/桶' }] },
        { id: 12, tenantId: 2009, enterpriseId: 81, shopId: 91, spuId: 102, skuId: 1002,
          spuName: '失效商品', quantity: 1, price: 100, selected: true, valid: false, invalidReason: '商品已下架' },
      ],
    }],
  }],
});

test('platform cart keeps server ownership groups and excludes invalid rows from checkout selection', () => {
  const cart = normalizePlatformCart(cartPayload());
  assert.equal(cart.tenantGroups[0].tenantId, 2009);
  assert.equal(cart.tenantGroups[0].shops[0].shopId, 91);
  assert.equal(cart.rows.length, 2);
  assert.deepEqual(platformSelectedIds(cart), [11]);
  assert.equal(cart.rows[1].invalidReason, '商品已下架');
  assert.equal(formatPlatformProperties(cart.rows[0].properties), '20kg/桶');
});

test('platform cart rejects retired tenant and ownership mismatches instead of repairing client data', () => {
  const retired = cartPayload();
  retired.tenantGroups[0].tenantId = 3000;
  assert.equal(normalizePlatformCart(retired).rows.length, 0);
  const mismatch = cartPayload();
  mismatch.tenantGroups[0].shops[0].items[0].shopId = 92;
  const cart = normalizePlatformCart(mismatch);
  assert.equal(cart.rows.some((item) => item.id === 11), false);
});

test('checkout paths contain only validated cart item ids and no tenant hint', () => {
  assert.deepEqual(normalizeCartItemIds(['12', 11, 12]), [12, 11]);
  const url = new URL(platformCheckoutPath([11, 12]), 'https://mall.example');
  assert.equal(url.pathname, '/pages/platform/checkout');
  assert.equal(url.searchParams.get('cartItemIds'), '11,12');
  assert.equal(url.searchParams.has('tenantId'), false);
  for (const invalid of [[], [0], ['1&tenantId=2'], Array.from({ length: 201 }, (_, index) => index + 1)]) {
    assert.throws(() => normalizeCartItemIds(invalid));
  }
  const history = new URL(platformCheckoutHistoryPath('PC202609210001'), 'https://mall.example');
  assert.equal(history.searchParams.get('checkoutNo'), 'PC202609210001');
  assert.equal(history.searchParams.has('tenantId'), false);
});

test('checkout preview accounts for every requested row and always stays fail closed', () => {
  const preview = normalizePlatformPreview({
    platformAccountId: 8,
    validItemCount: 1,
    totalQuantity: 2,
    goodsAmount: 8600,
    freightAmount: 0,
    discountAmount: 0,
    payAmount: 8600,
    validationPassed: true,
    submittable: true,
    address: { addressId: 9 },
    blockingReasons: ['聚合支付未开放'],
    tenantGroups: [{ tenantId: 2009, tenantName: '科盾公司', shops: [{ shopId: 91, shopName: '科盾旗舰店',
      enterpriseName: '科盾防腐科技', items: [{ cartItemId: 11, skuId: 1001 }] }] }],
    failedItems: [{ cartItemId: 12, skuId: 1002, reason: '商品已下架' }],
  }, [11, 12]);
  assert.equal(preview.validationPassed, true);
  assert.equal(preview.submittable, false);
  assert.deepEqual(preview.failedItems, [{ cartItemId: 12, reason: '商品已下架' }]);
  assert.throws(() => normalizePlatformPreview({ ...preview, failedItems: [], tenantGroups: [] }, [11]));
});

test('checkout history exposes only normalized planning summaries and keeps drafts non-payable', () => {
  const result = normalizePlatformCheckoutPage({
    total: 1,
    list: [{
      checkoutNo: 'PC202609210001', currency: 'CNY', goodsAmount: 8600, freightAmount: 600,
      discountAmount: 0, payAmount: 9200, paidAmount: 0, refundedAmount: 0,
      aggregateStatus: 'DRAFT_ONLY', payStatus: 'NOT_PAYABLE', payable: true,
      planningOnly: true, createTime: '2026-09-21T10:20:30', address: { mobile: '13800000000' },
    }],
  });
  assert.equal(result.total, 1);
  assert.equal(result.list.length, 1);
  assert.equal(result.list[0].payable, false);
  assert.equal(Object.hasOwn(result.list[0], 'address'), false);
  assert.equal(platformCheckoutAggregateLabel('DRAFT_ONLY'), '拆单规划');
  assert.equal(platformCheckoutAggregateLabel('PARTIALLY_DELIVERED'), '部分已发货');
  assert.equal(platformCheckoutAggregateLabel('ALL_COMPLETED'), '全部完成');
  assert.equal(platformCheckoutAggregateLabel('PARTIALLY_REFUNDED'), '部分退款');
  assert.equal(platformCheckoutPayLabel('NOT_PAYABLE'), '不可支付');
  assert.equal(new URL(platformCheckoutHistoryPath(), 'https://mall.example').searchParams.has('tenantId'), false);
  assert.throws(() => normalizePlatformCheckoutPage({ total: 1, list: [{ checkoutNo: '../bad' }] }));
});

test('checkout detail validates identity and keeps only valid split plan items', () => {
  const detail = normalizePlatformCheckoutDetail({
    checkoutNo: 'PC202609210001', currency: 'CNY', goodsAmount: 8600, freightAmount: 600,
    discountAmount: 0, payAmount: 9200, paidAmount: 0, refundedAmount: 0,
    aggregateStatus: 'DRAFT_ONLY', payStatus: 'NOT_PAYABLE', planningOnly: true, payable: false,
    childPlans: [{
      planNo: 'PLAN-1', tenantId: 2009, sellerEnterpriseId: 81, shopId: 91,
      tenantName: '科盾公司', enterpriseName: '科盾防腐科技', shopName: '科盾旗舰店',
      itemCount: 1, totalQuantity: 2, goodsAmount: 8600, freightAmount: 600,
      discountAmount: 0, payAmount: 9200, planStatus: 'DRAFT',
      items: [{ cartItemId: 11, tenantId: 2009, enterpriseId: 81, shopId: 91, spuId: 101,
        skuId: 1001, spuName: '环氧富锌底漆', quantity: 2, unitPrice: 4300, goodsAmount: 8600 }],
    }],
  }, 'PC202609210001');
  assert.equal(detail.childPlans.length, 1);
  assert.equal(detail.childPlans[0].items.length, 1);
  assert.equal(detail.payable, false);
  assert.throws(() => normalizePlatformCheckoutDetail({ ...detail, checkoutNo: 'PC-OTHER' }, 'PC202609210001'));
  const brokenPlan = { ...detail.childPlans[0], itemCount: 2 };
  assert.throws(() => normalizePlatformCheckoutDetail({ ...detail, childPlans: [brokenPlan] }, detail.checkoutNo));
});

test('platform checkout intent is stable for an ambiguous retry and rotates with address, account or success', () => {
  const values = new Map();
  const storage = {
    getItem: (key) => values.get(key) || null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key),
  };
  let sequence = 0;
  const suffix = () => `intent-${++sequence}`;
  const owner = { carrierTenantId: 1, memberId: 316 };
  const first = openPlatformCheckoutIntent(storage, owner, [12, 11], 9, suffix);
  const retry = openPlatformCheckoutIntent(storage, owner, [11, 12], 9, suffix);
  assert.equal(retry.idempotencyKey, first.idempotencyKey);
  const changedAddress = openPlatformCheckoutIntent(storage, owner, [11, 12], 10, suffix);
  assert.notEqual(changedAddress.idempotencyKey, first.idempotencyKey);
  const changedAccount = openPlatformCheckoutIntent(storage,
    { carrierTenantId: 1, memberId: 317 }, [11, 12], 9, suffix);
  assert.notEqual(changedAccount.idempotencyKey, first.idempotencyKey);
  first.clear();
  const nextConfirmedAttempt = openPlatformCheckoutIntent(storage, owner, [11, 12], 9, suffix);
  assert.notEqual(nextConfirmedAttempt.idempotencyKey, first.idempotencyKey);
});

test('platform checkout intent fails closed when identity or durable storage is unavailable', () => {
  const unusable = { getItem: () => null, setItem() {}, removeItem() {} };
  assert.throws(() => openPlatformCheckoutIntent(unusable, {}, [11], 9, () => 'intent-1'));
  assert.throws(() => openPlatformCheckoutIntent(unusable,
    { carrierTenantId: 1, memberId: 316 }, [11], 0, () => 'intent-1'));
  const discarded = { getItem: () => null, setItem() {}, removeItem() {} };
  assert.throws(() => openPlatformCheckoutIntent(discarded,
    { carrierTenantId: 1, memberId: 316 }, [11], 9, () => 'intent-1'));
});
