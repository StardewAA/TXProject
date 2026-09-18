import { requestClient } from '#/api/request';

export namespace ReportCenterApi {
  /** 报表筛选参数 */
  export interface Param {
    name: string; // 参数名
    label: string; // 显示名
  }

  /** 报表清单项 */
  export interface Report {
    code: string; // 数据集编码（调用用）
    name: string; // 报表名称
    description: string; // 报表用途说明
    params: Param[]; // 可筛选参数
    groups: string[]; // 业务分组（支持多归属，如 ["生产","库存"]）
    columnLabels: string[]; // 列中文标签（供搜索索引）
    queryCount: number; // 近 30 天查询次数（「常用」排序用）
  }

  /** 报表数据 */
  export interface ReportData {
    headers: string[]; // 中文表头
    rows: any[][]; // 行数据（按表头顺序）
    rowCount: number; // 总行数
    durationMs: number; // 查询耗时（毫秒）
  }
}

/** 获得报表清单 */
export function getReportCenterList() {
  return requestClient.get<ReportCenterApi.Report[]>('/report/center/list');
}

/** 查询报表数据 */
export function queryReportCenterData(
  code: string,
  params?: Record<string, any>,
) {
  return requestClient.post<ReportCenterApi.ReportData>('/report/center/data', {
    code,
    params,
  });
}

/** 一键导出报表 Excel（返回文件流） */
export function exportReportCenterExcel(
  code: string,
  params?: Record<string, any>,
) {
  return requestClient.download<Blob>('/report/center/export-excel', {
    method: 'POST',
    data: { code, params },
  });
}
