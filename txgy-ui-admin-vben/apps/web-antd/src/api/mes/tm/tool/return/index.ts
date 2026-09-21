import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace MesTmToolReturnApi {
  /** MES 工具归还单 */
  export interface ToolReturn {
    id?: number;
    code?: string;
    name?: string;
    workstationId?: number;
    workstationName?: string;
    returnDate?: Date | number;
    status?: number;
    remark?: string;
    createTime?: Date;
  }
  /** MES 工具归还单行 */
  export interface ToolReturnLine {
    id?: number;
    returnId?: number;
    toolId?: number;
    toolCode?: string;
    toolName?: string;
    quantity?: number;
    remark?: string;
  }
}

/** 查询工具归还单分页 */
export function getToolReturnPage(params: PageParam) {
  return requestClient.get<PageResult<MesTmToolReturnApi.ToolReturn>>('/mes/tm/tool-return/page', { params });
}

/** 查询工具归还单详情 */
export function getToolReturn(id: number) {
  return requestClient.get<MesTmToolReturnApi.ToolReturn>(`/mes/tm/tool-return/get?id=${id}`);
}

/** 新增工具归还单 */
export function createToolReturn(data: MesTmToolReturnApi.ToolReturn) {
  return requestClient.post('/mes/tm/tool-return/create', data);
}

/** 修改工具归还单 */
export function updateToolReturn(data: MesTmToolReturnApi.ToolReturn) {
  return requestClient.put('/mes/tm/tool-return/update', data);
}

/** 删除工具归还单 */
export function deleteToolReturn(id: number) {
  return requestClient.delete(`/mes/tm/tool-return/delete?id=${id}`);
}

/** 导出工具归还单 */
export function exportToolReturn(params: any) {
  return requestClient.download('/mes/tm/tool-return/export-excel', { params });
}

/** 确认工具归还单 */
export function confirmToolReturn(id: number) {
  return requestClient.put(`/mes/tm/tool-return/confirm?id=${id}`);
}

// ========== 行 ==========

/** 查询归还单行列表 */
export function getToolReturnLineListByReturnId(returnId: number) {
  return requestClient.get<MesTmToolReturnApi.ToolReturnLine[]>(`/mes/tm/tool-return/line/list-by-return?returnId=${returnId}`);
}

/** 新增归还单行 */
export function createToolReturnLine(data: MesTmToolReturnApi.ToolReturnLine) {
  return requestClient.post('/mes/tm/tool-return/line/create', data);
}

/** 修改归还单行 */
export function updateToolReturnLine(data: MesTmToolReturnApi.ToolReturnLine) {
  return requestClient.put('/mes/tm/tool-return/line/update', data);
}

/** 删除归还单行 */
export function deleteToolReturnLine(id: number) {
  return requestClient.delete(`/mes/tm/tool-return/line/delete?id=${id}`);
}
