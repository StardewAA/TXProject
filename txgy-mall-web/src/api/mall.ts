import { request } from './http'
import type {
  Address,
  AfterSale,
  CartItem,
  Category,
  PageResult,
  Product,
  Shop,
  TokenResult,
  TradeOrder,
  UserProfile,
  Wallet,
  WalletPackage,
  WalletTransaction,
  Identifier,
  CouponTemplate,
} from '@/types/mall'

export const authApi = {
  login: (data: { mobile: string; password: string }) =>
    request<TokenResult>({ url: '/member/auth/login', method: 'POST', data }),
  register: (data: { mobile: string; password: string; code: string; nickname: string; agree: true; memberIdentity: 'buyer' | 'supplier' }) =>
    request<TokenResult>({ url: '/member/auth/register', method: 'POST', data }),
  sendSmsCode: (mobile: string, scene: number) =>
    request<boolean>({ url: '/member/auth/send-sms-code', method: 'POST', data: { mobile, scene } }),
  logout: () => request<boolean>({ url: '/member/auth/logout', method: 'POST' }),
}

export const memberApi = {
  getProfile: () => request<UserProfile>({ url: '/member/user/get' }),
  updateProfile: (data: Partial<UserProfile>) =>
    request<boolean>({ url: '/member/user/update', method: 'PUT', data }),
  resetPassword: (data: { mobile: string; code: string; password: string }) =>
    request<boolean>({ url: '/member/user/reset-password', method: 'PUT', data }),
  getAddresses: () => request<Address[]>({ url: '/member/address/list' }),
  getDefaultAddress: () => request<Address | null>({ url: '/member/address/get-default' }),
  createAddress: (data: Omit<Address, 'id'>) =>
    request<Identifier>({ url: '/member/address/create', method: 'POST', data }),
  updateAddress: (data: Address) =>
    request<boolean>({ url: '/member/address/update', method: 'PUT', data }),
  deleteAddress: (id: Identifier) =>
    request<boolean>({ url: '/member/address/delete', method: 'DELETE', params: { id } }),
}

export const infraApi = {
  uploadAvatar: (file: File) => {
    const data = new FormData()
    data.append('file', file)
    data.append('directory', 'member/avatar')
    return request<string>({
      url: '/infra/file/upload',
      method: 'POST',
      data,
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
}

export interface AreaNode {
  id: Identifier
  name: string
  children?: AreaNode[]
}

export const systemApi = {
  getAreaTree: () => request<AreaNode[]>({ url: '/system/area/tree' }),
}

export const productApi = {
  getShops: () => request<Shop[]>({ url: '/product/shop/list' }),
  getCategories: () => request<Category[]>({ url: '/product/category/list' }),
  getPage: (params: Record<string, unknown>) =>
    request<PageResult<Product>>({ url: '/product/spu/page', params }),
  getDetail: (id: Identifier) => request<Product>({ url: '/product/spu/get-detail', params: { id } }),
  getShop: (id: Identifier) => request<Shop>({ url: '/product/shop/get', params: { id } }),
  getComments: (params: Record<string, unknown>) =>
    request<PageResult<Record<string, unknown>>>({ url: '/product/comment/page', params }),
  getFavorites: (params: Record<string, unknown>) =>
    request<PageResult<Product>>({ url: '/product/favorite/page', params }),
  addFavorite: (spuId: Identifier) =>
    request<Identifier>({ url: '/product/favorite/create', method: 'POST', data: { spuId } }),
  deleteFavorite: (spuId: Identifier) =>
    request<boolean>({ url: '/product/favorite/delete', method: 'DELETE', data: { spuId } }),
  getHistory: (params: Record<string, unknown>) =>
    request<PageResult<Product>>({ url: '/product/browse-history/page', params }),
  clearHistory: () => request<boolean>({ url: '/product/browse-history/clean', method: 'DELETE' }),
}

export const promotionApi = {
  getCouponTemplates: () =>
    request<CouponTemplate[]>({ url: '/promotion/coupon-template/list', params: { count: 50 } }),
  takeCoupon: (templateId: Identifier) =>
    request<boolean>({ url: '/promotion/coupon/take', method: 'POST', data: { templateId } }),
}

export const cartApi = {
  getList: () =>
    request<{ validList?: CartItem[]; invalidList?: CartItem[] }>({ url: '/trade/cart/list' }),
  add: (skuId: Identifier, count: number) =>
    request<Identifier>({ url: '/trade/cart/add', method: 'POST', data: { skuId, count } }),
  updateCount: (id: Identifier, count: number) =>
    request<boolean>({ url: '/trade/cart/update-count', method: 'PUT', data: { id, count } }),
  updateSelected: (ids: Identifier[], selected: boolean) =>
    request<boolean>({ url: '/trade/cart/update-selected', method: 'PUT', data: { ids, selected } }),
  delete: (ids: Identifier[]) =>
    request<boolean>({ url: '/trade/cart/delete', method: 'DELETE', params: { ids: ids.join(',') } }),
}

export interface SettlementItem {
  skuId: Identifier
  count: number
  cartId?: Identifier
}

function settlementParams(data: Record<string, unknown> & { items: SettlementItem[] }) {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(data)) {
    if (key === 'items' || value === undefined || value === null || value === '') continue
    params.set(key, String(value))
  }
  data.items.forEach((item, index) => {
    params.set(`items[${index}].skuId`, String(item.skuId))
    params.set(`items[${index}].count`, String(item.count))
    if (item.cartId) params.set(`items[${index}].cartId`, String(item.cartId))
  })
  return params
}

export const tradeApi = {
  getConfig: () => request<Record<string, unknown>>({ url: '/trade/config/get' }),
  settlement: (data: Record<string, unknown> & { items: SettlementItem[] }) =>
    request<Record<string, unknown>>({ url: '/trade/order/settlement', params: settlementParams(data) }),
  createOrder: (data: Record<string, unknown>) =>
    request<{ id?: Identifier; payOrderId?: Identifier } | Identifier>({
      url: '/trade/order/create',
      method: 'POST',
      data,
    }),
  getOrders: (params: Record<string, unknown>) =>
    request<PageResult<TradeOrder>>({ url: '/trade/order/page', params }),
  getOrder: (id: Identifier, sync = false) =>
    request<TradeOrder>({ url: '/trade/order/get-detail', params: { id, sync } }),
  cancelOrder: (id: Identifier) =>
    request<boolean>({ url: '/trade/order/cancel', method: 'DELETE', params: { id } }),
  receiveOrder: (id: Identifier) =>
    request<boolean>({ url: '/trade/order/receive', method: 'PUT', params: { id } }),
  deleteOrder: (id: Identifier) =>
    request<boolean>({ url: '/trade/order/delete', method: 'DELETE', params: { id } }),
  createComment: (data: Record<string, unknown>) =>
    request<number>({ url: '/trade/order/item/create-comment', method: 'POST', data }),
  getAfterSales: (params: Record<string, unknown>) =>
    request<PageResult<AfterSale>>({ url: '/trade/after-sale/page', params }),
  getAfterSale: (id: Identifier) =>
    request<AfterSale>({ url: '/trade/after-sale/get', params: { id } }),
  createAfterSale: (data: Record<string, unknown>) =>
    request<Identifier>({ url: '/trade/after-sale/create', method: 'POST', data }),
  deliverAfterSale: (data: { id: Identifier; logisticsId: Identifier; logisticsNo: string }) =>
    request<boolean>({ url: '/trade/after-sale/delivery', method: 'PUT', data }),
  cancelAfterSale: (id: Identifier) =>
    request<boolean>({ url: '/trade/after-sale/cancel', method: 'DELETE', params: { id } }),
  getDeliveryExpresses: () =>
    request<Array<{ id: Identifier; name: string }>>({ url: '/trade/delivery/express/list' }),
}

export const payApi = {
  getChannels: (appId: Identifier) =>
    request<string[]>({ url: '/pay/channel/get-enable-code-list', params: { appId } }),
  getOrder: (params: { id?: Identifier; no?: string; sync?: boolean }) =>
    request<Record<string, unknown>>({ url: '/pay/order/get', params }),
  submit: (data: Record<string, unknown>) =>
    request<Record<string, unknown>>({ url: '/pay/order/submit', method: 'POST', data }),
}

export const walletApi = {
  get: () => request<Wallet>({ url: '/pay/wallet/get' }),
  getPackages: () => request<WalletPackage[]>({ url: '/pay/wallet-recharge-package/list' }),
  getTransactions: (params: Record<string, unknown>) =>
    request<PageResult<WalletTransaction>>({ url: '/pay/wallet-transaction/page', params }),
  createRecharge: (data: { payPrice?: number; packageId?: Identifier }) =>
    request<{ id: Identifier; payOrderId: Identifier }>({ url: '/pay/wallet-recharge/create', method: 'POST', data }),
}

export interface CustomerMessage {
  id: Identifier
  conversationId: Identifier
  senderType: number
  contentType: number
  content: string
  createTime?: string | number
  readStatus?: boolean
}

export const customerServiceApi = {
  getMessages: (params: Record<string, unknown>) =>
    request<CustomerMessage[]>({ url: '/promotion/kefu-message/list', params, headers: { 'x-silent-error': true } }),
  sendMessage: (content: string) =>
    request<Identifier>({ url: '/promotion/kefu-message/send', method: 'POST', data: { contentType: 1, content: JSON.stringify({ text: content }) } }),
  markRead: (conversationId: Identifier) =>
    request<boolean>({ url: '/promotion/kefu-message/update-read-status', method: 'PUT', params: { conversationId }, headers: { 'x-silent-error': true } }),
}
