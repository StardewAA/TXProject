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

  /**
   * 报表中心概览统计
   *
   * 全部由服务端聚合，前端不做二次计算：
   *  - reportTotal / frequentCount：来自报表清单
   *  - todayQueryCount / lastUpdateTime：由报表执行日志 create_time 聚合得出
   */
  export interface Overview {
    reportTotal: number; // 可查看的报表总数
    frequentCount: number; // 常用报表数量（近 30 天被查询过）
    todayQueryCount: number; // 今日查询次数（当日零点起的成功查询）
    lastUpdateTime: string; // 最近更新时间（yyyy-MM-dd HH:mm:ss），无记录时为空
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

/** 获得报表中心概览统计 */
export function getReportCenterOverview() {
  return requestClient.get<ReportCenterApi.Overview>('/report/center/overview');
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
