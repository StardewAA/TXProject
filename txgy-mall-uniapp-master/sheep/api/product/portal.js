import request from '@/sheep/request';
import { normalizePortalSpuId, normalizePortalTenantSlug } from '@/sheep/helper/platform-portal.mjs';

const platformRead = {
  scope: 'platform',
  // 平台公开目录不依赖登录态，也不应把历史会员令牌带到公开读接口。
  isToken: false,
  showLoading: false,
  showError: false,
};

const PortalApi = {
  getProducts(params = {}) {
    return request({
      url: '/mall/portal/products',
      method: 'GET',
      params,
      custom: platformRead,
    });
  },
  getProduct(spuId) {
    const id = normalizePortalSpuId(spuId);
    return request({
      url: `/mall/portal/products/${id}`,
      method: 'GET',
      custom: platformRead,
    });
  },
  getHotTenants(window = '7d', limit = 10) {
    return request({
      url: '/mall/portal/tenants/hot',
      method: 'GET',
      params: { window, limit },
      custom: platformRead,
    });
  },
  getTenant(tenantSlug) {
    const slug = normalizePortalTenantSlug(tenantSlug);
    return request({
      url: `/mall/tenants/${encodeURIComponent(slug)}`,
      method: 'GET',
      custom: platformRead,
    });
  },
  getTenantProducts(tenantSlug, params = {}) {
    const slug = normalizePortalTenantSlug(tenantSlug);
    return request({
      url: `/mall/tenants/${encodeURIComponent(slug)}/products`,
      method: 'GET',
      params,
      custom: platformRead,
    });
  },
  getTenantProduct(tenantSlug, spuId) {
    const slug = normalizePortalTenantSlug(tenantSlug);
    const id = normalizePortalSpuId(spuId);
    return request({
      url: `/mall/tenants/${encodeURIComponent(slug)}/products/${id}`,
      method: 'GET',
      custom: platformRead,
    });
  },
};

export default PortalApi;
