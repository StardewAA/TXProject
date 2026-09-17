import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace MesTmToolRepairApi {
  /** MES 工具维修单 */
  export interface ToolRepair {
    id?: number;
    code?: string;
    name?: string;
    repairDate?: Date | number;
    finishDate?: Date | number;
    status?: number;
    repairResult?: string;
    remark?: string;
    createTime?: Date;
  }
  /** MES 工具维修单行 */
  export interface ToolRepairLine {
    id?: number;
    repairId?: number;
    toolId?: number;
    toolCode?: string;
    toolName?: string;
    quantity?: number;
    repairResult?: string;
    remark?: string;
  }
}

/** 查询工具维修单分页 */
export function getToolRepairPage(params: PageParam) {
  return requestClient.get<PageResult<MesTmToolRepairApi.ToolRepair>>('/mes/tm/tool-repair/page', { params });
}

/** 查询工具维修单详情 */
export function getToolRepair(id: number) {
  return requestClient.get<MesTmToolRepairApi.ToolRepair>(`/mes/tm/tool-repair/get?id=${id}`);
}

/** 新增工具维修单 */
export function createToolRepair(data: MesTmToolRepairApi.ToolRepair) {
  return requestClient.post('/mes/tm/tool-repair/create', data);
}

/** 修改工具维修单 */
export function updateToolRepair(data: MesTmToolRepairApi.ToolRepair) {
  return requestClient.put('/mes/tm/tool-repair/update', data);
}

/** 删除工具维修单 */
export function deleteToolRepair(id: number) {
  return requestClient.delete(`/mes/tm/tool-repair/delete?id=${id}`);
}

/** 导出工具维修单 */
export function exportToolRepair(params: any) {
  return requestClient.download('/mes/tm/tool-repair/export-excel', { params });
}

/** 确认工具维修单 */
export function confirmToolRepair(id: number) {
  return requestClient.put(`/mes/tm/tool-repair/confirm?id=${id}`);
}

/** 完成工具维修单 */
export function finishToolRepair(id: number) {
  return requestClient.put(`/mes/tm/tool-repair/finish?id=${id}`);
}

// ========== 行 ==========

/** 查询维修单行列表 */
export function getToolRepairLineListByRepairId(repairId: number) {
  return requestClient.get<MesTmToolRepairApi.ToolRepairLine[]>(`/mes/tm/tool-repair/line/list-by-repair?repairId=${repairId}`);
}

/** 新增维修单行 */
export function createToolRepairLine(data: MesTmToolRepairApi.ToolRepairLine) {
  return requestClient.post('/mes/tm/tool-repair/line/create', data);
}

/** 修改维修单行 */
export function updateToolRepairLine(data: MesTmToolRepairApi.ToolRepairLine) {
  return requestClient.put('/mes/tm/tool-repair/line/update', data);
}

/** 删除维修单行 */
export function deleteToolRepairLine(id: number) {
  return requestClient.delete(`/mes/tm/tool-repair/line/delete?id=${id}`);
}
