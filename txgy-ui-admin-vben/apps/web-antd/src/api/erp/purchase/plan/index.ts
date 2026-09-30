import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace ErpPurchasePlanApi {
  /** 采购计划明细 */
  export interface Item {
    id?: number; // 明细编号
    productId?: number; // 产品编号
    productName?: string; // 产品名称
    productUnitId?: number; // 产品单位编号
    productUnitName?: string; // 产品单位名称
    count?: number; // 计划数量
    targetPrice?: number; // 参考单价
    amount?: number; // 金额
    requiredDate?: number | string; // 需求日期
    remark?: string; // 备注
  }

  /** 采购计划信息 */
  export interface PurchasePlan {
    id?: number; // 计划编号
    no?: string; // 采购计划编号
    planName: string; // 计划名称
    planPeriod?: string; // 计划期间
    departmentId?: number; // 需求部门编号
    requesterId?: number; // 计划负责人编号
    planTime?: number | string; // 编制时间
    startDate?: number | string; // 计划开始日期
    endDate?: number | string; // 计划结束日期
    status?: number; // 审批状态
    reverseApproveReason?: string; // 反审批原因
    totalCount?: number; // 计划总数量
    budgetAmount?: number; // 预算金额
    purpose?: string; // 计划说明
    fileUrl?: string; // 附件地址
    remark?: string; // 备注
    creator?: string; // 创建者
    creatorName?: string; // 创建人名称
    createTime?: string; // 创建时间
    productNames?: string; // 计划物料
    items?: Item[]; // 采购计划明细
  }
}

/** 查询采购计划分页 */
export function getPurchasePlanPage(params: PageParam) {
  return requestClient.get<PageResult<ErpPurchasePlanApi.PurchasePlan>>(
    '/erp/purchase-plan/page',
    { params },
  );
}

/** 查询采购计划详情 */
export function getPurchasePlan(id: number) {
  return requestClient.get<ErpPurchasePlanApi.PurchasePlan>(
    `/erp/purchase-plan/get?id=${id}`,
  );
}

/** 新增采购计划 */
export function createPurchasePlan(data: ErpPurchasePlanApi.PurchasePlan) {
  return requestClient.post('/erp/purchase-plan/create', data);
}

/** 修改采购计划 */
export function updatePurchasePlan(data: ErpPurchasePlanApi.PurchasePlan) {
  return requestClient.put('/erp/purchase-plan/update', data);
}

/** 删除采购计划 */
export function deletePurchasePlan(ids: number[]) {
  return requestClient.delete('/erp/purchase-plan/delete', {
    params: { ids: ids.join(',') },
  });
}

/** 提交采购计划审批 */
export function submitPurchasePlan(id: number) {
  return requestClient.put(`/erp/purchase-plan/submit?id=${id}`);
}

/** 审批通过采购计划 */
export function approvePurchasePlan(id: number) {
  return requestClient.put(`/erp/purchase-plan/approve?id=${id}`);
}

/** 审批驳回采购计划 */
export function rejectPurchasePlan(id: number) {
  return requestClient.put(`/erp/purchase-plan/reject?id=${id}`);
}
