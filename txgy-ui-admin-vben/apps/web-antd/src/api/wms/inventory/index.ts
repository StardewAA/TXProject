import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace WmsInventoryApi {
  /** WMS 库存统计 */
  export interface Inventory {
    id?: number;
    itemId?: number;
    itemCode?: string;
    itemName?: string;
    unit?: string;
    skuId?: number;
    skuCode?: string;
    skuName?: string;
    warehouseId?: number;
    warehouseName?: string;
    quantity?: number;
    remark?: string;
    createTime?: Date;
  }

  export interface RiskItem {
    itemId?: number;
    itemCode?: string;
    itemName?: string;
    skuId?: number;
    skuCode?: string;
    skuName?: string;
    warehouseId?: number;
    warehouseName?: string;
    quantity?: number;
    level?: string;
    label?: string;
    reason?: string;
    daysSinceOutbound?: number;
  }

  export interface RiskSummary {
    inventoryWarnings?: RiskItem[];
    inventoryRisks?: RiskItem[];
  }

  /** WMS 库存统计列表请求 */
  export interface InventoryListReq {
    warehouseId: number;
  }

  /** WMS 库存统计分页查询参数 */
  export interface InventoryPageReq extends PageParam {
    type?: string;
    itemCode?: string;
    itemName?: string;
    skuId?: number;
    skuCode?: string;
    skuName?: string;
    warehouseId?: number;
    onlyPositiveQuantity?: boolean;
  }
}

/** 查询库存统计分页 */
export function getInventoryPage(params: WmsInventoryApi.InventoryPageReq) {
  return requestClient.get<PageResult<WmsInventoryApi.Inventory>>(
    '/wms/inventory/page',
    { params },
  );
}

/** 查询库存统计列表 */
export function getInventoryList(params: WmsInventoryApi.InventoryListReq) {
  return requestClient.get<WmsInventoryApi.Inventory[]>('/wms/inventory/list', {
    params,
  });
}

/** 查询某个 SKU 在各仓库的库存，供库存统计详情使用 */
export function getInventoryDetailList(skuId: number) {
  return getInventoryPage({
    pageNo: 1,
    pageSize: 200,
    type: 'item',
    skuId,
  });
}

/** 查询库存预警与积压风险汇总 */
export function getInventoryRiskSummary() {
  return requestClient.get<WmsInventoryApi.RiskSummary>('/wms/inventory/risk-summary');
}
