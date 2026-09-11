import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace CrmAfterSaleVisitTaskApi {
  /** 售后回访任务 */
  export interface VisitTask {
    id?: number;
    planId?: number;
    warrantyId?: number;
    customerId?: number;
    contractId?: number;
    assigneeUserId?: number;
    taskDate?: string;
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
  }
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
