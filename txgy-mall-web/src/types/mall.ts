import type { PaymentDeadlineFields } from '@/utils/payment-deadline'
import type { AddressLocation } from './fulfillment'

export interface PageResult<T> {
  list: T[]
  total: number
}

export type Identifier = string | number

export interface TokenResult {
  tenantId?: Identifier
  userId: Identifier
  accessToken: string
  refreshToken: string
  expiresTime: string
}

export interface UserProfile {
  tenantId?: Identifier
  memberIdentity?: 'buyer' | 'supplier'
  id?: Identifier
  nickname?: string
  mobile?: string
  loginName?: string
  contactMobile?: string
  avatar?: string
  gender?: number
  point?: number
}

export interface Wallet {
  balance: number
  totalExpense: number
  totalRecharge: number
}

export interface WalletPackage {
  id: Identifier
  name: string
  payPrice: number
  bonusPrice: number
}

export interface WalletTransaction {
  bizType: number
  price: number
  title: string
  createTime?: string | number
}

export interface Category {
  id: Identifier
  parentId?: Identifier
  name: string
  picUrl?: string
  sort?: number
  children?: Category[]
}

export interface ProductSku {
  id: Identifier
  price: number
  marketPrice?: number
  stock: number
  picUrl?: string
  properties?: Array<{
    propertyId?: Identifier
    propertyName?: string
    valueId?: Identifier
    valueName?: string
  }>
}

export interface Product {
  id: Identifier
  name: string
  introduction?: string
  description?: string
  picUrl?: string
  sliderPicUrls?: string[]
  price: number
  marketPrice?: number
  stock?: number
  salesCount?: number
  categoryId?: Identifier
  shopId?: Identifier
  shopName?: string
  skus?: ProductSku[]
  favorite?: boolean
}

/** 收藏/足迹是关系记录，id不是商品编号；失效商品不返回价格等经营内容。 */
export interface ProductCollectionItem {
  id: Identifier
  spuId?: Identifier
  spuName?: string
  name?: string
  picUrl?: string
  price?: number | null
  stock?: number | null
  salesCount?: number | null
  introduction?: string
  shopName?: string
  saleable?: boolean | null
  unavailableReason?: string | null
}

export interface Shop {
  id: Identifier
  name: string
  logo?: string
  logoUrl?: string
  introduction?: string
  contactName?: string
  contactMobile?: string
}

export interface CouponTemplate {
  id: Identifier
  name: string
  description?: string
  usePrice?: number
  discountType: number
  discountPrice?: number
  discountPercent?: number
  discountLimitPrice?: number
  validityType?: number
  validStartTime?: string
  validEndTime?: string
  fixedStartTerm?: number
  fixedEndTerm?: number
  canTake?: boolean
}

export interface CartItem {
  id: Identifier
  count: number
  selected: boolean
  spu?: Product
  sku: ProductSku & { spuId?: Identifier; name?: string }
}

export interface Address extends AddressLocation {
  id: Identifier
  name: string
  mobile: string
  areaId?: Identifier
  areaName?: string
  detailAddress: string
  defaultStatus?: boolean
}

export interface OrderItem {
  shopId?: Identifier
  id: Identifier
  spuId?: Identifier
  skuId?: Identifier
  spuName?: string
  picUrl?: string
  properties?: Array<{ propertyName?: string; valueName?: string }>
  count: number
  price: number
  payPrice?: number
  commentStatus?: boolean
  afterSaleStatus?: number
  latestAfterSaleId?: Identifier
  latestAfterSaleStatus?: number
  latestAfterSaleReason?: string
}

  export interface TradeOrder extends PaymentDeadlineFields {
    sourceType?: string
    inquiryId?: Identifier
    inquiryQuoteId?: Identifier
    inquiryQuoteVersion?: number
    buyerEnterpriseId?: Identifier
    createdByCurrentMember?: boolean
  shopId?: Identifier
  deliveryType?: number
  deliveryPrice?: number
  receiverLongitude?: AddressLocation['longitude']
  receiverLatitude?: AddressLocation['latitude']
  receiverCoordSystem?: AddressLocation['coordSystem']
  receiverLocationSource?: AddressLocation['locationSource']
  receiverLocationPrecision?: AddressLocation['locationPrecision']
  receiverLocationConfirmedAt?: AddressLocation['locationConfirmedAt']
  commentStatus?: boolean
  erpInventory?: import('@/components/ErpInventoryStatus.vue').ErpEntry[]
  id: Identifier
  no?: string
  status: number
  statusName?: string
  payStatus?: boolean
  payOrderId?: Identifier
  payPrice: number
  createTime?: string | number
  items?: OrderItem[]
  receiverName?: string
  receiverMobile?: string
  receiverAreaName?: string
  receiverDetailAddress?: string
  dispatchOriginAddress?: string
}

export interface AfterSale {
  createdByCurrentMember?: boolean
  shopId?: Identifier
  orderId?: Identifier
  returnReceiverName?: string
  returnReceiverMobile?: string
  returnAddress?: string
  auditReason?: string
  receiveReason?: string
  id: Identifier
  no?: string
  status: number
  statusName?: string
  type?: number
  reason?: string
  applyReason?: string
  applyDescription?: string
  refundPrice?: number
  createTime?: string | number
  spuName?: string
  picUrl?: string
  logisticsId?: Identifier
  logisticsNo?: string
}
