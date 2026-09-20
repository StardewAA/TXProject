import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace WmsSharedWarehouseApi {
  export interface Warehouse {
    warehouseId?: number;
    warehouseCode?: string;
    warehouseName?: string;
    ownerSystem?: string;
    sourceSystems?: string[];
    wmsSkuCount?: number;
    mallSkuCount?: number;
    erpProductCount?: number;
    physicalQuantity?: number;
    relatedErpWarehouseNames?: string[];
    remark?: string;
  }

  export interface Product {
    warehouseId?: number;
    warehouseName?: string;
    wmsSkuId?: number;
    wmsSkuName?: string;
    wmsItemId?: number;
    wmsItemName?: string;
    barCode?: string;
    physicalQuantity?: number;
    sourceSystems?: string[];
    mallSkuId?: number;
    mallSpuId?: number;
    mallSpuName?: string;
    mallCachedStock?: number;
    erpProductId?: number;
    erpProductName?: string;
  }

  export interface WarehousePageReq extends PageParam {
    code?: string;
    name?: string;
    sourceSystem?: string;
  }

  export interface ProductPageReq extends PageParam {
    warehouseId: number;
    itemName?: string;
    sourceSystem?: string;
  }
}

export function getSharedWarehousePage(
  params: WmsSharedWarehouseApi.WarehousePageReq,
) {
  return requestClient.get<PageResult<WmsSharedWarehouseApi.Warehouse>>(
    '/wms/shared-warehouse/page',
    { params },
  );
}

export function getSharedWarehouseProductPage(
  params: WmsSharedWarehouseApi.ProductPageReq,
) {
  return requestClient.get<PageResult<WmsSharedWarehouseApi.Product>>(
    '/wms/shared-warehouse/products',
    { params },
  );
}
