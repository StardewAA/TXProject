import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace CrmProductApi {
  /** 产品信息 */
  export interface Product {
    id: number;
    name: string;
    no: string;
    unit: number;
    price: number;
    status: number;
    categoryId: number;
    categoryName?: string;
    description: string;
    ownerUserId: number;
    /** 数据来源：1 手工创建，2 WMS 同步 */
    dataSource?: number;
    /** WMS 规格编码 */
    wmsSkuCode?: string;
  }

  /** 从 WMS 同步产品的结果 */
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
}

/** 查询产品列表 */
export function getProductPage(params: PageParam) {
  return requestClient.get<PageResult<CrmProductApi.Product>>(
    '/crm/product/page',
    { params },
  );
}

/** 获得产品精简列表 */
export function getProductSimpleList() {
  return requestClient.get<CrmProductApi.Product[]>('/crm/product/simple-list');
}

/** 查询产品详情 */
export function getProduct(id: number) {
  return requestClient.get<CrmProductApi.Product>(`/crm/product/get?id=${id}`);
}

/** 新增产品 */
export function createProduct(data: CrmProductApi.Product) {
  return requestClient.post('/crm/product/create', data);
}

/** 修改产品 */
export function updateProduct(data: CrmProductApi.Product) {
  return requestClient.put('/crm/product/update', data);
}

/** 删除产品 */
export function deleteProduct(id: number) {
  return requestClient.delete(`/crm/product/delete?id=${id}`);
}

/** 导出产品 */
export function exportProduct(params: any) {
  return requestClient.download('/crm/product/export-excel', { params });
}

/** 从 WMS 同步产品（只读取 WMS 商品数据，不修改 WMS 库存） */
export function syncProductFromWms() {
  return requestClient.post<CrmProductApi.ProductWmsSyncResult>(
    '/crm/product/sync-from-wms',
  );
}
