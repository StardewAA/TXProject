import { request } from './http'
import type { Shop } from '@/types/mall'
export interface EnterpriseApplication {
  id?: number; memberId?: number; identity?: 'buyer' | 'supplier'; status?: number
  enterpriseId?: number; auditRemark?: string; auditTime?: string
  name: string; creditCode: string; licenseData: string; contactName: string
  contactMobile: string; address: string; introduction: string
}
export const enterpriseApi = {
  mine: () => request<EnterpriseApplication | null>({ url: '/product/enterprise-membership/mine' }),
  apply: (data: EnterpriseApplication) => request<number>({ url: '/product/enterprise-membership/apply', method: 'POST', data }),
  salesShops: () => request<Shop[]>({ url: '/product/enterprise-membership/sales-shops' }),
}
