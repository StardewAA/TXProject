import type { PageParam, PageResult } from '@vben/request';

import type { WmsItemSkuApi } from './sku';
import type { QualityReport } from './quality-report';

import { requestClient } from '#/api/request';

export namespace WmsItemApi {
  /** WMS 商品 */
  export interface Item {
    id?: number;
    code?: string;
    name?: string;
    categoryId?: number;
    categoryName?: string;
    unit?: string;
    brandId?: number;
    brandName?: string;
    remark?: string;

    /** 物料类型 */
    materialType?: string;
    /** 是否需要质检 */
    qualityRequired?: boolean;
    /** 状态 */
    status?: number;

    // ========== 批次 / 保质期 / 序列号 ==========
    /** 是否启用批次管理 */
    batchManage?: boolean;
    /** 是否启用保质期管理 */
    shelfLifeManage?: boolean;
    /** 是否启用序列号管理 */
    serialNumberManage?: boolean;
    /** 保质期（月） */
    shelfLifeMonths?: number;
    /** 生产日期 */
    productionDate?: string;
    /** 有效期至 */
    expirationDate?: string;
    /** 批次号规则 */
    batchNumberRule?: string;
    /** 有效期计算规则 */
    expirationRule?: string;
    /** 到期预警天数 */
    expiryWarningDays?: number;
    /** 过期后是否禁止出库 */
    expiredOutboundBlocked?: boolean;

    // ========== 包装 ==========
    /** 包装规格 */
    packageSpec?: string;
    /** 箱规 */
    boxSpec?: string;
    /** 托盘规格 */
    palletSpec?: string;
    /** 最小包装单位 */
    minPackageUnit?: string;

    // ========== 存储 ==========
    /** 存储条件 */
    storageCondition?: string;
    /** 温度要求 */
    temperatureRequirement?: string;
    /** 是否危险品 */
    dangerousGoods?: boolean;

    skus?: WmsItemSkuApi.ItemSku[];
    initialQualityReport?: QualityReport;
    currentQualityReport?: QualityReport;
    qualityReportCount?: number;
    createTime?: Date;
  }
}

/** 查询商品分页 */
export function getItemPage(params: PageParam) {
  return requestClient.get<PageResult<WmsItemApi.Item>>('/wms/item/page', {
    params,
  });
}

/** 查询商品精简列表 */
export function getItemSimpleList(params?: any) {
  return requestClient.get<WmsItemApi.Item[]>('/wms/item/simple-list', {
    params,
  });
}

/** 查询商品详情 */
export function getItem(id: number) {
  return requestClient.get<WmsItemApi.Item>(`/wms/item/get?id=${id}`);
}

/** 新增商品 */
export function createItem(data: WmsItemApi.Item) {
  return requestClient.post('/wms/item/create', data);
}

/** 修改商品 */
export function updateItem(data: WmsItemApi.Item) {
  return requestClient.put('/wms/item/update', data);
}

/** 删除商品 */
export function deleteItem(id: number) {
  return requestClient.delete(`/wms/item/delete?id=${id}`);
}

/** 导出商品 */
export function exportItem(params: any) {
  return requestClient.download('/wms/item/export-excel', { params });
}
