import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace ErpStockCheckApi {
  /** 库存盘点单信息 */
  export interface StockCheck {
    id?: number; // 盘点编号
    no: string; // 盘点单号
    checkTime: Date; // 盘点时间
    totalCount: number; // 合计数量
    totalPrice: number; // 合计金额，单位：元
    status: number; // 状态
    remark: string; // 备注
    fileUrl?: string; // 附件
    productNames?: string; // 产品信息
    creatorName?: string; // 创建人
    items?: StockCheckItem[]; // 盘点产品清单
  }

  /** 库存盘点项 */
  export interface StockCheckItem {
    id?: number; // 编号
    seq?: number; // 前端行号
    warehouseId?: number; // 仓库编号
    productId?: number; // 产品编号
    productName?: string; // 产品名称
    productUnitId?: number; // 产品单位编号
    productUnitName?: string; // 产品单位名称
    productBarCode?: string; // 产品条码
    count?: number; // 盈亏数量
    actualCount?: number; // 实际库存
    productPrice?: number; // 产品单价
    totalPrice?: number; // 总价
    stockCount?: number; // 账面库存
    remark?: string; // 备注
  }

  /** 盘点单三方比对明细行 */
  export interface ComparisonLine {
    itemId?: number;
    productId?: number;
    productCode?: string; // 统一物料编码
    productName?: string;
    productUnitName?: string;
    warehouseId?: number;
    warehouseName?: string;
    wmsWarehouseCode?: string; // 为空表示该仓库未映射到 WMS
    bookCount?: number; // ① ERP 账面（实时）
    snapshotBookCount?: number; // ① ERP 账面（盘点单快照）
    actualCount?: number; // ② ERP 实盘
    wmsCount?: number | null; // ③ WMS 实物，null = 无镜像数据
    wmsSyncTime?: string; // WMS 镜像同步时间
    checkDiff?: number; // 盘点盈亏 = 实盘 - 账面快照
    bookDrift?: number; // 盘点期间账面变动
    wmsVsActual?: number | null; // WMS 需调整量
    wmsVsBook?: number | null; // 账实差
    mirrorAvailable?: boolean;
    wmsAdjustRequired?: boolean;
    bookDriftDetected?: boolean;
    remark?: string;
  }

  /** 盘点单三方比对结果 */
  export interface Comparison {
    checkId?: number;
    checkNo?: string;
    checkTime?: string;
    status?: number;
    totalLines?: number;
    checkDiffLineCount?: number;
    mirrorCoveredLineCount?: number;
    wmsAdjustLineCount?: number;
    bookDriftLineCount?: number;
    /** 为 false 时说明尚无 WMS 镜像，三方降级为两方 */
    mirrorAvailable?: boolean;
    lines?: ComparisonLine[];
  }
}

/** 查询库存盘点单分页 */
export function getStockCheckPage(params: PageParam) {
  return requestClient.get<PageResult<ErpStockCheckApi.StockCheck>>(
    '/erp/stock-check/page',
    {
      params,
    },
  );
}

/** 查询库存盘点单详情 */
export function getStockCheck(id: number) {
  return requestClient.get<ErpStockCheckApi.StockCheck>(
    `/erp/stock-check/get?id=${id}`,
  );
}

/** 新增库存盘点单 */
export function createStockCheck(data: ErpStockCheckApi.StockCheck) {
  return requestClient.post('/erp/stock-check/create', data);
}

/** 修改库存盘点单 */
export function updateStockCheck(data: ErpStockCheckApi.StockCheck) {
  return requestClient.put('/erp/stock-check/update', data);
}

/** 更新库存盘点单的状态 */
export function updateStockCheckStatus(id: number, status: number) {
  return requestClient.put('/erp/stock-check/update-status', null, {
    params: { id, status },
  });
}

/** 删除库存盘点 */
export function deleteStockCheck(ids: number[]) {
  return requestClient.delete('/erp/stock-check/delete', {
    params: {
      ids: ids.join(','),
    },
  });
}

/** 导出库存盘点单 Excel */
export function exportStockCheck(params: any) {
  return requestClient.download('/erp/stock-check/export-excel', {
    params,
  });
}

/** 导出库存盘点单 PDF */
export function exportStockCheckPdf(params: any) {
  return requestClient.download('/erp/stock-check/export-pdf', {
    params,
  });
}

/** 获得盘点单三方比对（ERP 账面 / ERP 实盘 / WMS 实物） */
export function getStockCheckComparison(checkId: number) {
  return requestClient.get<ErpStockCheckApi.Comparison>(
    '/erp/stock-check/comparison',
    { params: { checkId } },
  );
}
