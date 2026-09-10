import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace CrmAfterSaleVisitPlanApi {
  /** 售后回访计划 */
  export interface VisitPlan {
    id?: number;
    name?: string;
    warrantyId?: number;
    planType?: number;
    cycleType?: number;
    cycleValue?: number;
    startDate?: string;
    endDate?: string;
    assigneeType?: number;
    assigneeUserId?: number;
    visitType?: number;
    status?: number;
    lastTaskDate?: string;
    remark?: string;
    createTime?: string;
    /** 扩展：质保编号 */
    warrantyNo?: string;
    /** 扩展：工程项目名称 */
    projectName?: string;
    /** 扩展：客户名称 */
    customerName?: string;
    /** 扩展：处理人昵称 */
    assigneeNickname?: string;
  }

  /** 分页查询参数 */
  export interface PageReq extends PageParam, Partial<VisitPlan> {}
}

/** 查询回访计划分页 */
export function getAfterSaleVisitPlanPage(
  params: CrmAfterSaleVisitPlanApi.PageReq,
) {
  return requestClient.get<PageResult<CrmAfterSaleVisitPlanApi.VisitPlan>>(
    '/crm/after-sale/visit-plan/page',
    { params },
  );
}

/** 查询回访计划详情 */
export function getAfterSaleVisitPlan(id: number) {
  return requestClient.get<CrmAfterSaleVisitPlanApi.VisitPlan>(
    `/crm/after-sale/visit-plan/get?id=${id}`,
  );
}

/** 新增回访计划 */
export function createAfterSaleVisitPlan(
  data: CrmAfterSaleVisitPlanApi.VisitPlan,
) {
  return requestClient.post('/crm/after-sale/visit-plan/create', data);
}

/** 修改回访计划 */
export function updateAfterSaleVisitPlan(
  data: CrmAfterSaleVisitPlanApi.VisitPlan,
) {
  return requestClient.put('/crm/after-sale/visit-plan/update', data);
}

/** 删除回访计划 */
export function deleteAfterSaleVisitPlan(id: number) {
  return requestClient.delete(`/crm/after-sale/visit-plan/delete?id=${id}`);
}

/** 根据回访计划生成回访任务，返回生成条数 */
export function generateAfterSaleVisitTasks(id: number) {
  return requestClient.post<number>(
    `/crm/after-sale/visit-plan/generate-tasks?id=${id}`,
  );
}
