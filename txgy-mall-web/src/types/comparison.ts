export interface ComparisonList { version: string | number; skuIds: Array<string | number>; quantity: number; preference: ComparisonPreference }
export type ComparisonPreference = 'PRICE' | 'DELIVERY' | 'BALANCED'
export interface ComparisonInput { skuIds: string[]; quantity: number; preference: ComparisonPreference }
export interface ComparisonItem {
  skuId: string | number; available: boolean; unavailableReason?: string; spuId?: string | number; spuName?: string
  picUrl?: string; shopId?: string | number; shopName?: string; price?: number | null
  properties: Array<{ name: string; value: string }>; standardStatus: string; standardMessage: string
  stockStatus: string; stockMessage: string; unknowns: string[]; matchedRequirements: string[]; differences: string[]
}
export interface ComparisonResult {
  quantity: number; preference: ComparisonPreference; quantityUnit: 'SALE_UNIT'; calculatedAt: number; strategyVersion: string
  recommendationStatus: 'INSUFFICIENT_STANDARD_DATA' | 'NEED_MORE_ITEMS' | 'NEED_DIFFERENT_SHOPS' | 'NO_ELIGIBLE_ITEMS'
  recommendationReason: string; recommendedSkuId: null; items: ComparisonItem[]; warnings: string[]
}
export interface ComparisonQuoteItem {
  skuId: string | number; status: string; message: string; quantity: number; amountsComplete: boolean
  unitPrice?: number | null; goodsAmount?: number | null; discountAmount?: number | null; shippingFee?: number | null; totalAmount?: number | null
}
export interface ComparisonQuote { quantity: number; addressId?: string | number; calculatedAt: number; warnings: string[]; priceBasis: 'CHECKOUT_ESTIMATE'; items: ComparisonQuoteItem[] }
