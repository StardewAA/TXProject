import type { Address, TradeOrder } from '@/types/mall'
import type { AddressLocation, LocationCapability, LogisticsEvent, OrderLogistics } from '@/types/fulfillment'

const logisticsMessages: Record<string, string> = {
  ERP_PENDING: '这是切换 WMS 直连前的历史出库等待记录，暂不查询运输轨迹。',
  ERP_REJECTED: '历史出库记录未通过，请联系店铺核实。',
  NOT_SHIPPED: '订单尚未发货。',
  NO_TRACKING: '暂无有效运单，请等待店铺完善发货信息。',
  NOT_CONFIGURED: '物流查询暂未开通，您仍可查看已登记的快递公司和运单号。',
  NO_EVENTS: '运单已登记，暂未获取详细物流轨迹。',
  AVAILABLE: '以下为物流服务提供的文字轨迹。',
  PROVIDER_ERROR: '物流查询暂时失败，请稍后重试。',
  NOT_APPLICABLE: '该订单不适用快递物流查询。',
  ORDER_CANCELED: '订单已取消。',
}

export function logisticsPresentation(data?: OrderLogistics | null) {
  const known = data && Object.hasOwn(logisticsMessages, data.queryStatus)
  const status = known ? data.queryStatus : 'UNKNOWN'
  // Do not construct a fake timeline from an order state or unverified provider text.
  const events = data && (status === 'AVAILABLE' || (status === 'PROVIDER_ERROR' && data.stale))
    && Array.isArray(data.events) ? data.events.filter(event => event && typeof event.content === 'string') : []
  const warning = ['ERP_REJECTED', 'PROVIDER_ERROR', 'UNKNOWN'].includes(status)
  const showTransport = status === 'AVAILABLE' || status === 'NO_EVENTS' || (status === 'PROVIDER_ERROR' && data?.stale === true)
  return { status, message: logisticsMessages[status] || '物流信息暂不可用，请稍后重试。', events, warning, showTransport }
}

export function logisticsRetrySeconds(value: unknown): number {
  return typeof value === 'number' && Number.isFinite(value) ? Math.max(0, Math.min(86400, Math.ceil(value))) : 0
}

export interface LogisticsMapQuery {
  label: string
  query: string
  time?: string | number | null
}

function logisticsEventTime(value: LogisticsEvent['time']): number {
  if (typeof value !== 'string' && typeof value !== 'number') return 0
  const parsed = new Date(typeof value === 'string' ? value.replace(' ', 'T') : value).getTime()
  return Number.isFinite(parsed) ? parsed : 0
}

/** Extract only an administrative place from provider text; never invent a vehicle coordinate. */
export function logisticsMapQuery(content: unknown): string {
  if (typeof content !== 'string') return ''
  const normalized = content.replace(/\s+/g, ' ').trim().slice(0, 500)
  const bracketed = [...normalized.matchAll(/[【[(（]([^】\])）]{2,40})[】\])）]/g)]
    .map(match => match[1]?.trim() || '')
    .find(value => /(?:省|自治区|特别行政区|市|自治州|地区|盟|区|县|旗)/.test(value))
  if (bracketed) return bracketed.replace(/(?:分拨|转运|营业|配送|快递|服务)?(?:中心|网点|站点|公司).*$/, '').trim()
  const directed = normalized.match(/(?:到达|抵达|离开|发往|转运至|派送至|收寄于|揽收于|当前位置)[:：\s]*([\u4e00-\u9fa5]{2,20}?(?:省|自治区|特别行政区|市|自治州|地区|盟|区|县|旗))/)
  if (directed?.[1]) return directed[1]
  const city = normalized.match(/([\u4e00-\u9fa5]{2,10}(?:市|自治州|地区|盟))/)
  return city?.[1] || ''
}

export function logisticsMapQueries(events: LogisticsEvent[], limit = 6): LogisticsMapQuery[] {
  const seen = new Set<string>()
  return [...events].sort((a, b) => logisticsEventTime(a.time) - logisticsEventTime(b.time))
    .map(event => ({ event, query: logisticsMapQuery(event.content) }))
    .filter(({ query }) => Boolean(query) && !seen.has(query) && Boolean(seen.add(query)))
    .slice(-Math.max(1, Math.min(10, limit)))
    .map(({ event, query }) => ({ label: query, query, time: event.time }))
}

export function hasAddressLocation(value?: AddressLocation | null): boolean {
  return Boolean(value && typeof value.longitude === 'number' && Number.isFinite(value.longitude)
    && value.longitude >= -180 && value.longitude <= 180
    && typeof value.latitude === 'number' && Number.isFinite(value.latitude)
    && value.latitude >= -90 && value.latitude <= 90 && value.coordSystem === 'GCJ02')
}

const sourceNames: Record<string, string> = { MAP_PICKER: '地图选点', DEVICE_LOCATION: '设备定位', GEOCODER: '地址解析' }
const precisionNames: Record<string, string> = { ADDRESS: '地址级', BUILDING: '楼栋级', APPROXIMATE: '大致位置' }
export function addressLocationText(value?: AddressLocation | null): string {
  if (!hasAddressLocation(value)) return '未设置地图点位，手工地址可正常使用'
  return `GCJ02 · ${sourceNames[value?.locationSource || ''] || '来源未说明'} · ${precisionNames[value?.locationPrecision || ''] || '精度未说明'}`
}

/** No external SDK is installed until its authorization, domains and public configuration are confirmed. */
export function mapAvailability(capability?: LocationCapability | null, failed = false) {
  if (failed) return { status: 'UNAVAILABLE', message: '地图能力暂时无法查询，请继续手工填写地址。' }
  if (!capability || !capability.enabled || capability.status === 'NOT_CONFIGURED') {
    return { status: 'NOT_CONFIGURED', message: '地图服务暂未配置，请手工填写收货地址。' }
  }
  if (capability.status === 'READY' && capability.provider === 'AMAP' && capability.coordinateSystem === 'GCJ02') {
    return { status: 'READY', message: capability.message || '高德地图选点已启用。' }
  }
  return { status: 'ADAPTER_UNAVAILABLE', message: '地图配置不受支持，请手工填写地址。' }
}

export function orderLocation(order: TradeOrder): AddressLocation {
  return { longitude: order.receiverLongitude, latitude: order.receiverLatitude, coordSystem: order.receiverCoordSystem,
    locationSource: order.receiverLocationSource, locationPrecision: order.receiverLocationPrecision,
    locationConfirmedAt: order.receiverLocationConfirmedAt }
}

export function addressTextChanged(current: Address, original?: Address | null): boolean {
  return Boolean(original && (String(current.areaId ?? '') !== String(original.areaId ?? '')
    || current.detailAddress.trim() !== original.detailAddress))
}

/** Allowlist writes: timestamps and server-derived metadata are never echoed back as trusted proof. */
export function manualAddressPayload(current: Address, original?: Address | null, clearLocation = false) {
  const locationConfirmed = (current as Address & { locationConfirmed?: boolean }).locationConfirmed === true
  const location = locationConfirmed && hasAddressLocation(current) && !clearLocation && !addressTextChanged(current, original)
    ? { longitude: Number(current.longitude!.toFixed(6)), latitude: Number(current.latitude!.toFixed(6)),
        coordSystem: 'GCJ02' as const, locationSource: current.locationSource || 'MAP_PICKER',
        locationPrecision: current.locationPrecision || 'ADDRESS',
        locationConfirmed: true }
    : {}
  return { ...(current.id ? { id: current.id } : {}), name: current.name.trim(), mobile: current.mobile.trim(),
    areaId: current.areaId, detailAddress: current.detailAddress.trim(), defaultStatus: Boolean(current.defaultStatus),
    ...location,
    ...(clearLocation || (hasAddressLocation(original) && addressTextChanged(current, original)) ? { clearLocation: true } : {}) }
}
