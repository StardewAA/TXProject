import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  normalizePortalPage,
  normalizePortalProduct,
  normalizePortalSpuId,
  normalizePortalTenant,
  normalizePortalTenantSlug,
  portalProductDetailPath,
  portalProductListPath,
  selectFeaturedPortalProducts,
} from '../sheep/helper/platform-portal.mjs';

const validProduct = (overrides = {}) => ({
  tenantId: 2009,
  tenantSlug: 'kedun-materials',
  tenantName: '科盾公司',
  enterpriseId: 81,
  enterpriseName: '科盾防腐科技',
  shopId: 91,
  shopName: '科盾材料旗舰店',
  spuId: 940231,
  spuName: '环氧富锌底漆',
  price: 4300,
  salesCount: 12,
  saleable: true,
  skus: [{ skuId: 9402311, skuName: '20kg/桶', price: 4300 }],
  ...overrides,
});

test('portal rows keep explicit tenant, enterprise and shop ownership', () => {
  const page = normalizePortalPage({ list: [validProduct()], total: 1 });
  assert.equal(page.total, 1);
  assert.equal(page.list[0].tenantId, '2009');
  assert.equal(page.list[0].tenantSlug, 'kedun-materials');
  assert.equal(page.list[0].enterpriseName, '科盾防腐科技');
  assert.equal(page.list[0].shopName, '科盾材料旗舰店');
  assert.equal(page.list[0].id, 940231);
});

test('public detail keeps reviewed description and normalizes SKU identifiers without inventing stock', () => {
  const detail = normalizePortalProduct(validProduct({
    description: '<p>公开商品详情</p>',
    picUrl: '/material.jpg',
    skus: [{ skuId: 9402311, skuName: '20kg/桶', price: 4300, properties: [] }],
  }));
  assert.equal(detail.description, '<p>公开商品详情</p>');
  assert.deepEqual(detail.sliderPicUrls, ['/material.jpg']);
  assert.equal(detail.skus[0].id, 9402311);
  assert.equal(detail.skus[0].price, 4300);
  assert.equal(Object.hasOwn(detail.skus[0], 'stock'), false);
});

test('retired public tenant 3000 and incomplete ownership are never displayed', () => {
  const page = normalizePortalPage({ list: [
    validProduct({ tenantId: 3000 }),
    validProduct({ enterpriseName: '' }),
    validProduct({ shopId: null }),
  ], total: 3 });
  assert.deepEqual(page.list, []);
  assert.equal(normalizePortalTenant({ tenantId: 3000, tenantSlug: 'public', tenantName: '公共商城' }), null);
});

test('tenant slug routes are stable and do not carry a tenant id', () => {
  assert.equal(normalizePortalTenantSlug('  KeDun-Materials  '), 'kedun-materials');
  const path = portalProductListPath({ tenantSlug: 'kedun-materials', keyword: '保温 板' });
  const url = new URL(path, 'https://mall.example');
  assert.equal(url.pathname, '/pages/goods/list');
  assert.equal(url.searchParams.get('portal'), '1');
  assert.equal(url.searchParams.get('tenantSlug'), 'kedun-materials');
  assert.equal(url.searchParams.get('keyword'), '保温 板');
  assert.equal(url.searchParams.has('tenantId'), false);
  const detail = new URL(portalProductDetailPath({ tenantSlug: 'kedun-materials', spuId: 940231 }), 'https://mall.example');
  assert.equal(detail.pathname, '/pages/goods/index');
  assert.equal(detail.searchParams.get('portal'), '1');
  assert.equal(detail.searchParams.get('tenantSlug'), 'kedun-materials');
  assert.equal(detail.searchParams.get('id'), '940231');
  assert.equal(detail.searchParams.has('tenantId'), false);
  assert.equal(normalizePortalSpuId(940231), '940231');
  for (const invalid of ['', 0, -1, '1&tenantId=2', '9007199254740992']) {
    assert.throws(() => normalizePortalSpuId(invalid));
  }
  for (const invalid of ['', '../1', '科盾', 'a_1', '3000?tenantId=1']) {
    assert.throws(() => normalizePortalTenantSlug(invalid));
  }
});

test('home featured products diversify visuals, product families and tenants', () => {
  const candidates = [
    validProduct({ tenantId: 2024, spuId: 1, tenantName: '甲公司', spuName: '甲公司 高温粘结剂', picUrl: '/adhesive.jpg' }),
    validProduct({ tenantId: 2023, spuId: 2, tenantName: '乙公司', spuName: '乙公司 高温粘结剂', picUrl: '/adhesive.jpg' }),
    validProduct({ tenantId: 2024, spuId: 3, tenantName: '甲公司', spuName: '甲公司 保冷管托', picUrl: '/support.jpg' }),
    validProduct({ tenantId: 2023, spuId: 4, tenantName: '乙公司', spuName: '乙公司 保冷管托', picUrl: '/support.jpg' }),
    validProduct({ tenantId: 2022, spuId: 5, tenantName: '丙公司', spuName: '丙公司 岩棉板', picUrl: '/rock-wool.jpg' }),
    validProduct({ tenantId: 2021, spuId: 6, tenantName: '丁公司', spuName: '丁公司 防腐弯头', picUrl: '/elbow.jpg' }),
  ];
  const featured = selectFeaturedPortalProducts(candidates, 4);
  assert.deepEqual(featured.map((item) => item.spuId), [1, 4, 5, 6]);
  assert.equal(new Set(featured.map((item) => item.picUrl)).size, 4);
  assert.equal(new Set(featured.map((item) => item.tenantId)).size, 4);
});
