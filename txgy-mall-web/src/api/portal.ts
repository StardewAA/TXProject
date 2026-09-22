import { request } from './http'
import type { Identifier, PageResult } from '@/types/mall'

export interface PortalSku {
  skuId: Identifier
  skuName?: string
  properties?: Array<{ propertyName?: string; valueName?: string }>
  picUrl?: string
  price: number
}

export interface PortalProduct {
  tenantId: Identifier
  tenantSlug: string
  tenantName: string
  tenantLogo?: string
  enterpriseId: Identifier
  enterpriseName: string
  shopId: Identifier
  shopName: string
  spuId: Identifier
  spuName: string
  introduction?: string
  description?: string
  picUrl?: string
  categoryId?: Identifier
  price: number
  salesCount?: number
  saleable: boolean
  skus?: PortalSku[]
}

export interface PortalTenant {
  tenantId: Identifier
  tenantSlug: string
  tenantName: string
  tenantLogo?: string
  mainCategories?: string
}

export interface PortalHotTenant extends PortalTenant {
  rank: number
  validQuantity: number
}

export const portalApi = {
  getProducts: (params: { pageNo: number; pageSize: number; keyword?: string; categoryId?: Identifier; sortField?: 'price' | 'sales'; sortAsc?: boolean }) =>
    request<PageResult<PortalProduct>>({ url: '/mall/portal/products', params, scope: 'entry' }),
  getHotTenants: (window: '7d' | '30d', limit = 12) =>
    request<PortalHotTenant[]>({ url: '/mall/portal/tenants/hot', params: { window, limit }, scope: 'entry' }),
  getTenant: (tenantSlug: string) =>
    request<PortalTenant>({ url: `/mall/tenants/${encodeURIComponent(tenantSlug)}`, scope: 'entry' }),
  getTenantProducts: (tenantSlug: string, params: { pageNo: number; pageSize: number; keyword?: string; categoryId?: Identifier; sortField?: 'price' | 'sales'; sortAsc?: boolean }) =>
    request<PageResult<PortalProduct>>({ url: `/mall/tenants/${encodeURIComponent(tenantSlug)}/products`, params, scope: 'entry' }),
  getProduct: (spuId: Identifier) =>
    request<PortalProduct>({ url: `/mall/portal/products/${encodeURIComponent(String(spuId))}`, scope: 'entry' }),
  getTenantProduct: (tenantSlug: string, spuId: Identifier) =>
    request<PortalProduct>({ url: `/mall/tenants/${encodeURIComponent(tenantSlug)}/products/${encodeURIComponent(String(spuId))}`, scope: 'entry' }),
}

export const platformAccountApi = {
  getSession: () => request<{ platformAccountId: Identifier; platformNo: string; canonicalMemberId: Identifier; legacyMemberId: Identifier; sourceTenantId: Identifier; memberships: Array<{ tenantId: Identifier; enterpriseId?: Identifier; memberType: string }> }>({
    url: '/member/platform-account/session', scope: 'platform',
  }),
}
