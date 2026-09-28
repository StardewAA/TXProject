// Isolated public browser. Never reads, writes or refreshes account tokens.
const positiveId = value => {
  if (typeof value !== 'number' && typeof value !== 'string') throw new Error('商城或资源编号无效');
  const text = String(value ?? '');
  if (!/^[1-9]\d*$/.test(text) || !Number.isSafeInteger(Number(text))) throw new Error('商城或资源编号无效');
  return text;
};
export function parsePublicMarkets(value) {
  if (!value || typeof value.enabled !== 'boolean') throw new Error('商城目录暂不可用');
  if (!value.enabled || value.capabilities?.browsePreview !== true) return { enabled: false, tenants: [], publicTenantId: null };
  if (!Array.isArray(value.tenants)) throw new Error('商城目录无效');
  const seen = new Set(), publicTenantId = positiveId(value.publicTenantId);
  const tenants = value.tenants.map(item => {
    const tenantId = positiveId(item?.tenantId);
    if (seen.has(tenantId) || typeof item?.name !== 'string' || !item.name.trim()) throw new Error('商城目录无效');
    seen.add(tenantId);
    return Object.freeze({ tenantId, name: item.name.trim() });
  });
  if (!seen.has(publicTenantId)) throw new Error('公共商城暂不可用');
  return { enabled: true, publicTenantId, tenants: Object.freeze(tenants) };
}
const paths = Object.freeze({
  shops: '/product/shop/list', categories: '/product/category/list',
  products: '/product/spu/page', detail: '/product/spu/get-detail',
});
export function publicMarketRequest(tenant, kind, input = {}) {
  const tenantId = positiveId(tenant);
  if (!Object.hasOwn(paths, kind)) throw new Error('只允许浏览公开商品和店铺');
  const params = {};
  if (kind === 'detail') params.id = positiveId(input.id);
  if (kind === 'products') {
    const page = Number(input.pageNo ?? 1);
    if (!Number.isSafeInteger(page) || page < 1) throw new Error('页码无效');
    params.pageNo = page; params.pageSize = 12;
    for (const key of ['shopId', 'categoryId']) if (input[key]) params[key] = positiveId(input[key]);
    if (input.keyword) params.keyword = String(input.keyword).trim().slice(0, 100);
  }
  return { path: paths[kind], params, headers: { 'tenant-id': tenantId, terminal: '20', Accept: 'application/json' } };
}
export function createPublicMarketClient(base, fetcher = globalThis.fetch) {
  async function get(path, headers = {}, params = {}) {
    const query = new URLSearchParams(Object.entries(params).map(([key, value]) => [key, String(value)])).toString();
    const controller = new AbortController(), timer = setTimeout(() => controller.abort(), 12000);
    try {
      const response = await fetcher(base.replace(/\/$/, '') + path + (query ? '?' + query : ''), {
        method: 'GET', headers, credentials: 'omit', cache: 'no-store', redirect: 'error', signal: controller.signal,
      });
      if (!response.ok) throw new Error('商城服务暂不可用，请重试');
      const envelope = await response.json();
      if (envelope?.code !== 0) throw new Error('商城或商品当前不可用，请重新查询');
      return envelope.data;
    } catch (error) {
      if (error?.name === 'AbortError') throw new Error('查询超时，请重试');
      throw error;
    } finally { clearTimeout(timer); }
  }
  const directory = async () => parsePublicMarkets(await get('/member/mall-tenant/config'));
  return {
    directory,
    async read(tenant, kind, params = {}) {
      const request = publicMarketRequest(tenant, kind, params);
      // Revalidate on every query. A revoked entry must not use a previously cached directory.
      const catalog = await directory();
      if (!catalog.enabled || !catalog.tenants.some(item => item.tenantId === request.headers['tenant-id'])) throw new Error('所选商城未开放或已停用');
      return get(request.path, request.headers, request.params);
    },
  };
}
export function publicMarketState() {
  return { directory: [], tenantId: '', name: '', shops: [], categories: [], products: [], total: 0,
    pageNo: 1, keyword: '', shopId: '', categoryId: '', loading: false, error: '', detail: null,
    detailLoading: false, detailError: '', enabled: false };
}
const validRows = rows => Array.isArray(rows) && rows.every(row => {
  if (!row || typeof row.name !== 'string' || !row.name.trim()) return false;
  try { positiveId(row.id); return true; } catch { return false; }
});
export function createPublicMarketBrowser(state, client) {
  let generation = 0, detailVersion = 0, alive = true;
  const message = error => error instanceof Error ? error.message : '查询失败，请重试';
  function closeDetail() { detailVersion++; state.detail = null; state.detailLoading = false; state.detailError = ''; }
  function resetRows() { state.products = []; state.total = 0; closeDetail(); }
  async function open(tenant = '') {
    if (!alive) return;
    const version = ++generation;
    resetRows(); state.shops = []; state.categories = []; state.name = ''; state.tenantId = '';
    state.enabled = false; state.directory = []; state.error = ''; state.loading = true;
    state.pageNo = 1; state.keyword = ''; state.shopId = ''; state.categoryId = '';
    try {
      const catalog = await client.directory();
      if (!alive || version !== generation) return;
      if (!catalog.enabled) throw new Error('其他商城浏览尚未开放，当前商城功能不受影响');
      state.directory = catalog.tenants; state.enabled = true;
      const selected = catalog.tenants.find(item => item.tenantId === (tenant || catalog.publicTenantId));
      if (!selected) throw new Error('所选商城不在公开名单中');
      state.tenantId = selected.tenantId; state.name = selected.name;
      const [shops, categories, page] = await Promise.all([
        client.read(selected.tenantId, 'shops'), client.read(selected.tenantId, 'categories'),
        client.read(selected.tenantId, 'products', { pageNo: 1 }),
      ]);
      if (!alive || version !== generation) return;
      if (!validRows(shops) || !validRows(categories) || !validRows(page?.list) || !Number.isSafeInteger(page.total) || page.total < 0) throw new Error('商城返回的数据格式无效');
      state.shops = shops; state.categories = categories; state.products = page.list; state.total = page.total;
    } catch (error) { if (alive && version === generation) { resetRows(); state.error = message(error); } }
    finally { if (alive && version === generation) state.loading = false; }
  }
  async function search(pageNo = 1) {
    if (!alive || !state.tenantId) return;
    const version = ++generation, tenant = state.tenantId;
    resetRows(); state.loading = true; state.error = ''; state.pageNo = pageNo;
    try {
      const page = await client.read(tenant, 'products', { pageNo, keyword: state.keyword, shopId: state.shopId, categoryId: state.categoryId });
      if (!alive || version !== generation) return;
      if (!validRows(page?.list) || !Number.isSafeInteger(page.total) || page.total < 0) throw new Error('商城返回的数据格式无效');
      state.products = page.list; state.total = page.total;
    } catch (error) { if (alive && version === generation) state.error = message(error); }
    finally { if (alive && version === generation) state.loading = false; }
  }
  async function detail(id) {
    closeDetail();
    if (!alive || !state.tenantId) return;
    const version = ++detailVersion, context = generation, tenant = state.tenantId;
    state.detailLoading = true;
    try {
      const product = await client.read(tenant, 'detail', { id });
      if (!alive || version !== detailVersion || context !== generation) return;
      if (!validRows([product]) || String(product.id) !== String(id)
          || (product.skus != null && (!Array.isArray(product.skus) || product.skus.some(sku => !sku || (sku.properties != null && (!Array.isArray(sku.properties) || sku.properties.some(p => !p))))))) throw new Error('商品详情返回不匹配');
      state.detail = product;
    } catch (error) { if (alive && version === detailVersion && context === generation) state.detailError = message(error); }
    finally { if (alive && version === detailVersion && context === generation) state.detailLoading = false; }
  }
  return { open, search, detail, closeDetail, dispose() { alive = false; generation++; resetRows(); state.loading = false; } };
}
export const publicPrice = value => Number.isSafeInteger(value) && value >= 0 ? '¥' + (value / 100).toFixed(2) : '价格待确认';
