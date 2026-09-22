import { request } from './http'
import type { Identifier, PageResult } from '@/types/mall'
import {
  sanitizeInquiryMessages,
  sanitizeInquiryQuote,
  sanitizePublicInquiry,
  sanitizePublicInquiryPage,
} from '@/utils/inquiry-market'

export interface InquiryCapabilities {
  canQuote: boolean
  canEdit: boolean
  canSubmit: boolean
  canWithdraw: boolean
  canManage: boolean
  canViewQuotes: boolean
  canMessage: boolean
  canAccept: boolean
  canConvert: boolean
  canCheckout: boolean
  reason?: string
}

export interface PublicInquiryItem {
  id?: number
  skuId?: number
  spuId?: number
  picUrl?: string
  code: string
  name: string
  category: string
  specification: string
  quantity: number
  unit: string
  remark: string
}

export interface PublicInquiry {
  id: number
  no?: string
  title: string
  itemSummary: string
  remark: string
  deadline?: string | number
  expectedDate?: string | number
  status: number
  enterpriseName?: string
  addressSummary?: string
  itemCount: number
  quoteCount: number
  createTime?: string | number
  updateTime?: string | number
  capabilities: InquiryCapabilities
  items: PublicInquiryItem[]
}

export interface InquiryQuoteItem {
  id?: number
  inquiryItemId?: number
  skuId?: number
  name: string
  specification: string
  productName: string
  skuName: string
  skuSpecification: string
  quantity: number
  orderCount: number
  unit: string
  unitPrice: number
  amount: number
  remark: string
}

export interface InquiryQuote {
  id?: number
  inquiryId: number
  inquiryNo?: string
  inquiryTitle?: string
  shopId?: number
  shopName?: string
  status: number
  version: number
  freightPrice: number
  totalPrice: number
  validUntil?: string | number
  deliveryDate?: string | number
  submittedAt?: string | number
  acceptedAt?: string | number
  remark: string
  createTime?: string | number
  updateTime?: string | number
  capabilities: InquiryCapabilities
  items: InquiryQuoteItem[]
}

export interface InquiryMessage {
  id?: number
  quoteId?: number
  senderSide: string
  senderName?: string
  content: string
  clientMessageId?: string
  createTime?: string | number
}

export interface InquiryQuoteSave {
  id?: number
  inquiryId: number
  shopId: number
  version: number
  requestKey: string
  freightPrice: number
  validUntil: number
  deliveryDate: number
  remark: string
  items: Array<{
    inquiryItemId: number
    skuId: number
    count: number
    unitPrice: number
  }>
}

export interface InquirySettlement {
  quote?: InquiryQuote
  items?: Array<Record<string, unknown>>
  price?: Record<string, number>
  address?: Record<string, unknown>
}

const inquiryPath = '/product/purchase-inquiry'
const quotePath = `${inquiryPath}/quote`

export const inquiryMarketApi = {
  async publicPage(params: Record<string, unknown>): Promise<PageResult<PublicInquiry>> {
    const result = await request<unknown>({ url: `${inquiryPath}/public-page`, params, scope: 'entry' })
    return sanitizePublicInquiryPage(result)
  },
  async publicGet(id: number): Promise<PublicInquiry> {
    const result = await request<unknown>({ url: `${inquiryPath}/public-get`, params: { id }, scope: 'entry' })
    return sanitizePublicInquiry(result)
  },
  async mine(inquiryId: number, shopId: number): Promise<InquiryQuote | null> {
    const result = await request<unknown>({ url: `${quotePath}/mine`, params: { inquiryId, shopId } })
    return result ? sanitizeInquiryQuote(result) : null
  },
  async supplierPage(params: { shopId: number; pageNo?: number; pageSize?: number; status?: number }): Promise<PageResult<InquiryQuote>> {
    const result = await request<{ list?: unknown[]; total?: number }>({ url: `${quotePath}/supplier-page`, params })
    const list = Array.isArray(result?.list) ? result.list.map(sanitizeInquiryQuote) : []
    return { list, total: Number.isSafeInteger(result?.total) ? Number(result.total) : list.length }
  },
  async page(params: { inquiryId?: number; pageNo?: number; pageSize?: number; status?: number }): Promise<PageResult<InquiryQuote>> {
    const result = await request<{ list?: unknown[]; total?: number }>({ url: `${quotePath}/page`, params })
    const list = Array.isArray(result?.list) ? result.list.map(sanitizeInquiryQuote) : []
    return { list, total: Number.isSafeInteger(result?.total) ? Number(result.total) : list.length }
  },
  async get(id: number): Promise<InquiryQuote> {
    const result = await request<unknown>({ url: `${quotePath}/get`, params: { id } })
    return sanitizeInquiryQuote(result)
  },
  async save(data: InquiryQuoteSave): Promise<number> {
    return request<number>({ url: `${quotePath}/save`, method: 'POST', data })
  },
  submit: (id: number, version: number) => request<boolean>({ url: `${quotePath}/submit`, method: 'PUT', params: { id, version } }),
  withdraw: (id: number, version: number) => request<boolean>({ url: `${quotePath}/withdraw`, method: 'PUT', params: { id, version } }),
  accept: (id: number, version: number) => request<boolean>({ url: `${quotePath}/accept`, method: 'PUT', params: { id, version } }),
  async messages(quoteId: number): Promise<InquiryMessage[]> {
    const messages: InquiryMessage[] = []
    let afterId = 0
    for (let page = 0; page < 50; page += 1) {
      const result = await request<unknown>({
        url: `${quotePath}/messages`,
        params: { quoteId, afterId, pageSize: 200 },
        headers: { 'x-silent-error': true },
      })
      const rows = sanitizeInquiryMessages(result)
      messages.push(...rows)
      if (rows.length < 200) return messages
      const nextAfterId = rows.at(-1)?.id
      if (!nextAfterId || nextAfterId <= afterId) throw new Error('报价沟通记录分页游标无效')
      afterId = nextAfterId
    }
    throw new Error('报价沟通记录超过单次安全加载上限，请联系平台归档')
  },
  sendMessage: (data: { quoteId: number; content: string; clientMessageId: string }) =>
    request<Identifier>({ url: `${quotePath}/messages`, method: 'POST', data }),
}

export const inquiryTradeApi = {
  settlement: (quoteId: number, addressId: Identifier) =>
    request<InquirySettlement>({ url: '/trade/order/inquiry-settlement', params: { quoteId, addressId } }),
  create: (data: { quoteId: number; addressId: Identifier; remark: string; requestKey: string }) =>
    request<{ id?: Identifier; payOrderId?: Identifier } | Identifier>({ url: '/trade/order/inquiry-create', method: 'POST', data }),
  order: (quoteId: number) =>
    request<{ id?: Identifier; payOrderId?: Identifier } | null>({ url: '/trade/order/inquiry-order', params: { quoteId }, headers: { 'x-silent-error': true } }),
}
