import type { PageParam, PageResult } from '@/http/types'
import { http } from '@/http/http'
import { downloadApiFile } from '@/utils/download'

/** 产品 */
export interface Product {
  id?: number
  name: string
  no?: string
  unit?: number
  price?: number
  status?: number
  categoryId?: number
  categoryName?: string
  description?: string
  ownerUserId?: number
}

/** 查询产品分页列表 */
export function getProductPage(params: PageParam) {
  return http.get<PageResult<Product>>('/crm/product/page', params)
}

/** 获得产品精简列表 */
export function getProductSimpleList() {
  return http.get<Product[]>('/crm/product/simple-list')
}

/** 查询产品详情 */
export function getProduct(id: number) {
  return http.get<Product>(`/crm/product/get?id=${id}`)
}

/** 新增产品 */
export function createProduct(data: Product) {
  return http.post<number>('/crm/product/create', data)
}

/** 从 WMS 同步产品的结果（运维补数用） */
export interface ProductWmsSyncResult {
  /** 是否跳过本次同步 */
  skipped: boolean;
  /** 跳过原因或同步概要 */
  message?: string;
  /** WMS 返回的规格行数 */
  total: number;
  /** 新增产品数 */
  created: number;
  /** 更新产品数 */
  updated: number;
  /** 无变化产品数 */
  unchanged: number;
  /** 因 WMS 中已删除/停用而被置为「下架」的产品数 */
  offlined: number;
  /** 因产品编码被占用而跳过的数量 */
  conflict: number;
  /** 同步失败数量 */
  failed: number;
  /** 明细信息 */
  messages?: string[];
  /** 同步完成时间 */
  syncTime?: string;
}

/** 从 WMS 同步产品（运维补数，只读取 WMS 商品数据，不修改 WMS 库存） */
export function syncProductFromWms() {
  return http.post<ProductWmsSyncResult>('/crm/product/sync-from-wms')
}

/** 修改产品 */
export function updateProduct(data: Product) {
  return http.put<boolean>('/crm/product/update', data)
}

/** 删除产品 */
export function deleteProduct(id: number) {
  return http.delete<boolean>(`/crm/product/delete?id=${id}`)
}

/** 导出产品 Excel */
export function exportProduct(params: Record<string, any>) {
  return downloadApiFile('/crm/product/export-excel', params, '产品.xls')
}
