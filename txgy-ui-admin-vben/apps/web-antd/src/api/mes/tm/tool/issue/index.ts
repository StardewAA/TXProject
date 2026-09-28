import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace MesTmToolIssueApi {
  /** MES 工具领用单 */
  export interface ToolIssue {
    id?: number;
    code?: string;
    name?: string;
    workstationId?: number;
    workstationName?: string;
    issueDate?: Date | number;
    status?: number;
    remark?: string;
    createTime?: Date;
  }
  /** MES 工具领用单行 */
  export interface ToolIssueLine {
    id?: number;
    issueId?: number;
    toolId?: number;
    toolCode?: string;
    toolName?: string;
    quantity?: number;
    remark?: string;
  }
}

/** 查询工具领用单分页 */
export function getToolIssuePage(params: PageParam) {
  return requestClient.get<PageResult<MesTmToolIssueApi.ToolIssue>>('/mes/tm/tool-issue/page', { params });
}

/** 查询工具领用单详情 */
export function getToolIssue(id: number) {
  return requestClient.get<MesTmToolIssueApi.ToolIssue>(`/mes/tm/tool-issue/get?id=${id}`);
}

/** 新增工具领用单 */
export function createToolIssue(data: MesTmToolIssueApi.ToolIssue) {
  return requestClient.post('/mes/tm/tool-issue/create', data);
}

/** 修改工具领用单 */
export function updateToolIssue(data: MesTmToolIssueApi.ToolIssue) {
  return requestClient.put('/mes/tm/tool-issue/update', data);
}

/** 删除工具领用单 */
export function deleteToolIssue(id: number) {
  return requestClient.delete(`/mes/tm/tool-issue/delete?id=${id}`);
}

/** 导出工具领用单 */
export function exportToolIssue(params: any) {
  return requestClient.download('/mes/tm/tool-issue/export-excel', { params });
}

/** 确认工具领用单 */
export function confirmToolIssue(id: number) {
  return requestClient.put(`/mes/tm/tool-issue/confirm?id=${id}`);
}

// ========== 行 ==========

/** 查询领用单行列表 */
export function getToolIssueLineListByIssueId(issueId: number) {
  return requestClient.get<MesTmToolIssueApi.ToolIssueLine[]>(`/mes/tm/tool-issue/line/list-by-issue?issueId=${issueId}`);
}

/** 新增领用单行 */
export function createToolIssueLine(data: MesTmToolIssueApi.ToolIssueLine) {
  return requestClient.post('/mes/tm/tool-issue/line/create', data);
}

/** 修改领用单行 */
export function updateToolIssueLine(data: MesTmToolIssueApi.ToolIssueLine) {
  return requestClient.put('/mes/tm/tool-issue/line/update', data);
}

/** 删除领用单行 */
export function deleteToolIssueLine(id: number) {
  return requestClient.delete(`/mes/tm/tool-issue/line/delete?id=${id}`);
}
