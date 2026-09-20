import { requestClient } from '#/api/request';

export namespace ErpSalesAttributionApi {
  /** 查询条件：期间 + 销售员 + 仓库 */
  export interface QueryParams {
    userId?: number;
    warehouseId?: number;
    startTime?: string;
    endTime?: string;
  }

  /** 销售绩效归因（销售员 × 产品） */
  export interface Attribution {
    userId?: number;
    userName?: string;
    productCode?: string;
    productName?: string;
    outCount?: number;
    returnCount?: number;
    netCount?: number;
    outAmount?: number;
    returnAmount?: number;
    netAmount?: number;
  }

  /** 销售绩效汇总（销售员维度，含回款归因） */
  export interface PerformanceSummary {
    userId?: number;
    userName?: string;
    outCount?: number;
    returnCount?: number;
    netCount?: number;
    outAmount?: number;
    returnAmount?: number;
    netAmount?: number;
    /** 名下已审批销售订单金额（应收分母） */
    orderAmount?: number;
    orderCount?: number;
    /** 回款金额（经订单归因到销售员） */
    receiptAmount?: number;
    receiptCount?: number;
    /** 应收余额 = 订单金额 - 回款金额；为负表示预收 */
    unreceiptedAmount?: number;
    /** 回款率(%) */
    receiptRate?: number;
  }

  /** 出库来源构成 */
  export interface SourceBreakdown {
    sourceSystem?: string;
    sourceSystemName?: string;
    bizType?: number;
    bizTypeName?: string;
    totalCount?: number;
    recordCount?: number;
    erpSale?: boolean;
  }
}

/** 获得销售绩效归因（销售员 × 产品） */
export function getSalesAttribution(params: ErpSalesAttributionApi.QueryParams) {
  return requestClient.get<ErpSalesAttributionApi.Attribution[]>(
    '/erp/sales-attribution/performance',
    { params },
  );
}

/** 获得销售绩效汇总（销售员维度，含回款经订单归因） */
export function getPerformanceSummary(
  params: ErpSalesAttributionApi.QueryParams,
) {
  return requestClient.get<ErpSalesAttributionApi.PerformanceSummary[]>(
    '/erp/sales-attribution/performance-summary',
    { params },
  );
}

/** 获得出库来源构成（回答「仓库里多少是 ERP 销售的」） */
export function getSourceBreakdown(
  params: ErpSalesAttributionApi.QueryParams,
) {
  return requestClient.get<ErpSalesAttributionApi.SourceBreakdown[]>(
    '/erp/sales-attribution/source-breakdown',
    { params },
  );
}
