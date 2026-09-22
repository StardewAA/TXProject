import type {
  InquiryCapabilities,
  InquiryMessage,
  InquiryQuote,
  InquiryQuoteItem,
  PublicInquiry,
  PublicInquiryItem,
} from '@/api/inquiry-market'

type UnknownRecord = Record<string, unknown>

function record(value: unknown): UnknownRecord {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as UnknownRecord : {}
}

function text(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined
}

function integer(value: unknown): number | undefined {
  const parsed = typeof value === 'number' ? value : typeof value === 'string' && /^-?\d+$/.test(value) ? Number(value) : NaN
  return Number.isSafeInteger(parsed) ? parsed : undefined
}

function positiveInteger(value: unknown): number | undefined {
  const parsed = integer(value)
  return parsed !== undefined && parsed > 0 ? parsed : undefined
}

function nonNegativeInteger(value: unknown): number | undefined {
  const parsed = integer(value)
  return parsed !== undefined && parsed >= 0 ? parsed : undefined
}

function positiveQuantity(value: unknown): number | undefined {
  const parsed = typeof value === 'number' ? value : typeof value === 'string' && value.trim() ? Number(value) : NaN
  if (!Number.isFinite(parsed) || parsed <= 0) return undefined
  const rounded = Math.round(parsed * 1000) / 1000
  return Math.abs(parsed - rounded) < Number.EPSILON * Math.max(1, Math.abs(parsed)) ? rounded : undefined
}

function boolean(value: unknown): boolean | undefined {
  return typeof value === 'boolean' ? value : undefined
}

function timestamp(value: unknown): string | number | undefined {
  return typeof value === 'string' || typeof value === 'number' ? value : undefined
}

function publicItem(value: unknown): PublicInquiryItem {
  const source = record(value)
  return {
    ...(positiveInteger(source.id) ? { id: positiveInteger(source.id) } : {}),
    ...(positiveInteger(source.skuId) ? { skuId: positiveInteger(source.skuId) } : {}),
    ...(positiveInteger(source.spuId) ? { spuId: positiveInteger(source.spuId) } : {}),
    ...(text(source.picUrl) ? { picUrl: text(source.picUrl) } : {}),
    code: text(source.code) || '',
    name: text(source.name) || '未命名物料',
    category: text(source.category) || '',
    specification: text(source.specification) || '',
    quantity: positiveQuantity(source.quantity) || 1,
    unit: text(source.unit) || '',
    remark: text(source.remark) || '',
  }
}

function capabilities(value: unknown): InquiryCapabilities {
  const source = record(value)
  return {
    canQuote: boolean(source.canQuote) || false,
    canEdit: boolean(source.canEdit) || false,
    canSubmit: boolean(source.canSubmit) || false,
    canWithdraw: boolean(source.canWithdraw) || false,
    canManage: boolean(source.canManage) || false,
    canViewQuotes: boolean(source.canViewQuotes) || false,
    canMessage: boolean(source.canMessage) || false,
    canAccept: boolean(source.canAccept) || false,
    canConvert: boolean(source.canConvert) || false,
    canCheckout: boolean(source.canCheckout) || boolean(source.canConvert) || false,
    reason: text(source.reason),
  }
}

/**
 * Public responses are copied field-by-field. Unknown keys (memberId, mobile,
 * contactName and a full delivery address included by a bad server release)
 * never enter component state.
 */
export function sanitizePublicInquiry(value: unknown): PublicInquiry {
  const wrapper = record(value)
  const source = record(wrapper.order && typeof wrapper.order === 'object' ? wrapper.order : wrapper)
  const rawItems = Array.isArray(wrapper.items) ? wrapper.items : Array.isArray(source.items) ? source.items : []
  const id = positiveInteger(source.id)
  if (!id) throw new Error('公开询价单编号无效')
  return {
    id,
    ...(text(source.no) ? { no: text(source.no) } : {}),
    title: text(source.title) || '采购询价',
    itemSummary: text(source.itemSummary) || '',
    // 详情 DTO 将已清洗的询价说明放在包装层，列表 DTO 不返回该字段。
    remark: text(wrapper.remark) || text(source.remark) || '',
    deadline: timestamp(source.deadline),
    expectedDate: timestamp(source.expectedDate),
    status: integer(source.status) ?? 1,
    enterpriseName: text(source.enterpriseName) || text(source.buyerName),
    addressSummary: text(source.addressSummary) || text(source.deliveryArea),
    itemCount: nonNegativeInteger(source.itemCount) ?? rawItems.length,
    quoteCount: nonNegativeInteger(source.quoteCount) ?? 0,
    createTime: timestamp(source.createTime),
    updateTime: timestamp(source.updateTime),
    capabilities: capabilities(wrapper.capabilities ?? source.capabilities ?? source),
    items: rawItems.map(publicItem),
  }
}

export function sanitizePublicInquiryPage(value: unknown): { list: PublicInquiry[]; total: number } {
  const source = record(value)
  const rows = Array.isArray(source.list) ? source.list : []
  return {
    list: rows.map(sanitizePublicInquiry),
    total: nonNegativeInteger(source.total) ?? rows.length,
  }
}

function quoteItem(value: unknown): InquiryQuoteItem {
  const source = record(value)
  const quantity = positiveQuantity(source.quantity ?? source.requestedQuantity) || 1
  const orderCount = positiveInteger(source.orderCount ?? source.count) || (Number.isInteger(quantity) ? quantity : 1)
  const unitPrice = nonNegativeInteger(source.unitPrice ?? source.price) || 0
  const productName = text(source.productName) || text(source.spuName) || text(source.name) || ''
  const skuSpecification = text(source.skuSpecification) || text(source.skuSpec) || text(source.skuProperties) || text(source.specification) || ''
  return {
    ...(positiveInteger(source.id) ? { id: positiveInteger(source.id) } : {}),
    ...(positiveInteger(source.inquiryItemId) ? { inquiryItemId: positiveInteger(source.inquiryItemId) } : {}),
    ...(positiveInteger(source.skuId) ? { skuId: positiveInteger(source.skuId) } : {}),
    name: text(source.name) || productName || text(source.itemName) || text(source.skuName) || '物料',
    specification: text(source.specification) || skuSpecification,
    productName,
    skuName: text(source.skuName) || '',
    skuSpecification,
    quantity,
    orderCount,
    unit: text(source.unit) || '',
    unitPrice,
    amount: nonNegativeInteger(source.amount ?? source.totalPrice) ?? unitPrice * orderCount,
    remark: text(source.remark) || '',
  }
}

export function sanitizeInquiryQuote(value: unknown): InquiryQuote {
  const wrapper = record(value)
  const source = record(wrapper.quote && typeof wrapper.quote === 'object' ? wrapper.quote : wrapper)
  const rawItems = Array.isArray(wrapper.items) ? wrapper.items : Array.isArray(source.items) ? source.items : []
  const id = positiveInteger(source.id)
  const inquiryId = positiveInteger(source.inquiryId)
  if (!inquiryId) throw new Error('询价单编号无效')
  const items = rawItems.map(quoteItem)
  const itemTotal = items.reduce((sum, item) => sum + item.amount, 0)
  return {
    ...(id ? { id } : {}),
    inquiryId,
    inquiryNo: text(source.inquiryNo),
    inquiryTitle: text(source.inquiryTitle),
    shopId: positiveInteger(source.shopId),
    shopName: text(source.shopName),
    status: integer(source.status) ?? 0,
    version: nonNegativeInteger(source.version) ?? 0,
    freightPrice: nonNegativeInteger(source.freightPrice ?? source.deliveryPrice) || 0,
    totalPrice: nonNegativeInteger(source.totalPrice ?? source.price) ?? itemTotal,
    validUntil: timestamp(source.validUntil),
    deliveryDate: timestamp(source.deliveryDate),
    submittedAt: timestamp(source.submittedAt),
    acceptedAt: timestamp(source.acceptedAt),
    remark: text(source.remark) || '',
    createTime: timestamp(source.createTime),
    updateTime: timestamp(source.updateTime),
    capabilities: capabilities(wrapper.capabilities ?? source.capabilities ?? source),
    items,
  }
}

export function sanitizeInquiryMessage(value: unknown): InquiryMessage {
  const source = record(value)
  return {
    id: positiveInteger(source.id),
    quoteId: positiveInteger(source.quoteId),
    senderSide: text(source.senderSide) || text(source.senderRole) || (integer(source.senderType) === 1 ? 'BUYER' : 'SUPPLIER'),
    senderName: text(source.senderName),
    content: text(source.content) || '',
    clientMessageId: text(source.clientMessageId),
    createTime: timestamp(source.createTime),
  }
}

export function sanitizeInquiryMessages(value: unknown): InquiryMessage[] {
  const wrapper = record(value)
  const rows = Array.isArray(value) ? value : Array.isArray(wrapper.messages) ? wrapper.messages : Array.isArray(wrapper.list) ? wrapper.list : []
  return rows.map(sanitizeInquiryMessage)
}

export function inquiryStatusText(status?: number): string {
  return ({ 0: '草稿', 1: '报价中', 2: '已截止', 3: '已关闭', 4: '已成交' } as Record<number, string>)[status ?? -1] || '未知状态'
}

export function quoteStatusText(status?: number): string {
  return ({ 0: '草稿', 1: '已提交', 2: '已采纳', 3: '未采纳', 4: '已撤回' } as Record<number, string>)[status ?? -1] || '未知状态'
}

export function quoteDisplayTime(quote: Pick<InquiryQuote, 'submittedAt' | 'createTime' | 'updateTime'>): string | number | undefined {
  return quote.submittedAt || quote.updateTime || quote.createTime
}

export function inquiryQuoteActions(capabilities: InquiryCapabilities | undefined, hasQuote: boolean) {
  return {
    create: !hasQuote && capabilities?.canQuote === true,
    edit: hasQuote && capabilities?.canEdit === true,
    submit: hasQuote && capabilities?.canSubmit === true,
    withdraw: hasQuote && capabilities?.canWithdraw === true,
    accept: hasQuote && capabilities?.canAccept === true,
    convert: hasQuote && capabilities?.canConvert === true,
  }
}

export function isPositiveId(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0
}

export function makeRequestKey(prefix: string): string {
  const suffix = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`
  return `${prefix}-${suffix}`
}

export function quoteAmount(items: InquiryQuoteItem[], freightPrice = 0): number {
  return items.reduce((sum, item) => sum + (nonNegativeInteger(item.unitPrice) || 0) * (positiveInteger(item.orderCount) || 0), 0)
    + (nonNegativeInteger(freightPrice) || 0)
}

/**
 * Convert a user-entered RMB amount to integer cents without rejecting normal
 * two-decimal values such as 19.99 because of binary floating-point noise.
 */
export function yuanToCents(value: number, label = '金额'): number {
  if (!Number.isFinite(value) || value < 0) throw new Error(`${label}须为不超过两位小数的非负金额`)
  const scaled = value * 100
  const rounded = Math.round(scaled)
  if (!Number.isSafeInteger(rounded) || Math.abs(scaled - rounded) > 1e-6) {
    throw new Error(`${label}须为不超过两位小数的非负金额`)
  }
  return rounded
}
