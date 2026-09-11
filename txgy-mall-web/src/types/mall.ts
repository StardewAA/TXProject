export interface PageResult<T> {
  list: T[]
  total: number
}

export type Identifier = string | number

export interface TokenResult {
  userId: Identifier
  accessToken: string
  refreshToken: string
  expiresTime: string
}

export interface UserProfile {
  id?: Identifier
  nickname?: string
  mobile?: string
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

export interface Address {
  id: Identifier
  name: string
  mobile: string
  areaId?: Identifier
  areaName?: string
  detailAddress: string
  defaultStatus?: boolean
}

export interface OrderItem {
  id: Identifier
  spuId?: Identifier
  skuId?: Identifier
  spuName?: string
  picUrl?: string
  properties?: Array<{ propertyName?: string; valueName?: string }>
  count: number
  price: number
  payPrice?: number
  afterSaleStatus?: number
}

export interface TradeOrder {
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
}

export interface AfterSale {
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
