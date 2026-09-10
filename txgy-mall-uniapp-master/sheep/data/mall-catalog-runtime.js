/**
 * 商城商品档案运行时适配。
 *
 * mall-catalog.js 保存 ERP 展示元数据；本模块从商城 APP 接口读取租户 1
 * 的真实 SPU，并将接口字段合并到 ERP 元数据上。接口暂不可用时返回本地
 * 档案，保证首页和分类页仍可打开，待接口恢复后可通过 force 刷新。
 */
import CategoryApi from '@/sheep/api/product/category';
import SpuApi from '@/sheep/api/product/spu';
import { mallCategories, mallProducts } from './mall-catalog';

let catalogCache = null;
let catalogPromise = null;

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

function normalizeProduct(spu) {
  const localProduct = findLocalProduct(spu) || {};
  const categoryKey = resolveCategoryKey(spu, localProduct);
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

function normalizeFallbackProducts() {
  return mallProducts.map((product) => ({
    ...product,
    id: product.id || 0,
    stock: Number(product.stock ?? 0),
    source: 'local',
  }));
}

function normalizeApiCategories(categories = []) {
  const apiNameById = new Map(categories.map((item) => [Number(item.id), item.name]));
  return mallCategories.map((category) => ({
    ...category,
    apiId:
      [...categoryByApiId.entries()].find(([, key]) => key === category.key)?.[0] ||
      [...apiNameById.entries()].find(([, name]) => name === category.name)?.[0] ||
      null,
  }));
}

/**
 * 加载商城用户端商品档案。
 * @param {boolean} force 是否忽略缓存重新请求
 * @returns {Promise<{products: Array, categories: Array, source: 'api'|'local'}>}
 */
export async function loadMallCatalog(force = false) {
  if (catalogCache && !force) {
    return catalogCache;
  }
  if (catalogPromise && !force) {
    return catalogPromise;
  }

  catalogPromise = Promise.allSettled([
    SpuApi.getSpuPage({ pageNo: 1, pageSize: 100 }),
    CategoryApi.getCategoryList(),
  ])
    .then(([spuResult, categoryResult]) => {
      const spuResponse = spuResult.status === 'fulfilled' ? spuResult.value : null;
      const categoryResponse = categoryResult.status === 'fulfilled' ? categoryResult.value : null;
      const apiProducts = spuResponse?.code === 0 && Array.isArray(spuResponse.data?.list)
        ? spuResponse.data.list.filter((item) => item?.id)
        : [];
      const apiCategories = categoryResponse?.code === 0 && Array.isArray(categoryResponse.data)
        ? categoryResponse.data
        : [];

      if (apiProducts.length === 0) {
        catalogCache = {
          products: normalizeFallbackProducts(),
          categories: normalizeApiCategories(apiCategories),
          source: 'local',
        };
        return catalogCache;
      }

      // 按接口顺序展示，便于后台新增商品后用户端立即可见；ERP 元数据只负责
      // 分类、单位和规格等展示补充，不覆盖接口价格、库存和图片。
      catalogCache = {
        products: apiProducts.map(normalizeProduct),
        categories: normalizeApiCategories(apiCategories),
        source: 'api',
      };
      return catalogCache;
    })
    .catch(() => {
      catalogCache = {
        products: normalizeFallbackProducts(),
        categories: normalizeApiCategories(),
        source: 'local',
      };
      return catalogCache;
    })
    .finally(() => {
      catalogPromise = null;
    });

  return catalogPromise;
}

export function getCatalogCategoryKeyByName(name) {
  return categoryByName.get(name) || '';
}
