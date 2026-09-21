import type { PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace InfraWorkLogApi {
  export type Scope = 'ALL' | 'SELF';
  export type ResultStatus = 'FAILURE' | 'SUCCESS';

  export interface ScopeResult {
    canViewAll: boolean;
  }

  export interface QueryReq {
    action?: string;
    beginTime?: string[];
    keyword?: string;
    module?: string;
    resultStatus?: ResultStatus;
    scope?: Scope;
    userId?: number;
  }

  export interface PageReq extends QueryReq {
    pageNo: number;
    pageSize: number;
  }

  export interface ChangeEntry {
    after: unknown;
    before: unknown;
    operation: string;
    outcome?: 'COMMITTED' | 'EXECUTED' | 'PENDING' | 'ROLLED_BACK';
    table: string;
  }

  export interface Changes {
    entries: ChangeEntry[];
    warnings: string[];
  }

  export interface WorkLog {
    action: string;
    beginTime: string;
    businessKey: string;
    changes: string;
    duration: number;
    id: number;
    module: string;
    requestMethod: string;
    requestParams: string;
    requestUrl: string;
    resultCode: number;
    resultMsg: string;
    summary: string;
    traceId: string;
    userAgent: string;
    userId: number;
    userIp: string;
    userName: string;
  }
}

/** 查询当前账号可用的留痕范围 */
export function getWorkLogScope() {
  return requestClient.get<InfraWorkLogApi.ScopeResult>('/infra/work-log/scope');
}

/** 分页查询工作留痕 */
export function getWorkLogPage(params: InfraWorkLogApi.PageReq) {
  return requestClient.get<PageResult<InfraWorkLogApi.WorkLog>>('/infra/work-log/page', { params });
}

/** 查询工作留痕详情 */
export function getWorkLog(id: number, scope: InfraWorkLogApi.Scope) {
  return requestClient.get<InfraWorkLogApi.WorkLog>('/infra/work-log/get', {
    params: { id, scope },
  });
}

/** 按当前范围及筛选条件导出工作留痕 */
export function exportWorkLog(params: InfraWorkLogApi.QueryReq) {
  return requestClient.download('/infra/work-log/export-excel', { params });
}
