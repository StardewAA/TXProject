const TENANT_SLUG = /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/;
const RETIRED_PUBLIC_TENANT_ID = '3000';

const positiveId = (value) => {
  const text = String(value ?? '').trim();
  return /^[1-9]\d*$/.test(text) && Number.isSafeInteger(Number(text)) ? text : '';
};

export function normalizePortalSpuId(value) {
  const id = positiveId(value);
  if (!id) throw new Error('商品链接无效');
  return id;
}

export function normalizePortalTenantSlug(value) {
  const slug = String(value ?? '').trim().toLowerCase();
  if (!TENANT_SLUG.test(slug)) throw new Error('租户商城链接无效');
  return slug;
}

export function normalizePortalTenant(value) {
  const tenantId = positiveId(value?.tenantId);
  if (!tenantId || tenantId === RETIRED_PUBLIC_TENANT_ID) return null;
  let tenantSlug;
  try { tenantSlug = normalizePortalTenantSlug(value?.tenantSlug); }
  catch { return null; }
  const tenantName = String(value?.tenantName ?? '').trim();
  if (!tenantName) return null;
  return Object.freeze({
    rank: Number.isSafeInteger(Number(value?.rank)) ? Number(value.rank) : undefined,
    tenantId,
    tenantSlug,
    tenantName,
    tenantLogo: String(value?.tenantLogo ?? '').trim(),
    mainCategories: String(value?.mainCategories ?? '').trim(),
    validQuantity: Math.max(0, Number(value?.validQuantity) || 0),
  });
}

export function normalizePortalProduct(value) {
  const owner = normalizePortalTenant(value);
  const spuId = positiveId(value?.spuId);
  const enterpriseId = positiveId(value?.enterpriseId);
  const shopId = positiveId(value?.shopId);
  const spuName = String(value?.spuName ?? '').trim();
  const enterpriseName = String(value?.enterpriseName ?? '').trim();
  const shopName = String(value?.shopName ?? '').trim();
  const price = Number(value?.price);
  if (!owner || !spuId || !enterpriseId || !shopId || !spuName || !enterpriseName || !shopName
      || !Number.isSafeInteger(price) || price < 0 || value?.saleable !== true) return null;
  const skus = Array.isArray(value?.skus) ? value.skus.map((sku) => {
    const skuId = positiveId(sku?.skuId);
    const skuPrice = Number(sku?.price);
    if (!skuId || !Number.isSafeInteger(skuPrice) || skuPrice < 0) return null;
    return Object.freeze({
      ...sku,
      id: Number(skuId),
      skuId: Number(skuId),
      skuName: String(sku?.skuName ?? '').trim() || '默认规格',
      price: skuPrice,
      picUrl: String(sku?.picUrl ?? '').trim(),
      properties: Object.freeze(Array.isArray(sku?.properties)
        ? sku.properties.filter((property) => property && typeof property === 'object')
        : []),
    });
  }).filter(Boolean) : [];
  if (!skus.length) return null;
  return Object.freeze({
    ...value,
    ...owner,
    id: Number(spuId),
    spuId: Number(spuId),
    enterpriseId: Number(enterpriseId),
    shopId: Number(shopId),
    name: spuName,
    spuName,
    enterpriseName,
    shopName,
    price,
    salesCount: Math.max(0, Number(value?.salesCount) || 0),
    picUrl: String(value?.picUrl ?? '').trim(),
    introduction: String(value?.introduction ?? '').trim(),
    description: String(value?.description ?? '').trim(),
    sliderPicUrls: Object.freeze(String(value?.picUrl ?? '').trim() ? [String(value.picUrl).trim()] : []),
    saleable: true,
    skus: Object.freeze(skus),
  });
}

export function normalizePortalPage(value) {
  const rows = Array.isArray(value?.list) ? value.list.map(normalizePortalProduct).filter(Boolean) : [];
  const total = Number(value?.total);
  return Object.freeze({
    list: Object.freeze(rows),
    total: Number.isSafeInteger(total) && total >= 0 ? total : rows.length,
  });
}

export function portalProductListPath({ tenantSlug, keyword, categoryId } = {}) {
  const params = new URLSearchParams({ portal: '1' });
  if (tenantSlug) params.set('tenantSlug', normalizePortalTenantSlug(tenantSlug));
  if (keyword) params.set('keyword', String(keyword).trim().slice(0, 100));
  const category = positiveId(categoryId);
  if (category) params.set('categoryId', category);
  return `/pages/goods/list?${params.toString()}`;
}

export function portalProductDetailPath({ tenantSlug, spuId } = {}) {
  const id = normalizePortalSpuId(spuId);
  const params = new URLSearchParams({ portal: '1', id });
  if (tenantSlug) params.set('tenantSlug', normalizePortalTenantSlug(tenantSlug));
  return `/pages/goods/index?${params.toString()}`;
}

export const isRetiredPublicTenant = (tenantId) => String(tenantId ?? '').trim() === RETIRED_PUBLIC_TENANT_ID;
