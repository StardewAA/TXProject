import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace WmsItemBrandApi {
  /** WMS 商品品牌 */
  export interface ItemBrand {
    id?: number;
    code?: string;
    name?: string;
    /** 关联商品分类编号 */
    categoryId?: number;
    /** 关联商品分类名称 */
    categoryName?: string;
    /** 主要供应商编号 */
    supplierId?: number;
    /** 主要供应商名称 */
    supplierName?: string;
    /** 品牌说明 */
    description?: string;
    /** 资质文件地址，多个以逗号分隔 */
    qualificationFiles?: string;
    createTime?: Date;
  }
}

/** 查询商品品牌分页 */
export function getItemBrandPage(params: PageParam) {
  return requestClient.get<PageResult<WmsItemBrandApi.ItemBrand>>(
    '/wms/item-brand/page',
    { params },
  );
}

/** 查询商品品牌精简列表 */
export function getItemBrandSimpleList() {
  return requestClient.get<WmsItemBrandApi.ItemBrand[]>(
    '/wms/item-brand/simple-list',
  );
}

/** 查询商品品牌详情 */
export function getItemBrand(id: number) {
  return requestClient.get<WmsItemBrandApi.ItemBrand>(
    `/wms/item-brand/get?id=${id}`,
  );
}

/** 新增商品品牌 */
export function createItemBrand(data: WmsItemBrandApi.ItemBrand) {
  return requestClient.post('/wms/item-brand/create', data);
}

/** 修改商品品牌 */
export function updateItemBrand(data: WmsItemBrandApi.ItemBrand) {
  return requestClient.put('/wms/item-brand/update', data);
}

/** 删除商品品牌 */
export function deleteItemBrand(id: number) {
  return requestClient.delete(`/wms/item-brand/delete?id=${id}`);
}

/** 导出商品品牌 */
export function exportItemBrand(params: any) {
  return requestClient.download('/wms/item-brand/export-excel', { params });
}
