import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace MesTmToolScrapApi {
  /** MES 工具报废单 */
  export interface ToolScrap {
    id?: number;
    code?: string;
    name?: string;
    scrapDate?: Date | number;
    status?: number;
    scrapReason?: string;
    remark?: string;
    createTime?: Date;
  }
  /** MES 工具报废单行 */
  export interface ToolScrapLine {
    id?: number;
    scrapId?: number;
    toolId?: number;
    toolCode?: string;
    toolName?: string;
    quantity?: number;
    scrapReason?: string;
    remark?: string;
  }
}

/** 查询工具报废单分页 */
export function getToolScrapPage(params: PageParam) {
  return requestClient.get<PageResult<MesTmToolScrapApi.ToolScrap>>('/mes/tm/tool-scrap/page', { params });
}

/** 查询工具报废单详情 */
export function getToolScrap(id: number) {
  return requestClient.get<MesTmToolScrapApi.ToolScrap>(`/mes/tm/tool-scrap/get?id=${id}`);
}

/** 新增工具报废单 */
export function createToolScrap(data: MesTmToolScrapApi.ToolScrap) {
  return requestClient.post('/mes/tm/tool-scrap/create', data);
}

/** 修改工具报废单 */
export function updateToolScrap(data: MesTmToolScrapApi.ToolScrap) {
  return requestClient.put('/mes/tm/tool-scrap/update', data);
}

/** 删除工具报废单 */
export function deleteToolScrap(id: number) {
  return requestClient.delete(`/mes/tm/tool-scrap/delete?id=${id}`);
}

/** 导出工具报废单 */
export function exportToolScrap(params: any) {
  return requestClient.download('/mes/tm/tool-scrap/export-excel', { params });
}

/** 确认工具报废单 */
export function confirmToolScrap(id: number) {
  return requestClient.put(`/mes/tm/tool-scrap/confirm?id=${id}`);
}

// ========== 行 ==========

/** 查询报废单行列表 */
export function getToolScrapLineListByScrapId(scrapId: number) {
  return requestClient.get<MesTmToolScrapApi.ToolScrapLine[]>(`/mes/tm/tool-scrap/line/list-by-scrap?scrapId=${scrapId}`);
}

/** 新增报废单行 */
export function createToolScrapLine(data: MesTmToolScrapApi.ToolScrapLine) {
  return requestClient.post('/mes/tm/tool-scrap/line/create', data);
}

/** 修改报废单行 */
export function updateToolScrapLine(data: MesTmToolScrapApi.ToolScrapLine) {
  return requestClient.put('/mes/tm/tool-scrap/line/update', data);
}

/** 删除报废单行 */
export function deleteToolScrapLine(id: number) {
  return requestClient.delete(`/mes/tm/tool-scrap/line/delete?id=${id}`);
}
