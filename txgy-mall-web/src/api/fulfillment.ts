import { request } from './http'
import type { Identifier } from '@/types/mall'
import type { LocationCapability, OrderLogistics } from '@/types/fulfillment'

// Both endpoints remain in the authenticated account scope, never browse scope.
export const fulfillmentApi = {
  getLogistics: (id: Identifier) => request<OrderLogistics>({
    url: '/trade/order/get-logistics', params: { id }, headers: { 'x-silent-error': true },
  }),
  getLocationCapability: () => request<LocationCapability>({
    url: '/member/address/location-capability', headers: { 'x-silent-error': true },
  }),
}
