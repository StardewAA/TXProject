import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace ReportDatasetApi {
  /** 数据集参数定义 */
  export interface Param {
    name: string; // 参数名（对应 SQL 中的 ${name} 占位）
    label?: string; // 参数显示名
    type?: string; // 参数类型：string/number/datetime/date
    required?: boolean; // 是否必填
    defaultValue?: string; // 默认值
  }

  /** 报表数据集 */
  export interface Dataset {
    id?: number; // 编号
    code?: string; // 数据集编码
    name?: string; // 数据集名称
    category?: string; // 分类（域）
    sqlText?: string; // SQL 语句
    params?: Param[]; // 参数定义
    tenantColumn?: string; // 租户过滤列名（必填）
    maxRows?: number; // 最大返回行数
    timeoutSeconds?: number; // 查询超时（秒）
    status?: number; // 状态
    owner?: string; // 责任人
    remark?: string; // 备注
    tenantId?: number; // 租户编号（0 为平台级，只读）
    createTime?: Date; // 创建时间
  }

  /** 数据集分页查询参数 */
  export interface DatasetPageParam extends PageParam {
    code?: string;
    name?: string;
    category?: string;
    status?: number;
  }

  /** 数据集执行结果 */
  export interface ExecuteResult {
    dimensions: string[]; // 列名
    source: Record<string, any>[]; // 数据明细
    rowCount: number; // 返回行数
    durationMs: number; // 查询耗时（毫秒）
  }
}

/** 查询数据集分页 */
export function getDatasetPage(params: ReportDatasetApi.DatasetPageParam) {
  return requestClient.get<PageResult<ReportDatasetApi.Dataset>>(
    '/report/dataset/page',
    { params },
  );
}

/** 查询数据集详情 */
export function getDataset(id: number) {
  return requestClient.get<ReportDatasetApi.Dataset>(
    `/report/dataset/get?id=${id}`,
  );
}

/** 查询可用数据集列表（当前租户 + 平台级） */
export function getEnabledDatasetList() {
  return requestClient.get<ReportDatasetApi.Dataset[]>(
    '/report/dataset/list-enabled',
  );
}

/** 新增数据集 */
export function createDataset(data: ReportDatasetApi.Dataset) {
  return requestClient.post('/report/dataset/create', data);
}

/** 修改数据集 */
export function updateDataset(data: ReportDatasetApi.Dataset) {
  return requestClient.put('/report/dataset/update', data);
}

/** 删除数据集 */
export function deleteDataset(id: number) {
  return requestClient.delete(`/report/dataset/delete?id=${id}`);
}

/** 执行数据集查询 */
export function executeDataset(code: string, params?: Record<string, any>) {
  return requestClient.post<ReportDatasetApi.ExecuteResult>(
    '/report/dataset/execute',
    { code, params },
  );
}
