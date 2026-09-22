import { request } from './http'
import type { Identifier, PageResult } from '@/types/mall'

export interface PlatformProductProperty {
  propertyId?: Identifier
  propertyName?: string
  valueId?: Identifier
  valueName?: string
}

export interface PlatformCartItem {
  id: Identifier
  tenantId: Identifier
  enterpriseId: Identifier
  shopId: Identifier
  spuId: Identifier
  spuName?: string
  skuId: Identifier
  properties?: PlatformProductProperty[]
  picUrl?: string
  /** Unit price in cents. */
  price?: number
  quantity: number
  selected: boolean
  valid: boolean
  invalidReason?: string
}

export interface PlatformCartShopGroup {
  enterpriseId: Identifier
  enterpriseName: string
  shopId: Identifier
  shopName: string
  items: PlatformCartItem[]
}

export interface PlatformCartTenantGroup {
  tenantId: Identifier
  tenantName: string
  shops: PlatformCartShopGroup[]
}

export interface PlatformCart {
  itemCount: number
  totalQuantity: number
  selectedQuantity: number
  tenantGroups: PlatformCartTenantGroup[]
}

export interface PlatformCheckoutItem {
  cartItemId: Identifier
  tenantId: Identifier
  enterpriseId: Identifier
  shopId: Identifier
  spuId: Identifier
  spuName?: string
  skuId: Identifier
  properties?: PlatformProductProperty[]
  picUrl?: string
  quantity: number
  unitPrice: number
  goodsAmount: number
}

export interface PlatformCheckoutShopGroup {
  enterpriseId: Identifier
  enterpriseName: string
  shopId: Identifier
  shopName: string
  goodsAmount: number
  items: PlatformCheckoutItem[]
}

export interface PlatformCheckoutTenantGroup {
  tenantId: Identifier
  tenantName: string
  goodsAmount: number
  shops: PlatformCheckoutShopGroup[]
}

export interface PlatformCheckoutPreview {
  platformAccountId: Identifier
  currency: string
  validItemCount: number
  totalQuantity: number
  goodsAmount: number
  freightAmount: number
  discountAmount: number
  payAmount: number
  validationPassed: boolean
  submittable: boolean
  blockingReasons: string[]
  tenantGroups: PlatformCheckoutTenantGroup[]
  failedItems: Array<{ cartItemId: Identifier; skuId: Identifier; reason: string }>
}

export interface PlatformCheckoutDetail {
  checkoutNo: string
  currency: string
  goodsAmount: number
  freightAmount: number
  discountAmount: number
  payAmount: number
  paidAmount: number
  refundedAmount: number
  payStatus: string
  aggregateStatus: string
  failureReason?: string
  remark?: string
  payable: boolean
  planningOnly: boolean
  createTime?: string
  childPlans?: Array<{
    planNo: string
    tenantId: Identifier
    sellerEnterpriseId: Identifier
    shopId: Identifier
    tenantName?: string
    enterpriseName?: string
    shopName?: string
    itemCount: number
    totalQuantity: number
    goodsAmount: number
    freightAmount: number
    discountAmount: number
    payAmount: number
    planStatus: string
    blockReason?: string
    items: PlatformCheckoutItem[]
  }>
}

export interface PlatformCheckoutSummary {
  checkoutNo: string
  currency: string
  goodsAmount: number
  freightAmount: number
  discountAmount: number
  payAmount: number
  paidAmount: number
  refundedAmount: number
  payStatus: string
  aggregateStatus: string
  failureReason?: string
  payable: boolean
  planningOnly: boolean
  createTime?: string
}

export const platformCartApi = {
  getList: () => request<PlatformCart>({ url: '/trade/platform-cart/list', scope: 'platform' }),
  add: (skuId: Identifier, quantity: number) => request<Identifier>({
    url: '/trade/platform-cart/add', method: 'POST', data: { skuId, quantity }, scope: 'platform',
  }),
  updateCount: (id: Identifier, quantity: number) => request<boolean>({
    url: '/trade/platform-cart/update-count', method: 'PUT', data: { id, quantity }, scope: 'platform',
  }),
  updateSelected: (ids: Identifier[], selected: boolean) => request<boolean>({
    url: '/trade/platform-cart/update-selected', method: 'PUT', data: { ids, selected }, scope: 'platform',
  }),
  delete: (ids: Identifier[]) => request<boolean>({
    url: '/trade/platform-cart/delete', method: 'DELETE', params: { ids: ids.join(',') }, scope: 'platform',
  }),
}

export const platformCheckoutApi = {
  preview: (data: { cartItemIds: Identifier[]; addressId?: Identifier }) =>
    request<PlatformCheckoutPreview>({ url: '/trade/platform-checkout/preview', method: 'POST', data, scope: 'platform' }),
  submit: (data: { cartItemIds: Identifier[]; addressId: Identifier; idempotencyKey: string; remark?: string }) =>
    request<PlatformCheckoutDetail>({ url: '/trade/platform-checkout/submit', method: 'POST', data, scope: 'platform' }),
  get: (checkoutNo: string) => request<PlatformCheckoutDetail>({
    url: `/trade/platform-checkout/${encodeURIComponent(checkoutNo)}`, scope: 'platform',
  }),
  page: (params: { pageNo: number; pageSize: number }) => request<PageResult<PlatformCheckoutSummary>>({
    url: '/trade/platform-checkout/page', params, scope: 'platform',
  }),
}
