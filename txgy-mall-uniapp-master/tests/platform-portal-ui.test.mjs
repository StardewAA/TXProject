import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const root = new URL('../', import.meta.url);
const home = readFileSync(new URL('pages/index/index.vue', root), 'utf8');
const list = readFileSync(new URL('pages/goods/list.vue', root), 'utf8');
const detail = readFileSync(new URL('pages/goods/index.vue', root), 'utf8');
const api = readFileSync(new URL('sheep/api/product/portal.js', root), 'utf8');
const pagesWithoutTenantSwitch = [
  'pages/index/category.vue',
  'pages/shop/list.vue',
  'pages/inquiry/index.vue',
  'pages/inquiry/detail.vue',
].map((path) => readFileSync(new URL(path, root), 'utf8'));

test('H5 home is a platform portal without the retired tenant switch or anonymous public-market preview', () => {
  assert.doesNotMatch(home, /tenantEntry/);
  assert.doesNotMatch(home, /s-public-market-entry/);
  assert.match(home, /PortalApi\.getProducts/);
  assert.match(home, /PortalApi\.getHotTenants/);
  assert.match(home, /tenant\.tenantSlug/);
  assert.match(home, /product\.enterpriseName/);
  assert.match(home, /product\.shopName/);
  assert.match(home, /portalProductDetailPath/);
});

test('product list uses portal endpoints only for explicit platform or tenant-slug routes', () => {
  assert.match(list, /options\.portal === '1' \|\| Boolean\(options\.tenantSlug\)/);
  assert.match(list, /PortalApi\.getTenantProducts/);
  assert.match(list, /PortalApi\.getProducts/);
  assert.doesNotMatch(list, /tenantEntry/);
  assert.match(list, /!state\.portal && userStore\.isLogin/);
  assert.match(list, /SpuApi\.getSpuPage/);
  assert.match(list, /portalProductDetailPath/);
  assert.doesNotMatch(list, /title: item\.spuName/);
});

test('public browsing pages no longer render the retired top tenant switch', () => {
  for (const page of pagesWithoutTenantSwitch) assert.doesNotMatch(page, /tenantEntry/);
});

test('platform detail loads by tenant slug and never falls through to legacy tenant APIs', () => {
  assert.match(detail, /PortalApi\.getTenantProduct\(state\.tenantSlug, state\.goodsId\)/);
  assert.match(detail, /PortalApi\.getProduct\(state\.goodsId\)/);
  assert.match(detail, /if \(state\.portal\) \{\s*loadPortalProduct\(\);\s*return;/);
  assert.match(detail, /normalizePortalTenantSlug\(options\.tenantSlug\)/);
  assert.match(detail, /v-if="!state\.portal" class="detail-comment-selector"/);
  assert.match(detail, /公开可售规格/);
  assert.match(detail, />店铺客服<\/button>/);
  assert.match(detail, /chatEntry\(state\.goodsInfo\?\.shopId, 'PRODUCT', state\.goodsInfo\?\.id, skuId, state\.goodsInfo\?\.tenantId\)/);
  assert.doesNotMatch(detail, />平台商品<\/button>/);
});

test('every portal API call declares the platform request scope', () => {
  assert.match(api, /scope: 'platform'/);
  assert.match(api, /isToken: false/);
  for (const path of ['/mall/portal/products', '/mall/portal/tenants/hot', '/mall/tenants/']) {
    assert.match(api, new RegExp(path.replaceAll('/', '\\/')));
  }
  assert.match(api, /getProduct\(spuId\)/);
  assert.match(api, /getTenantProduct\(tenantSlug, spuId\)/);
});
