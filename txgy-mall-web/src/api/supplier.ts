import { request } from './http'
import type { PageResult } from '@/types/mall'

export interface SupplierSku {
  picUrl?:string
  properties?:{propertyId:number;valueId?:number}[]
  id?: number
  price: number
  marketPrice: number
  stock?: number
  initialStock?: number
  barCode: string
  weight: number
  volume: number
  specification?: string
  wmsManaged?: boolean
}
export interface SupplierProduct {
  reviewStatus?: number | null
  contentVersion?: number
  approvedVersion?: number | null
  submissionId?: string | null
  reviewRemark?: string | null
  expectedVersion?: number
  description?: string
  specType?:boolean
  sliderPicUrls?:string[]
  id?: number
  shopId: number
  name: string
  keyword: string
  introduction: string
  descriptionText?: string
  categoryId?: number
  brandId?: number
  picUrl: string
  deliveryTemplateId?: number
  status?: number
  price?: number
  stock?: number
  skus: SupplierSku[]
}
export interface SupplierOptions {
  properties?:{id:number;name:string;values:{id:number;name:string}[]}[]
  brands: { id: number; name: string }[]
  templates: { id: number; name: string; chargeMode: number }[]
}
export interface ProductReviewCommand { shopId: number; id: number; expectedVersion: number }
export interface ProductReviewRecord {
  id: number
  spuId: number
  shopId: number
  contentVersion: number
  submissionId?: string | null
  action: string
  actorType: string | number
  actorId: number
  remark?: string | null
  snapshot?: string | null
  createTime: string | number
}
const path = '/product/supplier-product'
export const supplierApi = {
  uploadImage:(file:File)=>{const data=new FormData();data.append('file',file);data.append('directory','mall/product');return request<string>({url:'/infra/file/upload',method:'POST',data,headers:{'Content-Type':'multipart/form-data'},timeout:60000})},
  options: (shopId: number) => request<SupplierOptions>({ url: path + '/options', params: { shopId } }),
  page: (params: Record<string, unknown>) => request<PageResult<SupplierProduct>>({ url: path + '/page', params }),
  detail: (shopId: number, id: number) => request<SupplierProduct>({ url: path + '/get', params: { shopId, id } }),
  save: (data: SupplierProduct) => request<number>({ url: path + '/save', method: 'POST', data }),
  status: (shopId: number, id: number, status: number, expectedVersion: number) => request<boolean>({ url: path + '/status', method: 'PUT', params: { shopId, id, status, expectedVersion } }),
  submitReview: (data: ProductReviewCommand) => request<boolean>({ url: path + '/review-submit', method: 'POST', data }),
  withdrawReview: (data: ProductReviewCommand & { submissionId: string }) => request<boolean>({ url: path + '/review-withdraw', method: 'POST', data }),
  reviewHistory: (shopId: number, id: number) => request<ProductReviewRecord[]>({ url: path + '/review-history', params: { shopId, id } }),
}
