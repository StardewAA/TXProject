import type { Identifier } from './mall'

export interface AddressLocation {
  longitude?: number | null
  latitude?: number | null
  coordSystem?: 'GCJ02' | null
  locationSource?: 'MAP_PICKER' | 'DEVICE_LOCATION' | 'GEOCODER' | null
  locationPrecision?: 'ADDRESS' | 'BUILDING' | 'APPROXIMATE' | null
  locationConfirmedAt?: string | number | null
}

export interface LocationCapability {
  status: string
  provider: string
  coordinateSystem: string
  enabled: boolean
  manualInputEnabled: boolean
  message?: string
}

export type LogisticsQueryStatus = 'ERP_PENDING' | 'ERP_REJECTED' | 'NOT_SHIPPED' | 'NO_TRACKING'
  | 'NOT_CONFIGURED' | 'NO_EVENTS' | 'AVAILABLE' | 'PROVIDER_ERROR' | 'NOT_APPLICABLE' | 'ORDER_CANCELED'

export interface LogisticsEvent {
  time?: string | number | null
  content: string
  status?: string | null
  statusLabel?: string | null
  rawStatus?: string | null
  source?: string | null
}

export interface OrderLogistics {
  orderId: Identifier
  deliveryType?: number
  queryStatus: LogisticsQueryStatus
  message?: string
  carrierName?: string | null
  carrierCode?: string | null
  trackingNo?: string | null
  transportStatus?: string | null
  transportStatusLabel?: string | null
  source?: string | null
  updatedTime?: string | number | null
  cached?: boolean
  stale?: boolean
  refreshAfterSeconds?: number
  events: LogisticsEvent[]
}
