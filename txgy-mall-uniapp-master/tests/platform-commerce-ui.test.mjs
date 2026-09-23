import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const root = new URL('../', import.meta.url);
const detail = readFileSync(new URL('pages/goods/index.vue', root), 'utf8');
const cart = readFileSync(new URL('pages/platform/cart.vue', root), 'utf8');
const checkout = readFileSync(new URL('pages/platform/checkout.vue', root), 'utf8');
const history = readFileSync(new URL('pages/platform/history.vue', root), 'utf8');
const member = readFileSync(new URL('pages/index/user.vue', root), 'utf8');
const pages = readFileSync(new URL('pages.json', root), 'utf8');
const cartApi = readFileSync(new URL('sheep/api/trade/platformCart.js', root), 'utf8');
const checkoutApi = readFileSync(new URL('sheep/api/trade/platformCheckout.js', root), 'utf8');
const portalApi = readFileSync(new URL('sheep/api/product/portal.js', root), 'utf8');

test('portal detail selects a public SKU and sends only sku id plus quantity to platform cart', () => {
  assert.match(detail, /selectPortalSku\(sku\)/);
  assert.match(detail, /PlatformCartApi\.add\(skuId, quantity\)/);
  assert.doesNotMatch(detail, /PlatformCartApi\.add\([^)]*tenantId/);
  assert.match(detail, /加入平台购物车/);
});

test('platform cart groups tenant then shop and exposes invalid reason without using legacy cart store', () => {
  assert.match(cart, /v-for="tenant in state\.cart\.tenantGroups"/);
  assert.match(cart, /v-for="shop in tenant\.shops"/);
  assert.match(cart, /item\.invalidReason/);
  assert.match(cart, /PlatformCartApi\.updateCount/);
  assert.match(cart, /PlatformCartApi\.updateSelected/);
  assert.match(cart, /PlatformCartApi\.delete/);
  assert.doesNotMatch(cart, /\$store\('cart'\)/);
});

test('platform checkout previews then saves only a non-payable idempotent split plan', () => {
  assert.match(checkout, /PlatformCheckoutApi\.preview\(state\.cartItemIds, state\.addressId\)/);
  assert.match(checkout, /getPlatformAddressList/);
  assert.match(checkout, /openPlatformCheckoutIntent/);
  assert.match(checkout, /PlatformCheckoutApi\.submit/);
  assert.match(checkout, /保存拆单规划/);
  assert.match(checkout, /detail\.aggregateStatus !== 'DRAFT_ONLY'/);
  assert.match(checkout, /detail\.payStatus !== 'NOT_PAYABLE'/);
  assert.match(checkout, /平台购物车仍会保留/);
  assert.doesNotMatch(checkout, /PlatformCartApi\.delete/);
  assert.match(checkoutApi, /platform-checkout\/submit/);
  assert.match(checkoutApi, /data: \{ cartItemIds, addressId, idempotencyKey \}/);
});

test('member can open authenticated platform checkout history without a browse tenant hint', () => {
  assert.match(checkoutApi, /platform-checkout\/page/);
  assert.match(checkoutApi, /platform-checkout\/\$\{encodeURIComponent/);
  assert.match(history, /PlatformCheckoutApi\.page\(pageNo, state\.pageSize\)/);
  assert.match(history, /PlatformCheckoutApi\.get\(checkoutNo\)/);
  assert.match(history, /options\.checkoutNo/);
  assert.match(history, /DRAFT_ONLY \/ NOT_PAYABLE/);
  assert.match(history, /不可支付、不代表正式订单/);
  assert.doesNotMatch(history, /PlatformCheckoutApi\.(?:page|get)\([^)]*tenantId/);
  assert.match(member, /path: '\/pages\/platform\/history'/);
  assert.match(pages, /"path": "pages\/platform\/history"/);
});

test('platform APIs carry no client tenant and public portal reads carry no token', () => {
  assert.match(cartApi, /scope: 'platform'/);
  assert.match(checkoutApi, /scope: 'platform'/);
  assert.doesNotMatch(cartApi, /tenant[-_]?id/i);
  assert.doesNotMatch(checkoutApi, /tenant[-_]?id/i);
  assert.match(portalApi, /isToken: false/);
});
