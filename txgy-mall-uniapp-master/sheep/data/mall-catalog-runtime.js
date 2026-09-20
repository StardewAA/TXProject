/**
 * 商城商品档案运行时适配。
 *
 * 按受控商城入口缓存真实接口结果。空商城保持空态，失败不回填旧租户商品。
 */
import CategoryApi from '@/sheep/api/product/category';
import SpuApi from '@/sheep/api/product/spu';
import { mallCategories, mallProducts } from './mall-catalog';
import { ensureTenantReady, getControlledTenantId } from '@/sheep/helper/tenant-runtime';

const catalogCaches = new Map();
const catalogPromises = new Map();

const categoryByApiId = new Map(
  [
    ['primer', 900002],
    ['intermediate', 900003],
    ['finish', 900004],
    ['adhesive', 900015],
    ['fireproof', 900005],
    ['insulation', 900006],
    ['cold-insulation', 900007],
    ['moisture-proof', 900008],
    ['outer-protection', 900009],
    ['pipe-support', 900010],
    ['construction', 900011],
    ['safety-tools', 900012],
  ].map(([key, id]) => [id, key]),
);

const categoryByName = new Map(mallCategories.map((category) => [category.name, category.key]));
const categoryColors = ['#2563eb', '#0f766e', '#7c3aed', '#c2410c', '#dc2626', '#ca8a04', '#0284c7', '#475569'];

function toYuan(value, fallback = 0) {
  const number = Number(value);
  return Number.isFinite(number) && number >= 0 ? number / 100 : fallback;
}

function extractCode(spu) {
  const keyword = String(spu?.keyword || '').trim();
  if (/^CP-\d+$/i.test(keyword)) {
    return keyword.toUpperCase();
  }
  const introduction = String(spu?.introduction || '');
  return introduction.match(/CP-\d+/i)?.[0]?.toUpperCase() || '';
}

function findLocalProduct(spu) {
  const code = extractCode(spu);
  return (
    mallProducts.find((product) => product.code === code) ||
    mallProducts.find((product) => product.name === spu?.name)
  );
}

function resolveCategoryKey(spu, localProduct) {
  if (localProduct?.categoryKey) {
    return localProduct.categoryKey;
  }
  return categoryByApiId.get(Number(spu?.categoryId)) || '';
}

function normalizeProduct(spu, tenant, categories) {
  // The bundled ERP metadata belongs to the historical tenant 1 only.
  const localProduct = tenant === '1' ? findLocalProduct(spu) || {} : {};
  const categoryKey = categories.find(c => Number(c.apiId) === Number(spu.categoryId))?.key
    || (tenant === '1' ? resolveCategoryKey(spu, localProduct) : '');
  return {
    ...localProduct,
    id: spu.id,
    code: extractCode(spu) || localProduct.code || `SPU-${spu.id}`,
    name: spu.name || localProduct.name || '未命名商品',
    categoryKey,
    categoryId: spu.categoryId,
    unit: localProduct.unit || '件',
    spec: localProduct.spec || spu.introduction || '标准规格',
    price: toYuan(spu.price, localProduct.price || 0),
    marketPrice: toYuan(spu.marketPrice, localProduct.price || 0),
    stock: Number(spu.stock ?? 0),
    picUrl: spu.picUrl || localProduct.picUrl || '',
    sliderPicUrls: spu.sliderPicUrls || [],
    shopId: spu.shopId,
    shopName: spu.shopName || '平台自营',
    deliveryTypes: spu.deliveryTypes || [],
    source: 'api',
  };
}

function normalizeApiCategories(categories = [], tenant, products = []) {
  const apiById = new Map(categories.map((item) => [Number(item.id), item]));
  const productCountByCategory = new Map();
  products.forEach((product) => {
    const categoryId = Number(product?.categoryId);
    if (Number.isFinite(categoryId) && categoryId > 0) {
      productCountByCategory.set(categoryId, (productCountByCategory.get(categoryId) || 0) + 1);
    }
  });

  // 分类页只展示当前商城实际有商品的末级分类。不同租户可以维护完全不同的
  // 分类名称和编号，不能再用平台旧分类名称做硬匹配。
  return [...productCountByCategory.entries()].map(([apiId, count], index) => {
    const apiCategory = apiById.get(apiId);
    const legacyKey = tenant === '1' ? categoryByApiId.get(apiId) : '';
    const exactKey = categoryByName.get(apiCategory?.name);
    const key = legacyKey || exactKey || `category-${apiId}`;
    const presentation = mallCategories.find((category) => category.key === key);
    const name = apiCategory?.name || presentation?.name || '其他材料';
    const parent = apiById.get(Number(apiCategory?.parentId));
    return {
      ...presentation,
      key,
      apiId,
      name,
      group: parent?.name || presentation?.group || '工程材料',
      icon: presentation?.icon || name.slice(0, 2),
      color: presentation?.color || categoryColors[index % categoryColors.length],
      hint: presentation?.hint || `${count} 款在售商品`,
    };
  });
}

/**
 * 加载商城用户端商品档案。
 * @param {boolean} force 是否忽略缓存重新请求
 * @returns {Promise<{products: Array, categories: Array, source: 'api'}>}
 */
export async function loadMallCatalog(force = false) {
  await ensureTenantReady();
  const tenant = getControlledTenantId();
  if (catalogCaches.has(tenant) && !force) return catalogCaches.get(tenant);
  if (catalogPromises.has(tenant) && !force) return catalogPromises.get(tenant);

  const pending = Promise.allSettled([
    SpuApi.getSpuPage({ pageNo: 1, pageSize: 100 }),
    CategoryApi.getCategoryList(),
  ])
    .then(([spuResult, categoryResult]) => {
      const spuResponse = spuResult.status === 'fulfilled' ? spuResult.value : null;
      const categoryResponse = categoryResult.status === 'fulfilled' ? categoryResult.value : null;
      if (getControlledTenantId() !== tenant) throw new Error('商城已变化，请刷新商品');
      if (spuResponse?.code !== 0 || !Array.isArray(spuResponse.data?.list)) throw new Error('商品暂无法加载');
      const apiProducts = spuResponse?.code === 0 && Array.isArray(spuResponse.data?.list)
        ? spuResponse.data.list.filter((item) => item?.id)
        : [];
      const apiCategories = categoryResponse?.code === 0 && Array.isArray(categoryResponse.data)
        ? categoryResponse.data
        : [];

      // 按接口顺序展示，便于后台新增商品后用户端立即可见；ERP 元数据只负责
      // 分类、单位和规格等展示补充，不覆盖接口价格、库存和图片。
      const categories = normalizeApiCategories(apiCategories, tenant, apiProducts);
      const catalog = {
        products: apiProducts.map(spu => normalizeProduct(spu, tenant, categories)),
        categories,
        source: 'api',
      };
      if (catalogPromises.get(tenant) === pending) catalogCaches.set(tenant, catalog);
      return catalog;
    })
    .finally(() => {
      if (catalogPromises.get(tenant) === pending) catalogPromises.delete(tenant);
    });

  catalogPromises.set(tenant, pending);
  return pending;
}

export function getCatalogCategoryKeyByName(name) {
  return categoryByName.get(name) || '';
}
