import { describe, expect, it } from 'vitest'
import type { Address, TradeOrder } from '@/types/mall'
import type { OrderLogistics } from '@/types/fulfillment'
import { addressLocationText, addressTextChanged, hasAddressLocation, logisticsPresentation, logisticsRetrySeconds, manualAddressPayload, mapAvailability, orderLocation } from './fulfillment'

const address: Address = { id: 1, name: '测试联系人', mobile: '13800000000', areaId: 110101, detailAddress: '隔离测试地址', defaultStatus: true }
const located: Address = { ...address, longitude: 116.4, latitude: 39.9, coordSystem: 'GCJ02', locationSource: 'MAP_PICKER', locationPrecision: 'BUILDING', locationConfirmedAt: '2026-09-17T10:00:00' }
const track = (queryStatus: OrderLogistics['queryStatus']): OrderLogistics => ({ orderId: 1, queryStatus, events: [{ content: '承运商扫描节点', time: '2026-09-17T10:00:00' }] })

describe('truthful logistics presentation', () => {
  it.each(['ERP_PENDING', 'ERP_REJECTED', 'NOT_SHIPPED', 'NO_TRACKING', 'NOT_CONFIGURED', 'NO_EVENTS', 'PROVIDER_ERROR', 'NOT_APPLICABLE', 'ORDER_CANCELED'] as const)('does not fabricate or expose a timeline for %s', status => {
    expect(logisticsPresentation(track(status)).events).toEqual([])
    expect(logisticsPresentation(track(status)).status).toBe(status)
  })
  it('separates ERP wait, not configured, no events and temporary failure', () => {
    expect(logisticsPresentation(track('ERP_PENDING')).message).toContain('ERP')
    expect(logisticsPresentation(track('NOT_CONFIGURED')).message).toContain('暂未开通')
    expect(logisticsPresentation(track('NO_EVENTS')).message).toContain('暂未获取详细物流轨迹')
    expect(logisticsPresentation(track('PROVIDER_ERROR')).message).toContain('失败')
  })
  it('uses authorized events without fabricating a shipment/signature from order state', () => {
    const result = logisticsPresentation(track('AVAILABLE'))
    expect(result.events).toEqual(track('AVAILABLE').events)
    expect(result.message).toContain('文字轨迹')
  })
  it('only shows a failure snapshot when it is explicitly stale', () => {
    expect(logisticsPresentation({ ...track('PROVIDER_ERROR'), stale: true }).events).toHaveLength(1)
  })
  it('a provider can report a known transport state without detailed events, but ERP gates cannot', () => {
    expect(logisticsPresentation({ ...track('NO_EVENTS'), transportStatus: 'SIGNED', events: [] }).showTransport).toBe(true)
    expect(logisticsPresentation({ ...track('ERP_PENDING'), transportStatus: 'SIGNED' }).showTransport).toBe(false)
  })
  it('unknown or malformed state fails closed and never uses arbitrary server message', () => {
    const result = logisticsPresentation({ ...track('AVAILABLE'), queryStatus: 'constructor', message: '假签收' } as unknown as OrderLogistics)
    expect(result.status).toBe('UNKNOWN'); expect(result.events).toEqual([]); expect(result.message).not.toContain('假签收')
    expect(logisticsPresentation(null).events).toEqual([])
  })
  it.each([[20.2, 21], [-2, 0], [Infinity, 0], [NaN, 0], ['300', 0], [999999999, 86400]])('normalizes retry budget %s', (value, expected) => expect(logisticsRetrySeconds(value)).toBe(expected))
})

describe('manual address and optional map', () => {
  it('old addresses do not need coordinates', () => {
    expect(hasAddressLocation(address)).toBe(false)
    expect(addressLocationText(address)).toContain('正常使用')
    expect(manualAddressPayload(address)).toEqual(address)
  })
  it('shows source and precision of a saved point without pretending real-time tracking', () => {
    expect(addressLocationText(located)).toBe('GCJ02 · 地图选点 · 楼栋级')
    expect(addressLocationText({ ...located, locationPrecision: 'APPROXIMATE' })).toContain('大致位置')
  })
  it.each([{ longitude: NaN }, { latitude: Infinity }, { longitude: 181 }, { latitude: -91 }, { coordSystem: 'WGS84' }, { latitude: null }, { longitude: '116.4' }])('rejects invalid or unconverted coordinates %j', patch => expect(hasAddressLocation({ ...located, ...patch } as typeof located)).toBe(false))
  it('does not reject legitimate zero coordinates', () => expect(hasAddressLocation({ ...located, latitude: 0, longitude: 0 })).toBe(true))
  it('does not send a saved coordinate or trusted timestamp back from the manual editor', () => {
    expect(manualAddressPayload(located, located)).toEqual(address)
  })
  it.each([{ areaId: 110102 }, { detailAddress: '新街道门牌' }])('clears stale points when text changes %j', patch => {
    expect(manualAddressPayload({ ...located, ...patch }, located)).toHaveProperty('clearLocation', true)
  })
  it('contact-only edits leave server point unchanged', () => {
    expect(manualAddressPayload({ ...located, name: '另一个联系人' }, located)).not.toHaveProperty('clearLocation')
    expect(addressTextChanged({ ...located, areaId: '110101', detailAddress: ` ${located.detailAddress} ` }, located)).toBe(false)
  })
  it('also clears when normalizing legacy whitespace changes the exact stored address', () => {
    const legacy = { ...located, detailAddress: ` ${located.detailAddress} ` }
    expect(manualAddressPayload(legacy, legacy)).toHaveProperty('clearLocation', true)
  })
  it('allows explicit clear without text edits', () => expect(manualAddressPayload(located, located, true)).toHaveProperty('clearLocation', true))
  it('empty new addresses never carry the last item id or a location proof', () => {
    const payload = manualAddressPayload({ ...located, id: 0 })
    expect(payload).not.toHaveProperty('id'); expect(payload).not.toHaveProperty('longitude'); expect(payload).not.toHaveProperty('locationConfirmed')
  })
  it('map unavailable or uninstalled adapter always keeps the manual flow', () => {
    expect(mapAvailability(null).status).toBe('NOT_CONFIGURED')
    expect(mapAvailability(null, true).status).toBe('UNAVAILABLE')
    expect(mapAvailability({ status: 'READY', provider: 'AMAP', coordinateSystem: 'GCJ02', enabled: true, manualInputEnabled: true }).status).toBe('ADAPTER_UNAVAILABLE')
  })
  it('reads coordinates from immutable order snapshot, not the editable address book', () => {
    const order = { id: 1, status: 20, payPrice: 1, receiverLongitude: 116.4, receiverLatitude: 39.9, receiverCoordSystem: 'GCJ02', receiverLocationSource: 'GEOCODER', receiverLocationPrecision: 'APPROXIMATE' } as TradeOrder
    expect(orderLocation(order)).toMatchObject({ longitude: 116.4, locationSource: 'GEOCODER', locationPrecision: 'APPROXIMATE' })
  })
})
