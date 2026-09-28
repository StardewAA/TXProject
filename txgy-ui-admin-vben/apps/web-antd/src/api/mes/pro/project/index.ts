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
    priority?: number; // 项目优先级
    ownerUserId?: number; // 负责人用户编号
    owner?: string; // 负责人（根据用户自动回填）
    dept?: string; // 责任部门
    planStartTime?: string; // 计划开始时间
    planEndTime?: string; // 计划结束时间
    actualStartTime?: string; // 实际开始时间
    actualEndTime?: string; // 实际结束时间
    durationDays?: number; // 工期天数
    remark?: string; // 备注
    workOrderCount?: number; // 关联生产工单数
    finishedWorkOrderCount?: number; // 已完成生产工单数
    createTime?: number; // 创建时间
  }

  /** MES 项目导入返回 */
  export interface ProjectImportRespVO {
    createCodes?: string[];
    updateCodes?: string[];
    failureCodes?: Record<string, string>;
  }

  /** MES 项目附件 */
  export interface ProjectFile {
    id?: number;
    projectId?: number;
    fileId?: number;
    fileName?: string;
    fileUrl?: string;
    fileSize?: number;
    remark?: string;
    createTime?: number;
  }

  /** MES 项目分页查询参数 */
  export interface PageParams extends PageParam {
    code?: string;
    name?: string;
    status?: number;
    sourceType?: number;
    priority?: number;
    owner?: string;
    dept?: string;
  }

  /** MES 项目生产进度统计 */
  export interface ProjectStatistics {
    projectId?: number; // 项目编号
    workOrderTotal?: number; // 生产工单总数（含已取消）
    prepareCount?: number; // 草稿工单数
    confirmedCount?: number; // 已确认(进行中)工单数
    finishedCount?: number; // 已完成工单数
    canceledCount?: number; // 已取消工单数
    issuedCount?: number; // 已下发工单数
    inProductionCount?: number; // 生产中工单数
    quantityTotal?: number; // 生产数量合计
    quantityProducedTotal?: number; // 已完成生产数量合计
    progress?: number; // 项目完成度百分比
  }

  /** MES 项目生产统计详情（工单进度 + 报工汇总） */
  export interface ProjectStatisticsDetail extends ProjectStatistics {
    feedbackTotal?: number; // 报工单总数
    feedbackPrepareCount?: number; // 草稿报工单数
    feedbackApprovingCount?: number; // 审核中报工单数
    feedbackFinishedCount?: number; // 已完成报工单数
    feedbackQuantityTotal?: number; // 报工数量合计
    qualifiedQuantityTotal?: number; // 合格品数量合计
    unqualifiedQuantityTotal?: number; // 不合格品数量合计
    qualifyRate?: number; // 合格率百分比
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

/** 获得项目的生产统计详情（工单进度 + 报工汇总） */
export function getProjectStatisticsDetail(projectId: number) {
  return requestClient.get<MesProProjectApi.ProjectStatisticsDetail>(
    `/mes/pro/project/statistics-detail?projectId=${projectId}`,
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

/** 恢复项目（已暂停 -> 进行中） */
export function resumeProject(id: number) {
  return requestClient.post(`/mes/pro/project/resume?id=${id}`);
}

/** 强制结项（任意非取消状态 -> 已完成） */
export function forceCloseProject(id: number) {
  return requestClient.put(`/mes/pro/project/force-close?id=${id}`);
}

/** 导入项目 Excel */
export function importProject(file: File, updateSupport: boolean) {
  if (!(file instanceof File)) {
    return Promise.reject(new Error('上传文件无效，请重新选择 Excel 文件'));
  }
  const formData = new FormData();
  // 显式声明 multipart，避免 axios 在 JSON Content-Type 下把 FormData 序列化成 JSON
  formData.append('file', file, file.name);
  formData.append('updateSupport', String(updateSupport ?? false));
  return requestClient.post<MesProProjectApi.ProjectImportRespVO>(
    '/mes/pro/project/import',
    formData,
    {
      headers: { 'Content-Type': 'multipart/form-data' },
    },
  );
}

/** 下载项目导入模板 */
export function getImportTemplate() {
  return requestClient.download('/mes/pro/project/get-import-template');
}

// ==================== 项目附件 ====================

/** 获得项目的附件列表 */
export function getProjectFileList(projectId: number) {
  return requestClient.get<MesProProjectApi.ProjectFile[]>(
    `/mes/pro-project-file/list?projectId=${projectId}`,
  );
}

/** 新增项目附件（文件需先上传到 infra 文件服务） */
export function createProjectFile(data: MesProProjectApi.ProjectFile) {
  return requestClient.post<number>('/mes/pro-project-file/create', data);
}

/** 删除项目附件 */
export function deleteProjectFile(id: number) {
  return requestClient.delete(`/mes/pro-project-file/delete?id=${id}`);
}
