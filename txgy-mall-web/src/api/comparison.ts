import { request } from './http'
import type { ComparisonInput, ComparisonList, ComparisonQuote, ComparisonResult } from '@/types/comparison'

const headers = { 'x-silent-error': true }
export const comparisonApi = {
  list: () => request<ComparisonList>({ url: '/product/comparison/list', headers }),
  replace: (data: ComparisonList) => request<ComparisonList>({ url: '/product/comparison/list', method: 'PUT', data, headers }),
  calculate: (data: ComparisonInput) => request<ComparisonResult>({ url: '/product/comparison/calculate', method: 'POST', data, scope: 'browse', headers }),
  quote: (data: { skuIds: string[]; quantity: number; addressId?: string | number }) => request<ComparisonQuote>({ url: '/trade/product-comparison/quote', method: 'POST', data, headers }),
}
