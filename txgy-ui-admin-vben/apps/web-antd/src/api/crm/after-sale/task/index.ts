import type { PageParam, PageResult } from '@vben/request';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';

import { requestClient } from '#/api/request';

export namespace CrmAfterSaleVisitTaskApi {
  /** 售后回访任务 */
  export interface VisitTask {
    id?: number;
    planId?: number;
    /** 业务编号：按质保档案独立递增（仅用于展示与任务绑定，非主键） */
    bizNo?: number;
    warrantyId?: number;
    customerId?: number;
    contractId?: number;
    assigneeUserId?: number;
    taskDate?: string;
    /**
     * 窗口期截止日期 = 计划回访日期 + 回访任务逾期判定天数
     * 当前日期 > 该日期时任务状态为「已逾期」
     */
    windowEndDate?: string;
    status?: number;
    finishTime?: string;
    overdueSentDate?: string;
    remark?: string;
    createTime?: string;
    /** 扩展：质保编号 */
    warrantyNo?: string;
    /** 扩展：工程项目名称 */
    projectName?: string;
    /** 扩展：合同名称 */
    contractName?: string;
    /** 扩展：客户名称 */
    customerName?: string;
    /** 扩展：处理人昵称 */
    assigneeNickname?: string;
    /** 扩展：是否已登记回访记录 */
    hasRecord?: boolean;
    /** 扩展：任务完成后自动生成的回访记录编号 */
    recordId?: number;
    /** 扩展：回访记录状态：1=待填写 2=已填写 */
    recordStatus?: number;
  }

  /** 分页查询参数 */
  export interface PageReq extends PageParam, Partial<VisitTask> {
    taskDate?: string[];
    /** 提醒类型：1=任务提醒（待办「任务提醒」，含临期与已逾期，实时查询且限本人及下属负责） */
    remindType?: number;
  }
}

/** 提醒类型：1=任务提醒（待办「任务提醒」使用，与后端 REMIND_TYPE_APPROACHING 保持一致） */
export const AFTER_SALE_VISIT_TASK_REMIND_TYPE_APPROACHING = 1;

/** 回访任务状态：3=已逾期（与后端 CrmAfterSaleVisitTaskStatusEnum.OVERDUE 保持一致） */
export const AFTER_SALE_VISIT_TASK_STATUS_OVERDUE = 3;

/** 获得「任务提醒」待办数量（实时查询，不落库；含临期与已逾期） */
export function getAfterSaleVisitTaskRemindCount() {
  return requestClient.get<number>('/crm/after-sale/visit-task/remind-count');
}

/** 查询回访任务分页 */
export function getAfterSaleVisitTaskPage(
  params: CrmAfterSaleVisitTaskApi.PageReq,
) {
  return requestClient.get<PageResult<CrmAfterSaleVisitTaskApi.VisitTask>>(
    '/crm/after-sale/visit-task/page',
    { params },
  );
}

/** 查询回访任务详情 */
export function getAfterSaleVisitTask(id: number) {
  return requestClient.get<CrmAfterSaleVisitTaskApi.VisitTask>(
    `/crm/after-sale/visit-task/get?id=${id}`,
  );
}

/** 新增回访任务 */
export function createAfterSaleVisitTask(
  data: CrmAfterSaleVisitTaskApi.VisitTask,
) {
  return requestClient.post('/crm/after-sale/visit-task/create', data);
}

/** 修改回访任务 */
export function updateAfterSaleVisitTask(
  data: CrmAfterSaleVisitTaskApi.VisitTask,
) {
  return requestClient.put('/crm/after-sale/visit-task/update', data);
}

/** 删除回访任务 */
export function deleteAfterSaleVisitTask(id: number) {
  return requestClient.delete(`/crm/after-sale/visit-task/delete?id=${id}`);
}

/** 完成回访任务 */
export function finishAfterSaleVisitTask(id: number) {
  return requestClient.post(`/crm/after-sale/visit-task/finish?id=${id}`);
}

/** 查询指定质保档案下的回访任务精简列表 */
export function getAfterSaleVisitTaskSimpleList(warrantyId: number) {
  return requestClient.get<CrmAfterSaleVisitTaskApi.VisitTask[]>(
    '/crm/after-sale/visit-task/simple-list',
    { params: { warrantyId } },
  );
}

/**
 * 获得回访任务下拉选项（按质保档案过滤）
 *
 * 展示格式：业务编号（任务状态），例如「1（待处理）」「2（已逾期）」
 */
export async function getAfterSaleVisitTaskOptions(
  warrantyId?: number,
): Promise<Array<{ label: string; value: number }>> {
  if (!warrantyId) {
    return [];
  }
  const list = await getAfterSaleVisitTaskSimpleList(warrantyId);
  const statusLabelMap = new Map(
    getDictOptions(DICT_TYPE.CRM_AFTER_SALE_VISIT_TASK_STATUS, 'number').map(
      (item) => [item.value, item.label],
    ),
  );
  return (list ?? []).map((item) => ({
    label: `${item.bizNo ?? item.id}（${statusLabelMap.get(item.status as number) ?? ''}）`,
    value: item.id as number,
  }));
}
