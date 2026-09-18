import type { Address, TradeOrder } from '@/types/mall'
import type { AddressLocation, LocationCapability, OrderLogistics } from '@/types/fulfillment'

const logisticsMessages: Record<string, string> = {
  ERP_PENDING: '发货正在等待 ERP 确认出库，暂不查询运输轨迹。',
  ERP_REJECTED: 'ERP 出库未通过，请联系店铺处理发货。',
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
  return { status: 'ADAPTER_UNAVAILABLE', message: '地图选点暂不可用，请手工填写地址。' }
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
  return { ...(current.id ? { id: current.id } : {}), name: current.name.trim(), mobile: current.mobile.trim(),
    areaId: current.areaId, detailAddress: current.detailAddress.trim(), defaultStatus: Boolean(current.defaultStatus),
    ...(clearLocation || (hasAddressLocation(original) && addressTextChanged(current, original)) ? { clearLocation: true } : {}) }
}
