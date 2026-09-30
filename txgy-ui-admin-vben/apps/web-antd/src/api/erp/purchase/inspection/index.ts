import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace ErpArrivalInspectionApi {
  /** 到货检验明细信息 */
  export interface Item {
    id?: number; // 明细编号
    inspectionId?: number; // 检验单编号
    productId?: number; // 产品编号
    productName?: string; // 产品名称
    productUnitId?: number; // 产品单位编号
    productUnitName?: string; // 产品单位名字
    deliveryCount?: number; // 到货数量
    qualifiedCount?: number; // 合格数量
    unqualifiedCount?: number; // 不合格数量
    result?: number; // 检验结论：1 合格，2 不合格
    remark?: string; // 备注
  }

  /** 到货检验信息 */
  export interface ArrivalInspection {
    id?: number; // 检验单编号
    no?: string; // 检验单号
    purchaseOrderId?: number; // 采购订单编号
    purchaseOrderNo?: string; // 采购订单号
    purchaseInId?: number; // 采购入库编号
    supplierId?: number; // 供应商编号
    inspectTime?: number | string; // 检验时间
    inspectorId?: number; // 检验人编号
    inspectType?: number; // 检验方式：1 全检，2 抽检
    inspectTypeName?: string; // 检验方式名
    result?: number; // 检验结论：1 合格，2 让步接收，3 退货
    resultName?: string; // 检验结论名
    conclusion?: string; // 检验结论说明
    origin?: string; // 来源
    status?: number; // 审批状态
    fileUrl?: string; // 附件地址
    remark?: string; // 备注
    creator?: string; // 创建人
    creatorName?: string; // 创建人名字
    createTime?: string; // 创建时间
    productNames?: string; // 检验物料
    items?: Item[]; // 检验明细
  }
}

/** 查询到货检验分页 */
export function getArrivalInspectionPage(params: PageParam) {
  return requestClient.get<
    PageResult<ErpArrivalInspectionApi.ArrivalInspection>
  >('/erp/purchase-inspection/page', { params });
}

/** 查询到货检验详情 */
export function getArrivalInspection(id: number) {
  return requestClient.get<ErpArrivalInspectionApi.ArrivalInspection>(
    `/erp/purchase-inspection/get?id=${id}`,
  );
}

/** 新增到货检验 */
export function createArrivalInspection(
  data: ErpArrivalInspectionApi.ArrivalInspection,
) {
  return requestClient.post('/erp/purchase-inspection/create', data);
}

/** 修改到货检验 */
export function updateArrivalInspection(
  data: ErpArrivalInspectionApi.ArrivalInspection,
) {
  return requestClient.put('/erp/purchase-inspection/update', data);
}

/** 删除到货检验 */
export function deleteArrivalInspection(ids: number[]) {
  return requestClient.delete('/erp/purchase-inspection/delete', {
    params: { ids: ids.join(',') },
  });
}

/** 提交到货检验审批 */
export function submitArrivalInspection(id: number) {
  return requestClient.put('/erp/purchase-inspection/submit', null, {
    params: { id },
  });
}

/** 审批通过到货检验 */
export function approveArrivalInspection(id: number) {
  return requestClient.put('/erp/purchase-inspection/approve', null, {
    params: { id },
  });
}

/** 审批驳回到货检验 */
export function rejectArrivalInspection(id: number) {
  return requestClient.put('/erp/purchase-inspection/reject', null, {
    params: { id },
  });
}
