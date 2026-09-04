import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace MesProProjectApi {
  /** MES 项目 */
  export interface Project {
    id?: number; // 编号
    code?: string; // 项目编码
    name?: string; // 项目名称
    orderSourceCode?: string; // 来源单据编号
    sourceType?: number; // 项目来源类型
    status?: number; // 项目状态
    remark?: string; // 备注
    workOrderCount?: number; // 关联生产工单数
    finishedWorkOrderCount?: number; // 已完成生产工单数
    createTime?: number; // 创建时间
  }

  /** MES 项目分页查询参数 */
  export interface PageParams extends PageParam {
    code?: string;
    name?: string;
    status?: number;
    sourceType?: number;
  }

  /** MES 项目生产进度统计 */
  export interface ProjectStatistics {
    projectId?: number; // 项目编号
    workOrderTotal?: number; // 生产工单总数（含已取消）
    prepareCount?: number; // 草稿工单数
    confirmedCount?: number; // 已确认(进行中)工单数
    finishedCount?: number; // 已完成工单数
    canceledCount?: number; // 已取消工单数
    quantityTotal?: number; // 生产数量合计
    quantityProducedTotal?: number; // 已完成生产数量合计
  }
}

/** 查询项目分页 */
export function getProjectPage(params: MesProProjectApi.PageParams) {
  return requestClient.get<PageResult<MesProProjectApi.Project>>(
    '/mes/pro/project/page',
    { params },
  );
}

/** 查询项目详情 */
export function getProject(id: number) {
  return requestClient.get<MesProProjectApi.Project>(
    `/mes/pro/project/get?id=${id}`,
  );
}

/** 新增项目 */
export function createProject(data: MesProProjectApi.Project) {
  return requestClient.post<number>('/mes/pro/project/create', data);
}

/** 修改项目 */
export function updateProject(data: MesProProjectApi.Project) {
  return requestClient.put('/mes/pro/project/update', data);
}

/** 删除项目 */
export function deleteProject(id: number) {
  return requestClient.delete(`/mes/pro/project/delete?id=${id}`);
}

/** 导出项目 */
export function exportProject(params: any) {
  return requestClient.download('/mes/pro/project/export-excel', { params });
}

/** 获得项目的生产进度统计 */
export function getProjectStatistics(projectId: number) {
  return requestClient.get<MesProProjectApi.ProjectStatistics>(
    `/mes/pro/project/statistics?projectId=${projectId}`,
  );
}

/** 按工单进度重算项目状态 */
export function refreshProjectStatus(projectId: number) {
  return requestClient.put(`/mes/pro/project/refresh-status?projectId=${projectId}`);
}

/** 批量将生产工单挂接到项目 */
export function bindWorkOrders(projectId: number, workOrderIds: number[]) {
  return requestClient.put('/mes/pro/project/bind-work-orders', workOrderIds, {
    params: { projectId },
  });
}

/** 批量解除生产工单的项目关联 */
export function unbindWorkOrders(workOrderIds: number[]) {
  return requestClient.put('/mes/pro/project/unbind-work-orders', workOrderIds);
}
