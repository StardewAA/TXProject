import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace ErpSaleOrderApi {
  /** 销售订单信息 */
  export interface SaleOrder {
    id?: number; // 订单工单编号
    no: string; // 销售订单号
    customerId: number; // 客户编号
    accountId?: number; // 收款账户编号
    orderTime: Date; // 订单时间
    totalCount: number; // 合计数量
    totalPrice: number; // 合计金额，单位：元
    status: number; // 状态
    processInstanceId?: string; // BPM 流程实例编号
    reverseApproveReason?: string; // 反审批原因
    customerName?: string; // 客户名称
    productNames?: string; // 产品名称列表
    creatorName?: string; // 创建人名称
    createTime?: Date | string; // 创建时间
    remark: string; // 备注
    outCount: number; // 销售出库数量
    fileUrl?: string; // 附件地址
    inCount?: number; // 采购入库数量
    returnCount: number; // 销售退货数量
    totalProductPrice?: number; // 产品金额，单位：元
    discountPercent?: number; // 优惠率，百分比
    discountPrice?: number; // 优惠金额，单位：元
    depositPrice?: number; // 定金金额，单位：元
    // ===== 溯源 =====
    sourceType?: string; // 来源类型（COST_RESULT=造价转换生成）
    sourceId?: number; // 来源编号（造价结果编号）
    sourceNo?: string; // 来源单号
    businessChainNo?: string; // 全局业务链编号
    // ===== WMS 履约 =====
    warehouseStatus?: string; // WMS 仓储履约状态
    inventoryDecisionNo?: string; // WMS 库存判断编号
    reservationNo?: string; // WMS 库存预留编号
    wmsShipmentOrderNo?: string; // WMS 销售出库单号
    wmsEventVersion?: number; // 最后处理的 WMS 事件版本
    wmsFailureReason?: string; // WMS 失败原因
    items?: SaleOrderItem[]; // 销售订单产品明细列表
  }

  /** 销售订单项 */
  export interface SaleOrderItem {
    id?: number; // 订单项编号
    seq?: number; // 前端行号
    orderId?: number; // 采购订单编号
    productId?: number; // 产品编号
    productName?: string; // 产品名称
    productBarCode?: string; // 产品条码
    productUnitId?: number; // 产品单位编号
    productUnitName?: string; // 产品单位名称
    productPrice?: number; // 产品单价，单位：元
    totalProductPrice?: number; // 产品总价，单位：元
    count?: number; // 数量
    totalPrice?: number; // 总价，单位：元
    taxPercent?: number; // 税率，百分比
    taxPrice?: number; // 税额，单位：元
    totalTaxPrice?: number; // 含税总价，单位：元
    remark?: string; // 备注
    stockCount?: number; // 库存数量（显示字段）
    warehouseCode?: string; // WMS 统一仓库编码
    outCount?: number; // 已出库数量（WMS 实发回传）
    reservedCount?: number; // WMS 预留数量
    shortageCount?: number; // WMS 缺口数量
  }
}

/** 查询销售订单分页 */
export function getSaleOrderPage(params: PageParam) {
  return requestClient.get<PageResult<ErpSaleOrderApi.SaleOrder>>(
    '/erp/sale-order/page',
    { params },
  );
}

/** 查询销售订单详情 */
export function getSaleOrder(id: number) {
  return requestClient.get<ErpSaleOrderApi.SaleOrder>(
    `/erp/sale-order/get?id=${id}`,
  );
}

/** 查询销售订单项列表 */
export function getSaleOrderItemListByOrderId(orderId: number) {
  return requestClient.get<ErpSaleOrderApi.SaleOrderItem[]>(
    `/erp/sale-order/item/list-by-order-id?orderId=${orderId}`,
  );
}

/** 新增销售订单 */
export function createSaleOrder(data: ErpSaleOrderApi.SaleOrder) {
  return requestClient.post('/erp/sale-order/create', data);
}

/** 修改销售订单 */
export function updateSaleOrder(data: ErpSaleOrderApi.SaleOrder) {
  return requestClient.put('/erp/sale-order/update', data);
}

/** 反审批销售订单（审批中 / 已审批 → 草稿），reason 为必填原因 */
export function reverseApproveSaleOrder(id: number, reason: string) {
  return requestClient.put('/erp/sale-order/reverse-approve', null, {
    params: { id, reason },
  });
}

/** 提交销售订单审批 */
export function submitSaleOrder(id: number) {
  return requestClient.put('/erp/sale-order/submit', null, {
    params: { id },
  });
}

/** 删除销售订单 */
export function deleteSaleOrder(ids: number[]) {
  return requestClient.delete('/erp/sale-order/delete', {
    params: { ids: ids.join(',') },
  });
}

/** 导出销售订单 Excel */
export function exportSaleOrder(params: any) {
  return requestClient.download('/erp/sale-order/export-excel', { params });
}

/** 导出销售订单 PDF */
export function exportSaleOrderPdf(params: any) {
  return requestClient.download('/erp/sale-order/export-pdf', { params });
}
