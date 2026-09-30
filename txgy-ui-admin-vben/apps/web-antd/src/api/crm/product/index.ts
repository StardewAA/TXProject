import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace CrmProductApi {
  /**
   * 产品信息
   *
   * 数据实时取自 WMS 仓库库存，CRM 侧只读：不支持新增、修改、删除。
   * WMS 存在的产品才展示，WMS 新增、修改、删除后 CRM 立即同步。
   */
  export interface Product {
    /** 产品编号，即 WMS 规格编号 */
    id: number;
    name: string;
    /** 产品编码，即 WMS 规格编码 */
    no: string;
    unit: number;
    /** WMS 单位名称 */
    unitName?: string;
    price: number;
    /** WMS 仓库库存数量，按所有仓库汇总 */
    stockQuantity?: number;
    status: number;
    categoryId?: number;
    categoryName?: string;
    description: string;
    ownerUserId?: number;
    ownerUserName?: string;
    /** 数据来源：1 手工创建，2 WMS 同步 */
    dataSource?: number;
    /** WMS 商品编号 */
    wmsItemId?: number;
    /** WMS 规格编号 */
    wmsSkuId?: number;
    /** WMS 规格编码 */
    wmsSkuCode?: string;
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

/** 查询产品列表（实时读取 WMS 仓库库存） */
export function getProductPage(params: PageParam) {
  return requestClient.get<PageResult<CrmProductApi.Product>>(
    '/crm/product/page',
    { params },
  );
}

/** 获得产品精简列表（实时读取 WMS 仓库库存） */
export function getProductSimpleList() {
  return requestClient.get<CrmProductApi.Product[]>('/crm/product/simple-list');
}

/** 查询产品详情（实时读取 WMS 仓库库存） */
export function getProduct(id: number) {
  return requestClient.get<CrmProductApi.Product>(`/crm/product/get?id=${id}`);
}

/** 导出产品 */
export function exportProduct(params: any) {
  return requestClient.download('/crm/product/export-excel', { params });
}

/** 从 WMS 同步产品（运维补数，只读取 WMS 商品数据，不修改 WMS 库存） */
export function syncProductFromWms() {
  return requestClient.post<CrmProductApi.ProductWmsSyncResult>(
    '/crm/product/sync-from-wms',
  );
}
